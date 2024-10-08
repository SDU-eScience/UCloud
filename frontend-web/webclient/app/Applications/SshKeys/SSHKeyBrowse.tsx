import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import SshKeyApi, {SSHKey} from "@/UCloud/SshKeyApi";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sorting: true,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    showColumnTitles: true,
    dragToSelect: true,
};

export function SSHKeyBrowse(props: {opts?: ResourceBrowserOpts<SSHKey>}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<SSHKey> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    usePage("SSH keys", SidebarTabId.RESOURCES);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<SSHKey>(mount, "SSH keys", props.opts).init(browserRef, FEATURES, "", browser => {
                browser.setColumns([{name: "Title"}, {name: "", columnWidth: 0}, {name: "", columnWidth: 0}, {name: "", columnWidth: 80}]);

                // Ensure no refecthing on `skipOpen`.
                browser.on("skipOpen", (oldPath, path, resource) => resource != null);
                browser.on("open", (oldPath, newPath, resource) => {
                    // For initial fetch.
                    callAPI(SshKeyApi.browse({
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
                        SshKeyApi.browse({
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
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon)

                    row.title.append(ResourceBrowser.defaultTitleRenderer(key.specification.title, row));

                    ResourceBrowser.icons.renderIcon({name: "heroKey", color: "textPrimary", color2: "textPrimary", height: 64, width: 64}).then(setIcon);
                });

                // We don't want it to capitalize the resource name
                browser.on("generateBreadcrumbs", () => [{title: browser.resourceName, absolutePath: ""}]);
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your SSH keys...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No SSH key found with active filters.")
                            else e.reason.append("You have no SSH keys.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your SSH keys.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your SSH keys. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.setEmptyIcon("heroKey");
                browser.on("fetchOperationsCallback", () => ({
                    dispatch,
                    navigate,
                    isCreating: false,
                    api: {isCoreResource: true},
                    invokeCommand: callAPI,
                    reload: () => browser.refresh()
                }));

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return SshKeyApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries));
                });

                browser.on("pathToEntry", sshKey => sshKey.id);
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
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
