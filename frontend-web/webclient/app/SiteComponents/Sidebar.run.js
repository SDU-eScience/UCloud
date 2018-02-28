import $ from 'jquery';

function sidebarNav() {
    let sidebarNav = document.querySelector(".sidebar-nav");

    sidebarNav.onclick = (event) => {
        let item = getItemElement(event);
        // check click is on a tag
        if (!item) return;
        let liParent = item.parentNode;
        let lis = item.parentNode.parentNode.children; // markup: ul > li > a
        // remove .active from children
        for (let li of lis) {
            console.log("DOM",li);
            if (li !== liParent) {
                li.classList.remove("active");
            }
        }
        let next = item.nextSibling;
        if (next && next.tagName === 'UL') {
            item.parentNode.classList.toggle('active');
            event.preventDefault();
        }
    };
    let layoutContainer = $('.layout-container');
    let $body = $('body');
    // Handler to toggle sidebar visibility on mobile
    $('#sidebar-toggler').click(function (e) {
        e.preventDefault();
        layoutContainer.toggleClass('sidebar-visible');
        // toggle icon state
        $(this).parent().toggleClass('active');
    });
    // Close sidebar when click on backdrop
    $('.sidebar-layout-obfuscator').click(function (e) {
        e.preventDefault();
        layoutContainer.removeClass('sidebar-visible');
        // restore icon
        $('#sidebar-toggler').parent().removeClass('active');
    });

    // Handler to toggle sidebar visibility on desktop
    $('#offcanvas-toggler').click(function (e) {
        e.preventDefault();
        $body.toggleClass('offcanvas-visible');
        // toggle icon state
        $(this).parent().toggleClass('active');
    });

    // remove desktop offcanvas when app changes to mobile
    // so when it returns, the sidebar is shown again
    window.addEventListener('resize', function () {
        if (window.innerWidth < 768) {
            $body.removeClass('offcanvas-visible');
            $('#offcanvas-toggler').parent().addClass('active');
        }
    });
}

// find the a element in click context
// doesn't check deeply, assuming two levels only
function getItemElement(event) {
    let element = event.target,
        parent = element.parentNode;
    if (element.tagName.toLowerCase() === "a") return element;
    if (parent.tagName.toLowerCase() === "a") return parent;
    if (parent.parentNode.tagName.toLowerCase() === "a") return parent.parentNode;
}

export default sidebarNav;
