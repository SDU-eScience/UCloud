<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <!-- DATATABLE DEMO 1-->
      <div class="col-lg-10">
        <loading-icon v-if="!applications.length"></loading-icon>
        <div v-cloak class="card" v-if="applications.length">
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
                <td title="The application is openly available for everyone" v-else ><em
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
          <button class="btn btn-primary ripple btn-block ion-android-upload" @click="newApplication()"> Upload Application</button>
          <br>
          <hr>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import swal from 'sweetalert2'
  import LoadingIcon from "./LoadingIcon";

  export default {
    components: {LoadingIcon},
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
      getApplications() {
        $.getJSON("/api/getApplications").then((data) => {
          this.applications = data;
        });
      },
      newApplication() {
        swal({
          input: 'file',
        }).then((input) => {
          this.uploadApplication(input)
        });
      },
      uploadApplication(file) {
        console.log(file);
      }
    }
  }
</script>
