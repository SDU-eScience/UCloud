import Vue from 'vue'
import Applications from './components/Applications.vue'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Applications/>',
  components: {Applications}
});
