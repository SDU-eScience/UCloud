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
import {Box, Button, Link, Flex, Card, Text, theme} from "ui-components";
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
import {ResponsiveContainer, AreaChart, Area, CartesianGrid, XAxis, YAxis, Tooltip} from "recharts";
import Table, {TableHeader, TableHeaderCell, TableCell, TableRow} from "ui-components/Table";
import {transformUsageChartForCharting, usage, UsageResponse} from "Accounting/Compute";
//import {dailyUsage, DailyUsageRequest, DailyUsageResponse, CumulativeUsageResponse, cumulativeUsage} from "Accounting/Compute";

/*async function fetchDailyUsage(projectId: string|undefined): Promise<DailyUsageResponse> {
    const [fetchedDailyUsage] = await useCloudAPI<DailyUsageResponse>(dailyUsage({project: projectId}), {
        chart: {
            'STANDARD': {timestamp: 0, creditsUsed: 0},
            'HIGH_MEMORY': {timestamp: 0, creditsUsed: 0},
            'GPU': {timestamp: 0, creditsUsed: 0}
        }
    });

    return fetchedDailyUsage.data;
}

async function fetchCumulativeUsage(projectId: string|undefined): Promise<CumulativeUsageResponse> {
    const [fetchedCumulativeUsage] = await useCloudAPI<CumulativeUsageResponse>(cumulativeUsage({project: projectId}), {
        chart: {
            'STANDARD': {timestamp: 0, creditsUsed: 0},
            'HIGH_MEMORY': {timestamp: 0, creditsUsed: 0},
            'GPU': {timestamp: 0, creditsUsed: 0}
        }
    });

    return fetchedCumulativeUsage.data;
}*/

