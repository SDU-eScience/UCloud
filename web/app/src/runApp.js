import Vue from 'vue'
import RunAppComponent from './components/RunAppComponent.vue'
import $ from 'jquery'

Vue.config.productionTip = false;


/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<RunAppComponent :application="application"/>',
  components: {RunAppComponent},
  data: {
    application: {}
  },
  mounted() {
    $.getJSON('/api/getApplicationInfo', {
      name: window.application.name,
      version: window.application.version
    }).then((application) => {
      this.application = application
    });
  }
});
