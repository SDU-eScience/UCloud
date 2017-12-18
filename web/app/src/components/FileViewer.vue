<template>
  <section>
    <div class="container-fluid">
      <div class="col-lg-10">
        <ol class="breadcrumb">
          <li v-for="breadcrumb in breadcrumbs" class="breadcrumb-item">
            <router-link :to="{ path: breadcrumb.second }" append>{{breadcrumb.first}}</router-link>
          </li>
        </ol>
        <loading-icon v-if="loading"></loading-icon>
        <div v-cloak class="card" v-if="!loading">
          <div v-if="!files.length">
            <h3 class="text-center">
              <small>There are no files in current folder</small>
            </h3>
          </div>
          <div v-else class="card-body">
            <div class="center-block text-center hidden-lg">
              <router-link class="btn btn-link btn-lg" to="/retrieveFavourites"><i class="icon ion-star"></i>
              </router-link>
              <a class="btn btn-link btn-lg" href="#/"><i class="icon ion-ios-home"></i></a>
            </div>
            <table class="table-datatable table table-hover mv-lg">
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
                <th class="text-left" @click="orderByName"><span class="text-left">Filename</span><span
                  :class="['icon', getIcon('filename')]"></span></th>
                <th class="text-left" @click="orderByFavourite"><span><a class="ion-star"></a></span><span
                  :class="['icon', getIcon('favourite')]"></span></th>
                <th class="text-left" @click="orderByDate"><span class="text-left">Last Modified</span><span
                  :class="['icon', getIcon('lastModified')]"></span></th>
                <th class="text-left" @click="orderByOwner"><span class="text-left">File Owner</span><span
                  :class="[getIcon('owners')]" class="icon"></span></th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="row-settings clickable-row" v-for="file in getFilePage()"
                  :style="{ cursor: file.type === 'DIRECTORY' ? 'pointer' : '' }" @click="openFile(file)">
                <td class="select-cell"><label class="mda-checkbox">
                  <input
                    name="select" class="select-box" :value="file" v-model="selectedFiles" @click="prevent"
                    type="checkbox"><em
                  class="bg-info"></em></label></td>
                <td v-if="file.type === 'FILE'">
                  <a class="ion-android-document"></a> {{ file.path.name }}
                </td>
                <td v-else><a class="ion-android-folder"></a> {{ file.path.name }}</td>
                <td v-if="file.isStarred"><a @click="favourite(file)" class="ion-star"></a></td>
                <td v-else><a class="ion-star" v-on:click="favourite(file.path.uri, $event)"></a></td>
                <td>{{ new Date(file.modifiedAt).toLocaleString() }}</td>
                <td>{{ file.acl.length > 1 ? file.acl.length + ' collaborators' : file.acl[0].right }}</td>
                <td>
                  <span class="hidden-lg">
                      <div class="pull-right dropdown">
                          <button type="button" data-toggle="dropdown"
                                  class="btn btn-flat btn-flat-icon"
                                  aria-expanded="false"><em class="ion-android-more-vertical"></em></button>
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
                                     v-on:click="showFileDeletionPrompt(file.path)"> Delete file</a></li>
                          </ul>
                      </div>
                  </span>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
        <select class="selectpicker" v-model="filesPerPage">
          <option value="10">10</option>
          <option value="25">25</option>
          <option value="50">50</option>
          <option value="100">100</option>
        </select> Files per page
        <div class="text-center">
          <button class="previous btn-default btn btn-circle" @click="previousPage()" :disabled="currentPage === 0"
                  id="datatable1_previous"><em class="ion-ios-arrow-left"></em></button>
          <span v-for="n in paginationPages">
            <button class="paginate_button btn btn-default btn-circle" :class="{ 'btn-info': n - 1 === currentPage }"
                    :disabled="n - 1 === currentPage"
                    @click="toPage(n - 1)">{{ n }}</button>
          </span>
          <button class="paginate_button next btn-default btn btn-circle ion-ios-arrow-right" @click="nextPage()"
                  :disabled="currentPage === Math.max(paginationPages - 1, 0)"></button>
        </div>
      </div>
      <div class="col-lg-2 visible-lg">
        <div>
          <div class="center-block text-center">
            <router-link class="btn btn-link btn-lg" to="/retrieveFavourites"><i class="icon ion-star"></i>
            </router-link>
            <a class="btn btn-link btn-lg" href="#/"><i class="icon ion-ios-home"></i></a>
          </div>
          <hr>
          <button class="btn btn-primary ripple btn-block ion-android-upload" v-on:click="uploadFile"> Upload Files</button>
          <br>
          <button class="btn btn-default ripple btn-block ion-folder" v-on:click="createFolder"> New
            folder
          </button>
          <br>
          <hr>
          <h3 v-if="selectedFiles.length" v-cloak>
            {{ 'Rights level: ' + options.rightsName }}<br>
            {{ fileText }}</h3>
          <div v-if="selectedFiles.length" v-cloak>
            <p>
              <button class="btn btn-info rippple btn-block"
                      v-on:click="sendToAbacus()"> Send to Abacus 2.0
              </button>
            </p>
            <p>
              <button type="button" class="btn btn-default ripple btn-block ion-share" :title="getTitle('share')"
                      v-on:click="shareFile(selectedFiles[0].path.name, 'folder')"> Share selected files
              </button>
            </p>
            <p>
              <button class="btn btn-default ripple btn-block ion-ios-download" :title="getTitle('download')">
                Download selected files
              </button>
            </p>
            <p>
              <button type="button" class="btn btn-default btn-block ripple ion-android-star">
                Favourite selected files
              </button>
            </p>
            <p>
              <button class="btn btn-default btn-block ripple ion-ios-photos" :title="getTitle('move')">
                Move folder
              </button>
            </p>
            <p>
              <button type="button" class="btn btn-default btn-block ripple ion-ios-compose"
                      v-on:click="renameFile(selectedFiles[0].path.name, 'folder')" :title="getTitle('rename')"
                      :disabled="options.rightsLevel < 3 || selectedFiles.length !== 1">
                Rename file
              </button>
            </p>
            <p>
              <button class="btn btn-danger btn-block ripple ion-ios-trash" :title="getTitle('delete')"
                      :disabled="options.rightsLevel < 3"
                      v-on:click="showFileDeletionPrompt(selectedFiles[0].path)">
                Delete selected files
              </button>
            </p>
          </div>
        </div>
      </div>
    </div>
    <status-page></status-page>
  </section>
