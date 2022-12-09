import { style } from "./components.js";

export class Card extends HTMLElement {
    shadow;
    constructor() {
        super();

        this.shadow = this.attachShadow({ mode: "open" });
        this.render();

        new MutationObserver(() => this.render()).observe(this, { attributes: true, childList: true, subtree: true });
    }

    render() {
        const shadow = this.shadow;
        shadow.innerHTML = `<div class="border"></div>`;

        const inner = document.createElement("div");
        inner.classList.add("inner");


        const titleElem = document.createElement("h3");
        titleElem.textContent = this.getAttribute("heading") ?? "No title";
        inner.appendChild(titleElem);

        inner.innerHTML += this.innerHTML;

        shadow.appendChild(inner);

        shadow.appendChild(style(`
            :host {
                display: block;
                height: auto;
                box-shadow: rgb(0 0 0 / 20%) 0px 3px 3px -2px, rgb(0 0 0 / 14%) 0px 3px 4px 0px, rgb(0 0 0 / 12%) 0px 1px 8px 0px;
                border: 0px solid rgb(201, 211, 223);
                border-radius: 6px;
                width: 100%;
                overflow: hidden;
            }

            .inner {
                padding: 4px 16px;
                height: calc(100% - 5px);
            }

            .border {
                border-top: 5px solid #70b;
            }
        `));

    }
}

customElements.define("debug-card", Card);