<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <!-- DATATABLE DEMO 1-->
      <div class="col-lg-10">
        <div class="file-loading">
          <h1 class="app-loading-content">Loading Applications...</h1>
        </div>
        <div v-cloak class="card" v-if="applications[0] != null">
          <div class="card-body">
            <table id="table-options" class="table-datatable table table-striped table-hover mv-lg">
              <thead>
              <tr>
                <th></th>
                <th>Application Name</th>
                <th>Author</th>
                <th>Rating</th>
                <th class="sort-numeric">Version</th>
                <th></th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="gradeA row-settings" v-for="app in applications">
                <td v-if="app.info.private"
                    title="The app is private and can only be seen by the creator and people it was shared with">
                  <em class="ion-locked"></em></td>
                <td v-else title='The application is openly available for everyone'><em
                  class="ion-unlocked"></em></td>
                <td v-bind:title="app.info.description">{{ app.info.name }}</td>
                <td v-bind:title="app.info.description">{{ app.info.author }}</td>
                <td title="Rating for the application">{{ app.info.rating }} / 5</td>
                <td v-bind:title="app.info.description">{{ app.info.version }}</td>
                <th>
                  <a v-bind:href="'/runApp/' + app.info.name + '/' + app.info.version "><button class="btn btn-info">Run</button></a>
                </th>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div class="col-lg-2 visible-lg">
        <div>
          <button class="btn btn-primary ripple btn-block ion-android-upload"> Upload Application</button>
          <br>
          <hr>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'

  export default {
    name: 'applications',
    data() {
      return {
        applications: [],
      }
    },
    mounted() {
      this.getApplications();
    },
    methods: {
      getApplications: function () {
        $.getJSON("/api/getApplications").then((data) => {
          if (data[0] === undefined) {
            $(".app-loading-content").text("No applications found.");
          } else {
            $(".app-loading-content").text("");
            this.applications = data
          }
        });
      },
      newApplication: function () {
        console.log("Empty");
      }
    }
  }

</script>
