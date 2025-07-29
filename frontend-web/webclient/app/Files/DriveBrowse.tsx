import * as React from "react";
import {useNavigate} from "react-router";
import {useCallback, useEffect, useLayoutEffect, useRef} from "react";
import {
    EmptyReasonTag,
    ResourceBrowser,
    ResourceBrowseFeatures,
    addContextSwitcherInPortal,
    providerIcon,
    ResourceBrowserOpts,
    useAllocations,
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
    CREATE_TAG,
    DELETE_TAG, Permission, ResourceAclEntry,
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2, supportV2ProductMatch
} from "@/UCloud/ResourceApi";
import {ProductV2, ProductV2Storage} from "@/Accounting";
import {bulkRequestOf} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {Client} from "@/Authentication/HttpClientInstance";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {addProjectListener} from "@/Project/ReduxState";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {useProject} from "@/Project/cache";
import {isAdminOrPI} from "@/Project";
import {Feature, hasFeature} from "@/Features";
import {dialogStore} from "@/Dialog/DialogStore";
import {ProductSelector} from "@/Products/Selector";
import {Box, Button, Flex, Input, Label} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import * as Heading from "@/ui-components/Heading";
import {MandatoryField} from "@/UtilityComponents";
import Text from "../ui-components/Text";
import {PermissionsTable} from "@/Resource/PermissionEditor";
import {slimModalStyle} from "@/Utilities/ModalUtilities";

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
    breadcrumbsSeparatedBySlashes: false,
    search: true,
    filters: true,
    sorting: true,
    contextSwitcher: true,
    showColumnTitles: true,
};

const RESOURCE_NAME = "Drive";
const DriveBrowse: React.FunctionComponent<{opts?: ResourceBrowserOpts<FileCollection>}> = ({opts}) => {
    const navigate = useNavigate();
    const mountRef = useRef<HTMLDivElement | null>(null);
    const browserRef = useRef<ResourceBrowser<FileCollection> | null>(null);
    const dispatch = useDispatch();
    usePage("Drives", SidebarTabId.FILES);

    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState(<></>);
    const allocations = useAllocations(browserRef, "STORAGE");

    const isWorkspaceAdmin = React.useRef(!Client.hasActiveProject);
    const project = useProject();

    React.useEffect(() => {
        const p = project.fetch();
        const oldPermission = isWorkspaceAdmin.current;
        if (p.id) {
            isWorkspaceAdmin.current = isAdminOrPI(p.status.myRole);
        } else {
            isWorkspaceAdmin.current = true;
        }
        if (isWorkspaceAdmin.current !== oldPermission) {
            if (browserRef.current) {
                browserRef.current.renderOperations();
            }
        }
    }, [project.fetch()]);

    useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<FileCollection>(mount, RESOURCE_NAME, opts).init(browserRef, FEATURES, "/", browser => {
                addProjectListener("drive-browse", p => {
                    browser.features.filters = !!p;
                    if (p) {
                        browser.header.setAttribute("data-has-filters", "");
                    } else {
                        browser.header.removeAttribute("data-has-filters");
                    }
                    fetchSupport(p ?? undefined);
                    browser.reevaluateSize();
                    browser.rerender();

                });


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

                                if (hasFeature(Feature.COMPONENT_STORED_CUT_COPY)) {
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
                                } else {
                                    browser._undoStack.unshift(() => {
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
                    const callbacks: ResourceBrowseCallbacks<FileCollection> = {
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
                        creationDisabled: browser.browseFilters[memberFilesKey] === "true",
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as unknown as any;
                    const operations = FileCollectionsApi.retrieveOperations();
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
                                        } catch (e) {
                                            snackbarStore.addFailure("Failed to create new drive. " + extractErrorMessage(e), false);
                                            browser.refresh();
                                            return;
                                        }
                                    }}
                                />,
                                () => {},
                                true,
                                slimModalStyle,
                            );
                        }
                    }
                    return operations.filter(op => op.enabled(selected, callbacks, selected));
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
                    return [{title: "Drives", absolutePath: "/"}, {
                        absolutePath: "",
                        title: `Search results for ${browser.searchQuery}`
                    }];
                });

                // Rendering of rows and empty pages
                // =========================================================================================================
                browser.on("renderRow", (drive, row, dims) => {
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
                    callAPI(FileCollectionsApi.search({
                        query,
                        itemsPerPage: 250,
                        flags: {},
                    })).then(res => {
                        if (browser.currentPath !== "/search") return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
                });

                browser.on("searchHidden", () => {
                    browser.open("/", true);
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
                {switcher}
                {productSelectorPortal}
                {allocations}
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
    const projectId = project.id;

    const onSubmit = useCallback((e: React.FormEvent) => {
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
                onKeyDown={e => e.stopPropagation()}
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
                            acl.push({entity: {type: "project_group", group, projectId: projectId!}, permissions: [permission]})
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
