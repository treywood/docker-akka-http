import Vuex from 'vuex';

import ApolloClient from 'apollo-client';
import { createHttpLink } from 'apollo-link-http';
import { InMemoryCache } from 'apollo-cache-inmemory';
import { GetToDos, AddItem, DeleteItem, UpdateItem, UpdatedToDos, NewToDos, DeletedToDos } from './queries.graphql';
import { merge } from 'rxjs/observable/merge';
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

const ws = new WebSocketLink(
  new SubscriptionClient('ws://localhost:8888/graphql', { reconnect: true })
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
    },
    add: (state, item) => {
      state.todos = [...state.todos, item];
    },
    delete: (state, item) => {
      state.todos = state.todos.filter(i => i.id !== item.id);
    },
    update: (state, item) => {
      state.todos = state.todos.map(i => i.id === item.id ? item : i);
    }
  },

  actions: {
    fetch({ commit, dispatch }) {
      return apollo.query({ query: GetToDos }).then(({ data }) => {
        commit('reset', data.todos);
        return dispatch('watch');
      });
    },
    watch({ commit }) {
      const sub = merge(
        apollo.subscribe({ query: NewToDos }),
        apollo.subscribe({ query: UpdatedToDos }),
        apollo.subscribe({ query: DeletedToDos })
      ).subscribe(({ data }) => {
        if (data.new) return commit('add', data.new);
        if (data.updated) return commit('update', data.updated);
        if (data.deleted) return commit('delete', data.deleted);
      });
      window.SOCKETS.push(sub);
      return sub;
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
    },
    delete({ commit }, item) {
      const variables = { id: item.id };
      return apollo.mutate({ mutation: DeleteItem, variables }).then(({ data }) => {
        return data.deleted;
      });
    }
  }

})