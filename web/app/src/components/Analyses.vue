<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <!-- DATATABLE DEMO 1-->
      <div class="col-lg-10">
        <div class="file-loading">
          <h1 v-if="loading">Loading Analyses...</h1>
        </div>
        <div v-cloak class="card" v-if="analyses.length">
          <div class="card-body">
            <table id="table-options" class="table-datatable table table-striped table-hover mv-lg">
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
                <td>{{ Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) }}</td>
                <td>{{ analysis.status }}</td>
              </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
      <!--<div class="col-lg-2 visible-lg">
      </div>-->
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'

  export default {
    name: 'analyses',
    data() {
      return {
        analyses: {},
        loading: true
      }
    },
    mounted() {
      this.getAnalyses()
    },
    methods: {
      getAnalyses() {
        this.loading = true;
        $.getJSON("/api/getAnalyses").then((data) => {
            this.analyses = data;
            this.loading = false;
        });
      }
    }
  }
</script>
