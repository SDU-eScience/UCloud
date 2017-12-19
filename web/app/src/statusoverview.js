import Vue from 'vue'
import StatusOverview from './components/StatusOverview'

Vue.config.productionTip = false;

/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<StatusOverview/>',
  components: {StatusOverview }
});
