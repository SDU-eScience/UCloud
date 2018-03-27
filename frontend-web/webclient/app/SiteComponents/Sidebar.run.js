export default sidebarNav = () => {
    let sidebarNav = document.querySelector(".sidebar-nav");

    sidebarNav.onclick = (event) => {
        let item = getItemElement(event);
        // check click is on a tag
        if (!item) return;
        let liParent = item.parentNode;
        let lis = item.parentNode.parentNode.children; // markup: ul > li > a
        // remove .active from children
        for (let li of lis) {
            if (li !== liParent) {
                li.classList.remove("active");
            }
        }
        let next = item.nextSibling;
        if (next && next.tagName === "UL") {
            item.parentNode.classList.toggle('active');
            event.preventDefault();
        }
    };
    let layoutContainer = document.querySelector(".layout-container");
    let body = document.querySelector("body");

    // Handler to toggle sidebar visibility on mobile
    document.querySelector("#sidebar-toggler").onclick = (e) => {
        e.preventDefault();
        layoutContainer.classList.toggle("sidebar-visible");
    };
    // Close sidebar when click on backdrop
    document.querySelector(".sidebar-layout-obfuscator").onclick = (e) => {
        e.preventDefault();
        layoutContainer.classList.remove('sidebar-visible');
        // restore icon
        document.querySelector("#sidebar-toggler").parentNode.classList.remove("active");
    };

    // Handler to toggle sidebar visibility on desktop
    document.querySelector("#offcanvas-toggler").onclick = (e) => {
        e.preventDefault();
        body.classList.toggle("offcanvas-visible");
    };

    // remove desktop offcanvas when app changes to mobile
    // so when it returns, the sidebar is shown again
    window.addEventListener("resize", () => {
        if (window.innerWidth < 768) {
            body.classList.remove("offcanvas-visible");
            document.querySelector("#offcanvas-toggler").parentNode.classList.add("active");
        }
    });
}

// find the a element in click context
// doesn't check deeply, assuming two levels only
const getItemElement = (event) => {
    let element = event.target,
        parent = element.parentNode;
    if (element.tagName.toLowerCase() === "a") return element;
    if (parent.tagName.toLowerCase() === "a") return parent;
    if (parent.parentNode.tagName.toLowerCase() === "a") return parent.parentNode;
}