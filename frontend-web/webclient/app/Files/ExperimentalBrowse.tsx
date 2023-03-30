import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {useLayoutEffect, useRef} from "react";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import MainContainer from "@/MainContainer/MainContainer";
import FilesApi, {UFile} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {fileName} from "@/Utilities/FileUtilities";

const ExperimentalBrowse: React.FunctionComponent = props => {
    const navigate = useNavigate();
    const location = useLocation();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browser = useRef<FileBrowser | null>(null);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browser.current) {
            const fileBrowser = new FileBrowser(mount);
            browser.current = fileBrowser;
            fileBrowser.mount();
            fileBrowser.navigate = navigate;
        }
    }, []);

    return <MainContainer main={<div ref={mountRef}/>}/>;
};

interface FileRow {
    row: HTMLElement;

    checkbox: HTMLElement;
    favorite: HTMLElement;
    title: HTMLElement;
    stat1: HTMLElement;
    stat2: HTMLElement;
    stat3: HTMLElement;
}

const maxRows = 100;

class FileBrowser {
    private root: HTMLDivElement;
    private breadcrumbs: HTMLUListElement;
    private scrolling: HTMLDivElement;
    private scrollingBefore: HTMLDivElement;
    private scrollingAfter: HTMLDivElement;
    private rows: FileRow[] = [];
    private lastFetch: Record<string, number> = {};
    private inflightRequests: Record<string, Promise<void>> = {};

    private cachedData: Record<string, UFile[]> = {};
    private currentPath: string = "/4";

    navigate: (to: string) => void = doNothing;

    constructor(root: HTMLDivElement) {
        this.root = root;
    }

    mount() {
        FileBrowser.injectStyle();

        this.root.classList.add("file-browser");
        this.root.innerHTML = `
            <header>
                <ul></ul>
            </header>
            
            <div style="overflow-y: auto">
                <div class="scrolling">
                </div>
            </div>
        `;

        this.scrolling = this.root.querySelector<HTMLDivElement>(".scrolling")!;
        this.breadcrumbs = this.root.querySelector<HTMLUListElement>("header ul")!;

        this.scrollingBefore = document.createElement("div");
        this.scrollingAfter = document.createElement("div");

        const rows: HTMLDivElement[] = [];
        for (let i = 0; i < maxRows; i++) {
            const row = div(`
                <input type="checkbox">
                <div class="favorite"></div>
                <div class="title"></div>
                <div class="stat1"></div>
                <div class="stat2"></div>
                <div class="stat3"></div>
            `);
            row.classList.add("row");
            const myIndex = i;
            row.addEventListener("pointerdown", () => {
                this.onRowPointerDown(myIndex);
            });
            row.addEventListener("click", () => {
                this.onRowClicked(myIndex);
            })
            rows.push(row);

            this.rows.push({
                row,
                checkbox: row.querySelector<HTMLElement>("input")!,
                favorite: row.querySelector<HTMLElement>(".favorite")!,
                title: row.querySelector<HTMLElement>(".title")!,
                stat1: row.querySelector<HTMLElement>(".stat1")!,
                stat2: row.querySelector<HTMLElement>(".stat2")!,
                stat3: row.querySelector<HTMLElement>(".stat3")!,
            });
        }

        this.scrolling.append(this.scrollingBefore);
        this.scrolling.append(...rows);
        this.scrolling.append(this.scrollingAfter);

        this.open(this.currentPath);
    }

    private setBreadcrumbs(path: string[]) {
        this.breadcrumbs.innerHTML = "";
        const fragment = document.createDocumentFragment();
        let pathBuilder = "";
        for (const component of path) {
            pathBuilder += "/" + component;
            const myPath = pathBuilder;

            const listItem = document.createElement("li");
            listItem.innerText = component;
            listItem.addEventListener("click", () => {
                this.open(myPath);
            });

            fragment.append(listItem);
        }
        this.breadcrumbs.append(fragment);
    }

    open(path: string) {
        this.currentPath = path;
        this.setBreadcrumbs(path.split("/"));
        this.renderPage();
        this.prefetch(path).then(() => {
            console.log("fetch complete")
            if (this.currentPath !== path) return;
            this.renderPage();
        });
    }

    private renderPage() {
        console.log("render")
        const page = this.cachedData[this.currentPath] ?? [];

        for (let i = 0; i < maxRows; i++) {
            const row = this.rows[i];
            row.row.classList.add("hidden");
            row.title.innerText = "";
        }

        for (let i = 0; i < page.length; i++) {
            const file = page[i];
            const row = this.findRow(i);
            if (!row) continue;

            row.row.setAttribute("data-file", file.id);
            row.row.classList.remove("hidden");
            row.title.innerText = fileName(file.id);
            if (file.status.type === "DIRECTORY") {
                row.title.innerText += "/";
            }
        }
    }

    private prefetch(path: string): Promise<void> {
        const now = timestampUnixMs();
        if (now - (this.lastFetch[path] ?? 0) < 500) return this.inflightRequests[path] ?? Promise.resolve();
        this.lastFetch[path] = now;

        const promise = callAPI(FilesApi.browse({ path, itemsPerPage: 250, includeMetadata: true }))
            .then(result => {
                this.cachedData[path] = result.items;
            });

        this.inflightRequests[path] = promise;
        return promise;
    }

    private onRowPointerDown(index: number) {
        const row = this.rows[index];
        const filePath = row.row.getAttribute("data-file");
        if (!filePath) return;
        this.prefetch(filePath).then(() => console.log("prefetch complete"));
    }

    private onRowClicked(index: number) {
        const row = this.rows[index];
        const filePath = row.row.getAttribute("data-file");
        if (!filePath) return;

        this.open(filePath);
    }

    private findRow(index: number): FileRow | null {
        if (index < 0 || index >= maxRows) return null; // TODO improve this
        return this.rows[index];
    }

    static styleInjected = false;

    static injectStyle() {
        if (FileBrowser.styleInjected) return;
        FileBrowser.styleInjected = true;

        const styleElem = document.createElement("style");
        //language=css
        styleElem.innerText = `
            .file-browser {
                width: 100%;
                height: calc(100vh - var(--headerHeight) - 32px);
                display: flex;
                flex-direction: column;
                font-size: 16px;
            }
            
            .file-browser header ul {
                margin: 0;
                padding: 0;
                display: flex;
                flex-direction: row;
                gap: 8px;
            }
            
            .file-browser header {
                height: 64px;
                width: 100%;
            }
            
            .file-browser > div {
                flex-grow: 1;
            }
            
            .file-browser header ul li {
                list-style: none;
                margin: 0;
                padding: 0;
                cursor: pointer;
                font-size: 120%;
            }
            
            .file-browser .row {
                display: flex;
                flex-direction: row;
                height: 55px;
                width: 100%;
                align-items: center;
                border-bottom: 1px solid #96B3F8;
                gap: 8px;
            }
            
            .file-browser .row.hidden {
                opacity: 0;
            }

            .file-browser .row input[type=checkbox] {
                height: 25px;
                width: 25px;
            }
            
            .file-browser .row .title {
                flex-grow: 1;
            }
        `;
        document.head.append(styleElem);
    }
}

function div(html: string): HTMLDivElement {
    const elem = document.createElement("div");
    elem.innerHTML = html;
    return elem;
}

export default ExperimentalBrowse;
