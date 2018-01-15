import $ from 'jquery';

function initSvgReplace() {
    var elements = $('[data-svg-replace]');

    elements.each(function() {
        var el = $(this);
        var src = el.data('svgReplace');

        if (!src || src.indexOf('.svg') < 0)
            throw "only support for SVG images";
        // return /*only support for SVG images*/;

        $.get(src).success(function(res) {
            var $svg = $(res).find('svg');
            $svg = $svg.removeAttr('xmlns:a');
            el.replaceWith($svg);
        })
    })

}

export default initSvgReplace;