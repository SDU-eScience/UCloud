function style(style) {
    const result = document.createElement("style");
    result.textContent = style;
    return result;
}

class DocProperty extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        shadow.innerHTML = this.innerHTML;
        shadow.append(style(`
            :host {
                display: block;
                margin: 32px 0;
            }

            .top-line {
                border-top: 1px solid rgb(100, 100, 100);
                padding-top: 32px;
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .is-required {
                color: red;
            }

            .default-value::before {
                content: "(Default: ";
            }

            .default-value::after {
                content: ")";
            }

            section, doc-sealed-container {
                margin-left: 40px;
            }

            code {
                font-family: 'JetBrains Mono', monospace;
                background-color: #e1e3e5;
                padding: 4px;
                border-radius: 4px;
            }
        `));

        const name = this.getAttribute("name");
        const type = this.getAttribute("type");
        const isRequired = this.getAttribute("required") !== null;
        const defaultValue = this.getAttribute("default");

        const docWrapper = document.createElement("div");
        docWrapper.classList.add("top-line");

        const nameElem = document.createElement("code");
        nameElem.classList.add("name")
        nameElem.innerText = name;
        docWrapper.append(nameElem);

        const typeElem = document.createElement("div");
        typeElem.classList.add("type")
        typeElem.innerText = type;
        docWrapper.append(typeElem);

        if (defaultValue) {
            const elem = document.createElement("span");
            elem.classList.add("default-value");
            elem.innerText = defaultValue;
            docWrapper.appendChild(elem);
        }

        if (isRequired) {
            const spacer = document.createElement("div")
            spacer.style.flexGrow = "1";
            docWrapper.append(spacer);

            const isRequiredElem = document.createElement("span");
            isRequiredElem.classList.add("is-required");
            isRequiredElem.textContent = "Required";
            docWrapper.append(isRequiredElem);
        }

        shadow.prepend(docWrapper);
    }
}

customElements.define("doc-prop", DocProperty);

class DocPropContainer extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        shadow.innerHTML = this.innerHTML;
        const canCollapse = this.getAttribute("can-collapse") !== null || this.getAttribute("collapsed") !== null;

        if (canCollapse) {
            const container = document.createElement("div");
            container.classList.add("collapsible");
            let isFirst = true;
            const childProps = shadow.querySelectorAll("doc-prop");
            for (const child of childProps) {
                if (isFirst) {
                    isFirst = false;
                    continue;
                }

                container.appendChild(child);
            }

            if (childProps.length > 1) {
                const collapseToggle = document.createElement("a");
                collapseToggle.ariaRoleDescription = "button";
                collapseToggle.href = "javascript:;";
                collapseToggle.addEventListener("click", (e) => {
                    e.preventDefault();
                    this.toggleAttribute("collapsed");
                });

                shadow.appendChild(collapseToggle);
                shadow.appendChild(container);

                const onCollapsed = () => {
                    const isCollapsed = this.getAttribute("collapsed") !== null;
                    container.style.display = isCollapsed ? "none" : "block";
                    collapseToggle.text = isCollapsed ? "Show all details" : "Hide details";
                };

                onCollapsed();
                new MutationObserver(onCollapsed).observe(this, { attributes: true, attributeFilter: ["collapsed"] });
            }

            shadow.append(style(`
                :host[collapsed] .detailed {
                    display: none;
                }

                .collapsible {
                    margin-left: 40px;
                }
            `));
        }
    }
}

customElements.define("doc-prop-container", DocPropContainer);


class DocSealedContainer extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        shadow.innerHTML = this.innerHTML;
        shadow.append(style(`
            :host {
                display: block;
            }
        `));
        const firstPropContainer = shadow.querySelector("doc-prop-container");
        if (firstPropContainer) {
            const instructions = document.createElement("p");
            instructions.innerHTML = `
                Select <i>one</i> of the following:
            `;
            shadow.insertBefore(instructions, firstPropContainer);
        }
    }
}

customElements.define("doc-sealed-container", DocSealedContainer);

class DocSnippet extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });

        let text = this.textContent;
        text = text.replace("\t", "    ");
        if (text.startsWith("\n")) text = text.substring(1);
        if (text.startsWith("\r\n")) text = text.substring(2);

        const lines = text.split("\n");
        if (lines.length === 0) return;

        let numberOfSpaces = 0;
        const firstLine = lines[0];
        for (let i = 0; i < firstLine.length; i++) {
            if (firstLine[i] === " ") numberOfSpaces++;
            else break;
        }

        let newText = "";
        for (const line of lines) {
            if (newText !== "") newText += "\n";
            newText += line.substring(numberOfSpaces);
        }

        shadow.innerHTML = `<pre><code>${newText}</code></pre>`;

        shadow.append(style(`
            pre > code {
                background-color: unset;
            }

            pre {
                background-color: #282c35;
                color: #ffffff;
                border-radius: 8px;
                padding: 16px;
                overflow: auto;
            }
        `));
    }
}

