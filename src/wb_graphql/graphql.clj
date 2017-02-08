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

(def starter-schema "enum Episode { NEWHOPE, EMPIRE, JEDI }

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

type Gene {
  id: String!
  name: GeneCgc_name
}

type GeneCgc_name {
  text: String
}

type Query {
  hero(episode: Episode): Character
  human(id: String!): Human
  droid(id: String!): Droid
  hello(world: WorldInput): String
  objectList: [Object!]!
  gene(id: String!): Gene
}

type Object {
  id: String!
}

input WorldInput {
  text: String
}

type Mutation {
  createHuman(name: String, friends: [String]): Human
}

schema {
  query: Query
  mutation: Mutation
}")

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
     ["Query", "gene"] (fn [context parent args]
                         (d/entity db [:gene/id (get args "id")]))
     ;; Hacky!!! Should use resolver for interface
     ["Human" "friends"] (fn [context parent args]
                           (get-friends parent))
     ["Droid" "friends"] (fn [context parent args]
                           (get-friends parent))
     ["Character" "friends"] (fn [context parent args]
                               (get-friends parent))
     ["Gene" "id"] (fn [context parent args]
                     (:gene/id parent))
     ["Gene" "name"] (fn [context parent args]
                       (->> parent
                            (:gene/cgc-name)
                            (:gene.cgc-name/text)
                            (assoc {} :text)
                            ))
     ["Mutation" "createHuman"] (fn [context parent args]
                                  (create-human args))
     :else nil)))

(def parsed-schema (parser/parse starter-schema))

(defn type-names [db]
  (d/q '[:find [?ns ...]
         :where
         [?e :db/ident ?ident]
         [_ :db.install/attribute ?e]
         [(namespace ?ident) ?ns]
           (not-join [?ns]
                     [_ :pace/use-ns ?ns])]
       db))

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

(defn field-type [field-entity]
  (case (:db/valueType field-entity)
    :db.type/string "String"
    :db.type/boolean "Boolean"
    :db.type/long "Int"
    :db.type/float "Float"
    :db.type/double "Float"
    :db.type/ref (if (:db/isComponent field-entity)
                   (->> (:db/ident field-entity)
                        (component-name)
                        (graphql-type-name))
                   (->> (:pace/obj-ref field-entity)
                        (namespace)
                        (graphql-type-name)))))

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

(defn type-schema [db type-name]
  (if-let [attrs
           (seq (d/q '[:find [?ident ...]
                       :in $ ?ns
                       :where
                       [?e :db/ident ?ident]
                       [_ :db.install/attribute ?e]
                       [(namespace ?ident) ?ns]]
                     db (str type-name)))]
    (format "type %s {
%s
}"
            (graphql-type-name type-name)
            (->> attrs
                 (map (partial field-schema db))
                 (map (partial str "  "))
                 (str/join "\n")))))

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




;; (def introspection-schema introspection/introspection-schema)

(defn execute
  [query variables]
  (let  [type-schema (validator/validate-schema parsed-schema)
         context nil]
    (executor/execute context type-schema starter-resolver-fn query variables)))
