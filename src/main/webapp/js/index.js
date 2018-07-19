import 'vue-material/dist/vue-material.min.css';
import 'vue-material/dist/theme/default.css';

import VueMaterial from 'vue-material';
import App from './app.vue';

Vue.use(VueMaterial);

new App({
    el: '#app'
});

window.SOCKETS = [];

function disconnect() {
  console.log('disconnect');
  for (const s of window.SOCKETS) {
    s.unsubscribe();
  }
  return 'sure?';
}

window.addEventListener('beforeunload', disconnect);