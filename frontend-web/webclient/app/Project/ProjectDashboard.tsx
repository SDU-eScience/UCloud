import {callAPIWithErrorHandler, useCloudAPI, useGlobalCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import {
    listOutgoingInvites,
    OutgoingInvite,
    ProjectMember,
    ProjectRole,
    UserInProject,
    viewProject,
    useProjectManagementStatus,
} from "Project/index";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useCallback, useEffect} from "react";
import {useHistory, useParams} from "react-router";
import {Box, Button, Link, Flex, Icon, theme} from "ui-components";
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
import {GroupWithSummary} from "./GroupList";
import {MembersBreadcrumbs} from "./MembersPanel";
import {Page} from "Types";
import {emptyPage, ReduxObject} from "DefaultObjects";
import {useGlobal} from "Utilities/ReduxHooks";
import {dispatchSetProjectAction} from "Project/Redux";
import {useProjectStatus} from "Project/cache";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {Client} from "Authentication/HttpClientInstance";
import {DashboardCard} from "Dashboard/Dashboard";
import {GridCardGroup} from "ui-components/Grid";
import {shortUUID} from "UtilityFunctions";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = props => {
    const {
        projectId,
        group,
        fetchGroupMembers,
        fetchGroupList,
        reloadProjectStatus,
        fetchOutgoingInvites,
        membersPage,
        fetchProjectDetails,
        projectDetailsParams,
        projectDetails
    } = useProjectManagementStatus();

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

    const reload = useCallback(() => {
        fetchProjectDetails(projectDetailsParams);
    }, []);

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, [reload]);

    useEffect(() => {
        if (projectId !== "") {
            props.setActiveProject(projectId);
        }
    }, [projectId]);

    function isPersonalProjectActive(projectId: string): boolean {
        return projectId === undefined || projectId === "";
    }

    const isSettingsPage = membersPage === "settings";

    return (
        <MainContainer
            header={<Flex>
                <MembersBreadcrumbs>
                    <li>
                        <Link to="/projects">
                            My Projects
                        </Link>
                    </li>
                    <li>
                        {projectDetails.data.title}
                    </li>
                    {isSettingsPage ? <li>Settings</li> : null}
                </MembersBreadcrumbs>
                <Flex>
                    {isPersonalProjectActive(projectId) ? (null) : (
                        <Link to={"/project/settings"}>
                            <Icon
                                name="properties"
                                m={8}
                                color={isSettingsPage ? "blue" : undefined}
                                hoverColor="blue"
                                cursor="pointer"
                            />
                        </Link>
                    )}
                </Flex>
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <GridCardGroup minmax={250}>
                        {projectId !== undefined && projectId !== "" ? (
                            <>
                                <DashboardCard title="Members" icon="user" color={theme.colors.blue} isLoading={false}>
                                    <Box>
                                        123 members
                                    </Box>
                                    <Box>
                                        12 groups
                                    </Box>
                                    <Box mt={20}>
                                        <Link to="/project/members">
                                            <Button mb="10px" width="100%">Manage Members</Button>
                                        </Link>
                                    </Box>
                                </DashboardCard>
                                <DashboardCard title="Subprojects" icon="projects"  color={theme.colors.purple} isLoading={false}>
                                    <Box>
                                        123 subprojects
                                    </Box>
                                    <Box mt={44}>
                                        <Link to="/project/subprojects">
                                            <Button mb="10px" width="100%">Manage Subprojects</Button>
                                        </Link>
                                    </Box>
                                </DashboardCard>
                            </>
                        ) : (null)}
                        <DashboardCard title="Usage" icon="hourglass" color={theme.colors.green} isLoading={false}>
                            <Box>
                                123 TB used
                            </Box>
                            <Box>
                                123 credits remaining
                            </Box>
                            <Box mt={20}>
                                <Link to="/project/usage">
                                    <Button mb="10px" width="100%">Manage Usage</Button>
                                </Link>
                            </Box>
                        </DashboardCard>
                    </GridCardGroup>
                </>
            )}
        />
    );
};

interface ProjectDashboardOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ProjectDashboardOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
});

export default connect(null, mapDispatchToProps)(ProjectDashboard);
