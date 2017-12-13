import Vue from 'vue'
import Projects from './components/Projects'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Projects/>',
  components: { Projects }
});
