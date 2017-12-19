<template>
  <div id="statusModal" class="modal fade" role="dialog">
    <div class="modal-dialog" v-cloak>
      <!-- Modal content-->
      <loading-icon v-if="statusLoading"></loading-icon>
      <div v-else class="modal-content">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal">&times;</button>
          <h4 class="modal-title"> {{ status.title }}<br></h4>
        </div>
        <div class="modal-body">
          <p>{{ status.body }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
        </div>
      </div>
    </div>
  </div>
</template>

data class StatusNotification(val title: String, val level: String, val body: String)

<script>
  import $ from 'jquery'
  import LoadingIcon from "./LoadingIcon";

  // MOCK Statuses:
  const status2 = {
    title: "Scheduled maintenance",
    level: "MAINTENANCE",
    body: "Maintenance is currently in progress.",
  };
  const status3 = {
    title: "An error has occurred",
    level: "ERROR",
    body: "An error has occurred. The site will be back momentarily."
  };
  const status4 = {
    title: "No issues",
    level: "UPCOMING MAINTENANCE",
    body: "Maintenance is scheduled from 18 PM until midnight CET."
  };
  // END MOCK Statuses

  export default {
    components: {LoadingIcon},
    name: "status-page",
    data() {
      return {
        status: {},
        statusLoading: false,
      }
    },
    mounted() {
      this.getCurrentStatus()
    },
    methods: {
      getCurrentStatus() {
        this.statusLoading = true;
        $.getJSON("/api/getStatus/").then((status) => {
          this.status = status;
          this.statusLoading = false;
        });
        // TODO REMOVE WHEN SHOWN
        setTimeout(() => {
          this.status = status2;
          setTimeout(() => {
            this.status = status3;
            setTimeout(() => this.status = status4, 10000);
          }, 10000);
        }, 10000);
      },
      statusToButton() {
        if (this.status.level === 'NO ISSUES') {
          return "btn-success";
        } else if ('UPCOMING MAINTENANCE') {
          return "btn-info";
        } else if (this.status.level === 'MAINTENANCE') {
          return "btn-warning"
        } else if (this.status.level === 'ERROR') {
          return "btn-danger"
        }
      }
    }
  }
</script>

<style scoped>

</style>
