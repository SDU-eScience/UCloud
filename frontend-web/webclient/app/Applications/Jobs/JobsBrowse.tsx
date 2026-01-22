import {callAPI as baseCallAPI} from "@/Authentication/DataHook";
import MainContainer from "@/ui-components/MainContainer";
import Text from "@/ui-components/Text";
import {usePage} from "@/Navigation/Redux";
import JobsApi, {Job, JobState} from "@/UCloud/JobsApi";
import {dateToDateStringOrTime, dateToString} from "@/Utilities/DateUtilities";
import {
    bulkRequestOf,
    doNothing, extractErrorMessage,
    isLightThemeStored,
    stopPropagationAndPreventDefault,
    timestampUnixMs
} from "@/UtilityFunctions";
import {
    addProjectSwitcherInPortal,
    checkIsWorkspaceAdmin,
    clearFilterStorageValue,
    dateRangeFilters,
    ResourceBrowseFeatures,
    ResourceBrowser,
    ResourceBrowserOpts,
    ColumnTitleList,
    checkCanConsumeResources,
} from "@/ui-components/ResourceBrowser";
import * as React from "react";
import Icon, {IconName} from "@/ui-components/Icon";
import {ThemeColor} from "@/ui-components/theme";
import {useNavigate} from "react-router-dom";
import {ResourceBrowseCallbacks} from "@/UCloud/ResourceApi";
import {useDispatch} from "react-redux";
import AppRoutes from "@/Routes";
import {Operation} from "@/ui-components/Operation";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {jobCache} from "./View";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as AppStore from "@/Applications/AppStoreApi";
import {Client} from "@/Authentication/HttpClientInstance";
import {getStoredProject} from "@/Project/ReduxState";
import {filterOption} from "@/ui-components/ResourceBrowserFilters";
import {useProject} from "@/Project/cache";
import {avatarState, useAvatars} from "@/AvataaarLib/hook";
import {Box, Flex, Input, Relative, Truncate} from "@/ui-components";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {createPortal} from "react-dom";
import {AvatarType} from "@/AvataaarLib";
import {FilterInputClass} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {injectStyle} from "@/Unstyled";
import {Feature, hasFeature} from "@/Features";
import {SimpleAvatarComponentCache} from "@/Files/Shares";
import {divText} from "@/Utilities/HTMLUtilities";
import {TruncateClass} from "@/ui-components/Truncate";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

const defaultRetrieveFlags: {itemsPerPage: number} = {
    itemsPerPage: 250,
};

const FEATURES: ResourceBrowseFeatures = {
    renderSpinnerWhenLoading: true,
    filters: true,
    sorting: true,
    breadcrumbsSeparatedBySlashes: false,
    dragToSelect: true,
    projectSwitcher: true,
    search: true,
    showColumnTitles: true,
};

const columnTitles: ColumnTitleList = [{name: "Job name"}, {name: "Created by", sortById: "createdBy", columnWidth: 250}, {name: "Created at", sortById: "createdAt", columnWidth: 160}, {name: "Time left", sortById: "timeLeft", columnWidth: 160}, {name: "State", columnWidth: 75}];
const simpleViewColumnTitles: ColumnTitleList = [{name: ""}, {name: "", sortById: "", columnWidth: 0}, {name: "", sortById: "", columnWidth: 160}, {name: "", sortById: "", columnWidth: 0}, {name: "State", columnWidth: 28}];


