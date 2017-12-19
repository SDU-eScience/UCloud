import Vue from 'vue'
import Applications from './components/Applications.vue'
import Status from './status'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Applications/>',
  components: {Applications}
});

Status();