customElements.define("doc-snippet", DocSnippet);

class DocTodo extends HTMLElement {
    constructor() {
        super();

        this.innerText = `This section is still under construction. The section can be misleading, incorrect or 
            simply incomplete. Use any information from this section with caution.`;
    }
}

customElements.define("doc-todo", DocTodo);

class DocTable extends HTMLElement {
    shadow;

    constructor() {
        super();
        this.shadow = this.attachShadow({ mode: "open" });
        this.rerender();
        new MutationObserver(() => this.rerender()).observe(this, { attributes: false, childList: true, subtree: true });
    }

    rerender() {
        this.shadow.innerHTML = this.innerHTML;
        this.shadow.append(style(`
            :host {
                display: block;
                border: var(--border);
                border-radius: var(--radius);
                margin-top: 16px;
                margin-bottom: 16px;

                --radius: 8px;
                --border: 1px solid #c1c7d4;
                --header-color: #f0f4ff;
                --cell-padding: 16px;
            }

            table {
                text-align: left;
                border-collapse: collapse;
                width: 100%;
            }

            tr {
                border-top: var(--border);
                border-bottom: var(--border);
                vertical-align: top;
            }

            th, td {
                border-left: var(--border);
                border-right: var(--border);
                padding: var(--cell-padding);
            }

            th:first-child, td:first-child {
                border-left: 0;
            }

            th:last-child, td:last-child {
                border-right: 0;
            }

            tr:first-child {
                border-top: 0;
            }

            thead tr:first-child > td:first-child, thead tr:first-child > th:first-child {
                border-top-left-radius: var(--radius);
            }

            thead tr:first-child > td:last-child, thead tr:first-child > th:last-child {
                border-top-right-radius: var(--radius);
            }

            tbody tr:last-child {
                border-bottom: 0;
            }

            thead tr {
                background: var(--header-color);
            }

            code {
                font-family: 'JetBrains Mono', monospace;
                background-color: #e1e3e5;
                padding: 4px;
                border-radius: 4px;
            }

            input[type=checkbox] {
                width: 24px;
                height: 24px;
            }
        `));
    }
}

customElements.define("doc-table", DocTable);

class DocHeader extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const style = document.createElement("style");
        style.textContent = `
            header {
                --header-size: 64px;
                background: rgb(0, 106, 255);
                color: #ffffff;
                height: var(--header-size);
                width: 100vw;
                display: flex;
                align-items: center;
                box-shadow: rgb(0 0 0 / 20%) 0px 3px 3px -2px, rgb(0 0 0 / 14%) 0px 3px 4px 0px, rgb(0 0 0 / 12%) 0px 1px 8px 0px;
            }

            header a {
                color: white;
                text-decoration: none;
                font-size: 120%;
            }

            header .logo {
                display: flex;
                align-items: center;
                gap: 15px;
                margin-left: 15px;
            }

            header .logo img {
                width: 38px;
            }

            header .logo {
                font-size: 150%;
            }

            header .middle, header .right {
                flex-grow: 1;
                display: flex;
                gap: 15px;
                margin: 0 15px;
            }

            header .middle {
                position: absolute;
                width: 500px;
                display: flex;
                justify-content: center;
                align-items: center;
                gap: 64px;
                left: calc((100% - 500px) / 2);
            }

            header .right {
                justify-content: end;
            }

            @media screen and (max-width: 1200px) {
                header .middle {
                    position: relative;
                    left: unset;
                    width: unset;
                    gap: 16px;
                    justify-content: unset;
                }
            }

            @media screen and (max-width: 950px) {
                header .right {
                    display: none;
                }
            }

            @media screen and (max-width: 600px) {
                header .middle {
                    display: none;
                }
            }

            .btn {
                display: block;
                text-decoration: none;
                user-select: none;
                background-color: black;
                border-radius: 15px;
                font-size: 120%;
                padding: 10px 16px;
                color: white;
                align-items: center;
                justify-content: center;
                display: flex;
                flex-direction: column;
            }

            .btn.secondary {
                color: black;
                background: white;
            }

            .btn.github {
                display: flex;
                gap: 8px;
                flex-direction: row;
            }

            .btn.github img {
                width: 28px;
            }
        `;

        shadow.appendChild(style);

        shadow.innerHTML += `
            <header>
                <a href="/" class="logo">
                    <img src="/images/deic.svg" alt="DeiC Logo">
                    DeiC Integration Portal
                </a>

                <div class="middle">
                </div>

                <div class="right">
                    <a href="https://github.com/SDU-eScience/UCloud" target="_blank" class="btn secondary github">
                        <img src="/images/github-dark.png">
                        Source code
                    </a>
                    <a href="https://github.com/SDU-eScience/UCloud/releases" target="_blank" class="btn primary">Download</a>
                </div>
            </header>
        `;
    }
}

