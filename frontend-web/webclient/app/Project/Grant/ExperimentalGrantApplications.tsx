import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {GrantApplication, browseGrantApplications} from "./GrantApplicationTypes";
import {useDispatch, useSelector} from "react-redux";
import {useLocation, useNavigate} from "react-router";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useProjectId} from "../Api";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {createPortal} from "react-dom";
import {ContextSwitcher} from "../ContextSwitcher";
import {callAPI} from "@/Authentication/DataHook";
import {GrantApplicationFilter} from ".";
import MainContainer from "@/MainContainer/MainContainer";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";

const defaultRetrieveFlags = {
    itemsPerPage: 100,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    sortDirection: true,
    filters: true,
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
}

export function ExperimentalGrantApplications(): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<GrantApplication>>(null);
    const dispatch = useDispatch();
    const navigate = useNavigate();
    useTitle("Grant Applications");
    const projectId = useProjectId();
    const theme = useSelector<ReduxObject, "light" | "dark">(it => it.sidebar.theme);
    const [switcher, setSwitcherWorkaround] = React.useState(<></>);

    const location = useLocation();
    let isIngoing = location.pathname.endsWith("/ingoing/");
    console.log(isIngoing);
    const avatars = useAvatars();

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<GrantApplication>(mount, "Grant Application").init(browserRef, FEATURES, "", browser => {
                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        // TODO(Jonas): Handle properties
                    }

                    callAPI(browseGrantApplications({
                        filter: GrantApplicationFilter.SHOW_ALL,
                        includeIngoingApplications: isIngoing,
                        includeOutgoingApplications: !isIngoing,
                        ...defaultRetrieveFlags
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    })
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    /* TODO(Jonas): Test if the fetch more works properly */
                    const result = await callAPI(
                        browseGrantApplications({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters,
                            filter: GrantApplicationFilter.SHOW_ALL,
                            includeIngoingApplications: isIngoing,
                            includeOutgoingApplications: !isIngoing
                        })
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, path, false);
                });

                browser.on("renderRow", (key, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon)

                    row.title.append(browser.defaultTitleRenderer(key.createdBy, dims));

                    browser.icons.renderSvg(
                        key.createdBy, () => <UserAvatar
                            avatar={avatars.avatar(key.createdBy)}
                            width={"45px"}
                        />,
                        32,
                        32
                    ).then(setIcon);
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
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

                browser.on("fetchOperationsCallback", () =>
                    /* TODO(Jonas): Missing props */
                    ({dispatch, navigate, isCreating: false, startCreation: () => console.log("TODO!"), cancelCreation: () => void 0})
                );

                browser.on("fetchOperations", () => []);

                browser.on("pathToEntry", grantApplication => grantApplication.id);
            });


            const contextSwitcher = document.querySelector<HTMLDivElement>(".context-switcher");
            if (contextSwitcher) {
                setSwitcherWorkaround(createPortal(<ContextSwitcher />, contextSwitcher));
            }
        }
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