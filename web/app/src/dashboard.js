import Vue from 'vue'
import DashboardComponent from './components/Dashboard.vue'

Vue.config.productionTip = false;

/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<DashboardComponent/>',
  components: {DashboardComponent} // TODO move to component
});
