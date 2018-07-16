import Vuex from 'vuex';

import ApolloClient from 'apollo';
import { createHttpLink } from 'apollo-link-http';
import { InMemoryCache } from 'apollo-cache-inmemory';
import { GetToDos } from './queries.graphql';

const apollo = new ApolloClient({
  link: createHttpLink({
    uri: '/graphql',
    fetchOptions: {
      fetchPolicy: 'no-cache'
    }
  }),
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
    remove: (state, id) => {
      state.todos = state.todos.filter(i => i.id !== id)
    },
    update: (state, item) => {
      state.todos = state.todos.map(i => i.id === item.id ? item : i);
    }
  },

  actions: {
    async fetch({ commit }) {
      return apollo.query({ query: GetToDos }).then(({ data }) => {
        commit('reset', data.todos);
        return data.todos;
      });
    }
  }

})