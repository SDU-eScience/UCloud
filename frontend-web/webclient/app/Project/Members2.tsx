import * as React from "react";
import {useRef, useCallback, useEffect, useMemo, useReducer, useState} from "react";
import {default as Api, Project, ProjectGroup, ProjectMember, ProjectInvite, ProjectRole, isAdminOrPI, OldProjectRole, ProjectInviteLink, projectRoleToString} from "./Api";
import styled from "styled-components";
import {NavigateFunction, useLocation, useNavigate, useParams} from "react-router";
import MainContainer from "@/MainContainer/MainContainer";
import {callAPIWithErrorHandler, useCloudAPI} from "@/Authentication/DataHook";
import {BreadCrumbsBase} from "@/ui-components/Breadcrumbs";
import {HexSpinWrapper} from "@/LoadingIcon/LoadingIcon";
import {
    Absolute,
    Box,
    Button,
    Checkbox,
    Flex,
    Icon,
    Input,
    Label,
    Link, List,
    NoSelect,
    RadioTile,
    RadioTilesContainer,
    Relative,
    Text,
    Tooltip, Truncate
} from "@/ui-components";
import {shorten} from "@/Utilities/TextUtilities";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {addStandardDialog, addStandardInputDialog, NamingField} from "@/UtilityComponents";
import {copyToClipboard, doNothing, preventDefault} from "@/UtilityFunctions";
import {useAvatars} from "@/AvataaarLib/hook";
import {UserAvatar} from "@/AvataaarLib/UserAvatar";
import {IconName} from "@/ui-components/Icon";
import {bulkRequestOf} from "@/DefaultObjects";
import * as Heading from "@/ui-components/Heading";
import {ItemRenderer, ItemRow, useRenamingState} from "@/ui-components/Browse";
import {BrowseType} from "@/Resource/BrowseType";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import BaseLink from "@/ui-components/BaseLink";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {Operation} from "@/ui-components/Operation";
import {useTitle, useLoading} from "@/Navigation/Redux/StatusActions";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/SidebarPagesEnum";
import {PageV2, BulkResponse, FindByStringId} from "@/UCloud";
import {emptyPageV2} from "@/DefaultObjects";
import {Client} from "@/Authentication/HttpClientInstance";
import {timestampUnixMs} from "@/UtilityFunctions";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {dialogStore} from "@/Dialog/DialogStore";
import {DropdownContent} from "@/ui-components/Dropdown";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {ProductSelector} from "@/Products/Selector";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

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
                ...Api.changeRole(bulkRequestOf(...action.changes.filter(it => it.username != Client.username!))),
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
            cb.navigate(buildQueryString(`/projects/${project.id}/members`, {group: action.group ?? undefined}));
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
            await callAPIWithErrorHandler({
                ...Api.deleteInvite(bulkRequestOf(...action.members.map(it => ({username: it, project: project.id})))),
                projectOverride: project.id
            });
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
    const location = useLocation();
    const params = useParams<{project: string}>();
    const inspectingGroupId = getQueryParam(location.search, "group");
    const projectId = params.project!;

    // Remote data
    const [invitesFromApi, fetchInvites] = useCloudAPI<PageV2<ProjectInvite>>({noop: true}, emptyPageV2);
    const [projectFromApi, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    const avatars = useAvatars();

    // UI state
    const [uiState, pureDispatch] = useReducer(projectReducer, {project: null, invites: emptyPageV2});
    const {project, invites} = uiState;
    const [creatingGroup, setIsCreatingGroup] = useState(false);
    const groupToggleSet = useToggleSet([]);
    const groupMemberToggleSet = useToggleSet([]);
    const inviteToggleSet = useToggleSet([]);
    const memberToggleSet = useToggleSet([]);
    const newMemberRef = useRef<HTMLInputElement>(null);

    const [memberQuery, setMemberQuery] = useState<string>("");
    const updateMemberQueryFromEvent = useCallback((e: React.SyntheticEvent) => {
        setMemberQuery((e.target as HTMLInputElement).value);
    }, []);

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

    const onAddMember = useCallback((e: React.SyntheticEvent) => {
        e.preventDefault();
        const value = newMemberRef.current?.value;
        if (!value) return;

        newMemberRef.current.value = "";
        avatars.updateCache([value]);
        dispatch({type: "InviteMember", members: [value]});
    }, [dispatch]);

    const startGroupCreation = useCallback(() => {
        setIsCreatingGroup(true);
    }, []);

    const groupRenaming = useRenamingState<ProjectGroup>(
        (group) => group.specification.title,
        [],

        (a, b) => a.id === b.id,
        [],

        async (group, newTitle) => {
            dispatch({type: "RenameGroup", group: group.id, newTitle});
        },
        [dispatch]
    );

    // Aliases and computed data
    const inspectingGroup: ProjectGroup | null =
        !inspectingGroupId || !project ? null :
            project.status.groups!.find(it => it.id === inspectingGroupId) ?? null;

    const isAdmin = !project ? false : isAdminOrPI(project.status.myRole!);

    const groups: (ProjectGroup | undefined)[] = useMemo(() => {
        if (!project) return [];
        if (creatingGroup) {
            return [undefined, ...project.status.groups!];
        } else {
            return project.status.groups!;
        }
    }, [creatingGroup, project]);

    const relevantMembers: ProjectMember[] = useMemo(() => {
        if (!project) return [];

        const allMembers = project.status.members!;
        const normalizedQuery = memberQuery.trim().toLowerCase();
        if (normalizedQuery === "") return allMembers;

        return allMembers.filter(m => m.username.toLowerCase().indexOf(normalizedQuery) != -1);
    }, [project, memberQuery]);

    const callbacks: Callbacks = useMemo(() => ({
        dispatch,
        inspectingGroup,
        isAdmin,
        project,
    }), [dispatch, inspectingGroup, isAdmin, project]);

    const groupCallbacks: GroupCallbacks = useMemo(() => ({
        ...callbacks,
        setRenaming: groupRenaming.setRenaming,
        setIsCreating: setIsCreatingGroup,
        create: (title: string) => {
            setIsCreatingGroup(false);
            callbacks.dispatch({type: "CreateGroup", title, placeholderId: groupIdCounter++});
        },

    }), [callbacks, setIsCreatingGroup, groupRenaming.setRenaming]);

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

    useEffect(() => {
        inviteToggleSet.uncheckAll();
    }, [invites]);

    useEffect(() => {
        groupToggleSet.uncheckAll();
    }, [groups]);

    useEffect(() => {
        memberToggleSet.uncheckAll();
    }, [relevantMembers]);

    useEffect(() => {
        groupMemberToggleSet.uncheckAll();
    }, [inspectingGroup?.status?.members]);

    useTitle("Member and Group Management");
    useRefreshFunction(reload);
    useSidebarPage(SidebarPages.Projects);
    useLoading(projectFromApi.loading || invitesFromApi.loading);

    if (!project) return null;

    return <MainContainer
        sidebar={null}
        main={
            <TwoColumnLayout>
                <div className="members">
                    <ProjectBreadcrumbsWrapper mb="12px" embedded={false}>
                        <span><Link to="/projects">My Projects</Link></span>
                        <span>
                            <Link to={`/projects/${projectId}`}>
                                {shorten(20, project.specification.title)}
                            </Link>
                        </span>
                        <span>Members</span>
                    </ProjectBreadcrumbsWrapper>

                    <SearchContainer>
                        {!isAdmin ? null : (
                            <>
                                <form onSubmit={onAddMember}>
                                    <Input
                                        id="new-project-member"
                                        placeholder="Username"
                                        autoComplete="off"
                                        ref={newMemberRef}
                                        rightLabel
                                    />
                                    <Button attached type={"submit"}>Add</Button>
                                    <Relative left="-95px" top="8px">
                                        <Absolute>
                                            <Tooltip tooltipContentWidth="160px" trigger={<HelpCircle />}>
                                                <Text color="black" fontSize={12}>
                                                    Your username can be found at the bottom of the sidebar next to
                                                    {" "}<Icon name="id" />.
                                                </Text>
                                            </Tooltip>
                                        </Absolute>
                                    </Relative>
                                </form>
                                <Button
                                    mb={10}
                                    mr={10}
                                    width={110}
                                    type="button"
                                    title="Invite with link"
                                    onClick={async () => {
                                        dialogStore.addDialog(
                                            <InviteLinkEditor
                                                groups={groups}
                                                project={project}
                                            />,
                                            doNothing,
                                            true
                                        );
                                    }}
                                >
                                    Invite link
                                </Button>
                            </>

                        )}
                        <form onSubmit={preventDefault}>
                            <Input
                                id="project-member-search"
                                placeholder="Search existing project members..."
                                pr="30px"
                                autoComplete="off"
                                value={memberQuery}
                                onChange={updateMemberQueryFromEvent}
                            />
                            <Relative>
                                <Absolute right="6px" top="10px">
                                    <Label htmlFor="project-member-search">
                                        <Icon name="search" size="24" />
                                    </Label>
                                </Absolute>
                            </Relative>
                        </form>
                    </SearchContainer>
                    <List mt="16px">
                        {invites.items.map(i =>
                            <ItemRow
                                item={i}
                                key={i.recipient}
                                browseType={BrowseType.Embedded}
                                renderer={InviteRenderer}
                                toggleSet={inviteToggleSet}
                                operations={inviteOperations}
                                callbacks={callbacks}
                                itemTitle={"Invite"}
                            />
                        )}
                        {relevantMembers.map(it => (
                            <ItemRow
                                item={it}
                                key={it.username}
                                browseType={BrowseType.Embedded}
                                renderer={MemberRenderer}
                                toggleSet={memberToggleSet}
                                callbacks={callbacks}
                                operations={memberOperations}
                                itemTitle={"Member"}
                            />
                        ))}
                    </List>
                </div>
                <div className="groups">
                    {inspectingGroup ? null : <>
                        <Flex mb={"12px"}>
                            <BreadCrumbsBase embedded={false}>
                                <span>Groups</span>
                            </BreadCrumbsBase>
                            <div style={{flexGrow: 1}} />
                            {!isAdmin ? null : (
                                <Button mt={"2px"} height={"40px"} width={"120px"} onClick={startGroupCreation}>
                                    New Group
                                </Button>
                            )}
                        </Flex>
                        {groups.length !== 0 ? null : <>
                            <Flex justifyContent={"center"} alignItems={"center"} minHeight={"300px"}
                                flexDirection={"column"}>
                                <Heading.h4>You have no groups to manage.</Heading.h4>
                                <ul>
                                    <li>Groups are used to manage permissions in your project</li>
                                    <li>You must create a group to grant members access to the project&#039;s files</li>
                                </ul>
                            </Flex>
                        </>}

                        <List>
                            {groups.map(g =>
                                <ItemRow
                                    item={g}
                                    key={g?.id ?? "creating"}
                                    browseType={BrowseType.Embedded}
                                    renderer={GroupRenderer}
                                    toggleSet={groupToggleSet}
                                    operations={groupOperations}
                                    callbacks={groupCallbacks}
                                    renaming={groupRenaming}
                                    itemTitle={"Group"}
                                    navigate={() => !g ? null : dispatch({type: "InspectGroup", group: g.id})}
                                />
                            )}
                        </List>
                    </>}

                    {!inspectingGroup ? null : <>
                        <Flex mb="16px">
                            <BaseLink onClick={() => dispatch({type: "InspectGroup", group: null})}>
                                <Button mt="4px" width="42px" height="34px"><Icon rotation={90} name="arrowDown" /></Button>
                            </BaseLink>
                            <Text mx="8px" fontSize="25px">|</Text>
                            <Flex width={"100%"}>
                                <Truncate fontSize="25px" width={1}>{inspectingGroup.specification.title}</Truncate>
                            </Flex>
                        </Flex>

                        {inspectingGroup.status.members!.length !== 0 ? null : <>
                            <Text mt={40} textAlign="center">
                                <Heading.h4>No members in group</Heading.h4>
                                You can add members by clicking on the green arrow in the
                                &apos;Members&apos; panel.
                            </Text>
                        </>}

                        <List>
                            {inspectingGroup.status.members!.map((member, idx) => (
                                <ItemRow
                                    item={member}
                                    key={idx}
                                    browseType={BrowseType.Embedded}
                                    renderer={GroupMemberRenderer}
                                    toggleSet={groupMemberToggleSet}
                                    operations={groupMemberOperations}
                                    callbacks={callbacks}
                                    itemTitle={"Member"}
                                />
                            ))}
                        </List>
                    </>}
                </div>
            </TwoColumnLayout>
        }
    />;
};

