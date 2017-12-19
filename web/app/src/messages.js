import Vue from 'vue'
import Messages from './components/Messages'
import Status from './status'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Messages/>',
  components: {Messages}
});

Status();
