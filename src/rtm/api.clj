(ns rtm.api)
(require '[ring.util.response :refer [response status]]
         '[clj-http.client :as client]
         '[compojure.core :refer :all]
         '[compojure.route :as route]
         '[cheshire.core :refer :all])

(def ^:private host-path "https://suuli.spv.fi")

(def ^:private default-headers
  {"Accept-Language" "fi,en-US;q=0.9,en;q=0.8"
   "Accept-Encoding" "gzip, deflate, br"
   "Referer" "https://suuli.spv.fi/"
   "Cache-Control" "no-cache"})

(def ^:private suuli-ttl 43200)

(defn- auth-headers [token]
  (assoc default-headers "Authorization" token))

(defn- suuli-login [username password ttl]
  (get-in (client/post (str host-path "/api/People/login")
               {:form-params {:username username :password password :ttl ttl}
                :headers default-headers
                :content-type :json
                :accept :json
                :as :json}) [:body :id]))

(defn- get-berths [token]
  (let [resp (client/get (str host-path "/api/Berths/search?query=" (generate-string {:clubId 138 :berthNumber ""}))
                         {:headers (auth-headers token)
                          :accept :json
                          :as :json})]
    (reduce (fn [berth-map berth]
              (assoc berth-map (:berthNumber berth)
                               (select-keys berth [:id :berthNumber :boatName :customerName :name :ownerId])))
            {} (:body resp)))
  )

(defn- wrap-suuli-session [handler]
  (fn [request]
    (let [session (:session request)
          {:keys [username password issued]} session]
      (cond
        (or (nil? username) (nil? password) (nil? issued)) (status (response {:message "No session"}) 401)

        (> (- (System/currentTimeMillis) issued) (* suuli-ttl 1000))
        (do
          (prn "Suuli session expired. Refreshing..")
          (let [new-session (assoc session
                              :token (suuli-login username password suuli-ttl)
                              :issued (System/currentTimeMillis))]
            (->
              (handler (assoc request :session new-session))
              (assoc :session new-session))))

        :else (handler request)))))

(defn- login [username password session]
  (if (not (or (nil? username) (nil? password)))

    (let [session (assoc session
                    :username username
                    :password password
                    :token (suuli-login username password suuli-ttl)
                    :issued (System/currentTimeMillis))]
      (-> (status (response {}) 204)
          (assoc :session (vary-meta session assoc :recreate true))))

    (-> (status (response {:message "NOK"}) 400)
        (assoc :session nil))))

(defroutes session-routes
           (GET "/berths" {{token :token} :session} (response (get-berths token))))

(defroutes api-routes
           (POST "/login" [username password :as {session :session}]
             (login username password session))
           (wrap-suuli-session session-routes)
           (route/not-found (response {:error "Not Found"})))