// Secondary interface (e.g. member rows and group rows)
// ================================================================================
const MemberRenderer: ItemRenderer<ProjectMember, Callbacks> = {
    Icon: ({resource}) => {
        const avatars = useAvatars();
        if (!resource) return null;

        return <UserAvatar avatar={avatars.avatar(resource.username)} />;
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <>{resource.username}</>;
    },

    ImportantStats: ({resource, callbacks}) => {
        const {inspectingGroup, isAdmin, project} = callbacks;
        if (!resource || !project) return null;

        const memberOfThisGroup = !inspectingGroup ? false : inspectingGroup.status.members!.some(m => m === resource.username);

        let options: RoleOption[];
        if (!isAdmin || resource.username === Client.username || !!inspectingGroup) {
            options = [roleOptionForRole(resource.role)];
        } else {
            options = [roleOptionForRole(OldProjectRole.USER), roleOptionForRole(OldProjectRole.ADMIN)];

            if (project.status.myRole! === OldProjectRole.PI) {
                options.push(roleOptionForRole(OldProjectRole.PI));
            }

            if (resource.role === OldProjectRole.PI) {
                options = [roleOptionForRole(OldProjectRole.PI)]
            }
        }

        const onRoleChange: Record<ProjectRole, (e: React.SyntheticEvent) => void> = useMemo(() => {
            const dispatchChange = (e: React.SyntheticEvent, role: ProjectRole) => {
                e.stopPropagation();
                callbacks.dispatch({type: "ChangeRole", changes: [{username: resource.username, role}]});
            };

            const result: Record<ProjectRole, (e: React.SyntheticEvent) => void> = {
                [OldProjectRole.USER]: (e) => dispatchChange(e, OldProjectRole.USER),
                [OldProjectRole.ADMIN]: (e) => dispatchChange(e, OldProjectRole.ADMIN),
                [OldProjectRole.PI]: (e) => {
                    e.stopPropagation();
                    addStandardDialog({
                        title: "Transfer PI Role",
                        message: "Are you sure you wish to transfer the PI role? " +
                            "A project can only have one PI. " +
                            "Your own user will be demoted to admin.",
                        onConfirm: () => {
                            callbacks.dispatch({
                                type: "ChangeRole", changes: [
                                    {username: resource.username, role: OldProjectRole.PI},
                                    {username: Client.username!, role: OldProjectRole.ADMIN}
                                ]
                            });
                        },
                        confirmText: "Transfer PI role"
                    });
                },
            };
            return result;
        }, [callbacks.dispatch, resource.username]);

        const addToGroup = useCallback((e: React.SyntheticEvent) => {
            e.stopPropagation();
            if (!inspectingGroup) return;
            callbacks.dispatch({type: "AddToGroup", member: resource.username, group: inspectingGroup.id});
        }, [callbacks.dispatch, resource, inspectingGroup]);

        return <MemberRowWrapper>
            <RadioTilesContainer height={"48px"}>
                {options.map(it => (
                    <RadioTile
                        key={it.text}
                        name={resource.username}
                        icon={it.icon}
                        height={40}
                        labeled
                        label={it.text}
                        fontSize={"0.5em"}
                        checked={resource.role === it.value}
                        onChange={onRoleChange[it.value]}
                    />
                ))}
            </RadioTilesContainer>

            {!isAdmin ? null : !inspectingGroup ? null : <>
                <Button ml="8px" color="green" height="35px" width="35px" onClick={addToGroup}
                    disabled={memberOfThisGroup}>
                    <Icon
                        color="white"
                        name="arrowDown"
                        rotation={-90}
                        width="1em"
                        title="Add to group"
                    />
                </Button>
            </>}
        </MemberRowWrapper>;
    },

    Stats: ({resource, callbacks}) => {
        const {project} = callbacks;
        if (!resource || !project) return null;
        const memberOfAnyGroup = (project.status.groups ?? [])
            .some(g => g.status.members!.some(m => m === resource.username));

        return <>
            {memberOfAnyGroup ? null : <>
                <Text color={"red"}>
                    <Icon name={"warning"} size={20} mr={"6px"} color="red" />
                    Not a member of any group
                </Text>
            </>}
        </>;
    }
};

