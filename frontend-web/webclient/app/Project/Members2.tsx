import * as React from "react";
import { useCallback, useEffect, useMemo, useReducer, useState } from "react";
import { default as Api, Project, ProjectGroup, ProjectMember, ProjectInvite } from "./Api";
import styled from "styled-components";
import { useHistory, useParams } from "react-router";
import MainContainer from "@/MainContainer/MainContainer";
import { callAPI, useCloudAPI } from "@/Authentication/DataHook";
import { BreadCrumbsBase } from "@/ui-components/Breadcrumbs";
import { HexSpinWrapper } from "@/LoadingIcon/LoadingIcon";
import {
    Absolute,
    Box,
    Button,
    Flex,
    Grid,
    Icon,
    Input,
    Label,
    Link, List,
    RadioTile,
    RadioTilesContainer,
    Relative,
    Text,
    Tooltip, Truncate
} from "@/ui-components";
import { shorten } from "@/Utilities/TextUtilities";
import { isAdminOrPI } from "@/Utilities/ProjectUtilities";
import { getCssVar } from "@/Utilities/StyledComponentsUtilities";
import { addStandardInputDialog } from "@/UtilityComponents";
import { doNothing, preventDefault } from "@/UtilityFunctions";
import { useAvatars } from "@/AvataaarLib/hook";
import { UserAvatar } from "@/AvataaarLib/UserAvatar";
import { defaultAvatar } from "@/UserSettings/Avataaar";
import { IconName } from "@/ui-components/Icon";
import { ProjectRole } from "@/Project/index";
import { ConfirmationButton } from "@/ui-components/ConfirmationAction";
import { bulkRequestOf } from "@/DefaultObjects";
import * as Heading from "@/ui-components/Heading";
import { ItemRenderer, ItemRow } from "@/ui-components/Browse";
import { BrowseType } from "@/Resource/BrowseType";
import { useToggleSet } from "@/Utilities/ToggleSet";
import { buildQueryString, getQueryParam } from "@/Utilities/URIUtilities";
import { History } from "history";
import BaseLink from "@/ui-components/BaseLink";
import { deepCopy } from "@/Utilities/CollectionUtilities";
import { Operation } from "@/ui-components/Operation";
import { useTitle, useLoading } from "@/Navigation/Redux/StatusActions";
import { useRefreshFunction } from "@/Navigation/Redux/HeaderActions";
import { SidebarPages, useSidebarPage } from "@/ui-components/Sidebar";
import { PageV2 } from "@/UCloud";
import { emptyPageV2 } from "@/DefaultObjects";

// UI state management
// ================================================================================
type ProjectAction = AddToGroup | RemoveFromGroup | Reload | InspectGroup | InviteMember | ReloadInvites;

interface Reload {
    type: "Reload";
    project: Project;
}

interface ReloadInvites {
    type: "ReloadInvites";
    invites: PageV2<ProjectInvite>;
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

interface InviteMember {
    type: "InviteMember";
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

        case "InviteMember": {
            // TODO actually show the invites
        }
    }
    return state;
}

// NOTE(Dan): Implements side effects which need to occur based on an action. This typically involves sending commands
// to the backend which reflect the desired change.
function onAction(state: UIState, action: ProjectAction, cb: ActionCallbacks) {
    const {project, invites} = state;
    if (!project) return;
    switch (action.type) {
        case "AddToGroup": {
            callAPI(Api.createGroupMember(bulkRequestOf({group: action.group, username: action.member})));
            break;
        }

        case "RemoveFromGroup": {
            callAPI(Api.deleteGroupMember(bulkRequestOf({group: action.group, username: action.member})));
            break;
        }

        case "InspectGroup": {
            cb.history.push(buildQueryString(`/projects2/members/${project.id}`, {group: action.group ?? undefined}));
            break;
        }

        case "InviteMember": {
            callAPI(Api.createInvite(bulkRequestOf(...action.members.map(it => ({recipient: it})))));
            break;
        }
    }
}

interface ActionCallbacks {
    history: History;
}

