function initNestable() {
    var updateOutput = function(e) {
        var list = e.length ? e : $(e.target),
            output = list.data('output');
        if (window.JSON) {
            output.text(window.JSON.stringify(list.nestable('serialize'))); //, null, 2));
        } else {
            output.text('JSON browser support required for this demo.');
        }
    };

    // activate Nestable for list 1
    $('#nestable').each(function() {
        $(this).nestable({
            group: 1
        })
        .on('change', updateOutput);

        // output initial serialised data
        updateOutput($(this).data('output', $('#nestable-output')));
    });

    $('.js-nestable-action').on('click', function(e) {
        var target = $(e.target),
            action = target.data('action');
        if (action === 'expand-all') {
            $('.dd').nestable('expandAll');
        }
        if (action === 'collapse-all') {
            $('.dd').nestable('collapseAll');
        }
    });
}

export default initNestable;