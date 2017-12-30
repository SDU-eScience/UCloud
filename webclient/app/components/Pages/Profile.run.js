function initProfile() {

    if (!$.fn.editable) return;

    var editables = $('.is-editable, #gender');

    $('#edit-enable').click(function(e) {
        e.preventDefault();
        editables.editable('toggleDisabled');
        $('#edit-disable').removeClass('hidden');
        $('#edit-enable').addClass('hidden');
    });
    $('#edit-disable').click(function(e) {
        e.preventDefault();
        editables.editable('toggleDisabled');
        $('#edit-enable').removeClass('hidden');
        $('#edit-disable').addClass('hidden');
    });


    $('.is-editable').each(function() {
        var opts = $(this).data();
        $(this).editable({
            showbuttons: 'bottom',
            disabled: true,
            mode: opts.mode || 'inline',
            type: opts.type || 'text'
        });
    });

    $('#gender').editable({
        // prepend: "not selected",
        disabled: true,
        mode: 'inline',
        url: '',
        source: [{
            value: 1,
            text: 'Male'
        }, {
            value: 2,
            text: 'Female'
        }]
    });

}

export default initProfile;
