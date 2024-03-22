import {ColumnTitleList, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import api, {ProjectInvite} from "./Api";
import {callAPI} from "@/Authentication/DataHook";
import {usePage} from "@/Navigation/Redux";
import {format} from "date-fns";
import {bulkRequestOf} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import {createHTMLElements} from "@/UtilityFunctions";
import {ButtonGroupClass} from "@/ui-components/ButtonGroup";
import {ShortcutKey} from "@/ui-components/Operation";
import {MainContainer} from "@/ui-components";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useAvatars} from "@/AvataaarLib/hook";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";
import {HTMLTooltip} from "@/ui-components/Tooltip";
import {TruncateClass} from "@/ui-components/Truncate";
import Avatar from "@/AvataaarLib/avatar";

const defaultRetrieveFlags: {itemsPerPage: number; filterType: "INGOING"} = {
    itemsPerPage: 250,
    filterType: "INGOING",
};

interface SetShowBrowserHack {
    setShowBrowser?: (show: boolean) => void;
}

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

let avatarCache: Record<string, ReactStaticRenderer> = {}
const rowTitles: ColumnTitleList = [{name: "Project title"}, {name: "", columnWidth: 150}, {name: "Invited", columnWidth: 150}, {name: "Invited by", columnWidth: 80}];
function ProviderBrowse({opts}: {opts?: ResourceBrowserOpts<ProjectInvite> & SetShowBrowserHack}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ProjectInvite> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        usePage("Project invites", SidebarTabId.WORKSPACE);
    }

    const omitFilters = !!opts?.omitFilters;
    const avatars = useAvatars();

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sorting: !omitFilters,
        dragToSelect: !opts?.embedded,
        search: !opts?.embedded,
    };

    avatars.subscribe(() => {
        avatarCache = {};
        browserRef.current?.renderRows()
    });

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<ProjectInvite>(mount, "Project invites", opts).init(browserRef, features, "", browser => {
                browser.setColumns(rowTitles);
                let currentAvatars = new Set<string>();
                let fetchedAvatars = new Set<string>();

                browser.on("skipOpen", (oldPath, newPath, res) => res != null);
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) return;

                    callAPI(api.browseInvites({
                        ...browser.browseFilters,
                        ...defaultRetrieveFlags,
                        ...opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                        opts?.setShowBrowser?.(result.items.length > 0);
                    });
                });

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        api.browseInvites({
                            next: browser.cachedNext[path] ?? undefined,
                            ...browser.browseFilters,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                    browser.renderRows();

                    // HACK(Jonas): For Dashboard invite cards.
                    opts?.setShowBrowser?.(result.items.length > 0);
                });

                browser.on("endRenderPage", () => {
                    if (currentAvatars.size > 0) {
                        avatars.updateCache([...currentAvatars]);
                        currentAvatars.forEach(it => fetchedAvatars.add(it));
                        currentAvatars.clear();
                    }
                });

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (invite, row, dims) => {
                    row.title.append(ResourceBrowser.defaultTitleRenderer(invite.projectTitle, dims, row));

                    const avatarWrapper = document.createElement("div");
                    row.stat3.append(avatarWrapper);
                    HTMLTooltip(avatarWrapper, createHTMLElements({tagType: "div", className: TruncateClass, innerText: `Invited by ${invite.invitedBy}`}), {tooltipContentWidth: 250});
                    if (avatarCache[invite.invitedBy]) {
                        const avatar = avatarCache[invite.invitedBy].clone()
                        avatarWrapper.appendChild(avatar);
                    } else {
                        // Row stat3
                        const avatar = avatars.avatar(invite.invitedBy);
                        if (!fetchedAvatars.has(invite.invitedBy)) {
                            currentAvatars.add(invite.invitedBy);
                        }

                        new ReactStaticRenderer(() =>
                            <Avatar style={{height: "40px", width: "40px"}} avatarStyle="Circle" {...avatar} />
                        ).promise.then(it => {
                            avatarCache[invite.invitedBy] = it;
                            const avatar = it.clone();
                            avatarWrapper.appendChild(avatar);
                        });
                    }

                    row.stat2.innerText = format(invite.createdAt, "hh:mm dd/MM/yyyy");
                    row.stat2.style.marginTop = row.stat2.style.marginBottom = "auto";
                    const group = createHTMLElements<HTMLDivElement>({
                        tagType: "div",
                        className: ButtonGroupClass,
                        style: {marginTop: "auto", marginBottom: "auto"}
                    });
                    row.stat1.append(group);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: async () => {
                            await callAPI(api.acceptInvite(bulkRequestOf({project: invite.invitedTo})))
                            browser.refresh();
                        },
                        show(res) {
                            return true
                        },
                        text: "Accept"
                    }, invite, {color: "successMain", width: "72px"})!);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: async () => {
                            await callAPI(api.deleteInvite(bulkRequestOf({username: Client.username!, project: invite.invitedTo})))
                            browser.refresh();
                        },
                        show(res) {
                            return true
                        },
                        text: "Decline"
                    }, invite, {color: "errorMain", width: "72px"})!);
                });

                browser.setEmptyIcon("play");

                browser.on("unhandledShortcut", () => void 0);

                browser.on("renderEmptyPage", reason => browser.defaultEmptyPage("project invite", reason, browser.browseFilters));

                browser.on("nameOfEntry", j => j.projectTitle);
                browser.on("pathToEntry", j => j.projectTitle);
                browser.on("fetchOperationsCallback", () => {
                    return {};
                });
                browser.on("fetchOperations", () => {
                    return [{
                        enabled: (selected) => selected.length === 1,
                        text: "Accept",
                        onClick: async ([invite]) => {
                            await callAPI(api.acceptInvite(bulkRequestOf({project: invite.invitedTo})));
                            browser.refresh();
                        },
                        icon: "check",
                        shortcut: ShortcutKey.N
                    }, {
                        enabled: (selected) => selected.length === 1,
                        text: "Decline",
                        color: "errorMain",
                        onClick: async ([invite]) => {
                            await callAPI(api.deleteInvite(bulkRequestOf({username: Client.username!, project: invite.invitedTo})))
                            browser.refresh();
                        },
                        icon: "close",
                        shortcut: ShortcutKey.Backspace
                    }];
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
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }
    }, []);


    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    return <MainContainer main={<div>
        <div ref={mountRef} />
        {switcher}
    </div>} />;
}

export default ProviderBrowse;