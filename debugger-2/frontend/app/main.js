import "./frame.js";
import "./card.js";

function main() {
    const header = document.createElement("debug-header");
    const sidebar = document.createElement("debug-sidebar");

    document.body.appendChild(header);
    document.body.appendChild(sidebar);

    {
        const contentWrapper = document.createElement("div");
        contentWrapper.classList.add("content-wrapper")

        const content = document.createElement("div");
        content.classList.add("content");

        contentWrapper.appendChild(content);
        document.body.appendChild(contentWrapper);

        const card = document.createElement("debug-card");
        card.setAttribute("heading", "Heading test");
        card.innerHTML = "<p>Hello world</p>";

        content.appendChild(card);
    }
    console.log(sidebar);

    document.body.classList.add("ready");
}

main();