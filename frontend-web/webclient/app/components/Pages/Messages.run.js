function initMessages() {
    var msgList = $('.msg-display');

    msgList.each(function() {
        var msg = $(this);

        msg.on('click', function(e){
            // Ignores drodown click to avoid opening modal at the same time
            if( $(e.target).is('.dropdown') ||
                $(e.target).parents('.dropdown').length > 0  ) {
                return;
            }
            // Open modal
            $('.modal-message').modal();

        });

    });

    $('#compose').on('click', function(){
        $('.modal-compose').modal();
    });

}

export default initMessages;
