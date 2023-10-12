import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch} from "react-redux";
import {useNavigate} from "react-router";
import SshKeyApi, {SSHKey} from "@/UCloud/SshKeyApi";
import {image} from "@/Utilities/HTMLUtilities";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sortDirection: true,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    rowTitles: true,
    dragToSelect: true,
};

export function ExperimentalSSHKey(props: {opts?: ResourceBrowserOpts<SSHKey>}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<SSHKey> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("SSH keys");
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<SSHKey>(mount, "SSH Keys", props.opts).init(browserRef, FEATURES, "", browser => {
                browser.setRowTitles([{name: "Title"}, {name: ""}, {name: ""}, {name: ""}]);

                // Ensure no refecthing on `beforeOpen`.
                browser.on("beforeOpen", (oldPath, path, resource) => resource != null);
                browser.on("open", (oldPath, newPath, resource) => {
                    // For initial fetch.
                    callAPI(SshKeyApi.browse({
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
                        SshKeyApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (key, row, dims) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    row.title.append(icon)

                    row.title.append(ResourceBrowser.defaultTitleRenderer(key.id, dims));

                    browser.icons.renderIcon({name: "key", color: "black", color2: "black", height: 32, width: 32}).then(setIcon);
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
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
                            else e.reason.append("You has no SSH keys.");
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

                browser.icons.renderIcon({
                    name: "key",
                    color: "iconColor",
                    color2: "iconColor",
                    height: 256,
                    width: 256
                }).then(icon => {
                    const fragment = document.createDocumentFragment();
                    fragment.append(image(icon, {height: 60, width: 60}));
                    browser.defaultEmptyGraphic = fragment;
                });

                browser.on("fetchOperationsCallback", () =>
                    ({dispatch, navigate, isCreating: false, api: {isCoreResource: true}})
                );

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

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}