import {productTypeToIcon, ProductV2, ProductV2NetworkIP} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import MainContainer from "@/ui-components/MainContainer";
import {useTitle} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import NetworkIPApi, {NetworkIP, NetworkIPSupport} from "@/UCloud/NetworkIPApi";
import {
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2,
    supportV2ProductMatch
} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon, resourceCreationWithProductSelector} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    showColumnTitles: true,
    dragToSelect: true,
};

const DUMMY_ENTRY_ID = "dummy";

const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2NetworkIP, NetworkIPSupport>>({
    globalTtl: 60_000
});

export function NetworkIPBrowse({opts}: {opts?: ResourceBrowserOpts<NetworkIP>}): JSX.Element {
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
            new ResourceBrowser<NetworkIP>(mount, "Public IPs", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumnTitles([
                    {name: "IP address"},
                    {name: ""},
                    {name: ""},
                    {name: "In use with"},
                ]);

                let startCreation = doNothing;

                supportByProvider.retrieve("", () => retrieveSupportV2(NetworkIPApi)).then(res => {
                    const creatableProducts: ProductV2[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product} of provider) {
                            creatableProducts.push(supportV2ProductMatch(product, res));
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
                                owner: {createdBy: ""},
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

                browser.on("open", (_oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("public-ips", resource.id));
                        return;
                    }

                    callAPI(NetworkIPApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        NetworkIPApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters,
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.setEmptyIcon(productTypeToIcon("NETWORK_IP"));

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
                        const icon = providerIcon(ip.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                        row.title.append(ResourceBrowser.defaultTitleRenderer(ip.status.ipAddress ?? ip.id, dims, row));
                    }

                    if (ip.status.boundTo.length === 1) {
                        const [boundTo] = ip.status.boundTo;
                        row.stat3.innerText = boundTo;
                    }
                });

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
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

                browser.on("fetchOperationsCallback", () => {
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
                        viewProperties(res: NetworkIP): void {
                            navigate(AppRoutes.resource.properties("public-ips", res.id));
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
            {productSelectorPortal}
        </>}
    />
}
