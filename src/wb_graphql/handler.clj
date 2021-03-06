(ns wb-graphql.handler
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes context]]
            [compojure.route :as route]
            [datomic.api :as d]
            [mount.core :as mount]
            [ring.util.response :as response]
            [ring.middleware.accept :refer [wrap-accept]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer :all]
            [wb-graphql.db :refer [datomic-conn]]
            [wb-graphql.graphql :as graphql]))

(defn create-routes []
  (routes
   (GET "/" [schema query variables :as request]
        (let [accept-format (->> request
                                 :accept
                                 :mime)]
          (if (= accept-format "text/html")
            (response/redirect (-> (:uri request)
                                   (clojure.string/replace #"\/$" "")
                                   (str "/index.html"))
                               301)
            (do (println "GET query: " query)
                (response/response
                 ((graphql/get-executor (:db request)) query variables))))))
   (POST "/" [schema query variables :as request]
         (println "POST query: " query)
         ;; (println "Post variables: " (json/parse-string variables))
         (response/response
          (try
            ((graphql/get-executor (:db request)) query (json/parse-string variables))
            (catch Throwable e
              (println e)))))
   (route/resources "/" {:root ""})
   (route/not-found "<h1>Page not found</h1>")))

(defn wrap-db [handler db]
  (fn [request]
    (handler (assoc request :db db))))

(defn wrap-request-id [handler]
  (fn [request]
    (let [response (handler request)
          x-request-id ((:headers request) "x-request-id")]
      (assoc-in response [:headers "x-request-id"] x-request-id))))

(defn wrap-app [handler db]
  (fn [request]
    (let [wrapped-app
          (-> handler
              (wrap-db db)
              (wrap-request-id)
              (wrap-json-response)
              (wrap-cors :access-control-allow-origin [#"http://localhost:8080" #"http://.*"]
                         :access-control-allow-methods [:get :put :post :delete]
                         :access-control-expose-headers ["x-request-id"])
              (wrap-defaults api-defaults)
              (wrap-json-params)
              (wrap-accept {:mime ["text/html" "application/json"]}))]
      (wrapped-app request))))

(defn init []
  (mount/start))

(defn destroy []
  (mount/stop))

(defn app [request]
  (let [handler (-> (create-routes)
                    (wrap-app (d/db datomic-conn)))]
    (handler request)))
