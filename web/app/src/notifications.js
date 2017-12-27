import Vue from 'vue';
import Notifications from './components/Notifications';
import Status from './status';

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<Notifications/>',
  components: { Notifications }
});

Status();
