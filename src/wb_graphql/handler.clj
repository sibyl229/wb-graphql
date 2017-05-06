(ns wb-graphql.handler
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes context]]
            [compojure.route :as route]
            [datomic.api :as d]
            [mount.core :as mount]
            [ring.util.response :as response]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.json :refer [wrap-json-params]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer :all]
            [wb-graphql.db :refer [datomic-conn]]
            [wb-graphql.graphql :as graphql]))

(defn create-routes []
  (routes
   (GET "/" [schema query variables :as request]
        (if (= (:content-type request) "application/json")
          (do (println "GET query: " query)
              (response/response
               ((graphql/create-executor (:db request)) query variables)))
          (response/redirect "/index.html" 301)))
   (POST "/" [schema query variables :as request]
         (println "POST query: " query)
         ;; (println "Post variables: " (json/parse-string variables))
         (response/response
          (try
            ((graphql/create-executor (:db request)) query (json/parse-string variables))
            (catch Throwable e
              (println e)))))
   (route/resources "/" {:root ""})
   (route/not-found "<h1>Page not found</h1>")))

(defn wrap-db [handler db]
  (fn [request]
    (handler (assoc request :db db))))

(defn wrap-app [handler db]
  (fn [request]
    (let [wrapped-app
          (-> handler
              (wrap-db db)
              (wrap-json-response)
              (wrap-cors :access-control-allow-origin [#"http://localhost:8080" #"http://.*"]
                         :access-control-allow-methods [:get :put :post :delete])
              (wrap-defaults api-defaults)
              (wrap-json-params))]
      (wrapped-app request))))

(defn init []
  (mount/start))

(defn destroy []
  (mount/stop))

(defn app [request]
  (let [handler (-> (create-routes)
                    (wrap-app (d/db datomic-conn)))]
    (handler request)))
