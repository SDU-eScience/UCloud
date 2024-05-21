import {productTypeToIcon, ProductV2, ProductV2Ingress} from "@/Accounting";
import {callAPI} from "@/Authentication/DataHook";
import {bulkRequestOf} from "@/UtilityFunctions";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";
import PublicLinkApi, {PublicLink, PublicLinkSupport} from "@/UCloud/PublicLinkApi";
import {
    ResourceBrowseCallbacks,
    retrieveSupportV2,
    SupportByProviderV2,
    supportV2ProductMatch
} from "@/UCloud/ResourceApi";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {createHTMLElements, doNothing, extractErrorMessage, timestampUnixMs} from "@/UtilityFunctions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal, checkIsWorkspaceAdmin, dateRangeFilters, providerIcon, resourceCreationWithProductSelector} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    includeUpdates: true,
    includeOthers: true,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};


const supportByProvider = new AsyncCache<SupportByProviderV2<ProductV2Ingress, PublicLinkSupport>>({
    globalTtl: 60_000
});

export function PublicLinkBrowse({opts}: {opts?: ResourceBrowserOpts<PublicLink>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<PublicLink> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    if (!opts?.embedded && !opts?.isModal) {
        usePage("Public links", SidebarTabId.RESOURCES);
    }
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<PublicLink>(mount, "Public Links", opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([
                    {name: "Domain"},
                    {name: "", columnWidth: 0},
                    {name: "", columnWidth: 0},
                    {name: "In use with", columnWidth: 250},
                ]);

                let startCreation: () => void = doNothing;
                const ingressBeingCreated = "$$collectionBeingCreated$$___$$";
                const isCreatingPrefix = "$$creating-";
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

                const supportPromise = supportByProvider.retrieve("", () => retrieveSupportV2(PublicLinkApi));

                supportPromise.then(res => {
                    browser.renderOperations();

                    const creatableProducts: ProductV2[] = [];
                    const ingressSupport: PublicLinkSupport[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            creatableProducts.push(supportV2ProductMatch(product, res));
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
                                if (browser.renameValue.length < 1) {
                                    browser.refresh();
                                    return;
                                }

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
                        "INGRESS",
                        product => {
                            const support = ingressSupport.find(it =>
                                it.product.id === product.category.name &&
                                it.product.provider === product.category.provider
                            );
                            if (!support) return;
                            browser.renamePrefix = support.domainPrefix;
                            browser.renameSuffix = support.domainSuffix;
                        }
                    );

                    startCreation = resourceCreator.startCreation;

                    setProductSelectorPortal(resourceCreator.portal);
                });

                browser.setEmptyIcon(productTypeToIcon("INGRESS"));

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        if (opts?.selection) {
                            if (opts.selection.show(resource) === true) {
                                opts.selection.onClick(resource);
                            }
                            return;
                        }

                        navigate(AppRoutes.resource.properties("public-links", resource.id));
                        return;
                    }

                    callAPI(PublicLinkApi.browse({
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
                        PublicLinkApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...opts?.additionalFilters
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
                        color: "textPrimary",
                        icon: "hashtag",
                        text: "Preparing",
                        value: "PREPARING",
                    }, {
                        color: "textPrimary",
                        icon: "hashtag",
                        text: "Ready",
                        value: "READY"
                    }, {
                        color: "textPrimary",
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
                    {
                        if (inputField?.style.display !== "none") {
                            const newPrefix = createHTMLElements({
                                tagType: "span",
                                className: "PREFIX",
                                innerText: browser.renamePrefix,
                                style: {
                                    position: "absolute",
                                    top: inputField.style.top,
                                    left: "10px"
                                }
                            });
                            parent.prepend(newPrefix);

                            const prefixRect = newPrefix.getBoundingClientRect();
                            inputField.style.left = prefixRect.width + 10 + "px";
                            const newPostfix = createHTMLElements({
                                tagType: "span",
                                className: "POSTFIX",
                                style: {
                                    position: "absolute",
                                    top: inputField.style.top,
                                    left: prefixRect.width + inputField.getBoundingClientRect().width + 10 + "px"
                                }
                            })
                            newPostfix.innerText = browser.renameSuffix;
                            parent.prepend(newPostfix);
                        }
                    }
                });

                browser.on("renderRow", (link, row, dims) => {
                    const {provider} = link.specification.product;

                    if (provider) {
                        const icon = providerIcon(link.specification.product.provider);
                        icon.style.marginRight = "8px";
                        row.title.append(icon);
                        row.title.append(ResourceBrowser.defaultTitleRenderer(link.specification.domain, dims, row));
                    }

                    if (link.status.boundTo.length === 1) {
                        const [boundTo] = link.status.boundTo;
                        row.stat3.innerText = boundTo;
                    }

                    if (opts?.selection && link.id !== ingressBeingCreated) {
                        const button = browser.defaultButtonRenderer(opts.selection, link);
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

                browser.on("fetchOperationsCallback", () => {
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
                        startRenaming(): void {},
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
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, [])

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
