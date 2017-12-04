<template>
  <div>
    <div class="btn btn-default" v-on:click="show">Browse</div><div v-if="selectedFile">Currently selected file: {{ selectedFile.path.name }}</div>
    <div ref="root" tabindex="-1" role="dialog" v-bind:class="{ hidden: !isShown }" class="modal modal-center"
         style="display: block;">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <div class="modal-header">
            <div data-dismiss="modal" class="pull-right clickable"><em class="ion-close-round text-soft" v-on:click="hide()"></em></div>
            <ol v-cloak class="breadcrumb">
              <li v-for="breadcrumb in breadcrumbs" class="breadcrumb-item"><a
                v-on:click="changePath(breadcrumb.second)"> {{breadcrumb.first}}</a></li>
            </ol>
            <h4 class="modal-title"><span>File selector</span></h4>
          </div>
          <div class="modal-body pre-scrollable">
            <table class="table-datatable table table-striped table-hover mv-lg">
              <thead>
              <tr>
                <th>Filename</th>
              </tr>
              </thead>
              <tbody>
              <tr class="gradeA row-settings" v-for="file in files">
                <td v-bind:class="computeIcon(file)" v-on:click="handleSelection(file)"> {{ file.path.name }}</td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import $ from 'jquery'

  export default {
    name: 'file-selector',

    data() {
      return {
        path: '',
        files: [],
        breadcrumbs: [],
        isShown: false,
        selectedFile: null
      }
    },
    mounted() {
      $.getJSON("/api/getFiles", {path: this.path}).then((files) => {
        this.files = files;
      });
      $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
        this.breadcrumbs = breadcrumbs
      });
    },
    watch: {
      path: function () {
        $.getJSON('/api/getFiles', {path: this.path}).then((files) => {
          this.files = files;
        });
        $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
          this.breadcrumbs = breadcrumbs
        });
      }
    },
    methods: {
      changePath: function (newPath) {
        this.path = newPath;
      },
      hide: function () {
        this.isShown = false;
      },
      setFile: function (file) {
        this.selectedFile = file;
        this.$emit('select', file);
        this.hide();
      },
      show: function () {
        this.isShown = true;
      },
      computeIcon: function (file) {
        if (file.type === "DIRECTORY") return "ion-folder";
        else return "ion-filing";
      },
      handleSelection: function (file) {
        if (file.type === "DIRECTORY") {
          this.changePath(file.path.path);
        } else {
          this.setFile(file);
          this.hide();
        }
      }
    }
  }
</script>
