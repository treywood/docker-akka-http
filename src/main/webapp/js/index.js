import 'vue-material/dist/vue-material.min.css';
import 'vue-material/dist/theme/default.css';

import VueMaterial from 'vue-material';
import App from './app.vue';

Vue.use(VueMaterial);

new App({
    el: '#app'
});