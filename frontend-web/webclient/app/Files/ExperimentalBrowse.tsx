import * as React from "react";
import {useLayoutEffect, useRef} from "react";
import {useLocation, useNavigate} from "react-router";
import {commonFileExtensions, doNothing, extensionFromPath, extensionType, timestampUnixMs} from "@/UtilityFunctions";
import MainContainer from "@/MainContainer/MainContainer";
import FilesApi, {
    FileSensitivityNamespace,
    FileSensitivityVersion,
    isSensitivitySupported,
    UFile, UFileIncludeFlags
} from "@/UCloud/FilesApi";
import {callAPI} from "@/Authentication/DataHook";
import {fileName, getParentPath, sizeToString} from "@/Utilities/FileUtilities";
import {Icon} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {createRoot} from "react-dom/client";
import {SvgFt} from "@/ui-components/FtIcon";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {FileIconHint} from "@/Files/index";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {dateToString} from "@/Utilities/DateUtilities";
import {file, PageV2} from "@/UCloud";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {bulkRequestOf, SensitivityLevel} from "@/DefaultObjects";
import metadataDocumentApi, {
    FileMetadataDocumentOrDeleted,
    FileMetadataDocument,
    FileMetadataHistory,
    FileMetadataDocumentStatus
} from "@/UCloud/MetadataDocumentApi";

const ExperimentalBrowse: React.FunctionComponent = props => {
    const navigate = useNavigate();
    const location = useLocation();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browser = useRef<FileBrowser | null>(null);
    const openTriggeredByPath = useRef<string | null>(null);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browser.current) {
            const fileBrowser = new FileBrowser(mount);
            browser.current = fileBrowser;
            fileBrowser.mount();
            fileBrowser.onOpen = (path: string) => {
                if (openTriggeredByPath.current === path) {
                    openTriggeredByPath.current = null;
                    return;
                }

                navigate("?path=" + encodeURIComponent(path));
            };
        }
    }, []);

    useLayoutEffect(() => {
        const b = browser.current;
        if (!b) return;

        const path = getQueryParamOrElse(location.search, "path", "");
        if (path) {
            openTriggeredByPath.current = path;
            b.open(path);
        }
    }, [location.search]);

    useRefreshFunction(() => {
        browser.current?.refresh();
    });

    return <MainContainer
        main={<div ref={mountRef}/>}
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

        // NOTE(Dan): The font-family is not (along with all other CSS) inherited into the image below. As a result,
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

enum SelectionMode {
    SINGLE,
    TOGGLE_SINGLE,
    ADDITIVE_SINGLE,
    ADDITIVE_LIST,
}

interface FileRow {
    container: HTMLElement;
    selected: HTMLInputElement;
    favorite: HTMLElement;
    title: HTMLElement;
    stat1: HTMLElement;
    stat2: HTMLElement;
    stat3: HTMLElement;
}

class FileBrowser {
    private root: HTMLDivElement;
    private breadcrumbs: HTMLUListElement;
    private scrolling: HTMLDivElement;
    private dragIndicator: HTMLDivElement;
    private rows: FileRow[] = [];
    private isSelected = new Uint8Array(0);
    private lastSingleSelection = -1;
    private lastListSelectionEnd = -1;
    private lastFetch: Record<string, number> = {};
    private inflightRequests: Record<string, Promise<boolean>> = {};
    private isFetchingNext: boolean = false;
    private icons: SvgCache = new SvgCache();

    private cachedNext: Record<string, string | null> = {};
    private cachedData: Record<string, UFile[]> = {};
    private scrollPosition: Record<string, number> = {};
    private currentPath: string = "/4";

    private searchQueryTimeout = -1;
    private searchQuery = "";

    private ignoreScrollEvent = false;
    private scrollingContainerWidth;
    private scrollingContainerHeight;

    public onOpen: (path: string) => void = doNothing;

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
            
            <div style="overflow-y: auto; position: relative;">
                <div class="scrolling">
                </div>
            </div>
            
