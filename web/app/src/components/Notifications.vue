<template>
  <!-- Page content-->
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container container-md">
      <loading-icon v-if="loading"></loading-icon>
      <p v-else class="ph">Last 24 hours</p>
      <div class="card" v-cloak v-if="recent.length">
        <table class="table table-hover table-fixed va-middle">
          <h3 v-if="!hasWebsocketSupport">
            <small>Websockets are not supported in this browser. Notifications won't be updated automatically.</small>
          </h3>
          <tbody>
          <tr class="msg-display clickable" v-for="notification in recentList" @click="updateCurrentNotification(notification)" data-toggle="modal" data-target="#notificationModal">
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
        <button @click="showMoreRecent" class="btn btn-info ion-ios-arrow-down" v-if="notificationsShownRecent < recent.length"></button>
      </div>
      <p v-if="!loading" class="ph">Older</p>
      <div class="card" v-cloak v-if="remaining.length">
        <table class="table table-hover table-fixed va-middle">
          <h3 v-if="!hasWebsocketSupport">
            <small>Websockets are not supported in this browser. Notifications won't be updated automatically.</small>
          </h3>
          <tbody>
          <tr class="msg-display clickable" v-for="notification in remainingList" @click="updateCurrentNotification(notification)" data-toggle="modal" data-target="#notificationModal">
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
        <button @click="showMoreRemaining" class="btn btn-info ion-ios-arrow-down" v-if="notificationsShownRemaining < remaining.length"></button>
      </div>
    </div>


    <div id="notificationModal" class="modal fade" role="dialog">
      <div class="modal-dialog">
        <!-- Modal content-->
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal">&times;</button>
            <h4 class="modal-title"> {{ currentNotification.message }}<br><small>{{ new Date(currentNotification.timestamp).toLocaleString() }}</small></h4>
          </div>
          <div class="modal-body">
            <p>{{ currentNotification.body }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
          </div>
        </div>

      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery'
  import LoadingIcon from './LoadingIcon'

  export default {
    name: 'notifications',
    components: {LoadingIcon},
    data() {
      return {
        loading: true,
        currentNotification: {},
        hasWebsocketSupport: "WebSocket" in window,
        // TODO: WSS
        ws: new WebSocket("ws://localhost:8080/ws/notifications"),
        recent: [],
        remaining: [],
        notificationsShownRecent: 10,
        notificationsShownRemaining: 10
      }
    },
    mounted() {
      this.getNotifications();
      if (this.hasWebsocketSupport) this.initWS();
    },
    computed: {
      recentList() {
        let lastElement = this.recent.length;
        this.recent.sort( (a, b) => {
          return b.timestamp - a.timestamp;
        });
        return this.recent.slice(0, Math.min(this.notificationsShownRecent, lastElement));
      },
      remainingList() {
        let lastElement = this.remaining.length;
        return this.remaining.slice(0, Math.min(this.notificationsShownRemaining, lastElement));
      },
    },
    methods: {
      updateCurrentNotification(notification) {
        this.currentNotification = notification;
      },
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
        let yesterday = new Date().getTime() - 24 * 60 * 60 * 1000;
        $.getJSON("/api/getNotifications").then((notifications) => {
          notifications.forEach( (it) => {
            if (it.timestamp > yesterday) {
              this.recent.push(it);
            } else {
              this.remaining.push(it);
            }
          });
          this.remaining.sort( (a, b) => {
            return b.timestamp - a.timestamp;
          });
          this.loading = false;
        });
      },
      showMoreRecent() {
        this.notificationsShownRecent += 10;
      },
      showMoreRemaining() {
        this.notificationsShownRemaining += 10;
      },
      initWS() {
        this.ws.onerror = () => {
          console.log("Socket error.")
        };

        this.ws.onopen = () => {
          console.log("Connected");
        };

        this.ws.onmessage = response => {
          this.recent.push(JSON.parse(response.data));
        };
      },
    }
  }
</script>
