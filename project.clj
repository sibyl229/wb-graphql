(defproject wormbase/wb-graphql "0.1.0-SNAPSHOT"
  :description "graphql-clj starter project"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.0"]
                 [com.taoensso/nippy "2.13.0"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]
                 [cheshire "5.6.3"]
                 [ring/ring-json "0.4.0" :exclusions [cheshire]] ;; outdated cheshire mess up connection to Datomic
                 [ring-cors "0.1.8"]
                 [ring-middleware-accept "2.0.3"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [graphql-clj "0.2.2" :exclusions [org.clojure/clojure]]
                 [clojure-future-spec "1.9.0-alpha13"]
                 [mount "0.1.11"]
                 [environ "1.1.0"]]
  :main ^:skip-aot wb-graphql.graphql
  :target-path "target/%s"
  :resource-paths ["resources" "build"]
  :profiles
  {:datomic-free
   {:dependencies [[com.datomic/datomic-free "0.9.5554"
                    :exclusions [joda-time]]]}

   :datomic-pro
   {:dependencies [[com.datomic/datomic-pro "0.9.5554"
                    :exclusions [joda-time]]
                   [com.amazonaws/aws-java-sdk-dynamodb "1.11.6"
                    :exclusions [joda-time]]]}

   :dev [:datomic-pro
         {; :ring {:stacktrace-middleware prone.middleware/wrap-exceptions}  ; http://localhost:3000/prone/latest
          :resource-paths ["resources" "build"]
          :dependencies [[prone "1.1.1"]]
          :env {:wb-db-uri "datomic:ddb://us-east-1/WS258/wormbase"}}]
   :uberjar [:datomic-pro {:aot :all}]}
  :plugins [[lein-environ "1.1.0"]
            [lein-ring "0.11.0"]]
  :ring {:handler wb-graphql.handler/app
         :init wb-graphql.handler/init
         :destroy wb-graphql.handler/destroy
         :auto-reload? true
         :host "0.0.0.0"}
  :repositories [["tmp" "file:///tmp/clojure_jars"]]
  :javac-options ["-target" "1.8" "-source" "1.8"]
  :jvm-opts ["-Xmx6G"
             ;; same GC options as the transactor,
             ;; should minimize long pauses.
             "-XX:+UseG1GC" "-XX:MaxGCPauseMillis=50"
             "-Ddatomic.objectCacheMax=2500000000"
             "-Ddatomic.txTimeoutMsec=1000000"])
