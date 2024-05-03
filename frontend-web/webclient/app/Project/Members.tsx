import * as React from "react";
import {useCallback, useEffect, useMemo, useReducer, useState} from "react";
import {default as Api, ProjectInvite, ProjectInviteLink, UpdateInviteLinkRequest, useProjectId} from "./Api";
import {NavigateFunction, useLocation, useNavigate} from "react-router";
import {callAPI, callAPIWithErrorHandler, useCloudAPI} from "@/Authentication/DataHook";
import {bulkRequestOf, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {useAvatars} from "@/AvataaarLib/hook";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {useLoading, usePage} from "@/Navigation/Redux";
import {BulkResponse, FindByStringId, PageV2} from "@/UCloud";
import {Client} from "@/Authentication/HttpClientInstance";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {OldProjectRole, Project, ProjectRole} from ".";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {MembersContainer} from "@/Project/MembersUI";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import AppRoutes from "@/Routes";
import {useDispatch} from "react-redux";
import {dispatchSetProjectAction} from "./ReduxState";
import {addStandardDialog} from "@/UtilityComponents";

export function ProjectPageTitle(props: React.PropsWithChildren): JSX.Element {
    return <span style={{fontSize: "25px", marginLeft: "8px"}}>{props.children}</span>
}

// UI state management
// ================================================================================
type ProjectAction = AddToGroup | RemoveFromGroup | Reload | InspectGroup | InviteMember | ReloadInvites |
    RemoveInvite | FailedInvite | RenameGroup | CreateGroup | UpdateGroupWithId | RemoveGroup | ChangeRole |
    RemoveMember | CreateInviteLink;

interface Reload {
    type: "Reload";
    project: Project;
}

interface ReloadInvites {
    type: "ReloadInvites";
    invites: PageV2<ProjectInvite>;
}

interface RemoveMember {
    type: "RemoveMember";
    members: string[];
}

interface ChangeRole {
    type: "ChangeRole";
    changes: {username: string; role: ProjectRole}[];
}

interface AddToGroup {
    type: "AddToGroup";
    member: string;
    group: string;
}

interface RemoveFromGroup {
    type: "RemoveFromGroup";
    member: string;
    group: string;
}

interface InspectGroup {
    type: "InspectGroup";
    group: string | null;
}

interface RenameGroup {
    type: "RenameGroup";
    group: string;
    newTitle: string;
}

const placeholderPrefix = "ucloud-placeholder-id-";

let groupIdCounter = 0;

interface CreateGroup {
    type: "CreateGroup";
    title: string;
    placeholderId: number; // NOTE(Dan): Should contain an id allocated from `groupIdCounter`
}

interface UpdateGroupWithId {
    type: "UpdateGroupWithId";
    actualId: string;
    placeholderId: number;
}

interface RemoveGroup {
    type: "RemoveGroup";
    ids: string[];
}

interface InviteMember {
    type: "InviteMember";
    members: string[];
}

interface CreateInviteLink {
    type: "CreateInviteLink";
}

interface RemoveInvite {
    type: "RemoveInvite";
    members: string[];
}

interface FailedInvite {
    type: "FailedInvite";
    members: string[];
}

interface UIState {
    project: Project | null;
    invites: PageV2<ProjectInvite>;
}

function projectReducer(state: UIState, action: ProjectAction): UIState {
    const copy = deepCopy(state);
    switch (action.type) {
        case "Reload": {
            copy.project = action.project;
            return copy;
        }

        case "ReloadInvites": {
            copy.invites = action.invites;
            return copy;
        }
    }

    const project = copy.project;
    if (!project) return state;

    switch (action.type) {
        case "RemoveMember": {
            project.status.members = project.status.members!.filter(mem =>
                action.members.every(toRemove => mem.username !== toRemove));
            return copy;
        }

        case "ChangeRole": {
            for (const member of project.status.members!) {
                const change = action.changes.find(it => it.username === member.username);
                if (!change) continue;
                member.role = change.role;

                if (change.role === OldProjectRole.PI) {
                    project.status.myRole = OldProjectRole.ADMIN;
                    const me = project.status.members?.find(it => it.username === Client.username);
                    if (me) me.role = OldProjectRole.ADMIN;
                }

                // If we are changing our own role, make sure this is reflected in the status object.
                if (member.username === Client.username!) {
                    project.status.myRole = change.role;
                }
            }
            return copy;
        }

        case "AddToGroup": {
            const g = project.status.groups!.find(it => it.id === action.group);
            if (!g) return state;
            g.status.members!.push(action.member);
            return copy;
        }

        case "RemoveFromGroup": {
            const g = project.status.groups!.find(it => it.id === action.group);
            if (!g) return state;
            g.status.members = g.status.members!.filter(gm => gm !== action.member);
            return copy;
        }

        case "RenameGroup": {
            const g = project.status.groups!.find(it => it.id === action.group);
            if (!g) return state;

            g.specification.title = action.newTitle;
            return copy;
        }

        case "CreateGroup": {
            project.status.groups = [
                {
                    id: placeholderPrefix + action.placeholderId,
                    createdAt: timestampUnixMs(),
                    specification: {
                        project: project.id,
                        title: action.title
                    },
                    status: {
                        members: []
                    }
                },
                ...project.status.groups!
            ];

            return copy;
        }

        case "RemoveGroup": {
            project.status.groups = project.status.groups!
                .filter(it => action.ids.every(beingRemoved => it.id !== beingRemoved));
            return copy;
        }

        case "UpdateGroupWithId": {
            const g = project.status.groups!.find(it => it.id === placeholderPrefix + action.placeholderId);
            if (!g) return state;

            g.id = action.actualId;
            return copy;
        }

        case "InviteMember": {
            for (const member of action.members) {
                copy.invites.items.push({
                    recipient: member,
                    createdAt: timestampUnixMs(),
                    invitedTo: project.id,
                    invitedBy: Client.username!,
                    projectTitle: project.specification.title
                });
            }

            return copy;
        }

        case "FailedInvite":
        case "RemoveInvite": {
            copy.invites.items = copy.invites.items.filter(invite =>
                action.members.every(removed => invite.recipient != removed)
            );
            return copy;
        }
    }
    return state;
}

// NOTE(Dan): Implements side effects which need to occur based on an action. This typically involves sending commands
// to the backend which reflect the desired change.
async function onAction(state: UIState, action: ProjectAction, cb: ActionCallbacks): Promise<void> {
    const {project} = state;
    if (!project) return;
    switch (action.type) {
        case "RemoveMember": {
            const success = await callAPIWithErrorHandler({
                ...Api.deleteMember(bulkRequestOf(...action.members.map(it => ({username: it})))),
                projectOverride: project.id
            });

            const containsMyself = action.members.some(it => it === Client.username);
            if (containsMyself && Client.projectId === project.id) {
                cb.clearProject();
                cb.navigate(AppRoutes.dashboard.dashboardB());
            }

            // NOTE(Dan): Something is probably really wrong if this happens. Just reload the entire thing.
            if (!success) cb.requestReload();
            break;
        }

        case "ChangeRole": {
            // NOTE(Dan): We can only change our own role, if we are promoting someone else to a PI. As a result,
            // such a change is meant only for the frontend, and not the backend. The backend will implicitly perform
            // this change for us, since there can be only one PI.
            const success = await callAPIWithErrorHandler({
                ...Api.changeRole(bulkRequestOf(...action.changes)),
                projectOverride: project.id
            });

            if (!success) {
                const oldRoles: {username: string, role: ProjectRole}[] = [];
                for (const member of project.status.members!) {
                    const hasChange = action.changes.some(it => it.username === member.username);
                    if (hasChange) {
                        oldRoles.push(member);
                    }
                }

                cb.pureDispatch({type: "ChangeRole", changes: oldRoles});
            }
            break;
        }

        case "AddToGroup": {
            const success = await callAPIWithErrorHandler({
                ...Api.createGroupMember(bulkRequestOf({group: action.group, username: action.member})),
                projectOverride: project.id
            }) != null;

            if (!success) {
                cb.pureDispatch({type: "RemoveFromGroup", group: action.group, member: action.member});
            }
            break;
        }

        case "RemoveFromGroup": {
            const success = await callAPIWithErrorHandler({
                ...Api.deleteGroupMember(bulkRequestOf({group: action.group, username: action.member})),
                projectOverride: project.id
            }) != null;

            if (!success) {
                cb.pureDispatch({type: "AddToGroup", group: action.group, member: action.member});
            }
            break;
        }

        case "RenameGroup": {
            const currentTitle = state.project?.status?.groups
                ?.find(it => it.id === action.group)?.specification?.title;

            const success = await callAPIWithErrorHandler({
                ...Api.renameGroup(bulkRequestOf({group: action.group, newTitle: action.newTitle})),
                projectOverride: project.id
            });

            if (!success && currentTitle) {
                cb.pureDispatch({type: "RenameGroup", group: action.group, newTitle: currentTitle});
            }
            break;
        }

        case "CreateGroup": {
            const ids = await callAPIWithErrorHandler<BulkResponse<FindByStringId>>({
                ...Api.createGroup(bulkRequestOf({project: project.id, title: action.title})),
                projectOverride: project.id
            });

            if (!ids || ids.responses.length === 0) {
                cb.pureDispatch({type: "RemoveGroup", ids: [placeholderPrefix + action.placeholderId]});
            } else {
                const id = ids.responses[0].id;
                cb.pureDispatch({type: "UpdateGroupWithId", actualId: id, placeholderId: action.placeholderId});
            }
            break;
        }

        case "RemoveGroup": {
            const success = await callAPIWithErrorHandler({
                ...Api.deleteGroup(bulkRequestOf(...action.ids.map(id => ({id})))),
                projectOverride: project.id
            });

            if (!success) {
                cb.requestReload();
            }
            break;
        }

        case "InspectGroup": {
            cb.navigate(buildQueryString(`/projects/members`, {group: action.group ?? undefined}));
            break;
        }

        case "InviteMember": {
            const success = await callAPIWithErrorHandler({
                ...Api.createInvite(bulkRequestOf(...action.members.map(it => ({recipient: it})))),
                projectOverride: project.id
            }) != null;

            if (!success) {
                cb.pureDispatch({type: "FailedInvite", members: action.members});
            }
            break;
        }

        case "RemoveInvite": {
            await callAPIWithErrorHandler(
                Api.deleteInvite(bulkRequestOf(...action.members.map(it => ({username: it, project: project.id}))))
            );
        }
    }
}

interface ActionCallbacks {
    navigate: NavigateFunction;
    pureDispatch: (action: ProjectAction) => void;
    requestReload: () => void; // NOTE(Dan): use when it is difficult to rollback a change
    clearProject: () => void; // NOTE(Jonas): When you leave the active project
}

// Primary user interface
// ================================================================================
export const ProjectMembers2: React.FunctionComponent = () => {
    // Input "parameters"
    const navigate = useNavigate();
    const projectId = useProjectId() ?? "";
    const location = useLocation();
    const groupIdParam = getQueryParam(location.search, "groupId");

    // Remote data
    const [invitesFromApi, fetchInvites] = useCloudAPI<PageV2<ProjectInvite>>({noop: true}, emptyPageV2);
    const [projectFromApi, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    const avatars = useAvatars();

    // UI state
    const [uiState, pureDispatch] = useReducer(projectReducer, {project: null, invites: emptyPageV2});
    const {project, invites} = uiState;
    const [sortUpdate, setSortUpdate] = useState("");
    const reduxDispatch = useDispatch();


    const [memberQuery, setMemberQuery] = useState<string>("");

    // UI callbacks and state manipulation
    const reload = useCallback(() => {
        fetchProject(Api.retrieve({
            id: projectId,
            includePath: true,
            includeMembers: true,
            includeArchived: true,
            includeGroups: true,
        }));

        fetchInvites({
            ...Api.browseInvites({
                itemsPerPage: 50,
                filterType: "OUTGOING",
            }),
            projectOverride: projectId
        });
    }, [projectId]);

    const actionCb: ActionCallbacks = useMemo(() => ({
        navigate,
        pureDispatch,
        requestReload: reload,
        clearProject: () => {
            dispatchSetProjectAction(reduxDispatch, undefined);
        }
    }), [pureDispatch, reload]);

    const dispatch = useCallback((action: ProjectAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);


    const roleToOrder: Record<ProjectRole, number> = {
        PI: 0,
        ADMIN: 1,
        USER: 2
    };

    React.useEffect(() => {
        if (!Client.hasActiveProject) {
            navigate(AppRoutes.dashboard.dashboardA());
        }
    }, [])

    const modifiedProject: Project | null = useMemo(() => {
        if (!project) return project;

        const allMembers = project.status.members!;
        const normalizedQuery = memberQuery.trim().toLowerCase();

        let relevantMembers = allMembers.filter(m => normalizedQuery === "" || m.username.toLowerCase().indexOf(normalizedQuery) != -1);
        relevantMembers = relevantMembers.sort((a, b) => {
            if (sortUpdate === "name") {
                return a.username.localeCompare(b.username);
            } else if (sortUpdate === "role") {
                const aOrder = roleToOrder[a.role];
                const bOrder = roleToOrder[b.role];
                if (aOrder > bOrder) {
                    return 1;
                } else if (aOrder < bOrder) {
                    return -1;
                } else {
                    return a.username.localeCompare(b.username);
                }
            } else {
                return a.username.localeCompare(b.username);
            }
        });
        return {
            ...project,
            status: {
                ...project.status,
                members: relevantMembers,
            }
        };
    }, [project, memberQuery, sortUpdate]);

    // Effects
    useEffect(() => reload(), [reload]);

    useEffect(() => {
        if (!projectFromApi.data) return;
        avatars.updateCache(projectFromApi.data.status.members!.map(it => it.username));
        dispatch({type: "Reload", project: projectFromApi.data});
    }, [projectFromApi.data]);

    useEffect(() => {
        if (!invitesFromApi.data) return;
        avatars.updateCache(invitesFromApi.data.items.map(it => it.recipient));
        dispatch({type: "ReloadInvites", invites: invitesFromApi.data});
    }, [invitesFromApi.data]);

    usePage("Member and Group Management", SidebarTabId.WORKSPACE);
    useSetRefreshFunction(reload);
    useLoading(projectFromApi.loading || invitesFromApi.loading);

    const [inviteLinks, setInviteLinks] = useState<ProjectInviteLink[]>([]);

    useEffect(() => {
        let didCancel = false;
        (async () => {
            const links = await fetchAll(next => {
                return callAPI<PageV2<ProjectInviteLink>>({
                    ...Api.browseInviteLinks({itemsPerPage: 250, next}),
                    projectOverride: projectId,
                });
            });

            if (!didCancel) setInviteLinks(links);
        })();
        return () => {
            didCancel = true;
        };
    }, []);

    function updateLink(linkId: string, newGroups: string[] | null, newRole: ProjectRole | null) {
        const oldLink = inviteLinks.find(it => it.token === linkId);
        const newLink: UpdateInviteLinkRequest = {
            token: linkId,
            role: newRole ?? oldLink?.roleAssignment ?? OldProjectRole.USER,
            groups: newGroups ?? oldLink?.groupAssignment ?? [],
        };

        callAPI(Api.updateInviteLink(newLink)).then(doNothing);
        setInviteLinks(prev => {
            return prev.map(it => {
                if (it.token === newLink.token) {
                    return {
                        token: newLink.token,
                        groupAssignment: newLink.groups,
                        roleAssignment: newLink.role as ProjectRole,
                        expires: oldLink?.expires ?? 0,
                    };
                } else {
                    return it;
                }
            });
        });
    }

    if (!modifiedProject) return null;

    const activeGroup = (modifiedProject.status.groups ?? [])
        .find(it => it.id === groupIdParam) ?? null;

    return <MembersContainer
        onSortUpdated={setSortUpdate}
        currentSortOption={sortUpdate}
        onInvite={username => {
            avatars.updateCache([username]);
            dispatch({type: "InviteMember", members: [username]});
        }}
        onSearch={query => {
            setMemberQuery(query);
        }}
        onAddToGroup={(username, groupId) => {
            dispatch({type: "AddToGroup", member: username, group: groupId});
        }}
        onRemoveFromGroup={(username, groupId) => {
            dispatch({type: "RemoveFromGroup", member: username, group: groupId});
        }}
        onCreateGroup={(groupTitle) => {
            dispatch({type: "CreateGroup", title: groupTitle, placeholderId: groupIdCounter++});
        }}
        onDeleteGroup={(groupId) => {
            dispatch({type: "RemoveGroup", ids: [groupId]});
        }}
        onChangeRole={(username, newRole) => {
            dispatch({type: "ChangeRole", changes: [{username: username, role: newRole}]});
        }}
        onRemoveFromProject={(username) => {
            const isSelf = username === Client.activeUsername;
            if (isSelf) {
                addStandardDialog({
                    title: "Leave project?",
                    message: "Clicking 'confirm' will leave the project. To return to the project, you must be re-invited.",
                    onConfirm: () => dispatch({type: "RemoveMember", members: [username]})
                });
            } else {
                dispatch({type: "RemoveMember", members: [username]});
            }
        }}
        onCreateInviteLink={async () => {
            const link = await callAPI(Api.createInviteLink());
            setInviteLinks(prev =>
                [...prev, link]
            );
        }}
        onDeleteLink={linkId => {
            callAPI(Api.deleteInviteLink({token: linkId})).then(doNothing);
            setInviteLinks(prev => {
                return prev.filter(it => it.token !== linkId)
            });
        }}
        onLinkGroupsUpdated={(linkId, groupIds) => {
            updateLink(linkId, groupIds, null);
        }}
        onUpdateLinkRole={(linkId, role) => {
            updateLink(linkId, null, role);
        }}
        onRenameGroup={(groupId, newTitle) => {
            dispatch({type: "RenameGroup", group: groupId, newTitle});
        }}
        onRemoveInvite={username => {
            dispatch({type: "RemoveInvite", members: [username]});
        }}
        onDuplicate={async (groupId: string) => {
            if (project === null) return;
            const group = modifiedProject?.status.groups?.find((group) => group.id === groupId);
            if (group === undefined) return;
            for (let i = 1; i < 100; i += 1) {
                let ids: BulkResponse<FindByStringId>;
                try {
                    ids = await callAPI<BulkResponse<FindByStringId>>({
                        ...Api.createGroup(bulkRequestOf({
                            project: project.id,
                            title: `${group.specification.title} (${i})`
                        })),
                        projectOverride: project.id
                    });
                } catch (e) {
                    continue;
                }
                const newId = ids.responses[0].id;

                const newGroupMembers = group.status.members!.map((member) => {
                    return {group: newId, username: member};
                });

                if (newGroupMembers.length === 0) {
                    reload();
                    return;
                }

                const success = await callAPIWithErrorHandler({
                    ...Api.createGroupMember(bulkRequestOf(...newGroupMembers)),
                    projectOverride: project.id
                }) != null;
                if (!success) {
                    snackbarStore.addFailure("Could not duplicate group", false);
                    return;
                }
                reload();
                break;
            }
        }}
        onRefresh={reload}
        invitations={invites.items}
        project={modifiedProject}
        activeGroup={activeGroup}
        inviteLinks={inviteLinks}
    />;
};

export default ProjectMembers2;
