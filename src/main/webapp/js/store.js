import Vuex from 'vuex';

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
      const todos = await fetch('/api/todo').then(r => r.json());
      commit('reset', todos);
    }
  }

})