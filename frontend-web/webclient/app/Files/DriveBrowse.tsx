import * as React from "react";
import {useLocation, useNavigate} from "react-router-dom";
import {useCallback, useEffect, useLayoutEffect, useRef} from "react";
import {
    EmptyReasonTag,
    ResourceBrowser,
    ResourceBrowseFeatures,
    ResourceBrowseHeaderControls,
    addProjectSwitcherInPortal,
    createProjectSwitcherPortal,
    providerIcon,
    ResourceBrowserOpts,
    SelectionMode,
} from "@/ui-components/ResourceBrowser";
import {useDispatch} from "react-redux";
import MainContainer from "@/ui-components/MainContainer";
import {callAPI, noopCall} from "@/Authentication/DataHook";
import {api as FileCollectionsApi, FileCollection, FileCollectionSupport, FileCollectionSpecification, isApplicationDrive} from "@/UCloud/FileCollectionsApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {FindByStringId, PageV2} from "@/UCloud";
import {dateToString} from "@/Utilities/DateUtilities";
import {doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {
    CREATE_TAG,
    DELETE_TAG, Permission, ResourceAclEntry,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2, supportV2ProductMatch
} from "@/UCloud/ResourceApi";
import {ProductStorage, ProductV2, ProductV2Storage} from "@/Accounting";
import {bulkRequestOf} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {Client} from "@/Authentication/HttpClientInstance";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {addProjectListener, getStoredProject} from "@/Project/ReduxState";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {useProject} from "@/Project/cache";
import {isAdminOrPI} from "@/Project";
import {dialogStore} from "@/Dialog/DialogStore";
import {ProductSelector} from "@/Products/Selector";
import {Box, Button, Flex, Input, Label} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import Text from "../ui-components/Text";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {connectionState} from "@/Providers/ConnectionState";
import {useProjectId} from "@/Project/Api";
import {sendFailureNotification} from "@/Notifications";
import {DriveChange} from "@/ui-components/Sidebar";

const applicationDrivesPath = "/application-drives";

type ApplicationDriveDirectory = {id: typeof applicationDrivesPath; title: string; virtual: true};
type DriveBrowserEntry = FileCollection | ApplicationDriveDirectory;

function isDriveEntry(entry: DriveBrowserEntry): entry is FileCollection {
    return !("virtual" in entry);
}

function driveEntryTitle(entry: DriveBrowserEntry): string {
    return isDriveEntry(entry) ? entry.specification.title : entry.title;
}

const collectionsOnOpen = new AsyncCache<PageV2<FileCollection>>({globalTtl: 500});
const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Storage, FileCollectionSupport>>({
    globalTtl: 60_000
});

const defaultRetrieveFlags: {itemsPerPage: number, includeOthers: true} = {
    itemsPerPage: 250,
    includeOthers: true, // Used to show permissions on load: issue #4209
};

const memberFilesKey = "filterMemberFiles";

const FEATURES: ResourceBrowseFeatures = {
    dragToSelect: true,
    supportsMove: false,
    supportsCopy: false,
    locationBar: false,
    showStar: false,
    renderSpinnerWhenLoading: true,
    breadcrumbsSeparatedBySlashes: true,
    search: true,
    // Note(Jonas): This feature is modified on project-change.
    // Initial value is based on having an active project context.
    filters: !!getStoredProject(),
    sorting: true,
    projectSwitcher: true,
    showColumnTitles: true,
};

const RESOURCE_NAME = "Drive";
const DriveBrowse: React.FunctionComponent<{
    opts?: ResourceBrowserOpts<FileCollection>;
    headerControls?: ResourceBrowseHeaderControls;
}> = ({opts, headerControls}) => {
    const navigate = useNavigate();
    const location = useLocation();
    const locationRef = useRef(location);
    locationRef.current = location;
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<DriveBrowserEntry> | null>(null);
    const dispatch = useDispatch();
    usePage("Drives", SidebarTabId.FILES);

    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    React.useEffect(() => {
        headerControls?.setRefresh?.(() => browserRef.current?.refresh());
        return () => {
            headerControls?.setRefresh?.(undefined);
        };
    }, [headerControls]);
    const isWorkspaceAdmin = React.useRef(!Client.hasActiveProject);
    const project = useProject();
    const projectId = useProjectId();

    React.useEffect(() => {
        if (opts?.embedded || opts?.isModal) return;
        const path = new URLSearchParams(location.search).get("driveView") === "application" ? applicationDrivesPath : "/";
        browserRef.current?.open(path);
    }, [location.search]);

    React.useEffect(() => {
        const p = project.fetch();
        const oldPermission = isWorkspaceAdmin.current;
        // Note(Jonas): Well, not anymore!!
        // Note(Jonas): project.fetch() always returns a project after having had one active before,
        // so use `projectId` instead.
        if (projectId) {
            isWorkspaceAdmin.current = isAdminOrPI(p.status.myRole);
        } else {
            isWorkspaceAdmin.current = true;
        }

        if (isWorkspaceAdmin.current !== oldPermission) {
            if (browserRef.current) {
                browserRef.current.renderOperations();
            }
        }
    }, [projectId, project.fetch()]);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            const browserOpts: ResourceBrowserOpts<DriveBrowserEntry> = {
                ...opts,
                selection: opts?.selection ? {
                    ...opts.selection,
                    show: entry => isDriveEntry(entry) ? opts.selection!.show(entry) : false,
                    onClick: entry => {if (isDriveEntry(entry)) opts.selection!.onClick(entry);},
                } : undefined,
            };
            const initialPath = !opts?.embedded && !opts?.isModal && new URLSearchParams(locationRef.current.search).get("driveView") === "application" ? applicationDrivesPath : "/";
            let searchOrigin = initialPath;
            const applicationFilter = (path: string): "ordinary" | "application" => path === applicationDrivesPath ? "application" : "ordinary";
            const directory: ApplicationDriveDirectory = {id: applicationDrivesPath, title: "Application drives", virtual: true};
            new ResourceBrowser<DriveBrowserEntry>(mount, RESOURCE_NAME, browserOpts).init(browserRef, FEATURES, initialPath, browser => {
                addProjectListener("drive-browse", p => {
                    browser.features.filters = !!p;
                    if (p) {
                        browser.header.setAttribute("data-has-filters", "");
                    } else {
                        browser.header.removeAttribute("data-has-filters");
                    }
                    fetchSupport(p ?? undefined);
                    browser.cachedData = {};
                    browser.cachedNext = {};
                    browser.open(browser.currentPath === applicationDrivesPath ? applicationDrivesPath : "/", true);
                    browser.reevaluateSize();
                    browser.rerender();
                });

                connectionState.fetch();


                browser.setColumns([
                    {name: "Drive name", sortById: "title"},
                    {name: "Provider", columnWidth: 100},
                    {name: "Created by", sortById: "createdBy", columnWidth: 250},
                    {name: "Created at", sortById: "createdAt", columnWidth: 160},
                ]);

                // Load products and initialize dependencies
                // =========================================================================================================

                function fetchSupport(projectId?: string) {
                    supportByProvider.retrieve(projectId ?? "", () => retrieveSupportV2(FileCollectionsApi)).then(() => {
                        browser.renderOperations();
                    });
                }

                fetchSupport(Client.projectId);

                // Operations
                // =========================================================================================================
                const startRenaming = (resource: FileCollection) => {
                    browser.showRenameField(
                        it => it.id === resource.id,
                        () => {
                            const oldTitle = resource.specification.title;
                            const page = browser.cachedData[browser.currentPath] ?? [];
                            const drive = page.filter(isDriveEntry).find(it => it.id === resource.id);
                            if (drive) {
                                drive.specification.title = browser.renameValue;
                                browser.dispatchMessage("sort", fn => fn(page));
                                browser.renderRows();
                                browser.selectAndShow(it => it.id === drive.id);

                                callAPI(FileCollectionsApi.rename(bulkRequestOf({
                                    id: drive.id,
                                    newTitle: drive.specification.title,
                                }))).catch(err => {
                                    sendFailureNotification(extractErrorMessage(err));
                                    browser.refresh();
                                });

                                ResourceBrowser.addUndoAction(RESOURCE_NAME, () => {
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

                browser.on("fetchFilters", () => {
                    if (Client.hasActiveProject) {
                        return [{type: "checkbox", key: memberFilesKey, icon: "user", text: "View member files"}];
                    }
                    return [];
                });

                browser.on("fetchOperationsCallback", () => {
                    const cachedSupport = supportByProvider.retrieveFromCacheOnly(Client.projectId ?? "");
                    const support = cachedSupport ?? {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<FileCollection, ProductStorage, FileCollectionSpecification> = {
                        supportByProvider: support,
                        dispatch,
                        isWorkspaceAdmin: isWorkspaceAdmin.current,
                        navigate: to => {
                            navigate(to)
                        },
                        reload: () => browser.refresh(),
                        startCreation: () => undefined,
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
                        creationDisabled: browser.currentPath !== "/" || browser.browseFilters[memberFilesKey] === "true" || browser.cachedData[browser.currentPath] == null,
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    if (selected.some(entry => !isDriveEntry(entry))) return [];
                    const drives = selected.filter(isDriveEntry);
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as unknown as any;
                    const actions = FileCollectionsApi.retrieveOperations();
                    const operations = actions;
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.onClick = () => {
                            const res = supportByProvider.retrieveFromCacheOnly(Client.projectId ?? "");
                            const creatableProducts: ProductV2[] = [];
                            if (res) {
                                for (const provider of Object.values(res.productsByProvider)) {
                                    for (const {product, support} of provider) {
                                        if (support.collection.usersCanCreate) {
                                            creatableProducts.push(supportV2ProductMatch(product, res));
                                        }
                                    }
                                }
                            }

                            dialogStore.addDialog(
                                <DriveCreate
                                    products={creatableProducts}
                                    onCancel={() => dialogStore.failure()}
                                    onCreate={async (product, title, permissions) => {
                                        const productReference = {
                                            id: product.name,
                                            category: product.category.name,
                                            provider: product.category.provider
                                        };

                                        const driveBeingCreated = {
                                            owner: {createdBy: Client.username ?? "", },
                                            updates: [],
                                            createdAt: timestampUnixMs(),
                                            status: {},
                                            permissions: {myself: []},
                                            id: title,
                                            specification: {
                                                title,
                                                product: productReference
                                            },
                                        } as FileCollection;

                                        browser.insertEntryIntoCurrentPage(driveBeingCreated);
                                        browser.renderRows();
                                        browser.selectAndShow(it => it === driveBeingCreated);

                                        try {
                                            const response = (await callAPI(FileCollectionsApi.create(bulkRequestOf({
                                                product: productReference,
                                                title
                                            })))).responses[0] as unknown as FindByStringId;

                                            driveBeingCreated.id = response.id;

                                            for (const permission of permissions) {
                                                const fixedPermissions: Permission[] = permission.permissions.find(it => it === "EDIT") ? ["READ", "EDIT"] : ["READ"];
                                                const newEntry: ResourceAclEntry = {
                                                    entity: {type: "project_group", projectId: permission.entity["projectId"], group: permission.entity["group"]},
                                                    permissions: fixedPermissions
                                                };

                                                await callAPI(
                                                    FileCollectionsApi.updateAcl(bulkRequestOf(
                                                        {
                                                            id: response.id,
                                                            added: [newEntry],
                                                            deleted: [permission.entity]
                                                        }
                                                    ))
                                                );
                                            }

                                            browser.renderRows();
                                            dialogStore.success();
                                            window.dispatchEvent(new CustomEvent(DriveChange));
                                        } catch (e: any) {
                                            sendFailureNotification("Failed to create new drive. " + extractErrorMessage(e));
                                            browser.refresh();
                                            return;
                                        }
                                    }}
                                />,
                                noopCall,
                                true,
                                slimModalStyle,
                            );
                        }
                    }
                    return operations.filter(op => op.enabled(drives, callbacks, drives)).map(op => ({
                        ...op,
                        text: typeof op.text === "function" ? op.text(drives, callbacks) : op.text,
                        confirmationText: typeof op.confirmationText === "function" ? op.confirmationText(drives, callbacks) : op.confirmationText,
                        confirmationButtonText: typeof op.confirmationButtonText === "function" ? op.confirmationButtonText(drives, callbacks) : op.confirmationButtonText,
                        operationType: op.operationType ? (location => op.operationType!(location, operations)) : undefined,
                        enabled: (entries: DriveBrowserEntry[]) => entries.every(isDriveEntry) && op.enabled(entries.filter(isDriveEntry), callbacks, entries.filter(isDriveEntry)),
                        onClick: (entries: DriveBrowserEntry[]) => {if (entries.every(isDriveEntry)) op.onClick(entries.filter(isDriveEntry), callbacks, entries.filter(isDriveEntry));},
                    }));
                });

                browser.on("rowSelectionUpdated", () => {
                    const selected = browser.findSelectedEntries();
                    if (selected.length <= 1 || selected.every(isDriveEntry)) return;
                    const index = browser.findVirtualRowIndex(entry => !isDriveEntry(entry));
                    if (index !== null) browser.select(index, SelectionMode.TOGGLE_SINGLE);
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
                                if (selected.length === 1 && isDriveEntry(selected[0])) {
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
                    const breadcrumbs = [{title: "Drives", absolutePath: "/"}];
                    if (browser.currentPath === applicationDrivesPath || browser.currentPath === "/search" && searchOrigin === applicationDrivesPath) {
                        breadcrumbs.push({title: "Application drives", absolutePath: applicationDrivesPath});
                    }
                    if (browser.searchQuery === "") return breadcrumbs;
                    return [...breadcrumbs, {
                        absolutePath: "",
                        title: `Search results for ${browser.searchQuery}`
                    }];
                });

                // Rendering of rows and empty pages
                // =========================================================================================================
                browser.on("renderRow", (drive, row, dims) => {
                    if (!isDriveEntry(drive)) {
                        const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                        row.title.append(icon);
                        ResourceBrowser.icons.renderIcon({name: "ftFolder", color: "FtFolderColor", color2: "FtFolderColor2", height: 64, width: 64}).then(setIcon);
                        icon.style.marginRight = "8px";
                        row.title.append(ResourceBrowser.defaultTitleRenderer(drive.title, row));
                        return;
                    }
                    if (drive.specification.product.provider) {
                        if (isShare(drive)) {
                            const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                            row.title.append(icon);
                            ResourceBrowser.icons.renderIcon({
                                name: "ftSharesFolder",
                                color: "FtFolderColor",
                                color2: "FtFolderColor2",
                                height: 64,
                                width: 64,
                            }).then(setIcon);
                            icon.style.marginRight = "8px";
                        } else {
                            const pIcon = providerIcon(drive.specification.product.provider);
                            pIcon.style.marginRight = "8px";
                            row.title.append(pIcon);
                        }
                    }

                    const title = ResourceBrowser.defaultTitleRenderer(drive.specification.title, row)
                    row.title.append(title);
                    if (browser.currentPath === "/search" && isApplicationDrive(drive)) {
                        const marker = document.createElement("span");
                        marker.innerText = "Application storage";
                        marker.style.marginLeft = "8px";
                        marker.style.fontSize = "12px";
                        marker.style.color = "var(--textSecondary)";
                        row.title.append(marker);
                    }
                    row.stat1.innerText = getShortProviderTitle(drive.specification.product.provider);
                    if (drive.owner.createdBy !== "_ucloud") {
                        const createdByElement = ResourceBrowser.defaultTitleRenderer(drive.owner.createdBy, row);
                        createdByElement.style.maxWidth = `calc(var(--stat2Width) - 20px)`;
                        row.stat2.append(createdByElement);
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
                            e.reason.append(browser.currentPath === applicationDrivesPath ? "No application drives found." : "No drives found.");
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
                browser.on("skipOpen", (oldPath, newPath, resource) => {
                    if (newPath === applicationDrivesPath || newPath === "/") return false;
                    if (!resource) return true;
                    if (!isDriveEntry(resource)) return false;
                    const isConnected = connectionState.isConnected(resource?.specification.product.provider);
                    if (!isConnected) {
                        const canConnect = connectionState.canConnectToProvider(resource?.specification.product.provider);
                        if (canConnect) {
                            connectionState.notification(resource.specification.product.provider, true);
                        }
                    }
                    return !isConnected;
                });

                browser.on("open", (oldPath, newPath) => {
                    if (newPath !== "/" && newPath !== applicationDrivesPath) {
                        const p = newPath.startsWith("/") ? newPath : "/" + newPath;
                        navigate(AppRoutes.files.path(p));
                        return;
                    }

                    if (!opts?.embedded && !opts?.isModal) {
                        const current = locationRef.current;
                        const params = new URLSearchParams(current.search);
                        if (newPath === applicationDrivesPath) params.set("driveView", "application");
                        else params.delete("driveView");
                        const search = params.toString();
                        if ((search ? "?" + search : "") !== current.search) navigate({pathname: current.pathname, search});
                    }

                    // Note(Jonas): This is to ensure no project and active project correctly reloads. Using "" as the key
                    // will not always work correctly, e.g. going from project to personal workspace with "View member files" active.
                    const project = Client.projectId;
                    const collectionKey = JSON.stringify([project, newPath, browser.browseFilters, opts?.additionalFilters]);
                    const page = collectionsOnOpen.retrieve(collectionKey, () =>
                        callAPI(FileCollectionsApi.browse({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                            filterApplicationFiles: applicationFilter(newPath),
                        }))
                    );
                    const applications = newPath === "/" ? collectionsOnOpen.retrieve(`${collectionKey}-applications`, () =>
                        callAPI(FileCollectionsApi.browse({
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                            itemsPerPage: 1,
                            filterApplicationFiles: "application",
                        }))
                    ) : Promise.resolve(undefined);
                    Promise.all([page, applications]).then(([res, applicationPage]) => {
                        if (Client.projectId !== project) return;
                        const items: DriveBrowserEntry[] = applicationPage?.items.length ? [directory, ...res.items] : res.items;
                        browser.registerPage({...res, items}, newPath, true);
                        browser.rerender();
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    if (path === "/search") {
                        const query = browser.searchQuery;
                        const result = await callAPI(FileCollectionsApi.search({
                            query,
                            next: browser.cachedNext[path] ?? undefined,
                            flags: {},
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters,
                            ...{filterApplicationFiles: searchOrigin === applicationDrivesPath ? "application" : "all"},
                        }));
                        if (path === browser.currentPath && query === browser.searchQuery) browser.registerPage(result, path, false);
                        return;
                    }
                    const result = await callAPI(
                        FileCollectionsApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                            filterApplicationFiles: applicationFilter(path),
                        })
                    );

                    if (path !== browser.currentPath) return;

                    browser.registerPage(result, path, false);
                });

                browser.on("search", async query => {
                    if (browser.currentPath !== "/search") searchOrigin = browser.currentPath;
                    browser.searchQuery = query;
                    browser.currentPath = "/search";
                    browser.cachedData["/search"] = [];
                    browser.renderRows();
                    browser.renderOperations();
                    callAPI(FileCollectionsApi.search({
                        query,
                        flags: {},
                        ...defaultRetrieveFlags,
                        ...opts?.additionalFilters,
                        ...{filterApplicationFiles: searchOrigin === applicationDrivesPath ? "application" : "all"},
                    })).then(res => {
                        if (browser.currentPath !== "/search" || browser.searchQuery !== query) return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
                });

                browser.on("searchHidden", () => {
                    browser.open(searchOrigin, true);
                });

                // Utilities required for the ResourceBrowser to understand the structure of the file-system
                // =========================================================================================================
                // This usually includes short functions which describe when certain actions should take place and what
                // the internal structure of a file is.
                browser.on("pathToEntry", f => f.id);
                browser.on("nameOfEntry", driveEntryTitle);
                browser.on("sort", page => page.sort((a, b) => !isDriveEntry(a) ? -1 : !isDriveEntry(b) ? 1 : driveEntryTitle(a).localeCompare(driveEntryTitle(b))));
            });
            addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
        }

        const b = browserRef.current;
        if (b) {
            b.renameField.style.left = "43px";
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
                {headerControls?.projectSwitcherTarget
                    ? createProjectSwitcherPortal(headerControls.projectSwitcherTarget)
                    : switcher}
            </>
        }
    />;
};

function isShare(d: FileCollection) {
    return d.specification.product.id === "share";
}

interface CreationWithInputFieldProps {
    onCreate(product: ProductV2, driveName: string, permissions: ResourceAclEntry[]): void;
    onCancel: () => void;
    products: ProductV2[];
}

const Container = injectStyle("drive-creation-container", k => `
    ${k} {
        display: flex;
        gap: 24px;
        flex-direction: column;
    }
`);

export function DriveCreate({onCreate, onCancel, products}: CreationWithInputFieldProps) {
    const [product, setSelectedProduct] = React.useState<ProductV2 | null>(null);
    const [entryId, setEntryId] = React.useState("");

    useEffect(() => {
        if (products.length === 1) {
            setSelectedProduct(products[0]);
        }
    }, [products]);

    const [acl, setAcl] = React.useState<ResourceAclEntry[]>([]);
    const project = useProject().fetch();
    const projectId = useProjectId();

    const onSubmit = useCallback((e: React.SubmitEvent) => {
        e.preventDefault();
        if (!product) return;
        onCreate(product, entryId, acl)
    }, [product, onCreate, entryId, acl]);

    return <form className={Container} onSubmit={onSubmit}>
        <Box>
            <Heading.h3>Create a drive</Heading.h3>
            <Box mt={"8px"}>
                Drives allow you to store files and folders. They are used to organize your data and allow you to grant
                different permissions to different parts of your data.
            </Box>
        </Box>

        <Label>
            Choose a name<MandatoryField />
            <Input
                placeholder="Enter drive name..."
                onKeyDown={e => {
                    if (e.key === "Escape") return;
                    e.stopPropagation()
                }}
                onChange={e => setEntryId(e.target.value)}
                autoFocus
            />
        </Label>

        <Box>
            <Label>Choose a product<MandatoryField /></Label>
            <ProductSelector
                onSelect={setSelectedProduct}
                products={products}
                selected={product}
                slim
            />
        </Box>

        {!projectId || !isAdminOrPI(project.status.myRole) ? null : (<Box>
            <Label>Choose access</Label>
            <Box maxHeight="400px" overflowY="auto">
                <Text mb="12px">
                    By default, only you and the project administrators can use this drive.
                    You can modify these permissions later on the <b>Properties</b> page.
                </Text>
                <PermissionsTable
                    acl={acl}
                    anyGroupHasPermission={false}
                    showMissingPermissionHelp={false}
                    warning="Warning"
                    title={"Drive"}
                    updateAcl={async (group, permission) => {
                        const aclEntry = acl.find(it => it.entity["group"] === group);
                        if (aclEntry) {
                            if (aclEntry.entity.type === "project_group") {
                                if (permission) { // READ, EDIT, ADMIN
                                    aclEntry.permissions = [permission]
                                } else { // None
                                    aclEntry.permissions = [];
                                }
                            }
                        } else if (permission) {
                            acl.push({entity: {type: "project_group", group, projectId}, permissions: [permission]})
                        }
                        setAcl([...acl]);
                    }}
                />
            </Box>
        </Box>)}

        <Box />

        <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
            <Button color={"errorMain"} type="button" onClick={onCancel}>Cancel</Button>
            <Button color={"successMain"} disabled={product == null || !entryId} type="submit">Create</Button>
        </Flex>
    </form>;
}

export default DriveBrowse;
