<template>
  <section>
    <div class="container-overlap" :class="[statusToColor()]">
      <div class="media m0 pv">
        <div class="media-left"><a href="#"></a></div>
        <div class="media-body media-middle">
          <h4 class="media-heading">{{ status.title }}</h4>
        </div>
      </div>
    </div>
    <div class="container-fluid">
      <div class="row">
        <!-- Left column-->
        <div class="col">
          <form class="card">
            <div class="card-body">
              <h3 data-type="textarea"
                  class="text-inherit editable editable-pre-wrapped editable-click editable-disabled" tabindex="-1">{{
                status.body }}</h3>
            </div>
          </form>
        </div>
        <!-- Right column-->
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'

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
    name: "status-overview",
    data() {
      // TODO REMOVE AFTER IT IS SHOWN
      setTimeout(() => {
        this.status = status2;
        setTimeout(() => {
          this.status = status3;
          setTimeout(() => this.status = status4, 10000);
        }, 10000);
      }, 10000);
      // END TODO REMOVE AFTER IT IS SHOWN
      return {
        status: {}
      }
    },
    mounted() {
      $.getJSON("/api/getStatus/").then((status) => {
        this.status = status;
      });
    },
    methods: {
      statusToColor() {
        if (this.status.level === 'NO ISSUES') {
          return 'bg-green-500';
        } else if (this.status.level === 'MAINTENANCE' || this.status.level === 'UPCOMING MAINTENANCE') {
          return 'bg-yellow-500';
        } else if (this.status.level === 'ERROR') {
          return 'bg-red-500';
        }
      }
    }
  }
</script>

<style scoped>

</style>
