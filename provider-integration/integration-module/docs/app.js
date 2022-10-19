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
