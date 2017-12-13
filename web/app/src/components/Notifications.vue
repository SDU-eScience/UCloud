<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container container-md">
      <loading-icon v-if="loading"></loading-icon>
      <div class="card" v-cloak v-if="notifications.length">
        <table class="table table-hover table-fixed va-middle">
          <h3 v-if="!hasWebsocketSupport">
            <small>Websockets are not supported in this browser. Notifications won't be updated automatically.</small>
          </h3>
          <tbody>
          <tr class="msg-display clickable" v-for="notification in recentNotifications">
            <td class="wd-xxs">
              <div v-if="notification.type === 'Complete'" class="initial32 bg-green-500">✓</div>
              <div v-else-if="notification.type === 'In Progress'" class="initial32 bg-blue-500">...</div>
              <div v-else-if="notification.type === 'Pending'" class="initial32 bg-blue-500"></div>
              <div v-else-if="notification.type === 'Failed'" class="initial32 bg-red-500">✖</div>
            </td>
            <th class="mda-list-item-text mda-2-line">
              <small>{{ notification.message }}</small>
              <br>
              <small class="text-muted">{{ new Date(notification.timestamp).toLocaleString() }}</small>
            </th>
            <td class="text">{{ getShorterBody(notification.body) }}</td>
          </tr>
          </tbody>
        </table>
        <button @click="showMore" class="btn btn-info ion-ios-arrow-down" v-if="notificationsShown < notifications.length"></button>
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
        ws: new WebSocket("ws://localhost:8080/ws/notifications"),
        notifications: [],
        notificationsShown: 10
      }
    },
    mounted() {
      this.getNotifications();
      if (this.hasWebsocketSupport) this.initWS();
    },
    computed: {
      recentNotifications() {
        let lastElement = this.notifications.length - 1;
        this.notifications.sort((a, b) => {
          return b.timestamp - a.timestamp
        });
        console.log(lastElement)
        return this.notifications.slice(0, Math.min(this.notificationsShown, lastElement));
      }
    },
    methods: {
      getShorterBody(body) {
        let bodyLength = body.length;
        const maxLength = 30;
        if (bodyLength < maxLength) {
          return body;
        } else {
          return body.substring(0, maxLength) + "..."
        }
      },
      getNotifications() {
        this.loading = true;
        $.getJSON("/api/getNotifications").then((notifications) => {
          notifications.forEach( (it) => {
            this.notifications.push(it);
          });
          this.loading = false;
        });
      },
      showMore() {
        this.notificationsShown += 10;
      },
      initWS() {
        this.ws.onerror = () => {
          console.log("Socket error.")
        };

        this.ws.onopen = () => {
          console.log("Connected");
        };

        this.ws.onmessage = response => {
          this.notifications.push(JSON.parse(response.data));
        };
      },
    }
  }
</script>