customElements.define("doc-header", DocHeader);

class DocRecipes extends HTMLElement {
    static recipes = [
        { 
            title: "Slurm + distributed file system",
            description: "A traditional HPC system consisting of Slurm and a distributed POSIX file system",
            href: "/recipes/slurm"
        },
        /*
        { 
            title: "Keycloak",
            description: "Connection procedure using Keycloak",
            href: "/recipes/keycloak"
        },
        { 
            title: "Kubernetes compute with storage",
            description: "A complete Kubernetes based setup which exposes storage from any compatible distributed file-system",
            href: "/recipes/kubernetes"
        },
        { 
            title: "Puhuri integration",
            description: "Show-cases a collection of plugins which together form the basis of an integration with Puhuri",
            href: "/recipes/puhuri"
        },
        */
    ];

    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const tableWrapper = document.createElement("doc-table");
        const table = document.createElement("table");
        const thead = document.createElement("thead");
        const tbody = document.createElement("tbody");

        shadow.append(tableWrapper);
        tableWrapper.append(table);
        table.append(thead);
        table.append(tbody);

        const tr = (target, cols, header) => {
            const row = document.createElement("tr");
            target.append(row);
            const result = [];
            for (let i = 0; i < cols; i++) {
                const cell = document.createElement(header ? "th" : "td");
                row.append(cell);
                result.push(cell);
            }
            return result;
        }

        {
            const row = tr(thead, 2, true);
            row[0].textContent = "Name";
            row[1].textContent = "Description";
        }

        for (const recipe of DocRecipes.recipes) {
            const row = tr(tbody, 2);

            const anchor = document.createElement("a");
            anchor.href = recipe.href;
            anchor.textContent = recipe.title;
            row[0].append(anchor);

            row[1].textContent = recipe.description;
        }

        document.create
    }
}

customElements.define("doc-recipes", DocRecipes);

class DocTableOfContents extends HTMLElement {
    static sections = [
        { title: "Getting started", href: "/getting-started" },
        { 
            title: "Configuration", 
            children: [
                { title: "Core", href: "/core" },
                { title: "Server", href: "/server" },
                { title: "Products", href: "/products" },
                { 
                    title: "Plugins", 
                    href: "/plugins",
                    children: [
                        { title: "Connections", href: "/plugins/connections.html" },
                        { title: "Projects", href: "/plugins/projects.html" },
                        { title: "Jobs", href: "/plugins/jobs.html" },
                        { title: "Drives", href: "/plugins/fileCollections.html" },
                        { title: "Files", href: "/plugins/files.html" },
                        { title: "Public links", href: "/plugins/ingresses.html" },
                        { title: "Public IPs", href: "/plugins/publicIps.html" },
                        { title: "Software licenses", href: "/plugins/licenses.html" },
                        { title: "Shares", href: "/plugins/shares.html" },
                        { title: "Allocations", href: "/plugins/allocations.html" },
                    ]
                },
            ]
        },
        {
            title: "Recipes",
            href: "/recipes",
            children: DocRecipes.recipes.map(it => ({
                title: it.title,
                href: it.href
            }))
        }
    ];

    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const selfEntry = DocTableOfContents.findSelf();

        shadow.append(style(`
            :host {
                display: block;
                height: calc(100vh - 64px);
                width: 100%;
                background: #F5F7F9;
                padding-top: 8px;
                padding-left: 8px;
                border-right: 1px #ddd solid;
                overflow-y: auto;
            }

            :host(.force) {
                background: unset;
            }

            :host > ul {
                margin: 0;
                padding: 0;
            }

            li.active > doc-link::part(link) {
                font-weight: 800;
            }

            li {
                margin: 16px;
            }

            ul {
                list-style: none;
            }

            ul ul {
                list-style: disc;
                padding-left: 32px;
            }

            ul doc-link::part(link) {
                text-decoration: none;
                color: black;
                font-size: 18px;
            }

            .link-to-toc {
                display: none;
            }

            @media screen and (max-width: 900px) {
                :host(:not(.force)) {
                    height: 48px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 0;
                }

                :host(:not(.force)) ul {
                    display: none;
                }

                :host(:not(.force)) .link-to-toc {
                    display: block;
                    text-align: center;
                }
            }
        `));

