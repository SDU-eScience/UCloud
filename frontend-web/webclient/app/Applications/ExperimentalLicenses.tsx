import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {EmptyReasonTag, ResourceBrowser, dateRangeFilters} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {useDispatch, useSelector} from "react-redux";
import {useNavigate} from "react-router";
import LicenseApi, {License} from "@/UCloud/LicenseApi";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {doNothing} from "@/UtilityFunctions";
import AppRoutes from "@/Routes";
import {useProjectId} from "@/Project/Api";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {createPortal} from "react-dom";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
}

const FEATURES = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
};

export function ExperimentalLicenses(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<License> | null>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Public IPs");
    const projectId = useProjectId();
    const theme = useSelector<ReduxObject, "light" | "dark">(it => it.sidebar.theme);
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    const dateRanges = dateRangeFilters("Date created");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<License>(mount, "licenses").init(browserRef, FEATURES, "", browser => {
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

                    callAPI(LicenseApi.browse({
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
                        LicenseApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => [dateRanges, {
                    key: "status",
                    type: "options",
                    clearable: true,
                    icon: "radioEmpty",
                    options: [{
                        color: "black",
                        icon: "hashtag",
                        text: "Preparing",
                        value: "PREPARING",
                    }, {
                        color: "black",
                        icon: "hashtag",
                        text: "Ready",
                        value: "READY"
                    }, {
                        color: "black",
                        icon: "hashtag",
                        text: "Unavailable",
                        value: "UNAVAILABLE"
                    }],
                    text: "Status"
                }, {
                        type: "input",
                        icon: "user",
                        key: "filterCreatedBy",
                        text: "Created by"
                    }
                ]);

                browser.on("renderRow", (key, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon)

                    row.title.append(browser.defaultTitleRenderer(key.id, dims));

                    browser.icons.renderIcon({name: "fileSignatureSolid", color: "black", color2: "black", height: 32, width: 32}).then(setIcon);
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your licenses...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No license found with active filters.")
                            else e.reason.append("This workspace has no licenses.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your licenses.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your licenses. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("fetchOperationsCallback", () => {/* TODO(Jonas): Missing props */
                    const callbacks: ResourceBrowseCallbacks<License> = {
                        supportByProvider: {productsByProvider: {}},
                        dispatch,
                        embedded: false,
                        isWorkspaceAdmin: false,
                        navigate,
                        reload: () => browser.refresh(),
                        startCreation(): void {
                            startCreation();
                        },
                        cancelCreation: doNothing,
                        startRenaming(resource: License): void {
                            // TODO
                        },
                        viewProperties(res: License): void {
                            navigate(AppRoutes.resource.properties(browser.resourceName, res.id));
                        },
                        commandLoading: false,
                        invokeCommand: callAPI,
                        api: LicenseApi,
                        isCreating: false
                    };

                    return callbacks;


                });

                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn());
                    return LicenseApi.retrieveOperations().filter(it => it.enabled(entries, callbacks as any, entries))
                });
            });
            const contextSwitcher = document.querySelector<HTMLDivElement>(".context-switcher");
            if (contextSwitcher) {
                setSwitcherWorkaround(createPortal(<ContextSwitcher />, contextSwitcher));
            }
        }
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