function initSettings() {

    // Themes setup
    var themes = [
        'theme-1',
        'theme-2',
        'theme-3',
        'theme-4',
        'theme-5',
        'theme-6',
        'theme-7',
        'theme-8',
        'theme-9'
    ];

    var body = $('body');
    var $document = $(document);
    var header = $('.layout-container > header');
    var sidebar = $('.layout-container > aside');
    var brand = sidebar.find('.sidebar-header');
    var content = $('.layout-container > main');

    // Handler for themes preview
    $document.on('change', 'input[name="setting-theme"]:radio', function() {
        var index = this.value;
        if (themes[index]) {
            body.removeClass(themeClassname);
            body.addClass(themes[index]);
        }
    });
        // Regular expression for the pattern bg-* to find the background class
        function themeClassname(index, css) {
            var cmatch = css.match(/(^|\s)theme-\S+/g);
            return (cmatch || []).join(' ');
        }


    // Handler for menu links
    $document.on('change', 'input[name="headerMenulink"]:radio', function() {
        var menulinks = $('.menu-link');
        // remove allowed classses
        menulinks.removeClass('menu-link-slide menu-link-arrow menu-link-close');
        // Add selected
        menulinks.addClass(this.value);
    });

    // Handlers for layout variations
    // var lContainer = $('.layout-container');
    $document.on('change', '#sidebar-showheader', function() {
        brand[this.checked ? 'show' : 'hide']();
    });

    var sidebarToolbar = $('.sidebar-toolbar');

    $document.on('change', '#sidebar-showtoolbar', function() {
        sidebarToolbar[this.checked ? 'show' : 'hide']();
    });

    $document.on('change', '#sidebar-offcanvas', function() {
        body[this.checked ? 'addClass' : 'removeClass']('sidebar-offcanvas');
    });
}

export default initSettings;
