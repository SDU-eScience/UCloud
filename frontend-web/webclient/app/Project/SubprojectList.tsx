import MainContainer from "@/MainContainer/MainContainer";
import * as React from "react";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Icon} from "@/ui-components";
import {createHTMLElements, doNothing, errorMessageOrDefault, extractErrorMessage} from "@/UtilityFunctions";
import {Operation} from "@/ui-components/Operation";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useDispatch} from "react-redux";
import {dispatchSetProjectAction} from "./Redux";
import api, {isAdminOrPI, OldProjectRole, Project, projectRoleToStringIcon, useProjectId} from "./Api";
import ProjectAPI from "@/Project/Api";
import {bulkRequestOf} from "@/DefaultObjects";
import {PaginationRequestV2} from "@/UCloud";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {EmptyReasonTag, ResourceBrowseFeatures, ResourceBrowser, ResourceBrowserOpts, SelectionMode, addContextSwitcherInPortal} from "@/ui-components/ResourceBrowser";
import {ReactStaticRenderer} from "@/Utilities/ReactStaticRenderer";

// Note(Jonas): Endpoint missing from ProjectV2-api
type ListSubprojectsRequest = PaginationRequestV2;
function listSubprojects(parameters: ListSubprojectsRequest): APICallParameters<ListSubprojectsRequest> {
    return ({
        method: "GET",
        path: buildQueryString(
            "/projects/sub-projects",
            parameters
        ),
        parameters,
        reloadId: Math.random()
    });
}

export interface OldProject {
    id: string;
    title: string;
    parent?: string;
    archived: boolean;
    fullPath?: string;
}

interface MemberInProject {
    role?: OldProjectRole;
    project: OldProject;
}

const FEATURES: ResourceBrowseFeatures = {
    breadcrumbsSeparatedBySlashes: false,
    contextSwitcher: true,
    showColumnTitles: true,
};

const UserRoleIconCache: Record<OldProjectRole, ReactStaticRenderer | null> = {
    [OldProjectRole.ADMIN]: null,
    [OldProjectRole.PI]: null,
    [OldProjectRole.USER]: null
};

