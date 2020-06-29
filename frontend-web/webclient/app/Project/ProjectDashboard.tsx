import {MainContainer} from "MainContainer/MainContainer";
import {useProjectManagementStatus, membersCountRequest, groupsCountRequest, subprojectsCountRequest} from "Project/index";
import * as React from "react";
import {Box, Button, Link, Flex, Icon, theme} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {DashboardCard} from "Dashboard/Dashboard";
import {GridCardGroup} from "ui-components/Grid";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import { useCloudAPI } from "Authentication/DataHook";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const {projectId, membersPage} = useProjectManagementStatus();

    function isPersonalProjectActive(projectId: string): boolean {
        return projectId === undefined || projectId === "";
    }

    const [membersCount, setMembersCount] = useCloudAPI<number>(
        membersCountRequest(),
        0
    );

    const [groupsCount, setGroupsCount] = useCloudAPI<number>(
        groupsCountRequest(),
        0
    );

    const [subprojectsCount, setSubprojectsCount] = useCloudAPI<number>(
        subprojectsCountRequest(),
        0
    );

    React.useEffect(() => {
        setMembersCount(membersCountRequest());
        setGroupsCount(groupsCountRequest());
        setSubprojectsCount(subprojectsCountRequest());
    }, []);


    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs crumbs={[]} />
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <GridCardGroup minmax={250}>
                        {projectId !== undefined && projectId !== "" ? (
                            <>
                                <DashboardCard title="Members" icon="user" color={theme.colors.blue} isLoading={false}>
                                    <Box>
                                        {membersCount.data} members
                                    </Box>
                                    <Box>
                                        {groupsCount.data} groups
                                    </Box>
                                    <Box mt={20}>
                                        <Link to="/project/members">
                                            <Button mb="10px" width="100%">Manage Members</Button>
                                        </Link>
                                    </Box>
                                </DashboardCard>
                                <DashboardCard
                                    title="Subprojects"
                                    icon="projects"
                                    color={theme.colors.purple}
                                    isLoading={false}
                                >
                                    <Box>
                                        {subprojectsCount.data} subprojects
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
                        {isPersonalProjectActive(projectId) ? null : (
                            <DashboardCard title="Settings" icon="properties" color={theme.colors.orange} isLoading={false}>
                                <Box mt={68}>
                                    <Link to="/project/settings">
                                        <Button mb="10px" width="100%">Manage Settings</Button>
                                    </Link>
                                </Box>
                            </DashboardCard>
                        )}
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
