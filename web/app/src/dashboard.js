import Vue from 'vue'
import DashboardComponent from './components/Dashboard.vue'
import Status from './status'


Vue.config.productionTip = false;

/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<DashboardComponent/>',
  components: {DashboardComponent}
});

Status();
