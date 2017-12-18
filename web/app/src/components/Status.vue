<template>
  <div id="statusModal" class="modal fade" role="dialog">
    <div class="modal-dialog">
      <!-- Modal content-->
      <div class="modal-content">
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

<script>
  import $ from 'jquery'

  export default {
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
      }
    }
  }
</script>

<style scoped>

</style>