            <div class="drag-indicator"></div>
        `;

        this.dragIndicator = this.root.querySelector<HTMLDivElement>(".drag-indicator")!;
        this.scrolling = this.root.querySelector<HTMLDivElement>(".scrolling")!;
        this.scrolling.parentElement!.addEventListener("scroll", () => {
            if (this.ignoreScrollEvent) {
                this.ignoreScrollEvent = false;
                return;
            }

            this.renderPage();
            this.fetchNext();
        });

        this.breadcrumbs = this.root.querySelector<HTMLUListElement>("header ul")!;
        const keyDownListener = (ev: KeyboardEvent) => {
            if (!this.root.isConnected) {
                document.removeEventListener("keydown", keyDownListener);
                return;
            }

            this.onKeyPress(ev);
        };
        document.addEventListener("keydown", keyDownListener);

        const rows: HTMLDivElement[] = [];
        for (let i = 0; i < FileBrowser.maxRows; i++) {
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
            row.addEventListener("pointerdown", e => {
                this.onRowPointerDown(myIndex, e);
            });
            row.addEventListener("click", () => {
                this.onRowClicked(myIndex);
            });
            row.addEventListener("dblclick", () => {
                this.onRowDoubleClicked(myIndex);
            });
            rows.push(row);

            const r = {
                container: row,
                selected: row.querySelector<HTMLInputElement>("input")!,
                favorite: row.querySelector<HTMLElement>(".favorite")!,
                title: row.querySelector<HTMLElement>(".title")!,
                stat1: row.querySelector<HTMLElement>(".stat1")!,
                stat2: row.querySelector<HTMLElement>(".stat2")!,
                stat3: row.querySelector<HTMLElement>(".stat3")!,
            };

            r.selected.addEventListener("pointerdown", e => {
                e.preventDefault();
                e.stopPropagation();
            });

            r.selected.addEventListener("click", e => {
                e.stopPropagation();
                this.select(myIndex, SelectionMode.TOGGLE_SINGLE);
            });

            r.favorite.addEventListener("pointerdown", e => {
                e.preventDefault();
                e.stopPropagation();
            });

            r.favorite.addEventListener("click", e => {
                e.preventDefault();
                e.stopPropagation();
                this.onFavoriteClicked(myIndex);
            });

            this.rows.push(r);
        }

        this.scrolling.append(...rows);

        {
            // Immediately start preloading directory icons
            this.renderFileIconFromProperties("", true).then(doNothing);
            this.renderFileIconFromProperties("", true, "DIRECTORY_JOBS").then(doNothing);
            this.renderFileIconFromProperties("", true, "DIRECTORY_STAR").then(doNothing);
            this.renderFileIconFromProperties("", true, "DIRECTORY_TRASH").then(doNothing);
            this.renderFileIconFromProperties("", true, "DIRECTORY_SHARES").then(doNothing);
        }

        window.setTimeout(() => {
            // Then a bit later, start preloading common file formats
            this.renderFileIconFromProperties("File", false).then(doNothing);
            for (const fileFormat of commonFileExtensions) {
                this.renderFileIconFromProperties(fileFormat, false).then(doNothing);
            }
        }, 500);

        {
            const sizeListener = () => {
                if (!this.root.isConnected) {
                    window.removeEventListener("resize", sizeListener);
                    return;
                }

                const parent = this.scrolling.parentElement!;
                const rect = parent.getBoundingClientRect();
                this.scrollingContainerWidth = rect.width;
                this.scrollingContainerHeight = rect.height;
            };
            window.addEventListener("resize", sizeListener);
            sizeListener();
        }

        this.refresh();
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

    refresh() {
        this.open(this.currentPath, true);
    }

    open(path: string, force?: boolean) {
        if (this.currentPath === path && force !== true) return;
        const oldPath = this.currentPath;

        this.currentPath = path;
        this.isFetchingNext = false;

        this.onOpen(path);
        this.isSelected = new Uint8Array(0);
        this.lastSingleSelection = -1;
        this.lastListSelectionEnd = -1;
        window.clearTimeout(this.searchQueryTimeout);
        this.searchQueryTimeout = -1;
        this.searchQuery = "";

        // NOTE(Dan): Need to get this now before we call renderPage(), since it will reset the scroll position.
        const scrollPositionElement = this.scrollPosition[path];

        this.setBreadcrumbs(path.split("/"));
        this.renderPage();
        this.prefetch(path).then(wasCached => {
            // NOTE(Dan): When wasCached is true, then the previous renderPage() already had the correct data because
            // the time between pointerdown and pointerup was sufficient to fetch the data before the UI could even be
            // updated. As a result, we do not need to call renderPage() again with new data, since we didn't have any.
            if (wasCached) return;
            if (this.currentPath !== path) return;
            this.renderPage();
        });

        // NOTE(Dan): We need to scroll to the position _after_ we have rendered the page.
        this.scrolling.parentElement!.scrollTo({top: scrollPositionElement ?? 0});

        {
            const toSelect = oldPath;
            let idx = 0;
            for (const file of this.cachedData[this.currentPath] ?? []) {
                if (file.id === toSelect) {
                    this.select(idx, SelectionMode.SINGLE);
                    break;
                }

                idx++;
            }
        }
    }

    select(rowIdx: number, selectionMode: SelectionMode, render: boolean = true) {
        const selection = this.isSelected;
        if (rowIdx < 0 || rowIdx >= selection.length) return;

        switch (selectionMode) {
            case SelectionMode.SINGLE: {
                for (let i = 0; i < selection.length; i++) {
                    if (i === rowIdx) selection[i] = 1;
                    else selection[i] = 0;
                }
                this.lastSingleSelection = rowIdx;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.TOGGLE_SINGLE: {
                selection[rowIdx] = selection[rowIdx] !== 0 ? 0 : 1;
                this.lastSingleSelection = rowIdx;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.ADDITIVE_SINGLE: {
                selection[rowIdx] = 1;
                this.lastSingleSelection = rowIdx;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.ADDITIVE_LIST: {
                if (this.lastListSelectionEnd !== -1) {
                    // Clear old list
                    const start = Math.min(this.lastSingleSelection, this.lastListSelectionEnd);
                    const end = Math.max(this.lastSingleSelection, this.lastListSelectionEnd);
                    for (let i = start; i <= end; i++) {
                        selection[i] = 0;
                    }
                }

                const firstEntry = Math.min(rowIdx, this.lastSingleSelection);
                const lastEntry = Math.max(rowIdx, this.lastSingleSelection);
                for (let i = firstEntry; i <= lastEntry; i++) {
                    selection[i] = 1;
                }
                this.lastListSelectionEnd = rowIdx;
                break;
            }
        }

        if (render) this.renderPage();
    }

    private renderPage() {
        const page = this.cachedData[this.currentPath] ?? [];
        if (this.isSelected.length < page.length) {
            const newSelected = new Uint8Array(page.length);
            newSelected.set(this.isSelected, 0);
            this.isSelected = newSelected;
        }

        // Determine the total size of the page and figure out where we are
        const totalSize = FileBrowser.rowSize * page.length;
        const firstVisiblePixel = this.scrolling.parentElement!.scrollTop;
        this.scrollPosition[this.currentPath] = firstVisiblePixel;

        const firstRowInsideRegion = Math.ceil(firstVisiblePixel / FileBrowser.rowSize);
        const firstRowToRender = Math.max(0, firstRowInsideRegion - FileBrowser.extraRowsToPreRender);

        this.scrolling.style.height = `${totalSize}px`;

        const findRow = (idx: number): FileRow | null => {
            const rowNumber = idx - firstRowToRender;
            if (rowNumber < 0) return null;
            if (rowNumber >= FileBrowser.maxRows) return null;
            return this.rows[rowNumber];
        }

        // Check if any item has been selected
        let hasAnySelected = false;
        const selection = this.isSelected;
        for (let i = 0; i < selection.length; i++) {
            if (selection[i] !== 0) {
                hasAnySelected = true;
                break;
            }
        }

        // Reset rows and place them accordingly
        for (let i = 0; i < FileBrowser.maxRows; i++) {
            const row = this.rows[i];
            row.container.classList.add("hidden");
            row.container.removeAttribute("data-file");
            row.container.removeAttribute("data-selected");
            row.container.removeAttribute("data-file-idx");
            row.container.style.position = "absolute";
            row.selected.style.display = hasAnySelected ? "block" : "none";
            row.favorite.style.display = hasAnySelected ? "none" : "block";
            const top = Math.min(totalSize - FileBrowser.rowSize, (firstRowToRender + i) * FileBrowser.rowSize);
            row.container.style.top = `${top}px`;
            row.title.innerHTML = "";
        }

        // Render the visible rows by iterating over all items
        for (let i = 0; i < page.length; i++) {
            const file = page[i];
            const row = findRow(i);
            if (!row) continue;

            row.container.setAttribute("data-file-idx", i.toString());
            row.container.setAttribute("data-file", file.id);
            row.container.setAttribute("data-selected", (this.isSelected[i] !== 0).toString());
            row.selected.checked = (this.isSelected[i] !== 0);
            row.container.classList.remove("hidden");
            row.title.innerText = fileName(file.id);
            row.stat2.innerText = dateToString(file.status.modifiedAt ?? file.status.accessedAt ?? timestampUnixMs());
            row.stat3.innerText = sizeToString(file.status.sizeIncludingChildrenInBytes ?? file.status.sizeInBytes ?? null);

            if (!hasAnySelected) {
                FileBrowser.findFavoriteStatus(file).then(async (isFavorite) => {
                    const icon = await this.icons.renderIcon({
                        name: (isFavorite ? "starFilled" : "starEmpty"),
                        color: (isFavorite ? "blue" : "midGray"),
                        color2: "midGray",
                        height: 64,
                        width: 64
                    });

                    if (row.container.getAttribute("data-file") !== file.id) return;

                    row.favorite.innerHTML = "";
                    row.favorite.append(image(icon, {width: 20, height: 20, alt: "Star"}))
                    row.favorite.style.cursor = "pointer";
                });
            }

            FileBrowser.findSensitivity(file).then(sensitivity => {
                if (row.container.getAttribute("data-file") !== file.id) return;
                row.stat1.innerHTML = "";
                if (!sensitivity) return;

                const badge = div("");
                badge.classList.add("sensitivity-badge");
                badge.classList.add(sensitivity.toString());
                badge.innerText = sensitivity.toString()[0];

                row.stat1.append(badge);
            });

            this.renderFileIcon(file).then(url => {
                if (row.container.getAttribute("data-file") !== file.id) return;
                row.title.innerHTML = "";
                row.title.append(image(url, {width: 20, height: 20, alt: "File icon"}));
                row.title.append(fileName(file.id));
            });
        }
    }

    private async renderFileIcon(file: UFile): Promise<string> {
        const ext = file.id.indexOf(".") !== -1 ? extensionFromPath(file.id) : undefined;
        const ext4 = ext?.substring(0, 4) ?? "File";
        return this.renderFileIconFromProperties(ext4, file.status.type === "DIRECTORY", file.status.icon);
    }

    private async renderFileIconFromProperties(
        extension: string,
        isDirectory: boolean,
        hint?: FileIconHint
    ): Promise<string> {
        const hasExt = !!extension;
        const type = extension ? extensionType(extension.toLocaleLowerCase()) : "binary";

        const width = 64;
        const height = 64;

        if (hint || isDirectory) {
            let name: IconName;
            let color: ThemeColor = "FtFolderColor";
            let color2: ThemeColor = "FtFolderColor2";
            switch (hint) {
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
            "file-" + extension,
            () => <SvgFt color={getCssVar("FtIconColor")} color2={getCssVar("FtIconColor2")} hasExt={hasExt}
                         ext={extension} type={type} width={width} height={height}/>,
            width,
            height
        );
    }

    private static defaultRetrieveFlags: Partial<UFileIncludeFlags> = {
        includeMetadata: true,
        includeSizes: true,
        includeTimestamps: true,
        includeUnixInfo: true,
        allowUnsupportedInclude: true,
    };

    private prefetch(path: string): Promise<boolean> {
        const now = timestampUnixMs();
        if (now - (this.lastFetch[path] ?? 0) < 500) return this.inflightRequests[path] ?? Promise.resolve(true);
        this.lastFetch[path] = now;

        const promise = callAPI(FilesApi.browse({path, itemsPerPage: 250, ...FileBrowser.defaultRetrieveFlags}))
            .then(result => {
                this.cachedData[path] = result.items;
                this.cachedNext[path] = result.next ?? null;
                return false;
            })
            .finally(() => delete this.inflightRequests[path]);

        this.inflightRequests[path] = promise;
        return promise;
    }

    private async fetchNext() {
        const initialPath = this.currentPath;
        if (this.isFetchingNext || this.cachedNext[initialPath] === null) return;

        const scrollingContainer = this.scrolling.parentElement!;
        const scrollingPos = scrollingContainer.scrollTop;
        const scrollingHeight = scrollingContainer.scrollHeight;
        if (scrollingPos < scrollingHeight * 0.8) return;

        this.isFetchingNext = true;
        try {
            const result = await callAPI(
                FilesApi.browse({
                    path: this.currentPath,
                    itemsPerPage: 250,
                    next: this.cachedNext[initialPath] ?? undefined,
                    ...FileBrowser.defaultRetrieveFlags,
                })
            );

            if (initialPath !== this.currentPath) return;

            this.cachedData[initialPath] = this.cachedData[initialPath].concat(result.items);
            this.cachedNext[initialPath] = result.next ?? null;
            this.renderPage();
        } finally {
            if (initialPath === this.currentPath) this.isFetchingNext = false;
        }
    }

    private onRowPointerDown(index: number, event: MouseEvent) {
        const row = this.rows[index];
        const filePath = row.container.getAttribute("data-file");
        const fileIdxS = row.container.getAttribute("data-file-idx");
        const fileIdx = fileIdxS ? parseInt(fileIdxS) : undefined;
        if (!filePath || fileIdx == null || isNaN(fileIdx)) return;
        this.prefetch(filePath);

        {
            let mode = SelectionMode.SINGLE;
            if (event.ctrlKey || event.metaKey) mode = SelectionMode.TOGGLE_SINGLE;
            if (event.shiftKey) mode = SelectionMode.ADDITIVE_LIST;
            this.select(fileIdx, mode);
        }

        const startX = event.clientX;
        const startY = event.clientY;

        this.dragMoveHandler = (e) => {
            const s = this.dragIndicator.style;
            s.display = "block";
            s.left = Math.min(e.clientX, startX) + "px";
            s.top = Math.min(e.clientY, startY) + "px";

            s.width = Math.abs(e.clientX - startX) + "px";
            s.height = Math.abs(e.clientY - startY) + "px";
        };

        this.dragReleaseHandler = () => {
            this.dragIndicator.style.display = "none";
            document.removeEventListener("mousemove", this.dragMoveHandler);
            document.removeEventListener("pointerup", this.dragReleaseHandler);
        };

        document.addEventListener("mousemove", this.dragMoveHandler);
        document.addEventListener("pointerup", this.dragReleaseHandler);
    }

    private dragMoveHandler = (e: MouseEvent) => {
    };
    private dragReleaseHandler = () => {
    };

    private onRowClicked(index: number) {
        const row = this.rows[index];
        const filePath = row.container.getAttribute("data-file");
        if (!filePath) return;
    }

    private onRowDoubleClicked(index: number) {
        const row = this.rows[index];
        const filePath = row.container.getAttribute("data-file");
        if (!filePath) return;

        this.open(filePath);
    }

    private onFavoriteClicked(index: number) {
        const row = this.rows[index];
        const fileIdx = parseInt(row.container.getAttribute("data-file-idx") ?? "a");
        if (isNaN(fileIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        if (fileIdx < 0 || fileIdx >= page.length) return;
        (async () => {
            const currentStatus = await FileBrowser.findFavoriteStatus(page[fileIdx]);
            await this.setFavoriteStatus(page[fileIdx], !currentStatus);
        })();
    }

    private onKeyPress(ev: KeyboardEvent) {
        const relativeSelect = (delta: number) => {
            ev.preventDefault();
            if (!ev.shiftKey) {
                const rowIdx = Math.max(0, Math.min(this.isSelected.length - 1, this.lastSingleSelection + delta));
                this.ensureRowIsVisible(rowIdx, delta <= 0, true);
                this.select(rowIdx, SelectionMode.SINGLE);
            } else {
                let baseIndex = this.lastListSelectionEnd;
                if (baseIndex === -1) {
                    this.select(this.lastSingleSelection, SelectionMode.ADDITIVE_SINGLE, false);
                    baseIndex = this.lastSingleSelection;
                }
                const rowIdx = Math.max(0, Math.min(this.isSelected.length - 1, baseIndex + delta));
                this.ensureRowIsVisible(rowIdx, delta <= 0, true);
                this.select(rowIdx, SelectionMode.ADDITIVE_LIST);
            }
        };

        switch (ev.code) {
            case "Escape": {
                const selected = this.isSelected;
                for (let i = 0; i < selected.length; i++) {
                    selected[i] = 0;
                }
                this.renderPage();
                break;
            }

            case "ArrowUp": {
                relativeSelect(-1);
                break;
            }

            case "ArrowDown": {
                relativeSelect(1);
                break;
            }

            case "Home": {
                relativeSelect(-1000000000);
                break;
            }

            case "End": {
                relativeSelect(1000000000);
                break;
            }

            case "PageUp": {
                relativeSelect(-50);
                break;
            }

            case "PageDown": {
                relativeSelect(50);
                break;
            }

            case "Backspace": {
                this.open(getParentPath(this.currentPath));
                break;
            }

            case "Enter": {
                const selected = this.isSelected;
                for (let i = 0; i < selected.length; i++) {
                    if (selected[i] !== 0) {
                        const file = this.cachedData[this.currentPath][i];
                        if (file.status.type === "DIRECTORY") this.open(file.id);
                        break;
                    }
                }
                break;
            }

            default: {
                // NOTE(Dan): Initiate search if the input is printable
                const printableChar = ev.key;
                if (printableChar && printableChar.length === 1) {
                    this.searchQuery += printableChar.toLowerCase();
                    window.clearTimeout(this.searchQueryTimeout);
                    this.searchQueryTimeout = window.setTimeout(() => {
                        this.searchQuery = "";
                    }, 1000);

                    const files = this.cachedData[this.currentPath] ?? [];
                    for (let i = 0; i < files.length; i++) {
                        const name = fileName(files[i].id).toLowerCase();
                        if (name.indexOf(this.searchQuery) === 0) {
                            this.ensureRowIsVisible(i, true, true);
                            this.select(i, SelectionMode.SINGLE);
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    private ensureRowIsVisible(rowIdx: number, topAligned: boolean, ignoreEvent: boolean = false) {
        const scrollingContainer = this.scrolling.parentElement!;
        const height = this.scrollingContainerHeight;

        const firstRowPixel = rowIdx * FileBrowser.rowSize;
        const lastRowPixel = firstRowPixel + FileBrowser.rowSize;

        const firstVisiblePixel = scrollingContainer.scrollTop;
        const lastVisiblePixel = firstVisiblePixel + height;

        if (firstRowPixel < firstVisiblePixel || firstRowPixel > lastVisiblePixel ||
            lastRowPixel < firstVisiblePixel || lastRowPixel > lastVisiblePixel) {
            if (ignoreEvent) this.ignoreScrollEvent = true;

            if (topAligned) {
                scrollingContainer.scrollTo({top: firstRowPixel});
            } else {
                scrollingContainer.scrollTo({top: lastRowPixel - height});
            }

            if (ignoreEvent) this.fetchNext();
        }
    }

    private static metadataTemplateCache = new AsyncCache<string>();

    private static async findTemplateId(file: UFile, namespace: string, version: string): Promise<string> {
        const template = Object.values(file.status.metadata?.templates ?? {}).find(it =>
            it.namespaceName === namespace && it.version == version
        );

        if (!template) {
            return FileBrowser.metadataTemplateCache.retrieve(namespace, async () => {
                const page = await callAPI<PageV2<FileMetadataTemplateNamespace>>(
                    MetadataNamespaceApi.browse({filterName: namespace, itemsPerPage: 250})
                );
                if (page.items.length === 0) return "";
                return page.items[0].id;
            });
        }

        return template.namespaceId;
    }

    private static async findSensitivity(file: UFile): Promise<SensitivityLevel> {
        if (!isSensitivitySupported(file)) return SensitivityLevel.PRIVATE;

        const sensitivityTemplateId = await FileBrowser.findTemplateId(file, FileSensitivityNamespace,
            FileSensitivityVersion);
        if (!sensitivityTemplateId) return SensitivityLevel.PRIVATE;

        const entry = file.status.metadata?.metadata[sensitivityTemplateId]?.[0];
        if (!entry || entry.type === "deleted") return SensitivityLevel.PRIVATE;
        return entry.specification.document.sensitivity;
    }

    private async setFavoriteStatus(file: UFile, isFavorite: boolean, render: boolean = true) {
        const templateId = await FileBrowser.findTemplateId(file, "favorite", "1.0.0");
        if (!templateId) return;
        const currentMetadata: FileMetadataHistory = file.status.metadata ?? {metadata: {}, templates: {}}
        const favorites: FileMetadataDocumentOrDeleted[] = currentMetadata.metadata[templateId] ?? [];
        let mostRecentStatusId = "";
        for (let i = 0; i < favorites.length; i++) {
            if (favorites[i].id === "fake_entry") continue;
            if (favorites[i].type !== "metadata") continue;

            mostRecentStatusId = favorites[i].id;
            break;
        }

        favorites.unshift({
            type: "metadata",
            status: {
                approval: { type: "not_required" },
            },
            createdAt: 0,
            createdBy: "",
            specification: {
                templateId: templateId!,
                changeLog: "",
                document: { favorite: isFavorite } as Record<string, any>,
                version: "1.0.0",
            },
            id: "fake_entry",
        })

        currentMetadata.metadata[templateId] = favorites;
        file.status.metadata = currentMetadata;

        if (!isFavorite) {
            callAPI(
                metadataDocumentApi.delete(
                    bulkRequestOf({
                        changeLog: "Remove favorite",
                        id: mostRecentStatusId
                    })
                )
            ).then(doNothing);
        } else {
            callAPI(
                metadataDocumentApi.create(bulkRequestOf({
                        fileId: file.id,
                        metadata: {
                            document: {favorite: isFavorite},
                            version: "1.0.0",
                            changeLog: "New favorite status",
                            templateId: templateId
                        }
                    })
                )
            ).then(doNothing);
        }

        if (render) this.renderPage();
    }

    private static async findFavoriteStatus(file: UFile): Promise<boolean> {
        const templateId = await FileBrowser.findTemplateId(file, "favorite", "1.0.0");
        if (!templateId) return false;

        const entry = file.status.metadata?.metadata[templateId]?.[0];
        if (!entry || entry.type === "deleted") return false;
        return entry.specification.document.favorite;
    }

    static rowSize = 55;
    static extraRowsToPreRender = 6;
    static maxRows = (Math.max(1080, window.screen.height) / FileBrowser.rowSize) + FileBrowser.extraRowsToPreRender;

    static styleInjected = false;

    static injectStyle() {
        if (FileBrowser.styleInjected) return;
        FileBrowser.styleInjected = true;

        const styleElem = document.createElement("style");
        //language=css
        styleElem.innerText = `
            .file-browser .drag-indicator {
                position: fixed;
                z-index: 10000;
                background-color: rgba(0, 0, 255, 30%);
                border: 2px solid blue;
                display: none;
                top: 0;
                left: 0;
            }

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
                flex-shrink: 0;
                display: flex;
                align-items: center;
            }

            .file-browser > div {
                flex-grow: 1;
            }

            .file-browser header ul li::before {
                display: inline-block;
                content: '/';
                margin-right: 8px;
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
                height: ${FileBrowser.rowSize}px;
                width: 100%;
                align-items: center;
                border-bottom: 1px solid #96B3F8;
                gap: 8px;
                user-select: none;
                padding: 0 8px;
            }

            .file-browser .row .title img {
                margin-right: 8px;
            }

            .file-browser .row.hidden {
                display: none;
            }

            .file-browser .row input[type=checkbox] {
                height: 20px;
                width: 20px;
            }

            .file-browser .row[data-selected="true"] {
                background: #DAE4FD;
            }

            .file-browser .row .title {
                width: 56%;
            }

            .file-browser .row .stat1,
            .file-browser .row .stat2,
            .file-browser .row .stat3 {
                display: flex;
                justify-content: end;
                text-align: end;
                width: 13%;
            }

            .file-browser .sensitivity-badge {
                height: 2em;
                width: 2em;
                display: flex;
                margin-right: 5px;
                align-items: center;
                justify-content: center;
                border: 0.2em solid var(--badgeColor, var(--midGray));
                border-radius: 100%;
            }

            .file-browser .sensitivity-badge.PRIVATE {
                --badgeColor: var(--midGray);
            }

            .file-browser .sensitivity-badge.CONFIDENTIAL {
                --badgeColor: var(--purple);
            }

            .file-browser .sensitivity-badge.SENSITIVE {
                --badgeColor: #ff0004;
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
