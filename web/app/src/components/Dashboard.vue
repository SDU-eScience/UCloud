<template>
  <div class="container container-fluid">
    <div class="col-lg-12">
      <div class="col-sm-3">
        <div class="card">
          <h5 class="card-heading pb0">
            Favourite files
          </h5>
          <loading-icon v-if="favouritesLoading"></loading-icon>
          <h3 v-else-if="!favourites.length" class="text-center"><small>No favourites found.</small></h3>
          <table class="table-datatable table table-hover mv-lg" v-else>
            <thead>
            <tr>
              <th>File</th>
              <th>Starred</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="file in favourites">
              <td><a href="#">{{ file.path.name }}</a></td>
              <td><em class="ion-star"></em></td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="col-sm-3">
        <div v-cloak class="card">
          <h5 class="card-heading pb0">
            Recently modified files
          </h5>
          <loading-icon v-if="mostRecentlyUsedLoading"></loading-icon>
          <h3 v-else-if="!mostRecentlyUsed.length" class="text-center"><small>No recently used files found.</small></h3>
          <table class="table-datatable table table-hover mv-lg" v-else>
            <thead>
            <tr>
              <th>File</th>
              <th>Modified</th>
            </tr>
            </thead>
            <tbody>
            <tr v-cloak v-for="file in mostRecentlyUsed">
              <td><a href="#">{{ file.path.name }}</a></td>
              <td>{{ new Date(file.modifiedAt).toLocaleString() }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="col-sm-3">
        <div v-cloak class="card">
          <h5 class="card-heading pb0">
            Analyses
          </h5>
          <loading-icon v-if="analysesLoading"></loading-icon>
          <h3 v-else-if="!analyses.length" class="text-center"><small>No analyses found.</small></h3>
          <table class="table-datatable table table-hover mv-lg" v-else>
            <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="analysis in analyses">
              <td><a href="#">{{ analysis.name }}</a></td>
              <td>{{ analysis.status }}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
      <div class="col-sm-3 ">
        <div class="card" v-cloak>
          <h5 class="card-heading pb0">
            Notifications
          </h5>
          <loading-icon v-if="notificationsLoading"></loading-icon>
          <h3 v-else-if="!notifications.length" class="text-center"><small>No activity found.</small></h3>
          <div>
              <table class="table-datatable table table-hover mv-lg">
                <tbody>
                <tr class="msg-display clickable" v-for="notification in notifications">
                  <td class="wd-xxs">
                    <div v-if="notification.type === 'Complete'" class="initial32 bg-green-500">✓</div>
                    <div v-else-if="notification.type === 'In Progress'" class="initial32 bg-blue-500">...</div>
                    <div v-else-if="notification.type === 'Pending'" class="initial32 bg-blue-500"></div>
                    <div v-else-if="notification.type === 'Failed'" class="initial32 bg-red-500">&times;</div>
                  </td>
                  <th class="mda-list-item-text mda-2-line">
                    <small>{{ notification.message }}</small>
                    <br>
                    <small class="text-muted">{{ new Date(notification.timestamp).toLocaleString() }}</small>
                  </th>
                  <td class="text">{{ getShorterBody(notification.body) }}</td>
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
  import LoadingIcon from "./LoadingIcon";
  export default {
    components: {
      LoadingIcon},
    name: 'dashboard-component',
    data() {
      return {
        favourites: [],
        favouritesLoading: false,
        mostRecentlyUsed: [],
        mostRecentlyUsedLoading: false,
        analyses: [],
        analysesLoading: false,
        notifications: [],
        notificationsLoading: false,
      }
    },
    mounted() {
      this.getFavourites();
      this.getMostRecentFiles();
      this.getWorkflowStatuses();
      this.getRecentActivity();
    },
    methods: {
      getFavourites() {
        this.favouritesLoading = true;
        $.getJSON("/api/getFavouritesSubset").then((data) => {
          this.favourites = data;
          this.favouritesLoading = false;
        });
      },
      getMostRecentFiles() {
        this.mostRecentlyUsedLoading = true;
        $.getJSON("/api/getMostRecentFiles").then((data) => {
          this.mostRecentlyUsed = data;
          this.mostRecentlyUsedLoading = false;
        });
      },
      getWorkflowStatuses() {
        this.analysesLoading = true;
        $.getJSON("/api/getRecentWorkflowStatus").then((data) => {
          this.analyses = data;
          this.analysesLoading = false;
        });
      },
      getRecentActivity() {
        this.notificationsLoading = true;
        $.getJSON("/api/getRecentActivity").then( (notifications) => {
          this.notifications = notifications;
            this.notificationsLoading = false;
        });
      },
      getShorterBody(body) {
        let bodyLength = body.length;
        const maxLength = 30;
        if (bodyLength < maxLength) {
          return body;
        } else {
          return body.substring(0, maxLength) + "..."
        }
      }
    }
  }
</script>
