<template>
  <div class="container container-md">
    <div class="card">
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
        <li class="mfb-component__wrap"><a id="compose" href="#" class="mfb-component__button--main"><i class="mfb-component__main-icon--resting ion-edit"></i><i class="mfb-component__main-icon--active ion-edit"></i></a>
          <ul class="mfb-component__list"></ul>
        </li>
      </ul>
    </div>
    <!-- Modal content example for messages-->
    <div tabindex="-1" role="dialog" class="modal modal-right modal-auto-size fade modal-message" style="display: none;">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-header">
            <div class="media m0 pv">
              <div class="pull-right">
                <div data-dismiss="modal" class="clickable"><sup><em class="ion-close-round text-muted icon-2x"></em></sup></div>
              </div>
              <div class="media-left"><a href="#"><img src="img/user/04.jpg" alt="User" class="media-object img-circle thumb64"></a></div>
              <div class="media-body media-middle">
                <h4 class="media-heading">Patrick Brooks</h4><span class="text-muted">patrick.brooks@mail.com</span>
              </div>
            </div>
          </div>
          <div class="modal-body">
            <!-- START dropdown-->
            <div class="pull-right dropdown">
              <button type="button" data-toggle="dropdown" class="btn btn-default btn-flat btn-flat-icon"><em class="ion-android-more-vertical"></em></button>
              <ul role="menu" class="dropdown-menu md-dropdown-menu dropdown-menu-right">
                <li><a href="#"><em class="ion-star icon-fw"></em>Favorite</a></li>
                <li><a href="#"><em class="ion-ios-box icon-fw"></em>Archive</a></li>
                <li><a href="#" data-dismiss="modal"><em class="ion-trash-a icon-fw"></em>Discard</a></li>
              </ul>
            </div>
            <!-- END dropdown-->
            <p class="text-muted">26 aug 2015 10:30 am</p>
            <h4 class="mt">Nam vel tristique dolor.</h4>
            <div class="reader-block">
              <p>Praesent vel nisi nibh. Vestibulum purus ipsum, rutrum varius aliquam id, rhoncus eget neque. Curabitur sodales nisl eu enim suscipit eu faucibus dui mattis.</p>
              <p>Aenean risus nulla, aliquam sed aliquam vitae, ultricies non elit. Suspendisse nunc massa, euismod eu egestas quis, molestie sit amet mauris. Mauris eu lacus massa, vel condimentum lectus. Quisque quam justo, cursus sit amet pretium vel, viverra vel leo. Nullam lobortis consectetur hendrerit. Aenean rhoncus, est vel vestibulum tristique, ante lectus elementum augue, eu dictum turpis dui ut ipsum. Lorem ipsum dolor sit amet, consectetur adipiscing elit.</p>
              <p>Pellentesque ac ligula varius nisl laoreet pretium quis quis tellus. Praesent et mauris lacus, non volutpat augue.</p>
            </div>
          </div>
          <div class="modal-footer">
            <div class="text-left">
              <p>Reply</p>
            </div>
            <div class="media m0 pv">
              <div class="media-left"><a href="#"><img src="img/user/01.jpg" alt="User" class="media-object img-circle thumb32"></a></div>
              <div class="media-body media-middle">
                <form action="">
                  <div class="mda-form-group">
                    <div class="mda-form-control pt0">
                      <textarea rows="3" aria-multiline="true" tabindex="0" aria-invalid="false" placeholder="Write here..." class="form-control"></textarea>
                      <div class="mda-form-control-line"></div>
                    </div>
                  </div>
                </form>
              </div>
            </div>
            <button type="button" data-dismiss="modal" class="btn btn-success">Send</button>
          </div>
        </div>
      </div>
    </div>
    <!-- End Modal content example for messages-->
    <!-- Modal content example for compose-->
    <div tabindex="-1" role="dialog" class="modal fade modal-compose" style="display: none;">
      <div class="modal-dialog">
        <div class="modal-content">
          <div class="modal-body">
            <form action="">
              <div class="mda-form-group">
                <div class="mda-form-control">
                  <input rows="3" aria-multiline="true" tabindex="0" aria-invalid="false" class="form-control">
                  <div class="mda-form-control-line"></div>
                  <label>To:</label>
                </div>
              </div>
              <div class="mda-form-group">
                <div class="mda-form-control">
                  <textarea rows="3" aria-multiline="true" tabindex="0" aria-invalid="false" class="form-control"></textarea>
                  <div class="mda-form-control-line"></div>
                  <label>Message</label>
                </div>
              </div>
              <button type="button" data-dismiss="modal" class="btn btn-success">Send</button>
            </form>
          </div>
        </div>
      </div>
    </div>
    <!-- End Modal content example for compose-->
  </div>
</template>

Messages structure:

Messages: {
  fromDate: Date (Millis)
  from: String,
  content: String
}


<script>
  import $ from 'jquery'
  import Vue from 'vue'
  import LoadingIcon from './LoadingIcon'

  export default {
    name: 'messages',
    components: {LoadingIcon},
    data() {
      return {
        currentMessage: {},
        messages: []
      }
    },
    mounted() {
      this.getMessages();
    },
    methods: {
      // TODO Websockets instead?
      getMessages() {
        $.getJSON("/api/getMessages/").then( (messages) => {
          console.log(messages);
          this.messages = messages.sort((a, b) => {
            return a.fromDate - b.fromDate;
          })
        });
      },
      getInitials(name) {
        let splitName = name.split(" ");
        let initials = splitName[0][0].toUpperCase() + splitName[splitName.length - 1][0].toUpperCase();
        return initials
      }
    }
  }
</script>
