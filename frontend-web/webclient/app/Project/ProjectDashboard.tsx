import {MainContainer} from "@/MainContainer/MainContainer";
import {
    useProjectManagementStatus,
    membersCountRequest,
    groupsCountRequest,
    Project,
    listSubprojects
} from "@/Project";
import * as React from "react";
import {Flex, Card, Icon, Box} from "@/ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {loadingAction} from "@/Loading";
import {dispatchSetProjectAction} from "@/Project/Redux";
import {GridCardGroup} from "@/ui-components/Grid";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {useCloudAPI} from "@/Authentication/DataHook";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import styled from "styled-components";
import {IngoingGrantApplicationsResponse, ProjectGrantSettings, readGrantRequestSettings} from "@/Project/Grant";
import {emptyPage} from "@/DefaultObjects";
import {useHistory} from "react-router";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "@/ui-components/Sidebar";
import {isAdminOrPI} from "@/Utilities/ProjectUtilities";
import * as UCloud from "@/UCloud";
import HighlightedCard from "@/ui-components/HighlightedCard";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const {projectId, projectDetails, projectRole} =
        useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    function isPersonalProjectActive(id: string): boolean {
        return id === undefined || id === "";
    }

    useTitle("Project Dashboard");
    useSidebarPage(SidebarPages.Projects);

    const history = useHistory();

    const [membersCount, setMembersCount] = useCloudAPI<number>(
        {noop: true},
        0
    );

    const [groupsCount, setGroupsCount] = useCloudAPI<number>(
        {noop: true},
        0
    );

    const [subprojects, setSubprojects] = useCloudAPI<Page<Project>>(
        {noop: true},
        emptyPage
    );

    const [apps, setGrantParams] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPage
    );

    const [settings, fetchSettings] = useCloudAPI<ProjectGrantSettings>(
        {noop: true},
        {allowRequestsFrom: [], automaticApproval: {from: [], maxResources: []}, excludeRequestsFrom: []}
    );

    React.useEffect(() => {
        setMembersCount(membersCountRequest());
        setGroupsCount(groupsCountRequest());
        setSubprojects(listSubprojects({itemsPerPage: 10}));
        setGrantParams(UCloud.grant.grant.ingoingApplications({filter: "ACTIVE", itemsPerPage: apps.data.itemsPerPage}));
        fetchSettings(readGrantRequestSettings({projectId}));
    }, [projectId]);

    function isAdmin(): boolean {
        if (membersCount.error?.statusCode === 403) {
            return false;
        } else if (groupsCount.error?.statusCode === 403) {
            return false;
        } else if (subprojects.error?.statusCode === 403) {
            return false;
        } else {
            return true;
        }
    }

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs allowPersonalProject crumbs={[]} />
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <ProjectDashboardGrid minmax={300}>
                        {projectId !== undefined && projectId !== "" ? (
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/members")}
                                title="Members"
                                icon="user"
                                color="blue"
                                isLoading={false}
                            >
                                <Table>
                                    {isAdmin() ? (
                                        <tbody>
                                            <TableRow cursor="pointer">
                                                <TableCell>Members</TableCell>
                                                <TableCell textAlign="right">{membersCount.data}</TableCell>
                                            </TableRow>
                                            <TableRow cursor="pointer">
                                                <TableCell>Groups</TableCell>
                                                <TableCell textAlign="right">{groupsCount.data}</TableCell>
                                            </TableRow>
                                        </tbody>) : null}
                                </Table>
                                {projectDetails.data.needsVerification ?
                                    <Box color="red" mt={16}><Icon name="warning" mr="4px" /> Attention required</Box> :
                                    null
                                }
                            </HighlightedCard>
                        ) : null}
                        <HighlightedCard
                            title={"Resources and Usage"}
                            icon="grant"
                            color="purple"
                            isLoading={false}
                            onClick={() => history.push("/project/resources")}
                            subtitle={<RightArrow />}
                        >
                        </HighlightedCard>

                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) || noSubprojectsAndGrantsAreDisallowed(subprojects.data.itemsInTotal, settings.data) ? null :
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/grants/ingoing")}
                                title="Grant Applications"
                                icon="mail"
                                color="red"
                                isLoading={false}
                            >
                                <Table>
                                    <tbody>
                                        <TableRow cursor="pointer">
                                            <TableCell>In Progress</TableCell>
                                            <TableCell textAlign="right">{apps.data.items.length}+</TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                            </HighlightedCard>}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null : (
                            <HighlightedCard
                                subtitle={<RightArrow />}
                                onClick={() => history.push("/project/settings")}
                                title="Settings"
                                icon="properties"
                                color="orange"
                                isLoading={false}
                            >
                                <Table>
                                    <tbody>
                                        <TableRow cursor="pointer">
                                            <TableCell>Archived</TableCell>
                                            <TableCell textAlign="right">
                                                {projectDetails.data.archived ? "Yes" : "No"}
                                            </TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                            </HighlightedCard>
                        )}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null :
                            <HighlightedCard
                                subtitle={<RightArrow/>}
                                onClick={() => history.push(`/subprojects?subproject=${projectId}`)}
                                title="Subprojects"
                                icon="projects"
                                color="green"
                            />
                        }
                    </ProjectDashboardGrid>
                </>
            )}
        />
    );
};

export function RightArrow(): JSX.Element {
    return (
        <Icon name="arrowDown" rotation={-90} size={18} color={"darkGray"} />
    );
}

function noSubprojectsAndGrantsAreDisallowed(
    subprojects: number,
    settings: ProjectGrantSettings
): boolean {
    return settings.allowRequestsFrom.length === 0 && subprojects === 0;
}

const ProjectDashboardGrid = styled(GridCardGroup)`
    & > ${Card} {
        position: relative;
        min-height: 200px;
        cursor: pointer;
        transition: transform 0.2s;
        &:hover {
            transform: translateY(-2px);
        }
    }
`;

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
