<template>
    <a href="/statusoverview" class="btn btn-info" :title="status.body" :class="[statusToButton()]">{{ status.title }}</a>
</template>

<script>
  import $ from 'jquery'
  import LoadingIcon from "./LoadingIcon";

  // MOCK Statuses:
  const status2 = {
    title: "Scheduled maintenance",
    level: "MAINTENANCE",
    body: "Maintenance is scheduled from 18 PM until midnight CET.",
  };
  const status3 = {
    title: "An error has occurred",
    level: "ERROR",
    body: "An error has occurred. The site will be back momentarily."
  };
  const status4 = {
    title: "No issues, upcoming maintenance",
    level: "UPCOMING MAINTENANCE",
    body: "Maintenance is scheduled from 18 PM until midnight CET."
  };
  // END MOCK Statuses

  export default {
    components: {LoadingIcon},
    name: "status",
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

        // TODO REMOVE AFTER IT IS SHOWN
        setTimeout(() => {
          this.status = status2;
          setTimeout(() => {
            this.status = status3;
            setTimeout(() => this.status = status4, 10000);
          }, 10000);
        }, 10000);
        // END TODO REMOVE AFTER IT IS SHOWN
      },
      statusToButton() {
        if (this.status.level === 'NO ISSUES') {
          return "btn-success";
        } else if (this.status.level === 'UPCOMING MAINTENANCE') {
          return "btn-warning";
        } else if (this.status.level === 'MAINTENANCE') {
          return "btn-warning";
        } else if (this.status.level === 'ERROR') {
          return "btn-danger";
        }
      }
    }
  }
</script>

<style scoped>

</style>
