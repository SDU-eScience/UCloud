import {MainContainer} from "MainContainer/MainContainer";
import {useProjectManagementStatus,} from "Project/index";
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

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const {projectId, membersPage} = useProjectManagementStatus();

    function isPersonalProjectActive(projectId: string): boolean {
        return projectId === undefined || projectId === "";
    }

    const isSettingsPage = membersPage === "settings";

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs crumbs={[]} />
                <Flex>
                    {isPersonalProjectActive(projectId) ? null : (
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
