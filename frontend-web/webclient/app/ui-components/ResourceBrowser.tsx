import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {IconName} from "@/ui-components/Icon";
import {
    ThemeColor,
    addThemeListener,
    removeThemeListener,
    selectContrastColor,
    selectHoverColor
} from "@/ui-components/theme";
import {SvgCache} from "@/Utilities/SvgCache";
import {capitalized, createHTMLElements, doNothing, inDevEnvironment, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {fileName, resolvePath} from "@/Utilities/FileUtilities";
import {visualizeWhitespaces} from "@/Utilities/TextUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {PageV2} from "@/UCloud";
import {injectStyle, makeClassName, injectStyle as unstyledInjectStyle} from "@/Unstyled";
import {InputClass} from "./Input";
import {getStartOfDay} from "@/Utilities/DateUtilities";
import {createPortal} from "react-dom";
import {ContextSwitcher, projectCache} from "@/Project/ContextSwitcher";
import {addProjectListener, removeProjectListener} from "@/Project/ReduxState";
import {ProductType, ProductV2} from "@/Accounting";
import ProviderInfo from "@/Assets/provider_info.json";
import {ProductSelector} from "@/Products/Selector";
import {Client} from "@/Authentication/HttpClientInstance";
import {div, image} from "@/Utilities/HTMLUtilities";
import {ConfirmationButtonPlainHTML} from "./ConfirmationAction";
import {HTMLTooltip} from "./Tooltip";
import {ButtonClass} from "./Button";
import {ResourceIncludeFlags} from "@/UCloud/ResourceApi";
import {TruncateClass} from "./Truncate";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import Flex, {FlexClass} from "./Flex";
import * as Heading from "@/ui-components/Heading";
import {dialogStore} from "@/Dialog/DialogStore";
import {isAdminOrPI} from "@/Project";

const CLEAR_FILTER_VALUE = "\n\nCLEAR_FILTER\n\n";

export type Filter = FilterWithOptions | FilterCheckbox | FilterInput | MultiOptionFilter;
export interface ResourceBrowserOpts<T> {
    additionalFilters?: Record<string, string> & ResourceIncludeFlags;
    omitFilters?: boolean;
    disabledKeyhandlers?: boolean;
    reloadRef?: React.MutableRefObject<() => void>;
    
    // Note(Jonas): Embedded changes a few stylings, omits shortcuts from operations, but I believe operations
    // are entirely omitted. Fetches only the first page, based on the amount passed by additionalFeatures or default.
    embedded?: boolean;
    // Note(Jonas): Is used in a similar manner as with `embedded`, but the ResourceBrowser-component uses this variable
    // to ensure that some keyhandler are only done for the active modal, and not a potential parent ResBrowser-comp. 
    isModal?: boolean;
    selection?: {
        onClick(res: T): void;
        show?(res: T): boolean | string;
        text: string;
    }
}

interface FilterInput {
    type: "input",
    key: string;
    text: string;
    icon: IconName;
}

interface FilterWithOptions {
    type: "options";
    key: string;
    text: string;
    clearable: boolean;
    options: FilterOption[];
    icon: IconName;
}

interface FilterCheckbox {
    type: "checkbox";
    key: string;
    text: string;
    icon: IconName;
}

interface FilterOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    value: string;
}

interface MultiOptionFilter {
    type: "multi-option";
    keys: [string, string];
    text: string;
    clearable: boolean;
    options: MultiOption[];
    icon: IconName;
}

interface MultiOption {
    text: string;
    icon: IconName;
    color: ThemeColor;
    values: [string, string];
}

const SORT_BY = "sortBy";
const SORT_DIRECTION = "sortDirection";
const ASC = "ascending";
const DESC = "descending";

export function dateRangeFilters(text: string): MultiOptionFilter {
    const todayMs = getStartOfDay(new Date(timestampUnixMs())).getTime();
    const yesterdayEnd = todayMs - 1;
    const yesterdayStart = getStartOfDay(new Date(todayMs - 1)).getTime();
    const pastWeekEnd = new Date(timestampUnixMs()).getTime();
    const pastWeekStart = getStartOfDay(new Date(pastWeekEnd - (7 * 1000 * 60 * 60 * 24))).getTime();
    const pastMonthEnd = new Date(timestampUnixMs()).getTime();
    const pastMonthStart = getStartOfDay(new Date(pastMonthEnd - (30 * 1000 * 60 * 60 * 24))).getTime();

    return {
        text,
        type: "multi-option",
        clearable: true,
        icon: "calendar",
        keys: ["filterCreatedAfter", "filterCreatedBefore"],
        options: [{
            text: "Today",
            color: "textPrimary",
            icon: "calendar",
            values: [todayMs.toString(), ""]
        }, {
            text: "Yesterday",
            color: "textPrimary",
            icon: "calendar",
            values: [yesterdayStart.toString(), yesterdayEnd.toString()]
        }, {
            text: "Past week",
            color: "textPrimary",
            icon: "calendar",
            values: [pastWeekStart.toString(), pastMonthEnd.toString()]
        }, {
            text: "Past month",
            color: "textPrimary",
            icon: "calendar",
            values: [pastMonthStart.toString(), pastMonthEnd.toString()]
        }]
    }
}

export type OperationOrGroup<T, R> = Operation<T, R> | OperationGroup<T, R>;

export function isOperation<T, R>(op: OperationOrGroup<unknown, unknown>): op is Operation<T, R> {
    return !("operations" in op);
}

export interface OperationGroup<T, R> {
    icon: IconName;
    text: string;
    color: ThemeColor;
    shortcut?: ShortcutKey;
    backgroundColor?: ThemeColor,
    operations: Operation<T, R>[];
    iconRotation?: number;
}

export enum SelectionMode {
    CLEAR,
    SINGLE,
    TOGGLE_SINGLE,
    ADDITIVE_SINGLE,
    ADDITIVE_LIST,
}

export enum EmptyReasonTag {
    LOADING,
    EMPTY,
    NOT_FOUND_OR_NO_PERMISSIONS,
    UNABLE_TO_FULFILL,
}

export interface EmptyReason {
    tag: EmptyReasonTag;
    information?: string;
}

export interface RenderDimensions {
    width: number;
    height: number;
    x: number;
    y: number;
}

interface ResourceBrowserListenerMap<T> {
    "mount": () => void;
    "unmount": () => void;

    "rowSelectionUpdated": () => void;

    "beforeShortcut": (ev: KeyboardEvent) => void;
    "unhandledShortcut": (ev: KeyboardEvent) => void;

    "startRenderPage": () => void;
    "renderRow": (entry: T, row: ResourceBrowserRow, dimensions: RenderDimensions) => void;
    "endRenderPage": () => void;

    // beforeOpen is called pre-navigation/calling "open". If it returns `true`, calling open is skipped.
    "beforeOpen": (oldPath: string, path: string, resource?: T) => boolean;
    "open": (oldPath: string, path: string, resource?: T) => void;
    "wantToFetchNextPage": (path: string) => Promise<void>;
    "search": (query: string) => void;
    "searchHidden": () => void;

    "useFolder": () => void;
    // UNUSED?
    "useEntry": (entry: T) => void;

    "copy": (entries: T[], target: string) => void;
    "move": (entries: T[], target: string) => void;

    "starClicked": (entry: T) => void;

    "renderEmptyPage": (reason: EmptyReason) => void;

    "fetchOperations": () => OperationOrGroup<T, unknown>[];
    "fetchOperationsCallback": () => unknown | null;

    "validDropTarget": (entry: T) => boolean;
    "renderDropIndicator": (selectedEntries: T[], currentTarget: string | null) => void;

    "generateBreadcrumbs": (path: string) => ({title: string, absolutePath: string}[]) | "custom-rendering";
    "locationBarVisibilityUpdated": (visible: boolean) => void;

    "generateTabCompletionEntries": (prompt: string, shouldFetch: boolean, lastSelectedIndex: number) => string[] | Promise<void>;
    "renderLocationBar": (prompt: string) => {rendered: string, normalized: string};

    "pathToEntry": (entry: T) => string;
    "nameOfEntry": (entry: T) => string;

    "sort": (page: T[]) => void;
    "fetchFilters": () => Filter[];
}

interface ResourceBrowserRow {
    container: HTMLElement;
    star: HTMLElement;
    title: HTMLElement;
    stat1: HTMLElement;
    stat2: HTMLElement;
    stat3: HTMLElement;
}

export interface ResourceBrowseFeatures {
    dragToSelect?: boolean;
    supportsMove?: boolean;
    supportsCopy?: boolean;
    locationBar?: boolean;
    showHeaderInEmbedded?: boolean;
    showUseButtonOnRows?: boolean;
    showUseButtonOnDirectory?: boolean;
    showProjectSelector?: boolean;
    showStar?: boolean;
    renderSpinnerWhenLoading?: boolean;
    breadcrumbsSeparatedBySlashes?: boolean;
    search?: boolean;
    filters?: boolean;

    // Enables sorting. Only works if `showColumnTitles = true`.
    // In addition, you must also set `sortById` on the appropriate columns.
    sorting?: boolean;

    contextSwitcher?: boolean;
    showColumnTitles?: boolean;
}

export interface ColumnTitle {
    name: string;
    sortById?: string;
}

export type ColumnTitleList = [ColumnTitle, ColumnTitle, ColumnTitle, ColumnTitle];

export class ResourceBrowser<T> {
    // DOM component references
    /* private */ root: HTMLElement;
    private operations: HTMLElement;
    /* private */ filters: HTMLElement;
    /* private */ rightFilters: HTMLElement;
    sessionFilters: HTMLElement;
    public header: HTMLElement;
    public breadcrumbs: HTMLUListElement;
    private scrolling: HTMLDivElement;
    private entryDragIndicator: HTMLDivElement;
    /* private */ entryDragIndicatorContent: HTMLDivElement;
    private dragIndicator: HTMLDivElement;
    private rows: ResourceBrowserRow[] = [];

    // Selection state
    private isSelected = new Uint8Array(0);
    private lastSingleSelection = -1;
    private lastListSelectionEnd = -1;

    // Data fetching (mostly controlled by the caller)
    isFetchingNext: boolean = false;
    cachedData: Record<string, T[]> = {};
    cachedNext: Record<string, string | null> = {};
    emptyReasons: Record<string, EmptyReason> = {};
    private scrollPosition: Record<string, number> = {};
    currentPath: string;

    // Cached values and temporary state for DOM events
    scrollingContainerWidth: number;
    scrollingContainerHeight: number;
    scrollingContainerTop: number;
    scrollingContainerLeft: number;
    private processingShortcut: boolean = false;
    private ignoreScrollEvent = false;
    private ignoreRowClicksUntil: number = 0;

    // alt shortcuts
    private shortCuts: Record<ShortcutKey, () => void> = {} as Record<ShortcutKey, () => void>;
    //private altKeys = ["KeyQ", "KeyW", "KeyE", "KeyR", "KeyT"];
    //private altShortcuts: (() => void)[] = [doNothing, doNothing, doNothing, doNothing, doNothing];

    // Context menu
    private contextMenu: HTMLDivElement;
    private contextMenuHandlers: (() => void)[] = [];

    // Rename
    renameField: HTMLInputElement;
    private renameFieldIndex: number = -1;
    renameValue: string = "";
    renamePrefix: string = "";
    renameSuffix: string = "";
    private renameOnSubmit: () => void = doNothing;
    private renameOnCancel: () => void = doNothing;

    // Location bar
    locationBar: HTMLInputElement;
    private locationBarTabIndex: number = -1;
    private locationBarTabCount: number = 0;

    // Drag-and-drop
    private entryBelowCursorTemporary: T | string | null = null;
    private entryBelowCursor: T | string | null = null;

    icons: SvgCache = new SvgCache();
    private didPerformInitialOpen = false;

    // Filters
    resourceName: string; // currently just used for
    browseFilters: Record<string, string> = {};

    // Inline searching
    searchQuery: string = "";
    private searchQueryTimeout = -1;

    // Clipboard
    clipboard: T[] = [];
    clipboardIsCut: boolean = false;

    // Undo and redo
    undoStack: (() => void)[] = [];
    redoStack: (() => void)[] = [];

    // Empty pages
    defaultEmptyGraphic: DocumentFragment | null = null;
    spinner = new ReactStaticRenderer(() => <HexSpin size={60} />);
    emptyPageElement: {
        container: HTMLElement;
        graphic: HTMLElement;
        reason: HTMLElement;
        providerReason: HTMLElement;
    };

    // Feature set supported by this component.
    // Must be set before mount is invoked. If these are changed after mount() then the results are undefined.
    features: ResourceBrowseFeatures = {
        dragToSelect: true,
        supportsMove: false,
        supportsCopy: false,
        locationBar: false,
        showUseButtonOnRows: false,
        showUseButtonOnDirectory: false,
        showProjectSelector: false,
        showStar: false,
        search: true,
        renderSpinnerWhenLoading: true, // automatically inserts the spinner graphic before invoking "renderEmptyPage"
        filters: false,
        sorting: false,
        contextSwitcher: false,
        showColumnTitles: false,
    };

    public static isAnyModalOpen = false;
    private isModal: boolean;
    private allowEventListenerAction(): boolean {
        if (ResourceBrowser.isAnyModalOpen) return false;
        if (this.isModal) return false;
        if (this.opts.embedded) return false;
        return true;
    }


    private listeners: Record<string, any[]> = {};

    public opts: {
        embedded: boolean;
        selector: boolean;
        disabledKeyhandlers: boolean;
        columnTitles: ColumnTitleList;
    };
    // Note(Jonas): To use for project change listening.
    private initialPath: string | undefined = "";
    constructor(root: HTMLElement, resourceName: string, opts: ResourceBrowserOpts<T> | undefined) {
        this.root = root;
        this.resourceName = resourceName;
        this.isModal = !!opts?.isModal;
        ResourceBrowser.isAnyModalOpen = ResourceBrowser.isAnyModalOpen || this.isModal;
        this.opts = {
            embedded: !!opts?.embedded,
            selector: !!opts?.selection,
            disabledKeyhandlers: !!opts?.disabledKeyhandlers,
            columnTitles: [{name: ""}, {name: ""}, {name: ""}, {name: ""}]
        }
    };

    public init(
        ref: React.MutableRefObject<ResourceBrowser<T> | null>,
        features: ResourceBrowseFeatures,
        initialPath: string | undefined,
        onInit: (browser: ResourceBrowser<T>) => void,
    ) {
        ref.current = this;
        this.features = features;
        this.initialPath = initialPath;
        onInit(this);
        this.mount();
        if (initialPath !== undefined) this.open(initialPath);
    }

