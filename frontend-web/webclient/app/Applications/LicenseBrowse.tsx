import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import {
    EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts,
    addProjectSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, getFilterStorageValue,
    providerIcon, setFilterStorageValue
} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router-dom";
import LicenseApi, {License, LicenseSupport} from "@/UCloud/LicenseApi";
import {
    CREATE_TAG,
    Permission,
    ResourceAclEntry,
    ResourceBrowseCallbacks, retrieveSupportV2,
    SupportByProviderV2
} from "@/UCloud/ResourceApi";
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {productTypeToIcon, ProductV2License} from "@/Accounting";
import {bulkRequestOf} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {addProjectListener, removeProjectListener} from "@/Project/ReduxState";
import {Client} from "@/Authentication/HttpClientInstance";
import {dialogStore} from "@/Dialog/DialogStore";
import {ProductSelectorWithPermissions} from "./PublicLinks/PublicLinkBrowse";
import {slimModalStyle} from "@/Utilities/ModalUtilities";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeOthers: true,
};

const DUMMY_ENTRY_ID = "dummy";

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2License, LicenseSupport>>({
    globalTtl: 60_000
});

const PROJECT_CHANGE_LISTENER_ID = "license-project-listener";
export function LicenseBrowse({opts}: {opts?: ResourceBrowserOpts<License>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<License> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    if (!opts?.isModal) {
        usePage("Licenses", SidebarTabId.RESOURCES);
    }
    React.useEffect(() => {
        return () => removeProjectListener(PROJECT_CHANGE_LISTENER_ID);
    }, []);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<License>(mount, "Licenses", opts).init(browserRef, FEATURES, "", browser => {
                let startCreation = doNothing;

                browser.setColumns([{name: "License id"}, {name: "", columnWidth: 0}, {name: "", columnWidth: 0}, {name: "", columnWidth: 80}]);

                supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(LicenseApi));
                addProjectListener(PROJECT_CHANGE_LISTENER_ID, p => {
                    supportByProvider.retrieve(p ?? "", () => retrieveSupportV2(LicenseApi));
                });

                browser.on("skipOpen", (oldPath, newPath, resource) => {
                    if (resource && opts?.selection) {
                        if (opts.selection.show(resource) === true) {
                            opts.selection.onClick(resource);
                        }
                        return true;
                    }
                    return false;
                });

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("licenses", resource.id));
                    }

                    callAPI(LicenseApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        LicenseApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => [
                    dateRanges,
                    {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    },
                ]);

                if (!getFilterStorageValue(browser.resourceName, "status")) {
                    setFilterStorageValue(browser.resourceName, "status", "READY");
                }

                browser.on("renderRow", (license, row, dims) => {
                    const {provider} = license.specification.product;
                    if (provider) {
                        const icon = providerIcon(license.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                    }

                    if (license.id !== DUMMY_ENTRY_ID) {
                        const {product} = license.specification;
                        const title = `${product.id}${(license.id ? ` (${license.id})` : "")}`;
                        row.title.append(ResourceBrowser.defaultTitleRenderer(title, row));
                    }

                    if (opts?.selection) {
                        const button = browser.defaultButtonRenderer(opts.selection, license);
                        if (button) {
                            row.stat3.replaceChildren(button);
                        }
                    }
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your licenses...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No license found with active filters.")
                            else e.reason.append("This workspace has no licenses.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your licenses.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your licenses. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.setEmptyIcon(productTypeToIcon("LICENSE"));

                browser.on("fetchOperationsCallback", () => {
                    const callbacks: ResourceBrowseCallbacks<License> = {
                        supportByProvider: supportByProvider.retrieveFromCacheOnly("") ?? {productsByProvider: {}},
                        dispatch,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation: opts?.isModal ? undefined : function (): void {
                            startCreation();
                        },
                        cancelCreation: doNothing,
                        viewProperties(res: License): void {
                            navigate(AppRoutes.resource.properties(browser.resourceName, res.id));
                        },
                        commandLoading: false,
                        invokeCommand: callAPI,
                        api: LicenseApi,
                        isCreating: false
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    const operations = LicenseApi.retrieveOperations();
                    const create = operations.find(it => it.tag === CREATE_TAG);
                    if (create) {
                        create.enabled = () => true;
                        create.onClick = onCreateStart;
                    }
                    return operations.filter(it => it.enabled(entries, callbacks as any, entries))
                });
                browser.on("pathToEntry", f => f.id);
                browser.on("nameOfEntry", f => f.specification.product.id);
                browser.on("sort", page => page.sort((a, b) => {
                    return a.specification.product.id.localeCompare(b.specification.product.id);
                }));

                async function onCreateStart() {
                    const products = (await supportByProvider.retrieve(Client.projectId ?? "", () => retrieveSupportV2(LicenseApi))).newProducts;
                    dialogStore.addDialog(
                        <ProductSelectorWithPermissions
                            products={products}
                            placeholder="Type url..."
                            dummyEntry={dummyEntry}
                            title={LicenseApi.title}
                            onCreate={async (entry, product) => {
                                try {
                                    const license = {
                                        product: {
                                            id: product.name,
                                            category: product.category.name,
                                            provider: product.category.provider
                                        },
                                        domain: "",
                                    }

                                    const response = (await callAPI(
                                        LicenseApi.create(
                                            bulkRequestOf(license)
                                        )
                                    )).responses[0] as unknown as FindByStringId;

                                    /* Note(Jonas): I can't find the creation function in the backend,
                                       but either I'm sending it in the wrong way, or permissions are ignored when creating them initially.  
                                       
                                       Seems to be ignored in the backend.
                                    */
                                    if (response) {
                                        for (const permission of entry.permissions.others ?? []) {
                                            const fixedPermissions: Permission[] = permission.permissions.find(it => it === "EDIT") ? ["READ", "EDIT"] : ["READ"];
                                            const newEntry: ResourceAclEntry = {
                                                entity: {type: "project_group", projectId: permission.entity["projectId"], group: permission.entity["group"]},
                                                permissions: fixedPermissions
                                            };

                                            await callAPI(
                                                LicenseApi.updateAcl(bulkRequestOf(
                                                    {
                                                        id: response.id,
                                                        added: [newEntry],
                                                        deleted: [permission.entity]
                                                    }
                                                ))
                                            );
                                        };

                                        // TODO(Jonas): Insert into browser instead of full refresh
                                        dialogStore.success();
                                        browser.refresh();
                                    }
                                } catch (e) {
                                    snackbarStore.addFailure("Failed to create license. " + extractErrorMessage(e), false);
                                    browser.refresh();
                                    return;
                                }
                            }}
                            onCancel={() => dialogStore.failure()}
                        />,
                        () => {},
                        true,
                        slimModalStyle,
                    );
                }
            });


        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);

    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}

const dummyEntry: License = {
    id: DUMMY_ENTRY_ID,
    specification: {product: {id: "string"}}
} as License;