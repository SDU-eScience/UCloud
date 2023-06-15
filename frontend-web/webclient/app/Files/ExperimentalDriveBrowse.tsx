import * as React from "react";
import {useNavigate} from "react-router";
import {useLayoutEffect, useRef} from "react";
import {
    div,
    image,
    EmptyReasonTag,
    ResourceBrowser,
    ResourceBrowseFeatures,
    addContextSwitcherInPortal,
    resourceCreationWithProductSelector,
    providerIcon,
} from "@/ui-components/ResourceBrowser";
import {useDispatch} from "react-redux";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import MainContainer from "@/MainContainer/MainContainer";
import {callAPI} from "@/Authentication/DataHook";
import {api as FileCollectionsApi, FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {FindByStringId, PageV2} from "@/UCloud";
import {dateToString} from "@/Utilities/DateUtilities";
import {doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {DELETE_TAG, ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {Product, ProductStorage} from "@/Accounting";
import {bulkRequestOf} from "@/DefaultObjects";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import AppRoutes from "@/Routes";
import {Client} from "@/Authentication/HttpClientInstance";

const collectionsOnOpen = new AsyncCache<PageV2<FileCollection>>({globalTtl: 500});
const supportByProvider = new AsyncCache<SupportByProvider<ProductStorage, FileCollectionSupport>>({
    globalTtl: 60_000
});

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    dragToSelect: true,
    supportsMove: false,
    supportsCopy: false,
    locationBar: false,
    showStar: false,
    renderSpinnerWhenLoading: true,
    breadcrumbsSeparatedBySlashes: true,
    search: true,
    filters: true,
    sortDirection: true,
    contextSwitcher: true,
};

const ExperimentalBrowse: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<FileCollection> | null>(null);
    const dispatch = useDispatch();
    useTitle("Drives");

    const [switcher, setSwitcherWorkaround] = React.useState(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState(<></>);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<FileCollection>(mount, "drive").init(browserRef, FEATURES, "/", browser => {


                // Load products and initialize dependencies
                // =========================================================================================================
                let startCreation: () => void = doNothing;
                const collectionBeingCreated = "collectionBeingCreated$$___$$";
                const isCreatingPrefix = "creating-";
                const dummyEntry: FileCollection = {
                    createdAt: timestampUnixMs(),
                    status: {},
                    specification: {title: "", product: {id: "", category: "", provider: ""}},
                    id: collectionBeingCreated,
                    owner: {createdBy: "", },
                    updates: [],
                    permissions: {myself: []}
                };

                const supportPromise = supportByProvider.retrieve("", () =>
                    callAPI(FileCollectionsApi.retrieveProducts())
                );

                supportPromise.then(res => {
                    browser.renderOperations();

                    const creatableProducts: Product[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            if (support.collection.usersCanCreate) {
                                creatableProducts.push(product);
                            }
                        }
                    }

                    const resourceCreator = resourceCreationWithProductSelector(
                        browser,
                        creatableProducts,
                        dummyEntry,
                        async product => {
                            const temporaryFakeId = isCreatingPrefix + browser.renameValue + "-" + timestampUnixMs();
                            const productReference = {
                                id: product.name,
                                category: product.category.name,
                                provider: product.category.provider
                            };

                            const driveBeingCreated = {
                                ...dummyEntry,
                                id: temporaryFakeId,
                                specification: {
                                    title: browser.renameValue,
                                    product: productReference
                                },                                
                            } as FileCollection;

                            browser.insertEntryIntoCurrentPage(driveBeingCreated);
                            browser.renderRows();
                            browser.selectAndShow(it => it === driveBeingCreated);

                            try {
                                const response = (await callAPI(FileCollectionsApi.create(bulkRequestOf({
                                    product: productReference,
                                    title: browser.renameValue
                                })))).responses[0] as unknown as FindByStringId;

                                driveBeingCreated.id = response.id;
                                browser.renderRows();
                            } catch (e) {
                                snackbarStore.addFailure("Failed to create new drive. " + extractErrorMessage(e), false);
                                browser.refresh();
                                return;
                            }
                        },
                        "STORAGE"
                    );

                    startCreation = resourceCreator.startCreation;
                    setProductSelectorPortal(resourceCreator.portal);
                });

                // Operations
                // =========================================================================================================
                const startRenaming = (resource: FileCollection) => {
                    browser.showRenameField(
                        it => it.id === resource.id,
                        () => {
                            const oldTitle = resource.specification.title;
                            const page = browser.cachedData["/"] ?? [];
                            const drive = page.find(it => it.id === resource.id);
                            if (drive) {
                                drive.specification.title = browser.renameValue;
                                browser.dispatchMessage("sort", fn => fn(page));
                                browser.renderRows();
                                browser.selectAndShow(it => it.id === drive.id);

                                callAPI(FileCollectionsApi.rename(bulkRequestOf({
                                    id: drive.id,
                                    newTitle: drive.specification.title,
                                }))).catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    browser.refresh();
                                });

                                browser.undoStack.unshift(() => {
                                    callAPI(FileCollectionsApi.rename(bulkRequestOf({
                                        id: drive.id,
                                        newTitle: oldTitle
                                    })));

                                    drive.specification.title = oldTitle;
                                    browser.dispatchMessage("sort", fn => fn(page));
                                    browser.renderRows();
                                    browser.selectAndShow(it => it.id === drive.id);
                                });
                            }
                        },
                        doNothing,
                        resource.specification.title,
                    );
                };

                browser.on("fetchFilters", () => [{
                    key: "sortBy",
                    text: "Sort by",
                    type: "options",
                    icon: "properties",
                    clearable: false,
                    options: [{
                        color: "black", icon: "id", text: "Name", value: "title"
                    }, {
                        color: "black", icon: "calendar", text: "Date created", value: "createdAt"
                    }, {
                        color: "black", icon: "user", text: "Created by", value: "createdBy"
                    }]
                }]);

                browser.on("fetchOperationsCallback", () => {
                    const cachedSupport = supportByProvider.retrieveFromCacheOnly("");
                    const support = cachedSupport ?? {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<FileCollection> = {
                        supportByProvider: support,
                        dispatch,
                        embedded: false,
                        /* TODO(Jonas): Find a way to get this info from cached projects. */
                        isWorkspaceAdmin: Client.hasActiveProject ? false : true, /* TODO(Jonas) */
                        navigate: to => {navigate(to)},
                        reload: () => browser.refresh(),
                        startCreation(): void {
                            startCreation();
                        },
                        cancelCreation: doNothing,
                        startRenaming(resource: FileCollection): void {
                            startRenaming(resource);
                        },
                        viewProperties(res: FileCollection): void {
                            navigate(AppRoutes.resource.properties("drives", res.id));
                        },
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        api: FileCollectionsApi,
                        isCreating: false
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as unknown as any;
                    return FileCollectionsApi.retrieveOperations().filter(op => op.enabled(selected, callbacks, selected) === true);
                });

                browser.on("unhandledShortcut", (ev) => {
                    let didHandle = true;
                    if (ev.ctrlKey || ev.metaKey) {
                        switch (ev.code) {
                            case "Backspace": {
                                browser.triggerOperation(it => it.tag === DELETE_TAG);
                                break;
                            }

                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    } else if (ev.altKey) {
                        switch (ev.code) {
                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    } else {
                        switch (ev.code) {
                            case "F2": {
                                const selected = browser.findSelectedEntries();
                                if (selected.length === 1) {
                                    startRenaming(selected[0]);
                                }
                                break;
                            }

                            case "Delete": {
                                browser.triggerOperation(it => it.tag === DELETE_TAG);
                                break;
                            }

                            default: {
                                didHandle = false;
                                break;
                            }
                        }
                    }

                    if (didHandle) {
                        ev.preventDefault();
                        ev.stopPropagation();
                    }
                })

                // Rendering of breadcrumbs
                // =========================================================================================================
                browser.on("generateBreadcrumbs", () => {
                    if (browser.searchQuery === "") return [{title: "Drives", absolutePath: "/"}];
                    return [{title: "Drives /", absolutePath: "/"}, {absolutePath: "", title: `Search results for ${browser.searchQuery}`}];
                });

                // Rendering of rows and empty pages
                // =========================================================================================================
                browser.on("renderRow", (drive, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon);

                    row.title.append(browser.defaultTitleRenderer(drive.specification.title, dims));
                    row.stat2.innerText = dateToString(drive.createdAt ?? timestampUnixMs());
                    row.stat3.append(providerIcon(drive.specification.product.provider));

                    if (drive.id.startsWith(isCreatingPrefix)) {
                        row.stat1.append(browser.createSpinner(30));
                    }

                    browser.icons.renderIcon({
                        name: "ftFileSystem",
                        color: "iconColor",
                        color2: "iconColor",
                        height: 64,
                        width: 64
                    }).then(setIcon);
                });

                browser.icons.renderIcon({
                    name: "ftFileSystem",
                    color: "iconColor",
                    color2: "iconColor",
                    height: 256,
                    width: 256
                }).then(icon => {
                    const fragment = document.createDocumentFragment();
                    fragment.append(image(icon, {height: 60, width: 60}));
                    browser.defaultEmptyGraphic = fragment;
                });

                browser.on("renderEmptyPage", reason => {
                    // NOTE(Dan): The reasons primarily come from the prefetch() function which fetches the data. If you
                    // want to recognize new error codes, then you should add the logic in prefetch() first.
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your drives...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            e.reason.append("This folder is empty");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your drives.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your drives. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                // Network requests
                // =========================================================================================================
                browser.on("open", (oldPath, newPath) => {
                    if (newPath !== "/") {
                        navigate("/files/?path=" + encodeURIComponent(`/${newPath}`));
                        return;
                    }

                    collectionsOnOpen.retrieve("", () =>
                        callAPI(FileCollectionsApi.browse({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        }))
                    ).then(res => {
                        browser.registerPage(res, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        FileCollectionsApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    );

                    if (path !== browser.currentPath) return;

                    browser.registerPage(result, path, false);
                });

                browser.on("search", async query => {
                    browser.searchQuery = query;
                    browser.currentPath = "/search";
                    browser.cachedData["/search"] = [];
                    browser.renderRows();
                    browser.renderOperations();
                    collectionsOnOpen.retrieve("/search", () =>
                        callAPI(FileCollectionsApi.search({
                            query,
                            itemsPerPage: 250,
                            flags: {},
                        }))
                    ).then(res => {
                        if (browser.currentPath !== "/search") return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
                });

                // Utilities required for the ResourceBrowser to understand the structure of the file-system
                // =========================================================================================================
                // This usually includes short functions which describe when certain actions should take place and what
                // the internal structure of a file is.
                browser.on("pathToEntry", f => f.id);
                browser.on("nameOfEntry", f => f.specification.title);
                browser.on("sort", page => page.sort((a, b) => a.specification.title.localeCompare(b.specification.title)));
            });

            addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
        }
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={
            <>
                <div ref={mountRef} />
                {switcher}
                {productSelectorPortal}
            </>
        }
    />;
};

export default ExperimentalBrowse;
