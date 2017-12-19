import Vue from 'vue'
import Projects from './components/Projects'
import Status from './status'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Projects/>',
  components: { Projects }
});

Status();
