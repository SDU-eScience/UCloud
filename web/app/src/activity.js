import Vue from 'vue'
import Activity from './components/Activity.vue'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Activity/>',
  components: {Activity}
});
