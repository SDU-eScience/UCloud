import * as React from "react";
import {useRef, useCallback, useEffect, useMemo, useReducer, useState} from "react";
import {default as Api, ProjectInvite, ProjectInviteLink, useProjectId} from "./Api";
import {NavigateFunction, useLocation, useNavigate} from "react-router";
import MainContainer from "@/ui-components/MainContainer";
import {callAPIWithErrorHandler, useCloudAPI} from "@/Authentication/DataHook";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {
    Absolute,
    Box,
    Button,
    Checkbox,
    Flex,
    Icon,
    Input,
    Label,
    List,
    RadioTile,
    RadioTilesContainer,
    Relative,
    Text,
    Tooltip, Truncate
} from "@/ui-components";
import {addStandardDialog, NamingField} from "@/UtilityComponents";
import {copyToClipboard, doNothing, preventDefault} from "@/UtilityFunctions";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {IconName} from "@/ui-components/Icon";
import {bulkRequestOf} from "@/UtilityFunctions";
import * as Heading from "@/ui-components/Heading";
import {ItemRenderer, ItemRow, useRenamingState} from "@/ui-components/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import BaseLink from "@/ui-components/BaseLink";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {usePage, useLoading} from "@/Navigation/Redux";
import {PageV2, BulkResponse, FindByStringId} from "@/UCloud";
import {Client} from "@/Authentication/HttpClientInstance";
import {timestampUnixMs} from "@/UtilityFunctions";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {dialogStore} from "@/Dialog/DialogStore";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {Spacer} from "@/ui-components/Spacer";
import {ListClass} from "@/ui-components/List";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {emptyPageV2} from "@/Utilities/PageUtilities";
import {OldProjectRole, Project, ProjectGroup, ProjectMember, ProjectRole, isAdminOrPI} from ".";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {MembersContainer} from "@/Project/MembersUI";

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
    changes: { username: string; role: ProjectRole }[];
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
                const oldRoles: { username: string, role: ProjectRole }[] = [];
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
}

// Primary user interface
// ================================================================================
export const ProjectMembers2: React.FunctionComponent = () => {
    // Input "parameters"
    const navigate = useNavigate();
    const projectId = useProjectId() ?? "";

    // Remote data
    const [invitesFromApi, fetchInvites] = useCloudAPI<PageV2<ProjectInvite>>({noop: true}, emptyPageV2);
    const [projectFromApi, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    const avatars = useAvatars();

    // UI state
    const [uiState, pureDispatch] = useReducer(projectReducer, {project: null, invites: emptyPageV2});
    const {project, invites} = uiState;

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

        fetchInvites(
            {
                ...Api.browseInvites({
                    itemsPerPage: 50,
                    filterType: "OUTGOING",
                }),
                projectOverride: projectId
            }
        );
    }, [projectId]);

    const actionCb: ActionCallbacks = useMemo(() => ({
        navigate,
        pureDispatch,
        requestReload: reload
    }), [pureDispatch, reload]);

    const dispatch = useCallback((action: ProjectAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);

    const modifiedProject: Project | null = useMemo(() => {
        if (!project) return project;

        const allMembers = project.status.members!;
        const normalizedQuery = memberQuery.trim().toLowerCase();
        if (normalizedQuery === "") return project;

        const relevantMembers = allMembers.filter(m => m.username.toLowerCase().indexOf(normalizedQuery) != -1);
        return {
            ...project,
            status: {
                ...project.status,
                members: relevantMembers,
            }
        };
    }, [project, memberQuery]);

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

    if (!modifiedProject) return null;

    return <MembersContainer
        onInvite={username => {
            avatars.updateCache([username]);
            dispatch({type: "InviteMember", members: [username]});
        }}
        onSearch={query => {
            setMemberQuery(query);
        }}
        onCreateLink={doNothing}
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
            dispatch({type: "RemoveMember", members: [username]});
        }}
        onRefresh={reload}
        invitations={invites.items}
        project={modifiedProject}
    />;
};

// Secondary interface (e.g. member rows and group rows)
// ================================================================================
function daysLeftToTimestamp(timestamp: number): number {
    return Math.floor((timestamp - timestampUnixMs()) / 1000 / 3600 / 24);
}

