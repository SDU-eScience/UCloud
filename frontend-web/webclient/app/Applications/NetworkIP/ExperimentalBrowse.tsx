import {Product, ProductNetworkIP} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import NetworkIPApi, {NetworkIP, NetworkIPSupport} from "@/UCloud/NetworkIPApi";
import {ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, addContextSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon, resourceCreationWithProductSelector} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    rowTitles: true,
};

const DUMMY_ENTRY_ID = "dummy";

const supportByProvider = new AsyncCache<SupportByProvider<ProductNetworkIP, NetworkIPSupport>>({
    globalTtl: 60_000
});

export function ExperimentalNetworkIP(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<NetworkIP> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Public IPs");
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState<JSX.Element>(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<NetworkIP>(mount, "Public IPs").init(browserRef, FEATURES, "", browser => {
                browser.setRowTitles(["IP address", "", "", ""]);

                var startCreation = function () { };

                supportByProvider.retrieve("", () =>
                    callAPI(NetworkIPApi.retrieveProducts())
                ).then(res => {
                    const creatableProducts: Product[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            creatableProducts.push(product);
                        }
                    }

                    const dummyEntry: NetworkIP = {
                        id: DUMMY_ENTRY_ID,
                        specification: {product: {category: "", id: "", provider: ""}},
                        createdAt: new Date().getTime(),
                        owner: {createdBy: ""},
                        status: {boundTo: [], state: "PREPARING"},
                        permissions: {myself: []},
                        updates: [],
                    };

                    const resourceCreator = resourceCreationWithProductSelector(
                        browser,
                        creatableProducts,
                        dummyEntry,
                        async product => {
                            const productReference = {
                                id: product.name,
                                category: product.category.name,
                                provider: product.category.provider
                            };

                            const activatedLicense = {
                                ...dummyEntry,
                                id: "",
                                specification: {
                                    product: productReference
                                },
                                owner: {createdBy: "", },
                            } as NetworkIP;

                            browser.insertEntryIntoCurrentPage(activatedLicense);
                            browser.renderRows();
                            browser.selectAndShow(it => it === activatedLicense);

                            try {
                                const response = (await callAPI(
                                    NetworkIPApi.create(
                                        bulkRequestOf({
                                            product: productReference,
                                            domain: "",
                                        })
                                    )
                                )).responses[0] as unknown as FindByStringId;

                                activatedLicense.id = response.id;
                                browser.renderRows();
                            } catch (e) {
                                snackbarStore.addFailure("Failed to activate public IP. " + extractErrorMessage(e), false);
                                browser.refresh();
                                return;
                            }
                        },
                        "NETWORK_IP"
                    )

                    startCreation = resourceCreator.startCreation;
                    setProductSelectorPortal(resourceCreator.portal);
                });

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        // TODO(Jonas): Handle properties
                    }

                    callAPI(NetworkIPApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    /* TODO(Jonas): Test if the fetch more works properly */
                    const result = await callAPI(
                        NetworkIPApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.setEmptyIcon("networkWiredSolid");

                browser.on("fetchFilters", () => [dateRanges, {
                    key: "filterState",
                    type: "options",
                    clearable: true,
                    icon: "radioEmpty",
                    options: [{
                        color: "black",
                        icon: "hashtag",
                        text: "Preparing",
                        value: "PREPARING",
                    }, {
                        color: "black",
                        icon: "hashtag",
                        text: "Ready",
                        value: "READY"
                    }, {
                        color: "black",
                        icon: "hashtag",
                        text: "Unavailable",
                        value: "UNAVAILABLE"
                    }],
                    text: "Status"
                }, {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    }
                ]);

                browser.on("renderRow", (ip, row, dims) => {
                    if (ip.id !== DUMMY_ENTRY_ID) {
                        row.title.append(ResourceBrowser.defaultTitleRenderer(ip.status.ipAddress ?? ip.id, dims));
                        const icon = providerIcon(ip.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                    }
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your public IPs...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No network IP found with active filters.")
                            else e.reason.append("This workspace has no public IPs.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your public IPs.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your public IPs. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {/* TODO(Jonas): Missing props */
                    const callbacks: ResourceBrowseCallbacks<NetworkIP> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        embedded: false,
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation(): void {
                            startCreation();
                        },
                        cancelCreation: doNothing,
                        startRenaming(resource: NetworkIP): void {
                            // TODO
                        },
                        viewProperties(res: NetworkIP): void {
                            navigate(AppRoutes.resource.properties(browser.resourceName, res.id));
                        },
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        api: NetworkIPApi,
                        isCreating: false
                    };

                    return callbacks;


                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as ResourceBrowseCallbacks<NetworkIP>;
                    return NetworkIPApi.retrieveOperations().filter(it => it.enabled(entries, callbacks, entries));
                });
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
        // TODO(Jonas): Creation
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
            {productSelectorPortal}
        </>}
    />
}