    mount() {
        // Mount primary UI and stylesheets
        ResourceBrowser.injectStyle();
        const browserClass = makeClassName("browser");
        this.root.classList.add(browserClass.class);
        this.root.innerHTML = `
            <header>
                <div class="header-first-row">
                    <div class="${FlexClass} location">
                        <ul></ul>
                        <input class="location-bar">
                    </div>
                    <div style="flex-grow: 1;"></div>
                    <input class="${InputClass} search-field" hidden>
                    <img alt="search" class="search-icon">
                    <img alt="refresh" class="refresh-icon">
                </div>
                <div class="operations"></div>
                <div style="display: flex; overflow-x: auto;">
                    <div class="filters"></div>
                    <div class="session-filters"></div>
                    <div class="right-sort-filters"></div>
                </div>
            </header>
            
            <div class="row rows-title">
                <div class="favorite" style="width: 20px;"></div>
                <div class="title"></div>
                <div class="stat-wrapper">
                    <div class="stat1"></div>
                    <div class="stat2"></div>
                    <div class="stat3"></div>
                </div>
            </div>
            <div style="overflow-y: auto; position: relative;">
                <div class="scrolling">
                    <input class="rename-field">
                </div>
            </div>
            
            <div class="file-drag-indicator-content"></div>
            <div class="file-drag-indicator"></div>
            <div class="drag-indicator"></div>
            <div class="context-menu"></div>
            
            <div class="page-empty">
                <div class="graphic"></div>
                <div class="reason"></div>
                <div class="provider-reason"></div>
            </div>
        `;

        this.operations = this.root.querySelector<HTMLElement>(".operations")!;
        this.dragIndicator = this.root.querySelector<HTMLDivElement>(".drag-indicator")!;
        this.entryDragIndicator = this.root.querySelector<HTMLDivElement>(".file-drag-indicator")!;
        this.entryDragIndicatorContent = this.root.querySelector<HTMLDivElement>(".file-drag-indicator-content")!;
        this.contextMenu = this.root.querySelector<HTMLDivElement>(".context-menu")!;
        this.scrolling = this.root.querySelector<HTMLDivElement>(".scrolling")!;
        this.renameField = this.root.querySelector<HTMLInputElement>(".rename-field")!;
        this.locationBar = this.root.querySelector<HTMLInputElement>(".location-bar")!;
        this.header = this.root.querySelector("header")!; // Add UtilityBar
        this.filters = this.root.querySelector<HTMLDivElement>(".filters")!;
        this.sessionFilters = this.root.querySelector<HTMLDivElement>(".session-filters")!;
        this.rightFilters = this.root.querySelector<HTMLDivElement>(".right-sort-filters")!;
        this.breadcrumbs = this.root.querySelector<HTMLUListElement>("header ul")!;
        this.emptyPageElement = {
            container: this.root.querySelector(".page-empty")!,
            graphic: this.root.querySelector(".page-empty .graphic")!,
            reason: this.root.querySelector(".page-empty .reason")!,
            providerReason: this.root.querySelector(".page-empty .provider-reason")!,
        };

        if (this.opts.embedded) {
            this.root.style.height = "auto";
            this.emptyPageElement.container.style.marginTop = "0px";
            if (this.features.showHeaderInEmbedded !== true) this.header.style.display = "none";
        }

        if (this.isModal) {
            this.root.style.maxHeight = `calc(${largeModalStyle.content?.maxHeight} - 64px)`;
            this.root.style.overflowY = "hidden";
            this.scrolling.style.overflowY = "auto";
        }

        const unmountInterval = window.setInterval(() => {
            if (!this.root.isConnected) {
                this.dispatchMessage("unmount", fn => fn());
                if (this.isModal) ResourceBrowser.isAnyModalOpen = false;
                removeThemeListener(this.resourceName);
                removeProjectListener(this.resourceName);
                window.clearInterval(unmountInterval);
            }
        }, 1000);

        this.breadcrumbs.setAttribute(
            "data-no-slashes",
            (this.features.breadcrumbsSeparatedBySlashes === false).toString()
        );

        if (this.features.locationBar) {
            this.header.setAttribute("has-location-bar", "true");
            const location = this.header.querySelector<HTMLDivElement>(".header-first-row > div.location")!;
            location.addEventListener("click", ev => {
                ev.stopPropagation();
                if (!this.isLocationBarVisible() && this.features.locationBar) {
                    this.setLocationBarVisibility(true);
                }
            });
            const listener = () => {
                if (!location.isConnected) {
                    document.body.removeEventListener("click", listener);
                }
                if (this.features.locationBar) { // We can toggle this value in the child component
                    this.setLocationBarVisibility(false);
                }
            };
            document.body.addEventListener("click", listener);
        }

        const searchIcon = this.header.querySelector<HTMLImageElement>(".header-first-row .search-icon")!;
        if (this.features.search) {
            searchIcon.setAttribute("data-shown", "");
            searchIcon.src = placeholderImage;
            searchIcon.style.display = "block";
            this.icons.renderIcon({name: "heroMagnifyingGlass", color: "primaryMain", color2: "primaryMain", width: 64, height: 64})
                .then(url => searchIcon.src = url);

            const input = this.header.querySelector<HTMLInputElement>(".header-first-row .search-field")!;
            input.placeholder = "Search...";
            input.onkeydown = e => e.stopPropagation();
            input.onkeyup = e => {
                if (e.key === "Enter") {
                    this.searchQuery = input.value;
                    this.dispatchMessage("search", fn => fn(this.searchQuery));
                }
            };

            searchIcon.onclick = () => {
                if (!input) return;
                input.toggleAttribute("hidden");
                if (input.hasAttribute("hidden")) {
                    this.dispatchMessage("searchHidden", fn => fn());
                } else {
                    input.focus()
                }
            }
        } else {
            searchIcon.style.display = "none";
        }

        if (this.features.filters) {
            // Note(Jonas): Expand height of header if filters/sort-directions are available.
            this.header.setAttribute("data-has-filters", "");
        }

        if (this.features.filters) {
            this.renderSessionFilters();
        }

        if (this.features.contextSwitcher) {
            const div = document.createElement("div");
            div.style.marginLeft = "20px";
            div.className = "context-switcher";
            const headerThing = this.header.querySelector<HTMLDivElement>(".header-first-row")!;
            headerThing.appendChild(div);
        }

        if (this.features.showColumnTitles) {
            const titleRow = this.root.querySelector(".row.rows-title")!;
            titleRow["style"].display = "flex";
            titleRow["style"].height = titleRow["style"].maxHeight = "28px";
            titleRow["style"].paddingBottom = "6px";
            if (!this.features.showStar) {
                const star = titleRow.querySelector<HTMLDivElement>(".favorite")!;
                star.remove();
            }
            this.setColumnTitles(this.opts.columnTitles);
        } else {
            const titleRow = this.root.querySelector(".row.rows-title")!;
            this.root.removeChild(titleRow);
        }

        {
            // Render refresh icon
            const icon = this.header.querySelector<HTMLImageElement>(".header-first-row .refresh-icon")!;
            icon.src = placeholderImage;
            icon.width = 24;
            icon.height = 24;
            this.icons.renderIcon({name: "heroArrowPath", color: "primaryMain", color2: "primaryMain", width: 64, height: 64})
                .then(url => icon.src = url);
            icon.addEventListener("click", () => {
                this.refresh();

                const evListener = () => {
                    icon.style.transition = "transform 0s";
                    icon.style.transform = "rotate(45deg)";
                    icon.removeEventListener("transitionend", evListener);
                    setTimeout(() => {
                        icon.style.removeProperty("transition");
                        icon.style.removeProperty("transform");
                    }, 30);
                };
                icon.addEventListener("transitionend", evListener);
                icon.style.transform = "rotate(405deg)";
            });
        }

        if (!this.opts.disabledKeyhandlers) {
            // Event handlers not related to rows
            this.renameField.addEventListener("keydown", ev => {
                if (this.allowEventListenerAction()) {
                    this.onRenameFieldKeydown(ev);
                }
            });

            this.renameField.addEventListener("beforeinput", ev => {
                this.onRenameFieldBeforeInput(ev);
            });

            this.renameField.addEventListener("input", ev => {
                this.onRenameFieldInput(ev);
            });

            this.scrolling.parentElement!.addEventListener("scroll", () => {
                this.onScroll();
            });

            this.scrolling.parentElement!.addEventListener("pointerdown", e => {
                this.onRowPointerDown(-1, e);
            });

            this.scrolling.parentElement!.addEventListener("contextmenu", e => {
                e.stopPropagation();
                e.preventDefault();
                this.onRowContextMenu(-1, e);
            });

            this.locationBar.addEventListener("keydown", ev => {
                if (this.allowEventListenerAction()) {
                    this.onLocationBarKeyDown(ev);
                }
            });

            this.locationBar.addEventListener("input", () => {
                this.onLocationBarKeyDown("input");
            });

            const keyDownListener = (ev: KeyboardEvent) => {
                if (!this.root.isConnected) {
                    document.removeEventListener("keydown", keyDownListener);
                    return;
                }

                if (this.allowEventListenerAction()) {
                    this.onKeyPress(ev);
                }
            };
            document.addEventListener("keydown", keyDownListener);
        }

        const clickHandler = ev => {
            if (!this.root.isConnected) {
                document.removeEventListener("click", clickHandler);
                return;
            }

            if (this.contextMenuHandlers.length) {
                this.closeContextMenu();
            }

            if (this.allowEventListenerAction()) {
                if (this.renameFieldIndex !== -1 && ev.target !== this.renameField) {
                    this.closeRenameField("submit");
                }
            }
        };
        document.addEventListener("click", clickHandler);

        const sizeListener = () => {
            if (!this.root.isConnected) {
                window.removeEventListener("resize", sizeListener);
                return;
            }

            const parent = this.scrolling.parentElement!;
            const rect = parent.getBoundingClientRect();
            this.scrollingContainerWidth = rect.width;
            this.scrollingContainerHeight = rect.height;
            this.scrollingContainerTop = rect.top;
            this.scrollingContainerLeft = rect.left;

            if (this.didPerformInitialOpen) {
                this.renderBreadcrumbs();
                this.renderOperations();
                this.renderRows();
            }
        };
        window.addEventListener("resize", sizeListener);
        sizeListener();

        // Mount rows and attach event handlers
        const rows: HTMLDivElement[] = [];
        for (let i = 0; i < ResourceBrowser.maxRows; i++) {
            const row = div(`
                <div class="favorite"></div>
                <div class="title"></div>
                <div class="stat-wrapper">
                    <div class="stat1"></div>
                    <div class="stat2"></div>
                    <div class="stat3"></div>
                </div>
            `);
            row.classList.add("row");
            const myIndex = i;
            row.addEventListener("pointerdown", e => {
                e.stopPropagation();
                this.onRowPointerDown(myIndex, e);
            });
            row.addEventListener("click", e => {
                this.onRowClicked(myIndex, e);
            });
            row.addEventListener("dblclick", () => {
                this.onRowDoubleClicked(myIndex);
            });
            row.addEventListener("mousemove", () => {
                this.onRowMouseMove(myIndex);
            });
            row.addEventListener("contextmenu", e => {
                e.stopPropagation();
                e.preventDefault();

                this.onRowContextMenu(myIndex, e);
            });
            rows.push(row);

            const r = {
                container: row,
                star: row.querySelector<HTMLElement>(".favorite")!,
                title: row.querySelector<HTMLElement>(".title")!,
                stat1: row.querySelector<HTMLElement>(".stat1")!,
                stat2: row.querySelector<HTMLElement>(".stat2")!,
                stat3: row.querySelector<HTMLElement>(".stat3")!,
            };

            r.star.addEventListener("pointerdown", e => {
                e.preventDefault();
                e.stopPropagation();
            });

            r.star.addEventListener("click", e => {
                e.preventDefault();
                e.stopPropagation();
                this.onStarClicked(myIndex);
            });

            this.rows.push(r);
        }

        this.scrolling.append(...rows);

        addThemeListener(this.resourceName, () => this.rerender());
        const path = this.initialPath;
        if (path !== undefined) {
            addProjectListener(this.resourceName, project => {
                this.canConsumeResources = checkCanConsumeResources(
                    project,
                    this.dispatchMessage("fetchOperationsCallback", fn => fn()) as unknown as null | {api: {isCoreResource: boolean}}
                );
                if (!this.canConsumeResources) {
                    this.renderCantConsumeResources();
                }

                this.open(path, true);
            })
        }
    }

    public canConsumeResources = true;

    refresh() {
        this.open(this.currentPath, true);
    }

    open(path: string, force: boolean = false, resource?: T) {
        // Note(Jonas): This happens before the child component gets a chance to access the resource and execute logic.
        // So how do we for instance ensure that the popIn can happen? Call a dispatchable function, provided that
        // the child component has provided one?

        this.didPerformInitialOpen = true;
        if (this.currentPath === path && !force) return;
        const oldPath = this.currentPath;

        // Set new state and notify event handlers
        path = resolvePath(path);
        this.currentPath = path;
        this.isFetchingNext = false;

        // Reset state
        this.isSelected = new Uint8Array(0);
        this.lastSingleSelection = -1;
        this.lastListSelectionEnd = -1;
        window.clearTimeout(this.searchQueryTimeout);
        this.searchQueryTimeout = -1;
        this.searchQuery = "";
        this.renameFieldIndex = -1; // NOTE(Dan): In this case we are _not_ running the onCancel function.
        this.renameValue = "";
        this.locationBarTabIndex = -1;
        this.locationBar.value = path + "/";
        this.locationBar.dispatchEvent(new Event("input"));
        this.entryBelowCursor = null;

        // NOTE(Dan): Need to get this now before we call renderPage(), since it will reset the scroll position.
        const scrollPositionElement = this.scrollPosition[path];

        // Perform renders
        this.rerender();

        // NOTE(Dan): We need to scroll to the position _after_ we have rendered the page.
        this.scrolling.parentElement!.scrollTo({top: scrollPositionElement ?? 0});
        // NOTE(Jonas): Navigating using mouse buttons or keyboard keys can otherwise leave the context menu open.
        this.closeContextMenu();
        this.dispatchMessage("open", fn => fn(oldPath, path, resource));
    }

    private renderCantConsumeResources() {
        this.prepareEmptyContainer();
        if (this.defaultEmptyGraphic) {
            this.emptyPageElement.graphic.append(this.defaultEmptyGraphic.cloneNode(true));
        }
        this.emptyPageElement.reason.innerHTML = `
        <p>
            <h3 style="text-align: center;">This project cannot consume resources</h3>
            This property is set for certain projects which are only meant for allocating resources. If you wish
            to consume any of these resources for testing purposes, then please allocate resources to a small
            separate test project. This can be done from the "Resource Allocations" menu in the project
            management interface.
        </p>
        `;
    }

