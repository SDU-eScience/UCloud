<template>
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <!-- DATATABLE DEMO 1-->
      <div class="col-lg-10">
        <div class="file-loading">
          <h1 class="workflows-loading-content">Loading Workflows...</h1>
        </div>
        <div v-cloak class="card" v-if="workflows[0] != null">
          <div class="card-body">
            <table id="table-options" class="table-datatable table table-striped table-hover mv-lg">
              <thead>
              <tr>
                <th>Name</th>
                <th>Applications</th>
              </tr>
              </thead>
              <tbody v-cloak>
              <tr class="gradeA row-settings" v-for="workflow in workflows">
                <td>{{ workflow.name }}</td>
                <td><div v-for="app in workflow.applications">
                  {{ app.info.name }}<br>
                </div></td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <div class="col-lg-2 visible-lg">
        <div>
          <button class="btn btn-primary ripple btn-block ion-android-upload"> Generate workflow</button>
          <br>
          <hr>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import Vue from 'vue'

  export default {
    name: 'workflows',
    data() {
      return {
        workflows: []
      }
    },
    mounted: function () {
      this.getWorkflows();
    },
    methods: {
      getWorkflows: function () {
        $.getJSON("/api/getWorkflows").then((data) => {
          if (data[0] === undefined) {
            this.workflows = [];
            $(".workflows-loading-content").text("No workflows found.");
            // TODO add add button?
          } else {
            $(".workflows-loading-content").text("");
            this.workflows = data;
          }
        });
      }
    }
  }
</script>
