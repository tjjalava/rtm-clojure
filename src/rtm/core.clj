(ns rtm.core
  (:gen-class)
  (:require [ring.middleware.json :refer [wrap-json-response wrap-json-params]]
            [ring.util.response :refer [response content-type status]]
            [ring.middleware.defaults :refer :all]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [rtm.api :refer [api-routes]]
            [ring.adapter.jetty :refer [run-jetty]]
            [config.core :refer [env]]))

(defn mailgun-handler []
  (content-type (response "Hello world") "text/plain"))

(defn wrap-exception [handler json?]
  (fn [request]
    (try (handler request)
         (catch Exception e
           (do
             (clojure.stacktrace/print-stack-trace e)
             (let [data (ex-data e)
                   resp-status (:status data 500)]
               (status (if json?
                         (response {:error (.getMessage e)})
                         (content-type (response (.getMessage e)) "text/plain")) resp-status)))))))

(defroutes api
           (context "/api" []
             api-routes))

(defroutes mailgun-routes
           (GET "/" [] mailgun-handler)
           (GET "/error" [] (throw (Exception. "this is error"))))

(defroutes app-routes
           (-> api
               (wrap-json-params)
               (wrap-exception true)
               (wrap-json-response))
           (wrap-exception mailgun-routes false)
           (route/not-found (content-type (response "Not Found") "text/plain")))


(def app
  (wrap-defaults app-routes (assoc api-defaults
                              :proxy true
                              :session {:cookie-attrs {:secure (:session-cookie-secure env)}
                                        :http-only true
                                        :same-site :strict})))

(defn -main [& args]
  (run-jetty app {:port (Integer/valueOf (:port env "3000"))}))