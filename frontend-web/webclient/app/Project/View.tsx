import {callAPIWithErrorHandler, useCloudAPI, useGlobalCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    listOutgoingInvites,
    OutgoingInvite,
    ProjectMember,
    ProjectRole,
    UserInProject,
    viewProject,
} from "Project/index";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {useHistory, useParams} from "react-router";
import {Box, Button, Link, Flex, Icon} from "ui-components";
import {connect, useSelector} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {
    groupSummaryRequest,
    listGroupMembersRequest,
    membershipSearch,
    shouldVerifyMembership,
    ShouldVerifyMembershipResponse,
    verifyMembership
} from "Project";
import styled from "styled-components";
import GroupView, {GroupWithSummary} from "./GroupList";
import ProjectMembers, {MembersBreadcrumbs} from "./MembersPanel";
import {Page} from "Types";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {useGlobal} from "Utilities/ReduxHooks";
import {dispatchSetProjectAction} from "Project/Redux";
import {useProjectStatus} from "Project/cache";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {ProjectSettings} from "Project/ProjectSettings";
import {Client} from "Authentication/HttpClientInstance";

// A lot easier to let typescript take care of the details for this one
// eslint-disable-next-line
export function useProjectManagementStatus() {
    const history = useHistory();
    const projectId = useSelector<ReduxObject, string | undefined>(it => it.project.project);
    const locationParams = useParams<{group: string; member?: string}>();
    let group = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    let membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;
    if (group === '-') group = undefined;
    if (membersPage === '-') membersPage = undefined;

    const [projectMembers, setProjectMemberParams, projectMemberParams] = useGlobalCloudAPI<Page<ProjectMember>>(
        "projectManagement",
        membershipSearch({itemsPerPage: 100, page: 0, query: ""}),
        emptyPage
    );

    const [projectDetails, fetchProjectDetails, projectDetailsParams] = useGlobalCloudAPI<UserInProject>(
        "projectManagementDetails",
        {noop: true},
        {
            projectId: projectId ?? "",
            favorite: false,
            needsVerification: false,
            title: projectId ?? "",
            whoami: {username: Client.username ?? "", role: ProjectRole.USER},
            archived: false
        }
    );

    const [groupMembers, fetchGroupMembers, groupMembersParams] = useGlobalCloudAPI<Page<string>>(
        "projectManagementGroupMembers",
        {noop: true},
        emptyPage
    );

    const [groupList, fetchGroupList, groupListParams] = useGlobalCloudAPI<Page<GroupWithSummary>>(
        "projectManagementGroupSummary",
        groupSummaryRequest({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    const [outgoingInvites, fetchOutgoingInvites, outgoingInvitesParams] = useGlobalCloudAPI<Page<OutgoingInvite>>(
        "projectManagementOutgoingInvites",
        listOutgoingInvites({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    const [memberSearchQuery, setMemberSearchQuery] = useGlobal("projectManagementQuery", "");

    if (projectId === undefined) {
        history.push("/");
    }

    const projects = useProjectStatus();
    const projectRole = projects.fetch().membership
        .find(it => it.projectId === projectId)?.whoami?.role ?? ProjectRole.USER;
    const allowManagement = isAdminOrPI(projectRole);
    const reloadProjectStatus = projects.reload;

    return {
        locationParams, projectId: projectId ?? "", group, projectMembers, setProjectMemberParams, groupMembers,
        fetchGroupMembers, groupMembersParams, groupList, fetchGroupList, groupListParams,
        projectMemberParams, memberSearchQuery, setMemberSearchQuery, allowManagement, reloadProjectStatus,
        outgoingInvites, outgoingInvitesParams, fetchOutgoingInvites, membersPage, projectRole,
        projectDetails, projectDetailsParams, fetchProjectDetails
    };
}

const View: React.FunctionComponent<ViewOperations> = props => {
    const {
        projectId,
        group,
        projectMembers,
        setProjectMemberParams,
        projectMemberParams,
        groupMembers,
        fetchGroupMembers,
        fetchGroupList,
        memberSearchQuery,
        groupMembersParams,
        reloadProjectStatus,
        fetchOutgoingInvites,
        outgoingInvitesParams,
        membersPage,
        fetchProjectDetails,
        projectDetailsParams
    } = useProjectManagementStatus();

    const [shouldVerify, setShouldVerifyParams] = useCloudAPI<ShouldVerifyMembershipResponse>(
        shouldVerifyMembership(projectId),
        {shouldVerify: false}
    );

    useEffect(() => {
        if (group !== undefined) {
            fetchGroupMembers(listGroupMembersRequest({group, itemsPerPage: 25, page: 0}));
        } else {
            fetchGroupList(groupSummaryRequest({itemsPerPage: 10, page: 0}));
        }

        reloadProjectStatus();
        fetchOutgoingInvites(listOutgoingInvites({itemsPerPage: 10, page: 0}));
        fetchProjectDetails(viewProject({id: projectId}));
    }, [projectId, group]);

    useEffect(() => {
        setProjectMemberParams(
            membershipSearch({
                ...projectMemberParams.parameters,
                query: memberSearchQuery,
                notInGroup: group
            })
        );
    }, [projectId, group, groupMembers.data, memberSearchQuery]);

    useEffect(() => {
        props.setLoading(projectMembers.loading || groupMembers.loading);
    }, [projectMembers.loading, groupMembers.loading]);

    const reload = useCallback(() => {
        fetchOutgoingInvites(outgoingInvitesParams);
        setProjectMemberParams(projectMemberParams);
        fetchProjectDetails(projectDetailsParams);
        if (group !== undefined) {
            fetchGroupMembers(groupMembersParams);
        }
    }, [projectMemberParams, groupMembersParams, setProjectMemberParams, group]);

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [reload]);

    useEffect(() => {
        if (projectId !== "") {
            props.setActiveProject(projectId);
        }
    }, [projectId]);

    const onApprove = async (): Promise<void> => {
        await callAPIWithErrorHandler(verifyMembership(projectId));
        setShouldVerifyParams(shouldVerifyMembership(projectId));
    };

    const isSettingsPage = membersPage === "settings";

    const memberText = `Members of ${projectId.slice(0, 20).trim()}${projectId.length > 20 ? "..." : ""}`;

    return (
        <MainContainer
            main={(
                <>
                    {!shouldVerify.data.shouldVerify ? null : (
                        <Box backgroundColor="orange" color="white" p={32} m={16}>
                            <Heading.h4>Time for a review!</Heading.h4>

                            <ul>
                                <li>PIs and admins are asked to occasionally review members of their project</li>
                                <li>We ask you to ensure that only the people who need access have access</li>
                                <li>If you find someone who should not have access then remove them by clicking
                                &apos;X&apos; next to their name
                                </li>
                                <li>
                                    When you are done, click below:

                                    <Box mt={8}>
                                        <Button color={"green"} textColor={"white"} onClick={onApprove}>
                                            Everything looks good now
                                        </Button>
                                    </Box>
                                </li>
                            </ul>

                        </Box>
                    )}
                    <TwoColumnLayout>
                        <Box className="members">
                            <Flex mb="12px">
                                <MembersBreadcrumbs embedded={false}>
                                    <span title={"My Projects"}>
                                        <Link to="/projects">
                                            My Projects
                                        </Link>
                                    </span>
                                    <span title={memberText}>
                                        {isSettingsPage ?
                                            <Link to={`/projects/view/${group ? encodeURIComponent(group) : "-"}`}>
                                                {memberText}
                                            </Link> :
                                            memberText
                                        }
                                    </span>
                                    {isSettingsPage ? <span>Settings</span> : null}
                                </MembersBreadcrumbs>
                                <Link to={isSettingsPage ? "/projects/view/" : "/projects/view/-/settings"}>
                                    <Icon
                                        name="properties"
                                        m={8}
                                        color={isSettingsPage ? "blue" : undefined}
                                        hoverColor="blue"
                                        cursor="pointer"
                                    />
                                </Link>
                            </Flex>
                            <Box ml={8} mr={8}>
                                {isSettingsPage ? <ProjectSettings /> : <ProjectMembers />}
                            </Box>
                        </Box>
                        <Box className="groups">
                            <GroupView />
                        </Box>
                    </TwoColumnLayout>
                </>
            )}
        />
    );
};

const TwoColumnLayout = styled.div`
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
    
    & > * {
        flex-basis: 100%;
    }

    & > .groups {
        overflow: auto;
    }
    
    @media screen and (min-width: 1200px) {
        & > .members {
            padding-right: 10px;
            border-right: 2px solid var(--black);
        }

        & {
            height: calc(100vh - 100px);
            overflow: hidden;
        }
        
        & > .members {
            height: 100%;
            flex: 1;
            overflow-y: scroll;
            margin-right: 32px;
        }
        
        & > .groups {
            flex: 1;
            height: 100%;
        }
    }
`;

interface ViewOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ViewOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
});

export default connect(null, mapDispatchToProps)(View);
