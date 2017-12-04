import Vue from 'vue'
import Workflows from './components/Workflows.vue'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Workflows/>',
  components: { Workflows }
});
