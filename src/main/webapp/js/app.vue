<template>
    <h1>Hello from {{name}}!!</h1>
</template>

<script>
    import { Component } from 'vue-property-decorator';
    import fetch from 'unfetch';

    @Component()
    export default class App extends Vue {

        name = '...';

        mounted() {
          fetch('/datas').then(res => res.json())
            .then(({ name }) => this.name = name)
        }

    }
</script>