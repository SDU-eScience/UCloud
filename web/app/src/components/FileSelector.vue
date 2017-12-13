<template>
  <div>
    <div class="btn btn-default" v-on:click="show">Browse</div>
    <div v-if="selectedFile">Currently selected file: {{ selectedFile.path.name }}</div>
    <div ref="root" tabindex="-1" role="dialog" v-bind:class="{ hidden: !isShown }" class="modal modal-center"
         style="display: block;">
      <div class="modal-dialog modal-lg">
        <div class="modal-content">
          <loading-icon v-if="loading"></loading-icon>
          <div class="modal-header">
            <div data-dismiss="modal" class="pull-right clickable"><em class="ion-close-round text-soft"
                                                                       v-on:click="hide()"></em></div>
            <ol v-cloak class="breadcrumb">
              <li v-for="breadcrumb in breadcrumbs" class="breadcrumb-item"><a
                v-on:click="changePath(breadcrumb.second)"> {{breadcrumb.first}}</a></li>
            </ol>
            <h4 class="modal-title"><span>File selector</span></h4>
          </div>
          <div v-if="!loading" class="modal-body pre-scrollable">
            <h4 v-if="!files.length">
              <small>No files in current folder.</small>
            </h4>
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
          <div class="modal-body">
            <button class="btn btn-info" :disabled="!isShown" @click="createFolder">Create new folder</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import $ from 'jquery'
  import swal from 'sweetalert2'
  import LoadingIcon from "./LoadingIcon";

  export default {
    components: {LoadingIcon},
    name: 'file-selector',

    data() {
      return {
        path: '/',
        files: [],
        loading: true,
        breadcrumbs: [],
        isShown: false,
        selectedFile: null
      }
    },
    mounted() {
      $.getJSON("/api/getFiles", {path: this.path}).then((files) => {
        this.files = files.sort((a, b) => {
          if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
            return -1;
          else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
            return 1;
          else {
            return a.path.name.localeCompare(b.path.name);
          }
        });
        this.loading = false;
      });
      $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
        this.breadcrumbs = breadcrumbs
      });
      window.addEventListener('keydown', this.escapeKeyListener);
    },
    watch: {
      path: function () {
        this.loading = true;
        this.files = [];
        $.getJSON('/api/getFiles', {path: this.path}).then((files) => {
          this.files = files.sort((a, b) => {
            if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
              return -1;
            else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
              return 1;
            else {
              return a.path.name.localeCompare(b.path.name);
            }
          });
          this.loading = false;
        });
        $.getJSON("/api/getBreadcrumbs", {path: this.path}).then((breadcrumbs) => {
          this.breadcrumbs = breadcrumbs
        });
      }
    },
    methods: {
      escapeKeyListener($event) {
        if ($event.key === 'Escape' && this.isShown) {
          this.hide()
        }
      },
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
        else return "ion-android-document";
      },
      handleSelection: function (file) {
        if (file.type === "DIRECTORY") {
          this.changePath(file.path.path);
        } else {
          this.setFile(file);
          this.hide();
        }
      },
      createFolder() {
        let currentDir = this.breadcrumbs[this.breadcrumbs.length - 1].second;
        swal({
          title: "Create a folder",
          text: "Input the folder name:",
          input: "text",
          showCancelButton: true,
          inputPlaceholder: "Folder name...",
          preConfirm: (text) => {
            if (text === "")
              swal.showValidationError("You need to enter a folder name.");
          }
        }).then((inputValue) => {
          if (inputValue.dismiss !== 'cancel') {
            $.getJSON("/api/createDir", {dirPath: currentDir + inputValue.value}, (result) => {
              if (result === 200) {
                swal("Success", "Folder " + currentDir + inputValue.value + " created", "success");
              } else {
                swal("Error", "Folder " + currentDir + inputValue.value + " was not created", "error");
              }
            });
          }
        });
      }
    }
  }
</script>


