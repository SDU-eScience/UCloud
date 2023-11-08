import * as React from "react";
import {default as Api, FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import {ResourceRouter} from "@/Resource/Router";
import Create from "@/Files/Metadata/Templates/Create";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, ColumnTitle, addContextSwitcherInPortal, checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import MainContainer from "@/MainContainer/MainContainer";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useNavigate} from "react-router";
import {useDispatch} from "react-redux";
import {callAPI} from "@/Authentication/DataHook";
import {dateToString} from "@/Utilities/DateUtilities";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {timestampUnixMs} from "@/UtilityFunctions";

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    dragToSelect: true,
    contextSwitcher: true,
    search: true,
    showColumnTitles: true,
};

const rowTitles: [ColumnTitle, ColumnTitle, ColumnTitle, ColumnTitle] = [{name: "Metadata namespace"}, {name: ""}, {name: ""}, {name: ""}];
export function MetadataNamespacesBrowse({opts}: {opts?: ResourceBrowserOpts<FileMetadataTemplateNamespace>}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<FileMetadataTemplateNamespace> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    useTitle("Metadata");

    const omitFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sorting: !omitFilters,
        dragToSelect: !opts?.embedded,
        search: !opts?.embedded,
        showColumnTitles: !opts?.embedded,
    };

    const simpleView = !!(opts?.embedded && !opts.isModal);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<FileMetadataTemplateNamespace>(mount, "Jobs", opts).init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.

                browser.setColumnTitles(rowTitles);

                const flags = {
                    ...defaultRetrieveFlags,
                    ...(opts?.additionalFilters ?? {})
                };

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        //navigate(AppRoutes.jobs.view(resource.id));
                        return;
                    }

                    callAPI(Api.browse({
                        ...browser.browseFilters,
                        ...flags,
                        ...opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        Api.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...browser.browseFilters,
                            ...flags,
                            ...opts?.additionalFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (provider, row, dims) => {
                    row.title.append(ResourceBrowser.defaultTitleRenderer(provider.specification.name, dims, row));

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
                                e.reason.append("No metadata namespace found with active filters.")
                            else e.reason.append("No metadata namespace found.");
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

                browser.on("nameOfEntry", j => j.specification.name ?? j.id ?? "");
                browser.on("pathToEntry", j => j.id);
                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}}; 
                    const callbacks: ResourceBrowseCallbacks<FileMetadataTemplateNamespace> = {
                        api: Api,
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
                        viewProperties: j => {
                            console.log("todo");
                        }
                    };
                    return callbacks;
                });
                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as any;
                    return Api.retrieveOperations().filter(op => op.enabled(entries, callbacks, entries));
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

const MetadataNamespacesRouter: React.FunctionComponent = () => {
    return <ResourceRouter api={Api} Browser={MetadataNamespacesBrowse} Create={Create} />;
};

export default MetadataNamespacesRouter;
