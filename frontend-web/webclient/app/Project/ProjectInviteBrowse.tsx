import {ColumnTitleList, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import api, {ProjectInvite} from "./Api";
import {callAPI} from "@/Authentication/DataHook";
import {useTitle} from "@/Navigation/Redux";
import {format} from "date-fns";
import {bulkRequestOf} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import {createHTMLElements} from "@/UtilityFunctions";
import {ButtonGroupClass} from "@/ui-components/ButtonGroup";
import {ShortcutKey} from "@/ui-components/Operation";
import {MainContainer} from "@/ui-components";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";

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

const rowTitles: ColumnTitleList = [{name: "Project title"}, {name: ""}, {name: "Invited by"}, {name: "Invited"}];
function ProviderBrowse({opts}: {opts?: ResourceBrowserOpts<ProjectInvite> & SetShowBrowserHack}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ProjectInvite> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        useTitle("Providers");
    }

    const omitFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sorting: !omitFilters,
        dragToSelect: !opts?.embedded,
        search: !opts?.embedded,
    };

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<ProjectInvite>(mount, "", opts).init(browserRef, features, "", browser => {
                browser.setColumnTitles(rowTitles);

                browser.on("beforeOpen", (oldPath, newPath, res) => res != null);
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

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (invite, row, dims) => {
                    row.title.append(ResourceBrowser.defaultTitleRenderer(invite.projectTitle, dims, row));

                    row.stat2.innerText = invite.invitedBy;
                    row.stat3.innerText = format(invite.createdAt, "dd/MM/yy");
                    const group = createHTMLElements<HTMLDivElement>({
                        tagType: "div",
                        className: ButtonGroupClass,
                    });
                    row.stat1.append(group);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: async () => {
                            await callAPI(api.acceptInvite(bulkRequestOf({project: invite.invitedTo})))
                            browser.refresh();
                        },
                        text: "Accept"
                    }, invite, {color: "green", width: "72px"})!);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: async () => {
                            await callAPI(api.deleteInvite(bulkRequestOf({username: Client.username!, project: invite.invitedTo})))
                            browser.refresh();
                        },
                        text: "Decline"
                    }, invite, {color: "red", width: "72px"})!);
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
                        color: "red",
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