    public rerender() {
        this.renderBreadcrumbs();
        this.renderOperations();
        this.renderRows();
        this.clearFilters();

        if (!this.canConsumeResources) {
            this.renderCantConsumeResources();
            return;
        }

        if (this.features.filters) {
            this.renderFilters();
            this.rerenderSessionFilterIcons();
        }
        this.renderColumnTitles();
    }

    private rerenderSessionFilterIcons() {
        const filters = this.dispatchMessage("fetchFilters", fn => fn()).filter(it => it.type === "input");
        this.sessionFilters.querySelectorAll<HTMLImageElement>("img").forEach((it, index) => {
            const filter = filters[index];
            if (!filter) return;
            this.icons.renderIcon({name: filter.icon, color: "textPrimary", color2: "textPrimary", height: 32, width: 32}).then(icon =>
                it.src = icon
            );
        })
    }

    public renderRows() {
        const page = this.cachedData[this.currentPath] ?? [];
        if (this.isSelected.length < page.length) {
            const newSelected = new Uint8Array(page.length);
            newSelected.set(this.isSelected, 0);
            this.isSelected = newSelected;
        } else if (this.isSelected.length > page.length) {
            this.isSelected = new Uint8Array(page.length);
        }

        const containerWidth = this.scrollingContainerWidth;
        const approximateSizeForTitle = containerWidth * (ResourceBrowser.rowTitleSizePercentage / 100);

        // Determine the total size of the page and figure out where we are
        const totalSize = ResourceBrowser.rowSize * page.length;
        const firstVisiblePixel = this.scrolling.parentElement!.scrollTop;
        this.scrollPosition[this.currentPath] = firstVisiblePixel;

        const firstRowInsideRegion = Math.ceil(firstVisiblePixel / ResourceBrowser.rowSize);
        const firstRowToRender = Math.max(0, firstRowInsideRegion - ResourceBrowser.extraRowsToPreRender);

        this.scrolling.style.height = `${totalSize}px`;

        const findRow = (idx: number): ResourceBrowserRow | null => {
            const rowNumber = idx - firstRowToRender;
            if (rowNumber < 0) return null;
            if (rowNumber >= ResourceBrowser.maxRows) return null;
            return this.rows[rowNumber];
        }


        // Reset rows and place them accordingly
        for (let i = 0; i < ResourceBrowser.maxRows; i++) {
            const row = this.rows[i];
            row.container.classList.add("hidden");
            row.container.removeAttribute("data-selected");
            row.container.removeAttribute("data-idx");
            row.container.style.position = "absolute";
            row.star.style.display = !this.features.showStar ? "none" : "block";
            const top = Math.min(totalSize - ResourceBrowser.rowSize, (firstRowToRender + i) * ResourceBrowser.rowSize);
            row.container.style.top = `${top}px`;
            row.title.innerHTML = "";
            row.stat1.innerHTML = "";
            row.stat2.innerHTML = "";
            row.stat3.innerHTML = "";
        }

        this.renameField.style.display = "none";

        if (!this.canConsumeResources) return;

        // Render the visible rows by iterating over all items
        this.dispatchMessage("startRenderPage", fn => fn());
        for (let i = 0; i < page.length; i++) {
            const entry = page[i];
            const row = findRow(i);
            if (!row) continue;

            const relativeX = 60;
            const relativeY = parseInt(row.container.style.top.replace("px", ""));

            if (i === this.renameFieldIndex) {
                this.renameField.style.display = "block";
                this.renameField.style.top = `${relativeY + ((ResourceBrowser.rowSize - 30) / 2)}px`;
                this.renameField.style.width = approximateSizeForTitle + "px";
                this.renameField.value = this.renameValue;
                this.renameField.focus();
            }

            row.container.setAttribute("data-idx", i.toString());
            row.container.setAttribute("data-selected", (this.isSelected[i] !== 0).toString());
            row.container.classList.remove("hidden");

            const x = this.scrollingContainerLeft + relativeX;
            const y = this.scrollingContainerTop + relativeY - firstVisiblePixel;
            this.dispatchMessage("renderRow", fn => fn(
                entry,
                row,
                {
                    width: containerWidth,
                    height: ResourceBrowser.rowSize,
                    x, y
                }
            ));
        }
        this.dispatchMessage("endRenderPage", fn => fn());

        if (page.length === 0) {
            const initialPage = this.currentPath;
            const initialReason = this.emptyReasons[this.currentPath] ?? {tag: EmptyReasonTag.LOADING};

            const renderEmptyPage = async () => {
                if (!this.canConsumeResources) return;
                if (this.currentPath !== initialPage) return;
                const page = this.cachedData[this.currentPath] ?? [];
                if (page.length !== 0) return;

                const reason = this.emptyReasons[this.currentPath] ?? {tag: EmptyReasonTag.LOADING};

                this.prepareEmptyContainer();
                const e = this.emptyPageElement;

                if (reason.tag === EmptyReasonTag.LOADING) {
                    if (this.features.renderSpinnerWhenLoading) {
                        e.graphic.append(this.spinner.clone());
                    }
                } else {
                    if (this.defaultEmptyGraphic) {
                        e.graphic.append(this.defaultEmptyGraphic.cloneNode(true));
                    }
                }

                this.dispatchMessage("renderEmptyPage", fn => fn(reason));
            };

            if (initialReason.tag === EmptyReasonTag.LOADING) {
                // Delay rendering of the loading page a bit until we are sure the loading operation is actually "slow"
                window.setTimeout(() => {
                    renderEmptyPage()
                }, 300);
            } else {
                renderEmptyPage();
            }
        } else {
            this.emptyPageElement.container.style.display = "none";
        }
    }

    static defaultTitleRenderer(title: string, dimensions: RenderDimensions, row: ResourceBrowserRow): HTMLDivElement {
        const div = createHTMLElements<HTMLDivElement>({
            tagType: "div",
            className: TruncateClass,
            style: {
                userSelect: "none",
                webkitUserSelect: "none" // This is deprecated, but this is the only one accepted in Safari.
            },
            children: [{
                tagType: "span",
                innerText: title
            }]
        });
        div.onclick = mockDoubleClick();
        return div;

        function mockDoubleClick() {
            const ALLOWED_CLICK_DELAY = 300;
            return (e: MouseEvent) => {
                const now = new Date().getTime();
                const idx = row.container.getAttribute("data-idx")!;
                var lastClick = ResourceBrowser.lastClickCache[idx];
                if (lastClick + ALLOWED_CLICK_DELAY > now) {
                    row.container.dispatchEvent(new Event("dblclick"))
                    e.stopPropagation();
                    lastClick = -1;
                } else {
                    lastClick = now;
                }
                ResourceBrowser.lastClickCache[idx] = lastClick;
            }
        }
    }

    static lastClickCache: Record<string, number> = {};

    static defaultIconRenderer(embedded: boolean): [HTMLDivElement, (url: string) => void] {
        // NOTE(Dan): We have to use a div with a background image, otherwise users will be able to drag the
        // image itself, which breaks the drag-and-drop functionality.
        const icon = createHTMLElements<HTMLDivElement>({
            tagType: "div", style: {
                width: embedded ? "20px" : "24px",
                height: embedded ? "20px" : "24px",
                backgroundSize: "contain",
                marginRight: "8px",
                display: "inline-block",
                backgroundPosition: "center",
            }
        });
        return [icon, (url) => icon.style.backgroundImage = `url(${url})`];
    }

    public defaultButtonRenderer<T>(selection: ResourceBrowserOpts<T>["selection"], item: T, opts?: {
        color?: ThemeColor, width?: string, height?: string
    }) {
        if (!selection) return;
        if (!selection.show || selection.show(item) === true) {
            const button = document.createElement("button");
            button.innerText = selection.text;
            button.className = ButtonClass;
            button.style.height = opts?.height ?? "32px";
            button.style.width = opts?.width ?? "96px";

            const color = opts?.color ?? "secondaryMain";
            button.style.setProperty("--bgColor", `var(--${color})`);
            button.style.setProperty("--hoverColor", `var(--${selectHoverColor(color)})`);
            button.style.color = `var(--${selectContrastColor(color)})`;

            button.onclick = e => {
                e.stopImmediatePropagation();
                selection?.onClick(item);
            }
            return button;
        }
        return null
    }

    defaultBreadcrumbs(): {title: string; absolutePath: string;}[] {
        return [{title: capitalized(this.resourceName), absolutePath: ""}];
    }

    static resetTitleComponent(element: HTMLElement) {
        element.style.display = "none";
    }

    placeTitleComponent(element: HTMLElement, dimensions: RenderDimensions, estimatedHeight?: number) {
        const height = estimatedHeight ?? 40;
        element.style.left = dimensions.x + "px";
        const absY = dimensions.y + (ResourceBrowser.rowSize - height) / 2;
        element.style.top = absY + "px";
        element.style.width = (dimensions.width * (ResourceBrowser.rowTitleSizePercentage / 100)) + "px";

        if (absY >= this.scrollingContainerTop &&
            absY <= this.scrollingContainerTop + this.scrollingContainerHeight) {
            element.style.display = "block";
        }
    }

    renderBreadcrumbs() {
        this.breadcrumbs.innerHTML = "";

        if (!this.canConsumeResources) return;

        const crumbs = this.dispatchMessage("generateBreadcrumbs", fn => fn(this.currentPath));
        if (crumbs === "custom-rendering") return;
        // NOTE(Dan): The next section computes which crumbs should be shown and what the content of them should be.
        // We start out by truncating all components down to a maximum length. This truncation takes place regardless
        // of how much screen estate we have. Following this, we determine a target size of our breadcrumbs based on
        // the size of our current container. Along with an estimated size of the font, we determine how many characters
        // in total we should target. In the end we will end up in one of the following states:
        //
        // 1. We will show all breadcrumbs
        // 2. We show only the first, immediate parent and last dir
        // 3. We only show the first and last dir
        //
        // We select this by choosing the first from the list which can stay within the target.
        const containerWidth = this.breadcrumbs.parentElement!.getBoundingClientRect().width;
        const approximateLengthPerCharacter = 22;
        const maxComponentLength = 30;
        const targetPathLength = (containerWidth / approximateLengthPerCharacter) - 5;

        let combinedLengthAfterTruncation = 0;
        let combinedLengthWithParent = 0;
        const truncatedComponents: string[] = [];
        for (let i = 0; i < crumbs.length; i++) {
            const component = crumbs[i];
            if (component.title.length > maxComponentLength) {
                truncatedComponents.push(component.title.substring(0, maxComponentLength - 3) + "...");
                combinedLengthAfterTruncation += maxComponentLength;
                if (i >= crumbs.length - 2) combinedLengthWithParent += maxComponentLength;
            } else {
                truncatedComponents.push(component.title);
                combinedLengthAfterTruncation += component.title.length;
                if (i >= crumbs.length - 2) combinedLengthWithParent += component.title.length;
            }
        }

        const canKeepMiddle = combinedLengthAfterTruncation <= targetPathLength;
        const canKeepParent = combinedLengthWithParent <= targetPathLength;

        const fragment = document.createDocumentFragment();
        let idx = 0;
        for (const component of crumbs) {
            const myPath = crumbs[idx].absolutePath;

            const canKeepThis =
                canKeepMiddle ||
                idx === 0 ||
                idx === crumbs.length - 1 ||
                (idx === crumbs.length - 2 && canKeepParent);

            if (idx === 1 && !canKeepMiddle && idx !== crumbs.length - 2) {
                const listItem = document.createElement("li");
                listItem.innerText = "...";
                fragment.append(listItem);
            }

            if (canKeepThis) {
                const listItem = document.createElement("li");
                listItem.innerText = visualizeWhitespaces(truncatedComponents[idx]);
                listItem.addEventListener("click", e => {
                    this.open(myPath);
                    stopPropagation(e);
                });
                listItem.addEventListener("mousemove", () => {
                    if (this.allowEventListenerAction()) {
                        this.entryBelowCursorTemporary = myPath;
                    }
                });

                fragment.append(listItem);
            }
            idx++;
        }

        this.breadcrumbs.append(fragment);
    }

    renderFilters() {
        const filters = this.dispatchMessage("fetchFilters", k => k());
        for (const f of filters) {
            switch (f.type) {
                case "checkbox": {
                    this.addCheckboxToFilter(f);
                    break;
                }
                case "options":
                case "multi-option": {
                    this.addOptionsToFilter(f);
                    break;
                }
            }
        }
    }

    renderSessionFilters() {
        const filters = this.dispatchMessage("fetchFilters", k => k());

        for (const f of filters) {
            if (f.type === "input") this.addInputToFilter(f);
        }
    }

    renderOperations() {
        this.renderOperationsIn(false);
    }

    private renderFiltersInContextMenu(filter: FilterWithOptions | MultiOptionFilter, x: number, y: number) {
        const renderOpIconAndText = (
            op: {text: string; icon: IconName; color: ThemeColor},
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
            }

            {
                // ...and the text
                let operationText = op.text;
                if (operationText) element.append(operationText);
                if (operationText && shortcut) {
                    const shortcutElem = document.createElement("kbd");
                    shortcutElem.append(shortcut);
                    element.append(shortcutElem);
                }
            }
        }

