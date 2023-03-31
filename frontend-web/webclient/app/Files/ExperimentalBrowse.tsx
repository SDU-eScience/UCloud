import * as React from "react";
import {useLocation, useNavigate} from "react-router";
import {useLayoutEffect, useMemo, useRef} from "react";
import {doNothing, extensionFromPath, extensionType, timestampUnixMs} from "@/UtilityFunctions";
import MainContainer from "@/MainContainer/MainContainer";
import FilesApi, {UFile} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {fileName} from "@/Utilities/FileUtilities";
import {Icon} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {createRoot} from "react-dom/client";
import {SvgFt} from "@/ui-components/FtIcon";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";

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

    return <MainContainer
        main={
            <>
                <div ref={mountRef}/>
            </>
        }
    />;
};

class AsyncCache<V> {
    private cache: Record<string, V> = {};
    private inflight: Record<string, Promise<V>> = {};

    retrieve(name: string, fn: () => Promise<V>): Promise<V> {
        const cached = this.cache[name];
        if (cached) return Promise.resolve(cached);

        const inflight = this.inflight[name];
        if (inflight) return Promise.all([inflight]);

        const promise = fn();
        this.inflight[name] = promise;
        return promise
            .then(r => this.cache[name] = r)
            .finally(() => delete this.inflight[name]);
    }
}

// NOTE(Dan): Now why are we doing all of this when we could just be using SVGs? Because they are slow. Not when we
// show off one or two SVGs, but when we start displaying 5 SVGs per item and the user is loading in hundreds of items.
// Then the entire thing becomes a mess really quickly. And this isn't just a matter of rendering fewer DOM elements, we
// are simply displaying too much vector graphics and the computer is definitely suffering under this. You could say
// that it is the job of the browser to do this operation for us, but it just seems like the browser is incapable of
// doing this well. This is most likely because the browser cannot guarantee that we do not attempt to update the SVG.
// However, we know for a fact that we are not modifying the SVG in any way, as a result, we know that it is perfectly
// safe to rasterize the entire thing and simply keep it as a bitmap.
class SvgCache {
    private cache = new AsyncCache<string>();

    renderSvg(
        name: string,
        node: () => React.ReactElement,
        width: number,
        height: number,
        colorHint?: string,
    ): Promise<string> {
        return this.cache.retrieve(name, () => {
            // NOTE(Dan): This function is capable of rendering arbitrary SVGs coming from React and rasterizing them
            // to a canvas and then later a bitmap (image). It does this by creating a new React root, which is used
            // only to render the SVG once. Once the SVG has been rendered we send it to our rasterizer, cache the
            // result, and then tear down the React root along with any other associated resources.
            const fragment = document.createDocumentFragment();
            const root = createRoot(fragment);

            const promise = new Promise<string>((resolve, reject) => {
                const Component: React.FunctionComponent<{ children: React.ReactNode }> = props => {
                    const div = useRef<HTMLDivElement | null>(null);
                    useLayoutEffect(() => {
                        const svg = div.current!.querySelector<SVGElement>("svg");
                        if (!svg) {
                            reject();
                        } else {
                            this.rasterize(fragment.querySelector<SVGElement>("svg"), width, height, colorHint)
                                .then(r => resolve(r))
                                .catch(r => reject(r));
                        }
                    }, []);

                    return <div ref={div}>{props.children}</div>;
                };

                root.render(<Component>{node()}</Component>);
            });

            return promise.finally(() => {
                root.unmount();
            });
        });
    }

    async renderIcon(
        {name, color, color2, width, height}: {
            name: IconName,
            color: ThemeColor,
            color2: ThemeColor,
            width: number,
            height: number
        }
    ): Promise<string> {
        return await this.renderSvg(
            `${name}-${color}-${color2}-${width}-${height}`,
            () => <Icon name={name} color={color} color2={color2} width={width} height={height}/>,
            width,
            height,
            getCssVar(color),
        );
    }