const memberOperations: Operation<ProjectMember, Callbacks>[] = [
    {
        text: "Promote to admin",
        icon: "userAdmin",
        enabled: (members, cb) => members.length >= 1 && cb.isAdmin && !cb.inspectingGroup &&
            members.every(m => m.role === OldProjectRole.USER && m.username !== Client.username),
        onClick: (members, cb) => {
            cb.dispatch({
                type: "ChangeRole",
                changes: members.map(m => ({username: m.username, role: OldProjectRole.ADMIN}))
            });
        }
    },
    {
        text: "Demote to user",
        icon: "user",
        enabled: (members, cb) => members.length >= 1 && cb.isAdmin && !cb.inspectingGroup &&
            members.every(m => m.role === OldProjectRole.ADMIN && m.username !== Client.username),
        onClick: (members, cb) => {
            cb.dispatch({
                type: "ChangeRole",
                changes: members.map(m => ({username: m.username, role: OldProjectRole.USER}))
            });

        }
    },
    {
        text: "Remove",
        icon: "close",
        color: "red",
        confirm: true,
        enabled: (members, cb) => members.length >= 1 && cb.isAdmin && !cb.inspectingGroup &&
            members.every(m => m.role !== OldProjectRole.PI && m.username !== Client.username),
        onClick: (members, cb) => {
            cb.dispatch({type: "RemoveMember", members: members.map(it => it.username)});
        }
    },
    {
        text: "Add to group",
        icon: "arrowDown",
        iconRotation: -90,
        enabled: (members, cb) => members.length >= 1 && cb.isAdmin && !!cb.inspectingGroup,
        onClick: (members, cb) => {
            for (const member of members) {
                cb.dispatch({type: "AddToGroup", group: cb.inspectingGroup!.id, member: member.username});
            }
        }
    }
];