        const renderFilterInContextMenu = (
            options: FilterOption[] | MultiOption[],
            posX: number,
            posY: number
        ) => {
            var counter = 1;
            this.prepareContextMenu(posX, posY, options.length);
            const menu = this.contextMenu;
            const menuList = document.createElement("ul");
            menu.append(menuList);
            let shortcutNumber = counter;
            for (const child of options) {
                const item = document.createElement("li");
                renderOpIconAndText(child, item, shortcutNumber <= 9 ? `[${shortcutNumber}]` : undefined);

                const myIndex = shortcutNumber - 1;
                this.contextMenuHandlers.push(() => {
                    if (filter.type === "options") {
                        let c = child as FilterOption;
                        this.browseFilters[filter.key] = c.value;
                        if (c.value === CLEAR_FILTER_VALUE) {
                            clearFilterStorageValue(this.resourceName, filter.key);
                        } else {
                            setFilterStorageValue(this.resourceName, filter.key, c.value);
                        }
                    } else if (filter.type === "multi-option") {
                        let c = child as MultiOption;
                        const [keyOne, keyTwo] = filter.keys;
                        const [valueOne, valueTwo] = c.values;
                        this.browseFilters[keyOne] = valueOne;
                        if (valueTwo) this.browseFilters[keyTwo] = valueTwo;
                        else delete this.browseFilters[keyTwo];
                        if (valueOne === CLEAR_FILTER_VALUE) {
                            clearFilterStorageValue(this.resourceName, keyOne);
                            clearFilterStorageValue(this.resourceName, keyTwo);
                        } else {
                            setFilterStorageValue(this.resourceName, keyOne, valueOne);
                            if (valueTwo) setFilterStorageValue(this.resourceName, keyTwo, valueTwo);
                            else clearFilterStorageValue(this.resourceName, keyTwo);
                        }
                    }
                    this.open(this.currentPath, true);
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
        };

        if (filter.type === "options") {
            let filters = filter.options.slice();
            if (filter.clearable) {
                filters.unshift({
                    text: "Clear filter",
                    color: "errorMain",
                    icon: "close",
                    value: CLEAR_FILTER_VALUE
                });
            }
            renderFilterInContextMenu(filters, x, y);
        } else if (filter.type === "multi-option") {
            let filters = filter.options.slice();
            filters.unshift({
                text: "Clear filter",
                color: "errorMain",
                icon: "close",
                values: [CLEAR_FILTER_VALUE, ""]
            });
            renderFilterInContextMenu(filters, x, y);
        }
    }

    public setToContextMenuEntries(elements: HTMLElement[], handlers: (() => void)[]): void {
        const menu = this.contextMenu;
        const menuList = document.createElement("ul");
        menu.append(menuList);
        let shortcutNumber = 1;
        for (const element of elements) {
            const myIndex = shortcutNumber - 1;
            this.contextMenuHandlers.push(() => {
                const handler = handlers[myIndex];
                handler?.();
            });

            shortcutNumber++;

            element.addEventListener("mouseover", () => {
                this.findActiveContextMenuItem(true);
                this.selectContextMenuItem(myIndex);
            });

            element.addEventListener("click", ev => {
                ev.stopPropagation();
                const handler = handlers[myIndex];
                handler?.();
                this.selectContextMenuItem(myIndex);
            });

            menuList.append(element);
        }
    }

    private renderOperationsIn(useContextMenu: boolean, contextOpts?: {
        x: number,
        y: number,
    }) {
        const callbacks = this.dispatchMessage("fetchOperationsCallback", fn => fn());
        if (callbacks === null) return;

        const operations = this.dispatchMessage("fetchOperations", fn => fn());
        if (operations.length === 0 || !this.canConsumeResources) {
            this.operations.innerHTML = "";
            return;
        }

        if (!useContextMenu) {
            this.shortCuts = {} as Record<ShortcutKey, () => void>;

            printDuplicateShortcuts(operations);
        }

        const selected = this.findSelectedEntries();
        const page = this.cachedData[this.currentPath] ?? [];

        const renderOpIconAndText = (
            op: OperationOrGroup<unknown, unknown>,
            element: HTMLElement,
            shortcut?: string,
            inContextMenu?: boolean
        ) => {
            const isConfirmButton = isOperation(op) && op.confirm;
            // Set the icon
            const icon = image(placeholderImage, {height: 16, width: 16, alt: "Icon"});
            const contrastColor = selectContrastColor(
                op.color ??
                (isConfirmButton ?
                    "errorMain" :
                    inContextMenu ? "backgroundDefault" :
                        ("operations" in op ? "primaryMain" : "secondaryMain")
                )
            );

            this.icons.renderIcon({
                name: op.icon as IconName,
                color: contrastColor,
                color2: contrastColor,
                width: 64,
                height: 64,
            }).then(url => icon.src = url);
            if (op.iconRotation) {
                icon.style.transform = `rotate(${op.iconRotation}deg)`;
            }

            // ...and the text
            let operationText = typeof op.text === "string" ? op.text : op.text(selected, callbacks);

            if (isConfirmButton) {
                const opEnabled = op.enabled(selected, callbacks, page) === true;

                const button = ConfirmationButtonPlainHTML(
                    icon,
                    operationText,
                    () => {
                        op.onClick(selected, callbacks);
                        if (useContextMenu) this.closeContextMenu();
                    },
                    {asSquare: inContextMenu, color: op.color ?? "errorMain", hoverColor: op.color === "errorMain" ? "errorDark" : undefined, disabled: !opEnabled}
                );

                // HACK(Jonas): Very hacky way to solve styling for confirmation button in the two different contexts.
                if (inContextMenu) {
                    element.style.height = "40px";
                    button.style.height = "40px";
                    button.style.width = "100%";
                    button.querySelectorAll("button > div.ucloud-native-icons").forEach(it => {
                        it["style"].marginLeft = "-6px";
                        it["style"].marginTop = "-2px";
                    });
                    button.style.fontSize = "16px";
                    button.querySelectorAll("button > div.icons").forEach(it => {
                        it["style"].marginLeft = "-10px";
                    });
                    button.querySelector("button > ul")!["style"].marginLeft = "-60px";
                } else {
                    element.style.height = "35px";
                    button.style.height = "35px";

                    const textChildren = button.querySelectorAll("button > ul > li");
                    textChildren.item(0)["style"].marginTop = "8px";
                    textChildren.item(2)["style"].marginTop = "-10px";

                    button.querySelectorAll("button > div.ucloud-native-icons").forEach(it => {
                        it["style"].marginLeft = "-6px";
                        it["style"].marginTop = "-2px";
                    });
                    button.querySelectorAll("button > div.icons").forEach(it => {
                        it["style"].marginTop = "0px";
                        it["style"].marginLeft = "-8px";
                    });
                    button.querySelectorAll("button > ul").forEach(it => {
                        it["style"]["marginTop"] = "-8px";
                    });

                    button.querySelectorAll("button > ul > li").forEach(it => {
                        it["style"]["right"] = "unset";
                    });
                }

                element.style.padding = "0";

                element.append(button);
                element.setAttribute("data-is-confirm", "true");
                return;
            }

            {
                element.append(icon);

                if (!isOperation(op)) {
                    if (op.backgroundColor) {
                        element.style.setProperty("--bgColor", `var(--${op.backgroundColor})`);
                        element.style.setProperty("--hoverColor", `var(--${selectHoverColor(op.backgroundColor)})`);
                        element.style.color = `var(--${selectContrastColor(op.backgroundColor)})`;
                    }
                }
            }

            {
                if (operationText) {
                    element.append(operationText);
                }
                if (operationText && shortcut) {
                    const shortcutElem = document.createElement("kbd");
                    shortcutElem.append(shortcut);
                    element.append(shortcutElem);
                }
            }
        }

        const renderOperationsInContextMenu = (
            operations: OperationOrGroup<T, unknown>[],
            posX: number,
            posY: number,
            counter: number = 1,
            allowCreation: boolean = true
        ): number => {
            const menu = this.contextMenu;
            if (allowCreation) {
                let opCount = 0;
                for (const op of operations) {
                    if (!isOperation(op)) {
                        opCount += op.operations.length;
                    } else {
                        opCount++;
                    }
                }
                this.prepareContextMenu(posX, posY, opCount);
            }

            const menuList = allowCreation ? document.createElement("ul") : menu.querySelector("ul")!;
            if (allowCreation) menu.append(menuList);
            let shortcutNumber = counter;

            const useShortcuts = !this.opts?.embedded && !this.opts?.selector;
            for (const child of operations) {
                if (child["hackNotInTheContextMenu"]) continue;
                if (!isOperation(child)) {
                    counter += renderOperationsInContextMenu(child.operations, posX, posY, shortcutNumber, false);
                    shortcutNumber = counter + 1;
                    continue;
                }

                const text = child.enabled(selected, callbacks, page);
                const isDisabled = typeof text === "string";

                const item = document.createElement("li");
                const isConfirm = isOperation(child) && child.confirm;

                if (isDisabled) {
                    item.style.cursor = "not-allowed";
                    item.style.filter = "opacity(25%)";
                    const d = document.createElement("div");
                    d.innerText = text;
                    HTMLTooltip(item, d, {tooltipContentWidth: 450});
                }

                renderOpIconAndText(child, item, shortcutNumber <= 9 && useShortcuts && !isDisabled ? `[${shortcutNumber}]` : undefined, true);

                const myIndex = shortcutNumber - 1;
                this.contextMenuHandlers.push(() => {
                    if (isDisabled) {
                        // No action
                    } else if (isConfirm) {
                        // This case is handled inside the button.
                    } else {
                        child.onClick(selected, callbacks, page);
                    }
                });

                shortcutNumber++;

                item.addEventListener("mouseover", () => {
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                });

                item.addEventListener("click", ev => {
                    ev.stopPropagation();
                    this.findActiveContextMenuItem(true);
                    this.selectContextMenuItem(myIndex);
                    if (!isDisabled) {
                        this.onContextMenuItemSelection();
                    }
                });

                menuList.append(item);
            }
            return counter;
        };

        let opCount = 0;

        const renderOperation = (
            op: OperationOrGroup<T, unknown>
        ): HTMLElement => {
            const useShortcuts = !this.opts?.embedded && !this.opts?.selector;
            const element = document.createElement("div");
            element.classList.add("operation");
            const isConfirmButton = isOperation(op) && op.confirm;
            if (!isConfirmButton) {
                element.classList.add(ButtonClass);

                const color = op?.color ?? "secondaryMain";
                element.style.setProperty("--bgColor", `var(--${color})`);
                element.style.setProperty("--hoverColor", `var(--${selectHoverColor(color)})`);
                element.style.color = `var(--${selectContrastColor(color)})`;
            }
            element.classList.add(!useContextMenu ? "in-header" : "in-context-menu");

            const enabled = isOperation(op) ? op.enabled(selected, callbacks, page) : true;
            const handleDisabled = !useContextMenu && typeof enabled === "string";

            if (handleDisabled) {
                const d = document.createElement("div");
                d.innerText = enabled;
                if (isOperation(op) && !op.confirm) {
                    element.style.cursor = "not-allowed";
                    element.style.filter = "opacity(25%)";
                }
                HTMLTooltip(element, d, {tooltipContentWidth: 230});
            }

            renderOpIconAndText(op, element, useShortcuts && op.shortcut ? `[${ALT_KEY} + ${op.shortcut.replace("Key", "")}]` : undefined);

            {
                // ...and the handlers
                const handler = (ev?: Event) => {
                    ev?.stopPropagation();
                    if (!isOperation(op)) {
                        const elementBounding = element.getBoundingClientRect();
                        renderOperationsInContextMenu(
                            op.operations,
                            elementBounding.left,
                            (elementBounding.top + elementBounding.height),
                        );
                    } else if (isOperation(op) && enabled === true) {
                        op.onClick(selected, callbacks, page);
                    }
                };

                element.addEventListener("click", handler);
                if (!useContextMenu) {
                    if (op.shortcut) {
                        this.shortCuts[op.shortcut] = handler;
                    }
                    opCount++;
                }
            }

            return element;
        }

        if (!useContextMenu) {
            const target = this.operations;
            target.innerHTML = "";
            for (const op of operations) {
                target.append(renderOperation(op));
            }
        } else {
            const posX = contextOpts?.x ?? 0;
            const posY = contextOpts?.y ?? 0;
            renderOperationsInContextMenu(operations, posX, posY);
        }
    }

    clearFilters() {
        const filtersToKeep = this.dispatchMessage("fetchFilters", fn => fn())
            .filter(it => it.type === "input").map((it: FilterInput) => it.key);
        for (const key of Object.keys(this.browseFilters)) {
            if (!filtersToKeep.includes(key)) delete this.browseFilters[key];
        }
        this.filters.replaceChildren();
        this.rightFilters.replaceChildren();
    }

    select(virtualRowIndex: number, selectionMode: SelectionMode, render: boolean = true) {
        const selection = this.isSelected;
        if (virtualRowIndex < 0 || virtualRowIndex >= selection.length) return;

        switch (selectionMode) {
            case SelectionMode.CLEAR: {
                for (let i = 0; i < selection.length; i++) {
                    selection[i] = 0;
                }
                break;
            }

            case SelectionMode.SINGLE: {
                for (let i = 0; i < selection.length; i++) {
                    if (i === virtualRowIndex) selection[i] = 1;
                    else selection[i] = 0;
                }
                this.lastSingleSelection = virtualRowIndex;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.TOGGLE_SINGLE: {
                selection[virtualRowIndex] = selection[virtualRowIndex] !== 0 ? 0 : 1;
                this.lastSingleSelection = virtualRowIndex;
                this.lastListSelectionEnd = -1;
                break;
            }

            case SelectionMode.ADDITIVE_SINGLE: {
                selection[virtualRowIndex] = 1;
                this.lastSingleSelection = virtualRowIndex;
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

                const firstEntry = Math.min(virtualRowIndex, this.lastSingleSelection);
                const lastEntry = Math.max(virtualRowIndex, this.lastSingleSelection);
                for (let i = firstEntry; i <= lastEntry; i++) {
                    selection[i] = 1;
                }
                this.lastListSelectionEnd = virtualRowIndex;
                break;
            }
        }

        if (render) {
            this.renderRows();
            this.renderOperations();
        }

        this.dispatchMessage("rowSelectionUpdated", fn => fn());
    }

    findSelectedEntries(): T[] {
        const selectedEntries: T[] = [];
        const page = this.cachedData[this.currentPath];
        for (let i = 0; i < this.isSelected.length && i < page.length; i++) {
            if (this.isSelected[i] !== 0) selectedEntries.push(page[i]);
        }
        return selectedEntries;
    }

    findVirtualRowIndex(predicate: (arg: T) => boolean): number | null {
        let idx = 0;
        for (const entry of this.cachedData[this.currentPath] ?? []) {
            if (predicate(entry)) {
                return idx;
            }

            idx++;
        }
        return null;
    }

    ensureRowIsVisible(rowIdx: number, topAligned: boolean, ignoreEvent: boolean = false) {
        const scrollingContainer = this.scrolling.parentElement!;
        const height = this.scrollingContainerHeight;

        const firstRowPixel = rowIdx * ResourceBrowser.rowSize;
        const lastRowPixel = firstRowPixel + ResourceBrowser.rowSize;

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

    // Location bar utilities
    setLocationBarVisibility(visible: boolean) {
        if (visible) {
            this.header.classList.add("show-location-bar");
            this.locationBar.focus();
            this.locationBar.setSelectionRange(0, this.locationBar.value.length);
        } else {
            this.header.classList.remove("show-location-bar");
        }

        this.dispatchMessage("locationBarVisibilityUpdated", fn => fn(visible));
    }

    isLocationBarVisible(): boolean {
        return this.header.classList.contains("show-location-bar");
    }

    toggleLocationBar() {
        this.setLocationBarVisibility(!this.isLocationBarVisible());
    }

    registerPage(page: PageV2<T>, path: string, shouldClear: boolean) {
        const initialData: T[] = shouldClear ? [] : (this.cachedData[path] ?? []);
        this.cachedData[path] = initialData.concat(page.items);
        this.cachedNext[path] = page.next ?? null;
        if (this.cachedData[path].length === 0) {
            this.emptyReasons[path] = {tag: EmptyReasonTag.EMPTY};
        }

        if (page.next && this.cachedData[path].length < 10000 && !this.opts?.embedded) {
            this.dispatchMessage("wantToFetchNextPage", fn => fn(this.currentPath));
        }
    }

    // Page modification (outside normal loads)
    insertEntryIntoCurrentPage(item: T) {
        const page = this.cachedData[this.currentPath] ?? [];
        page.push(item);
        this.dispatchMessage("sort", fn => fn(page));
    }

    removeEntryFromCurrentPage(predicate: (entry: T) => boolean) {
        const page = this.cachedData[this.currentPath] ?? [];
        const idx = page.findIndex(predicate);
        if (idx !== -1) page.splice(idx, 1);
        this.dispatchMessage("sort", fn => fn(page));

        if (page.length === 0) {
            this.emptyReasons[this.currentPath] = {tag: EmptyReasonTag.EMPTY};
        }

        this.isSelected = new Uint8Array(0);
        this.renderOperations();
    }

    // Renaming and creation input fields
    showRenameField(
        predicate: (arg: T) => boolean,
        onSubmit: () => void,
        onCancel: () => void,
        initialValue: string,
    ) {
        const idx = this.findVirtualRowIndex(predicate);
        if (idx == null) return;

        this.closeRenameField("cancel", false);
        this.renameFieldIndex = idx;
        this.renameValue = initialValue;
        this.renameOnSubmit = onSubmit;
        this.renameOnCancel = onCancel;
        this.renderRows();

        const extensionStart = initialValue.lastIndexOf(".");
        const selectionEnd = extensionStart === -1 ? initialValue.length : extensionStart;
        this.renameField.setSelectionRange(0, selectionEnd);
    }

    closeRenameField(why: "submit" | "cancel", render: boolean = true) {
        if (this.renameFieldIndex !== -1) {
            if (why === "submit") this.renameOnSubmit();
            else this.renameOnCancel();
        }

        this.renameFieldIndex = -1;
        this.renameOnSubmit = doNothing;
        this.renameOnCancel = doNothing;
        if (render) this.renderRows();
    }

    // Context menu
    closeContextMenu() {
        this.contextMenuHandlers = [];
        this.contextMenu.style.removeProperty("transform");
        this.contextMenu.style.opacity = "0";
        window.setTimeout(() => {
            if (this.contextMenu.style.opacity === "0") {
                this.contextMenu.style.display = "none";
                this.contextMenu.style.opacity = "1";
            }
        }, 240);
    }

    findActiveContextMenuItem(clearActive: boolean = true): number {
        const listItems = this.contextMenu.querySelectorAll(".context-menu > ul > li");
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

    selectContextMenuItem(index: number) {
        const listItems = this.contextMenu.querySelectorAll(".context-menu > ul > li");
        if (index < 0 || index >= listItems.length) return;
        listItems.item(index).setAttribute("data-selected", "true");
    }

    // Row event handlers
    private onRowPointerDown(index: number, event: MouseEvent) {
        let shouldDragAndDrop = true;

        let rowRect: DOMRect;
        let entryIdx: number = NaN;

        const startX = event.clientX;
        const startY = event.clientY;

        const isBeyondDeadZone = (ev: MouseEvent) => {
            const dx = ev.clientX - startX;
            const dy = ev.clientY - startY;
            const distanceTravelled = Math.sqrt((dx * dx) + (dy * dy));
            return distanceTravelled >= 15;
        };

        const currentPathToTarget = (): string | null => {
            const e = this.entryBelowCursor;
            if (e === null) {
                return null;
            } else if (typeof e === "string") {
                return e;
            } else {
                return this.dispatchMessage("pathToEntry", fn => fn(e));
            }
        };

        const isDraggingAllowed = (selectedEntries: T[]): boolean => {
            const e = this.entryBelowCursor;
            if (e === null) return false;
            const pathToEntryBelowCursor = currentPathToTarget()!;

            const draggingToSelf = selectedEntries.some(it =>
                it === this.entryBelowCursor ||
                this.dispatchMessage("pathToEntry", fn => fn(it)) === pathToEntryBelowCursor
            );

            const belowCursor: T | null | string = this.entryBelowCursor;
            let draggingAllowed = belowCursor !== null && !draggingToSelf;
            if (draggingAllowed && typeof belowCursor !== "string" && belowCursor !== null) {
                draggingAllowed = this.dispatchMessage("validDropTarget", fn => fn(belowCursor));
            }

            return draggingAllowed;
        }

        if (index < 0 || !this.features.supportsMove) {
            shouldDragAndDrop = false;
        } else {
            const row = this.rows[index];
            const idxString = row.container.getAttribute("data-idx");
            entryIdx = idxString ? parseInt(idxString) : NaN;
            if (isNaN(entryIdx)) return;

            const range = document.createRange();
            if (row.title.lastChild) range.selectNode(row.title.lastChild);
            const textRect = range.getBoundingClientRect();
            rowRect = row.container.getBoundingClientRect();
            range.detach();

            // NOTE(Dan): We are purposefully only checking if x <= end of text.
            // NOTE(Dan): We are adding 30px to increase the hit-box slightly
            shouldDragAndDrop = event.clientX <= textRect.x + textRect.width + 30;
            // NOTE(Jonas): This is the case that ensure that the user drags the text and not the rest of the truncate-tag.
            shouldDragAndDrop = (event.target as HTMLElement).children.length === 0;
        }

        if (shouldDragAndDrop) {
            // Drag-and-drop functionality
            let didMount = false;
            let selectedEntries: T[] = [];
            let selectedIndices: number[] = [];

            // NOTE(Dan): Next we render the fileDragIndicator and display it. This will move around with the cursor.
            const moveHandler = (e: MouseEvent) => {
                if (!this.root.isConnected) return;

                if (!didMount) {
                    if (isBeyondDeadZone(e)) {
                        didMount = true;

                        const indicator = this.entryDragIndicator;
                        indicator.innerHTML = "";
                        indicator.style.transform = `translate(${rowRect.left + (rowRect.width / 2)}px, ${rowRect.top}px) scale3d(${rowRect.width}, 100%, 100%)`;
                        indicator.style.display = "block";

                        const page = this.cachedData[this.currentPath] ?? [];
                        let startEntry: T | null = null;
                        let startEntryIsSelected: boolean = false;
                        for (let i = 0; i < this.isSelected.length && i < page.length; i++) {
                            if (this.isSelected[i] !== 0) {
                                selectedEntries.push(page[i]);
                                selectedIndices.push(i);
                            }

                            if (i === entryIdx) {
                                startEntry = page[i];
                                startEntryIsSelected = this.isSelected[i] !== 0;
                            }
                        }

                        if (!startEntryIsSelected && startEntry != null) {
                            selectedEntries = [startEntry];
                            selectedIndices = [entryIdx];
                            this.select(entryIdx, SelectionMode.SINGLE);
                        }

                        const content = this.entryDragIndicatorContent;
                        content.innerHTML = "";
                        this.dispatchMessage("renderDropIndicator", fn => fn(selectedEntries, null));
                        content.style.display = "block";

                        if (selectedEntries.length > 0) {
                            for (const selected of selectedIndices) {
                                for (let i = 0; i < this.rows.length; i++) {
                                    const row = this.rows[i];
                                    const container = row.container;
                                    if (container.getAttribute("data-idx") === selected.toString()) {
                                        container.style.opacity = "50%";
                                        break;
                                    }
                                }
                            }
                        }

                        indicator.ontransitionend = () => {
                            indicator.style.transition = "none";
                        };

                        window.setTimeout(() => {
                            indicator.classList.add("animate");
                            indicator.style.transform = `translate(${rowRect.left + 200}px, ${rowRect.top}px) scale3d(400, 100%, 100%)`;
                        }, 0);
                    }
                } else {
                    const draggingAllowed = isDraggingAllowed(selectedEntries);

                    // NOTE(Dan): We use a data-attribute on the body such that we can set the cursor property to
                    // count for all elements. It is not enough to simply use !important on the body since that will
                    // still be overridden by most links.
                    if (!draggingAllowed) {
                        document.body.setAttribute("data-cursor", "not-allowed");
                    } else {
                        document.body.setAttribute("data-cursor", "grabbing");
                    }

                    const s = this.entryDragIndicator.style;
                    s.transform = `translate(${e.clientX + 200 + 10}px, ${e.clientY + 10}px) scale3d(400, 100%, 100%)`;

                    const s2 = this.entryDragIndicatorContent.style;
                    s2.left = (e.clientX + 10) + "px";
                    s2.top = (e.clientY + 10) + "px";

                    if (this.entryBelowCursorTemporary) {
                        this.entryBelowCursor = this.entryBelowCursorTemporary;
                        this.entryBelowCursorTemporary = null;
                    }

                    const content = this.entryDragIndicatorContent;
                    content.innerHTML = "";
                    this.dispatchMessage(
                        "renderDropIndicator",
                        fn => fn(selectedEntries, draggingAllowed ? currentPathToTarget() : null)
                    );
                }
            };

            const releaseHandler = () => {
                document.removeEventListener("mousemove", moveHandler);
                document.removeEventListener("pointerup", releaseHandler);
                if (!this.root.isConnected) return;
                if (!didMount) return;
                this.entryDragIndicator.classList.remove("animate");
                this.entryDragIndicator.style.removeProperty("transition");
                for (let i = 0; i < this.rows.length; i++) {
                    this.rows[i].container.style.removeProperty("opacity");
                }

                this.entryDragIndicator.style.display = "none";
                this.entryDragIndicatorContent.style.display = "none";

                document.body.removeAttribute("data-cursor");

                if (selectedEntries.length > 0 && isDraggingAllowed(selectedEntries)) {
                    const belowCursor = this.entryBelowCursor;
                    const pathToEntryBelowCursor = typeof belowCursor === "string" ? belowCursor :
                        this.dispatchMessage("pathToEntry", fn => fn(belowCursor!));

                    this.dispatchMessage("move", fn => fn(selectedEntries, pathToEntryBelowCursor));
                }

                this.ignoreRowClicksUntil = timestampUnixMs() + 15;
            };

            document.addEventListener("mousemove", moveHandler);
            document.addEventListener("pointerup", releaseHandler);
        } else {
            // Drag-to-select functionality
            if (!this.features.dragToSelect) return;

            const scrollingContainer = this.scrolling.parentElement!;
            const scrollingRectangle = scrollingContainer.getBoundingClientRect();
            let initialScrollTop = scrollingContainer.scrollTop;

            let didMount = false;
            document.body.setAttribute("data-no-select", "true");

            const dragMoveHandler = (e) => {
                if (!didMount && isBeyondDeadZone(e)) {
                    if (!isNaN(entryIdx)) {
                        this.select(entryIdx, SelectionMode.SINGLE);
                    } else {
                        this.select(0, SelectionMode.CLEAR);
                    }

                    didMount = true;
                }
                if (!didMount) return;
                const s = this.dragIndicator.style;
                s.display = "block";
                s.left = Math.min(e.clientX, startX) + "px";
                s.top = Math.min(e.clientY, startY) + "px";

                s.width = Math.abs(e.clientX - startX) + "px";
                s.height = Math.abs(e.clientY - startY) + "px";

                // NOTE(Dan): Figure out where the mouse is relative to rows.
                // TODO(Dan): Should we extract this to a function?
                let scrollOffset = 0;
                let rowOffset = 0;
                const scrollStart = scrollingContainer.scrollTop;
                for (let i = 0; i < this.rows.length; i++) {
                    const row = this.rows[i];
                    const top = parseInt(row.container.style.top.replace("px", ""));
                    if (top >= scrollStart) {
                        scrollOffset = top - scrollStart;
                        rowOffset = i;
                        break;
                    }
                }

                const lowerY = Math.min(e.clientY, startY);
                const upperY = Math.max(e.clientY, startY);

                const lowerOffset = Math.max(
                    0,
                    Math.floor((lowerY - scrollingRectangle.top - scrollOffset) / ResourceBrowser.rowSize) + rowOffset
                );
                const upperOffset = Math.min(
                    this.rows.length,
                    Math.floor((upperY - scrollingRectangle.top - scrollOffset) / ResourceBrowser.rowSize) + rowOffset
                );
                const baseFileIdx = parseInt(this.rows[0].container.getAttribute("data-idx")!);

                for (let i = 0; i < this.rows.length; i++) {
                    if (i >= lowerOffset && i <= upperOffset) {
                        this.isSelected[i + baseFileIdx] = 1;
                    } else {
                        // NOTE(Dan): When scrolling, then we should never turn anything off if scrolling has begun
                        // TODO(Dan): Maybe we should just move the scrolling region along with the scrolling?
                        if (initialScrollTop === scrollStart) this.isSelected[i + baseFileIdx] = 0;
                    }
                }
                this.renderRows();
                this.renderOperations();
            };

            const dragReleaseHandler = ev => {
                document.body.removeAttribute("data-no-select");
                this.dragIndicator.style.display = "none";
                document.removeEventListener("mousemove", dragMoveHandler);
                document.removeEventListener("pointerup", dragReleaseHandler);
                if (!ev.target) return;

                if (ev.target === this.dragIndicator) {
                    // NOTE(Dan): If the mouse haven't moved much, then we should just treat it as a selection. We have to
                    // this here since the normal click handler won't be invoked since it goes to the drag indicator
                    // instead.
                    if (!isBeyondDeadZone(ev)) this.onRowClicked(index, ev);
                }
            };

            document.addEventListener("mousemove", dragMoveHandler);
            document.addEventListener("pointerup", dragReleaseHandler);
        }
    }

    private onRowClicked(index: number, event: MouseEvent) {
        if (timestampUnixMs() < this.ignoreRowClicksUntil) return;
        if (index < 0 || index >= this.rows.length) return;
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        let mode = SelectionMode.SINGLE;
        if (event.ctrlKey || event.metaKey) mode = SelectionMode.TOGGLE_SINGLE;
        if (event.shiftKey) mode = SelectionMode.ADDITIVE_LIST;
        this.select(entryIdx, mode);
    }

    private onRowDoubleClicked(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        const pathToEntry = this.dispatchMessage("pathToEntry", fn => fn(page[entryIdx]));
        if (this.dispatchMessage("beforeOpen", fn => fn(pathToEntry, "", page[entryIdx]))) return;
        this.open(pathToEntry, false, page[entryIdx]);
    }

    private onRowMouseMove(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        this.entryBelowCursor = page[entryIdx];
    }

    private onRowContextMenu(index: number, event: MouseEvent) {
        if (index >= 0) {
            const row = this.rows[index];
            const entryIdxS = row.container.getAttribute("data-idx");
            const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
            if (entryIdx == null || isNaN(entryIdx)) return;

            if (this.isSelected[entryIdx] === 0) {
                // If this file isn't part of the selected set, then make it selected as the only item
                this.select(entryIdx, SelectionMode.SINGLE);
            }
        } else {
            // If we are not right-clicking on a specific file, then clear the entire selection.
            this.select(0, SelectionMode.CLEAR);
            this.renderRows();
        }

        if (this.contextMenuHandlers.length) this.closeContextMenu();
        this.renderOperationsIn(true, {x: event.clientX, y: event.clientY});
    }

    private onStarClicked(index: number) {
        const row = this.rows[index];
        const entryIdxS = row.container.getAttribute("data-idx");
        const entryIdx = entryIdxS ? parseInt(entryIdxS) : undefined;
        if (entryIdx == null || isNaN(entryIdx)) return;

        const page = this.cachedData[this.currentPath] ?? [];
        this.dispatchMessage("starClicked", fn => fn(page[entryIdx]));
    }

    // Row container event handlers
    private onScroll() {
        if (this.ignoreScrollEvent) {
            this.ignoreScrollEvent = false;
            return;
        }

        this.renderRows();
        this.fetchNext();
    }

    private async fetchNext() {
        const initialPath = this.currentPath;
        if (this.isFetchingNext || this.cachedNext[initialPath] === null) return;

        const scrollingContainer = this.scrolling.parentElement!;
        const scrollingPos = scrollingContainer.scrollTop;
        const scrollingHeight = scrollingContainer.scrollHeight;
        if (scrollingPos < scrollingHeight * 0.8) return;

        this.isFetchingNext = true;
        this.dispatchMessage("wantToFetchNextPage", fn => fn(this.currentPath))
            .finally(() => {
                if (initialPath === this.currentPath) this.isFetchingNext = false;
                this.renderRows();
            });
    }

    // Generic keyboard shortcut event handler
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
            const listItems = ul.querySelectorAll(":scope > li");

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

            // Note(Jonas): Skip disabled and confirm-button entries. Ensure that both directions are valid.
            const order = delta > 0 ? 1 : -1;
            while (listItems.item(selectedIndex)?.getAttribute("disabled") === "true"
                || listItems.item(selectedIndex)?.getAttribute("data-is-confirm") === "true") {
                selectedIndex += order;
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

        if (!this.contextMenuHandlers.length) {
            this.dispatchMessage("beforeShortcut", fn => fn(ev));
            if (ev.defaultPrevented) return;
        }

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
                    if (ev.shiftKey || ev.altKey) {
                        didHandle = false;
                    } else if (!this.features.supportsMove || !this.features.supportsCopy) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;

                        const newClipboard = this.findSelectedEntries();
                        this.clipboard = newClipboard;
                        this.clipboardIsCut = ev.code === "KeyX";
                        if (newClipboard.length) {
                            const key = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌘" : "Ctrl + ";
                            snackbarStore.addInformation(
                                `${newClipboard.length} copied to clipboard. Use ${key}V to insert.`,
                                false
                            );
                        }
                    }
                    break;
                }

                case "KeyV": {
                    if (!this.features.supportsMove || !this.features.supportsCopy) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;
                        if (this.clipboard.length) {
                            this.dispatchMessage(
                                this.clipboardIsCut ? "move" : "copy",
                                fn => fn(this.clipboard, this.currentPath)
                            );

                            if (this.clipboardIsCut) this.clipboard = [];
                        }
                    }
                    break;
                }

                case "KeyG": {
                    if (!this.features.locationBar) {
                        didHandle = false;
                    } else {
                        if (this.contextMenuHandlers.length) return;
                        this.toggleLocationBar();
                    }
                    break;
                }

                case "KeyZ": {
                    if (this.contextMenuHandlers.length) return;
                    const fn = this.undoStack.shift();
                    if (fn !== undefined) fn();
                    break;
                }

                case "KeyY": {
                    if (this.contextMenuHandlers.length) return;
                    const fn = this.redoStack.shift();
                    if (fn !== undefined) fn();
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
            } else {
                this.dispatchMessage("unhandledShortcut", fn => fn(ev));
            }
        } else if (ev.altKey) {
            if (this.shortCuts[ev.code]) {
                ev.preventDefault();
                ev.stopPropagation();
                this.shortCuts[ev.code]();
            } else {
                this.dispatchMessage("unhandledShortcut", fn => fn(ev));
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
                        this.renderRows();
                        this.renderOperations();
                        this.searchQuery = "";
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
                    const crumbs = this.dispatchMessage("generateBreadcrumbs", fn => fn(this.currentPath));
                    if (crumbs === "custom-rendering") return;
                    const parent = crumbs[crumbs.length - 2];
                    this.open(parent.absolutePath);
                    break;
                }

                case "Enter": {
                    if (this.contextMenuHandlers.length) {
                        this.onContextMenuItemSelection();
                    } else {
                        const selected = this.isSelected;
                        for (let i = 0; i < selected.length; i++) {
                            if (selected[i] !== 0) {
                                const entry = this.cachedData[this.currentPath][i];
                                const path = this.dispatchMessage("pathToEntry", fn => fn(entry));
                                if (!this.dispatchMessage("beforeOpen", fn => fn(this.currentPath, path, entry))) {
                                    this.open(path, false, entry);
                                }

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
                            if (!isNaN(selectedItem) && selectedItem !== 0) {
                                this.onContextMenuItemSelection(selectedItem - 1);
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
                                const name = this.dispatchMessage("nameOfEntry", fn => fn(files[i])).toLowerCase();
                                if (name.indexOf(this.searchQuery) === 0) {
                                    this.ensureRowIsVisible(i, true, true);
                                    this.select(i, SelectionMode.SINGLE);
                                    break;
                                }
                            }
                        } else {
                            this.dispatchMessage("unhandledShortcut", fn => fn(ev));
                        }
                    }
                    break;
                }
            }
        }
    }

    // Location bar event handlers
    private onLocationBarKeyDown(ev: "input" | KeyboardEvent) {
        if (ev !== "input") ev.stopPropagation();

        const attrRealPath = "data-real-path";

        const setValue = (path: string): string | null => {
            const {rendered, normalized} = this.dispatchMessage("renderLocationBar", fn => fn(path));
            this.locationBar.setAttribute(attrRealPath, normalized);
            this.locationBar.value = rendered;
            return path;
        };

        const readValue = (): string | null => {
            if (!this.locationBar.hasAttribute(attrRealPath)) return setValue(this.locationBar.value);
            return this.locationBar.getAttribute(attrRealPath);
        };

        const doTabComplete = (allowFetch: boolean = true) => {
            const path = readValue();
            if (path === null) return;

            if (this.locationBarTabIndex === -1) this.locationBarTabIndex = path.length;

            const pathPrefix = path.substring(0, this.locationBarTabIndex);

            const entries = this.dispatchMessage(
                "generateTabCompletionEntries",
                fn => fn(pathPrefix, allowFetch, this.locationBarTabCount)
            );

            if ("then" in entries) {
                return entries.then(() => {
                    if (readValue() !== path) return;
                    return doTabComplete(false);
                });
            }

            if (entries.length === 0) return;

            const itemToUse = this.locationBarTabCount % entries.length;
            let newValue = entries[itemToUse];
            if (!newValue.startsWith("/")) newValue = "/" + newValue;
            newValue += "/";
            setValue(newValue);

            this.locationBarTabCount = (itemToUse + 1) % entries.length;
            if (entries.length === 1) {
                this.locationBarTabIndex = readValue()?.length ?? 0;
                this.locationBarTabCount = 0;
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
                    if (newPath && newPath !== "/search") {
                        this.setLocationBarVisibility(false);
                        this.open(newPath);
                    }
                    break;
                }

                case "Escape": {
                    this.setLocationBarVisibility(false);
                    setValue(this.currentPath);
                    break;
                }
            }
        }
    }