    private rasterize(data: SVGElement, width: number, height: number, colorHint?: string): Promise<string> {
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;

        // NOTE(Dan): For some reason, some of our SVGs don't have this. This technically makes them invalid, not sure
        // why the browsers allow it regardless.
        data.setAttribute("xmlns", "http://www.w3.org/2000/svg");

        // NOTE(Dan): CSS is not inherited, compute the real color.
        data.setAttribute("color", colorHint ?? window.getComputedStyle(data).color);

        // NOTE(Dan): The font-family is not (along with all other CSS) not inherited into the image below. As a result,
        // we must embed all of these resources directly into the svg. For the font, we simply choose to use a simple
        // font similar to what we use. Note that it is complicated, although possible, to load the actual font. But
        // we would need to actually embed it in the style sheet (as in the font data, not just font reference).
        const svgNamespace = "http://www.w3.org/2000/svg";
        const svgStyle = document.createElementNS(svgNamespace, 'style');
        svgStyle.innerHTML = `
            text {
                font-family: Helvetica, sans-serif
            }
        `;
        data.prepend(svgStyle);

        const ctx = canvas.getContext("2d")!;

        const image = new Image();
        const svgBlob = new Blob([data.outerHTML], {type: "image/svg+xml;charset=utf-8"});
        const svgUrl = URL.createObjectURL(svgBlob);

        return new Promise((resolve, reject) => {
            image.onerror = (e) => {
                reject(e);
            };

            image.onload = () => {
                ctx.drawImage(image, 0, 0);
                URL.revokeObjectURL(svgUrl);

                canvas.toBlob((canvasBlob) => {
                    if (!canvasBlob) {
                        reject();
                    } else {
                        resolve(URL.createObjectURL(canvasBlob));
                    }
                });
            };

            image.src = svgUrl;
        });
    }
}

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
    private inflightRequests: Record<string, Promise<boolean>> = {};
    private icons: SvgCache = new SvgCache();

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
            if (component.length === 0) continue
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
        this.prefetch(path).then(wasCached => {
            // NOTE(Dan): When wasCached is true, then the previous renderPage() already had the correct data because
            // the time between pointerdown and pointerup was sufficient to fetch the data before the UI could even be
            // updated. As a result, we do not need to call renderPage() again with new data, since we didn't have any.
            if (wasCached) return;
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
            row.row.removeAttribute("data-file");
            row.title.innerText = "";
        }

        for (let i = 0; i < page.length; i++) {
            const file = page[i];
            const row = this.findRow(i);
            if (!row) continue;

            row.row.setAttribute("data-file", file.id);
            row.row.classList.remove("hidden");
            row.title.innerHTML = "";
            row.title.innerText = fileName(file.id);
            if (file.status.type === "DIRECTORY") {
                row.title.innerText += "/";
            }

            let resolved = false;
            this.renderFileIcon(file).then(url => {
                resolved = true;
                if (row.row.getAttribute("data-file") !== file.id) return;
                row.title.prepend(image(url, {width: 20, height: 20, alt: "File icon"}));
            });
        }
    }

    private async renderFileIcon(file: UFile): Promise<string> {
        const ext = file.id.indexOf(".") !== -1 ? extensionFromPath(file.id) : undefined;
        const hasExt = !!ext;
        const ext4 = ext?.substring(0, 4) ?? "File";
        const type = ext ? extensionType(ext.toLocaleLowerCase()) : "binary";

        const width = 64;
        const height = 64;

        if (file.status.icon || file.status.type === "DIRECTORY") {
            let name: IconName;
            let color: ThemeColor = "FtFolderColor";
            let color2: ThemeColor = "FtFolderColor2";
            switch (file.status.icon) {
                case "DIRECTORY_JOBS":
                    name = "ftResultsFolder";
                    break;
                case "DIRECTORY_SHARES":
                    name = "ftSharesFolder";
                    break;
                case "DIRECTORY_STAR":
                    name = "ftFavFolder";
                    break;
                case "DIRECTORY_TRASH":
                    color = "red";
                    color2 = "lightRed";
                    name = "trash";
                    break;
                default:
                    name = "ftFolder";
                    break;
            }

            return this.icons.renderIcon({name, color, color2, width, height});
        }

        return this.icons.renderSvg(
            "file-" + ext,
            () => <SvgFt color={getCssVar("FtIconColor")} color2={getCssVar("FtIconColor2")} hasExt={hasExt}
                         ext={ext4} type={type} width={width} height={height}/>,
            width,
            height
        );
    }

    private prefetch(path: string): Promise<boolean> {
        const now = timestampUnixMs();
        if (now - (this.lastFetch[path] ?? 0) < 500) return this.inflightRequests[path] ?? Promise.resolve(true);
        this.lastFetch[path] = now;

        const promise = callAPI(FilesApi.browse({path, itemsPerPage: 250, includeMetadata: true}))
            .then(result => {
                this.cachedData[path] = result.items;
                return false;
            })
            .finally(() => delete this.inflightRequests[path]);

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

function image(src: string, opts?: { alt?: string; height?: number; width?: number; }): HTMLImageElement {
    const result = new Image();
    result.src = src;
    result.alt = opts?.alt ?? "Icon";
    if (opts?.height != null) result.height = opts.height;
    if (opts?.width != null) result.width = opts.width;
    return result;
}

export default ExperimentalBrowse;