const InviteRenderer: ItemRenderer<ProjectInvite> = {
    Icon: ({resource}) => {
        const avatars = useAvatars();
        if (!resource) return null;
        return <UserAvatar avatar={avatars.avatar(resource.recipient)} />;
    },

    MainTitle: ({resource}) => {
        if (!resource) return null;
        return <>{resource.recipient}</>;
    },

    Stats: ({resource}) => {
        if (!resource) return null;
        return <>Invited to join by {resource.invitedBy}</>;
    }
};

const inviteOperations: Operation<ProjectInvite, Callbacks>[] = [
    {
        text: "Remove",
        icon: "close",
        color: "red",
        confirm: true,
        enabled: (invites, cb) => invites.length > 0 && cb.isAdmin,
        onClick: (invites, cb) => {
            cb.dispatch({type: "RemoveInvite", members: invites.map(it => it.recipient)});
        }
    }
];

const GroupRenderer: ItemRenderer<ProjectGroup> = addNamingToRenderer({
    Icon: () => null,
    MainTitle: ({resource}) => {
        if (!resource) return null;

        const isPlaceholder = resource.id.indexOf(placeholderPrefix) === 0;
        return <Flex alignItems="center">
            <Box mr="10px">{resource?.specification?.title}</Box>
            {!isPlaceholder ? null : <Spinner />}
        </Flex>;
    },
    Stats: () => null,
    ImportantStats: ({resource}) => {
        if (!resource) return null;
        const numberOfMembers = resource.status.members?.length;
        return <><Icon name="user" mr="8px" /> {numberOfMembers}</>;
    },
});