export default function SubprojectBrowse({opts}: {opts?: ResourceBrowserOpts<MemberInProject>}) {
    const mountRef = React.useRef<HTMLDivElement | null>(null);
    const browserRef = React.useRef<ResourceBrowser<MemberInProject> | null>(null);
    const dispatch = useDispatch();
    const projectId = useProjectId();
    const isPersonalWorkspace = !!projectId;
    const [project, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);

    const projectReload = React.useCallback(() => {
        if (!isPersonalWorkspace && projectId) {
            fetchProject(api.retrieve({
                id: projectId,
                includePath: true,
                includeMembers: true,
                includeArchived: true,
                includeGroups: true,
                includeSettings: true,
            }));
        }
    }, [projectId]);

    React.useEffect(() => {
        projectReload();
    }, [projectId]);


    const [switcher, setSwitcherWorkaround] = React.useState<JSX.Element>(<></>);
    useTitle("Subprojects");

    React.useLayoutEffect(() => {
        const mount = mountRef.current;
        if (mount && !browserRef.current) {
            if (UserRoleIconCache.ADMIN === null) {
                fillRoleIconCache();
            }

            new ResourceBrowser<MemberInProject>(mount, "Subprojects", opts).init(browserRef, FEATURES, "", browser => {

                browser.setColumnTitles([{name: "Project name"}, {name: ""}, {name: ""}, {name: "Archival status"}]);

                const startRenaming = (path: string) => {
                    const page = browser.cachedData["/"] ?? [];
                    const actualProject = page.find(it => it.project.id === path);

                    browser.showRenameField(
                        it => it.project.id === path,
                        () => {
                            if (!browser.renameValue) return; // No change
                            if (browser.renameValue.trim().length !== browser.renameValue.length) {
                                snackbarStore.addFailure("Subproject name cannot end or start with whitespace.", false);
                                return;
                            }
                            if (actualProject) {
                                const oldTitle = actualProject.project.title;
                                page.sort((a, b) => a.project.title.localeCompare(b.project.title));
                                const newRow = browser.findVirtualRowIndex(it => it.project.id === actualProject.project.id);
                                if (newRow != null) {
                                    browser.ensureRowIsVisible(newRow, true, true);
                                    browser.select(newRow, SelectionMode.SINGLE);
                                }

                                if (browser.renameValue === actualProject.project.title) return; // No change

                                callAPI(ProjectAPI.renameProject(bulkRequestOf(
                                    ...[{id: actualProject.project.id, newTitle: browser.renameValue}]
                                ))).catch(err => {
                                    snackbarStore.addFailure(extractErrorMessage(err), false);
                                    browser.refresh();
                                });

                                browser.undoStack.unshift(() => {
                                    callAPI(ProjectAPI.renameProject(bulkRequestOf(
                                        ...[{id: actualProject.project.id, newTitle: oldTitle}]
                                    ))).catch(err => {
                                        snackbarStore.addFailure(extractErrorMessage(err), false);
                                        browser.refresh();
                                    });

                                    actualProject.project.title = oldTitle;
                                    browser.renderRows();
                                });

                                actualProject.project.title = browser.renameValue;
                                browser.renderRows();
                            }
                        },
                        doNothing,
                        actualProject?.project.title ?? ""
                    );
                };

                browser.on("beforeOpen", (oldPath, newPath, res) => res != null);
                browser.on("open", async (oldPath, newPath, resource) => {
                    const result = await callAPI(listSubprojects({itemsPerPage: 250}));
                    browser.registerPage(result, "/", true);
                    browser.renderRows();
                });

                browser.on("unhandledShortcut", () => { });

                browser.on("wantToFetchNextPage", async path => {
                    const result = await callAPI(
                        listSubprojects({itemsPerPage: 250, next: browser.cachedNext[path] ?? undefined})
                    )

                    if (path !== browser.currentPath) return; // Shouldn't be possible.

                    browser.registerPage(result, "/", false);
                });

                browser.on("fetchFilters", () => []);

                browser.on("renderRow", (project, row, dims) => {
                    const title = project.project.title;

                    row.title.append(ResourceBrowser.defaultTitleRenderer(title, dims, row));
                    if (project.project.fullPath) row.title.title = project.project.fullPath;

                    if (project.project.archived) {
                        const [archiveIcon, setArchiveIcon] = ResourceBrowser.defaultIconRenderer();
                        row.stat3.appendChild(archiveIcon);
                        browser.icons.renderIcon({name: "tags", color: "black", color2: "black", height: 36, width: 36}).then(setArchiveIcon);
                    }

                    if (project.role) {
                        const roleIcon = UserRoleIconCache[project.role];
                        if (roleIcon != null) {
                            row.title.prepend(roleIcon.clone());
                        }
                    } else {
                        row.title.prepend(createHTMLElements({tagType: "div", style: {width: "28px", height: "20px"}}));
                    }
                });

                browser.on("generateBreadcrumbs", () => browser.defaultBreadcrumbs());
                browser.on("renderEmptyPage", reason => {
                    const e = browser.emptyPageElement;
                    switch (reason.tag) {
                        case EmptyReasonTag.LOADING: {
                            e.reason.append("We are fetching your subprojects...");
                            break;
                        }

                        case EmptyReasonTag.EMPTY: {
                            if (Object.values(browser.browseFilters).length !== 0)
                                e.reason.append("No license found with active filters.")
                            else e.reason.append("This workspace has no subprojects.");
                            break;
                        }

                        case EmptyReasonTag.NOT_FOUND_OR_NO_PERMISSIONS: {
                            e.reason.append("We could not find any data related to your subprojects.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }

                        case EmptyReasonTag.UNABLE_TO_FULFILL: {
                            e.reason.append("We are currently unable to show your subprojects. Try again later.");
                            e.providerReason.append(reason.information ?? "");
                            break;
                        }
                    }
                });

                browser.setEmptyIcon("heroUserGroup");

                browser.on("fetchOperationsCallback", (): {} => ({}));

                browser.on("fetchOperations", () => {
                    // View subprojects, rename, archive
                    const isAdminOrPIForParent = isAdminOrPI(project.data?.status.myRole);
                    const operations: Operation<MemberInProject, {}>[] = [{
                        text: "View subprojects",
                        icon: "projects",
                        enabled(entries): boolean {
                            return entries.length === 1 && entries[0].role != null;
                        },
                        onClick([entry]) {
                            dispatchSetProjectAction(dispatch, entry.project.id);
                        }
                    }, {
                        text: "Rename",
                        icon: "rename",
                        enabled(entries) {
                            if (entries.length !== 1) return false;
                            if (isAdminOrPIForParent || isAdminOrPI(entries[0].role)) {
                                return true;
                            } else {
                                return "Only Admins and PIs can rename.";
                            }
                        },
                        onClick([selected]) {
                            startRenaming(selected.project.id);
                        },
                    }, {
                        text: "Archive",
                        icon: "tags",
                        enabled(entries) {
                            return entries.length > 0 && entries.every(it => !it.project.archived && isAdminOrPI(it.role));
                        },
                        onClick(entries) {
                            onSetArchivedStatus(entries.map(it => it.project.id), true);
                        }
                    }, {
                        text: "Unarchive",
                        icon: "tags",
                        enabled(entries) {
                            return entries.length > 0 && entries.every(it => it.project.archived && isAdminOrPI(it.role));
                        },
                        onClick(entries) {
                            onSetArchivedStatus(entries.map(it => it.project.id), false);
                        }
                    }];

                    const selected = browser.findSelectedEntries();
                    const callbacks = browser.dispatchMessage("fetchOperationsCallback", fn => fn()) as {};
                    return operations.filter(it => it.enabled(selected, callbacks, selected));

                    async function onSetArchivedStatus(ids: string[], archive: boolean) {
                        const page = browser.cachedData["/"];

                        ids.forEach(id => {
                            const project = page.find(it => it.project.id === id);
                            if (project) project.project.archived = archive;
                        });

                        try {
                            const bulk = bulkRequestOf(...ids.map(id => ({id})));
                            const req = archive ? ProjectAPI.archive(bulk) : ProjectAPI.unarchive(bulk);
                            await callAPI(req);
                        } catch (e) {
                            const archiveString = archive ? "archive" : "unarchive"
                            ids.forEach(id => {
                                const project = page.find(it => it.project.id === id);
                                if (project) project.project.archived = !archive;
                            });
                            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to " + archiveString + " project"), false);
                            browser.renderRows();
                        }

                        browser.renderRows();
                    }
                });
                browser.on("pathToEntry", f => f.project.id);
                browser.on("nameOfEntry", f => f.project.title);
                browser.on("sort", page => page.sort((a, b) => a.project.title.localeCompare(b.project.title)));
            });
        }
        addContextSwitcherInPortal(browserRef, setSwitcherWorkaround);
    }, []);

    useRefreshFunction(() => {
        browserRef.current?.refresh();
    });

    if (!projectId) return null;

    return <MainContainer
        main={<>
            <div ref={mountRef} />
            {switcher}
        </>}
    />
}

function fillRoleIconCache() {
    new ReactStaticRenderer(() =>
        <RoleIconComp role={OldProjectRole.USER} />
    ).promise.then(it => UserRoleIconCache[OldProjectRole.USER] = it);
    new ReactStaticRenderer(() =>
        <RoleIconComp role={OldProjectRole.ADMIN} />
    ).promise.then(it => UserRoleIconCache[OldProjectRole.ADMIN] = it);
    new ReactStaticRenderer(() =>
        <RoleIconComp role={OldProjectRole.PI} />
    ).promise.then(it => UserRoleIconCache[OldProjectRole.PI] = it);
}

function RoleIconComp({role}: {role: OldProjectRole}): JSX.Element {
    return (
        <Icon
            size="20"
            squared={false}
            name={projectRoleToStringIcon(role)}
            color="gray"
            color2="midGray"
            mr=".5em"
        />
    )
}
