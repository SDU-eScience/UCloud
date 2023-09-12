import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {GrantApplication, browseGrantApplications} from "./GrantApplicationTypes";
import {useDispatch} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {callAPI} from "@/Authentication/DataHook";
import {GrantApplicationFilter} from ".";
import MainContainer from "@/MainContainer/MainContainer";
import AppRoutes from "@/Routes";
import {IconName} from "@/ui-components/Icon";
import {STATE_ICON_AND_COLOR} from "./GrantApplications";
import {dateToString} from "@/Utilities/DateUtilities";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sortDirection: true,
    filters: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
}

export function ExperimentalGrantApplications({opts}: {opts?: {embedded: boolean; omitBreadcrumbs?: boolean; omitFilters?: boolean; disabledKeyhandlers?: boolean}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<GrantApplication>>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState(<></>);

    if (!opts?.embedded) {
        useTitle("Grant Applications");
    }

    const location = useLocation();
    let isIngoing = location.pathname.endsWith("/ingoing/");

    const omitsFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitsFilters,
        sortDirection: !omitsFilters,
        dragToSelect: !opts?.embedded,
    };

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<GrantApplication>(mount, "Grant Application", opts).init(browserRef, features, "", browser => {
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.project.grant(resource.id));
                        return;
                    }

                    callAPI(browseGrantApplications({
                        filter: GrantApplicationFilter.SHOW_ALL,
                        includeIngoingApplications: isIngoing,
                        includeOutgoingApplications: !isIngoing,
                        ...defaultRetrieveFlags
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    /* TODO(Jonas): Test if the fetch more works properly */
                    const result = await callAPI(
                        browseGrantApplications({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            filter: GrantApplicationFilter.SHOW_ALL,
                            includeIngoingApplications: isIngoing,
                            includeOutgoingApplications: !isIngoing
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (key, row, dims) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);
                    browser.icons.renderIcon({
                        name: "fileSignatureSolid",
                        color: "black",
                        color2: "iconColor2",
                        height: 32,
                        width: 32,
                    }).then(setIcon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(key.createdBy, dims));
                    const [statusIconName, statusIconColor] = STATE_ICON_AND_COLOR[key.status.overallState];
                    const [status, setStatus] = ResourceBrowser.defaultIconRenderer();
                    browser.icons.renderIcon({
                        name: statusIconName,
                        color: statusIconColor,
                        color2: "iconColor2",
                        height: 32,
                        width: 32,
                    }).then(setStatus);
                    row.stat2.innerText = dateToString(key.currentRevision.createdAt);
                    row.stat3.append(status);
                });

                browser.setEmptyIcon("fileSignatureSolid");

                browser.on("generateBreadcrumbs", () => {
                    if (opts?.omitBreadcrumbs) return [];
                    return [{title: `${isIngoing ? "Ingoing" : "Outgoing"} grants`, absolutePath: ""}];
                });
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your grant applications...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No grant applications found with active filters.")
                            else e.reason.append("This workspace has no grant applications.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your grant applications.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your grant applications. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => ({
                    dispatch, navigate, api: {}, isCreating: false, startCreation: () => console.log("TODO!"), cancelCreation: () => void 0
                }));

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const ops = [{
                        icon: "fileSignatureSolid" as IconName,
                        enabled(selected: GrantApplication[]) {
                            return selected.length === 0 && isIngoing;
                        },
                        onClick() {navigate(AppRoutes.project.grantsOutgoing())},
                        text: "Show outgoing applications",
                    }, {
                        icon: "fileSignatureSolid" as IconName,
                        enabled(selected: GrantApplication[]) {return selected.length === 0 && !isIngoing},
                        onClick() {navigate(AppRoutes.project.grantsIngoing())},
                        text: "Show ingoing applications",
                    }];
                    return ops.filter(it => it.enabled(selected));
                });

                browser.on("pathToEntry", grantApplication => grantApplication.id);
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
    return <MainContainer main={main}/>
}