interface GroupCallbacks extends Callbacks, CreationCallbacks {
    setRenaming: (group: ProjectGroup) => void;
}

const groupOperations: Operation<ProjectGroup, GroupCallbacks>[] = [
    {
        text: "Rename",
        icon: "rename",
        enabled: (groups, cb) => {
            return groups.length === 1 && cb.isAdmin;
        },
        onClick: ([group], cb) => {
            cb.setRenaming(group);
        }
    },
    {
        text: "Delete",
        icon: "trash",
        color: "red",
        confirm: true,
        enabled: (groups, cb) => groups.length >= 1 && cb.isAdmin,
        onClick: (groups, cb) => {
            cb.dispatch({type: "RemoveGroup", ids: groups.map(it => it.id)});
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: (groups) => groups.length === 1,
        onClick: ([group], cb) => {
            cb.dispatch({type: "InspectGroup", group: group.id});
        }
    }
];

const GroupMemberRenderer: ItemRenderer<string> = {
    Icon: ({resource}) => {
        const avatars = useAvatars();
        if (!resource) return null;
        return <UserAvatar avatar={avatars.avatar(resource)} />;
    },
    MainTitle: ({resource}) => <>{resource}</>,
};

const groupMemberOperations: Operation<string, Callbacks>[] = [
    {
        text: "Remove",
        icon: "close",
        color: "red",
        primary: true,
        enabled: (members, cb) => members.length > 0 && !!cb.inspectingGroup && cb.isAdmin,
        onClick: (members, cb) => {
            if (!cb.inspectingGroup) return;
            members.forEach(member => {
                cb.dispatch({type: "RemoveFromGroup", member, group: cb.inspectingGroup!.id});
            });
        }
    }
];

function daysLeftToTimestamp(timestamp: number): number {
    return Math.floor((timestamp - timestampUnixMs())/1000 / 3600 / 24);
}

function inviteLinkFromToken(token: string): string {
    return window.location.origin + "/app/projects/invite/" + token;
}


const InviteLinkEditor: React.FunctionComponent<{project: Project, groups: (ProjectGroup | undefined)[]}> = ({project, groups}) => {
    const [inviteLinksFromApi, fetchInviteLinks] = useCloudAPI<PageV2<ProjectInviteLink>>({noop: true}, emptyPageV2);
    const [editingLink, setEditingLink] = useState<string|undefined>(undefined);
    const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
    const [selectedRole, setSelectedRole] = useState<string>("USER");
    const linkToggleSet = useToggleSet([]);

    const roles = [
        {text: "User", value: "USER"},
        {text: "Admin", value: "ADMIN"}
    ];

    const groupItems = groups.map(g => 
        g ? 
        {text: g.specification.title, value: g.id}
        : null
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
            <Text mb="20px" mt="20px">Invite collaborators to this project by sharing a link</Text>
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
                        <Icon name="backward" size={20} />
                    </Button>
                    <Heading.h3>Edit link settings</Heading.h3>
                </Flex>

                <Flex justifyContent="space-between" mt={20} mb={10}>
                    <Text pt="10px">Assign members to role</Text>
                    <SelectBox>
                        <ClickableDropdown
                            useMousePositioning
                            width="100px"
                            chevron
                            trigger={<>{roles.find(it => it.value === selectedRole)?.text}</>} 
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
                    </SelectBox> 
                </Flex>
                <Flex justifyContent="space-between">
                    <Text pt="10px">Assign members to groups</Text>

                    <SelectBox>
                        <ClickableDropdown
                            useMousePositioning
                            width="300px"
                            chevron
                            trigger={<>{selectedGroups.length} selected groups</>} 
                            keepOpenOnClick={true}
                            onChange={() =>
                                console.log("closed")
                            }
                        >
                            <>
                                {groupItems.length < 1 ? 
                                    <>No selectable groups</>
                                :
                                    groupItems.map(item =>
                                        item ?
                                            <Box
                                                key={item.value}
                                                onClick={async _ => {
                                                    const newSelection = selectedGroups.length < 1 ? [item.value] :
                                                        selectedGroups.includes(item.value) ?
                                                        selectedGroups.filter(it => it != item.value) : selectedGroups.concat([item.value]);

                                                    await callAPIWithErrorHandler({
                                                        ...Api.updateInviteLink({token: editingLink, role: selectedRole, groups: newSelection}),
                                                        projectOverride: project.id
                                                    });
                                                    
                                                    fetchInviteLinks({
                                                        ...Api.browseInviteLinks({itemsPerPage: 10}),
                                                        projectOverride: project.id
                                                    });
                                                }}
                                            >
                                                <Checkbox checked={selectedGroups.includes(item.value)} readOnly />
                                                {item.text}
                                            </Box>
                                        : <></>
                                    )
                                }
                            </>
                        </ClickableDropdown>
                    </SelectBox>
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
                                    left="-50%"
                                    top="1"
                                    mb="35px"
                                    trigger={(
                                            <Input
                                                readOnly
                                                style={{"cursor": "pointer"}}
                                                onClick={() => {
                                                    copyToClipboard({value: inviteLinkFromToken(link.token), message: "Link copied to clipboard"})
                                                }}
                                                mr={10}
                                                value={inviteLinkFromToken(link.token)}
                                                width="500px"
                                            />
                                    )}
                                >
                                    Click to copy link to clipboard
                                </Tooltip>
                                <Text fontSize={12}>This link will automatically expire in {daysLeftToTimestamp(link.expires)} days</Text>
                            </Flex>
                            <Box>
                                <Button
                                    mr="5px"
                                    height={40}
                                    onClick={() => 
                                        setEditingLink(link.token)
                                    }
                                >
                                    <Icon name="edit" size={20} />
                                </Button>

                                <ConfirmationButton
                                    color="red"
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
                            </Box>
                        </Flex>
                    </Box>
                ))}
            </Box>
        </>}
    </>
};

