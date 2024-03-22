import {callAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import JobsApi, {Job, JobState} from "@/UCloud/JobsApi";
import {dateToDateStringOrTime, dateToString} from "@/Utilities/DateUtilities";
import {timestampUnixMs} from "@/UtilityFunctions";
import {addContextSwitcherInPortal, checkIsWorkspaceAdmin, clearFilterStorageValue, dateRangeFilters, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, ColumnTitle, ColumnTitleList} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import {AppLogo, appLogoCache, hashF} from "../AppToolLogo";
import {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useNavigate} from "react-router";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useDispatch} from "react-redux";
import AppRoutes from "@/Routes";
import {Operation} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {logoDataUrls} from "./LogoDataCache";
import {jobCache} from "./View";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

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

const columnTitles: ColumnTitleList = [{name: "Job name"}, {name: "Created by", sortById: "createdBy", columnWidth: 250}, {name: "Created at", sortById: "createdAt", columnWidth: 150}, {name: "State", columnWidth: 75}];
const simpleViewColumnTitles: ColumnTitleList = [{name: ""}, {name: "", sortById: "", columnWidth: 0}, {name: "", sortById: "", columnWidth: 160}, {name: "State", columnWidth: 28}];

function JobBrowse({opts}: {opts?: ResourceBrowserOpts<Job> & {omitBreadcrumbs?: boolean; omitFilters?: boolean; operations?: Operation<Job, ResourceBrowseCallbacks<Job>>[]}}): JSX.Element {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Job> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        usePage("Jobs", SidebarTabId.RUNS);
    }

    const omitFilters = !!opts?.omitFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !omitFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sorting: !omitFilters,
        dragToSelect: !opts?.embedded,
        search: !opts?.embedded,
        showColumnTitles: !opts?.embedded,
    };

    const dateRanges = dateRangeFilters("Created");

    const simpleView = !!(opts?.embedded && !opts.isModal) || opts?.selection !== undefined;

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            new ResourceBrowser<Job>(mount, "Jobs", opts).init(browserRef, features, "", browser => {
                // Removed stored filters that shouldn't persist.
                dateRanges.keys.forEach(it => clearFilterStorageValue(browser.resourceName, it));

                if (opts?.selection) {
                    const withUseRowTitles: ColumnTitleList = JSON.parse(JSON.stringify(simpleViewColumnTitles));
                    withUseRowTitles[3].columnWidth = 100;
                    browser.setColumns(withUseRowTitles)
                } else {
                    browser.setColumns(simpleView ? simpleViewColumnTitles : columnTitles);
                }

                const flags = {
                    ...defaultRetrieveFlags,
                    ...(opts?.additionalFilters ?? {})
                };

                browser.on("open", (oldPath, newPath, resource) => {
                    if (resource && opts?.selection) {
                        const doShow = opts.selection.show ?? (() => true);
                        if (doShow(resource)) {
                            opts.selection.onClick(resource);
                        }
                        browser.open(oldPath, true);
                        return;
                    }

                    if (resource) {
                        navigate(AppRoutes.jobs.view(resource.id));
                        return;
                    }

                    callAPI(JobsApi.browse({
                        ...browser.browseFilters,
                        ...flags,
                    })).then(result => {
                        browser.registerPage(result, newPath, true);
                        browser.renderRows();
                        jobCache.updateCache(result);
                    });
                });

                browser.on("wantToFetchNextPage", async (path) => {
                    const result = await callAPI(
                        JobsApi.browse({
                            next: browser.cachedNext[path] ?? undefined,
                            ...browser.browseFilters,
                            ...flags,
                        })
                    );
                    browser.registerPage(result, path, false);
                    browser.renderRows();
                    jobCache.updateCache(result);
                });

                browser.on("fetchFilters", () => [{
                    key: "filterCreatedBy",
                    text: "Created by",
                    type: "input",
                    icon: "user"
                }, dateRanges, {
                    key: "filterState",
                    options: [
                        {text: "In queue", value: "IN_QUEUE", icon: "hashtag", color: "textPrimary"},
                        {text: "Running", value: "RUNNING", icon: "hashtag", color: "textPrimary"},
                        {text: "Success", value: "SUCCESS", icon: "check", color: "textPrimary"},
                        {text: "Failure", value: "FAILURE", icon: "close", color: "textPrimary"},
                        {text: "Expired", value: "EXPIRED", icon: "chrono", color: "textPrimary"},
                        {text: "Suspended", value: "SUSPENDED", icon: "pauseSolid", color: "textPrimary"},
                    ],
                    clearable: true,
                    text: "Status",
                    type: "options",
                    icon: "radioEmpty"
                }]);

                browser.on("renderRow", (job, row, dims) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    icon.style.minWidth = "20px"
                    icon.style.minHeight = "20px"
                    row.title.append(icon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(job.specification.name ?? job.id, dims, row));
                    if (!simpleView) {
                        row.stat1.innerText = job.owner.createdBy;
                        row.stat2.innerText = dateToString(job.createdAt ?? timestampUnixMs());
                    } else {
                        row.stat2.innerText = dateToDateStringOrTime(job.createdAt ?? timestampUnixMs());
                    }

                    logoDataUrls.retrieve(job.specification.application.name, async () => {
                        const result = await appLogoCache.fetchLogo(job.specification.application.name);
                        if (result !== null) {
                            return result;
                        }

                        return await browser.icons.renderSvg(
                            job.specification.application.name,
                            () => <AppLogo size="32px" hash={hashF(job.specification.application.name)} />,
                            32,
                            32
                        ).then(it => it).catch(e => {
                            console.log("render SVG error", e);
                            return "";
                        });
                    }).then(result => {
                        if (result) {
                            setIcon(result);
                        }
                    });

                    if (opts?.selection) {
                        const button = browser.defaultButtonRenderer(opts.selection, job);
                        if (button) {
                            row.stat3.replaceChildren(button);
                        }
                    } else {
                        const [status, setStatus] = ResourceBrowser.defaultIconRenderer();
                        const [statusIconName, statusIconColor] = JOB_STATE_AND_ICON_COLOR_MAP[job.status.state];
                        browser.icons.renderIcon({
                            name: statusIconName,
                            width: 32,
                            height: 32,
                            color: statusIconColor,
                            color2: statusIconColor
                        }).then(setStatus);
                        status.style.margin = "0";
                        status.style.width = "24px";
                        status.style.height = "24px";
                        row.stat3.append(status);
                    }
                });

                browser.setEmptyIcon("heroServer");
                browser.on("unhandledShortcut", () => void 0);
                browser.on("renderEmptyPage", reason => browser.defaultEmptyPage("jobs", reason, opts?.additionalFilters));
                browser.on("nameOfEntry", j => j.specification.name ?? j.id ?? "");
                browser.on("pathToEntry", j => j.id);
                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<Job> = {
                        api: JobsApi,
                        navigate: to => navigate(to),
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        onSelect: opts?.selection?.onClick,
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
                    return JobsApi.retrieveOperations().filter(op => op.enabled(entries, callbacks, entries)).concat(opts?.operations ?? []);
                });
                browser.on("generateBreadcrumbs", () => {
                    if (opts?.omitBreadcrumbs) return [];
                    return [{title: "Jobs", absolutePath: ""}]
                });
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
        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    const main = <>
        <div ref={mountRef} />
        {switcher}
    </>;
    if (opts?.embedded === true) return <div>{main}</div>;
    return <MainContainer main={main} />;
}

const JOB_STATE_AND_ICON_COLOR_MAP: Record<JobState, [IconName, ThemeColor]> = {
    IN_QUEUE: ["heroCalendar", "iconColor"],
    RUNNING: ["heroClock", "successMain"],
    SUCCESS: ["heroCheck", "successMain"],
    FAILURE: ["heroXMark", "errorMain"],
    EXPIRED: ["heroClock", "warningMain"],
    SUSPENDED: ["heroPause", "iconColor"],
    CANCELING: ["heroXMark", "errorMain"]
};

export default JobBrowse;
