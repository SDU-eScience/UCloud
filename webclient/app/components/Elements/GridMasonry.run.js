function runMasonry() {

    // if( ! $.fn.imagesLoaded ) return;

    // init Masonry after all images have loaded
    var $grid = $('.grid').imagesLoaded(function() {
        new Masonry( $grid[0], {
            itemSelector: '.grid-item',
            percentPosition: true,
            columnWidth: '.grid-sizer'
        });
    });
}

export default runMasonry;