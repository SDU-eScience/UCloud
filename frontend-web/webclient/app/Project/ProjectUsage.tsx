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

const ProjectUsage: React.FunctionComponent<ProjectUsageOperations> = props => {
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
        fetchProjectDetails,
        projectDetails,
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
                        <Link to={`/project/dashboard`}>
                            {projectDetails.data.title}
                        </Link>
                    </li>
                    <li>
                        Usage
                    </li>
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

                            <Heading.h5>Daily compute usage</Heading.h5>
                            <Box mt={20} mb={20}>
                                <ResponsiveContainer width="100%" height={200}>
                                    <AreaChart
                                        syncId="someId"
                                        data={data1}
                                        margin={{
                                        top: 10, right: 30, left: 0, bottom: 0,
                                        }}
                                    >
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis dataKey="time" />
                                        <YAxis />
                                        <Tooltip />
                                        <Area type="linear" dataKey="standard" stroke={theme.colors.darkBlue} fill={theme.colors.blue} opacity="0.3" />
                                        <Area type="linear" dataKey="high memory" stroke={theme.colors.darkRed} fill={theme.colors.red} opacity="0.3" />
                                        <Area type="linear" dataKey="gpu" stroke={theme.colors.darkGreen} fill={theme.colors.green} opacity="0.3" />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </Box>

                            <Heading.h5>Cumulative compute usage</Heading.h5>
                            <Box mt={20}>
                                <ResponsiveContainer width="100%" height={200}>
                                    <AreaChart
                                        syncId="someId"
                                        data={data2}
                                        margin={{
                                        top: 10, right: 30, left: 0, bottom: 0,
                                        }}
                                    >
                                        <CartesianGrid strokeDasharray="3 3" />
                                        <XAxis dataKey="name" />
                                        <YAxis />
                                        <Tooltip />
                                        <Area type="linear" dataKey="standard" stackId="1" stroke={theme.colors.darkBlue} fill={theme.colors.blue} opacity="0.3" />
                                        <Area type="linear" dataKey="high memory" stackId="1" stroke={theme.colors.darkRed} fill={theme.colors.red} opacity="0.3" />
                                        <Area type="linear" dataKey="gpu" stackId="1" stroke={theme.colors.darkGreen} fill={theme.colors.green} opacity="0.3" />
                                    </AreaChart>
                                </ResponsiveContainer>
                            </Box>


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

const data1 = [
    {
        time: 'time1', standard: 4000, 'high memory': 2400, gpu: 0,
    },
    {
        time: 'time2', standard: 3000, 'high memory': 1400, gpu: 2210,
    },
    {
        time: 'time3', standard: 2000, 'high memory': 9800, gpu: 2290,
    },
    {
        time: 'time4', standard: 2780, 'high memory': 3900, gpu: 2000,
    },
    {
        time: 'time5', standard: 1890, 'high memory': 4800, gpu: 0,
    },
    {
        time: 'time6', standard: 2390, 'high memory': 3800, gpu: 2500,
    },
    {
        time: 'time7', standard: 3490, 'high memory': 4300, gpu: 0,
    },
  ];
  
  const data2 = [
    {
        name: 'time1', standard: 4000, 'high memory': 2400, gpu: 0,
    },
    {
        name: 'time2', standard: 7000, 'high memory': 3800, gpu: 2210,
    },
    {
        name: 'time3', standard: 9000, 'high memory': 13600, gpu: 5000,
    },
    {
        name: 'time4', standard: 11780, 'high memory': 17500, gpu: 7000,
    },
    {
        name: 'time5', standard: 13870, 'high memory': 22300, gpu: 7000,
    },
    {
        name: 'time6', standard: 16260, 'high memory': 26100, gpu: 9500,
    },
    {
        name: 'time7', standard: 19750, 'high memory': 30400, gpu: 9500,
    },
  ];

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
