import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {EmptyReasonTag, ResourceBrowser, addContextSwitcherInPortal, dateRangeFilters, getFilterStorageValue, resourceCreationWithProductSelector, setFilterStorageValue} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import LicenseApi, {License, LicenseSupport} from "@/UCloud/LicenseApi";
import {ResourceBrowseCallbacks, SupportByProvider} from "@/UCloud/ResourceApi";
import {doNothing, extractErrorMessage} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {Client} from "@/Authentication/HttpClientInstance";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {Product, ProductLicense} from "@/Accounting";
import {bulkRequestOf} from "@/DefaultObjects";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {FindByStringId} from "@/UCloud";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
};

const DUMMY_ENTRY_ID = "dummy";

const FEATURES = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
};

const supportByProvider = new AsyncCache<SupportByProvider<ProductLicense, LicenseSupport>>({
    globalTtl: 60_000
});

export function ExperimentalLicenses(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<License> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    const [productSelectorPortal, setProductSelectorPortal] = React.useState<JSX.Element>(<></>);
    useTitle("Licenses");

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<License>(mount, "Licenses").init(browserRef, FEATURES, "", browser => {
                var startCreation = function () { };

                supportByProvider.retrieve("", () =>
                    callAPI(LicenseApi.retrieveProducts())
                ).then(res => {
                    const creatableProducts: Product[] = [];
                    for (const provider of Object.values(res.productsByProvider)) {
                        for (const {product, support} of provider) {
                            // TODO(Jonas): What to guard against?
                            creatableProducts.push(product);
                        }
                    }

                    const dummyEntry = {
                        id: DUMMY_ENTRY_ID,
                        specification: {product: {id: "string"}}
                    } as License;

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
                            } as License;

                            browser.insertEntryIntoCurrentPage(activatedLicense);
                            browser.renderRows();
                            browser.selectAndShow(it => it === activatedLicense);

                            try {
                                const response = (await callAPI(
                                    LicenseApi.create(
                                        bulkRequestOf({
                                            product: productReference,
                                            domain: "",
                                        })
                                    )
                                )).responses[0] as unknown as FindByStringId;

                                activatedLicense.id = response.id;
                                browser.renderRows();
                            } catch (e) {
                                snackbarStore.addFailure("Failed to activate license. " + extractErrorMessage(e), false);
                                browser.refresh();
                                return;
                            }
                        },
                        "LICENSE"
                    )

                    startCreation = resourceCreator.startCreation;
                    setProductSelectorPortal(resourceCreator.portal);
                });

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.resource.properties("licenses", resource.id));
                    }

                    callAPI(LicenseApi.browse({
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
                        LicenseApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => [dateRanges, {
                    key: "status",
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

                if (!getFilterStorageValue(browser.resourceName, "status")) {
                    setFilterStorageValue(browser.resourceName, "status", "READY");
                }

                browser.on("renderRow", (license, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon)
                    if (license.id !== DUMMY_ENTRY_ID) {
                        const {product} = license.specification;
                        const title = product.id + (license.id ? ` (${license.id})` : "")
                        row.title.append(browser.defaultTitleRenderer(title, dims));
                    }

                    browser.icons.renderIcon({name: "fileSignatureSolid", color: "black", color2: "black", height: 32, width: 32}).then(setIcon);
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

                browser.on("fetchOperationsCallback", () => {/* TODO(Jonas): Missing props */
                    const callbacks: ResourceBrowseCallbacks<License> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        embedded: false,
                        /* TODO(Jonas): Find out how to cache projects in a meaningful way. */
                        isWorkspaceAdmin: !Client.hasActiveProject,
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation(): void {
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
                    return LicenseApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries) === true)
                });
                browser.on("pathToEntry", f => f.id);
                browser.on("nameOfEntry", f => f.specification.product.id);
                browser.on("sort", page => page.sort((a, b) => a.specification.product.id.localeCompare(b.specification.product.id)));
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
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