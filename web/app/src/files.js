// The Vue build version to load with the `import` command
// (runtime-only or standalone) has been set in webpack.base.conf with an alias.
import Vue from 'vue'
import FileViewer from './components/FileViewer.vue'
import Status from './status'

Vue.config.productionTip = false;

/* eslint-disable no-new */
new Vue({
  el: '#app',
  template: '<FileViewer/>',
  components: { FileViewer }
});

Status();