function inviteLinkFromToken(token: string): string {
    return window.location.origin + "/app/projects/invite/" + token;
}

const InviteLinkEditor: React.FunctionComponent<{
    project: Project,
    groups: (ProjectGroup | undefined)[]
}> = ({project, groups}) => {
    const [inviteLinksFromApi, fetchInviteLinks] = useCloudAPI<PageV2<ProjectInviteLink>>({noop: true}, emptyPageV2);
    const [editingLink, setEditingLink] = useState<string | undefined>(undefined);
    const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
    const [selectedRole, setSelectedRole] = useState<string>("USER");

    const roles = [
        {text: "User", value: "USER"},
        {text: "Admin", value: "ADMIN"}
    ];

    const groupItems = groups.map(g =>
        g ? {text: g.specification.title, value: g.id} : null
    ).filter(g => g?.text != "All users");

    useEffect(() => {
        if (editingLink) {
            setSelectedRole(
                inviteLinksFromApi.data.items.find(it => it.token === editingLink)?.roleAssignment ?? "USER"
            );

            setSelectedGroups(
                inviteLinksFromApi.data.items.find(it => it.token === editingLink)?.groupAssignment.map(it => it) ?? []
            );
        }
    }, [editingLink, inviteLinksFromApi]);

    useEffect(() => {
        fetchInviteLinks({
            ...Api.browseInviteLinks({itemsPerPage: 10}),
            projectOverride: project.id
        });
    }, []);

    return inviteLinksFromApi.data.items.length < 1 ? <>
        <Heading.h3>Invite with link</Heading.h3>
        <Box textAlign="center">
            <Text mb="20px" mt="20px">Invite collaborators to this project with a link</Text>
            <Button
                onClick={async () => {
                    await callAPIWithErrorHandler({
                        ...Api.createInviteLink(),
                        projectOverride: project.id
                    });

                    fetchInviteLinks({
                        ...Api.browseInviteLinks({itemsPerPage: 10}),
                        projectOverride: project.id
                    });
                }}
            >Create link</Button>
        </Box>
    </> : <>
        {editingLink !== undefined ?
            <Box minHeight="200px">
                <Flex>
                    <Button mr={20} onClick={() => setEditingLink(undefined)}>
                        <Icon name="backward" size={20}/>
                    </Button>
                    <Heading.h3>Edit link settings</Heading.h3>
                </Flex>

                <Flex justifyContent="space-between" mt={20} mb={10}>
                    <Text pt="10px">Assign members to role</Text>
                    <div className={SelectBox}>
                        <ClickableDropdown
                            useMousePositioning
                            width="100px"
                            chevron
                            trigger={roles.find(it => it.value === selectedRole)?.text}
                            options={roles}
                            onChange={async role => {
                                await callAPIWithErrorHandler({
                                    ...Api.updateInviteLink({token: editingLink, role: role, groups: selectedGroups}),
                                    projectOverride: project.id
                                });

                                fetchInviteLinks({
                                    ...Api.browseInviteLinks({itemsPerPage: 10}),
                                    projectOverride: project.id
                                });
                            }}
                        />
                    </div>
                </Flex>
                <Flex justifyContent="space-between">
                    <Text pt="10px">Assign members to groups</Text>

                    <div className={SelectBox}>
                        <ClickableDropdown
                            useMousePositioning
                            width="300px"
                            height={300}
                            chevron
                            trigger={<>{selectedGroups.length} selected groups</>}
                            keepOpenOnClick={true}
                        >
                            {groupItems.length < 1 ?
                                <>No selectable groups</>
                                :
                                groupItems.map(item =>
                                    item ?
                                        <Box
                                            key={item.value}
                                            onClick={async () => {
                                                const newSelection = selectedGroups.length < 1 ? [item.value] :
                                                    selectedGroups.includes(item.value) ?
                                                        selectedGroups.filter(it => it != item.value) : selectedGroups.concat([item.value]);

                                                await callAPIWithErrorHandler({
                                                    ...Api.updateInviteLink({
                                                        token: editingLink,
                                                        role: selectedRole,
                                                        groups: newSelection
                                                    }),
                                                    projectOverride: project.id
                                                });

                                                fetchInviteLinks({
                                                    ...Api.browseInviteLinks({itemsPerPage: 10}),
                                                    projectOverride: project.id
                                                });
                                            }}
                                        >
                                            <Checkbox checked={selectedGroups.includes(item.value)} readOnly/>
                                            {item.text}
                                        </Box>
                                        : <></>
                                )
                            }
                        </ClickableDropdown>
                    </div>
                </Flex>
            </Box> : <>
                <Flex justifyContent="space-between">
                    <Heading.h3>Invite with link</Heading.h3>
                    <Box textAlign="right">
                        <Button
                            onClick={async () => {
                                await callAPIWithErrorHandler({
                                    ...Api.createInviteLink(),
                                    projectOverride: project.id
                                });

                                fetchInviteLinks({
                                    ...Api.browseInviteLinks({itemsPerPage: 10}),
                                    projectOverride: project.id
                                });
                            }}
                        >Create link</Button>
                    </Box>
                </Flex>
                <Box mt={20}>
                    {inviteLinksFromApi.data.items.map(link => (
                        <Box key={link.token} mb="10px">
                            <Flex justifyContent="space-between">

                                <Flex flexDirection={"column"}>
                                    <Tooltip
                                        trigger={(
                                            <Input
                                                readOnly
                                                style={{"cursor": "pointer"}}
                                                onClick={() => {
                                                    copyToClipboard({
                                                        value: inviteLinkFromToken(link.token),
                                                        message: "Link copied to clipboard"
                                                    })
                                                }}
                                                mr={10}
                                                value={inviteLinkFromToken(link.token)}
                                                width="500px"
                                            />
                                        )}
                                    >
                                        Click to copy link to clipboard
                                    </Tooltip>
                                    <Text fontSize={12}>This link will automatically expire
                                        in {daysLeftToTimestamp(link.expires)} days</Text>
                                </Flex>
                                <Flex>
                                    <Button
                                        mr="5px"
                                        height={40}
                                        onClick={() =>
                                            setEditingLink(link.token)
                                        }
                                    >
                                        <Icon name="edit" size={20}/>
                                    </Button>

                                    <ConfirmationButton
                                        color="errorMain"
                                        height={40}
                                        onAction={async () => {
                                            await callAPIWithErrorHandler({
                                                ...Api.deleteInviteLink({token: link.token}),
                                                projectOverride: project.id
                                            });

                                            fetchInviteLinks({
                                                ...Api.browseInviteLinks({itemsPerPage: 10}),
                                                projectOverride: project.id
                                            });
                                        }}
                                        icon="trash"
                                    />
                                </Flex>
                            </Flex>
                        </Box>
                    ))}
                </Box>
            </>}
    </>
};

