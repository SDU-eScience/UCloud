import {Product, ProductIngress} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import IngressApi, {Ingress, IngressSupport} from "@/UCloud/IngressApi";
import {ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, addContextSwitcherInPortal, dateRangeFilters, providerIcon, resourceCreationWithProductSelector} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeUpdates: true,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
};

const INGRESS_PREFIX = "app-";
const INGRESS_POSTFIX = ".dev.cloud.sdu.dk";


const supportByProvider = new AsyncCache<SupportByProvider<ProductIngress, IngressSupport>>({
    globalTtl: 60_000
});

const DUMMY_ENTRY_ID = "dummy";

export function ExperimentalPublicLinks(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Ingress> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Public links");
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Ingress>(mount, "Public Links").init(browserRef, FEATURES, "", browser => {
                // TODO(Jonas): Set filter to "RUNNING" initially for state.

                let startCreation: () => void = doNothing;
                const ingressBeingCreated = "collectionBeingCreated$$___$$";
                const isCreatingPrefix = "creating-";
                const dummyEntry = {
                    createdAt: timestampUnixMs(),
                    status: {createdAt: 0, boundTo: [], state: "PREPARING"},
                    specification: {domain: "", product: {category: "", id: "", provider: ""}},
                    id: ingressBeingCreated,
                    owner: {createdBy: "", },
                    updates: [],
                    permissions: {myself: []},
                    domain: ""
                } as Ingress;

                const supportPromise = supportByProvider.retrieve("", () =>
                    callAPI(IngressApi.retrieveProducts())
                );

                supportPromise.then(res => {
                    browser.renderOperations();

                    const creatableProducts: Product[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            creatableProducts.push(product);
                        }
                    }

                    browser.renamePrefix = INGRESS_PREFIX;
                    browser.renamePostfix = INGRESS_POSTFIX;

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

                            const ingressBeingCreated = {
                                ...dummyEntry,
                                id: temporaryFakeId,
                                specification: {
                                    domain: INGRESS_PREFIX + browser.renameValue + INGRESS_POSTFIX,
                                    title: browser.renameValue,
                                    product: productReference
                                },
                            } as Ingress;


                            browser.insertEntryIntoCurrentPage(ingressBeingCreated);
                            browser.renderRows();
                            browser.selectAndShow(it => it === ingressBeingCreated);

                            try {
                                const response = (await callAPI(IngressApi.create(bulkRequestOf({
                                    domain: browser.renameValue,
                                    product: productReference
                                })))).responses[0] as unknown as FindByStringId;

                                ingressBeingCreated.id = response.id;
                                browser.renderRows();
                            } catch (e) {
                                snackbarStore.addFailure("Failed to create public link. " + extractErrorMessage(e), false);
                                browser.refresh();
                                return;
                            }
                        },
                        "INGRESS"
                    );

                    startCreation = resourceCreator.startCreation;

                    setProductSelectorPortal(resourceCreator.portal);
                });

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("public-links", resource.id));
                    }

                    callAPI(IngressApi.browse({
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
                        IngressApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

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

                browser.on("renderRow", (link, row, dims) => {
                    const icon = providerIcon(link.specification.product.provider);
                    icon.style.marginRight = "8px";
                    row.title.append(icon);
                    row.title.append(browser.defaultTitleRenderer(link.specification.domain, dims));
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your public links...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No public link found with active filters.")
                            else e.reason.append("This workspace has no public links.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your public links.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your public links. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {/* TODO(Jonas): Missing props */
                    const callbacks: ResourceBrowseCallbacks<Ingress> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        embedded: false,
                        isWorkspaceAdmin: false,
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation(): void {
                            startCreation();
                        },
                        cancelCreation: doNothing,
                        startRenaming(resource: Ingress): void { },
                        viewProperties(res: Ingress): void {
                            navigate(AppRoutes.resource.properties(browser.resourceName, res.id));
                        },
                        commandLoading: false,
                        invokeCommand: callAPI,
                        api: IngressApi,
                        isCreating: false
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return IngressApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries) === true)
                });

                browser.on("pathToEntry", entry => entry.id);
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
        // TODO(Jonas): Creation
    }, [])

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