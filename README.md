### Introduction
The GraphQL API and IDE for WormBase data in Datomic database.

## Get started
Here is a web tool I made for exploring data in our Datomic database, and fetching just the fields you need on any entity/object. All of these through the website or its web API. This tool implements the GraphQL API standard, which is designed for ease of data exchange over the web.

So far, I made this tool to work with two types of queries (or access point), one is to get a list of genes based on a list of gene names, the other is to get any entity by its WormBase ID. Within each query, you will specify which fields you like to fetch, and you will get results that mirror the shape of the query.


#### Sample queries

Here are some example query:
* a simple query to get the concise description of a gene
```
query {
  gene(id: "WBGene00000426") {
    concise_description {
      text
    }
  }
}
```

* a more complex query to get genes that interact with the gene being queried
(apology for the long feild name. Please use auto-complete **control-space** to help with long field names.)

```
query {
  gene(id: "WBGene00000426") {
    public_name
    gene__OF__interaction__VIA__interactor_overlapping_gene {
      interactor_overlapping_gene__OF__interaction {
        id
        interactor_overlapping_gene {
          gene {
            public_name
          }
        }
      }
    }
  }
}
```

* get a list of genes by their names and get their CDS IDs ([pagination through cursor](http://graphql.org/learn/pagination/))
```
query {
  getGenesByNames(names: ["abi-1", "xbp-1", "ced-1", "unc-89", "unc-86", "unc-87", "unc-90", "unc-95", "unc-96", "unc-93", "unc-94", "unc-77", "unc-78", "unc-75", "unc-76", "unc-79", "unc-80", "unc-84", "unc-85", "unc-82", "unc-83", "unc-64"], after: "-1") {
    hasNextPage
    endCursor
    edges {
      cursor
      node {
        id
        public_name
        corresponding_cds {
          cds {
            id
          }
        }
      }
    }
  }
}
```


#### Sample mutations

N/A at the moment

### FAQ and tips
* Dontrol-space will open up auto-completion. This is useful for composing a GraphQL query.
* Docs can be accessed from the top right corner

* What queries are available?
> Search the Docs for "query", and select Query in the result to access the index of all available queries.

* How to find out the data type of a query's argument or return value?
> Search the Docs (refer above) by the query name, such as gene, getGenesByNames, etc. Look for results with "gene on Query", "getGenesByNames on Query" to access documentation on the particular query. Finally, check under "Arguments" for type of the arguments, and "Type" for type of the return value. Do

* What fields available for fetching?
> Find out the return value type of your query (refer above), access the docs for the return type, look under "Fields" for all the fields available for fetching on the current object and the data type of the field. Subfields can be fetched through nesting (refer to examples la)


## Use wb-graphql as a library

wb-graphql provides ring handlers that can be combined with a ring web service.

Include the following in the your project.clj dependencies:

```clojure
[wormbase/wb-graphql "0.1.0-SNAPSHOT"]
[com.datomic/datomic-pro "0.9.5554" :exclusions [joda-time]]
[com.amazonaws/aws-java-sdk-dynamodb "1.11.6" :exclusions [joda-time]]

```

Create a ring handler with wb-graphql

```clojure
(ns rest-api.routing
  (:require
   [datomic.api :as d]
   [wb-graphql.handler]))

(def db (d/db datomic-conn))

(defn graphql-routes [request]
  (let [handler (-> (wb-graphql.handler/create-routes)
                    (wb-graphql.handler/wrap-app db))]
    (handler request)))
```

## Run wb-graphql as a standalone web service

Locate the standlone uberjar, or refer [here to build a standalone uberjar](#build-standalone-uberjar).

    jar -jar path/to/standalone-uberjar.jar

Or with appropriate environment variable

    PORT=[Your_Port] WB_DB_URI=[Your_Datomic_URI] jar -jar path/to/standalone-uberjar.jar

## To contribute

### Obtain credentials ###
TODO...

### Prepare environment

    npm install

    npm run build

### Start server

    lein ring server-headless [YOUR_PORT_NO]

### Access graphiql from

    http://localhost:[YOUR_PORT_NO]

### Build jar

to use as a library

    npm run build    # build static resources
    lein run    # build graphql schema
    lein jar    # build jar
    lein deploy tmp   # deploy to local repository for testing

### Build standalone uberjar

to run as a server

    npm run build    # build static resources
    lein run    # build graphql schema
    lein ring uberjar    # build standalone uberjar


### Acknowledgement

This project was bootstrapped with [GraphQL starter project for Clojure](https://github.com/tendant/graphql-clj-starter), which uses [graphql-clj](https://github.com/tendant/graphql-clj), [GraphiQL](https://github.com/graphql/graphiql), and [Create React App](https://github.com/facebookincubator/create-react-app).
