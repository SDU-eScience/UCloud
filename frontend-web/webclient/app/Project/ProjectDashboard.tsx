import {MainContainer} from "MainContainer/MainContainer";
import {useProjectManagementStatus, membersCountRequest, groupsCountRequest, subprojectsCountRequest} from "Project/index";
import * as React from "react";
import {Box, Button, Link, Flex, Icon, theme, Card} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {DashboardCard} from "Dashboard/Dashboard";
import {GridCardGroup} from "ui-components/Grid";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import { useCloudAPI } from "Authentication/DataHook";
import { retrieveBalance, RetrieveBalanceResponse, ProductArea, UsageResponse, transformUsageChartForCharting, usage } from "Accounting";
import { creditFormatter, durationOptions } from "./ProjectUsage";
import Table, { TableCell, TableRow } from "ui-components/Table";
import { Dictionary } from "Types";
import styled from "styled-components";

const ProjectDashboard: React.FunctionComponent<ProjectDashboardOperations> = () => {
    const {projectId, membersPage, projectDetails} = useProjectManagementStatus();

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

    const [balance, fetchBalance, balanceParams] = useCloudAPI<RetrieveBalanceResponse>(
        retrieveBalance({includeChildren: true}),
        {wallets: []}
    );

    const durationOption = durationOptions[3];

    const remainingComputeBalance = balance.data.wallets.reduce((sum, wallet) => {
        if (wallet.area === ProductArea.COMPUTE && wallet.wallet.id === projectId) return sum + wallet.balance;
        else return sum;
    }, 0);

    const remainingStorageBalance = balance.data.wallets.reduce((sum, wallet) => {
        if (wallet.area === ProductArea.STORAGE && wallet.wallet.id === projectId) return sum + wallet.balance;
        else return sum;
    }, 0);

    const now = new Date().getTime();

    const [usageResponse, setUsageParams, usageParams] = useCloudAPI<UsageResponse>(
        usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }),
        {charts: []}
    );

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
            let point = chart.points[i];
            for (const category of Object.keys(point)) {
                if (category === "time") continue;

                const currentUsage = usageByCurrentProvider[category] ?? 0;
                usageByCurrentProvider[category] = currentUsage + point[category];
                storageCreditsUsedInPeriod += point[category];
            }
        }
    }

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

    return (
        <MainContainer
            header={<Flex>
                <ProjectBreadcrumbs crumbs={[]} />
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <ProjectDashboardGrid minmax={300}>
                        {projectId !== undefined && projectId !== "" ? (
                            <>
                                <DashboardCard title="Members" icon="user" color={theme.colors.blue} isLoading={false}>
                                    <Table>
                                        <TableRow>
                                            <TableCell>Members</TableCell>
                                            <TableCell textAlign="right">{membersCount.data}</TableCell>
                                        </TableRow>
                                        <TableRow>
                                            <TableCell>Groups</TableCell>
                                            <TableCell textAlign="right">{groupsCount.data}</TableCell>
                                        </TableRow>
                                    </Table>
                                    <DashboardCardButton>
                                        <Link to="/project/members">
                                            <Button width="100%">Manage Members</Button>
                                        </Link>
                                    </DashboardCardButton>
                                </DashboardCard>
                                <DashboardCard
                                    title="Subprojects"
                                    icon="projects"
                                    color={theme.colors.purple}
                                    isLoading={false}
                                >
                                    <Table>
                                        <TableRow>
                                            <TableCell>Subprojects</TableCell>
                                            <TableCell textAlign="right">{subprojectsCount.data}</TableCell>
                                        </TableRow>
                                    </Table>
                                    <DashboardCardButton>
                                        <Link to="/project/subprojects">
                                            <Button width="100%">Manage Subprojects</Button>
                                        </Link>
                                    </DashboardCardButton>
                                </DashboardCard>
                            </>
                        ) : (null)}
                        <DashboardCard title="Usage" subtitle="Past 30 days" icon="hourglass" color={theme.colors.green} isLoading={false}>
                            <Table>
                                <TableRow>
                                    <TableCell>Storage</TableCell>
                                    <TableCell textAlign="right">{creditFormatter(storageCreditsUsedInPeriod)}</TableCell>
                                </TableRow>
                                <TableRow>
                                    <TableCell>Compute</TableCell>
                                    <TableCell textAlign="right">{creditFormatter(computeCreditsUsedInPeriod)}</TableCell>
                                </TableRow>
                            </Table>
                            <DashboardCardButton>
                                <Link to="/project/usage">
                                    <Button width="100%">Manage Usage</Button>
                                </Link>
                            </DashboardCardButton>
                        </DashboardCard>
                        {isPersonalProjectActive(projectId) ? null : (
                            <DashboardCard title="Settings" icon="properties" color={theme.colors.orange} isLoading={false}>
                                <Table>
                                    <TableRow>
                                        <TableCell>Archived</TableCell>
                                        <TableCell textAlign="right">{projectDetails.data.archived ? "Yes" : "No"}</TableCell>
                                    </TableRow>
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
