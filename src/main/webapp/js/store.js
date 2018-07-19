import Vuex from 'vuex';

import ApolloClient from 'apollo-client';
import { createHttpLink } from 'apollo-link-http';
import { InMemoryCache } from 'apollo-cache-inmemory';
import { GetToDos, AddItem, DeleteItem, UpdateItem, WatchToDos } from './queries.graphql';
import fetch from 'unfetch';
import { WebSocketLink } from 'apollo-link-ws';
import { SubscriptionClient } from 'subscriptions-transport-ws';
import { RetryLink } from 'apollo-link-retry';
import find from 'lodash/find';

Vue.use(Vuex);

const http = createHttpLink({
  uri: '/graphql',
  fetch: fetch,
  fetchOptions: {
    fetchPolicy: 'no-cache'
  }
});

const ws =  new WebSocketLink(
  new SubscriptionClient('ws://localhost:8080/graphql', { reconnect: true })
);


const apollo = new ApolloClient({
  link: new RetryLink().split(
    ({ query }) => {
      const main = find(query.definitions, { kind: 'OperationDefinition' });
      return main && main.operation === 'subscription'
    },
    ws,
    http
  ),
  cache: new InMemoryCache()
});

export default new Vuex.Store({

  state: {
    todos: []
  },

  mutations: {
    reset: (state, items) => {
      state.todos = items;
    }
  },

  actions: {
    subscribe({ commit }) {
      return apollo.subscribe({ query: WatchToDos }).subscribe(({ data }) => {
        commit('reset', data.todos);
      });
    },
    add({ commit }, label) {
      const variables = { label };
      return apollo.mutate({ mutation: AddItem, variables }).then(({ data }) => {
        return data.item;
      });
    },
    update({ dispatch }, item) {
      const variables = { id: item.id, done: item.done };
      return apollo.mutate({ mutation: UpdateItem, variables }).then(({ data }) => {
        return data.item;
      });
    },
    toggle({ dispatch }, item) {
      return dispatch('update', { ...item, done: !item.done });
    }
  }

})