// Primary user interface
// ================================================================================
export const ProjectMembers2: React.FunctionComponent = () => {
    // Input "parameters"
    const history = useHistory();
    const params = useParams<{ project: string }>();
    const inspectingGroupId = getQueryParam(history.location.search, "group");
    const projectId = params.project;

    // Remote data
    const [invitesFromApi, fetchInvites] = useCloudAPI<PageV2<ProjectInvite>>({noop: true}, emptyPageV2);
    const [projectFromApi, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    const avatars = useAvatars();

    // UI state
    const [uiState, pureDispatch] = useReducer(projectReducer, {project: null, invites: emptyPageV2});
    const {project, invites} = uiState;
    const groupToggleSet = useToggleSet([]);
    const groupMemberToggleSet = useToggleSet([]);

    const [memberQuery, setMemberQuery] = useState<string>("");
    const updateMemberQueryFromEvent = useCallback((e: React.SyntheticEvent) => {
        setMemberQuery((e.target as HTMLInputElement).value);
    }, []);

    // UI callbacks and state manipulation
    const actionCb: ActionCallbacks = useMemo(() => ({
        history
    }), [history]);

    const dispatch = useCallback((action: ProjectAction) => {
        onAction(uiState, action, actionCb);
        pureDispatch(action);
    }, [uiState, pureDispatch, actionCb]);

    const reload = useCallback(() => {
        fetchProject(Api.retrieve({
            id: projectId,
            includePath: true,
            includeMembers: true,
            includeArchived: true,
            includeGroups: true,
        }));

        fetchInvites(Api.browseInvites({
            itemsPerPage: 50,
            filterType: "OUTGOING",
        }));
    }, [projectId]);

    // Aliases and computed data
    const inspectingGroup: ProjectGroup | null =
        !inspectingGroupId || !project ? null : 
            project.status.groups!.find(it => it.id === inspectingGroupId) ?? null;

    const isAdmin = !project ? false : isAdminOrPI(project.status.myRole!);

    const groups: ProjectGroup[] = !project ? [] : project.status.groups!;
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
        isAdmin
    }), [dispatch, inspectingGroup, isAdmin]);

    // Effects
    useEffect(() => reload(), [reload]);

    useEffect(() => {
        if (!projectFromApi.data) return;
        avatars.updateCache(projectFromApi.data.status.members!.map(it => it.username));
        dispatch({type: "Reload", project: projectFromApi.data});
    }, [projectFromApi.data]);

    useEffect(() => {
        if (!invitesFromApi.data) return;
        dispatch({type: "ReloadInvites", invites: invitesFromApi.data});
    }, [invitesFromApi.data]);

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
                        <span><Link to="/projects2">My Projects</Link></span>
                        <span><Link
                            to="/projects2/dashboard">{shorten(20, project.specification.title)}</Link></span>
                        <span>Members</span>
                    </ProjectBreadcrumbsWrapper>

                    <SearchContainer>
                        {!isAdmin ? null : (
                            <form onSubmit={preventDefault}>
                                <Absolute>
                                    <Relative left="94px" top="8px">
                                        <Tooltip tooltipContentWidth="160px" trigger={<HelpCircle/>}>
                                            <Text color="black" fontSize={12}>
                                                Your username can be found at the bottom of the sidebar next to
                                                {" "}<Icon name="id"/>.
                                            </Text>
                                        </Tooltip>
                                    </Relative>
                                </Absolute>
                                <Input
                                    id="new-project-member"
                                    placeholder="Username"
                                    autoComplete="off"
                                    // disabled={isLoading}
                                    // ref={newMemberRef}
                                    // onChange={e => {
                                    //     const shouldShow = e.target.value === "";
                                    //     if (showId !== shouldShow) setShowId(shouldShow);
                                    // }
                                    rightLabel
                                />
                                <Button
                                    asSquare
                                    color="green"
                                    type="button"
                                    title="Bulk invite"
                                    onClick={async () => {
                                        try {
                                            const res = await addStandardInputDialog({
                                                title: "Bulk invite",
                                                type: "textarea",
                                                confirmText: "Invite users",
                                                width: "450px",
                                                help: (<>Enter usernames in the box below. One username per line.</>)
                                            });

                                            const usernames = res.result
                                                .split("\n")
                                                .map(it => it.trim())
                                                .filter(it => it.length > 0);

                                            // await runCommand(inviteMember({projectId, usernames}));
                                            // reloadMembers();
                                        } catch (ignored) {
                                            // Ignored
                                        }
                                    }}
                                >
                                    <Icon name="open"/>
                                </Button>
                                <Button attached type={"submit"}>Add</Button>
                            </form>
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
                                        <Icon name="search" size="24"/>
                                    </Label>
                                </Absolute>
                            </Relative>
                        </form>
                    </SearchContainer>
                    <Grid gridGap={"16px"}>
                        {relevantMembers.map(it => (
                            <MemberCard key={it.username} project={project} member={it}
                                        inspectingGroup={inspectingGroup} dispatch={dispatch}/>
                        ))}
                    </Grid>
                </div>
                <div className="groups">
                    {inspectingGroup ? null : <>
                        <Flex mb={"12px"}>
                            <BreadCrumbsBase embedded={false}>
                                <span>Groups</span>
                            </BreadCrumbsBase>
                            <div style={{flexGrow: 1}}/>
                            {!isAdmin ? null : (
                                <Button mt={"2px"} height={"40px"} width={"120px"} onClick={doNothing}>
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

                        {groups.map(g =>
                            <ItemRow
                                item={g}
                                key={g.id}
                                browseType={BrowseType.Embedded}
                                renderer={GroupRenderer}
                                toggleSet={groupToggleSet}
                                operations={groupOperations}
                                callbacks={callbacks}
                                itemTitle={"Group"}
                                navigate={() => dispatch({type: "InspectGroup", group: g.id})}
                            />
                        )}
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
const MemberCard: React.FunctionComponent<{
    project: Project;
    member: ProjectMember;
    inspectingGroup?: ProjectGroup | null;
    dispatch: (action: ProjectAction) => void;
}> = ({project, member, inspectingGroup, dispatch}) => {
    const isAdmin = isAdminOrPI(project.status.myRole!);
    const memberOfAnyGroup = (project.status.groups ?? []).some(g => g.status.members!.some(m => m === member.username));
    const memberOfThisGroup = !inspectingGroup ? false : inspectingGroup.status.members!.some(m => m === member.username);
    const avatars = useAvatars();

    let options: RoleOption[];
    if (!isAdmin) {
        options = [roleOptionForRole(member.role)];
    } else {
        options = [roleOptionForRole(ProjectRole.USER), roleOptionForRole(ProjectRole.ADMIN)];

        if (project.status.myRole! === ProjectRole.PI) {
            options.push(roleOptionForRole(ProjectRole.PI));
        }

        if (member.role === ProjectRole.PI) {
            options = [roleOptionForRole(ProjectRole.PI)]
        }
    }

    const addToGroup = useCallback(() => {
        if (!inspectingGroup) return;
        dispatch({type: "AddToGroup", member: member.username, group: inspectingGroup.id });
    }, [dispatch, member, inspectingGroup]);

    return <MemberCardWrapper>
        <UserAvatar avatar={avatars.cache[member.username] ?? defaultAvatar}/>

        <div>
            <Text bold>{member.username}</Text>
            {memberOfAnyGroup ? null : <>
                <Text color={"red"}>
                    <Icon name={"warning"} size={20} mr={"6px"}/>
                    Not a member of any group
                </Text>
            </>}
        </div>

        <div className="spacer"/>

        <RadioTilesContainer height={"48px"}>
            {options.map(it => (
                <RadioTile
                    key={it.text}
                    name={member.username}
                    icon={it.icon}
                    height={40}
                    labeled
                    label={it.text}
                    fontSize={"0.5em"}
                    checked={member.role === it.value}
                    onChange={doNothing}
                />
            ))}
        </RadioTilesContainer>

        {!isAdmin ? null : !inspectingGroup ? <>
            {member.role === ProjectRole.PI ? null :
                <ConfirmationButtonStyling>
                    <ConfirmationButton actionText={"Remove"} icon={"close"}/>
                </ConfirmationButtonStyling>
            }
        </> : <>
            {memberOfThisGroup ? null :
                <Button ml="8px" color="green" height="35px" width="35px" onClick={addToGroup}>
                    <Icon
                        color="white"
                        name="arrowDown"
                        rotation={-90}
                        width="1em"
                        title="Add to group"
                    />
                </Button>
            }
        </>}

    </MemberCardWrapper>;
};

const GroupRenderer: ItemRenderer<ProjectGroup> = {
    Icon: ({resource}) => null,
    MainTitle: ({resource}) => <>{resource?.specification?.title}</>,
    Stats: ({resource}) => null,
    ImportantStats: ({resource}) => {
        if (!resource) return null;
        const numberOfMembers = resource.status.members?.length;
        return <><Icon name="user" mr="8px" /> {numberOfMembers}</>;
    },
};

const groupOperations: Operation<ProjectGroup, Callbacks>[] = [
    {
        text: "Rename",
        icon: "rename",
        enabled: (groups, cb) => {
            return groups.length === 1 && cb.isAdmin;
        },
        onClick: (groups, cb) => {
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
        return <UserAvatar avatar={avatars.cache[resource] ?? defaultAvatar}/>;
    },
    MainTitle: ({resource}) => <>{resource}</>,
};

const groupMemberOperations: Operation<string, Callbacks>[] = [
    {
        text: "Remove",
        icon: "close",
        color: "red",
        primary: true,
        enabled: (members, cb) => members.length > 0 && !!cb.inspectingGroup,
        onClick: (members, cb) => {
            if (!cb.inspectingGroup) return;
            members.forEach(member => {
                cb.dispatch({type: "RemoveFromGroup", member, group: cb.inspectingGroup!.id});
            });
        }
    }
];

// Utilities
// ================================================================================
interface Callbacks {
    dispatch: (action: ProjectAction) => void;
    inspectingGroup: ProjectGroup | null;
    isAdmin: boolean;
}

interface RoleOption {
    text: string;
    icon: IconName;
    value: ProjectRole;
}

const allRoleOptions: RoleOption[] = [
    {text: "User", icon: "user", value: ProjectRole.USER},
    {text: "Admin", icon: "userAdmin", value: ProjectRole.ADMIN},
    {text: "PI", icon: "userPi", value: ProjectRole.PI}
];

function roleOptionForRole(role: ProjectRole): RoleOption {
    return allRoleOptions.find(it => it.value === role)!;
}

// Styling
// ================================================================================
const MemberCardWrapper = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;

  .spacer {
    flex-grow: 1;
  }
`;

const ConfirmationButtonStyling = styled(Box)`
  margin-left: 3px;

  & > button {
    min-width: 175px;
    font-size: 12px;
  }

  & ${Icon} {
    height: 12px;
    width: 12px;
  }
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

  form {
    flex-grow: 1;
    flex-basis: 350px;
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