const SelectBox = styled.div`
    border: 2px solid var(--midGray);
    border-radius: 5px;
    padding: 10px;
`;

// Utilities
// ================================================================================
interface Callbacks {
    dispatch: (action: ProjectAction) => void;
    inspectingGroup: ProjectGroup | null;
    project: Project | null;
    isAdmin: boolean;
}

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

function roleOptionForRole(role: ProjectRole): RoleOption {
    return allRoleOptions.find(it => it.value === role)!;
}

interface CreationCallbacks {
    create: (title: string) => void;
    setIsCreating: (visible: boolean) => void;
}

function addNamingToRenderer<T, CB extends CreationCallbacks>(renderer: ItemRenderer<T, CB>): ItemRenderer<T, CB> {
    const copy: ItemRenderer<T, CB> = {...renderer};
    const NormalMainTitle = renderer.MainTitle;
    copy.MainTitle = function mainTitle(props) {
        const {resource, callbacks} = props;
        const cancelCreate = useCallback(() => callbacks.setIsCreating(false), [callbacks.setIsCreating]);
        const inputRef = useRef<HTMLInputElement>(null);
        const onCreate = useCallback(() => {
            const value = inputRef.current?.value;
            if (!value) return;
            callbacks.create(value);
        }, [callbacks.create]);

        if (resource === undefined) {
            return <NamingField
                confirmText={"Create"}
                inputRef={inputRef}
                onCancel={cancelCreate}
                onSubmit={onCreate}
            />;
        } else {
            return NormalMainTitle ? <NormalMainTitle {...props} /> : null;
        }
    };
    return copy;
}

