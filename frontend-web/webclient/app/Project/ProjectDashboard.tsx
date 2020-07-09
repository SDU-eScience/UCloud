import {MainContainer} from "MainContainer/MainContainer";
import {
    useProjectManagementStatus,
    membersCountRequest,
    groupsCountRequest,
    subprojectsCountRequest,
    ProjectRole,
    isAdminOrPI
} from "Project/index";
import * as React from "react";
import {Box, Button, Link, Flex, theme, Card} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {DashboardCard} from "Dashboard/Dashboard";
import {GridCardGroup} from "ui-components/Grid";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {useCloudAPI} from "Authentication/DataHook";
import {ProductArea, UsageResponse, transformUsageChartForCharting, usage} from "Accounting";
import {creditFormatter, durationOptions} from "./ProjectUsage";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {Dictionary} from "Types";
import styled from "styled-components";
import {ingoingGrantApplications, IngoingGrantApplicationsResponse} from "Project/Grant";
import {emptyPage} from "DefaultObjects";
import {Client} from "Authentication/HttpClientInstance";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const {projectId, projectDetails, projectRole} = useProjectManagementStatus(true);

    function isPersonalProjectActive(id: string): boolean {
        return id === undefined || id === "";
    }

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
        setUsageParams(usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }));
    }, [projectId]);

    const computeCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, ProductArea.COMPUTE));

    const computeCreditsUsedByWallet: Dictionary<Dictionary<number>> = {};
    let computeCreditsUsedInPeriod = 0;

    for (const chart of computeCharts) {
        const usageByCurrentProvider: Dictionary<number> = {};
        computeCreditsUsedByWallet[chart.provider] = usageByCurrentProvider;

        for (let i = 0; i < chart.points.length; i++) {
            let point = chart.points[i];
            for (const category of Object.keys(point)) {
                if (category === "time") continue;

                const currentUsage = usageByCurrentProvider[category] ?? 0;
                usageByCurrentProvider[category] = currentUsage + point[category];
                computeCreditsUsedInPeriod += point[category];
            }
        }
    }

    const storageCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, ProductArea.STORAGE));

    const storageCreditsUsedByWallet: Dictionary<Dictionary<number>> = {};
    let storageCreditsUsedInPeriod = 0;

    for (const chart of storageCharts) {
        const usageByCurrentProvider: Dictionary<number> = {};
        storageCreditsUsedByWallet[chart.provider] = usageByCurrentProvider;

        for (let i = 0; i < chart.points.length; i++) {
            const point = chart.points[i];
            for (const category of Object.keys(point)) {
                if (category === "time") continue;

                const currentUsage = usageByCurrentProvider[category] ?? 0;
                usageByCurrentProvider[category] = currentUsage + point[category];
                storageCreditsUsedInPeriod += point[category];
            }
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
                            <DashboardCard title="Members" icon="user" color={theme.colors.blue} isLoading={false}>
                                <Table>
                                    <tbody>
                                        <TableRow>
                                            <TableCell>Members</TableCell>
                                            <TableCell textAlign="right">{membersCount.data}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell>Groups</TableCell>
                                            <TableCell textAlign="right">{groupsCount.data}</TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                                <DashboardCardButton>
                                    <Link to="/project/members">
                                        <Button width="100%">Manage Members</Button>
                                    </Link>
                                </DashboardCardButton>
                            </DashboardCard>
                        ) : null}
                        <DashboardCard
                            title={Client.hasActiveProject ? "Subprojects" : "Resource Allocation"}
                            icon="projects"
                            color={theme.colors.purple}
                            isLoading={false}
                        >
                            {Client.hasActiveProject ? <Table>
                                <tbody>
                                    <TableRow>
                                        <TableCell>Subprojects</TableCell>
                                        <TableCell textAlign="right">{subprojectsCount.data}</TableCell>
                                    </TableRow>
                                </tbody>
                            </Table> : null}
                            <DashboardCardButton>
                                <Link to="/project/subprojects">
                                    <Button width="100%">Manage Resources</Button>
                                </Link>
                            </DashboardCardButton>
                        </DashboardCard>

                        <DashboardCard title="Usage" subtitle="Past 30 days" icon="hourglass" color={theme.colors.green}
                            isLoading={false}>
                            <Table>
                                <tbody>
                                    <TableRow>
                                        <TableCell>Storage</TableCell>
                                        <TableCell
                                            textAlign="right">{creditFormatter(storageCreditsUsedInPeriod)}</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell>Compute</TableCell>
                                        <TableCell
                                            textAlign="right">{creditFormatter(computeCreditsUsedInPeriod)}</TableCell>
                                    </TableRow>
                                </tbody>
                            </Table>
                            <DashboardCardButton>
                                <Link to="/project/usage">
                                    <Button width="100%">Manage Usage</Button>
                                </Link>
                            </DashboardCardButton>
                        </DashboardCard>
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null :
                            <DashboardCard title="Grant Applications" icon="mail" color={theme.colors.red}
                                isLoading={false}>
                                <Table>
                                    <tbody>
                                        <TableRow>
                                            <TableCell>In Progress</TableCell>
                                            <TableCell textAlign="right">{apps.data.itemsInTotal}</TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                                <DashboardCardButton>
                                    <Link to="/project/grants/ingoing">
                                        <Button width="100%">Manage Applications</Button>
                                    </Link>
                                </DashboardCardButton>
                            </DashboardCard>}
                        {isPersonalProjectActive(projectId) || !isAdminOrPI(projectRole) ? null : (
                            <DashboardCard title="Settings" icon="properties" color={theme.colors.orange}
                                isLoading={false}>
                                <Table>
                                    <tbody>
                                        <TableRow>
                                            <TableCell>Archived</TableCell>
                                            <TableCell
                                                textAlign="right">{projectDetails.data.archived ? "Yes" : "No"}</TableCell>
                                        </TableRow>
                                    </tbody>
                                </Table>
                                <DashboardCardButton>
                                    <Link to="/project/settings">
                                        <Button width="100%">Manage Settings</Button>
                                    </Link>
                                </DashboardCardButton>
                            </DashboardCard>
                        )}
                    </ProjectDashboardGrid>
                </>
            )}
        />
    );
};

const ProjectDashboardGrid = styled(GridCardGroup)`
    & > ${Card} {
        position: relative;
        min-height: 200px;
    }
`;

const DashboardCardButton = styled(Box)`
    position: absolute;  
    bottom: 10px;
    left: 10px;
    right: 10px;
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
