import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import JobsApi, {Job, JobState} from "@/UCloud/JobsApi";
import {dateToString} from "@/Utilities/DateUtilities";
import {timestampUnixMs} from "@/UtilityFunctions";
import {addContextSwitcherInPortal, checkIsWorkspaceAdmin, clearFilterStorageValue, dateRangeFilters, EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {appLogoCache} from "../AppToolLogo";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {AsyncCache} from "@/Utilities/AsyncCache";
import {AppLogo, hashF} from "../Card";
import {useNavigate} from "react-router";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useDispatch} from "react-redux";
import AppRoutes from "@/Routes";

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sortDirection: true,
    breadcrumbsSeparatedBySlashes: false,
    dragToSelect: true,
    contextSwitcher: true,
    search: true,
};

const logoDataUrls = new AsyncCache<string>();

function ExperimentalJobs({opts}: {opts?: ResourceBrowserOpts<Job>}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Job> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    if (!opts?.embedded) {
        useTitle("Jobs");
    }

    const dateRanges = dateRangeFilters("Created after");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Job>(mount, "Jobs", opts).init(browserRef, FEATURES, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));

                const flags = browser.opts?.embedded ? {itemsPerPage: 10} : {
                    ...defaultRetrieveFlags,
                    ...(opts?.additionalFilters ?? {})
                };

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource) {
                        navigate(AppRoutes.jobs.view(resource.id));
                        return;
                    }


                    callAPI(JobsApi.browse({
                        ...flags,
                        ...browser.browseFilters
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        JobsApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...defaultRetrieveFlags,
                            ...browser.browseFilters
                        })
                    );
                    browser.registerPage(result, path, false);
                });

                browser.on("fetchFilters", () => [{
                    key: "sortBy",
                    text: "Sort by",
                    clearable: false,
                    options: [{
                        color: "text",
                        icon: "calendar",
                        text: "Date created",
                        value: "createdAt"
                    }, {
                        value: "createdBy",
                        color: "text",
                        icon: "user",
                        text: "Created by"
                    }],
                    type: "options",
                    icon: "properties",
                },
                {
                    key: "filterCreatedBy",
                    text: "Created by",
                    type: "input",
                    icon: "user"
                },
                    dateRanges,
                {
                    key: "filterState",
                    options: [
                        {text: "In queue", value: "IN_QUEUE", icon: "hashtag", color: "text"},
                        {text: "Running", value: "RUNNING", icon: "hashtag", color: "text"},
                        {text: "Success", value: "SUCCESS", icon: "check", color: "text"},
                        {text: "Failure", value: "FAILURE", icon: "close", color: "text"},
                        {text: "Expired", value: "EXPIRED", icon: "chrono", color: "text"},
                        {text: "Suspended", value: "SUSPENDED", icon: "pauseSolid", color: "text"},
                    ],
                    clearable: true,
                    text: "Status",
                    type: "options",
                    icon: "radioEmpty"
                }]);

                browser.on("renderRow", (job, row, dims) => {
                    const [icon, setIcon] = browser.defaultIconRenderer();
                    row.title.append(icon);

                    row.title.append(browser.defaultTitleRenderer(job.specification.name ?? job.id, dims));
                    row.stat2.innerText = dateToString(job.createdAt ?? timestampUnixMs());


                    let didSetLogo = false;
                    logoDataUrls.retrieve(job.specification.application.name, async () => {
                        // Note(Jonas): Some possible improvements
                        browser.icons.renderSvg(
                            job.specification.application.name,
                            () => <AppLogo size={"32px"} hash={hashF(job.specification.application.name)} />,
                            32,
                            32
                        ).then(it => {
                            if (!didSetLogo) setIcon(it);
                        }).catch(e => console.log("render SVG error", e));

                        const result = await appLogoCache.fetchLogo(job.specification.application.name);
                        return result == null ? "" : result;
                    }).then(result => {
                        if (result) {
                            didSetLogo = true;
                            setIcon(result);
                        }
                    });

                    const [status, setStatus] = browser.defaultIconRenderer();
                    const [statusIconName, statusIconColor] = JOB_STATE_AND_ICON_COLOR_MAP[job.status.state];
                    browser.icons.renderIcon({
                        name: statusIconName,
                        width: 32,
                        height: 32,
                        color: statusIconColor,
                        color2: statusIconColor
                    }).then(setStatus);
                    row.stat1.append(status);
                });

                browser.setEmptyIcon("play");

                browser.on("unhandledShortcut", () => void 0);

                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your jobs...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values({...browser.browseFilters, ...(opts?.additionalFilters ?? {})}).length !== 0)
                                e.reason.append("No jobs found with active filters.")
                            else e.reason.append("This workspace has not run any jobs yet.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your jobs.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your jobs. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.on("nameOfEntry", j => j.specification.name ?? j.id ?? "");
                browser.on("pathToEntry", j => j.id);
                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}}; // TODO(Jonas), FIXME(Jonas): We need to do something different here.
                    const callbacks: ResourceBrowseCallbacks<Job> = {
                        api: JobsApi,
                        navigate: to => navigate(to),
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        onSelect: opts?.onSelect,
                        embedded: false,
                        isCreating: false,
                        dispatch: dispatch,
                        supportByProvider: support,
                        reload: () => browser.refresh(),
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        viewProperties: j => {
                            navigate(AppRoutes.jobs.view(j.id))
                        }
                    };
                    return callbacks;
                });
                browser.on("fetchOperations", () => {
                    const entries = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as any;
                    return JobsApi.retrieveOperations().filter(op => op.enabled(entries, callbacks, entries));
                });
                browser.on("generateBreadcrumbs", () => [{title: "Jobs", absolutePath: ""}]);
                browser.on("search", query => {
                    browser.searchQuery = query;
                    browser.currentPath = "/search";
                    browser.cachedData["/search"] = [];
                    browser.renderRows();
                    browser.renderOperations();

                    callAPI(JobsApi.search({
                        query,
                        itemsPerPage: 250,
                        flags: {},
                    })).then(res => {
                        if (browser.currentPath !== "/search") return;
                        browser.registerPage(res, "/search", true);
                        browser.renderRows();
                        browser.renderBreadcrumbs();
                    })
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

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />;
}

const JOB_STATE_AND_ICON_COLOR_MAP: Record<JobState, [IconName, ThemeColor]> = {
    IN_QUEUE: ["calendar", "iconColor"],
    RUNNING: ["chrono", "iconColor"],
    SUCCESS: ["check", "green"],
    FAILURE: ["close", "red"],
    EXPIRED: ["chrono", "orange"],
    SUSPENDED: ["pauseSolid", "iconColor"],
    CANCELING: ["close", "red"]
};

export default ExperimentalJobs;