// A lot easier to let typescript take care of the details for this one
// eslint-disable-next-line
export function useProjectManagementStatus() {
    const history = useHistory();
    const projectId = useSelector<ReduxObject, string | undefined>(it => it.project.project);
    const locationParams = useParams<{group: string; member?: string}>();

    /*const [dailyUsageData, setDailyUsageData] = React.useState<DailyUsageResponse>({
        chart: {
            'STANDARD': {timestamp: 0, creditsUsed: 0},
            'HIGH_MEMORY': {timestamp: 0, creditsUsed: 0},
            'GPU': {timestamp: 0, creditsUsed: 0}
        }
    });

    const [cumulativeUsageData, setCumulativeUsageData] = React.useState<CumulativeUsageResponse>({
        chart: {
            'STANDARD': {timestamp: 0, creditsUsed: 0},
            'HIGH_MEMORY': {timestamp: 0, creditsUsed: 0},
            'GPU': {timestamp: 0, creditsUsed: 0}
        }
    });*/

    let group = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    let membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;
    if (group === '-') group = undefined;
    if (membersPage === '-') membersPage = undefined;

    const [projectMembers, setProjectMemberParams, projectMemberParams] = useGlobalCloudAPI<Page<ProjectMember>>(
        "projectManagement",
        membershipSearch({itemsPerPage: 100, page: 0, query: ""}),
        emptyPage
    );

    /*useEffect(() => {
        fetchDailyUsage(projectId).then(it => setDailyUsageData(it));
        fetchCumulativeUsage(projectId).then(it => setCumulativeUsageData(it));
    }, [projectId]);*/


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

function dateFormatter(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth()}/${date.getFullYear()} ` +
        `${date.getHours().toString().padStart(2, "0")}:` +
        `${date.getMinutes().toString().padStart(2, "0")}:` +
        `${date.getSeconds().toString().padStart(2, "0")}`;
}

const ProjectUsage: React.FunctionComponent<ProjectUsageOperations> = props => {
    const {projectId, projectDetails} = useProjectManagementStatus();

    const reload = useCallback(() => {

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

    const title = projectDetails.data.title;
    const projectText = `${title.slice(0, 20).trim()}${title.length > 20 ? "..." : ""}`;


    const now = new Date().getTime();
    const [usageResponse, setUsageParams, usageParams] = useCloudAPI<UsageResponse>(
        usage({
            bucketSize: 1000 * 60 * 60,
            periodStart: now - (1000 * 60 * 60 * 24 * 7),
            periodEnd: now
        }),
        {charts: []}
    );

    const charts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it));
    console.log(charts);

    return (
        <MainContainer
            header={<Flex>
                <MembersBreadcrumbs>
                    <li><Link to="/projects">My Projects</Link></li>
                    <li><Link to={`/project/dashboard`}>{projectText}</Link></li>
                    <li>Usage</li>
                </MembersBreadcrumbs>
            </Flex>}
            sidebar={null}
            main={(
                <>
                    <Box>
                        <Card padding={15} margin={15} ml={0} mr={0}>
                            <Flex>
                                <Box width="25%">
                                    <Heading.h4 paddingTop="5px">Computation</Heading.h4>
                                </Box>
                                <Box width="25%" textAlign="center">
                                    <Text>123</Text>
                                    <Text color="gray" fontSize={12}>CREDITS USED</Text>
                                </Box>
                                <Box width="25%" textAlign="center">
                                    <Text>123M</Text>
                                    <Text color="gray" fontSize={12}>CREDITS REMAINING</Text>
                                </Box>
                                <Box width="25%" textAlign="right">
                                    <Button>Details</Button>
                                </Box>
                            </Flex>
                        </Card>
                        <Box padding={15} margin={25}>

                            <Heading.h5>Total usage</Heading.h5>
                            <Box mb={40}>
                                <Table>
                                    <TableHeader>
                                        <TableHeaderCell width={30}></TableHeaderCell>
                                        <TableHeaderCell></TableHeaderCell>
                                        <TableHeaderCell textAlign="right">Credits Used</TableHeaderCell>
                                        <TableHeaderCell textAlign="right">Remaining</TableHeaderCell>
                                    </TableHeader>
                                    <TableRow>
                                        <TableCell>
                                            <Box width={20} height={20} backgroundColor={theme.colors.blue} opacity={0.3}></Box>
                                        </TableCell>
                                        <TableCell>Standard</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell>
                                            <Box width={20} height={20} backgroundColor={theme.colors.red} opacity={0.3}></Box>
                                        </TableCell>
                                        <TableCell>High Memory</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                    </TableRow>
                                    <TableRow>
                                        <TableCell>
                                            <Box width={20} height={20} backgroundColor={theme.colors.green} opacity={0.3}></Box>
                                        </TableCell>
                                        <TableCell>GPU</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                        <TableCell textAlign="right">123</TableCell>
                                    </TableRow>
                                </Table>
                            </Box>

                            {charts.map(chart => (
                                <>
                                    <Heading.h5>Usage for {chart.provider}</Heading.h5>
                                    <Box mt={20} mb={20}>
                                        <ResponsiveContainer width="100%" height={200}>
                                            <AreaChart
                                                syncId="someId"
                                                data={chart.points}
                                                margin={{
                                                    top: 10, right: 30, left: 0, bottom: 0,
                                                }}
                                            >
                                                <CartesianGrid strokeDasharray="3 3" />
                                                <XAxis dataKey="time" tickFormatter={dateFormatter} />
                                                <YAxis />
                                                <Tooltip labelFormatter={dateFormatter} />
                                                {chart.lineNames.map((id, idx) => (
                                                    <Area
                                                        key={id}
                                                        type="linear"
                                                        dataKey={id}
                                                        stroke={theme.appColors[idx][1]}
                                                        fill={theme.appColors[idx][2]}
                                                        opacity="0.3"
                                                    />
                                                ))}
                                            </AreaChart>
                                        </ResponsiveContainer>
                                    </Box>
                                </>
                            ))}

                       </Box>

                        <Card padding={15} margin={15} ml={0} mr={0}>
                            <Flex>
                                <Box width="25%">
                                    <Text paddingTop="9px">Storage</Text>
                                </Box>
                                <Box width="25%" textAlign="center">
                                    <Text>123</Text>
                                    <Text color="gray" fontSize={12}>CREDITS USED</Text>
                                </Box>
                                <Box width="25%" textAlign="center">
                                    <Text>123M</Text>
                                    <Text color="gray" fontSize={12}>CREDITS REMAINING</Text>
                                </Box>
                                <Box width="25%" textAlign="right">
                                    <Button>Details</Button>
                                </Box>
                            </Flex>
                        </Card>
                    </Box>
                </>
            )}
        />
    );
};

interface ProjectUsageOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
    setActiveProject: (project: string) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ProjectUsageOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading)),
    setActiveProject: project => dispatchSetProjectAction(dispatch, project),
});

export default connect(null, mapDispatchToProps)(ProjectUsage);
