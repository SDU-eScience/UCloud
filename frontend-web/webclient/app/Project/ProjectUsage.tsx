import {useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useEffect} from "react";
import {Box, Button, Flex, Card, Text, theme} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {ResponsiveContainer, CartesianGrid, XAxis, YAxis, Tooltip, Line, LineChart} from "recharts";
import Table, {TableHeader, TableHeaderCell, TableCell, TableRow} from "ui-components/Table";
import {transformUsageChartForCharting, usage, UsageResponse} from "Accounting/Compute";
import {useProjectManagementStatus} from "Project";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";

function dateFormatter(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} ` +
        `${date.getHours().toString().padStart(2, "0")}:` +
        `${date.getMinutes().toString().padStart(2, "0")}:` +
        `${date.getSeconds().toString().padStart(2, "0")}`;
}

function creditFormatter(credits: number): string {
    let s = credits.toString();
    const a = s.substr(0, s.length - 4);

    let before = a.substr(0, a.length - 2);
    let after = a.substr(a.length - 2);
    if (before === "") before = "0";
    if (after === "") after = "0";
    after = after.padStart(2, "0");

    let beforeFormatted = "";
    {
        const chunksInTotal = Math.ceil(before.length / 3);
        let offset = 0;
        for (let i = 0; i < chunksInTotal; i++) {
            if (i === 0) {
                let firstChunkSize = before.length % 3;
                if (firstChunkSize === 0) firstChunkSize = 3;
                beforeFormatted += before.substr(0, firstChunkSize);
                offset += firstChunkSize;
            } else {
                beforeFormatted += '.';
                beforeFormatted += before.substr(offset, offset + 3);
                offset += 3;
            }
        }
    }

    return `${beforeFormatted},${after} DKK`;
}

const ProjectUsage: React.FunctionComponent<ProjectUsageOperations> = props => {
    const {...projectManagement} = useProjectManagementStatus();

    const now = new Date().getTime();
    const [usageResponse, setUsageParams, usageParams] = useCloudAPI<UsageResponse>(
        usage({
            bucketSize: 1000 * 60 * 60 * 24,
            periodStart: now - (1000 * 60 * 60 * 24 * 31),
            periodEnd: now
        }),
        {charts: []}
    );

    useEffect(() => {
        props.setRefresh(() => {
            projectManagement.reload();
            setUsageParams({...usageParams, reloadId: Math.random()});
        });
        return () => props.setRefresh();
    }, [projectManagement.reload]);

    const charts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it));

    return (
        <MainContainer
            header={<ProjectBreadcrumbs crumbs={[{title: "Usage"}]} />}
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


                            {charts.map(chart => (
                                <React.Fragment key={chart.provider}>
                                    <Heading.h5>Total usage</Heading.h5>
                                    <Box mb={40}>
                                        <Table>
                                            <TableHeader>
                                                <TableRow>
                                                    <TableHeaderCell width={30}/>
                                                    <TableHeaderCell/>
                                                    <TableHeaderCell textAlign="right">Credits Used</TableHeaderCell>
                                                    <TableHeaderCell textAlign="right">Remaining</TableHeaderCell>
                                                </TableRow>
                                            </TableHeader>
                                            <tbody>
                                                {chart.lineNames.map((p, idx) => (
                                                    <TableRow key={p}>
                                                        <TableCell>
                                                            <Box width={20} height={20}
                                                                 backgroundColor={theme.chartColors[idx]}/>
                                                        </TableCell>
                                                        <TableCell>{p}</TableCell>
                                                        <TableCell textAlign="right">NOT YET IMPLEMENTED</TableCell>
                                                        <TableCell textAlign="right">NOT YET IMPLEMENTED</TableCell>
                                                    </TableRow>
                                                ))}
                                            </tbody>
                                        </Table>
                                    </Box>

                                    <Heading.h5>Usage for {chart.provider}</Heading.h5>
                                    <Box mt={20} mb={20}>
                                        <ResponsiveContainer width="100%" height={200}>
                                            <LineChart
                                                syncId="someId"
                                                data={chart.points}
                                                margin={{
                                                    top: 10, right: 30, left: 0, bottom: 0,
                                                }}
                                            >
                                                <CartesianGrid strokeDasharray="3 3"/>
                                                <XAxis dataKey="time" tickFormatter={dateFormatter}/>
                                                <YAxis width={150} tickFormatter={creditFormatter}/>
                                                <Tooltip labelFormatter={dateFormatter} formatter={creditFormatter}/>
                                                {chart.lineNames.map((id, idx) => (
                                                    <Line
                                                        key={id}
                                                        type="linear"
                                                        dataKey={id}
                                                        stroke={theme.chartColors[idx]}
                                                        dot={false}
                                                    />
                                                ))}
                                            </LineChart>
                                        </ResponsiveContainer>
                                    </Box>
                                </React.Fragment>
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
