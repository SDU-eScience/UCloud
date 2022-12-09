import { style } from "./components.js";

export class Header extends HTMLElement {
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
                padding: 16px;
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
                    <img src="/images/logo_esc.svg" alt="UCloud Logo">
                    UCloud/Debugger
                </a>

                <div class="middle">
                </div>

                <div class="right">
                    <a href="https://github.com/SDU-eScience/UCloud" target="_blank" class="btn secondary github">
                        <img src="/images/github-dark.png">
                        Documentation
                    </a>
                </div>
            </header>
        `;
    }
}

customElements.define("debug-header", Header);


export class Sidebar extends HTMLElement {
    constructor() {
        super();

        const shadow = this.attachShadow({ mode: "open" });

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
        this.shadowRoot.appendChild(rootList);
    }

    renderNode(domNode, sectionNode, selfNode) {
        const itemNode = document.createElement("li");
        // TODO
    }
}

customElements.define("debug-sidebar", Sidebar);