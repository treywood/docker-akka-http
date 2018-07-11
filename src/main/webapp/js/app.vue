<template>
    <h1>Hello from {{name}}</h1>
</template>

<script>
    import { Component } from 'vue-property-decorator';

    @Component()
    export default class App extends Vue {

        name = 'Vue!!'

    }
</script>