const RESOURCE_NAME = "JOBS";
function JobBrowse({opts}: {opts?: ResourceBrowserOpts<Job> & {omitBreadcrumbs?: boolean; operations?: Operation<Job, ResourceBrowseCallbacks<Job>>[]}}): React.ReactNode {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<Job> | null>(null);
    const navigate = useNavigate();
    const dispatch = useDispatch();
    const activeProject = React.useRef<string | null | undefined>(Client.projectId);
    const [switcher, setSwitcherWorkaround] = React.useState<React.ReactNode>(<></>);

    const filterCreatedBy = React.useRef("");
    const [projectMemberList, setProjectMemberList] = React.useState<React.ReactNode>(<></>);

    if (!opts?.embedded && !opts?.isModal) {
        usePage("Jobs", SidebarTabId.RUNS);
    }

    function callAPI<T>(parameters: APICallParameters<unknown, T>): Promise<T> {
        if (!opts?.isModal) activeProject.current = getStoredProject();
        return baseCallAPI({
            ...parameters,
            projectOverride: activeProject.current ?? ""
        });
    }

    const hideFilters = !!opts?.embedded?.hideFilters;

    const features: ResourceBrowseFeatures = {
        ...FEATURES,
        filters: !hideFilters,
        showHeaderInEmbedded: !!opts?.selection,
        sorting: !hideFilters,
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

                    if (filterCreatedBy.current) {
                        browser.browseFilters["filterCreatedBy"] = filterCreatedBy.current;
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
                    if (filterCreatedBy.current) {
                        browser.browseFilters["filterCreatedBy"] = filterCreatedBy.current;
                    }

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

                browser.on("fetchFilters", () => [
                    dateRanges, {
                        key: "filterState",
                        options: [
                            filterOption("In queue", "IN_QUEUE", "hashtag", "textPrimary"),
                            filterOption("Running", "RUNNING", "hashtag", "textPrimary",),
                            filterOption("Success", "SUCCESS", "check", "textPrimary"),
                            filterOption("Failure", "FAILURE", "close", "textPrimary"),
                            filterOption("Expired", "EXPIRED", "chrono", "textPrimary"),
                            filterOption("Suspended", "SUSPENDED", "pauseSolid", "textPrimary"),
                        ],
                        clearable: true,
                        text: "Status",
                        type: "options",
                        icon: "radioEmpty"
                    }]);

                browser.on("endRenderPage", () => {
                    SimpleAvatarComponentCache.fetchMissingAvatars();
                });

                avatarState.subscribe(() => {
                    browser.rerender();
                });

                browser.on("renderRow", (job, row, dims) => {
                    const [icon, setIcon] = ResourceBrowser.defaultIconRenderer();
                    icon.style.minWidth = "20px"
                    icon.style.minHeight = "20px"
                    row.title.append(icon);

                    row.title.append(ResourceBrowser.defaultTitleRenderer(job.specification.name ?? job.id, row));
                    if (!simpleView) {
                        if (job.owner.createdBy === "_ucloud") {
                            row.stat1.innerHTML = "";
                            const elem = document.createElement("i");
                            elem.innerText = "Unknown";
                            row.stat1.append(elem);
                        } else {
                            row.stat1.style.justifyContent = "center";
                            SimpleAvatarComponentCache.appendTo(row.stat1, job.owner.createdBy, `Started by ${job.owner.createdBy}`).then(wrapper => {
                                const div = divText(job.owner.createdBy);
                                div.style.marginTop = div.style.marginBottom = "auto";
                                div.classList.add(TruncateClass);
                                div.style.maxWidth = "150px";
                                div.style.marginLeft = "12px";
                                wrapper.append(div);
                                wrapper.style.display = "flex";
                            });
                        }
                        row.stat2.innerText = dateToString(job.createdAt ?? timestampUnixMs());
                    } else {
                        row.stat2.innerText = dateToDateStringOrTime(job.createdAt ?? timestampUnixMs());
                    }
                    
                    // Time left in stat3
                    if (!simpleView && job.status.expiresAt) {
                        const now = timestampUnixMs();
                        const timeLeft = job.status.expiresAt - now;
                        if (timeLeft > 0) {
                            const hours = Math.floor(timeLeft / (1000 * 60 * 60));
                            const minutes = Math.floor((timeLeft % (1000 * 60 * 60)) / (1000 * 60));
                            if (hours > 24) {
                                const days = Math.floor(hours / 24);
                                row.stat3.innerText = `${days}d ${hours % 24}h`;
                            } else if (hours > 0) {
                                row.stat3.innerText = `${hours}h ${minutes}m`;
                            } else {
                                row.stat3.innerText = `${minutes}m`;
                            }
                        } else {
                            row.stat3.innerText = "Expired";
                        }
                    } else if (!simpleView) {
                        row.stat3.innerText = "N/A";
                    }
                    
                    setIcon(AppStore.retrieveAppLogo({
                        name: job.specification.application.name,
                        darkMode: !isLightThemeStored(),
                        includeText: false,
                        placeTextUnderLogo: false,
                    }));

                    if (opts?.selection) {
                        const button = browser.defaultButtonRenderer(opts.selection, job);
                        if (button) {
                            row.stat4.replaceChildren(button);
                        }
                    } else {
                        const [status, setStatus] = ResourceBrowser.defaultIconRenderer();
                        const [statusIconName, statusIconColor] = JOB_STATE_AND_ICON_COLOR_MAP[job.status.state];
                        ResourceBrowser.icons.renderIcon({
                            name: statusIconName,
                            width: 32,
                            height: 32,
                            color: statusIconColor,
                            color2: statusIconColor
                        }).then(setStatus);
                        status.style.margin = "0";
                        status.style.width = "24px";
                        status.style.height = "24px";
                        status.style.marginTop = status.style.marginBottom = "auto";
                        row.stat4.append(status);
                    }
                });

                const startRenaming = hasFeature(Feature.JOB_RENAME) ? (resource: Job) => {
                    browser.showRenameField(
                        it => it.id === resource.id,
                        () => {
                            const oldTitle = resource.specification.name;
                            const page = browser.cachedData["/"] ?? [];
                            const job = page.find(it => it.id === resource.id);
                            if (job) {
                                job.specification.name = browser.renameValue;
                                browser.dispatchMessage("sort", fn => fn(page));
                                browser.renderRows();
                                browser.selectAndShow(it => it.id === job.id);

                                callAPI(JobsApi.rename(bulkRequestOf({
                                    id: job.id,
                                    newTitle: job.specification.name,
                                }))).catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    browser.refresh();
                                });

                                if (hasFeature(Feature.COMPONENT_STORED_CUT_COPY)) {
                                    ResourceBrowser.addUndoAction(RESOURCE_NAME, () => {
                                        callAPI(JobsApi.rename(bulkRequestOf({
                                            id: job.id,
                                            newTitle: oldTitle ?? ""
                                        })));

                                        job.specification.name = oldTitle;
                                        browser.dispatchMessage("sort", fn => fn(page));
                                        browser.renderRows();
                                        browser.selectAndShow(it => it.id === job.id);
                                    });
                                } else {
                                    browser._undoStack.unshift(() => {
                                        callAPI(JobsApi.rename(bulkRequestOf({
                                            id: job.id,
                                            newTitle: oldTitle ?? ""
                                        })));

                                        job.specification.name = oldTitle;
                                        browser.dispatchMessage("sort", fn => fn(page));
                                        browser.renderRows();
                                        browser.selectAndShow(it => it.id === job.id);
                                    });
                                }
                            }
                        },
                        doNothing,
                        resource.specification.name ?? "",
                    );
                } : undefined;


                browser.setEmptyIcon("heroServer");
                browser.on("unhandledShortcut", () => void 0);
                browser.on("renderEmptyPage", reason => browser.defaultEmptyPage("jobs", reason, opts?.additionalFilters));
                browser.on("nameOfEntry", j => j.specification.name ?? j.id ?? "");
                browser.on("pathToEntry", j => j.id);
                browser.on("fetchOperationsCallback", () => {
                    const support = {productsByProvider: {}};
                    const callbacks: ResourceBrowseCallbacks<Job> & {isModal: boolean} = {
                        api: JobsApi,
                        navigate: to => navigate(to),
                        commandLoading: false,
                        invokeCommand: call => callAPI(call),
                        onSelect: opts?.selection?.onClick,
                        isModal: !!opts?.isModal,
                        isCreating: false,
                        dispatch: dispatch,
                        supportByProvider: support,
                        reload: () => browser.refresh(),
                        isWorkspaceAdmin: checkIsWorkspaceAdmin(),
                        viewProperties: j => {
                            navigate(AppRoutes.jobs.view(j.id))
                        },
                        startRenaming(resource, defaultValue) {
                            startRenaming?.(resource);
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
                        flags,
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

        if (browserRef.current) {
            const f = browserRef.current.sessionFilters;
            setProjectMemberList(createPortal(<ProjectMemberFilter onSelect={member => {
                const b = browserRef.current;
                if (b) {
                    filterCreatedBy.current = member;
                    b.refresh();
                }
            }} />, f));
        }

        if (opts?.reloadRef) {
            opts.reloadRef.current = () => {
                browserRef.current?.refresh();
            }
        }

        addProjectSwitcherInPortal(browserRef, setSwitcherWorkaround, setLocalProject ? {setLocalProject} : undefined);

        const b = browserRef.current;
        if (b) {
            b.renameField.style.left = "43px";
        }
    }, []);

    const setLocalProject = opts?.isModal ? (projectId?: string) => {
        const b = browserRef.current;
        if (b) {
            b.canConsumeResources = checkCanConsumeResources(projectId ?? null, {api: JobsApi});
            activeProject.current = projectId;
            b.refresh();
        }
    } : undefined;

    if (!opts?.embedded && !opts?.isModal) {
        useSetRefreshFunction(() => {
            browserRef.current?.refresh();
        });
    }

    const main = <>
        <div ref={mountRef} />
        {switcher}
        {projectMemberList}
    </>;

    if (opts?.embedded) return <div>{main}</div>;
    return <MainContainer main={main} />;
}

const JOB_STATE_AND_ICON_COLOR_MAP: Record<JobState, [IconName, ThemeColor]> = {
    IN_QUEUE: ["heroCalendar", "iconColor"],
    RUNNING: ["heroClock", "successMain"],
    SUCCESS: ["heroCheck", "successMain"],
    FAILURE: ["close", "errorMain"],
    EXPIRED: ["heroClock", "warningMain"],
    SUSPENDED: ["pauseSolid", "iconColor"],
    CANCELING: ["close", "errorMain"]
};

export function ProjectMemberFilter({onSelect}: {onSelect: (username: string) => void}) {
    const [activeUser, setActiveUser] = React.useState("");
    const [filter, setFilter] = React.useState("");

    const p = useProject();
    const project = p.fetch();
    const projectId = useProjectId();
    const members = project.status.members?.map(it => it.username) ?? [Client.username!];
    const avatars = useAvatars();

    const setFilterMember = React.useCallback((member: string) => {
        setFilter("");
        onSelect(member);
        setActiveUser(member);
    }, []);

    React.useEffect(() => {
        setFilterMember("")
    }, [projectId]);

    return <ClickableDropdown
        arrowkeyNavigationKey="data-user"
        onSelect={el => {
            const user = el?.getAttribute("data-user");
            if (user) setFilterMember(user);
        }}
        paddingControlledByContent
        colorOnHover={false}
        useMousePositioning
        trigger={
            <Flex my="auto">
                <Icon size="12px" my="auto" mr="10px" name="user" color="textPrimary" />
                <Text>Created by </Text>
                {activeUser ?
                    <Flex pr="8px">
                        <UserAvatar width={"24px"} height={"24px"} avatar={avatars.avatar(activeUser)} />
                        <Truncate title={activeUser} fontSize={"16px"}>{activeUser}</Truncate>
                    </Flex> :
                    null
                }
            </Flex>
        }
    >
        <Flex>
            <Input
                autoFocus
                className={FilterInputClass}
                placeholder="Search for a person..."
                defaultValue={filter}
                onClick={stopPropagationAndPreventDefault}
                enterKeyHint="enter"
                onKeyDown={e => {
                    if (["Escape"].includes(e.code) && e.target["value"]) {
                        setFilter("");
                        e.target["value"] = "";
                    }
                    e.stopPropagation();
                }}
                onKeyUp={e => {
                    e.stopPropagation();
                    setFilter("value" in e.target ? e.target.value as string : "");
                }}
                type="text"
            />
            <Relative right="24px" top="5px" width="0px" height="0px">
                <Icon name="search" />
            </Relative>
        </Flex>
        <Box maxHeight={"450px"} overflowY="auto">
            {activeUser ?
                <Flex
                    className={HoverClass}
                    height={"32px"}
                    onClick={() => setFilterMember("")}
                >
                    <Icon ml="12px" mt="8px" size="16px" color="errorMain" name="close" />
                    <Truncate ml="11px" style={{alignContent: "center"}} fontSize={"16px"}>
                        Clear
                    </Truncate>
                </Flex> :
                null
            }
            {members.filter(it => it.includes(filter)).map(it => <UserRow key={it} username={it} setMember={setFilterMember} avatar={avatars.avatar(it)} />)}
        </Box>
    </ClickableDropdown>
}

function UserRow({username, setMember, avatar, size = "24px"}: {username: string; setMember(name: string): void; avatar: AvatarType, size?: string}) {
    return <Flex className={HoverClass} pr="8px" data-user={username} onClick={() => setMember(username)} py={"4px"}>
        <UserAvatar width={size} height={size} avatar={avatar} />
        {" "}
        <Truncate title={username} my="auto" fontSize={"16px"}>{username}</Truncate>
    </Flex>
}

const HoverClass = injectStyle("hover-color", k => `
    ${k}:hover {
        background: var(--rowHover); 
    }
`);

export default JobBrowse;
