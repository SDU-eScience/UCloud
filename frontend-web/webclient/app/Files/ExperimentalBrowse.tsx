import * as React from "react";
import {useLayoutEffect, useRef} from "react";
import {useLocation, useNavigate} from "react-router";
import {
    commonFileExtensions,
    doNothing,
    extensionFromPath,
    extensionType,
    extractErrorMessage,
    timestampUnixMs
} from "@/UtilityFunctions";
import MainContainer from "@/MainContainer/MainContainer";
import FilesApi, {
    ExtraFileCallbacks,
    FileSensitivityNamespace,
    FileSensitivityVersion,
    FilesMoveRequestItem,
    isSensitivitySupported,
    UFile,
    UFileIncludeFlags
} from "@/UCloud/FilesApi";
import {api as FileCollectionsApi, FileCollection} from "@/UCloud/FileCollectionsApi";
import {callAPI} from "@/Authentication/DataHook";
import {fileName, getParentPath, pathComponents, resolvePath, sizeToString} from "@/Utilities/FileUtilities";
import {Icon} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {createRoot} from "react-dom/client";
import {SvgFt} from "@/ui-components/FtIcon";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {FileIconHint, FileType} from "@/Files/index";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {dateToString} from "@/Utilities/DateUtilities";
import {accounting, PageV2} from "@/UCloud";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {bulkRequestOf, SensitivityLevel} from "@/DefaultObjects";
import metadataDocumentApi, {FileMetadataDocumentOrDeleted, FileMetadataHistory,} from "@/UCloud/MetadataDocumentApi";
import {ResourceBrowseCallbacks, ResourceOwner, ResourcePermissions, SupportByProvider} from "@/UCloud/ResourceApi";
import {Dispatch} from "redux";
import {useDispatch} from "react-redux";
import {Operation} from "@/ui-components/Operation";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Client} from "@/Authentication/HttpClientInstance";
import ProductReference = accounting.ProductReference;

