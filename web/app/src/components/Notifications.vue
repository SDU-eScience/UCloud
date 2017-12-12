<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container container-md">
    <!-- DATATABLE DEMO 1-->
      <loading-icon v-if="loading"></loading-icon>
        <div class="card" v-cloak v-if="notifications.length">
          <table class="table table-hover table-fixed va-middle">
            <h3 v-if="!hasWebsocketSupport"><small>Websockets are not supported in this browser. Notifications won't be updated automatically.</small></h3>
            <tbody>
            <tr class="msg-display clickable" v-for="notification in recentNotifications">
              <td class="wd-xxs">
                <div v-if="notification.type === 'Complete'" class="initial32 bg-green-500">✓</div>
                <div v-else-if="notification.type === 'In Progress'" class="initial32 bg-blue-500">...</div>
                <div v-else-if="notification.type === 'Pending'" class="initial32 bg-pink-500"></div>
                <div v-else-if="notification.type === 'Failed'" class="initial32 bg-red-500">✖</div>
              </td>
              <th class="mda-list-item-text mda-2-line">
                <small>{{ notification.message }}</small> <br>
                <small class="text-muted">{{ new Date(notification.timestamp).toLocaleString() }}</small>
              </th>
              <td class="text">{{ notification.body }}</td>
            </tr>
            </tbody>
          </table>
        </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import Vue from 'vue'
  import LoadingIcon from './LoadingIcon'

  export default {
    name: 'notifications',
    components: {LoadingIcon},
    data() {
      return {
        loading: true,
        hasWebsocketSupport: "WebSocket" in window,
        notifications: []
      }
    },
    mounted() {
      this.getNotifications()
    },
    computed: {
      recentNotifications() {
        return this.notifications;
      }
    },
    methods: {
      getNotifications() {
        this.loading = true;
        $.getJSON("/api/getNotifications").then((notifications) => {
          this.notifications = notifications.sort((a, b) => {
            return b.timestamp - a.timestamp
          });
          this.loading = false;
        });
      }
    }
  }
</script>
