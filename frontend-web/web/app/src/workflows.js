import Vue from 'vue'
import Workflows from './components/Workflows.vue'
import Status from './status'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Workflows/>',
  components: { Workflows }
});

Status();