const SelectBox = injectStyleSimple("select-box", `
    border: 2px solid var(--borderColor);
    border-radius: 5px;
    padding: 10px;
`);

// Utilities
// ================================================================================
interface RoleOption {
    text: string;
    icon: IconName;
    value: ProjectRole;
}

const allRoleOptions: RoleOption[] = [
    {text: "User", icon: "user", value: OldProjectRole.USER},
    {text: "Admin", icon: "userAdmin", value: OldProjectRole.ADMIN},
    {text: "PI", icon: "userPi", value: OldProjectRole.PI}
];

// Styling
// ================================================================================


const SearchContainer = injectStyle("search-container", k => `
    ${k} > form {
        display: flex;
        margin-right: 10px;
        margin-bottom: 10px;
        flex-grow: 1;
        flex-basis: 350px;
    }
`);

const HelpCircleClass = injectStyle("help-circle", k => `
    ${k} {
        border-radius: 500px;
        width: 26px;
        height: 26px;
        border: 2px solid var(--textPrimary);
        cursor: pointer;
    }

    ${k}::after {
        content: "?";
        display: flex;
        justify-content: center;
    }
`);

const USER_ID_HELP = (
    <Tooltip tooltipContentWidth={250} trigger={<div className={HelpCircleClass}/>}>
        <Text fontSize={12}>
            Your username can be found next to {" "}<Icon name="heroIdentification"/>, by clicking the avatar in the
            bottom of the sidebar.
        </Text>
    </Tooltip>
);

export default ProjectMembers2;
