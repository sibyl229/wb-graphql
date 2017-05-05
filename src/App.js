import React, { Component } from 'react';
// import logo from './logo.svg';
import './graphiql.css';
import GraphiQL from 'graphiql';
import fetch from 'isomorphic-fetch';

class App extends Component {
  graphQLFetcher(graphQLParams) {
    const urlPath = window.location.pathname;
    const graphqlPath = (urlPath.replace(/\/index.html$/, "") || "/");
    console.log(graphqlPath);
    return fetch(graphqlPath, {
      method: 'post',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify(graphQLParams)
    }).then(response => response.json());
  }

  render() {
    return (
        <GraphiQL fetcher={this.graphQLFetcher} />
    );
  }
}

export default App;
