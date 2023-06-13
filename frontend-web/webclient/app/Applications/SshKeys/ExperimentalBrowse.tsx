import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, addContextSwitcherInPortal, dateRangeFilters} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {useNavigate} from "react-router";
import SshKeyApi, {SSHKey} from "@/UCloud/SshKeyApi";
import {useProjectId} from "@/Project/Api";
import {createPortal} from "react-dom";
import {ContextSwitcher} from "@/Project/ContextSwitcher";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sortDirection: true,
    filters: false,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
};

export function ExperimentalSSHKey(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<SSHKey> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("SSH keys");
    const projectId = useProjectId();
    const theme = useSelector<ReduxObject, "light" | "dark">(it => it.sidebar.theme);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<SSHKey>(mount, "SSH Keys").init(browserRef, FEATURES, "", browser => {
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
                            e.reason.append("We are fetching your SSH keys...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No SSH key found with active filters.")
                            else e.reason.append("This workspace has no SSH keys.");
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

                browser.on("fetchOperationsCallback", () =>
                    /* TODO(Jonas): Missing props */
                    ({dispatch, navigate, isCreating: false, startCreation, cancelCreation})
                );

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return SshKeyApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries) === true); 
                });

                browser.on("pathToEntry", sshKey => sshKey.id);
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
        // TODO(Jonas): Creation
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    /* Reload on new project */
    React.useEffect(() => {
        if (mountRef.current && browserRef.current) {
            browserRef.current.open("", true);
        }
    }, [projectId]);


    /* Re-render on theme change */
    React.useEffect(() => {
        if (mountRef.current && browserRef.current) {
            browserRef.current.rerender();
        }
    }, [theme]);

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}