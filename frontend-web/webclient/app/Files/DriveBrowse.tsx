import * as React from "react";
import {useNavigate} from "react-router";
import {useLayoutEffect, useRef} from "react";
import {
    EmptyReasonTag,
    ResourceBrowser,
    ResourceBrowseFeatures,
    addContextSwitcherInPortal,
    resourceCreationWithProductSelector,
    providerIcon,
    checkIsWorkspaceAdmin,
    ResourceBrowserOpts,
} from "@/ui-components/ResourceBrowser";
import {useDispatch} from "react-redux";
import MainContainer from "@/ui-components/MainContainer";
import {callAPI} from "@/Authentication/DataHook";
import {api as FileCollectionsApi, FileCollection, FileCollectionSupport} from "@/UCloud/FileCollectionsApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {FindByStringId, PageV2} from "@/UCloud";
import {dateToString} from "@/Utilities/DateUtilities";
import {doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {
    DELETE_TAG,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2, supportV2ProductMatch
} from "@/UCloud/ResourceApi";
import {ProductV2, ProductV2Storage} from "@/Accounting";
import {bulkRequestOf} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useTitle} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {Client} from "@/Authentication/HttpClientInstance";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

const collectionsOnOpen = new AsyncCache<PageV2<FileCollection>>({globalTtl: 500});
const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Storage, FileCollectionSupport>>({
    globalTtl: 60_000
});

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const memberFilesKey = "filterMemberFiles";

const FEATURES: ResourceBrowseFeatures = {
    dragToSelect: true,
    supportsMove: false,
    supportsCopy: false,
    locationBar: false,
    showStar: false,
    renderSpinnerWhenLoading: true,
    breadcrumbsSeparatedBySlashes: false,
    search: true,
    filters: true,
    sorting: true,
    contextSwitcher: true,
    showColumnTitles: true,
};

const DriveBrowse: React.FunctionComponent<{opts?: ResourceBrowserOpts<FileCollection>}> = ({opts}) => {
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
            new ResourceBrowser<FileCollection>(mount, "drive", opts).init(browserRef, FEATURES, "/", browser => {
                browser.setColumnTitles([
                    {name: "Drive name", sortById: "title"},
                    {name: ""},
                    {name: "Created by", sortById: "createdBy"},
                    {name: "Created at", sortById: "createdAt"},
                ]);

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
                    owner: {createdBy: Client.username ?? "", },
                    updates: [],
                    permissions: {myself: []}
                };

                const supportPromise = supportByProvider.retrieve("", () => retrieveSupportV2(FileCollectionsApi));

                supportPromise.then(res => {
                    browser.renderOperations();

                    const creatableProducts: ProductV2[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            if (support.collection.usersCanCreate) {
                                creatableProducts.push(supportV2ProductMatch(product, res));
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

                browser.on("beforeOpen", (oldPath, newPath, drive) =>
                    drive?.id === dummyEntry.id
                );

                browser.on("fetchFilters", () => {
                    if (Client.hasActiveProject) {
                        return [{type: "checkbox", key: memberFilesKey, icon: "user", text: "View member files"}];
                    }
                    return [];
                });

                browser.on("fetchOperationsCallback", () => {
                    const cachedSupport = supportByProvider.retrieveFromCacheOnly("");
                    const support = cachedSupport ?? {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<FileCollection> = {
                        supportByProvider: support,
                        dispatch,
                        embedded: false,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
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
                        isCreating: false,
                        creationDisabled: browser.browseFilters[memberFilesKey] === "true",
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as unknown as any;
                    return FileCollectionsApi.retrieveOperations().filter(op => op.enabled(selected, callbacks, selected));
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
                    return [{title: "Drives", absolutePath: "/"}, {absolutePath: "", title: `Search results for ${browser.searchQuery}`}];
                });

                // Rendering of rows and empty pages
                // =========================================================================================================
                browser.on("renderRow", (drive, row, dims) => {
                    if (drive.specification.product.provider) {
                        if (isShare(drive)) {
                            const [icon, setIcon] = ResourceBrowser.defaultIconRenderer(opts?.embedded === true);
                            row.title.append(icon);
                            browser.icons.renderIcon({
                                name: "ftSharesFolder",
                                color: "FtFolderColor",
                                color2: "FtFolderColor2",
                                height: 64,
                                width: 64,
                            }).then(setIcon);
                            icon.style.marginLeft = "4px";
                            icon.style.marginRight = "10px";
                        } else {
                            const pIcon = providerIcon(drive.specification.product.provider);
                            pIcon.style.marginRight = "8px";
                            row.title.append(pIcon);
                        }
                    }

                    const title = ResourceBrowser.defaultTitleRenderer(drive.specification.title, dims, row)
                    row.title.append(title);
                    if (drive.owner.createdBy !== "_ucloud") {
                        row.stat2.innerText = drive.owner.createdBy;
                    }
                    if (drive.id.startsWith(isCreatingPrefix)) {
                        row.stat2.append(browser.createSpinner(30));
                    }

                    row.stat3.innerText = dateToString(drive.createdAt ?? timestampUnixMs());
                });


                browser.setEmptyIcon("ftFileSystem");

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
                            e.reason.append("No drives found.");
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
                        const p = newPath.startsWith("/") ? newPath : "/" + newPath;
                        navigate("/files?path=" + encodeURIComponent(p));
                        return;
                    }

                    // Note(Jonas): This is to ensure no project and active project correctly reloads. Using "" as the key
                    // will not always work correctly, e.g. going from project to personal workspace with "View member files" active.
                    const collectionKey = `${Client.projectId}-${browser.browseFilters[memberFilesKey]}`;
                    collectionsOnOpen.retrieve(collectionKey, () =>
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

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

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

function isShare(d: FileCollection) {
    return d.specification.product.id === "share";
}


export default DriveBrowse;
