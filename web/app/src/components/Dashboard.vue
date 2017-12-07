<template>
  <div class="container container-fluid">
    <div class="col-lg-12">
      <div class="col-sm-3">
        <div class="card">
          <h5 class="card-heading pb0">
            Favourite files
          </h5>
          <table class="table-datatable table table-striped table-hover mv-lg">
            <thead>
            <tr>
              <th>File</th>
              <th>Starred</th>
            </tr>
            </thead>
            <loading-icon v-if="favouritesLoading"></loading-icon>
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
          <table class="table-datatable table table-striped table-hover mv-lg">
            <thead>
            <tr>
              <th>File</th>
              <th>Modified</th>
            </tr>
            </thead>
            <loading-icon v-if="mostRecentlyUsedLoading"></loading-icon>
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
          <table class="table-datatable table table-striped table-hover mv-lg">
            <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
            </tr>
            </thead>
            <loading-icon v-if="analysesLoading"></loading-icon>
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
        <div class="card">
          <h5 class="card-heading">Activity</h5>
          <div class="card-body pb0">
            <p class="pull-left mr"><em class="ion-record text-info"></em></p>
            <div class="oh">
              <p><strong class="mr-sm">Added</strong><span class="mr-sm">a new issue</span><a
                href="#">#5478</a></p>
              <div class="clearfix">
                <div class="pull-left text-muted"><em
                  class="ion-android-time mr-sm"></em><span>an hour ago</span></div>
              </div>
            </div>
          </div>
          <div class="card-body pb0">
            <p class="pull-left mr"><em class="ion-record text-danger"></em></p>
            <div class="oh">
              <p><strong class="mr-sm">Commented</strong><span class="mr-sm"> on the project</span><a
                href="#">Material</a></p>
              <p class="bl pl"><i>That's awesome!</i></p>
              <div class="clearfix">
                <div class="pull-left text-muted"><em
                  class="ion-android-time mr-sm"></em><span>2 hours ago</span></div>
              </div>
            </div>
          </div>
          <div class="card-body pb0">
            <p class="pull-left mr"><em class="ion-record text-success"></em></p>
            <div class="oh">
              <p><strong class="mr-sm">Completed</strong><span> all tasks asigned this week</span></p>
              <div class="clearfix">
                <div class="pull-left text-muted"><em
                  class="ion-android-time mr-sm"></em><span>3 hours ago</span></div>
              </div>
            </div>
          </div>
          <div class="card-body pb0">
            <p class="pull-left mr"><em class="ion-record text-info"></em></p>
            <div class="oh">
              <p><strong class="mr-sm">Published</strong><span class="mr-sm"> new photos on the album</span><a
                href="#">WorldTrip</a></p>
              <p><a href="#"><img src="/img/pic4.jpg" alt="Pic" class="mr-sm thumb48"></a><a href="#"><img
                src="/img/pic5.jpg" alt="Pic" class="mr-sm thumb48"></a><a href="#"><img
                src="/img/pic6.jpg" alt="Pic" class="mr-sm thumb48"></a></p>
              <div class="clearfix">
                <div class="pull-left text-muted"><em
                  class="ion-android-time mr-sm"></em><span>4 hours ago</span></div>
              </div>
            </div>
          </div>
          <div class="card-body">
            <div class="clearfix">
              <p class="pull-left mr"><em class="ion-record text-primary"></em></p>
              <div class="oh">
                <p><strong class="mr-sm">Following</strong><span class="mr-sm">Jane Kuhn</span></p>
                <p><span class="image-list"><a href="#"><img src="img/user/03.jpg" alt="User"
                                                             class="img-circle thumb32"></a><a href="#"><img
                  src="/img/user/04.jpg" alt="User" class="img-circle thumb32"></a><a href="#"><img
                  src="/img/user/05.jpg" alt="User" class="img-circle thumb32"></a><a href="#"><img
                  src="/img/user/06.jpg" alt="User" class="img-circle thumb32"></a><a href="#"><img
                  src="/img/user/07.jpg" alt="User" class="img-circle thumb32"></a><strong><a href="#"
                                                                                              class="ml-sm link-unstyled">+200</a></strong></span>
                </p>
                <div class="clearfix">
                  <div class="pull-left text-muted"><em class="ion-android-time mr-sm"></em><span>yesterday</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import Vue from 'vue'
  import $ from 'jquery'
  import LoadingIcon from "./LoadingIcon";
  export default {
    components: {LoadingIcon},
    name: 'dashboard-component',
    data() {
      return {
        favourites: [],
        favouritesLoading: false,
        mostRecentlyUsed: [],
        mostRecentlyUsedLoading: false,
        analyses: [],
        analysesLoading: false
      }
    },
    mounted() {
      this.getFavourites();
      this.getMostRecentFiles();
      this.getWorkflowStatuses();
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
      }
    }
  }
</script>
