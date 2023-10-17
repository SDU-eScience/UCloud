import * as React from "react";
import ProvidersApi, {Provider} from "@/UCloud/ProvidersApi";
import {useNavigate} from "react-router";
import MainContainer from "@/MainContainer/MainContainer";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, RowTitle, addContextSwitcherInPortal, checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";
import {useDispatch} from "react-redux";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {callAPI} from "@/Authentication/DataHook";
import {dateToString} from "@/Utilities/DateUtilities";
import {timestampUnixMs} from "@/UtilityFunctions";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
    dragToSelect: true,
    contextSwitcher: true,
    search: true,
    rowTitles: true,
};


const rowTitles: [RowTitle, RowTitle, RowTitle, RowTitle] = [{name: "Provider name"}, {name: ""}, {name: ""}, {name: ""}];
console.log("Provider Browse Not meaningfully tested at all");
function ProviderBrowse({opts}: {opts?: ResourceBrowserOpts<Provider>}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Provider> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    if (!opts?.embedded) {
        useTitle("Providers");
    }

    const omitFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sortDirection: !omitFilters,
        dragToSelect: !opts?.embedded,
        search: !opts?.embedded,
        rowTitles: !opts?.embedded,
    };

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Provider>(mount, "Jobs", opts).init(browserRef, features, "", browser => {
                browser.setRowTitles(rowTitles);

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        //navigate(AppRoutes.jobs.view(resource.id));
                        return;
                    }

                    callAPI(ProvidersApi.browse({
                        ...browser.browseFilters,
                        ...defaultRetrieveFlags,
                        ...opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        ProvidersApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...browser.browseFilters,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (provider, row, dims) => {
                    row.title.append(ResourceBrowser.defaultTitleRenderer(provider.specification.domain, dims));

                    row.stat1.innerText = provider.owner.createdBy;
                    row.stat2.innerText = dateToString(provider.createdAt ?? timestampUnixMs());
                });

                browser.setEmptyIcon("play");

                browser.on("unhandledShortcut", () => void 0);

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your jobs...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values({...browser.browseFilters, ...(opts?.additionalFilters ?? {})}).length !== 0)
                                e.reason.append("No providers found with active filters.")
                            else e.reason.append("No providers found.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to providers.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show providers. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("nameOfEntry", j => j.specification.domain ?? j.id ?? "");
                browser.on("pathToEntry", j => j.id);
                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}}; // TODO(Jonas), FIXME(Jonas): I assume that we need to do something different here.
                    const callbacks: ResourceBrowseCallbacks<Provider> = {
                        api: ProvidersApi,
                        navigate: to => navigate(to),
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        onSelect: opts?.selection?.onSelect,
                        embedded: false,
                        isCreating: false,
                        dispatch: dispatch,
                        supportByProvider: support,
                        reload: () => browser.refresh(),
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        viewProperties: provider => {
                            console.log("todo");
                        }
                    };
                    return callbacks;
                });
                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as any;
                    return ProvidersApi.retrieveOperations().filter(op => op.enabled(entries, callbacks, entries));
                });
                browser.on("generateBreadcrumbs", () => {
                    return [{title: "Providers", absolutePath: ""}]
                });

                browser.on("searchHidden", () => {
                    browser.searchQuery = "";
                    browser.currentPath = "/";
                    browser.renderRows();
                    browser.renderOperations();
                    browser.renderBreadcrumbs();
                });
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    const main = <>
        <div ref={mountRef} />
        {switcher}
    </>;
    if (opts?.embedded === true) return <div>{main}</div>;
    return <MainContainer main={main} />;
}

export default ProviderBrowse;