// Styling
// ================================================================================
const MemberRowWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`;

const ProjectBreadcrumbsWrapper = styled(BreadCrumbsBase)`
  width: 100%;
  max-width: unset;
  flex-grow: 1;

  ${HexSpinWrapper} {
    margin: 0;
    display: inline;
  }
`;

const TwoColumnLayout = styled.div`
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  width: 100%;

  & > * {
    flex-basis: 100%;
  }

  @media screen and (min-width: 1200px) {
    & {
      height: calc(100vh - 100px);
      overflow: hidden;
    }

    & > .members {
      border-right: 2px solid var(--gray, #f00);
      height: 100%;
      flex: 1;
      overflow-y: auto;
      margin-right: 16px;
      padding-right: 16px;
    }

    & > .groups {
      flex: 1;
      height: 100%;
      overflow-y: auto;
      overflow-x: hidden;
    }
  }
`;

const SearchContainer = styled(Flex)`
  flex-wrap: wrap;
  justify-content: space-between;

  form {
    display: flex;
    margin-right: 10px;
    margin-bottom: 10px;
  }
`;

const HelpCircle = styled.div`
  border-radius: 500px;
  width: 20px;
  height: 20px;
  border: 1px solid ${getCssVar("black")};
  margin: 4px 4px 4px 2px;
  cursor: pointer;

  ::after {
    content: "?";
    display: block;
    margin-top: -3px;
    margin-left: 5px;
  }
`;

export default ProjectMembers2;
