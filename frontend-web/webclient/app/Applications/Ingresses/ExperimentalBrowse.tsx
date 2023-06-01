import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import IngressApi, {Ingress} from "@/UCloud/IngressApi";
import {EmptyReasonTag, ResourceBrowser, dateRangeFilters} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
};

export function ExperimentalPublicLinks(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Ingress> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Public links");

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Ingress>(mount, "Public links").init(browserRef, FEATURES, "", browser => {
                // TODO(Jonas): Set filter to "RUNNING" initially for state.

                const isCreatingPrefix = "creating-";
                const {startCreation, cancelCreation} = {
                    startCreation: () => void 0,
                    cancelCreation: () => void 0,
                };


                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        // TODO(Jonas): Handle properties
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
                }]);

                browser.on("renderRow", (key, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon)

                    row.title.append(browser.defaultTitleRenderer(key.id, dims));

                    browser.icons.renderIcon({name: "networkWiredSolid", color: "black", color2: "black", height: 32, width: 32}).then(setIcon);
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

                browser.on("fetchOperationsCallback", () => /* TODO(Jonas): Missing props */
                    ({dispatch, navigate, isCreating: false, startCreation, cancelCreation})
                );

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return IngressApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries))
                });
            });
        }
        // TODO(Jonas): Creation
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<div ref={mountRef} />}
    />
}