    // Context menu event handlers
    private onContextMenuItemSelection(fixedIdx?: number) {
        if (!this.contextMenuHandlers.length) return;
        const idx = fixedIdx ?? this.findActiveContextMenuItem(false);
        if (idx < 0 || idx >= this.contextMenuHandlers.length) return;
        this.contextMenuHandlers[idx]();
        this.closeContextMenu();
    }

    triggerOperation(predicate: (op: Operation<T, unknown>) => boolean): boolean {
        const callbacks = this.dispatchMessage("fetchOperationsCallback", fn => fn());
        if (callbacks === null) return false;
        const ops = this.dispatchMessage("fetchOperations", fn => fn());
        for (const op of ops) {
            let toCheck: Operation<T, unknown>[] = [];
            if (!isOperation(op)) {
                toCheck = op.operations;
            } else {
                toCheck = [op];
            }

            for (const child of toCheck) {
                if (predicate(child)) {
                    child.onClick(this.findSelectedEntries(), callbacks, this.cachedData[this.currentPath] ?? []);
                    return true;
                }
            }
        }

        return false;
    }

    // Rename field event handlers
    private onRenameFieldKeydown(ev: KeyboardEvent) {
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
    }

    private onRenameFieldBeforeInput(ev: InputEvent) {
        if (this.processingShortcut) {
            ev.preventDefault();
            ev.stopPropagation();
        }
    }

