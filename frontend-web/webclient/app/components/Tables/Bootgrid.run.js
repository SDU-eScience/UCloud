function tableBootgrid() {

    if ( !$.fn.bootgrid ) return;

    var ioniconCss = {
        icon: "icon",
        iconColumns: "ion-ios-list-outline",
        iconDown: "ion-chevron-down",
        iconRefresh: "ion-refresh",
        iconSearch: "ion-search",
        iconUp: "ion-chevron-up"
    }

    $('#bootgrid-basic').bootgrid({
        css: ioniconCss
    });

    $('#bootgrid-selection').bootgrid({
        css: ioniconCss,
        selection: true,
        multiSelect: true,
        rowSelect: true,
        keepSelection: true,
        templates: {
            select: '<label class="mda-checkbox">' +
                        '<input name="select" type="{{ctx.type}}" class="{{css.selectBox}}" value="{{ctx.value}}" {{ctx.checked}} />' +
                        '<em class="bg-warning"></em>' +
                    '</label>'
        }
    })
    ;

    $('#bootgrid-command').bootgrid({
        css: ioniconCss,
        formatters: {
            commands: function(column, row) {
                return '<button type="button" class="btn btn-flat btn-sm btn-info" data-row-id="' + row.id + '"><em class="ion-edit"></em></button>' +
                    '<button type="button" class="btn btn-flat btn-sm btn-danger" data-row-id="' + row.id + '"><em class="ion-trash-a"></em></button>';
            }
        }
    });

}

export default tableBootgrid;
