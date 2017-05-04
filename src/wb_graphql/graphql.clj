(ns wb-graphql.graphql
  (:require [graphql-clj.parser :as parser]
            [graphql-clj.resolver :as resolver]
            [graphql-clj.executor :as executor]
            [graphql-clj.schema-validator :as schema-validator]
            [graphql-clj.introspection :as introspection]
            [clojure.string :as str]
            [clojure.core.match :as match]
            [datomic.api :as d]
            [taoensso.nippy :as nippy]
            [wb-graphql.db :refer [datomic-conn]]))



(def starter-type-schema "
type Object {
  id: String!
}
")

(def starter-schema "
schema {
  query: Query
}
")

(defn type-names [db]
  (d/q '[:find [?ns ...]
         :where
         [?e :db/ident ?ident]
         [_ :db.install/attribute ?e]
         [(namespace ?ident) ?ns]
         (not-join [?ns]
                   [_ :pace/use-ns ?ns])]
       db))

(defn has-type [db datomic-type-name]
  (seq
   (d/q '[:find [?ident ...]
          :in $ ?ns
          :where
          [?e :db/ident ?ident]
          [_ :db.install/attribute ?e]
          [(namespace ?ident) ?ns]
          (not-join [?ns]
                    [_ :pace/use-ns ?ns])]
        db (name datomic-type-name))))

(defn core-type-names [db]
  (d/q '[:find [?ns ...]
         :in $ ?n
         :where
         [(name ?ident) ?n]
         [?e :db/ident ?ident]
         [?e :db/valueType :db.type/string]
         [_ :db.install/attribute ?e]
         [(namespace ?ident) ?ns]
         (not-join [?ns]
                    [_ :pace/use-ns ?ns])]
       db "id")) ;; TODO check type isn't a component

(defn component-name [attr-name]
  (keyword (str (namespace attr-name)
                "."
                (name attr-name))))

