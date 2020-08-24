import {MainContainer} from "MainContainer/MainContainer";
import {
    useProjectManagementStatus,
    membersCountRequest,
    groupsCountRequest,
    subprojectsCountRequest
} from "Project";
import * as React from "react";
import {Flex, Card, Icon, Text, Box} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {DashboardCard} from "Dashboard/Dashboard";
import {GridCardGroup} from "ui-components/Grid";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {useCloudAPI} from "Authentication/DataHook";
import {ProductArea, UsageResponse, transformUsageChartForCharting, usage, NativeChart} from "Accounting";
import {creditFormatter, durationOptions} from "./ProjectUsage";
import Table, {TableCell, TableRow} from "ui-components/Table";
import styled from "styled-components";
import {
    ingoingGrantApplications, IngoingGrantApplicationsResponse, ProjectGrantSettings, readGrantRequestSettings
} from "Project/Grant";
import {emptyPage} from "DefaultObjects";
import {Client} from "Authentication/HttpClientInstance";
import {useHistory} from "react-router";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";
import {isAdminOrPI} from "Utilities/ProjectUtilities";

function computeUsageInPeriod(charts: NativeChart[]): number {
    let result = 0;

    for (const chart of charts) {
        const usageByCurrentProvider: Record<string, number> = {};

        for (const point of chart.points) {
            for (const category of Object.keys(point)) {
                if (category === "time") continue;

                const currentUsage = usageByCurrentProvider[category] ?? 0;
                usageByCurrentProvider[category] = currentUsage + point[category];
                result += point[category];
            }
        }
    }

    return result;
}

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

    const [subprojectsCount, setSubprojectsCount] = useCloudAPI<number>(
        {noop: true},
        0
    );

    const [apps, setGrantParams] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPage
    );

    const [settings, fetchSettings] = useCloudAPI<ProjectGrantSettings>(
        {noop: true},
        {allowRequestsFrom: [], automaticApproval: {from: [], maxResources: []}}
    );

    const durationOption = durationOptions[3];
    const now = new Date().getTime();

    const [usageResponse, setUsageParams] = useCloudAPI<UsageResponse>(
        {noop: true},
        {charts: []}
    );

    React.useEffect(() => {
        setMembersCount(membersCountRequest());
        setGroupsCount(groupsCountRequest());
        setSubprojectsCount(subprojectsCountRequest());
        setGrantParams(ingoingGrantApplications({itemsPerPage: apps.data.itemsPerPage, page: apps.data.pageNumber}));
        fetchSettings(readGrantRequestSettings({projectId}));
        setUsageParams(usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }));
    }, [projectId]);

    const computeCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, ProductArea.COMPUTE));
    const computeCreditsUsedInPeriod = computeUsageInPeriod(computeCharts);
    const storageCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, ProductArea.STORAGE));
    const storageCreditsUsedInPeriod = computeUsageInPeriod(storageCharts);

    function isAdmin(): boolean {
        if (membersCount.error?.statusCode == 403) {
            return false;
        } else if (groupsCount.error?.statusCode == 403) {
            return false;
        } else if (subprojectsCount.error?.statusCode == 403) {
            return false;
        } else { return true }
    }

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs allowPersonalProject crumbs={[]}/>
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <ProjectDashboardGrid minmax={300}>
                        {projectId !== undefined && projectId !== "" ? (
                            <DashboardCard
                                subtitle={<RightArrow/>}
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
                                    </tbody> ) : null }
                                </Table>
                                {projectDetails.data.needsVerification ?
                                    <Box mt={16}><Icon name={"warning"} mr={"4px"}/> Attention required</Box> :
                                    null
                                }
                            </DashboardCard>
                        ) : null}
                        <DashboardCard
                            title={"Resource Allocation"}
                            icon="projects"
                            color="purple"
                            isLoading={false}
                            onClick={() => history.push("/project/subprojects")}
                            subtitle={<RightArrow/>}
                        >
                            {Client.hasActiveProject ? <Table>
                                {isAdmin() ? (
                                <tbody>
                                <TableRow cursor="pointer">
                                    <TableCell>Subprojects</TableCell>
                                    <TableCell textAlign="right">{subprojectsCount.data}</TableCell>
                                </TableRow>
                                </tbody> ) : null }
                            </Table> : null}
                        </DashboardCard>

                        <DashboardCard title="Usage" icon="hourglass" color="green"
                                       isLoading={false}
                                       subtitle={<RightArrow/>}
                                       onClick={() => history.push("/project/usage")}
                        >
                            <Text color="darkGray" fontSize={1}>Past 30 days</Text>
                            <Table>
                                <tbody>
                                <TableRow cursor="pointer">
                                    <TableCell>Storage</TableCell>
                                    <TableCell
                                        textAlign="right">{creditFormatter(storageCreditsUsedInPeriod)}</TableCell>
                                </TableRow>
                                <TableRow cursor="pointer">
                                    <TableCell>Compute</TableCell>
                                    <TableCell
                                        textAlign="right">{creditFormatter(computeCreditsUsedInPeriod)}</TableCell>
                                </TableRow>
                                </tbody>
                            </Table>
                        </DashboardCard>
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) || noSubprojectsAndGrantsAreDisallowed(subprojectsCount.data, settings.data) ? null :
                            <DashboardCard
                                subtitle={<RightArrow/>}
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
                                        <TableCell textAlign="right">{apps.data.itemsInTotal}</TableCell>
                                    </TableRow>
                                    </tbody>
                                </Table>
                            </DashboardCard>}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null : (
                            <DashboardCard
                                subtitle={<RightArrow/>}
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
                            </DashboardCard>
                        )}
                    </ProjectDashboardGrid>
                </>
            )}
        />
    );
};

const RightArrow = (): JSX.Element => (
    <Icon name="arrowDown" rotation={-90} size={18} color={"darkGray"}/>
);

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
            transform: scale(1.02);
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
