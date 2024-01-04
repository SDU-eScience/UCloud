import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import AppRoutes from "@/Routes";
import {IconName} from "@/ui-components/Icon";
import {dateToDateStringOrTime, dateToString} from "@/Utilities/DateUtilities";
import * as Grants from ".";
import {stateToIconAndColor} from ".";
import {Client} from "@/Authentication/HttpClientInstance";
import {addTrailingSlash, createHTMLElements, timestampUnixMs} from "@/UtilityFunctions";
import {ShortcutKey} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: true,
    filters: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
}

export function GrantApplicationBrowse({opts}: {opts?: ResourceBrowserOpts<Grants.Application> & {both?: boolean}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Grants.Application>>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        useTitle("Grant Applications");
    }

    const location = useLocation();
    let isIngoing = addTrailingSlash(location.pathname).endsWith("/ingoing/");

    const omitsFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitsFilters,
        sorting: !omitsFilters,
        dragToSelect: !opts?.embedded,
    };

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Grants.Application>(mount, "Grant Application", opts).init(browserRef, features, "", browser => {
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.grants.editor(resource.id));
                        return;
                    }

                    callAPI(Grants.browse({
                        filter: Grants.ApplicationFilter.SHOW_ALL,
                        includeIngoingApplications: isIngoing || opts?.both,
                        includeOutgoingApplications: !isIngoing || opts?.both,
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...opts?.additionalFilters,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        Grants.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters,
                            ...browser.browseFilters,
                            filter: Grants.ApplicationFilter.SHOW_ALL,
                            includeIngoingApplications: isIngoing || opts?.both,
                            includeOutgoingApplications: !isIngoing || opts?.both
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

                    row.title.append(ResourceBrowser.defaultTitleRenderer(key.createdBy, dims, row));
                    if (opts?.both) {
                        const currentRevision = key.status.revisions.at(0);
                        if (currentRevision) {
                            let isIngoing = currentRevision.document.allocationRequests.find(it => it.grantGiver === Client.projectId);
                            if (isIngoing) {
                                const text = createHTMLElements({
                                    tagType: "span",
                                    style: {color: "var(--midGray)"},
                                });
                                text.innerText = " (ingoing)";
                                row.title.append(text);
                            }
                        }
                    }
                    const stateIconAndColor = stateToIconAndColor(key.status.overallState);
                    const statusIconName = stateIconAndColor.icon;
                    const statusIconColor = stateIconAndColor.color;

                    const [status, setStatus] = ResourceBrowser.defaultIconRenderer();
                    browser.icons.renderIcon({
                        name: statusIconName,
                        color: statusIconColor,
                        color2: "iconColor2",
                        height: 32,
                        width: 32,
                    }).then(setStatus);
                    row.stat2.innerText = dateToString(key.currentRevision.createdAt);

                    const simpleView = !!(opts?.embedded && !opts.isModal);
                    if (!simpleView) {
                        row.stat2.innerText = dateToString(key.currentRevision.createdAt ?? timestampUnixMs());
                    } else {
                        row.stat2.innerText = dateToDateStringOrTime(key.currentRevision.createdAt ?? timestampUnixMs());
                    }

                    status.style.margin = "0";
                    status.style.width = "24px";
                    status.style.height = "24px";
                    row.stat3.append(status);
                });

                browser.setEmptyIcon("heroDocument");

                browser.on("generateBreadcrumbs", () => {
                    if (opts?.embedded) return [];
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
                    dispatch, navigate, api: {isCoreResource: true}, isCreating: false, cancelCreation: () => void 0
                }));

                browser.on("fetchOperations", () => {
                    const selected = browser.findSelectedEntries();
                    const ops = [{
                        icon: "fileSignatureSolid" as IconName,
                        enabled(selected: Grants.Application[]) {
                            return selected.length === 0 && isIngoing;
                        },
                        onClick() {navigate(AppRoutes.grants.outgoing())},
                        text: "Show outgoing applications",
                        shortcut: ShortcutKey.U
                    }, {
                        icon: "fileSignatureSolid" as IconName,
                        enabled(selected: Grants.Application[]) {return selected.length === 0 && !isIngoing},
                        onClick() {navigate(AppRoutes.grants.ingoing())},
                        text: "Show ingoing applications",
                        shortcut: ShortcutKey.I
                    }];
                    return ops.filter(it => it.enabled(selected));
                });

                browser.on("pathToEntry", grantApplication => grantApplication.id);
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    const main = <>
        <div ref={mountRef} />
        {switcher}
    </>;
    if (opts?.embedded === true) return <div>{main}</div>;
    return <MainContainer main={main} />
}