        const rootList = document.createElement("ul");
        for (const section of DocTableOfContents.sections) {
            this.renderNode(rootList, section, selfEntry);
        }

        const linkToToc = document.createElement("doc-link");
        linkToToc.classList.add("link-to-toc");
        linkToToc.setAttribute("href", "/toc.html");
        linkToToc.textContent = "Table of contents";

        shadow.append(rootList, linkToToc);
    }

    renderNode(domNode, sectionNode, selfNode) {
        const itemNode = document.createElement("li");

        if (selfNode && selfNode.href == sectionNode.href) {
            itemNode.classList.add("active");
        }

        const linkNode = document.createElement("doc-link");
        if (sectionNode.href) linkNode.setAttribute("href", sectionNode.href);
        else linkNode.setAttribute("href", "#");
        linkNode.textContent = sectionNode.title;

        itemNode.append(linkNode);
        domNode.append(itemNode);

        const children = sectionNode["children"];
        if (children) {
            const subList = document.createElement("ul");
            itemNode.append(subList);
            for (const subchild of children) {
                this.renderNode(subList, subchild, selfNode);
            }
        }

        domNode.append(itemNode);
    }

    static findSelf(children, startIdx) {
        const idxWrapper = startIdx ?? { idx: 0 };
        let selfUrl = window.location.pathname.replace("/index.html", "");
        if (selfUrl.startsWith("/versions/")) {
            const components = selfUrl.split("/").filter(it => it.length > 0);
            components.splice(0, 2);
            selfUrl = "/" + components.join("/");
        }
        if (selfUrl.length && selfUrl[selfUrl.length - 1] === '/') {
            selfUrl = selfUrl.substring(0, selfUrl.length - 1);
        }

        for (const child of (children ?? DocTableOfContents.sections)) {
            child.index = idxWrapper.idx++;
            if (child.href === selfUrl) return child;

            const subchildren = child["children"];
            if (subchildren) {
                const res = DocTableOfContents.findSelf(subchildren, idxWrapper);
                if (res) return res;
            }
        }

        return null;
    }

    static findIndexed(searchIndex, children, startIdx) {
        const idxWrapper = startIdx ?? { idx: 0 };
        for (const child of (children ?? DocTableOfContents.sections)) {
            child.index = idxWrapper.idx
            if (idxWrapper.idx === searchIndex) {
                return child;
            }

            idxWrapper.idx++;

            const subchildren = child["children"];
            if (subchildren) {
                const res = DocTableOfContents.findIndexed(searchIndex, subchildren, idxWrapper);
                if (res) return res;
            }
        }

        return null;
    }
}

customElements.define("doc-toc", DocTableOfContents);

class DocNavigationButtons extends HTMLElement {
    constructor() {
        super();

        const selfEntry = DocTableOfContents.findSelf();
        if (!selfEntry) return;

        const selfIndex = selfEntry.index;

        let previousEntry = DocTableOfContents.findIndexed(selfIndex - 1);
        let nextEntry = DocTableOfContents.findIndexed(selfIndex + 1);

        if (previousEntry && !previousEntry.href) previousEntry = DocTableOfContents.findIndexed(selfIndex - 2);
        if (nextEntry && !nextEntry.href) nextEntry = DocTableOfContents.findIndexed(selfIndex + 2);

        const shadow = this.attachShadow({ mode: "open" });
        shadow.append(style(`
            :host {
                width: 100%;
                display: flex;
                gap: 32px;
            }

            .spacer {
                flex-grow: 1;
            }

            a {
                display: block;
                text-decoration: none;
                user-select: none;
                background-color: white;
                color: black;
                border-radius: 15px;
                font-size: 120%;
                padding: 16px;
                align-items: center;
                justify-content: center;
                display: flex;
                flex-direction: column;
                border: 2px solid black;
                min-width: 200px;
                box-sizing: border-box;
            }

            a span {
                color: rgb(150, 150, 150);
                font-size: 80%;
                display: block;
            }

            @media screen and (max-width: 700px) {
                a {
                    width: 100%;
                }

                :host {
                    flex-direction: column;
                    gap: 16px;
                }
            }
        `));

        const previousDom = document.createElement("div");
        const nextDom = document.createElement("div");
        const spacer = document.createElement("div");
        spacer.classList.add("spacer");
        shadow.append(previousDom, spacer, nextDom);

        if (previousEntry) {
            const link = document.createElement("a");
            link.href = DocLink.createLinkHref(previousEntry.href);
            link.textContent = "⬅️ Previous";

            const span = document.createElement("span");
            span.textContent = previousEntry.title;
            link.append(span);

            previousDom.append(link);
        }

        if (nextEntry) {
            const link = document.createElement("a");
            link.href = DocLink.createLinkHref(nextEntry.href);
            link.textContent = "Next ➡️";

            const span = document.createElement("span");
            span.textContent = nextEntry.title;
            link.append(span);

            nextDom.append(link);
        }
    }
}