    private onRenameFieldInput(ev: Event) {
        ev.preventDefault();
        ev.stopPropagation();
        if (this.processingShortcut) return;
        this.renameValue = this.renameField.value;
    }

    // Minor utilities
    createSpinner(size: number): Node {
        const fragment = this.spinner.clone();
        const parent = fragment.querySelector('[data-tag="loading-spinner"]')! as HTMLElement;
        parent.style.width = `${size}px`;
        parent.style.height = `${size}px`;

        const spinner = fragment.querySelector("svg")!;
        spinner.setAttribute("width", size.toString());
        spinner.setAttribute("height", size.toString());
        return fragment;
    }

    // NOTE(Dan): selectAndShow() requires that the matching row has been rendered at least once.
    // If it has not been rendered, then we won't be able to allocate the physical row, which we need in order to
    // scroll to it.
    selectAndShow(predicate: (arg: T) => boolean) {
        const idx = this.findVirtualRowIndex(predicate);
        if (idx !== null) {
            this.ensureRowIsVisible(idx, true, true);
            this.select(idx, SelectionMode.SINGLE);
        }
    }

    // Message passing required to control the component
    on<K extends keyof ResourceBrowserListenerMap<T>>(
        type: K,
        listener: ResourceBrowserListenerMap<T>[K],
    ) {
        let arr = this.listeners[type] ?? [];
        this.listeners[type] = arr;
        arr.push(listener);
    }

    private defaultHandlers: Partial<ResourceBrowserListenerMap<T>> = {
        open: doNothing,
        beforeOpen: () => false,
        rowSelectionUpdated: doNothing,
        mount: doNothing,
        unmount: doNothing,
        locationBarVisibilityUpdated: doNothing,
        sort: doNothing,
        startRenderPage: doNothing,
        endRenderPage: doNothing,
        beforeShortcut: doNothing,
        fetchFilters: () => [],
        searchHidden: () => {},

        renderLocationBar: prompt => {
            return {rendered: prompt, normalized: prompt};
        },

        nameOfEntry: entry => {
            const path = this.dispatchMessage("pathToEntry", fn => fn(entry));
            return fileName(path);
        },
    }

    dispatchMessage<K extends keyof ResourceBrowserListenerMap<T>>(
        type: K,
        invoker: (fn: ResourceBrowserListenerMap<T>[K]) => ReturnType<ResourceBrowserListenerMap<T>[K]>
    ): ReturnType<ResourceBrowserListenerMap<T>[K]> {
        let arr = this.listeners[type];
        if (arr === undefined) {
            const defaultHandler = this.defaultHandlers[type];
            if (defaultHandler === undefined) throw "Missing listener for " + type;
            arr = [defaultHandler];
        }

        let result: any;
        for (const l of arr) {
            result = invoker(l);
        }
        return result;
    }

    public prepareContextMenu(posX: number, posY: number, entryCount: number) {
        let actualPosX = posX;
        let actualPosY = posY;
        const menu = this.contextMenu;
        menu.innerHTML = "";
        const itemSize = 40;
        const listHeight = entryCount * itemSize;
        const listWidth = 400;

        const rootWidth = window.innerWidth;
        const rootHeight = window.innerHeight;

        if (posX + listWidth >= rootWidth - 32) {
            actualPosX = rootWidth - listWidth - 32;
        }

        if (posY + listHeight >= rootHeight - 32) {
            actualPosY = rootHeight - listHeight - 32;
        }

        menu.style.transform = `translate(0, -${listHeight / 2}px) scale3d(1, 0.1, 1)`;
        window.setTimeout(() => menu.style.transform = "scale3d(1, 1, 1)", 0);
        menu.style.display = "block";
        menu.style.opacity = "1";

        menu.style.top = actualPosY + "px";
        menu.style.left = actualPosX + "px";
    }

    static rowTitleSizePercentage = 56;
    static rowSize = 55;
    static extraRowsToPreRender = 6;
    static maxRows = (Math.max(1080, window.screen.height) / ResourceBrowser.rowSize) + ResourceBrowser.extraRowsToPreRender;

