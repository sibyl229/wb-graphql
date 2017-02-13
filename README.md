### Introduction
The GraphQL API for WormBase data.

## Get started

#### Sample queries
```
query {
  gene(id: "WBGene00000426") {
    id
    biotype {
      name
    }
    concise_description {
      text
    }
    corresponding_transcript {
      transcript {
        id
      }
    }
    gene__OF__interaction__VIA__interactor_overlapping_gene {
      interactor_overlapping_gene__OF__interaction {
        id
        interactor_overlapping_gene {
          gene {
            id
            public_name
          }
        }
      }
    }
  }
}
```

Example with pagination:

```
query {
  getGenesByNames(names: "abi-1 xbp-1 ced-1 unc-89 unc-86 unc-87 unc-90 unc-95 unc-96 unc-93 unc-94 unc-77 unc-78 unc-75 unc-76 unc-79 unc-80 unc-84 unc-85 unc-82 unc-83 unc-64" cursor:"936783907216245") {
    hasNextPage
    endCursor
    edges {
      node {
        id
        public_name
        biotype {
          name
        }
      }
    }
  }
}
```

#### Sample mutations

N/A at the moment


## To contribute

### Obtain credentials ###
TODO...

### Prepare environment

    npm install

    npm run build

### Start server

    lein ring server-headless

### Access graphiql from

    http://localhost:3002/index.html

### Acknowledgement

This project was bootstrapped with [GraphQL starter project for Clojure](https://github.com/tendant/graphql-clj-starter), which uses [graphql-clj](https://github.com/tendant/graphql-clj), [GraphiQL](https://github.com/graphql/graphiql), and [Create React App](https://github.com/facebookincubator/create-react-app).
