<template>
    <div>
        <ul>
            <li v-for="todo in todos" v-key="todo.id">
                {{todo.label}}
            </li>
        </ul>
        <form @submit.prevent="add">
            <input v-model="label" type="text" />
            <button type="submit">Add</button>
        </form>
    </div>
</template>

<script>
    import { Component, Watch } from 'vue-property-decorator';

    import store from './store';

    @Component({ store })
    export default class App extends Vue {

        label = '';

        mounted() {
          this.$store.dispatch('subscribe');
        }

        get todos() {
          return this.$store.state.todos;
        }

        add() {
          this.$store.dispatch('add', this.label)
            .then(() => {
                this.label = '';
            })
        }

    }
</script>