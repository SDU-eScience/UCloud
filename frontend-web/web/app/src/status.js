import Vue from 'vue'
import Status from './components/Status.vue'

Vue.config.productionTip = false;


/* eslint-disable no-new */
export default function() {
  new Vue({
    el: '#status',
    template: '<Status/>',
    components: {Status}
  });
}
