(ns wb-graphql.db
  (:require
   [environ.core :refer (env)]
   [datomic.api :as d]
   [mount.core :refer [defstate]]))

(defn- new-connection []
  (d/connect (env :wb-db-uri)))

(defn- disconnect [conn]
  (d/release conn))

(defstate datomic-conn
  :start (new-connection)
  :stop (disconnect datomic-conn))
