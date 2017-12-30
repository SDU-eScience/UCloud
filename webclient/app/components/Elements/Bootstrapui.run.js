function runBootstrap() {

    // POPOVER
    // -----------------------------------

    $('[data-toggle="popover"]').popover();

    // TOOLTIP
    // -----------------------------------

    $('[data-toggle="tooltip"]').tooltip({
        container: 'body'
    });

}

export default runBootstrap;