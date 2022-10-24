class DocProperty extends HTMLElement {
    constructor() {
        super();

        this.innerHTML = `<div class="description">${this.innerHTML}</div>`;

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

        this.prepend(docWrapper);
    }
}

customElements.define("doc-prop", DocProperty);

class DocPropContainer extends HTMLElement {
    constructor() {
        super();

        const canCollapse = this.getAttribute("can-collapse") !== null || this.getAttribute("collapsed") !== null;

        if (canCollapse) {
            const container = document.createElement("div");
            container.classList.add("collapsible");
            let isFirst = true;
            const childProps = this.querySelectorAll(":scope > doc-prop");
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

                this.appendChild(collapseToggle);
                this.appendChild(container);

                const onCollapsed = () => {
                    const isCollapsed = this.getAttribute("collapsed") !== null;
                    container.style.display = isCollapsed ? "none" : "block";
                    collapseToggle.text = isCollapsed ? "Show all details" : "Hide details";
                };

                onCollapsed();
                new MutationObserver(onCollapsed).observe(this, { attributes: true, attributeFilter: ["collapsed"] });
            }
        }
    }
}

customElements.define("doc-prop-container", DocPropContainer);


class DocSealedContainer extends HTMLElement {
    constructor() {
        super();

        const firstPropContainer = this.querySelector("doc-prop-container");
        if (firstPropContainer) {
            const instructions = document.createElement("p");
            instructions.innerHTML = `
                Select <i>one</i> of the following:
            `;
            this.insertBefore(instructions, firstPropContainer);
        }
    }
}

customElements.define("doc-sealed-container", DocSealedContainer);

class DocSnippet extends HTMLElement {
    constructor() {
        super();

        let text = this.innerText;
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

        this.innerHTML = `<pre><code>${newText}</code></pre>`;
    }
}

customElements.define("doc-snippet", DocSnippet);