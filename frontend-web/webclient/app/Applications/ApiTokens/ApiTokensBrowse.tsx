import * as React from "react";
import {MainContainer} from "@/ui-components";
import {addProjectSwitcherInPortal, EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts} from "@/ui-components/ResourceBrowser";
import {useNavigate} from "react-router";
import {useDispatch} from "react-redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import * as Api from "./api";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {callAPI} from "@/Authentication/DataHook";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {StandardCallbacks} from "@/ui-components/Browse";
import AppRoutes from "@/Routes";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: true,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    projectSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

export function ApiTokenBrowse(props: {opts?: ResourceBrowserOpts<Api.ApiToken>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Api.ApiToken> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    usePage("API tokens", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Api.ApiToken>(mount, "SSH keys", props.opts).init(browserRef, FEATURES, "", browser => {
                // TODO(Jonas): More can be added here
                browser.setColumns([{name: "Title"}, {name: "", columnWidth: 0}, {name: "", columnWidth: 0}, {name: "", columnWidth: 80}]);

                browser.on("skipOpen", (oldPath, path, resource) => resource != null);

                browser.on("open", (oldPath, newPath, resource) => {
                    // For initial fetch.
                    callAPI(Api.browse({
                        ...defaultRetrieveFlags,
                        ...browser.browseFilters,
                        ...props.opts?.additionalFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => {});

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        Api.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            ...props.opts?.additionalFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (key, row, dims) => {
                    // TODO(Jonas): Plenty to do
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(key.specification.title, row));

                    ResourceBrowser.icons.renderIcon({name: "heroCircleStack", color: "textPrimary", color2: "textPrimary", height: 64, width: 64}).then(setIcon);
                });

                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your API tokens...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No API token found with active filters.")
                            else e.reason.append("You have no API tokens.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your API tokens.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your API tokens. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.setEmptyIcon("heroCircleStack");

                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    isCreating: false,
                    api: {isCoreResource: true},
                    invokeCommand: callAPI,
                    reload: () => browser.refresh()
                }));

                browser.on("pathToEntry", sshKey => sshKey.id);

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries));
                });
            });
        }
        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    useSetRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}

function retrieveOperations(): Operation<Api.ApiToken, StandardCallbacks<Api.ApiToken>>[] {
    return [{
        icon: "heroCircleStack",
        text: "Add API token",
        primary: true,
        enabled: (selected) => selected.length === 0,
        onClick: (selected, cb) => {
            cb.navigate(AppRoutes.resources.apiTokensCreate());
        },
        shortcut: ShortcutKey.N,
    },
    {
        icon: "trash",
        text: "Revoke",
        color: "errorMain",
        enabled: (selected) => selected.length === 1,
        confirm: true,
        onClick: async ([element], cb) => {
            await cb.invokeCommand(
                Api.revoke({id: element.id})
            );

            cb.reload();
        },
        shortcut: ShortcutKey.R
    }];
}