<template>
  <section>
    <div class="container-fluid">
      <div class="col-lg-10">
        <ol class="breadcrumb">
          <li v-for="breadcrumb in breadcrumbs" class="breadcrumb-item">
            <router-link :to="{ path: breadcrumb.second }" append>{{breadcrumb.first}}</router-link></li>
        </ol>
        <div :class="{ hidden: !loading }" class="card-body">
          <h1>Loading files...</h1>
          <div class="row loader-primary">
              <div class="loader-demo">
                <div class="loader-inner pacman">
                  <div></div>
                  <div></div>
                  <div></div>
                  <div></div>
                  <div></div>
                </div>
              </div>
              <!-- Fallback -->
              <!--<div class="loader-demo">
                <div class="loader-inner ball-pulse"><div></div><div></div><div></div></div>
              </div>-->
          </div>
        </div>
        <div v-cloak class="card" v-if="files.length && !loading">
          <div class="card-body">
            <table class="table-datatable table table-striped table-hover mv-lg">
              <thead>
              <tr>
                <th class="select-cell disabled">
                  <label class="mda-checkbox">
                    <input name="select" class="select-box"
                           v-model="masterCheckbox"
                           v-on:change="onMasterCheckboxChange"
                           value="all"
                           type="checkbox"><em
                    class="bg-info"></em></label></th>
                <th @click="orderByName">Filename</th>
                <th @click="orderByFavourite"><a class="ion-star"></a></th>
                <th @click="orderByDate" class="sort-numeric">Modified</th>
                <th @click="orderByOwner" class="sort-alpha">File Owner</th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="row-settings clickable-row" v-for="file in files" @click="openFile(file)">
                <td class="select-cell" style=""><label class="mda-checkbox"><input
                  name="select" class="select-box" :value="file" v-model="selectedFiles" type="checkbox"><em
                  class="bg-info"></em></label></td>
                <td v-if="file.type === 'FILE'">
                  <a class="ion-android-document"></a> {{ file.path.name }}
                </td>
                <td v-else><a class="ion-android-folder"></a> {{ file.path.name }}</td>
                <td v-if="file.isStarred"><a @click="favourite(file)" class="ion-star"></a></td>
                <td v-else><a class="ion-star" v-on:click="favourite(file.path.path, $event)"></a></td>
                <td>{{ new Date(file.modifiedAt).toLocaleString() }}</td>
                <td>{{ file.acl.length > 1 ? file.acl.length + ' collaborators' : file.acl[0].right }}</td>
                <td>
                  <span class="hidden-lg">
                      <div class="pull-right dropdown">
                          <button type="button" data-toggle="dropdown"
                                  class="btn btn-flat btn-flat-icon"
                                  aria-expanded="false"><em
                            class="ion-android-more-vertical"></em></button>
                          <ul role="menu" class="dropdown-menu md-dropdown-menu dropdown-menu-right">
                              <li><a class="btn btn-info ripple btn-block"
                                     v-on:click="sendToAbacus()"> Send to Abacus 2.0</a></li>
                              <li><a class="btn btn-default ripple btn-block ion-share"
                                     v-on:click="shareFile(file.path.name, 'file')"> Share file</a></li>
                              <li><a class="btn btn-default ripple btn-block ion-ios-download"> Download file</a></li>
                              <li><a class="btn btn-default ripple ion-ios-photos"> Move file</a></li>
                              <li><a class="btn btn-default ripple ion-ios-compose"
                                     v-on:click="renameFile(file.path.name, 'file')"> Rename file</a></li>
                              <li><a class="btn btn-danger ripple ion-ios-trash"
                                     v-on:click="showFileDeletionPrompt(file.path.name, file.path)"> Delete file</a></li>
                          </ul>
                      </div>
                  </span>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div class="col-lg-2 visible-lg">
        <div>
          <ol v-cloak class="icons-list">
            <li data-pack="default" class="ion-star" v-on:click="getFavourites()"></li>
            <li data-pack="default" class="ion-ios-home" onclick="location.href='/files#/'"></li>
            <hr>
          </ol>
          <button class="btn btn-primary ripple btn-block ion-android-upload"> Upload Files</button>
          <br>
          <button class="btn btn-default ripple btn-block ion-folder" v-on:click="createFolder"> New
            folder
          </button>
          <br>
          <hr>
          <h3 v-if="selectedFiles.length" v-cloak>
            {{ 'Rights level: ' + options.rightsName }}<br>
            {{ selectedFiles.length > 1 ? selectedFiles.length + ' files selected.' : selectedFiles[0].path.name }}</h3>
          <div v-if="selectedFiles.length" v-cloak> <!-- TODO Are separate options necessary? -->
              <p><button class="btn btn-info rippple btn-block"
                    v-on:click="sendToAbacus()"> Send to Abacus 2.0</button></p>
              <p><button type="button" class="btn btn-default ripple btn-block ion-share"
                    v-on:click="shareFile(selectedFiles[0].path.name, 'folder')"> Share selected files</button></p>
              <p><button class="btn btn-default ripple btn-block ion-ios-download"> Download selected files</button></p>
              <p><button type="button" class="btn btn-default btn-block ripple ion-android-star"> Favourite selected files</button>
              </p>
              <p><button class="btn btn-default btn-block ripple ion-ios-photos"> Move folder</button></p> <!-- TODO When is this allowed? -->
              <p><button type="button" class="btn btn-default btn-block ripple ion-ios-compose"
                    v-on:click="renameFile(selectedFiles[0].path.name, 'folder')"  :disabled="options.rightsLevel < 3 || selectedFiles.length !== 1"> Rename file</button></p>
              <p><button class="btn btn-danger btn-block ripple ion-ios-trash" :disabled="options.rightsLevel < 3"
                    v-on:click="showFileDeletionPrompt(selectedFiles[0].path.name, selectedFiles[0].path)">
                Delete selected files</button></p>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import swal from 'sweetalert2'
  import Vue from 'vue'
  import VueRouter from 'vue-router'

  Vue.use(VueRouter);

  export default {
    name: 'file-viewer',

    data() {
      return {
        files: [],
        selectedFiles: [],
        breadcrumbs: [],
        loading: true,
        masterCheckbox: false,
        sortOrders: {
          filename: true,
          favourite: true,
          lastModified: true,
          owners: true
        },
        buttonTitles: {
          share: {
            normal: 'Click to share the selected files.',
            lowRightsLevel: 'You do not have the rights to rename this file',
            multipleFilesSelected: 'You can only rename one file at a time. Please only select one file to rename.'
          },
          delete: {
            normal: 'Click to delete the selected files',
            lowRightsLevel: 'You do not have the rights to delete the selected files.'
          },
          move: {
            normal: 'Click to move the selected files to a different location',
            lowRightsLevel: 'You do not have the rights to move the selected files'
          }
        },
        rightsMap: {
          'NONE': 1,
          'READ': 2,
          'READ_WRITE': 3,
          'OWN': 4
        }
      }
    },
    mounted() {
      this.getFiles(this.$route.path);
    },
    router: new VueRouter(),
    computed: {
      options() {
        if (!this.selectedFiles.length) return 0;
        let lowestPrivilegeOptions = this.rightsMap['OWN'];
        this.selectedFiles.forEach((it) => {
          it.acl.forEach((acl) => {
            lowestPrivilegeOptions = Math.min(this.rightsMap[acl.right], lowestPrivilegeOptions);
          });
        });
        return {
          rightsName: Object.keys(this.rightsMap)[lowestPrivilegeOptions - 1],
          rightsLevel: lowestPrivilegeOptions
        }
      },
      isEmptyFolder() {
        return this.files.length === 0;
      }
    },
    watch: {
      '$route'(to, fr) {
        this.getFiles(to.path);
      }
    },
    methods: {
      clickRow(clickedFile) {
        let previousLength = this.selectedFiles.length;
        this.selectedFiles = this.selectedFiles.filter((file) => {
          return file.path.uri !== clickedFile.path.uri
        });
        if (this.selectedFiles.length === previousLength) {
          this.selectedFiles.push(clickedFile)
        }
      },
      openFile(file, $event) {
        if (file.type === 'DIRECTORY') {
          window.location.hash = file.path.path
        }
      },
      getFavourites() {
        $.getJSON("/api/getFavourites").then((files) => {
          this.files = files;
          this.breadcrumbs = [{
            first: "home",
            second: ""
          }];
          this.masterCheckbox = false;
          this.selectedFiles = [];
        });
      },
      onMasterCheckboxChange() {
        if (this.masterCheckbox) {
          this.selectedFiles = this.files;
        } else {
          this.selectedFiles = [];
        }
      },
      getBreadcrumbs(path) {
        $.getJSON("/api/getBreadcrumbs", {path: path}).then((breadcrumbs) => {
          if (breadcrumbs.length) {
            this.breadcrumbs = breadcrumbs;
          } else {
            this.breadcrumbs = [];
          }
        });
      },
      favourite(path, $event) {
        $event.stopPropagation();
        $.getJSON("/api/favouriteFile", {path: path}).then((success) => {
          if (success === 200) {
            console.log("Favouriting files doesn't work yet.")
          }
        }).fail(function () {
          swal('An Error Occurred', 'Something went wrong, please try again later', 'error');
        });
      },
      getFiles(path) {
        this.loading = true;
        $.getJSON("/api/getFiles", {path: path}).then((files) => {
          this.files = files
          this.orderByName()
          this.loading = false;
          this.masterCheckbox = false;
          this.selectedFiles = [];
          this.getBreadcrumbs(path);
        });
      },
      orderByName() {
        let order = this.sortOrders.name ? 1 : -1;
        this.sortOrders.name = !this.sortOrders.name;
        this.files.sort((a, b) => {
          if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
            return -1 * order;
          else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
            return 1 * order;
          else {
            return a.path.name.localeCompare(b.path.name) * order;
          }
        });
      },
      orderByFavourite() {
        let order = this.sortOrders.favourite ? 1 : -1;
        this.sortOrders.favourite = !this.sortOrders.favourite;
        this.files.sort((a, b) => {
          if (a.favourite && b.favourite) {
            return 0;
          } else if (a.favourite) {
            return 1 * order;
          } else {
            return -1 * order;
          }
        });
      },
      orderByDate() {
        let order = this.sortOrders.lastModified ? 1 : -1;
        this.sortOrders.lastModified = !this.sortOrders.lastModified;
        this.files.sort((a, b) => {
          if (a.modifiedAt === b.modifiedAt) {
            return 0;
          } else if (a.modifiedAt < b.modifiedAt) {
            return 1 * order;
          } else {
            return -1 * order;
          }
        });
      },
      orderByOwner() {
        let order = this.sortOrders.owners ? 1 : -1;
        this.sortOrders.owners = !this.sortOrders.owners
        this.files.sort((a, b) => {
          if (a.acl.length === b.acl.length) {
            return 0;
          } else if (a.acl.length < b.acl.length) {
            return 1 * order;
          } else {
            return -1 * order;
          }
        });
      },
      showFileDeletionPrompt(name, path, $event = null) {
        swal({
          title: "Are you sure?",
          text: "Your will not be able to recover " + name + ".",
          type: "warning",
          showCancelButton: true,
          confirmButtonClass: "btn-danger",
          confirmButtonText: "Yes, delete it!",
        }).then((name) => {
          swal("Deleted!", "Your file " + name.value + " has been deleted.", "success");
        });
      },
      createFolder() {
        swal({
          title: "Create a folder",
          text: "Input the folder name:",
          input: "text",
          showCancelButton: true,
          inputPlaceholder: "Folder name..."
        }).then((inputValue) => {
          if (inputValue === false) return false;
          if (inputValue === "") {
            swal.showInputError("You need to enter a folder name.");
            return false
          }
          swal("Success", "Folder " + inputValue.value + " created", "success");
        });
      },
      sendToAbacus() {
        swal({
          title: 'Send to Abacus 2.0',
          text: "Are you sure?",
          showCancelButton: true,
          confirmButtonColor: '#52d64b',
          cancelButtonColor: '#d33',
          confirmButtonText: 'Yes, send it.'
        }).then((input) => {
          if (input.value) {
            swal('File sent!', 'Your file has been transferred to Abacus 2.0', 'success');
          }
        });
      },
      shareFile: function (name, type) {
        swal({
          title: "Share a " + type,
          text: "Enter the mail of the person you want to share with:",
          input: "email",
          showCancelButton: true,
          inputPlaceholder: "Mail..."
        }).then((inputValue) => {
          if (inputValue === false) return false;
          if (inputValue === "") {
            swal.showInputError("You need to enter an e-mail.");
            return false
          }
          swal("Success", name + " shared with " + inputValue.value, "success");
        });
      },
      renameFile: function (name, type) {
        swal({
          title: "Rename " + type + " " + name,
          text: "Enter a new name for file " + name + ":",
          input: "text",
          showCancelButton: true,
          inputPlaceholder: "New filename..."
        }).then((inputValue) => {
          if (inputValue === false) return false;
          if (inputValue === "") {
            swal.showInputError("You need to enter an e-mail.");
            return false
          }
          if (inputValue === name) {
            swal.showInputError("A " + type + " can't be renamed to itself");
            return false
          }
          swal("Success", name + " renamed to " + inputValue.value, "success");
        });
      }
    }
  }
</script>

