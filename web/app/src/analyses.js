import Vue from 'vue'
import Analyses from './components/Analyses.vue'
import Status from './status'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Analyses/>',
  components: {Analyses}
});

Status();