customElements.define("doc-nav-buttons", DocNavigationButtons);

class DocLink extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const render = () => {
            shadow.innerHTML = "";

            const hrefAttribute = this.getAttribute("href");
            const targetAttribute = this.getAttribute("target");

            const linkTag = document.createElement("a");
            linkTag.href = DocLink.createLinkHref(hrefAttribute);
            linkTag.part = "link";
            if(targetAttribute) linkTag.target = targetAttribute;
            linkTag.innerHTML = this.innerHTML;
            shadow.append(linkTag)
        };
        render();


        new MutationObserver(render).observe(this, { attributes: true, childList: true, subtree: true });
    }

    static createLinkHref(href) {
        let prefix;
        {
            const pathName = window.location.pathname;
            if (!pathName.startsWith("/versions/")) {
                prefix = "";
            } else {
                const components = pathName.split("/").filter(it => it.length > 0);
                if (components.length <= 1) {
                    prefix = "";
                } else {
                    prefix = `/versions/${components[1]}`;
                }
            }
        }
        return prefix + href;
    }
}

customElements.define("doc-link", DocLink);

class DocArticleToc extends HTMLElement {
    static idGenerator = 0;

    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const listNode = document.createElement("ul");
        const headings = document.body.querySelectorAll(".content h2");
        console.log("headings", headings);
        headings.forEach(heading => {
            if (heading.classList.contains("summary")) return;
            let id = heading.id;
            if (!id) {
                id = heading.id = `section-${DocArticleToc.idGenerator}`;
            }

            const itemNode = document.createElement("li");
            const sectionLink = document.createElement("a");
            sectionLink.href = `#${id}`;
            sectionLink.textContent = heading.textContent;
            itemNode.append(sectionLink);
            listNode.append(itemNode);

            DocArticleToc.idGenerator++;
        });

        if (headings.length > 0) {
            const heading = document.createElement("h1");
            heading.textContent = "In this article";

            shadow.append(heading, listNode, style(`
                :host {
                    margin: 0 16px;
                }

                h1 {
                    font-size: 1.5rem;
                    font-weight: 500;
                    margin-top: 26px;
                }

                a {
                    color: black;
                    text-decoration: none;
                }

                a:hover {
                    color: blue;
                }

                ul {
                    padding: 0;
                }

                li {
                    list-style: none;
                    padding: 0;
                    padding-left: 8px;
                    border-left: 2px solid black;
                    line-height: 2;
                }
            `));
        }
    }
}

customElements.define("doc-article-toc", DocArticleToc);

class DocImage extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });
        const src = this.getAttribute("src");
        const alt = this.getAttribute("alt");

        const linkNode = document.createElement("a");
        linkNode.href = src;
        linkNode.target = "_blank";

        const imageNode = document.createElement("img");
        imageNode.src = src;
        if (alt) imageNode.alt = alt;

        linkNode.append(imageNode);
        shadow.append(linkNode, style(`
            img {
                width: 100%;
                object-fit: contain;
            }
        `));
    }
}

customElements.define("doc-image", DocImage);

function init() {
    document.body.classList.remove("content");
    document.body.innerHTML = `<div class="content">${document.body.innerHTML}</div>`;

    const header = document.createElement("doc-header");
    document.body.prepend(header);

    const content = document.querySelector("div.content");
    const navigationButtons = document.createElement("doc-nav-buttons");
    content.append(navigationButtons);

    if (document.location.pathname !== "/toc.html") {
        const toc = document.createElement("doc-toc");
        document.body.append(toc);
    }

    const contentWrapper = document.createElement("div");
    contentWrapper.classList.add("content-wrapper");
    contentWrapper.append(content);
    document.body.append(contentWrapper);

    const articleToc = document.createElement("doc-article-toc");
    document.body.append(articleToc);

    document.body.classList.add("ready");
}

init();
