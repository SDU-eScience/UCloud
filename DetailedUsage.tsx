import {emptyPage, ReduxObject} from "DefaultObjects";
import {File} from "Files";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Card, Flex, Icon, Link, Text, theme} from "ui-components";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {AccountingOperations, AccountingProps, AccountingStateProps, DetailedUsageProps} from ".";
import {match} from "react-router";
import Table, {TableRow, TableCell, TableHeaderCell, TableHeader} from "ui-components/Table";
import {
  AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts';


function DetailedUsage(props: DetailedUsageProps & {history: History}): JSX.Element {
    const [selectedProject, setSelectedProject] = React.useState<string|null>(null);

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {

    }

    const header = (
        <>
            <Heading.h4><Link to="/accounting/usage">Usage</Link> »</Heading.h4>
            <Heading.h2>{props.match.params.projectId}</Heading.h2>
        </>
    );

    const main = (
        <>
            <Box>
                <Card padding={15} margin={15} ml={0} mr={0}>
                    <Flex>
                        <Box width="25%">
                            <Text paddingTop="9px">Computation</Text>
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
                            <Button onClick={() => setSelectedProject("Personal Project")}>Group usage</Button>
                        </Box>
                    </Flex>
                </Card>
                <Box padding={15} margin={25}>

                    <Heading.h4>Total usage</Heading.h4>
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
                                    <Box width={20} height={20} backgroundColor="pink"></Box>
                                </TableCell>
                                <TableCell>Standard</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell>
                                    <Box width={20} height={20} backgroundColor="lightblue"></Box>
                                </TableCell>
                                <TableCell>High Memory</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                            </TableRow>
                            <TableRow>
                                <TableCell>
                                    <Box width={20} height={20} backgroundColor="lightgreen"></Box>
                                </TableCell>
                                <TableCell>GPU</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                                <TableCell textAlign="right">123</TableCell>
                            </TableRow>
                        </Table>
                    </Box>

                    <Heading.h4>Daily compute usage</Heading.h4>
                    <Box mt={20}>
                        <ResponsiveContainer width="100%" height={200}>
                            <AreaChart
                                data={data1}
                                margin={{
                                top: 10, right: 30, left: 0, bottom: 0,
                                }}
                            >
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis dataKey="name" />
                                <YAxis />
                                <Tooltip />
                                <Area type="monotone" dataKey="standard" stackId="1" stroke="red" fill="pink" />
                                <Area type="monotone" dataKey="high memory" stackId="1" stroke="blue" fill="lightBlue" />
                                <Area type="monotone" dataKey="gpu" stackId="1" stroke="green" fill="lightGreen" />
                            </AreaChart>
                        </ResponsiveContainer>
                    </Box>

                    <Heading.h4>Cumulative compute usage</Heading.h4>
                    <Box mt={20}>
                        <ResponsiveContainer width="100%" height={200}>
                            <AreaChart
                                data={data2}
                                margin={{
                                top: 10, right: 30, left: 0, bottom: 0,
                                }}
                            >
                                <CartesianGrid strokeDasharray="3 3" />
                                <XAxis dataKey="name" />
                                <YAxis />
                                <Tooltip />
                                <Area type="monotone" dataKey="standard" stackId="1" stroke="red" fill="pink" />
                                <Area type="monotone" dataKey="high memory" stackId="1" stroke="blue" fill="lightBlue" />
                                <Area type="monotone" dataKey="gpu" stackId="1" stroke="green" fill="lightGreen" />
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
                            <Button onClick={() => setSelectedProject("Lorem Ipsum Project")}>Group usage</Button>
                        </Box>
                    </Flex>
                </Card>
            </Box>
        </>
    );

    return (<MainContainer main={main} header={header} />);
}


const data1 = [
  {
    name: 'time1', standard: 4000, 'high memory': 2400, gpu: 2400,
  },
  {
    name: 'time2', standard: 3000, 'high memory': 1398, gpu: 2210,
  },
  {
    name: 'time3', standard: 2000, 'high memory': 9800, gpu: 2290,
  },
  {
    name: 'time4', standard: 2780, 'high memory': 3908, gpu: 2000,
  },
  {
    name: 'time5', standard: 1890, 'high memory': 4800, gpu: 2181,
  },
  {
    name: 'time6', standard: 2390, 'high memory': 3800, gpu: 2500,
  },
  {
    name: 'time7', standard: 3490, 'high memory': 4300, gpu: 2100,
  },
];

const data2 = [
  {
    name: 'time1', standard: 1300, 'high memory': 0, gpu: 2000,
  },
  {
    name: 'time2', standard: 1300, 'high memory': 0, gpu: 2000,
  },
  {
    name: 'time3', standard: 2000, 'high memory': 200, gpu: 2290,
  },
  {
    name: 'time4', standard: 2080, 'high memory': 1500, gpu: 2290,
  },
  {
    name: 'time5', standard: 2080, 'high memory': 3000, gpu: 2400,
  },
  {
    name: 'time6', standard: 2390, 'high memory': 3000, gpu: 2181,
  },
  {
    name: 'time7', standard: 3490, 'high memory': 3000, gpu: 2500,
  },   
];




const mapDispatchToProps = (dispatch: Dispatch): AccountingOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Dashboard"));
        dispatch(setActivePage(SidebarPages.None));
    },
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = ({applicationsBrowse}: ReduxObject): AccountingStateProps => ({
});


export default connect(mapStateToProps, mapDispatchToProps)(DetailedUsage);