(defn graphql-type-name [datomic-type-name]
  (-> (name datomic-type-name)
      (str/replace #"-" "_")
      (str/replace #"\." "__")
      (str/capitalize)))

(defn graphql-field-name [datomic-attr-name]
  (-> (name datomic-attr-name)
      (str/replace #"-" "_")
      (str/replace #"\." "__")))

(defn graphql-field-name-reverse [datomic-attr-name]
  (let [[type-name comp-name]
        (str/split (namespace datomic-attr-name) #"\." 2)]
    (-> (str (name datomic-attr-name)
             "__OF__" type-name
             (if comp-name (str "__VIA__" comp-name)))
        (str/replace #"-" "_")
        (str/replace #"\." "__"))))

(defn has-attr [db datomic-attr-name]
  (let [fixed-attr-name (-> datomic-attr-name
                            (str/replace #"/_" "/")
                            (str/replace #"^:" ""))]
    (d/entity db [:db/ident (keyword fixed-attr-name)])))

(defmulti datomic-field-name
  (fn[_ graphql-field-name]
    (cond
      (re-find #"__OF__" graphql-field-name) :reverse)))

(defmethod datomic-field-name :default [graphql-type-name graphql-field-name]
  (-> (str graphql-type-name "/" graphql-field-name)
      (str/replace #"__" ".")
      (str/replace #"_" "-")
      (str/lower-case)
      (keyword)))

(defmethod datomic-field-name :reverse [_ graphql-field-name]
  (let [[attr-name type-name comp-name]
        (str/split graphql-field-name #"(__OF__|__VIA__)")]
    (-> (str type-name
             (if comp-name (str "." comp-name))
             "/"
             attr-name)
        (str/replace #"__" ".")
        (str/replace #"_" "-")
        (str/replace #"/" "/_")
        (keyword))))

(defn field-type [field-entity]
  (case (:db/valueType field-entity)
    :db.type/string "String"
    :db.type/boolean "Boolean"
    :db.type/long "Int"
    :db.type/float "Float"
    :db.type/double "Float"
    :db.type/ref (if (:db/isComponent field-entity)
                   (let [cn (->> (:db/ident field-entity)
                                 (component-name))]
                     (if-let [c (has-type (d/entity-db field-entity) cn)]
                       (graphql-type-name cn)
                       "String"))
                   (if-let [ref-name (:pace/obj-ref field-entity)]
                     (->> ref-name
                          (namespace)
                          (graphql-type-name))
                     "String")) ;; TODO Enum
    "String")) ;; TODO Date (instance type)

(defn field-type-reverse [field-entity]
  (let [tn (namespace (:db/ident field-entity))]
    (if (has-type (d/entity-db field-entity) tn)
      (graphql-type-name tn)
      "String")))

(defn field-schema [db field-name]
  (let [field-entity (d/entity db field-name)]
    (case (:db/cardinality field-entity)
      :db.cardinality/one
      (format "%s: %s"
              (graphql-field-name field-name)
              (field-type field-entity))

      :db.cardinality/many
      (format "%s: [%s]"
              (graphql-field-name field-name)
              (field-type field-entity)))))

(defn field-schema-reverse [db field-name]
  (let [field-entity (d/entity db field-name)
        gq-field-name (graphql-field-name-reverse field-name)
        gq-type-name (field-type-reverse field-entity)]
    (if (:db/isComponent field-entity)
      (format "%s: %s" gq-field-name gq-type-name)
      (format "%s: [%s]" gq-field-name gq-type-name))))

(defn get-attrs [db type-name]
  (d/q '[:find [?ident ...]
         :in $ ?ns
         :where
         [?e :db/ident ?ident]
         [_ :db.install/attribute ?e]
         [(namespace ?ident) ?ns]]
       db (name type-name)))

(defn get-refs-reverse [db type-name]
  (d/q '[:find [?ident ...]
         :in $ ?ns
         :where
         [?e :pace/obj-ref ?ref]
         [?ref :db/ident ?ref-ident]
         [(namespace ?ref-ident) ?ns]
         [?e :db/ident ?ident]
         [_ :db.install/attribute ?e]]
       db (name type-name)))

(defn get-comp-reverse [db comp-type-name]
  (if-let [[_ parent-name attr-name]
           (re-matches #"(.+)\.(.+)" (name comp-type-name))]
    (let [attr-kw (keyword (str parent-name "/" attr-name))]
      (if (d/entity db attr-kw)
        [attr-kw]
        []))))

(defn type-schema [db type-name]
  (if-let [attrs (seq (get-attrs db type-name))]
    (let [reverse-refs (get-refs-reverse db type-name)
          reverse-comp (get-comp-reverse db type-name)]
      (->> (concat (map (partial field-schema db) attrs)
                   (map (partial field-schema-reverse db) reverse-refs)
                   (map (partial field-schema-reverse db) reverse-comp))
           (map (partial str "  "))
           (str/join "\n")
           (format "
type %s {
%s
}
"
                   (graphql-type-name type-name))))))

(defn type-connection-schema [db type-name]
  (let [gq-type-name (graphql-type-name type-name)]
    (apply format "
type %sEdge {
  node: %s
  cursor: String
}

type %sConnection {
  edges: [%sEdge]
  hasNextPage: Boolean
  endCursor: String
}
" (take 4 (repeat gq-type-name)))))

(defn interface-names [db]
  (d/q '[:find [?inf ...]
           :where
           [?e :db/ident _]
           [_ :db.install/attribute ?e]
           [?e :pace/use-ns ?inf]]
         db))

(defn x [ident]
  (let [db (d/db datomic-conn)]
    (d/q '[:find (pull ?e [*])
           :in $ ?ident
           :where
           [?e :db/ident ?ident]
           (not [_ :db.install/attribute ?e])
           ]
         db ident)))

(defn generate-type-schema [db]
  (let [ts (type-names db)]
    (concat
     (map (fn [tn]
            (try
              (type-schema db tn)
              (catch Exception e (str tn " causes problem")))) ts)
     (map (fn [tn]
            (try
              (type-connection-schema db tn)
              (catch Exception e (str tn " causes problem")))) ts))))

(defn generate-query-schema [db]
  (->> (core-type-names db)
       (map #(format "%s(id: String!): %s"
                     (str/replace % #"-" "_")
                     (graphql-type-name %)))
       (str/join "\n  ")
       (format "
type Query {
  %s
  getGenesByNames(names: String!, cursor: String): GeneConnection
}
")))


(defn starter-resolver-fn [type-name field-name]
  (let [db (d/db datomic-conn)]
    (match/match
     [type-name field-name]
     ["Query" "getGenesByNames"] (defn x [context parent args]
                                   (let [names (str/split (get args "names") #"\s+")
                                         cursor (read-string (get args "cursor" "0"))
                                         objects (take 10 (sort (d/q '[:find [?g ...]
                                                                       :in $ [?nm ...] ?c
                                                                       :where
                                                                       [?g :gene/public-name ?nm]
                                                                       [(> ?g ?c)]]
                                                                     db names cursor)))]
                                     (->> objects
                                          (map #(assoc {}
                                                       :node (d/entity db %)
                                                       :cursor %))
                                          (assoc {:hasNextPage (boolean (seq objects))
                                                  :endCursor (last objects)} :edges))))

     ["Query", _] (let [kwid (-> (str field-name "/id")
                                 (str/replace #"_" "-")
                                 (keyword))]
                    (if (d/entity db kwid)
                      (fn [context parent args]
                        (d/entity db [kwid (get args "id")]))))

     :else (let [attr-kw (datomic-field-name type-name field-name)]
             (if (has-attr db attr-kw)
               (fn [context parent args]
                 (attr-kw parent)))))))





;; (def introspection-schema introspection/introspection-schema)

(def schema-filename "schema.graphql")

(def serialized-schema-filename "validated-schema")

(defn load-validated-schema []
  (let [schema-input-stream (->> (clojure.java.io/resource serialized-schema-filename)
                                 (clojure.java.io/input-stream))]
    (with-open [out (new java.io.ByteArrayOutputStream)]
      (clojure.java.io/copy schema-input-stream out)
      (nippy/thaw (.toByteArray out)))))

(defn create-executor [db]
  (let [validated-schema (load-validated-schema)
        context nil]
    (fn [query variables]
      (executor/execute context validated-schema starter-resolver-fn query variables))))

(defn merge-schema [& schema-parts]
  (->> (flatten schema-parts)
       (str/join "\n")))

(defn -main []
  (let [db (do (mount.core/start)
               (d/db datomic-conn))
        raw-schema (merge-schema starter-type-schema
                                 (generate-type-schema db)
                                 (generate-query-schema db)
                                 starter-schema)
        validated-schema (->> raw-schema
                              (parser/parse-schema)
                              (schema-validator/validate-schema))
        resources-path (partial format "resources/%s")]
    (do (spit (resources-path schema-filename) raw-schema)
        (with-open [w (clojure.java.io/output-stream (resources-path serialized-schema-filename))]
          (.write w (nippy/freeze validated-schema))))
        (mount.core/stop)))
