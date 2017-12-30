import $ from 'jquery';

function sidebarNav() {

    var $sidebarNav = $('.sidebar-nav');
    var $sidebarContent = $('.sidebar-content');

    activate($sidebarNav);

    $sidebarNav.on('click', function(event) {
        var item = getItemElement(event);
        // check click is on a tag
        if (!item) return;

        var ele = $(item),
            liparent = ele.parent()[0];

        var lis = ele.parent().parent().children(); // markup: ul > li > a
        // remove .active from childs
        lis.find('li').removeClass('active');
        // remove .active from siblings ()
        $.each(lis, function(idx, li) {
            if (li !== liparent)
                $(li).removeClass('active');
        });

        var next = ele.next();
        if (next.length && next[0].tagName === 'UL') {
            ele.parent().toggleClass('active');
            event.preventDefault();
        }
    });

    // find the a element in click context
    // doesn't check deeply, asumens two levels only
    function getItemElement(event) {
        var element = event.target,
            parent = element.parentNode;
        if (element.tagName.toLowerCase() === 'a') return element;
        if (parent.tagName.toLowerCase() === 'a') return parent;
        if (parent.parentNode.tagName.toLowerCase() === 'a') return parent.parentNode;
    }

    function activate(sidebar) {
        sidebar.find('a').each(function() {
            var href = $(this).attr('href').replace('#', '');
            if (href !== '' && window.location.href.indexOf(href) >= 0) {
                var item = $(this).parents('li').addClass('active');
                // Animate scrolling to focus active item
                // $sidebarContent.animate({
                //     scrollTop: $sidebarContent.scrollTop() + item.position().top
                // }, 1200);
                return false; // exit foreach
            }
        });
    }

    var layoutContainer = $('.layout-container');
    var $body = $('body');
    // Handler to toggle sidebar visibility on mobile
    $('#sidebar-toggler').click(function(e) {
        e.preventDefault();
        layoutContainer.toggleClass('sidebar-visible');
        // toggle icon state
        $(this).parent().toggleClass('active');
    });
    // Close sidebar when click on backdrop
    $('.sidebar-layout-obfuscator').click(function(e) {
        e.preventDefault();
        layoutContainer.removeClass('sidebar-visible');
        // restore icon
        $('#sidebar-toggler').parent().removeClass('active');
    });

    // Handler to toggle sidebar visibility on desktop
    $('#offcanvas-toggler').click(function(e) {
        e.preventDefault();
        $body.toggleClass('offcanvas-visible');
        // toggle icon state
        $(this).parent().toggleClass('active');
    });

    // remove desktop offcanvas when app changes to mobile
    // so when it returns, the sidebar is shown again
    window.addEventListener('resize', function() {
        if (window.innerWidth < 768) {
            $body.removeClass('offcanvas-visible');
            $('#offcanvas-toggler').parent().addClass('active');
        }
    });

}

export default sidebarNav;
