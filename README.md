### Introduction
The GraphQL API and IDE for WormBase data in Datomic database.

## Get started
Here is a web tool I made for exploring data in our Datomic database, and fetching just the fields you need on any entity/object. All of these through the website or its web API. This tool implements the GraphQL API standard, which is designed for ease of data exchange over the web.

So far, I made this tool to work with two types of queries (or access point), one is to get a list of genes based on a list of gene names, the other is to get any entity by its WormBase ID. Within each query, you will specify which fields you like to fetch, and you will get results that mirror the shape of the query.


#### Sample queries

Here are some example query:
* a simple query to get the concise description of a gene
```
{
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
{
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
{
  getGenesByNames(names: "abi-1 xbp-1 ced-1 unc-89 unc-86 unc-87 unc-90 unc-95 unc-96 unc-93 unc-94 unc-77 unc-78 unc-75 unc-76 unc-79 unc-80 unc-84 unc-85 unc-82 unc-83 unc-64" cursor:"936783907210927") {
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


## To contribute

### Obtain credentials ###
TODO...

### Prepare environment

    npm install

    npm run build

### Start server

    lein ring server-headless [YOUR_PORT_NO]

### Access graphiql from

    http://localhost:[YOUR_PORT_NO]/index.html

### Acknowledgement

This project was bootstrapped with [GraphQL starter project for Clojure](https://github.com/tendant/graphql-clj-starter), which uses [graphql-clj](https://github.com/tendant/graphql-clj), [GraphiQL](https://github.com/graphql/graphiql), and [Create React App](https://github.com/facebookincubator/create-react-app).
