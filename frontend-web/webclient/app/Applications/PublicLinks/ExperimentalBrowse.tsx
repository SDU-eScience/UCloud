import {Product, ProductIngress} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/DefaultObjects";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import PublicLinkApi, {PublicLink, PublicLinkSupport} from "@/UCloud/PublicLinkApi";
import {ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon, resourceCreationWithProductSelector} from "@/ui-components/ResourceBrowser";
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
    contextSwitcher: true,
    rowTitles: true,
    dragToSelect: true,
};


const supportByProvider = new AsyncCache<SupportByProvider<ProductIngress, PublicLinkSupport>>({
    globalTtl: 60_000
});

export function ExperimentalPublicLinks(opts: ResourceBrowserOpts<PublicLink>): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<PublicLink> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Public links");
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<PublicLink>(mount, "Public Links").init(browserRef, FEATURES, "", browser => {
                browser.setRowTitles([{name: "Domain"}, {name: ""}, {name: ""}, {name: ""}]);

                let startCreation: () => void = doNothing;
                const ingressBeingCreated = "collectionBeingCreated$$___$$";
                const isCreatingPrefix = "creating-";
                const dummyEntry = {
                    createdAt: timestampUnixMs(),
                    status: {createdAt: 0, boundTo: [], state: "PREPARING"},
                    specification: {domain: "", product: {category: "", id: "", provider: ""}},
                    id: ingressBeingCreated,
                    owner: {createdBy: ""},
                    updates: [],
                    permissions: {myself: []},
                    domain: ""
                } as PublicLink;

                const supportPromise = supportByProvider.retrieve("", () =>
                    callAPI(PublicLinkApi.retrieveProducts())
                );

                supportPromise.then(res => {
                    browser.renderOperations();

                    const creatableProducts: Product[] = [];
                    const ingressSupport: PublicLinkSupport[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            creatableProducts.push(product);
                            ingressSupport.push(support);
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

                            const support = ingressSupport.find(it =>
                                it.product.id === product.category.name &&
                                it.product.provider === product.category.provider
                            );

                            if (!support) return;

                            browser.renamePrefix = support.domainPrefix;
                            browser.renameSuffix = support.domainSuffix;

                            const ingressBeingCreated: PublicLink = {
                                ...dummyEntry,
                                id: temporaryFakeId,
                                specification: {
                                    domain: browser.renamePrefix + browser.renameValue + browser.renameSuffix,
                                    product: productReference
                                },
                            };

                            browser.insertEntryIntoCurrentPage(ingressBeingCreated);
                            browser.renderRows();
                            browser.selectAndShow(it => it === ingressBeingCreated);

                            try {
                                const response = (await callAPI(PublicLinkApi.create(bulkRequestOf({
                                    domain: browser.renamePrefix + browser.renameValue + browser.renameSuffix,
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

                browser.setEmptyIcon("globeEuropeSolid");

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("public-links", resource.id));
                        return;
                    }

                    callAPI(PublicLinkApi.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    /* TODO(Jonas): Test if the fetch more works properly */
                    const result = await callAPI(
                        PublicLinkApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts.additionalFilters
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

                browser.on("startRenderPage", () => {
                    const inputField = browser.renameField;
                    const parent = inputField?.parentElement;
                    const prefix = browser.root.querySelector(".PREFIX");
                    const postfix = browser.root.querySelector(".POSTFIX");
                    if (!parent) return;
                    if (prefix) parent.removeChild(prefix);
                    if (postfix) parent.removeChild(postfix);
                });

                browser.on("endRenderPage", () => {
                    const inputField = browser.renameField;
                    const parent = inputField?.parentElement;
                    if (!parent || parent.hidden) return;
                    const prefix = browser.root.querySelector(".PREFIX");
                    const postfix = browser.root.querySelector(".POSTFIX");
                    {
                        if (inputField?.style.display !== "none") {
                            if (!prefix) {
                                const newPrefix = document.createElement("span");
                                newPrefix.innerText = browser.renamePrefix;
                                newPrefix.className = "PREFIX";
                                newPrefix.style.position = "absolute";
                                newPrefix.style.top = inputField.style.top;
                                newPrefix.style.left = inputField.getBoundingClientRect().left - 120 + "px";
                                parent.prepend(newPrefix);
                            }
                            if (!postfix) {
                                const newPostfix = document.createElement("span");
                                newPostfix.innerText = browser.renameSuffix;
                                newPostfix.className = "POSTFIX";
                                newPostfix.style.position = "absolute";
                                newPostfix.style.top = inputField.style.top;
                                newPostfix.style.left = inputField.getBoundingClientRect().right - 80 + "px";
                                parent.prepend(newPostfix);
                            }
                        }
                    }
                });

                browser.on("renderRow", (link, row, dims) => {
                    const {provider} = link.specification.product;

                    if (provider) {
                        const icon = providerIcon(link.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                        row.title.append(ResourceBrowser.defaultTitleRenderer(link.specification.domain, dims));
                    }
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
                    const callbacks: ResourceBrowseCallbacks<PublicLink> = {
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
                        startRenaming(resource: PublicLink): void { },
                        viewProperties(res: PublicLink): void {
                            navigate(AppRoutes.resource.properties("public-links", res.id));
                        },
                        commandLoading: false,
                        invokeCommand: callAPI,
                        api: PublicLinkApi,
                        isCreating: false
                    };

                    return callbacks;
                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return PublicLinkApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries))
                });

                browser.on("pathToEntry", entry => entry.id);
            });
        }
        const renameField = browserRef.current?.root.querySelector(".rename-field") as HTMLInputElement | null;
        if (renameField) {
            renameField.style.left = "90px";
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
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