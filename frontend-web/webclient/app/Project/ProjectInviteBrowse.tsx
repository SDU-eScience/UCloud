import {ColumnTitleList, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import api, {ProjectInvite} from "./Api";
import {callAPI} from "@/Authentication/DataHook";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {PageV2} from "@/UCloud";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {format} from "date-fns";
import {bulkRequestOf} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {createHTMLElements} from "@/UtilityFunctions";
import {ButtonGroupClass} from "@/ui-components/ButtonGroup";

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


const rowTitles: ColumnTitleList = [{name: "Project title"}, {name: "Invited by"}, {name: "Invited"}, {name: ""}];
function ProviderBrowse({opts}: {opts?: ResourceBrowserOpts<ProjectInvite> & {page?: PageV2<ProjectInvite>}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<ProjectInvite> | null>(null);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        useTitle("Providers");
    }

    React.useEffect(() => {
        if (browserRef.current && opts?.page) {
            browserRef.current.registerPage(opts.page, "/", true);
        }
    }, [opts?.page]);

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
            new ResourceBrowser<ProjectInvite>(mount, "Project invites", opts).init(browserRef, features, "", browser => {
                browser.setColumnTitles(rowTitles);

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) return;

                    if (!opts?.page) {
                        callAPI(api.browseInvites({
                            ...browser.browseFilters,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters
                        })).then(result => {
                            browser.registerPage(result, newPath, true);
                            browser.renderRows();
                        });
                    }
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        api.browseInvites({
                            next: browser.cachedNext[path] ?? undefined,
                            ...browser.browseFilters,
                            ...defaultRetrieveFlags,
                            ...opts?.additionalFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (invite, row, dims) => {
                    row.title.append(ResourceBrowser.defaultTitleRenderer(invite.projectTitle, dims, row));


                    row.stat1.innerText = invite.invitedBy;
                    row.stat2.innerText = format(invite.createdAt, "dd/MM/yy");
                    const group = createHTMLElements<HTMLDivElement>({
                        tagType: "div",
                        className: ButtonGroupClass, 
                    });
                    row.stat3.append(group);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: () => {
                            callAPI(api.acceptInvite(bulkRequestOf({project: invite.invitedTo})))
                        },
                        text: "Accept"
                    }, invite, {color: "green", width: "72px"})!);
                    group.appendChild(browser.defaultButtonRenderer({
                        onClick: () => {
                            callAPI(api.deleteInvite(bulkRequestOf({username: Client.username!, project: invite.invitedTo})))
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
                    return [];
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
    return <div>{main}</div>;
}

export default ProviderBrowse;