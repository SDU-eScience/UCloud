<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
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
                <th>Comments</th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="gradeA row-settings" v-for="analysis in analyses">
                <td>{{ analysis.name }}</td>
                <td>{{ analysis.jobId }}</td>
                <td>{{ analysis.status }}</td>
                <td>
                  <button v-if="analysis.comments.length" data-toggle="modal" data-target="#commentsModal" class="btn btn-primary" @click="setCurrentAnalysis(analysis)">Show {{ analysis.comments.length  }} comments</button>
                  <button v-else>Write comment</button>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <div id="commentsModal" class="modal fade" role="dialog">
      <div class="modal-dialog">
        <!-- Modal content-->
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">&times;</button>
            <h4 class="modal-title"> {{ modalAnalysis.name }}<br><small>Comments</small></h4>
          </div>
          <div class="modal-body">
            <div v-for="comment in modalAnalysis.comments">
              <b>{{ comment.author }} @</b> <i>{{ new Date(comment.timestamp).toLocaleString() + ':' }}</i> <br><span>{{ comment.content }}</span>
            </div>
          <hr>
            <div>You: <input v-model="newComment" type="text"/> <button @click="postComment()" type="button" class="btn btn-primary text-right">Send</button></div>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
          </div>
        </div>

      </div>
    </div>
    <status-page></status-page>
  </section>
</template>

<script>
  import $ from 'jquery'
  import LoadingIcon from "./LoadingIcon";
  import StatusPage from "./Status";


  export default {
    components: {
      StatusPage,
      LoadingIcon},
    name: 'analyses',
    data() {
      return {
        analyses: {},
        newComment: "",
        websocketSupport: "WebSocket" in window,
        loading: true,
        // TODO Fix location
        ws: new WebSocket("ws://localhost:8080/ws/analyses"),
        modalAnalysis: {}
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
          this.analyses.forEach((it) => {
            it.jobId = Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000)
          });
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
      setCurrentAnalysis(analysis) {
        this.modalAnalysis = analysis;
      },
      postComment() {
        if (this.newComment) {
          this.modalAnalysis.comments.push({author: "You", content: this.newComment, timestamp: new Date()});
        }
        // TODO Actually post comment
      }
    }
  }
</script>
