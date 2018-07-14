<template>
    <div>
        <input type="text" v-model.lazy="name" />
        <h1>{{greeting}}!!</h1>
    </div>
</template>

<script>
    import { Component, Watch } from 'vue-property-decorator';
    import fetch from 'unfetch';

    @Component()
    export default class App extends Vue {

        name = 'you';
        greeting = 'Hi you';

        @Watch('name')
        update() {
          //
          fetch(`/api/greet?name=${this.name}`).then(res => res.text())
            .then(g => this.greeting = g);
        }

    }
</script>