    static styleInjected = false;
    static injectStyle() {
        if (ResourceBrowser.styleInjected) return;
        ResourceBrowser.styleInjected = true;
        const browserClass = makeClassName("browser");
        unstyledInjectStyle("ignored", () => `
            body[data-cursor=not-allowed] * {
                cursor: not-allowed !important;
            }

            body[data-cursor=grabbing] * {
                cursor: grabbing !important;
            }
            
            body[data-no-select=true] * {
                user-select: none;
            }

            ${browserClass.dot} .drag-indicator {
                position: fixed;
                z-index: 10000;
                background-color: var(--primaryMain);
                opacity: 30%;
                border: 2px solid var(--primaryDark);
                display: none;
                top: 0;
                left: 0;
            }

            ${browserClass.dot} .file-drag-indicator-content,
            ${browserClass.dot} .file-drag-indicator {
                position: fixed;
                z-index: 10000;
                display: none;
                top: 0;
                left: 0;
                height: ${this.rowSize}px;
                user-select: none;
            }
            
            ${browserClass.dot} .file-drag-indicator-content {
                z-index: 10001;
                width: 400px;
                margin: 16px;
                white-space: pre;
            }

            ${browserClass.dot} header[data-has-filters] .filters, 
            ${browserClass.dot} header[data-has-filters] .session-filters, 
            ${browserClass.dot} header[data-has-filters] .right-sort-filters {
                display: flex;
                margin-top: 12px;
            }

            ${browserClass.dot} .file-drag-indicator-content img {
                margin-right: 8px;
            }

            ${browserClass.dot} .file-drag-indicator {
                transition: transform 0.06s;
                background: var(--rowActive);
                color: var(--textPrimary);
                width: 1px;
                overflow: hidden;
            }
         
            ${browserClass.dot} .file-drag-indicator.animate {
            }

            ${browserClass.dot} {
                width: 100%;
                height: calc(100vh - 32px);
                display: flex;
                flex-direction: column;
                font-size: 16px;
            }

            ${browserClass.dot} header .header-first-row {
                display: flex;
                align-items: center;
                margin-bottom: 8px;
            }

            ${browserClass.dot} header .header-first-row img {
                cursor: pointer;
                flex-shrink: 0;
                margin-left: 16px;
            }

            ${browserClass.dot} header .header-first-row .location-bar {
                flex-grow: 1;
            }

            .header-first-row .search-icon[data-shown] {
                width: 24px;
                height: 24px;
            }

            ${browserClass.dot} header ul {
                padding: 0;
                margin: 0;
                display: flex;
                flex-direction: row;
                gap: 8px;
                height: 35px;
                white-space: pre;
                align-items: center;
            }

            ${browserClass.dot} > div {
                flex-grow: 1;
            }

            ${browserClass.dot} header {
                width: 100%;
                height: 92px;
                flex-shrink: 0;
                overflow: hidden;
            }
            
            ${browserClass.dot} header[data-has-filters] {
                height: 136px;
            }

            ${browserClass.dot} header .location-bar,
            ${browserClass.dot} header.show-location-bar ul {
                display: none;
            }

            ${browserClass.dot} header.show-location-bar .location-bar,
            ${browserClass.dot} header ul {
                display: flex;
            }

            ${browserClass.dot} .location-bar {
                width: 100%;
                font-size: 120%;
                height: 35px;
            }
            
            ${browserClass.dot} header[has-location-bar] .location:hover {
                border: 1px solid var(--borderColorHover);
            }
            
            ${browserClass.dot} header[has-location-bar] .location li:hover {
                user-select: none;
            }
            
            ${browserClass.dot} header[has-location-bar] .location li:hover {
                cursor: pointer;
                text-decoration: underline;
            }
            
            ${browserClass.dot} header[has-location-bar] .location {
                flex-grow: 1;
                border: 1px solid var(--borderColor);
                margin-left: -6px;
                border-radius: 5px;
                width: 100%;
                cursor: text;
                height: 35px;
            }
            
            ${browserClass.dot} header[has-location-bar] .location input {
                outline: none;
                border: 0;
                height: 32px;
                margin-top: 1px;
                margin-left: 5px;
                background: transparent;
                color: var(--textPrimary);
            }
            
            ${browserClass.dot} header > div > div > ul {
                margin-left: 6px;
            }
            
            ${browserClass.dot} header > div > div > ul[data-no-slashes="true"] li::before {
                display: inline-block;
                content: unset;
                margin: 0;
            }

            ${browserClass.dot} header > div > div > ul li::before {
                display: inline-block;
                content: '/';
                margin-right: 8px;
                text-decoration: none !important;
            }

            ${browserClass.dot} header div ul li {
                list-style: none;
                margin: 0;
                padding: 0;
                cursor: pointer;
                font-size: 120%;
            }

            ${browserClass.dot} .row {
                display: flex;
                flex-direction: row;
                container-type: inline-size;
                height: ${ResourceBrowser.rowSize}px;
                width: 100%;
                align-items: center;
                border-bottom: 1px solid var(--borderColor);
                gap: 8px;
                user-select: none;
                padding: 0 8px;
                transition: filter 0.3s;
            }
            
            ${browserClass.dot} .rows-title {
                max-height: 0;
                color: var(--textPrimary);
                display: none;
            }
            
            body[data-cursor=grabbing] ${browserClass.dot} .row:hover {
                filter: hue-rotate(10deg) saturate(500%);
            }

            ${browserClass.dot} .row.hidden {
                display: none;
            }

            ${browserClass.dot} .row[data-selected="true"] {
                /* NOTE(Dan): We only have an active state, as a result we just use the hover variable. As the active 
                   variable is intended for differentiation between the two. This is consistent with how it is used in
                   the Tree component */
                background: var(--rowHover); 
            }

            ${browserClass.dot} .row .title {
                display: flex;
                align-items: center;
                width: 85%;
                white-space: pre;


                @container (max-width: 600px) {
                    max-width: 45%;
                }

                @container (min-width: 600px) {
                    width: ${ResourceBrowser.rowTitleSizePercentage}%;
                }
            }
            
            ${browserClass.dot} .stat-wrapper {
                flex-grow: 1;
                flex-shrink: 1;
                display: flex;
                gap: 8px;
            }

            ${browserClass.dot} .row .stat2,
            ${browserClass.dot} .row .stat3  {
                display: none;
                width: 0;
            }

            @media screen and (min-width: 860px) {
                ${browserClass.dot} .row .stat1,
                ${browserClass.dot} .row .stat2,
                ${browserClass.dot} .row .stat3 {
                    display: flex;
                    justify-content: end;
                    text-align: end;
                    width: 33%;
                }
            }

            @media screen and (max-width: 860px) {
                ${browserClass.dot} .row .stat1 {
                    margin-left: auto;
                }
            }

 
            ${browserClass.dot} .sensitivity-badge {
                height: 2em;
                width: 2em;
                display: flex;
                align-items: center;
                justify-content: center;
                border: 0.2em solid var(--borderColor);
                border-radius: 100%;
            }

            ${browserClass.dot} .sensitivity-badge.PRIVATE {
                --badgeColor: var(--borderColor);
            }

            ${browserClass.dot} .sensitivity-badge.CONFIDENTIAL {
                --badgeColor: var(--warningMain);
            }

            ${browserClass.dot} .sensitivity-badge.SENSITIVE {
                --badgeColor: var(--errorMain);
            }

            ${browserClass.dot} .operation {
                cursor: pointer;
                display: flex;
                align-items: center;
                gap: 8px;
            }
            
            ${browserClass.dot} .operations {
                display: flex;
                flex-direction: row;
                gap: 8px;
            }

            ${browserClass.dot} .context-menu {
                position: fixed;
                z-index: 10000;
                top: 0;
                left: 0;
                border-radius: 8px;
                border: 1px solid #E2DDDD;
                cursor: pointer;
                background: var(--backgroundDefault);
                box-shadow: 0 3px 6px rgba(0, 0, 0, 30%);
                width: 400px;
                display: none;
                max-height: calc(40px * 8.5);
                overflow-y: auto;
                transition: opacity 120ms, transform 60ms;
            }

            ${browserClass.dot} .context-menu ul {
                padding: 0;
                margin: 0;
                display: flex;
                flex-direction: column;
            }

            ${browserClass.dot} .context-menu li {
                margin: 0;
                padding: 8px 8px;
                list-style: none;
                display: flex;
                align-items: center;
                gap: 8px;
            }
            
            ${browserClass.dot} kbd {
                font-family: var(--sansSerif);
            }

            ${browserClass.dot} .context-menu li kbd {
                flex-grow: 1;
                text-align: end;
            }

            ${browserClass.dot} .context-menu li[data-selected=true] {
                background: var(--rowHover);
            }

            ${browserClass.dot} .context-menu > ul > *:first-child,
            ${browserClass.dot} .context-menu > ul > li:first-child > button {
                border-top-left-radius: 8px;
                border-top-right-radius: 8px;
            }
            
            ${browserClass.dot} .context-menu > ul > *:last-child,
             ${browserClass.dot} .context-menu > ul > li:last-child,
             ${browserClass.dot} .context-menu > ul > li:last-child > button {
                border-bottom-left-radius: 8px;
                border-bottom-right-radius: 8px;
            }
            
            ${browserClass.dot} .rename-field {
                display: none;
                position: absolute;
                background-color: var(--backgroundDefault);
                border-radius: 5px;
                border: 1px solid var(--borderColor);
                outline: 0;
                color: var(--textPrimary);
                z-index: 1;
                top: 0;
                left: 12px;
            }

            ${browserClass.dot} .page-empty {
                display: none;
                position: fixed;
                top: 0;
                left: 0;
                height: 200px;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                gap: 16px;
                text-align: center;
            }
            
            ${browserClass.dot} .page-empty .graphic {
                background: var(--primaryMain);
                min-height: 100px;
                min-width: 100px;
                border-radius: 100px;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            
            ${browserClass.dot} .page-empty .provider-reason {
                font-style: italic;
            }

            ${browserClass.dot} div > div.right-sort-filters {
                margin-left: auto;
            }
            
            ${browserClass.dot} .refresh-icon {
                transition: transform 0.5s;
            }
            
            ${browserClass.dot} .refresh-icon:hover {
                transform: rotate(45deg);
            }
        `);
    }

    private addCheckboxToFilter(filter: FilterCheckbox) {
        const wrapper = document.createElement("label");
        wrapper.style.cursor = "pointer";
        wrapper.style.marginRight = "24px";

        const icon = this.createFilterImg(filter.icon);
        icon.style.marginTop = "0";
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const node = document.createTextNode(filter.text);
        wrapper.appendChild(node);

        const check = document.createElement("input");
        check.style.marginLeft = "5px";
        check.style.cursor = "pointer";
        check.type = "checkbox";
        check.checked = false;

        const valueFromStorage = getFilterStorageValue(this.resourceName, filter.key);
        if (valueFromStorage === "true") {
            this.browseFilters[filter.key] = "true";
            check.checked = true;
        }

        wrapper.appendChild(check);
        this.filters.appendChild(wrapper);
        wrapper.onclick = e => {
            e.stopImmediatePropagation();
            e.preventDefault();
            check.checked = !check.checked;
            if (this.browseFilters[filter.key]) {
                setFilterStorageValue(this.resourceName, filter.key, "false");
                delete this.browseFilters[filter.key];
            } else {
                setFilterStorageValue(this.resourceName, filter.key, "true");
                this.browseFilters[filter.key] = "true";
            }
            this.open(this.currentPath, true);
        }
    }

    private addOptionsToFilter(filter: FilterWithOptions | MultiOptionFilter) {
        const wrapper = createHTMLElements({
            tagType: "div", style: {
                display: "flex",
                cursor: "pointer",
                marginRight: "24px",
                userSelect: "none"
            }
        });

        const valueFromStorage = getFilterStorageValue(this.resourceName, filter.type === "options" ? filter.key : filter.keys[0]);
        let iconName: IconName = filter.icon;
        if (valueFromStorage !== null) {
            if (filter.type === "options") {
                iconName = filter.options.find(it => it.value === valueFromStorage)?.icon ?? filter.icon;
            } else if (filter.type === "multi-option") {
                const secondValue = getFilterStorageValue(this.resourceName, filter.keys[1]);
                iconName = filter.options.find(it => it.values[0] === valueFromStorage && (!it.values[1] || it.values[1] === secondValue))?.icon ?? filter.icon;
            }
        }
        const icon = this.createFilterImg(iconName);
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const text = createHTMLElements({
            tagType: "span",
            style: {marginRight: "5px"},
            innerText: filter.text
        });

        if (valueFromStorage != null) {
            if (filter.type === "options") {
                const option = filter.options.find(it => it.value === valueFromStorage);
                if (option) {
                    text.innerText = option.text;
                    this.browseFilters[filter.key] = option.value;
                }
            } else if (filter.type === "multi-option") {
                const secondValue = getFilterStorageValue(this.resourceName, filter.keys[1]);
                const option = filter.options.find(it => it.values[0] === valueFromStorage && (!it.values[1] || it.values[1] === secondValue));
                if (option) {
                    text.innerText = option.text;
                    this.browseFilters[filter.keys[0]] = option.values[0];
                    if (secondValue) this.browseFilters[filter.keys[1]] = option.values[1];
                    else delete this.browseFilters[filter.keys[1]];
                }
            }
        }

        wrapper.appendChild(text);
        const chevronIcon = this.createFilterImg("chevronDownLight");
        wrapper.appendChild(chevronIcon);
        if (["Sort by", "Sort order"].includes(filter.text)) {
            this.rightFilters.appendChild(wrapper);
        } else {
            this.filters.appendChild(wrapper);
        }

        wrapper.onclick = e => {
            const wrapperRect = wrapper.getBoundingClientRect();
            e.stopImmediatePropagation();
            this.renderFiltersInContextMenu(
                filter,
                wrapperRect.x,
                wrapperRect.y + wrapperRect.height,
            );
        };
    }


    private addInputToFilter(filter: FilterInput) {
        const wrapper = document.createElement("div");
        wrapper.style.display = "flex";
        wrapper.style.cursor = "pointer";
        wrapper.style.marginRight = "24px";
        wrapper.style.userSelect = "none";

        const icon = this.createFilterImg(filter.icon)
        icon.style.marginRight = "8px";
        wrapper.appendChild(icon);

        const text = document.createElement("span");
        text.style.marginRight = "5px";
        /* Hacky solution to keep width */
        text.style.width = filter.text.length + 2 + "ch";
        text.innerText = filter.text;
        wrapper.appendChild(text);

        const input = document.createElement("input");
        input.type = "text";
        input.className = InputClass;
        input.placeholder = "Search by...";
        input.style.display = "none";
        input.style.marginTop = "-4px";
        input.style.height = "32px";
        input.onclick = e => e.stopImmediatePropagation();
        input.onkeydown = e => {
            e.stopPropagation();
        };
        input.onkeyup = () => {
            if (input.value) {
                this.browseFilters[filter.key] = input.value;
            } else {
                delete this.browseFilters[filter.key];
            }
            this.open(this.currentPath, true);
        }
        wrapper.append(input);

        this.sessionFilters.append(wrapper);
        wrapper.onclick = e => {
            e.stopImmediatePropagation();
            input.value = "";
            if (input.style.display === "none") {
                input.style.display = "unset";
            } else input.style.display = "none";
        }
    }

    private prepareEmptyContainer() {
        const containerTop = this.scrollingContainerTop;
        const containerLeft = this.scrollingContainerLeft;
        const containerHeight = this.scrollingContainerHeight;
        const containerWidth = this.scrollingContainerWidth;
        const e = this.emptyPageElement;
        e.container.style.display = "flex";
        e.graphic.innerHTML = "";
        e.reason.innerHTML = "";
        e.providerReason.innerHTML = "";
        const containerSize = 400;
        e.container.style.width = containerSize + "px";
        e.container.style.height = containerSize + "px";
        e.container.style.left = (containerLeft + (containerWidth / 2) - (containerSize / 2)) + "px";
        e.container.style.top = (containerTop + (containerHeight / 2) - (containerSize / 2)) + "px";
        if (this.opts?.embedded || this.opts?.selector) {
            e.container.style.position = "unset";
            e.container.style.marginLeft = "auto";
            e.container.style.marginRight = "auto";
        }
    }

    private createFilterImg(icon: IconName): HTMLImageElement {
        const c = document.createElement("img");
        c.width = 12;
        c.height = 12;
        c.style.marginTop = "7px";
        this.icons.renderIcon({color: "textPrimary", color2: "textPrimary", height: 32, width: 32, name: icon}).then(it => c.src = it);
        return c;
    }

    public setEmptyIcon(icon: IconName) {
        this.icons.renderIcon({
            name: icon,
            color: "primaryContrast",
            color2: "primaryContrast",
            height: 256,
            width: 256
        }).then(icon => {
            const fragment = document.createDocumentFragment();
            fragment.append(image(icon, {height: 60, width: 60}));
            this.defaultEmptyGraphic = fragment;
            this.rerender();
        });
    }