const ExperimentalBrowse: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browser = useRef<FileBrowser | null>(null);
    const openTriggeredByPath = useRef<string | null>(null);
    const dispatch = useDispatch();

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
            fileBrowser.dispatch = dispatch;
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
    private expiration: Record<string, number> = {};
    private cache: Record<string, V> = {};
    private inflight: Record<string, Promise<V>> = {};
    private globalTtl: number | undefined = undefined;

    constructor(opts?: {
        globalTtl?: number;
    }) {
        this.globalTtl = opts?.globalTtl;
    }

    retrieveFromCacheOnly(name: string): V | undefined {
        return this.cache[name];
    }

    retrieveWithInvalidCache(name: string, fn: () => Promise<V>, ttl?: number): [V | undefined, Promise<V>] {
        const cached = this.cache[name];
        if (cached) {
            const expiresAt = this.expiration[name];
            if (expiresAt !== undefined && timestampUnixMs() > expiresAt) {
                delete this.cache[name];
            } else {
                return [cached, Promise.resolve(cached)];
            }
        }

        const inflight = this.inflight[name];
        if (inflight) return [cached, inflight];

        const promise = fn();
        this.inflight[name] = promise;
        return [
            cached,
            promise
                .then(r => {
                    this.cache[name] = r;
                    const actualTtl = ttl ?? this.globalTtl;
                    if (actualTtl !== undefined) {
                        this.expiration[name] = timestampUnixMs() + actualTtl;
                    }
                    return r;
                })
                .finally(() => delete this.inflight[name])
        ];
    }

    retrieve(name: string, fn: () => Promise<V>, ttl?: number): Promise<V> {
        return this.retrieveWithInvalidCache(name, fn, ttl)[1];
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
                            this.rasterize(fragment.querySelector<SVGElement>("svg")!, width, height, colorHint)
                                .then(r => {
                                    resolve(r);
                                })
                                .catch(r => {
                                    reject(r);
                                });
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

type OperationOrGroup<T, R> = Operation<T, R> | OperationGroup<T, R>;

interface OperationGroup<T, R> {
    icon: IconName;
    text: string;
    color: ThemeColor;
    operations: Operation<T, R>[];
    iconRotation?: number;
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
    private operations: HTMLElement;
    private header: HTMLElement;
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

    private folderCache = new AsyncCache<UFile>({globalTtl: 15_000});
    private collectionCache = new AsyncCache<FileCollection>({globalTtl: 15_000});

    private clipboard: UFile[] = [];
    private clipboardIsCut: boolean = false;

    private altKeys = ["KeyQ", "KeyW", "KeyE", "KeyR", "KeyT"];
    private altShortcuts: (() => void)[] = [doNothing, doNothing, doNothing, doNothing, doNothing];

    private contextMenu: HTMLDivElement;
    private contextMenuHandlers: (() => void)[] = [];

    private renameField: HTMLInputElement;
    private renameFieldIndex: number = -1;
    private renameValue: string = "";
    private renameOnSubmit: () => void = doNothing;
    private renameOnCancel: () => void = doNothing;

    private locationBar: HTMLInputElement;
    private locationBarTabIndex: number = -1;
    private locationBarTabCount: number = 0;

    private processingShortcut: boolean = false;

    public onOpen: (path: string) => void = doNothing;
    public dispatch: Dispatch = doNothing as Dispatch;

    constructor(root: HTMLDivElement) {
        this.root = root;
    }

    mount() {
        FileBrowser.injectStyle();

        this.root.classList.add("file-browser");
        this.root.innerHTML = `
            <header>
                <div class="header-first-row">
                    <ul></ul>
                    <input class="location-bar">
                </div>
                <div class="operations"></div>
            </header>
            
            <div style="overflow-y: auto; position: relative;">
                <div class="scrolling">
                    <input class="rename-field">
                </div>
            </div>
            
            <div class="drag-indicator"></div>
            <div class="context-menu"></div>
        `;

        this.operations = this.root.querySelector<HTMLElement>(".operations")!;
        this.dragIndicator = this.root.querySelector<HTMLDivElement>(".drag-indicator")!;
        this.contextMenu = this.root.querySelector<HTMLDivElement>(".context-menu")!;
        this.scrolling = this.root.querySelector<HTMLDivElement>(".scrolling")!;
        this.renameField = this.root.querySelector<HTMLInputElement>(".rename-field")!;
        this.locationBar = this.root.querySelector<HTMLInputElement>(".location-bar")!;
        this.header = this.root.querySelector("header")!;
        this.breadcrumbs = this.root.querySelector<HTMLUListElement>("header ul")!;

        {
            // Render edit button for the location bar
            const headerFirstRow = this.header.querySelector(".header-first-row")!;
            const img = image(placeholderImage, { width: 24, height: 24 });
            headerFirstRow.append(img);
            this.icons.renderIcon({ name: "edit", color: "iconColor", color2: "iconColor2", width: 64, height: 64 })
                .then(url => img.src = url);
            img.addEventListener("click", () => {
                this.toggleLocationBar();
            });
        }

        this.renameField.addEventListener("keydown", ev => {
            ev.stopPropagation();
            if (this.processingShortcut) {
                ev.preventDefault();
                return;
            }

            switch (ev.code) {
                case "Enter": {
                    this.closeRenameField("submit");
                    break;
                }

                case "Escape": {
                    this.closeRenameField("cancel");
                    break;
                }

                case "Home":
                case "End": {
                    ev.preventDefault();
                    break;
                }
            }
        });

        this.renameField.addEventListener("beforeinput", ev => {
            if (this.processingShortcut) {
                ev.preventDefault();
                ev.stopPropagation();
            }
        });
        this.renameField.addEventListener("input", ev => {
            ev.preventDefault();
            ev.stopPropagation();
            if (this.processingShortcut) return;
            this.renameValue = this.renameField.value;
        });

        this.scrolling.parentElement!.addEventListener("scroll", () => {
            if (this.ignoreScrollEvent) {
                this.ignoreScrollEvent = false;
                return;
            }

            this.renderPage();
            this.fetchNext();
        });

        this.locationBar.addEventListener("keydown", ev => {
            this.onLocationBarKeyDown(ev);
        });

        this.locationBar.addEventListener("input", ev => {
            this.onLocationBarKeyDown("input");
        });

        const keyDownListener = (ev: KeyboardEvent) => {
            if (!this.root.isConnected) {
                document.removeEventListener("keydown", keyDownListener);
                return;
            }

            this.onKeyPress(ev);
        };
        document.addEventListener("keydown", keyDownListener);

        const clickHandler = ev => {
            if (!this.root.isConnected) {
                document.removeEventListener("click", clickHandler);
                return;
            }

            if (this.contextMenuHandlers.length) {
                this.closeContextMenu();
            }

            if (this.renameFieldIndex !== -1 && ev.target !== this.renameField) {
                this.closeRenameField("submit");
            }
        };
        document.addEventListener("click", clickHandler);

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

    private renderBreadcrumbs() {
        const path = pathComponents(this.currentPath);
        const collection = this.collectionCache.retrieveFromCacheOnly(path[0]);
        const collectionName = collection ? `${collection.specification.title} (${path[0]})` : path[0];

        this.breadcrumbs.innerHTML = "";
        const fragment = document.createDocumentFragment();
        let pathBuilder = "";
        let idx = 0;
        for (const component of path) {
            pathBuilder += "/" + component;
            const myPath = pathBuilder;

            const listItem = document.createElement("li");
            listItem.innerText = idx === 0 ? collectionName : component;
            listItem.addEventListener("click", () => {
                this.open(myPath);
            });

            fragment.append(listItem);
            idx++;
        }
        this.breadcrumbs.append(fragment);
    }

    private renderOperations() {
        function groupOperations<T, R>(ops: Operation<T, R>[]): OperationOrGroup<T, R>[] {
            const uploadOp = ops.find(it => it.icon === "upload");
            const folderOp = ops.find(it => it.icon === "uploadFolder");
            const result: OperationOrGroup<T, R>[] = [];
            if (uploadOp && folderOp) {
                result.push({
                    color: "iconColor",
                    icon: "chevronDown",
                    text: "Create...",
                    operations: [uploadOp, folderOp]
                });
            }
            let i = 0;
            for (; i < ops.length && result.length < 4; i++) {
                const op = ops[i];
                if (op === uploadOp || op === folderOp) continue;
                result.push(op);
            }

            const overflow: Operation<T, R>[] = [];
            for (; i < ops.length; i++) {
                overflow.push(ops[i]);
            }

            if (overflow.length > 0) {
                result.push({
                    color: "iconColor",
                    icon: "ellipsis",
                    text: "",
                    iconRotation: 90,
                    operations: overflow,
                })
            }

            return result;
        }

        const path = this.currentPath;
        const components = pathComponents(path);
        const collection = this.collectionCache.retrieveFromCacheOnly(components[0]);
        const folder = this.folderCache.retrieveFromCacheOnly(path);
        if (!collection || !folder) return;

        for (let i = 0; i < this.altShortcuts.length; i++) {
            this.altShortcuts[i] = doNothing;
        }

        const supportByProvider: SupportByProvider = {productsByProvider: {}};
        supportByProvider.productsByProvider[collection.specification.product.provider] = [collection.status.resolvedSupport!];

        const self = this;
        const callbacks: ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks = {
            supportByProvider,
            allowMoveCopyOverride: false,
            collection: collection!,
            directory: folder!,
            dispatch: this.dispatch,
            embedded: false,
            isWorkspaceAdmin: false,
            navigate: () => {
                // TODO
            },
            reload: () => this.refresh(),
            setSynchronization(file: UFile, shouldAdd: boolean): void {
                // TODO
            },
            startCreation(): void {
                self.showCreateDirectory();
            },
            cancelCreation: doNothing,
            startRenaming(resource: UFile, defaultValue: string): void {
                self.startRenaming(resource.id);
            },
            viewProperties(res: UFile): void {
                // TODO
            },
            commandLoading: false,
            invokeCommand: call => callAPI(call),
            api: FilesApi,
            isCreating: false
        };

        const selected: UFile[] = [];
        const page = this.cachedData[path] ?? [];
        {
            const selection = this.isSelected;
            for (let i = 0; i < selection.length && i < page.length; i++) {
                if (selection[i] !== 0) {
                    selected.push(page[i]);
                }
            }
        }

        const renderOpIconAndText = (
            op: OperationOrGroup<unknown, unknown>,
            element: HTMLElement,
            shortcut?: string,
        ) => {
            {
                // Set the icon
                const icon = image(placeholderImage, {height: 16, width: 16, alt: "Icon"});
                element.append(icon);
                this.icons.renderIcon({
                    name: op.icon as IconName,
                    color: op.color as ThemeColor,
                    color2: "iconColor2",
                    width: 64,
                    height: 64,
                }).then(url => icon.src = url);
                if (op.iconRotation) {
                    icon.style.transform = `rotate(${op.iconRotation}deg)`;
                }
            }

            {
                // ...and the text
                let operationText = "";
                if (typeof op.text === "string") {
                    operationText = op.text;
                } else {
                    operationText = op.text(selected, callbacks);
                }
                if (operationText) element.append(operationText);
                if (operationText && shortcut) {
                    const shortcutElem = document.createElement("kbd");
                    shortcutElem.append(shortcut);
                    element.append(shortcutElem);
                }
            }
        }

        let opCount = 0;
        const renderOperation = (
            displayInHeader: boolean,
            op: OperationOrGroup<UFile, ResourceBrowseCallbacks<UFile> & ExtraFileCallbacks>
        ): HTMLElement => {
            const element = document.createElement("div");
            element.classList.add("operation");
            element.classList.add(displayInHeader ? "in-header" : "in-context-menu");

            const altKey = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌥" : "Alt + ";
            renderOpIconAndText(op, element, `[${altKey}${this.altKeys[opCount].replace("Key", "")}]`);

            {
                // ...and the handlers
                const handler = (ev?: Event) => {
                    ev?.stopPropagation();
                    if ("operations" in op) {
                        const elementBounding = element.getBoundingClientRect();
                        const menu = this.contextMenu;
                        menu.innerHTML = "";
                        menu.style.top = (elementBounding.top + elementBounding.height) + "px";
                        menu.style.left = elementBounding.left + "px";
                        menu.style.display = "block";

                        const menuList = document.createElement("ul");
                        let shortcutNumber = 1;
                        for (const child of op.operations) {
                            const item = document.createElement("li");
                            renderOpIconAndText(child, item, `[${shortcutNumber}]`);

                            const myIndex = shortcutNumber - 1;
                            const text = item.innerText;
                            this.contextMenuHandlers.push(() => {
                                child.onClick(selected, callbacks, page);
                            });
                            item.addEventListener("mouseover", () => {
                                this.findActiveContextMenuItem(true);
                                this.selectContextMenuItem(myIndex);
                            });
                            item.addEventListener("click", ev => {
                                ev.stopPropagation();
                                this.findActiveContextMenuItem(true);
                                this.selectContextMenuItem(myIndex);
                                this.onContextMenuItemSelection();
                            });

                            menuList.append(item);
                            shortcutNumber++;
                        }
                        menu.append(menuList);
                    } else if ("onClick" in op) {
                        op.onClick(selected, callbacks, page);
                    }
                };

                element.addEventListener("click", handler);
                if (displayInHeader) {
                    this.altShortcuts[opCount] = handler;
                    opCount++;
                }
            }

            return element;
        }

        const operations = groupOperations(
            FilesApi.retrieveOperations().filter(op => op.enabled(selected, callbacks, page))
        );
        this.operations.innerHTML = "";
        for (const op of operations) {
            this.operations.append(renderOperation(true, op));
        }
    }

    refresh() {
        this.open(this.currentPath, true);
    }

    open(path: string, force?: boolean) {
        if (this.currentPath === path && force !== true) return;
        const oldPath = this.currentPath;

        // Set new state and notify event handlers
        path = resolvePath(path);
        this.currentPath = path;
        this.isFetchingNext = false;
        this.onOpen(path);

        // Reset state
        this.isSelected = new Uint8Array(0);
        this.lastSingleSelection = -1;
        this.lastListSelectionEnd = -1;
        window.clearTimeout(this.searchQueryTimeout);
        this.searchQueryTimeout = -1;
        this.searchQuery = "";
        // NOTE(Dan): In this case we are _not_ running the onCancel function, maybe we should?
        this.renameFieldIndex = -1;
        this.renameValue = "";
        this.locationBarTabIndex = -1;
        this.locationBar.value = path + "/";
        this.locationBar.dispatchEvent(new Event("input"));

        // NOTE(Dan): Need to get this now before we call renderPage(), since it will reset the scroll position.
        const scrollPositionElement = this.scrollPosition[path];

        // Perform most renders
        this.renderBreadcrumbs();
        this.renderOperations();
        this.renderPage();

        // Fetch all the required information and perform conditional renders if needed
        const collectionId = pathComponents(path)[0];

        this.folderCache
            .retrieve(path, () => callAPI(FilesApi.retrieve({id: path})))
            .then(() => this.renderOperations());

        this.collectionCache
            .retrieve(collectionId, () => callAPI(FileCollectionsApi.retrieve({
                id: collectionId,
                includeOthers: true,
                includeSupport: true
            })))
            .then(() => {
                this.renderBreadcrumbs();
                this.renderOperations();
                this.locationBar.dispatchEvent(new Event("input"));
            });

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
        this.selectByPath(oldPath, SelectionMode.SINGLE);
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

        if (render) {
            this.renderPage();
            this.renderOperations();
        }
    }

    findRowIndexByPath(path: string): number | null {
        let idx = 0;
        for (const file of this.cachedData[this.currentPath] ?? []) {
            if (file.id === path) {
                return idx;
            }

            idx++;
        }
        return null;
    }

    selectByPath(path: string, mode: SelectionMode) {
        let idx = 0;
        for (const file of this.cachedData[this.currentPath] ?? []) {
            if (file.id === path) {
                this.select(idx, mode);
                break;
            }

            idx++;
        }
    }

    private renderPage() {
        const page = this.cachedData[this.currentPath] ?? [];
        if (this.isSelected.length < page.length) {
            const newSelected = new Uint8Array(page.length);
            newSelected.set(this.isSelected, 0);
            this.isSelected = newSelected;
        } else if (this.isSelected.length > page.length) {
            this.isSelected = new Uint8Array(page.length);
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

        this.renameField.style.display = "none";

        // Render the visible rows by iterating over all items
        for (let i = 0; i < page.length; i++) {
            const file = page[i];
            const row = findRow(i);
            if (!row) continue;

            if (i === this.renameFieldIndex) {
                this.renameField.style.display = "block";
                const top = parseInt(row.container.style.top.replace("px", ""));
                this.renameField.style.top = `${top + ((FileBrowser.rowSize - 30) / 2)}px`;
                this.renameField.value = this.renameValue;
                this.renameField.focus();
            }

            row.container.setAttribute("data-file-idx", i.toString());
            row.container.setAttribute("data-file", file.id);
            row.container.setAttribute("data-selected", (this.isSelected[i] !== 0).toString());
            row.selected.checked = (this.isSelected[i] !== 0);
            row.container.classList.remove("hidden");
            row.title.innerText = fileName(file.id);
            row.stat2.innerText = dateToString(file.status.modifiedAt ?? file.status.accessedAt ?? timestampUnixMs());
            row.stat3.innerText = sizeToString(file.status.sizeIncludingChildrenInBytes ?? file.status.sizeInBytes ?? null);

            const isOutOfDate = () => row.container.getAttribute("data-file") !== file.id;

            if (!hasAnySelected) {
                FileBrowser.findFavoriteStatus(file).then(async (isFavorite) => {
                    const icon = await this.icons.renderIcon({
                        name: (isFavorite ? "starFilled" : "starEmpty"),
                        color: (isFavorite ? "blue" : "midGray"),
                        color2: "midGray",
                        height: 64,
                        width: 64
                    });

                    if (isOutOfDate()) return;

                    row.favorite.innerHTML = "";
                    row.favorite.append(image(icon, {width: 20, height: 20, alt: "Star"}))
                    row.favorite.style.cursor = "pointer";
                });
            }

            FileBrowser.findSensitivity(file).then(sensitivity => {
                if(isOutOfDate()) return;
                row.stat1.innerHTML = ""; // NOTE(Dan): Clear the container regardless
                if (!sensitivity) return;

                const badge = div("");
                badge.classList.add("sensitivity-badge");
                badge.classList.add(sensitivity.toString());
                badge.innerText = sensitivity.toString()[0];

                row.stat1.append(badge);
            });

            this.renderFileIcon(file).then(url => {
                if (isOutOfDate()) return;

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
        const relativeSelectFiles = (delta: number, forceShift?: boolean) => {
            ev.preventDefault();
            const shift = forceShift ?? ev.shiftKey;
            if (!shift) {
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

        const relativeContextMenuSelect = (delta: number) => {
            const ul = this.contextMenu.querySelector("ul");
            if (!ul) return;
            const listItems = ul.querySelectorAll("li");

            let selectedIndex = -1;
            for (let i = 0; i < listItems.length; i++) {
                const item = listItems.item(i);
                if (item.getAttribute("data-selected") === "true") {
                    selectedIndex = i;
                }

                item.removeAttribute("data-selected");
            }

            selectedIndex = selectedIndex + delta;
            if (Math.abs(delta) > 1 && (selectedIndex < 0 || selectedIndex >= listItems.length)) {
                // Large jumps should not cause wrap around, instead move to either the top or the bottom.
                if (selectedIndex < 0) selectedIndex = 0;
                else selectedIndex = listItems.length - 1;
            }

            // Wrap around the selection, moving up from the top should go to the bottom and vice-versa.
            if (selectedIndex < 0) selectedIndex = listItems.length - 1;
            else if (selectedIndex >= listItems.length) selectedIndex = 0;

            listItems.item(selectedIndex).setAttribute("data-selected", "true");
        };

        const relativeSelect = (delta: number, forceShift?: boolean) => {
            if (!this.contextMenuHandlers.length) {
                return relativeSelectFiles(delta, forceShift);
            } else {
                return relativeContextMenuSelect(delta);
            }
        };

        if (ev.ctrlKey || ev.metaKey) {
            let didHandle = true;
            switch (ev.code) {
                case "KeyA": {
                    if (this.contextMenuHandlers.length) return;

                    relativeSelect(-1000000000, false);
                    relativeSelect(1000000000, true);
                    break;
                }

                case "KeyX":
                case "KeyC": {
                    if (this.contextMenuHandlers.length) return;

                    const newClipboard: UFile[] = [];
                    const page = this.cachedData[this.currentPath];
                    const selected = this.isSelected;
                    for (let i = 0; i < selected.length && i < page.length; i++) {
                        if (selected[i] !== 0) newClipboard.push(page[i]);
                    }
                    this.clipboard = newClipboard;
                    this.clipboardIsCut = ev.code === "KeyX";
                    if (newClipboard.length) {
                        const key = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌘" : "Ctrl + ";
                        snackbarStore.addInformation(
                            `${newClipboard.length} copied to clipboard. Use ${key}V to insert the files.`,
                            false
                        );
                    }
                    break;
                }

                case "KeyV": {
                    if (this.contextMenuHandlers.length) return;
                    if (this.clipboard.length) {
                        // Optimistically update the user-interface to contain the new state
                        let lastEntry: string | null = null;
                        for (const entry of this.clipboard) {
                            lastEntry = this.insertFakeEntry(
                                fileName(entry.id),
                                {
                                    type: entry.status.type,
                                    modifiedAt: timestampUnixMs(),
                                    size: 0
                                }
                            );

                            // NOTE(Dan): Putting this after the insertion is consistent with the most common
                            // backends, even though it produces surprising results.
                            if (this.clipboardIsCut) this.removeEntry(entry.id);
                        }

                        this.renderPage();
                        if (lastEntry) {
                            const idx = this.findRowIndexByPath(lastEntry);
                            if (idx !== null) {
                                this.ensureRowIsVisible(idx, true, true);
                                this.select(idx, SelectionMode.SINGLE);
                            }
                        }

                        // Perform the requested action
                        const requestPayload = bulkRequestOf(
                            ...this.clipboard.map<FilesMoveRequestItem>(file => ({
                                oldId: file.id,
                                newId: this.currentPath + "/" + fileName(file.id),
                                conflictPolicy: "RENAME",
                            }))
                        );

                        const call = this.clipboardIsCut ?
                            FilesApi.move(requestPayload) :
                            FilesApi.copy(requestPayload);

                        callAPI(call).catch(err => {
                            snackbarStore.addFailure(extractErrorMessage(err), false);
                            this.refresh();
                        });

                        // Cleanup the clipboard if needed
                        if (this.clipboardIsCut) this.clipboard = [];
                    }
                    break;
                }

                case "KeyG": {
                    this.toggleLocationBar();
                    break;
                }

                default: {
                    didHandle = false;
                    break;
                }
            }

            if (didHandle) {
                ev.preventDefault();
                ev.stopPropagation();
            }
        } else if (ev.altKey) {
            const altCodeIndex = this.altKeys.indexOf(ev.code);
            if (altCodeIndex >= 0 && altCodeIndex < this.altShortcuts.length) {
                ev.preventDefault();
                ev.stopPropagation();

                this.altShortcuts[altCodeIndex]();
            }
        } else {
            // NOTE(Dan): Don't add printable keys to the switch statement here, as it will break the search
            // functionality. Instead, add it to the default case.
            switch (ev.code) {
                case "Escape": {
                    if (this.contextMenuHandlers.length) {
                        this.closeContextMenu();
                    } else {
                        const selected = this.isSelected;
                        for (let i = 0; i < selected.length; i++) {
                            selected[i] = 0;
                        }
                        this.renderPage();
                        this.renderOperations();
                    }
                    break;
                }

                case "F2": {
                    if (this.contextMenuHandlers.length) return;

                    const page = this.cachedData[this.currentPath] ?? [];
                    const selection = this.isSelected;
                    for (let i = 0; i < selection.length && i < page.length; i++) {
                        if (selection[i] !== 0) {
                            this.startRenaming(page[i].id);
                            break;
                        }
                    }
                    break;
                }

                case "ArrowUp": {
                    ev.preventDefault();
                    relativeSelect(-1);
                    break;
                }

                case "ArrowDown": {
                    ev.preventDefault();
                    relativeSelect(1);
                    break;
                }

                case "Home": {
                    ev.preventDefault();
                    relativeSelect(-1000000000);
                    break;
                }

                case "End": {
                    ev.preventDefault();
                    relativeSelect(1000000000);
                    break;
                }

                case "PageUp": {
                    ev.preventDefault();
                    relativeSelect(-50);
                    break;
                }

                case "PageDown": {
                    ev.preventDefault();
                    relativeSelect(50);
                    break;
                }

                case "Backspace": {
                    if (this.contextMenuHandlers.length) return;
                    this.open(getParentPath(this.currentPath));
                    break;
                }

                case "Enter": {
                    if (this.contextMenuHandlers.length) {
                        this.onContextMenuItemSelection();
                    } else {
                        const selected = this.isSelected;
                        for (let i = 0; i < selected.length; i++) {
                            if (selected[i] !== 0) {
                                const file = this.cachedData[this.currentPath][i];
                                if (file.status.type === "DIRECTORY") this.open(file.id);
                                break;
                            }
                        }
                    }
                    break;
                }

                default: {
                    if (this.contextMenuHandlers.length) {
                        if (ev.code.startsWith("Digit")) {
                            this.processingShortcut = true;
                            window.setTimeout(() => {
                                this.processingShortcut = false;
                            }, 0);

                            const selectedItem = parseInt(ev.code.substring("Digit".length));
                            if (!isNaN(selectedItem)) {
                                this.selectContextMenuItem(selectedItem - 1);
                                this.onContextMenuItemSelection();
                            }
                        }
                    } else {
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
                    }
                    break;
                }
            }
        }
    }

    private onLocationBarKeyDown(ev: "input" | KeyboardEvent) {
        if (ev !== "input") ev.stopPropagation();

        const attrRealPath = "data-real-path";

        const setValue = (path: string): string | null => {
            if (path.length === 0) return null;
            if (path.startsWith("/")) path = path.substring(1);
            let endOfFirstComponent = path.indexOf("/");
            if (endOfFirstComponent === -1) endOfFirstComponent = path.length;

            let collectionId: string | null = null;

            const firstComponent = path.substring(0, endOfFirstComponent);
            if (firstComponent === "~") {
                const currentComponents = pathComponents(this.currentPath);
                if (currentComponents.length > 0) collectionId = currentComponents[0];
            } else {
                let parenthesisStart = firstComponent.indexOf("(");
                let parenthesisEnd = firstComponent.indexOf(")");
                if (parenthesisStart !== -1 && parenthesisEnd !== -1 && parenthesisStart < parenthesisEnd) {
                    const parsedNumber = parseInt(firstComponent.substring(parenthesisStart + 1, parenthesisEnd));
                    if (!isNaN(parsedNumber) && parsedNumber > 0) {
                        collectionId = parsedNumber.toString();
                    }
                } else {
                    const parsedNumber = parseInt(firstComponent);
                    if (!isNaN(parsedNumber) && parsedNumber > 0) {
                        collectionId = parsedNumber.toString();
                    }
                }
            }

            if (collectionId === null) return null; // TODO(Dan): Do something else?

            const collection = this.collectionCache.retrieveFromCacheOnly(collectionId);
            const collectionName = collection ? `${collection.specification.title} (${collectionId})` : collectionId;
            const remainingPath = path.substring(endOfFirstComponent);

            this.locationBar.value = `/${collectionName}${remainingPath}`;

            const realPath = `/${collectionId}${remainingPath}`;
            this.locationBar.setAttribute(attrRealPath, realPath);
            return realPath;
        };

        const readValue = (): string | null => {
            if (!this.locationBar.hasAttribute(attrRealPath)) {
                return setValue(this.locationBar.value);
            }
            return this.locationBar.getAttribute(attrRealPath);
        };

        const doTabComplete = (allowFetch: boolean = true) => {
            const path = readValue();
            if (path === null) return;

            if (this.locationBarTabIndex === -1) this.locationBarTabIndex = path.length;

            const pathPrefix = path.substring(0, this.locationBarTabIndex);
            const parentPath = pathPrefix.endsWith("/") ?
                pathPrefix :
                resolvePath(getParentPath(pathPrefix));

            const page = this.cachedData[parentPath];
            if (page == null) {
                if (!allowFetch) return;
                this.prefetch(parentPath).then(() => {
                    if (readValue() !== path) return;
                    doTabComplete(false);
                });
                return;
            }

            const fileNamePrefix = pathPrefix.endsWith("/") ?
                "" :
                fileName(pathPrefix).toLowerCase();

            let firstMatch: UFile | null = null;
            let acceptedMatch: UFile | null = null;
            let matchCount = 0;
            let tabCount = this.locationBarTabCount;
            for (const file of page) {
                if (file.status.type !== "DIRECTORY") continue;
                let s = fileName(file.id).toLowerCase();
                if (s.startsWith(fileNamePrefix)) {
                    matchCount++;
                    if (!firstMatch) firstMatch = file;

                    if (tabCount === 0 && !acceptedMatch) {
                        acceptedMatch = file;
                    } else {
                        tabCount--;
                    }
                }
            }

            if (acceptedMatch !== null || firstMatch !== null) {
                const match = (acceptedMatch ?? firstMatch)!;
                this.locationBarTabCount = acceptedMatch ? this.locationBarTabCount + 1 : 1;

                let newValue = match.id;
                if (match.status.type === "DIRECTORY") newValue += "/";
                setValue(newValue);

                if (matchCount === 1) {
                    let readValue1 = readValue();
                    this.locationBarTabIndex = readValue1?.length ?? 0;
                    this.locationBarTabCount = 0;
                }
            }
        };

        if (ev === "input") {
            this.locationBarTabIndex = -1;
            this.locationBarTabCount = 0;

            setValue(this.locationBar.value);
        } else {
            switch (ev.code) {
                case "Tab": {
                    ev.preventDefault();
                    doTabComplete();
                    break;
                }

                case "Enter": {
                    const newPath = readValue();
                    if (newPath) {
                        this.closeLocationBar();
                        this.open(newPath);
                    }
                    break;
                }

                case "Escape": {
                    this.closeLocationBar();
                    setValue(this.currentPath);
                    break;
                }
            }
        }
    }

    private toggleLocationBar() {
        this.header.classList.toggle("show-location-bar");
        this.locationBar.focus();
        this.locationBar.setSelectionRange(0, this.locationBar.value.length);
    }

    private closeLocationBar() {
        this.header.classList.remove("show-location-bar");
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

    private insertFakeEntry(
        name: string,
        opts?: {
            type?: FileType,
            hint?: FileIconHint,
            modifiedAt?: number,
            size?: number
        }
    ): string {
        const page = this.cachedData[this.currentPath] ?? [];
        const existing = page.find(it => fileName(it.id) === name);
        let actualName: string = name;

        let likelyProduct: ProductReference = { id: "", provider: "", category: "" };
        if (page.length > 0) likelyProduct = page[0].specification.product;

        let likelyOwner: ResourceOwner = { createdBy: Client.username ?? "", project: Client.projectId };
        if (page.length > 0) likelyOwner = page[0].owner;

        let likelyPermissions: ResourcePermissions = { myself: ["ADMIN", "READ", "EDIT"] };
        if (page.length > 0) likelyPermissions = page[0].permissions;

        if (existing != null) {
            const hasExtension = name.includes(".");
            const baseName = name.substring(0, hasExtension ? name.lastIndexOf(".") : undefined);
            const extension = hasExtension ? name.substring(name.lastIndexOf(".") + 1) : undefined;

            let attempt = 1;
            while (true) {
                actualName = `${baseName}(${attempt})`;
                if (hasExtension) actualName += `.${extension}`;
                if (page.find(it => fileName(it.id) === actualName) === undefined) break;
                attempt++;
            }
        }

        const path = resolvePath(this.currentPath) + "/" + actualName;
        page.push({
            createdAt: opts?.modifiedAt ?? 0,
            owner: likelyOwner,
            permissions: likelyPermissions,
            specification: {
                product: likelyProduct,
                collection: ""
            },
            id: path,
            updates: [],
            status: {
                type: opts?.type ?? "FILE",
                modifiedAt: opts?.modifiedAt,
                sizeInBytes: opts?.size
            }
        });

        page.sort((a, b) => a.id.localeCompare(b.id));
        return path;
    }

    private removeEntry(path: string) {
        const page = this.cachedData[resolvePath(getParentPath(path))];
        if (!page) return;
        const entryIdx = page.findIndex(it => it.id === path);
        if (entryIdx !== -1) page.splice(entryIdx, 1);
    }

    private closeContextMenu() {
        this.contextMenuHandlers = [];
        this.contextMenu.style.display = "none";
    }

    private findActiveContextMenuItem(clearActive: boolean = true): number {
        const listItems = this.contextMenu.querySelectorAll("ul li");
        let selectedIndex = -1;
        for (let i = 0; i < listItems.length; i++) {
            const item = listItems.item(i);
            if (item.getAttribute("data-selected") === "true") {
                selectedIndex = i;
            }

            if (clearActive) item.removeAttribute("data-selected");
        }

        return selectedIndex;
    }

    private selectContextMenuItem(index: number) {
        const listItems = this.contextMenu.querySelectorAll("ul li");
        if (index < 0 || index >= listItems.length) return;
        listItems.item(index).setAttribute("data-selected", "true");
    }

    private onContextMenuItemSelection() {
        if (!this.contextMenuHandlers.length) return;
        const idx = this.findActiveContextMenuItem(false);
        if (idx < 0 || idx >= this.contextMenuHandlers.length) return;
        this.contextMenuHandlers[idx]();
        this.closeContextMenu();
    }

    private startRenaming(path: string) {
        this.showRenameField(
            path,
            () => {
                const parentPath = resolvePath(getParentPath(path));
                const page = this.cachedData[parentPath] ?? [];
                const actualFile = page.find(it => fileName(it.id) === fileName(path));
                if (actualFile) {
                    const oldId = actualFile.id;
                    actualFile.id = parentPath + "/" + this.renameValue;
                    page.sort((a, b) => fileName(a.id).localeCompare(fileName(b.id)));
                    const newRow = this.findRowIndexByPath(actualFile.id);
                    if (newRow != null) {
                        this.ensureRowIsVisible(newRow, true, true);
                        this.select(newRow, SelectionMode.SINGLE);
                    }

                    callAPI(FilesApi.move(bulkRequestOf({
                        oldId,
                        newId: actualFile.id,
                        conflictPolicy: "REJECT"
                    }))).catch(err => {
                        snackbarStore.addFailure(extractErrorMessage(err), false);
                        this.refresh();
                    });
                }
            },
            doNothing
        );
    }

    private shouldRemoveFakeDirectory: boolean = true;
    private showCreateDirectory() {
        const fakePath = resolvePath(this.currentPath) + "/$NEW_DIR";
        if (this.findRowIndexByPath(fakePath) != null) {
            this.removeEntry(fakePath);
        }
        this.shouldRemoveFakeDirectory = false;
        this.insertFakeEntry("$NEW_DIR", { type: "DIRECTORY" });

        this.showRenameField(
            fakePath,
            () => {
                this.removeEntry(fakePath);
                if (!this.renameValue) return;

                const realPath = resolvePath(this.currentPath) + "/" + this.renameValue;
                this.insertFakeEntry(this.renameValue, { type: "DIRECTORY" });
                const idx = this.findRowIndexByPath(realPath);
                if (idx !== null) {
                    this.ensureRowIsVisible(idx, true, true);
                    this.select(idx, SelectionMode.SINGLE);
                }
                callAPI(FilesApi.createFolder(bulkRequestOf({ id: realPath, conflictPolicy: "RENAME" })))
                    .catch(err => {
                        snackbarStore.addFailure(extractErrorMessage(err), false);
                        this.refresh();
                    });
            },
            () => {
                if (this.shouldRemoveFakeDirectory) this.removeEntry(fakePath);
            },
            ""
        );
        this.shouldRemoveFakeDirectory = true;
    }

    private showRenameField(
        path: string,
        onSubmit: () => void,
        onCancel: () => void,
        initialValue: string = fileName(path)
    ) {
        const idx = this.findRowIndexByPath(path);
        if (idx == null) return;

        this.closeRenameField("cancel", false);
        this.renameFieldIndex = idx;
        this.renameValue = initialValue;
        this.renameOnSubmit = onSubmit;
        this.renameOnCancel = onCancel;
        this.renderPage();

        const extensionStart = initialValue.lastIndexOf(".");
        const selectionEnd = extensionStart === -1 ? initialValue.length : extensionStart;
        this.renameField.setSelectionRange(0, selectionEnd);
    }

    private closeRenameField(why: "submit" | "cancel", render: boolean = true) {
        if (this.renameFieldIndex !== -1) {
            if (why === "submit") this.renameOnSubmit();
            else this.renameOnCancel();
        }

        this.renameFieldIndex = -1;
        this.renameOnSubmit = doNothing;
        this.renameOnCancel = doNothing;
        if (render) this.renderPage();
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
                approval: {type: "not_required"},
            },
            createdAt: 0,
            createdBy: "",
            specification: {
                templateId: templateId!,
                changeLog: "",
                document: {favorite: isFavorite} as Record<string, any>,
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
            
            .file-browser header .header-first-row {
                display: flex;
            }
            
            .file-browser header .header-first-row img {
                cursor: pointer;
                flex-shrink: 0;
                margin-left: 16px;
                margin-top: 5px;
            }
            
            .file-browser header .header-first-row ul,
            .file-browser header .header-first-row .location-bar {
                flex-grow: 1;
            }

            .file-browser header ul {
                padding: 0;
                margin: 0 0 8px;
                display: flex;
                flex-direction: row;
                gap: 8px;
                height: 35px;
            }

            .file-browser > div {
                flex-grow: 1;
            }
            
            .file-browser header {
                width: 100%;
                height: 100px;
                flex-shrink: 0;
                overflow: hidden;
            }
            
            .file-browser header .location-bar,
            .file-browser header.show-location-bar ul {
                display: none;
            }

            .file-browser header.show-location-bar .location-bar,
            .file-browser header ul {
                display: flex;
            }
            
            .file-browser .location-bar {
                width: 100%;
                font-size: 120%;
                height: 35px;
                margin-bottom: 8px;
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

            .file-browser .operation {
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 8px;
            }

            .file-browser .operation.in-header {
                padding: 11px;
                background: #F5F5F5;
                border-radius: 8px;
            }

            .file-browser .operations {
                display: flex;
                flex-direction: row;
                gap: 16px;
            }

            .file-browser .context-menu {
                position: fixed;
                z-index: 10000;
                top: 0;
                left: 0;
                border-radius: 8px;
                border: 1px solid #E2DDDD;
                cursor: pointer;
                background: var(--white);
                box-shadow: 0 3px 6px rgba(0, 0, 0, 30%);
                width: 400px;
                display: none;
            }
            
            .file-browser .context-menu ul {
                padding: 0;
                margin: 0;
                display: flex;
                flex-direction: column;
            }
            
            .file-browser .context-menu li {
                margin: 0;
                padding: 8px 8px;
                list-style: none;
                display: flex;
                align-items: center;
                gap: 8px;
            }
            
            .file-browser .context-menu li kbd {
                flex-grow: 1;
                text-align: end;
            }
            
            .file-browser .context-menu li[data-selected=true] {
                background: #DAE4FD;
            }
            
            .file-browser .rename-field {
                display: none;
                position: absolute;
                z-index: 10000;
                top: 0;
                left: 60px;
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

// https://stackoverflow.com/a/13139830
const placeholderImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

export default ExperimentalBrowse;