</template>

<script>
  import $ from 'jquery'
  import swal from 'sweetalert2'
  import Vue from 'vue'
  import VueRouter from 'vue-router'
  import LoadingIcon from "./LoadingIcon";
  import StatusPage from "./Status";

  Vue.use(VueRouter);

  export default {
    components: {
      StatusPage,
      LoadingIcon},
    name: 'file-viewer',

    data() {
      return {
        files: [],
        filesPerPage: 10,
        currentPage: 0,
        selectedFiles: [],
        breadcrumbs: [],
        loading: true,
        masterCheckbox: false,
        sortOrders: {
          lastSorted: 'filename',
          filename: true,
          favourite: false,
          lastModified: false,
          owners: false
        },
        buttonTitles: {
          rename: {
            normal: 'Click to rename the selected file.',
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
          },
          share: {
            normal: 'Click to share the selected files.'
          },
          download: {
            normal: 'Click to download the selected files.',
            lowRightsLevel: 'You do not the correct permissions to delete the selected files',
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
      window.addEventListener('keydown', this.deselectKeyListener)
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
      paginationPages() {
        return Math.ceil(this.files.length / this.filesPerPage)
      },
      fileText() {
        return this.selectedFiles.length > 1 ? this.selectedFiles.length + ' files selected.' : this.selectedFiles[0].path.name
      }
    },
    watch: {
      '$route'(to, fr) {
        if (to.path === '/retrieveFavourites') {
          this.getFavourites();
        } else {
          this.getFiles(to.path);
        }
      },
      filesPerPage() { // Don't delete me, I am useful
        this.currentPage = 0
      }
    },
    methods: {
      nextPage() {
        this.currentPage++;
      },
      previousPage() {
        this.currentPage--;
      },
      toPage(pageNumber) {
        this.currentPage = pageNumber;
      },
      getFilePage() {
        let filesPerPage = parseInt(this.filesPerPage);
        return this.files.slice(this.currentPage * filesPerPage, this.currentPage * filesPerPage + filesPerPage);
      },
      getTitle(buttonName) {
        if (buttonName === 'rename') {
          if (this.options.rightsLevel < 3) {
            return this.buttonTitles[buttonName]['lowRightsLevel'];
          } else if (this.selectedFiles.length > 1) { // Special case for rename
            return this.buttonTitles[buttonName]['multipleFilesSelected'];
          }
        } else if (buttonName === 'share') {
          if (this.options.rightsLevel < 3) {
            return this.buttonTitles[buttonName]['lowRightsLevel']
          }
        } else if (buttonName === 'move') {
          if (this.options.rightsLevel < 3) {
            return this.buttonTitles[buttonName]['lowRightsLevel']
          }
        } else if (buttonName === 'delete') {
          if (this.options.rightsLevel < 3) {
            return this.buttonTitles[buttonName]['lowRightsLevel']
          }
        }
        return this.buttonTitles[buttonName]['normal'];
      },
      prevent($event) {
        $event.stopPropagation();
      },
      deselectKeyListener($event) {
        if ($event.key === 'Escape') {
          this.selectedFiles = [];
          this.masterCheckbox = false;
        }
      },
      openFile(file) {
        if (file.type === 'DIRECTORY') {
          window.location.hash = file.path.path
        }
      },
      getFavourites() {
        this.loading = true;
        $.getJSON("/api/getFavourites").then((files) => {
          this.files = files;
          this.breadcrumbs = [{
            first: "Favourites",
            second: ""
          }];
          this.masterCheckbox = false;
          this.selectedFiles = [];
          this.loading = false;
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
          this.breadcrumbs = breadcrumbs;
        });
      },
      getIcon(name) {
        if (this.sortOrders.lastSorted !== name) {
          return ""
        }
        if (this.sortOrders[name]) {
          return "ion-chevron-up"
        } else {
          return "ion-chevron-down"
        }
      },
      favourite(uri, $event) {
        $event.stopPropagation();
        $.getJSON("/api/favouriteFile", {path: uri}).then((success) => {
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
          this.files = files;
          this.orderByName();
          this.loading = false;
          this.masterCheckbox = false;
          this.selectedFiles = [];
          this.getBreadcrumbs(path);
        });
      },
      orderByName($event) {
        let order = this.sortOrders.filename ? 1 : -1;
        this.sortOrders.filename = !this.sortOrders.filename;
        this.sortOrders.lastSorted = "filename";
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
      orderByFavourite($event) {
        let order = this.sortOrders.favourite ? 1 : -1;
        this.sortOrders.favourite = !this.sortOrders.favourite;
        this.sortOrders.lastSorted = "favourite";
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
        this.sortOrders.lastSorted = "lastModified";
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
        this.sortOrders.owners = !this.sortOrders.owners;
        this.sortOrders.lastSorted = "owners";
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
      showFileDeletionPrompt(path, $event = null) {
        let fileName = path.name;
        let uri = path.uri;
        swal({
          title: "Are you sure?",
          text: "Your will not be able to recover " + fileName + ".",
          type: "warning",
          showCancelButton: true,
          confirmButtonClass: "btn-danger",
          confirmButtonText: "Yes, delete it!",
        }).then((input) => {
          if (!input.dismiss) {
            swal("Deleted!", "Your file " + fileName + " has been deleted.", "success");
            // TODO: Send URI or bubble up request.
          }
        });
      },
      createFolder() {
        let currentDir = this.breadcrumbs[this.breadcrumbs.length - 1].second;
        swal({
          title: "Create a folder",
          text: "Input the folder name:",
          input: "text",
          showCancelButton: true,
          inputPlaceholder: "Folder name...",
          confirmButtonText: "Create folder",
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
      shareFile(name, type) {
        swal({
          title: "Share a " + type,
          text: "Enter the mail of the person you want to share with:",
          input: "email",
          showCancelButton: true,
          inputPlaceholder: "Mail..."
        }).then((input) => {
          if (!input.dismiss) {
            swal("Success", name + " shared with " + input.value, "success");
          }
        });
      },
      renameFile(name, type) {
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
      },
      uploadFile() {
        swal({
          title: "Upload file",
          input: "file",
          showCancelButton: true,
          preConfirm: (file) => {
            if (file === null || file === '') {
              swal.showValidationError("Please select a file.")
            }
          }
        }).then( (file) => {
          if (!file.dismiss) {
            console.log(file.value);
          }
        });
      }
    }
  }
</script>
