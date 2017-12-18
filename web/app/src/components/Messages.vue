<template>
  <div class="container container-md">
    <loading-icon v-if="loading"></loading-icon>
    <div class="card" v-else>
      <div v-if="!messages.length">
        <h3 class="text-center"><small>No messages found.</small></h3>
      </div>
      <table class="table table-hover table-fixed va-middle">
        <tbody>
        <tr class="msg-display clickable" v-for="message in messages">
          <td class="wd-xxs">
            <div class="initial32 bg-indigo-500">{{ getInitials(message.from) }}</div>
          </td>
          <td class="text-ellipsis wd-sm">{{ message.from }}</td>
          <td class="text-ellipsis">{{ message.content }}</td>
          <td class="wd-xxs">
            <div class="pull-right dropdown">
              <button type="button" data-toggle="dropdown" class="btn btn-default btn-flat btn-flat-icon" aria-expanded="false"><em class="ion-android-more-vertical"></em></button>
              <ul role="menu" class="dropdown-menu md-dropdown-menu dropdown-menu-right">
                <li><a href="#"><em class="ion-reply icon-fw"></em>Reply</a></li>
                <li><a href="#"><em class="ion-forward icon-fw"></em>Forward</a></li>
                <li><a href="#"><em class="ion-trash-a icon-fw"></em>Spam</a></li>
              </ul>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <!-- Floating button for compose-->
    <div class="floatbutton">
      <ul class="mfb-component--br mfb-zoomin">
        <li class="mfb-component__wrap"><a @click="showComposeMessage()" class="mfb-component__button--main"><i class="mfb-component__main-icon--resting ion-edit"></i><i class="mfb-component__main-icon--active ion-edit"></i></a>
          <ul class="mfb-component__list"></ul>
        </li>
      </ul>
    </div>
    <!-- Modal content example for compose-->
    <div role="dialog" class="modal fade modal-compose">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-body">
            <form>
              <div class="mda-form-group">
                <div class="mda-form-control">
                  <input rows="3" v-model="composeMessage.to" aria-multiline="true" tabindex="0" aria-invalid="false" class="form-control" required>
                  <div class="mda-form-control-line"></div>
                  <label>To:</label>
                </div>
              </div>
              <div class="mda-form-group">
                <div class="mda-form-control">
                  <textarea rows="3" v-model="composeMessage.content" aria-multiline="true" tabindex="0" aria-invalid="false" class="form-control" required></textarea>
                  <div class="mda-form-control-line"></div>
                  <label>Message</label>
                </div>
              </div>
              <button type="button" @click="sendMessage()" class="btn btn-success">Send</button>
            </form>
          </div>
        </div>
      </div>
    </div>
    <!-- End Modal content example for compose-->
    <status-page></status-page>
  </div>
</template>

<script>
  import $ from 'jquery'
  import Vue from 'vue'
  import LoadingIcon from './LoadingIcon'
  import StatusPage from "./Status";

  export default {
    name: 'messages',
    components: {
      StatusPage,
      LoadingIcon},
    data() {
      return {
        loading: true,
        messages: [],
        composeMessage: {
          to: "",
          content: ""
        }
      }
    },
    mounted() {
      this.getMessages();
    },
    methods: {
      // TODO Websockets instead?
      getMessages() {
        this.loading = true;
        $.getJSON("/api/getMessages/").then( (messages) => {
          this.messages = messages.sort((a, b) => {
            return b.fromDate - a.fromDate;
          });

          this.loading = false;
        });
      },
      getInitials(name) {
        let splitName = name.split(" ");
        return splitName[0][0].toUpperCase() + splitName[splitName.length - 1][0].toUpperCase()
      },
      sendMessage() {
        // FIXME check empty strings
        $.post("/api/sendMessage", { to: this.composeMessage.to, content: this.composeMessage.content }).then( (result) => {
          if (result.statusCode) {
            swal("Message sent!");
            this.composeMessage.content = "";
            this.composeMessage.to = "";
          }
        });
      },
      showComposeMessage() {
        this.showCompose = true
      }
    }
  }
</script>
