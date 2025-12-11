import {
    addContextSwitcherInPortal,
    ColumnTitleList,
    EmptyReasonTag,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts
} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {usePage} from "@/Navigation/Redux";
import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import AppRoutes from "@/Routes";
import {IconName} from "@/ui-components/Icon";
import {dateToDateStringOrTime, dateToString} from "@/Utilities/DateUtilities";
import * as Grants from ".";
import {exportGrants, stateToIconAndColor} from ".";
import {Client} from "@/Authentication/HttpClientInstance";
import {addTrailingSlash, createHTMLElements, prettierString, timestampUnixMs} from "@/UtilityFunctions";
import {ShortcutKey} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {divText} from "@/Utilities/HTMLUtilities";
import {SimpleAvatarComponentCache} from "@/Files/Shares";
import {avatarState} from "@/AvataaarLib/hook";
import {TruncateClass} from "@/ui-components/Truncate";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
    filter: Grants.ApplicationFilter.SHOW_ALL,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: true,
    filters: true,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
}

export function GrantApplicationBrowse({opts}: {opts?: ResourceBrowserOpts<Grants.Application> & {both?: boolean}}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Grants.Application>>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        usePage("Grant Applications", SidebarTabId.PROJECT);
    }

    const location = useLocation();
    let isIngoing = addTrailingSlash(location.pathname).endsWith("/ingoing/");

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        dragToSelect: !opts?.embedded && !opts?.isModal,
        showColumnTitles: !opts?.embedded && !opts?.isModal,
    };

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Grants.Application>(mount, "Grants Application", opts).init(browserRef, features, "", browser => {
                const simpleView = !!(opts?.embedded && !opts.isModal);
                const columns: ColumnTitleList = [{name: "Application"}, {name: "Submitted by", columnWidth: 220}, {name: "Last updated", columnWidth: 200}, {name: "Comments", columnWidth: 150}]
                if (simpleView) {
                    columns[1].columnWidth = 50;
                    columns[2].columnWidth = 120;
                    columns[3].columnWidth = 0;
                }
                browser.setColumns(columns);
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.grants.editor(resource.id));
                        return;
                    }

                    callAPI(Grants.browse({
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

                avatarState.subscribe(() => {
                    browser.rerender();
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        Grants.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters,
                            ...browser.browseFilters,
                            includeIngoingApplications: isIngoing || opts?.both,
                            includeOutgoingApplications: !isIngoing || opts?.both
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (app, row, dims) => {
                    const stateIconAndColor = stateToIconAndColor(app.status.overallState);
                    const statusIconName = stateIconAndColor.icon;
                    const statusIconColor = stateIconAndColor.color;

                    const [status, setStatus] = ResourceBrowser.defaultIconRenderer();
                    status.title = prettierString(app.status.overallState);
                    ResourceBrowser.icons.renderIcon({
                        name: statusIconName,
                        color: statusIconColor,
                        color2: "iconColor2",
                        height: 32,
                        width: 32,
                    }).then(setStatus);

                    status.style.width = "24px";
                    status.style.height = "24px";
                    row.title.append(status);

                    let subtitle: string = "";
                    let grantTitle = app.createdBy;
                    {
                        const recipient = app.currentRevision.document.recipient;
                        const projectTitle = app.status.projectTitle ?? "Unknown title";
                        switch (recipient.type) {
                            case "existingProject": {
                                subtitle = "Extension";
                                grantTitle = projectTitle;
                                break;
                            }

                            case "newProject": {
                                subtitle = "New project";
                                grantTitle = recipient.title;
                                break;
                            }

                            case "personalWorkspace": {
                                grantTitle = "Personal workspace of " + recipient.username;
                                break;
                            }
                        }
                    }

                    let combinedTitle = `${app.id}: ${grantTitle}`;

                    row.title.append(ResourceBrowser.defaultTitleRenderer(combinedTitle, row));

                    if (opts?.both) {
                        const currentRevision = app.status.revisions.at(0);
                        if (currentRevision) {
                            let isIngoing = currentRevision.document.allocationRequests.find(it => it.grantGiver === Client.projectId);
                            if (isIngoing) {
                                const text = createHTMLElements({
                                    tagType: "span",
                                    style: {color: "var(--textSecondary)"},
                                });
                                text.innerText = " (ingoing)";
                                row.title.append(text);
                            }
                        }
                    }
                    row.stat2.innerText = dateToString(app.currentRevision.createdAt);

                    if (!simpleView) {
                        row.stat2.innerText = dateToString(app.currentRevision.createdAt ?? timestampUnixMs());
                    } else {
                        row.stat2.innerText = dateToDateStringOrTime(app.currentRevision.createdAt ?? timestampUnixMs());
                    }

                    row.stat1.style.justifyContent = "left";
                    SimpleAvatarComponentCache.appendTo(row.stat1, app.createdBy, `Created by ${app.createdBy}`).then(wrapper => {
                        if (!simpleView) {
                            const div = divText(app.createdBy);
                            div.style.marginTop = div.style.marginBottom = "auto";
                            div.classList.add(TruncateClass);
                            div.style.maxWidth = "150px";
                            div.style.marginLeft = "12px";
                            wrapper.append(div);
                            wrapper.style.display = "flex";
                        }
                    });

                    if (!simpleView) {
                        const div = divText(app.status.comments.length.toString());
                        div.style.marginTop = div.style.marginBottom = "auto";
                        row.stat3.append(div);
                    }
                });

                browser.on("endRenderPage", () => {
                    SimpleAvatarComponentCache.fetchMissingAvatars();
                });

                browser.setEmptyIcon("heroDocument");

                browser.on("fetchFilters", () => [{
                    clearable: false,
                    type: "options",
                    icon: "fileSignatureSolid",
                    key: "filter",
                    options: [{
                        icon: "fileSignatureSolid", color: "primaryMain", text: "Show all", value: Grants.ApplicationFilter.SHOW_ALL
                    }, {
                        icon: "heroMinus", color: "primaryMain", text: "Active", value: Grants.ApplicationFilter.ACTIVE
                    }, {
                        icon: "heroCheck", color: "primaryMain", text: "Inactive", value: Grants.ApplicationFilter.INACTIVE
                    }],
                    text: "State"
                }]);

                browser.on("generateBreadcrumbs", () => {
                    if (opts?.embedded) return [];
                    return [{title: `Grant applications ${isIngoing ? "received" : "sent"} `, absolutePath: ""}];
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
                    const ops = [
                        {
                            icon: "heroArrowRight" as IconName,
                            enabled(selected: Grants.Application[]) {
                                return selected.length === 0 && isIngoing;
                            },
                            onClick() {navigate(AppRoutes.grants.outgoing())},
                            text: "Show applications sent",
                            shortcut: ShortcutKey.U
                        },
                        {
                            icon: "heroInbox" as IconName,
                            enabled(selected: Grants.Application[]) {return selected.length === 0 && !isIngoing},
                            onClick() {navigate(AppRoutes.grants.ingoing())},
                            text: "Show applications received",
                            shortcut: ShortcutKey.I
                        },
                        {
                            icon: "heroBellSlash" as IconName,
                            text: "Export",
                            enabled(selected: Grants.Application[]) {
                                return selected.length === 0 && isIngoing;
                            },
                            shortcut: ShortcutKey.X,
                            async onClick() {
                                const result = await callAPI(exportGrants());
                                console.log(result)
                            }
                        }
                    ];
                    return ops.filter(it => it.enabled(selected));
                });

                browser.on("pathToEntry", grantApplication => grantApplication.id);
            });
        }
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
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
    if (opts?.embedded) return <div>{main}</div>;
    return <MainContainer main={main} />
}
