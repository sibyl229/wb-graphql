(ns wb-graphql.graphql
  (:require [graphql-clj.parser :as parser]
            [graphql-clj.type :as type]
            [graphql-clj.resolver :as resolver]
            [graphql-clj.executor :as executor]
            [graphql-clj.validator :as validator]
            [graphql-clj.introspection :as introspection]
            [clojure.string :as str]
            [clojure.core.match :as match]
            [datomic.api :as d]
            [wb-graphql.db :refer [datomic-conn]]))



(def starter-type-schema "
enum Episode { NEWHOPE, EMPIRE, JEDI }

interface Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
}

type Human implements Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  homePlanet: String
}

type Droid implements Character {
  id: String!
  name: String
  friends: [Character]
  appearsIn: [Episode]
  primaryFunction: String
}

type Object {
  id: String!
}

input WorldInput {
  text: String
}
")

(def starter-schema "
type Mutation {
  createHuman(name: String, friends: [String]): Human
}

schema {
  query: Query
  mutation: Mutation
}
")

(def luke {:id "1000",
           :name "Luke Skywalker"
           :friends ["1002" "1003" "2000" "2001" ]
           :appearsIn [ 4, 5, 6 ],
           :homePlanet "Tatooine"})

(def vader {:id "1001",
            :name "Darth Vader"
            :friends [ "1004" ]
            :appearsIn [ 4, 5, 6 ]
            :homePlanet "Tatooine"})

(def han {
          :id "1002",
          :name "Han Solo",
          :friends [ "1000", "1003", "2001" ],
          :appearsIn [ 4, 5, 6 ],
})

(def leia {
           :id "1003",
           :name "Leia Organa",
           :friends [ "1000", "1002", "2000", "2001" ],
           :appearsIn [ 4, 5, 6 ],
           :homePlanet "Alderaan",
})

(def tarkin {
             :id "1004",
             :name "Wilhuff Tarkin",
             :friends [ "1001" ],
             :appearsIn [ 4 ],
             })

(def humanData  (atom {
                       "1000" luke
                       "1001" vader
                       "1002" han
                       "1003" leia
                       "1004" tarkin}))

(def threepio {
               :id "2000",
               :name "C-3PO",
               :friends [ "1000", "1002", "1003", "2001" ],
               :appearsIn [ 4, 5, 6 ],
               :primaryFunction "Protocol",
               })

(def artoo {
            :id "2001",
            :name "R2-D2",
            :friends [ "1000", "1002", "1003" ],
            :appearsIn [ 4, 5, 6 ],
            :primaryFunction "Astromech",
            })

(def droidData (atom {"2000" threepio
                      "2001" artoo}))

(defn get-human [id]
  (get @humanData (str id))) ; BUG: String should be parsed as string instead of int

(defn get-droid [id]
  (get @droidData (str id))) ; BUG: String should be parsed as string instead of int

(defn get-character [id]
  (or (get-human id) ; BUG: String should be parsed as string instead of int
      (get-droid id)))

(defn get-friends [character]
  (map get-character (:friends character)))

(defn get-hero [episode]
  (if (= episode 5)
    luke
    artoo))

(def human-id (atom 2050))

(defn create-human [args]
  (let [new-human-id (str (swap! human-id inc))
        new-human {:id new-human-id
                   :name (get args "name")
                   :friends (get args "friends")}]
    (swap! humanData assoc new-human-id new-human)
    new-human))

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
  (->> (type-names db)
       (map (fn [tn]
              (try
                (type-schema db tn)
                (catch Exception e (str tn " causes problem")))))))

(defn generate-query-schema [db]
  (->> (core-type-names db)
       (map #(format "%s(id: String!): %s"
                     (str/replace % #"-" "_")
                     (graphql-type-name %)))
       (str/join "\n  ")
       (format "
type Query {
  %s
}
")))

(defn parse-schema [& schema-parts]
  (->> (flatten schema-parts)
       (str/join "\n")
       (parser/parse)))

(defn starter-resolver-fn [type-name field-name]
  (let [db (d/db datomic-conn)]
    (match/match
     [type-name field-name]
     ["Query" "hero"] (fn [context parent args]
                        (get-hero (:episode args)))
     ["Query" "human"] (fn [context parent args]
                         (get-human (str (get args "id"))))
     ["Query" "droid"] (fn [context parent args]
                         (get-droid (str (get args "id"))))
     ["Query" "objectList"] (fn [context parent args]
                              (repeat 3 {:id (java.util.UUID/randomUUID)}))
     ["Query", _] (let [kwid (-> (str field-name "/id")
                                 (str/replace #"_" "-")
                                 (keyword))]
                    (if (d/entity db kwid)
                      (fn [context parent args]
                        (d/entity db [kwid (get args "id")]))))
     ;; Hacky!!! Should use resolver for interface
     ["Human" "friends"] (fn [context parent args]
                           (get-friends parent))
     ["Droid" "friends"] (fn [context parent args]
                           (get-friends parent))
     ["Character" "friends"] (fn [context parent args]
                               (get-friends parent))
     ;; ["Gene" "id"] (fn [context parent args]
     ;;                 (:gene/id parent))
     ;; ["Gene" "name"] (fn [context parent args]
     ;;                   (->> parent
     ;;                        (:gene/cgc-name)
     ;;                        (:gene.cgc-name/text)
     ;;                        (assoc {} :text)
     ;;                        ))
     ["Mutation" "createHuman"] (fn [context parent args]
                                  (create-human args))

     :else (let [attr-kw (datomic-field-name type-name field-name)]
             (if (has-attr db attr-kw)
               (fn [context parent args]
                 (attr-kw parent)))))))





;; (def introspection-schema introspection/introspection-schema)

(defn create-executor [db]
  (let [validated-schema
        (validator/validate-schema
         (parse-schema starter-type-schema
                       (generate-type-schema db)
                       (generate-query-schema db)
                       starter-schema))
        context nil]
    (fn [query variables]
          (executor/execute context validated-schema starter-resolver-fn query variables))))
