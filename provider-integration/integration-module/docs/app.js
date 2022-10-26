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

            console.log(childProps.length, childProps);

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
                position: sticky;
                top: 0;
                z-index: 100;
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
                flex-grow: 1;
                gap: 15px;
                margin-left: 15px;
            }

            header .logo img {
                width: 38px;
            }

            header .logo span {
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
                width: 100vw;
                display: flex;
                justify-content: center;
                align-items: center;
                gap: 64px;
            }

            header .right {
                justify-content: end;
            }
        `;

        shadow.appendChild(style);

        shadow.innerHTML += `
            <header>
                <div class="logo">
                    <img src="/images/logo_esc.svg" alt="UCloud Logo">
                    <span>UCloud</span>
                </div>

                <div class="middle">
                    <a href="#">Version history</a>
                    <a href="#">Documentation</a>
                    <a href="#">Support</a>
                </div>

                <div class="right">
                    <a href="https://github.com/SDU-eScience/UCloud" class="btn secondary">Source Code</a>
                    <a href="https://github.com/SDU-eScience/UCloud" class="btn primary">Download</a>
                </div>
            </header>
        `;
    }
}

customElements.define("doc-header", DocHeader);

class DocRecipes extends HTMLElement {
    static recipes = [
        { 
            title: "Puhuri integration",
            description: "Show-cases a collection of plugins which together form the basis of an integration with Puhuri",
            href: "/recipes/puhuri"
        },
        { 
            title: "Kubernetes compute with storage",
            description: "Show-cases a collection of plugins which together form the basis of an integration with Puhuri",
            href: "/recipes/kubernetes"
        },
        { 
            title: "Slurm + distributed file system",
            description: "Show-cases a collection of plugins which together form the basis of an integration with Puhuri",
            href: "/recipes/slurm"
        },
        { 
            title: "Keycloak",
            description: "Show-cases a collection of plugins which together form the basis of an integration with Puhuri",
            href: "/recipes/keycloak"
        }
    ];
}

class DocTableOfContents extends HTMLElement {
    static sections = [
        { title: "Getting started", href: "/getting-started" },
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
                position: fixed;
                top: 64px;
                height: 100vh;
                width: 300px;
                background: #F5F7F9;
                padding-top: 8px;
                border-right: 1px #ddd solid;
                overflow-y: auto;
            }

            :host > ul {
                margin: 0;
                padding: 0;
            }

            li.active > a {
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

            a {
                text-decoration: none;
                color: black;
                font-size: 18px;
            }
        `));

        const rootList = document.createElement("ul");
        for (const section of DocTableOfContents.sections) {
            this.renderNode(rootList, section, selfEntry);
        }

        shadow.append(rootList);
    }

    renderNode(domNode, sectionNode, selfNode) {
        const itemNode = document.createElement("li");

        if (selfNode && selfNode.href == sectionNode.href) {
            itemNode.classList.add("active");
        }

        const linkNode = document.createElement("a");
        linkNode.href = sectionNode.href;
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

        const previousEntry = DocTableOfContents.findIndexed(selfIndex - 1);
        const nextEntry = DocTableOfContents.findIndexed(selfIndex + 1);

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
            }

            a span {
                color: rgb(150, 150, 150);
                font-size: 80%;
                display: block;
            }
        `));

        const previousDom = document.createElement("div");
        const nextDom = document.createElement("div");
        const spacer = document.createElement("div");
        spacer.classList.add("spacer");
        shadow.append(previousDom, spacer, nextDom);

        if (previousEntry) {
            const link = document.createElement("a");
            link.href = previousEntry.href;
            link.textContent = "⬅️ Previous";

            const span = document.createElement("span");
            span.textContent = previousEntry.title;
            link.append(span);

            previousDom.append(link);
        }

        if (nextEntry) {
            const link = document.createElement("a");
            link.href = nextEntry.href;
            link.textContent = "Next ➡️";

            const span = document.createElement("span");
            span.textContent = nextEntry.title;
            link.append(span);

            nextDom.append(link);
        }
    }
}

customElements.define("doc-nav-buttons", DocNavigationButtons);

function init() {
    document.body.classList.remove("content");
    document.body.innerHTML = `<div class="content">${document.body.innerHTML}</div>`;

    const header = document.createElement("doc-header");
    document.body.prepend(header);

    const toc = document.createElement("doc-toc");
    document.body.prepend(toc);

    const content = document.querySelector("div.content");
    const navigationButtons = document.createElement("doc-nav-buttons");
    content.append(navigationButtons);
}

init();