    public setColumnTitles(titles: ColumnTitleList) {
        this.opts.columnTitles = titles;
        this.renderColumnTitles();
    }

    public renderColumnTitles() {
        const titles = this.opts.columnTitles;
        for (const title of titles) {
            if (title.sortById) {
                const value = getFilterStorageValue(this.resourceName, SORT_BY);
                if (value) this.browseFilters["sortBy"] = value;
            }
        }
        if (this.features.sorting) {
            const value = getFilterStorageValue(this.resourceName, SORT_DIRECTION);
            if (value) this.browseFilters[SORT_DIRECTION] = value;
        }
        const titleRow = this.root.querySelector(".row.rows-title");
        if (!titleRow) return;
        this.setTitleAndHandlers(titleRow.querySelector(".title")!, titles[0], "right");
        this.setTitleAndHandlers(titleRow.querySelector(".stat1")!, titles[1], "left");
        this.setTitleAndHandlers(titleRow.querySelector(".stat2")!, titles[2], "left");
        // If this is a selector, the third row will show the use button.
        if (!this.opts.selector) this.setTitleAndHandlers(titleRow.querySelector(".stat3")!, titles[3], "left");
    }

    public defaultEmptyPage(resourceName: string, reason: EmptyReason, additionalFilters: Record<string, string> | undefined) {
        const e = this.emptyPageElement;
        switch (reason.tag) {
            case EmptyReasonTag.LOADING: {
                e.reason.append(`We are fetching your ${resourceName}...`);
                break;
            }

            case EmptyReasonTag.EMPTY: {
                if (Object.values({...this.browseFilters, ...(additionalFilters ?? {})}).length !== 0)
                    e.reason.append(`No ${resourceName} found with active filters.`)
                else e.reason.append(`This workspace has no ${resourceName} yet.`);
                break;
            }

            case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                e.reason.append(`We could not find any data related to your ${resourceName}.`);
                e.providerReason.append(reason.information ?? "");
                break;
            }

            case EmptyReasonTag.UNABLE_TO_FULFILL: {
                e.reason.append(`We are currently unable to show your ${resourceName}. Try again later.`);
                e.providerReason.append(reason.information ?? "");
                break;
            }
        }
    }

    private setTitleAndHandlers(el: HTMLElement, rowTitle: ColumnTitle, position: "left" | "right"): void {
        el.innerHTML = "";
        const wrapper = document.createElement("div");
        el.append(wrapper);
        const rowTitleName = document.createElement("span");
        rowTitleName.innerText = rowTitle.name;
        wrapper.append(rowTitleName);
        const filter = rowTitle.sortById;
        if (!filter) return;
        wrapper.style.cursor = "pointer";
        wrapper.onclick = e => {
            e.stopPropagation();
            if (this.browseFilters[SORT_BY] !== filter) {
                setFilterStorageValue(this.resourceName, SORT_BY, filter);
            } else if (this.features.sorting) {
                if (this.browseFilters[SORT_DIRECTION] === ASC) {
                    setFilterStorageValue(this.resourceName, SORT_DIRECTION, DESC);
                } else {
                    setFilterStorageValue(this.resourceName, SORT_DIRECTION, ASC);
                }
            }
            this.open(this.currentPath, true);
        }
        if (this.browseFilters["sortBy"] === filter) {
            wrapper.style.fontWeight = "bold";
            const [arrow, setArrow] = ResourceBrowser.defaultIconRenderer(true);
            this.icons.renderIcon({
                name: this.browseFilters[SORT_DIRECTION] === DESC ? "heroArrowDown" : "heroArrowUp",
                color: "textPrimary",
                color2: "textPrimary",
                height: 24,
                width: 24,
            }).then(setArrow);
            arrow.style.height = arrow.style.width = "12px";
            arrow.style.marginLeft = "8px";
            if (position === "left") {
                arrow.style.marginTop = "6px";
                wrapper.prepend(arrow);
            } else {
                wrapper.appendChild(arrow);
            }
        } else {
            wrapper.style.fontWeight = "unset";
        }
    }
}

export function getFilterStorageValue(namespace: string, key: string): string | null {
    return localStorage.getItem(`${namespace}:${key}`);
}

export function setFilterStorageValue(namespace: string, key: string, value: string) {
    localStorage.setItem(`${namespace}:${key}`, value);
}

export function clearFilterStorageValue(namespace: string, key: string) {
    localStorage.removeItem(`${namespace}:${key}`);
}

export function addContextSwitcherInPortal<T>(
    browserRef: React.RefObject<ResourceBrowser<T>>, setPortal: (el: JSX.Element) => void,
    managed?: {
        setLocalProject: (project: string | undefined) => void
    }
) {
    const browser = browserRef.current;
    if (browser != null) {
        const contextSwitcher = browser.header.querySelector<HTMLDivElement>(".context-switcher");
        if (contextSwitcher) {
            setPortal(createPortal(<ContextSwitcher managed={managed} />, contextSwitcher));
        }
    }
}

export function resourceCreationWithProductSelector<T>(
    browser: ResourceBrowser<T>,
    products: ProductV2[],
    dummyEntry: T,
    onCreate: (product: ProductV2) => void,
    type: ProductType,
    // Note(Jonas): Used in the event that the product contains info that the browser component needs.
    // See PublicLinks usage for an example of its usage.
    onSelect?: (product: ProductV2) => void,
): {startCreation: () => void, cancelCreation: () => void, portal: React.ReactPortal} {
    const productSelector = document.createElement("div");
    productSelector.style.display = "none";
    productSelector.style.position = "fixed";
    document.body.append(productSelector);
    const Component: React.FunctionComponent = () => {
        return <ProductSelector
            products={products}
            selected={null}
            onSelect={onProductSelected}
            slim
            type={type}
        />;
    };

    let selectedProduct: ProductV2 | null = null;

    const portal = createPortal(<Component />, productSelector);

    browser.on("startRenderPage", () => {
        ResourceBrowser.resetTitleComponent(productSelector);
    });

    browser.on("renderRow", (entry, row, dims) => {
        if (entry !== dummyEntry) return;
        if (selectedProduct !== null) return;
        dims.x -= 52;

        browser.placeTitleComponent(productSelector, dims);
    });

    const isSelectingProduct = () => {
        return (browser.cachedData[browser.currentPath] ?? []).some(it => it === dummyEntry);
    }

    browser.on("beforeShortcut", ev => {
        if (ev.code === "Escape" && isSelectingProduct()) {
            ev.preventDefault();

            browser.removeEntryFromCurrentPage(it => it === dummyEntry);
            browser.renderRows();
        }
    });

    const startCreation = () => {
        if (isSelectingProduct()) return;
        selectedProduct = null;
        browser.insertEntryIntoCurrentPage(dummyEntry);
        browser.renderRows();
    };

    const cancelCreation = () => {
        browser.removeEntryFromCurrentPage(it => it === dummyEntry);
        browser.renderRows();
    };

    const onProductSelected = (product: ProductV2) => {
        onSelect?.(product);
        if (["STORAGE", "INGRESS"].includes(type)) {
            selectedProduct = product;
            browser.showRenameField(
                it => it === dummyEntry,
                () => {
                    browser.removeEntryFromCurrentPage(it => it === dummyEntry);
                    onCreate(product);
                },
                () => {
                    browser.removeEntryFromCurrentPage(it => it === dummyEntry);
                },
                ""
            );
        } else if (["LICENSE", "NETWORK_IP"].includes(type)) {
            browser.removeEntryFromCurrentPage(it => it === dummyEntry);
            onCreate(product);
        } else if (type === "COMPUTE") {
            // Not handled.
        }
    };

    const onOutsideClick = () => {
        if (selectedProduct === null && isSelectingProduct()) {
            cancelCreation();
        }
    };

    document.body.addEventListener("click", onOutsideClick);

    browser.on("unmount", () => {
        document.body.removeEventListener("click", onOutsideClick);
    });


    return {startCreation, cancelCreation, portal};
}

export function providerIcon(providerId: string, opts?: Partial<CSSStyleDeclaration>): HTMLElement {
    const myInfo = ProviderInfo.providers.find(p => p.id === providerId);
    const outer = div("");
    outer.className = "provider-icon"
    outer.style.background = "var(--primaryMain)";
    outer.style.borderRadius = "8px";
    outer.style.width = outer.style.minWidth = opts?.width ?? "30px";
    outer.style.height = outer.style.minHeight = opts?.height ?? "30px";

    const inner = div("");
    inner.style.backgroundSize = "contain";
    inner.style.width = "100%";
    inner.style.height = "100%";
    inner.style.fontSize = opts?.fontSize ?? "14px";
    inner.style.color = "white"
    if (myInfo) {
        outer.style.padding = "5px";
        inner.style.backgroundImage = `url('/Images/${myInfo.logo}')`;
        inner.style.backgroundPosition = "center";
    } else {
        inner.style.textAlign = "center";
        inner.append((providerId[0] ?? "-").toUpperCase());
    }

    outer.append(inner);
    return outer;
}

export function checkIsWorkspaceAdmin(): boolean {
    if (!Client.hasActiveProject) return true; // My Workspace.
    const projects = projectCache.retrieveFromCacheOnly("");
    if (!projects) return false;
    const project = projects.items.find(it => it.id === Client.projectId);
    if (!project) return false;
    return isAdminOrPI(project.status.myRole);
}

export function checkCanConsumeResources(projectId: string | null, callbacks: null | {api: {isCoreResource: boolean}}): boolean {
    if (!projectId) return true;
    if (!callbacks) return true;

    const {api} = callbacks;
    if (!api) return true;

    if (api.isCoreResource) return true;

    const project = projectCache.retrieveFromCacheOnly("")?.items.find(it => it.id === projectId);
    // Don't consider yet-to-be-fetched projects as non-consumer
    if (!project) return true;

    return project.specification.canConsumeResources !== false;
}

// https://stackoverflow.com/a/13139830
export const placeholderImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

function printDuplicateShortcuts<T>(operations: OperationOrGroup<T, unknown>[]) {
    if (!inDevEnvironment()) return;

    const shortCuts = operations.map(it => {
        if (isOperation(it)) {
            return ({shortcut: it.shortcut, text: it.text.toString()})
        } else {
            return [{shortcut: it.shortcut, text: it.text.toString()}, it.operations.map(it => ({shortcut: it.shortcut, text: it.text.toString()}))]
        }
    }).flat(5);
    const entries: Record<string, string> = {};
    for (const short of shortCuts) {
        if (entries[short.shortcut ?? ""]) {
            console.log(`Shortcut: ${short.shortcut} already reserved for ${entries[short.shortcut ?? ""]}, attempted to use for ${short.text}`);
        }
        entries[short.shortcut ?? ""] = short.text;
    }
}

const ARROW_UP = "↑";
const ARROW_DOWN = "↓";
const ALT_KEY = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌥" : "alt";
const CTRL_KEY = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌘" : "ctrl";
const ShortcutClass = injectStyle("shortcut", k => `
    ${k} {
        border-radius: 5px;
        border: 1px solid var(--textPrimary);
        font-size: 12px;
        min-width: 20px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 0 5px;
    }
`);

interface ShortcutProps {
    name: string;
    ctrl?: boolean;
    alt?: boolean;
    shift?: boolean;
    keys: string | string[];
}

const Shortcut: React.FunctionComponent<ShortcutProps> = props => {
    const normalizedKeys = typeof props.keys === "string" ? [props.keys] : props.keys;
    return <tr>
        <td>{props.name}</td>
        <td>
            <Flex gap={"4px"} justifyContent={"end"}>
                {normalizedKeys.map(((k, i) =>
                    <React.Fragment key={i}>
                        {i > 0 && <> or </>}
                        {props.ctrl && <div className={ShortcutClass}>{CTRL_KEY}</div>}
                        {props.alt && <div className={ShortcutClass}>{ALT_KEY}</div>}
                        {props.shift && <div className={ShortcutClass}>Shift</div>}
                        <div className={ShortcutClass}>{k}</div>
                    </React.Fragment>
                ))}
            </Flex>
        </td>
    </tr>;
}

const ControlsClass = injectStyle("controls", k => `
    ${k} table {
        margin: 16px 0;
        width: 100%;
    }
    
    ${k} td:first-child, ${k} th:first-child {
        border-left: 1px solid var(--borderColor);
    }
    
    ${k} td:last-child, ${k} th:last-child {
        border-right: 1px solid var(--borderColor);
    }
    
    ${k} td, ${k} th {
        padding: 8px;
        border-top: 1px solid var(--borderColor);
        border-bottom: 1px solid var(--borderColor);
    }
    
    ${k} td:last-child {
        text-align: right;
    }
`);

interface ControlDescription {
    name: string;
    shortcut?: Partial<ShortcutProps>;
    description?: string;
}

function ControlsDialog({features, custom}: {features: ResourceBrowseFeatures, custom?: ControlDescription[]}) {
    return <div className={ControlsClass}>
        <Heading.h4>Controls and keyboard shortcuts</Heading.h4>
        <table>
            <tbody>
                <Shortcut name={"Move selection up/down (Movement keys)"} keys={[ARROW_UP, "Home", "PgUp", ARROW_DOWN, "End", "PgDown"]} />
                <Shortcut name={"Select multiple (in a row)"} shift keys={["Movement key", "Left click"]} />
                <Shortcut name={"Select multiple (individual)"} ctrl keys={["Left click"]} />
                <tr>
                    <td>Go to row</td>
                    <td>Type part of name</td>
                </tr>
                <Shortcut name={"Open selection"} keys={"Enter"} />
                <Shortcut name={"Delete"} keys={"Delete"} />
                {(features.supportsMove || features.supportsCopy) && <>
                    <Shortcut name={"Undo"} ctrl keys={"Z"} />
                    <Shortcut name={"Copy"} ctrl keys={"C"} />
                    <Shortcut name={"Cut"} ctrl keys={"X"} />
                    <Shortcut name={"Paste"} ctrl keys={"V"} />
                </>}

                {(custom ?? []).map((c, i) => <React.Fragment key={i}>
                    {c.shortcut && <Shortcut name={c.name} keys={[]} {...c.shortcut} />}
                    {c.description && <tr>
                        <td>{c.name}</td>
                        <td>{c.description}</td>
                    </tr>}
                </React.Fragment>)}
            </tbody>
        </table>
    </div>
}

export function controlsOperation(features: ResourceBrowseFeatures, custom?: ControlDescription[]): Operation<unknown, unknown> & any {
    return {
        text: "",
        icon: "heroBolt",
        onClick: () => dialogStore.addDialog(<ControlsDialog features={features} custom={custom} />, () => {}),
        enabled: () => true,
        shortcut: ShortcutKey.Z,
        hackNotInTheContextMenu: true
    };
}
