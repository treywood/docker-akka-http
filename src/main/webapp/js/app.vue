<template>
    <div>
        <form novalidate class="md-layout" @submit.prevent="add">
            <md-card class="md-layout-item">
                <md-card-header>
                    <div class="md-title">To Dos</div>
                </md-card-header>
                <md-card-content>
                    <md-list>
                        <md-list-item v-for="todo in todos" :key="todo.id">
                            <md-checkbox :checked="todo.done" @change="update(todo)">{{todo.label}}</md-checkbox>
                        </md-list-item>
                    </md-list>
                    <md-field>
                        <label>New Item</label>
                        <md-input v-model="label" />
                    </md-field>
                </md-card-content>
                <md-card-actions>
                    <md-button type="submit" class="md-primary">Add</md-button>
                </md-card-actions>
            </md-card>
        </form>
    </div>
</template>

<script>
    import { Component } from 'vue-property-decorator';
    import store from './store';

    @Component({ store })
    export default class App extends Vue {

        label = '';

        mounted() {
          this.$store.dispatch('fetch');
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

        update(item) {
          this.$store.dispatch('toggle', item);
        }

    }
</script>