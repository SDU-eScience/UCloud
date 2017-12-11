import Vue from 'vue'
import Notifications from './components/Notifications'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Notifications/>',
  components: { Notifications }
});
