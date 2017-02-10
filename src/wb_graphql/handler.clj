(ns wb-graphql.handler
  (:require [cheshire.core :as json]
            [compojure.core :refer [GET POST routes]]
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

(defn create-routes [db]
  (let [graphql-executor (graphql/create-executor db)]
    (routes
     (GET "/" [] "<h1>Hello World</h1>")
     (GET "/graphql" [schema query variables :as request]
          (println "GET query: " query)
          (response/response
           (graphql-executor query variables)))
     (POST "/graphql" [schema query variables :as request]
           (println "POST query: " query)
           ;; (println "Post variables: " (json/parse-string variables))
           (response/response
            (try
              (graphql-executor query (json/parse-string variables))
              (catch Throwable e
                (println e)))))
     (route/resources "/" {:root ""})
     (route/not-found "<h1>Page not found</h1>"))))

(defn init []
  (mount/start))

(defn destroy []
  (mount/stop))

(defn app [request]
  (let [handler (-> (d/db datomic-conn)
                    (create-routes)
                    (wrap-json-response)
                    (wrap-cors :access-control-allow-origin [#"http://localhost:8080" #"http://.*"]
                               :access-control-allow-methods [:get :put :post :delete])
                    (wrap-defaults api-defaults)
                    (wrap-json-params))]
    (handler request)))
