<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <!-- DATATABLE DEMO 1-->
      <div class="col-lg-10">
        <loading-icon v-if="loading"></loading-icon>
        <div v-cloak class="card" v-else>
          <div class="card-body">
            <h3 v-if="!websocketSupport"><small>Note: The browser in use does not support websockets. The progress of the analyses will not be updated unless the page is reloaded.</small></h3>
            <h3 v-if="!analyses.length" class="text-center">
              <small>No analyses found. Go to workflows to start one.</small>
            </h3>
            <table v-else id="table-options" class="table-datatable table table-striped table-hover mv-lg">
              <thead>
              <tr>
                <th>Workflow Name</th>
                <th>Job Id</th>
                <th>Status</th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="gradeA row-settings" v-for="analysis in analyses">
                <td>{{ analysis.name }}</td>
                <td>{{ Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) + '-' +
                  Math.floor(Math.random() * 100000) }}
                </td>
                <td>{{ analysis.status }}</td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import LoadingIcon from "./LoadingIcon";


  export default {
    components: {LoadingIcon},
    name: 'analyses',
    data() {
      return {
        analyses: {},
        websocketSupport: "WebSocket" in window,
        loading: true,
        // TODO Fix location
        ws: new WebSocket("ws://localhost:8080/ws/analyses")
      }
    },
    mounted() {
      this.getAnalyses();
      this.wsInit();
    },
    methods: {
      getAnalyses() {
        this.loading = true;
        $.getJSON("/api/getAnalyses").then(data => {
          this.analyses = data;
          this.loading = false;
          this.subscribeToProgress();
        });
      },
      wsInit() {
        this.ws.onerror = () => {
          console.log("Socket error.")
        };

        this.ws.onopen = () => {
          console.log("Connected");
        };

        this.ws.onmessage = response => {
          console.log(response.data.toString());
        };
      },
      subscribeToProgress() {
        let subscriptionList = this.analyses.filter(analysis => analysis.status === 'In Progress' || analysis.status === 'Pending');
        this.ws.send(subscriptionList);
      }
    }
  }
</script>
