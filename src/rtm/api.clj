(ns rtm.api
  (:require [clojure.string :as str]
            [ring.util.response :refer [response status]]
            [clj-http.client :as client]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [cheshire.core :refer :all]))

(def ^:private ^:const host-path "https://suuli.spv.fi")

(def ^:private ^:const default-headers
  {"Accept-Language" "fi,en-US;q=0.9,en;q=0.8"
   "Accept-Encoding" "gzip, deflate, br"
   "Referer" "https://suuli.spv.fi/"
   "Cache-Control" "no-cache"})

(def ^:private ^:const suuli-ttl 43200)

(def ^:private ^:const path-access
  {"/berths" (fn [roles] (every? #(contains? roles %) [:berthReader :boatReader :memberReader]))})

(defn- auth-headers [token]
  (assoc default-headers "Authorization" token))

(defn- suuli-login [username password ttl]
  (let [body (:body (client/post (str host-path "/api/People/login")
                          {:form-params {:username username :password password :ttl ttl}
                           :headers default-headers
                           :content-type :json
                           :accept :json
                           :as :json}))]
    {:token (:id body)
     :role-names (set (map #(keyword %) (str/split (:roleNames body) #";")))}))



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
        (not (and username password issued)) (status (response {:message "No session"}) 401)

        (> (- (System/currentTimeMillis) issued) (* suuli-ttl 1000))
        (do
          (prn "Suuli session expired. Refreshing..")
          (let [new-session (merge (assoc session :issued (System/currentTimeMillis))
                                   (suuli-login username password suuli-ttl))]
            (->
              (handler (assoc request :session new-session))
              (assoc :session new-session))))

        :else (handler request)))))

(defn- wrap-role-check [handler]
  (fn [request]
    (let [roles (get-in request [:session :role-names])
          path (:path-info request)]
      (if ((path-access path) roles)
        (handler request)
        (status (response {:error "Access denied"}) 403)))))

(defn- login [username password session]
  (if (not (or (nil? username) (nil? password)))

    (let [session (merge (assoc session
                           :username username
                           :password password
                           :issued (System/currentTimeMillis))
                         (suuli-login username password suuli-ttl))]
      (-> (status (response {}) 204)
          (assoc :session (vary-meta session assoc :recreate true))))

    (-> (status (response {:message "NOK"}) 400)
        (assoc :session nil))))

(defroutes session-routes
           (GET "/berths" {{token :token} :session} (response (get-berths token))))

(defroutes api-routes
           (POST "/login" [username password :as {session :session}]
             (login username password session))
           (->
             session-routes
             (wrap-role-check)
             (wrap-suuli-session))
           (route/not-found (response {:error "Not Found"})))