import {APICallState, useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useEffect, useState} from "react";
import {Box, Card, Flex, Icon, Text, theme} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis} from "recharts";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {
    ProductArea,
    productAreaTitle,
    retrieveBalance,
    RetrieveBalanceResponse,
    transformUsageChartForCharting,
    usage,
    UsageResponse
} from "Accounting";
import {useProjectManagementStatus} from "Project";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import styled from "styled-components";
import {ThemeColor} from "ui-components/theme";
import {Toggle} from "ui-components/Toggle";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Client} from "Authentication/HttpClientInstance";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";

function dateFormatter(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} ` +
        `${date.getHours().toString().padStart(2, "0")}:` +
        `${date.getMinutes().toString().padStart(2, "0")}`
}

function dateFormatterDay(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} `
}

function dateFormatterMonth(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getMonth() + 1}/${date.getFullYear()} `
}

function getDateFormatter(duration: Duration): (timestamp: number) => string {
    switch (duration.text) {
        case "Past 14 days":
        case "Past 30 days":
        case "Past 180 days":
            return dateFormatterDay;
        case "Past 365 days":
            return dateFormatterMonth;
        case "Past 7 days":
        case "Today":
        default:
            return dateFormatter;
    }
}

export function creditFormatter(credits: number, precision: number = 2): string {
    if (precision < 0 || precision > 6) throw "Precision must be in 0..6";

    // Edge-case handling
    if (credits < 0) {
        return "-" + creditFormatter(-credits);
    } else if (credits === 0) {
        return "0 DKK";
    } else if (credits < Math.pow(10, 6 - precision)) {
        if (precision === 0) return "< 1 DKK";
        let builder = "< 0,";
        for (let i = 0; i < precision - 1; i++) builder += "0";
        builder += "1 DKK";
        return builder;
    }

    // Group into before and after decimal separator
    const stringified = credits.toString().padStart(6, "0");

    let before = stringified.substr(0, stringified.length - 6);
    let after = stringified.substr(stringified.length - 6);
    if (before === "") before = "0";
    if (after === "") after = "0";
    after = after.padStart(precision, "0");
    after = after.substr(0, precision);

    // Truncate trailing zeroes (but keep at least two)
    if (precision > 2) {
        let firstZeroAt = -1;
        for (let i = 2; i < after.length; i++) {
            if (after[i] === "0") {
                if (firstZeroAt === -1) firstZeroAt = i;
            } else {
                firstZeroAt = -1;
            }
        }

        if (firstZeroAt !== -1) { // We have trailing zeroes
            after = after.substr(0, firstZeroAt);
        }
    }

    // Thousand separator
    const beforeFormatted = addThousandSeparators(before);

    if (after === "") return `${beforeFormatted} DKK`;
    else return `${beforeFormatted},${after} DKK`;
}

export function addThousandSeparators(numberOrString: string | number): string {
    const numberAsString = typeof numberOrString === "string" ? numberOrString : numberOrString.toString(10);
    let result = "";
    const chunksInTotal = Math.ceil(numberAsString.length / 3);
    let offset = 0;
    for (let i = 0; i < chunksInTotal; i++) {
        if (i === 0) {
            let firstChunkSize = numberAsString.length % 3;
            if (firstChunkSize === 0) firstChunkSize = 3;
            result += numberAsString.substr(0, firstChunkSize);
            offset += firstChunkSize;
        } else {
            result += '.';
            result += numberAsString.substr(offset, 3);
            offset += 3;
        }
    }
    return result;
}

interface Duration {
    text: string;
    bucketSize: number;
    bucketSizeText: string;
    timeInPast: number;
}

export const durationOptions: Duration[] = [
    {
        text: "Today",
        bucketSize: 1000 * 60 * 60,
        bucketSizeText: "every hour",
        timeInPast: 1000 * 60 * 60 * 24
    },
    {
        text: "Past week",
        bucketSize: 1000 * 60 * 60 * 12,
        bucketSizeText: "every 12 hours",
        timeInPast: 1000 * 60 * 60 * 24 * 7
    },
    {
        text: "Past 14 days",
        bucketSize: 1000 * 60 * 60 * 24,
        bucketSizeText: "every day",
        timeInPast: 1000 * 60 * 60 * 24 * 14
    },
    {
        text: "Past 30 days",
        bucketSize: 1000 * 60 * 60 * 24 * 2,
        bucketSizeText: "every other day",
        timeInPast: 1000 * 60 * 60 * 24 * 30
    },
    {
        text: "Past 180 days",
        bucketSize: 1000 * 60 * 60 * 24 * 14,
        bucketSizeText: "every other week",
        timeInPast: 1000 * 60 * 60 * 24 * 180
    },
    {
        text: "Past 365 days",
        bucketSize: 1000 * 60 * 60 * 24 * 30,
        bucketSizeText: "every 30 days",
        timeInPast: 1000 * 60 * 60 * 24 * 365
    },
];

const ProjectUsage: React.FunctionComponent<ProjectUsageOperations> = props => {
    const {projectId, reload} = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    useTitle("Usage");
    useSidebarPage(SidebarPages.Projects);

    const [durationOption, setDurationOption] = useState<Duration>(durationOptions[3]);

    // ProductArea -> Provider -> LineName -> includeInChartStatus (default: true)
    const [includeInCharts, setIncludeInCharts] = useState<Record<string, Record<string, Record<string, boolean>>>>({});

    const computeIncludeInCharts: Record<string, Record<string, boolean>> = includeInCharts[ProductArea.COMPUTE] ?? {};
    const storageIncludeInCharts: Record<string, Record<string, boolean>> = includeInCharts[ProductArea.STORAGE] ?? {};

    const onIncludeInChart = (area: ProductArea) => (provider: string, lineName: string) => {
        const existingAtProvider: Record<string, boolean> = (includeInCharts[area] ?? {})[provider] ?? {};
        const newIncludeAtProvider: Record<string, boolean> = {...existingAtProvider};
        newIncludeAtProvider[lineName] = !(existingAtProvider[lineName] ?? true);

        const newComputeInclude = {...computeIncludeInCharts};
        newComputeInclude[provider] = newIncludeAtProvider;

        const newState = {...includeInCharts};
        newState[area] = newComputeInclude;

        setIncludeInCharts(newState);
    };

    function periodStartFunction(time: Date, duration: Duration): number {
        switch (duration.text) {
            case "Today":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate(),
                    time.getHours() + 1,
                    0,
                    0
                ).getTime();
            case "Past week":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate(),
                    time.getHours() + 1,
                    0,
                    0
                ).getTime();
            case "Past 14 days":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate() + 1,
                    0,
                    0,
                    0
                ).getTime();
            case "Past 30 days":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate() + 1,
                    0,
                    0,
                    0
                ).getTime();
            case "Past 180 days":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate() + 1,
                    0,
                    0,
                    0
                ).getTime();
            case "Past 365 days":
                return new Date(
                    time.getFullYear(),
                    time.getMonth(),
                    time.getDate() + 1,
                    0,
                    0,
                    0
                ).getTime();
            default:
                return time.getTime();
        }
    }

    const currentTime = new Date();
    const now = periodStartFunction(currentTime, durationOption);

    const [balance, fetchBalance, balanceParams] = useCloudAPI<RetrieveBalanceResponse>(
        retrieveBalance({includeChildren: true}),
        {wallets: []}
    );

    const [usageResponse, setUsageParams, usageParams] = useCloudAPI<UsageResponse>(
        usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }),
        {charts: []}
    );

    useEffect(() => {
        setUsageParams(usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }));

        setIncludeInCharts({});
    }, [durationOption]);

    useEffect(() => {
        props.setRefresh(() => {
            reload();
            setUsageParams({...usageParams, reloadId: Math.random()});
            fetchBalance({...balanceParams, reloadId: Math.random()});
        });
        return () => props.setRefresh();
    }, [reload]);

    return (
        <MainContainer
            header={
                <Flex>
                    <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Usage"}]}/>
                    <ClickableDropdown
                        trigger={<Heading.h4>{durationOption.text} <Icon name={"chevronDown"} size={16}/></Heading.h4>}
                        onChange={opt => setDurationOption(durationOptions[parseInt(opt)])}
                        options={durationOptions.map((it, idx) => {
                            return {text: it.text, value: `${idx}`};
                        })}
                    />
                </Flex>
            }
            sidebar={null}
            main={(
                <>
                    <VisualizationForArea
                        area={ProductArea.COMPUTE}
                        projectId={projectId}
                        usageResponse={usageResponse}
                        durationOption={durationOption}
                        balance={balance}
                        includeInCharts={computeIncludeInCharts}
                        onIncludeInChart={onIncludeInChart(ProductArea.COMPUTE)}
                    />
                    <VisualizationForArea
                        area={ProductArea.STORAGE}
                        projectId={projectId}
                        usageResponse={usageResponse}
                        durationOption={durationOption}
                        balance={balance}
                        includeInCharts={storageIncludeInCharts}
                        onIncludeInChart={onIncludeInChart(ProductArea.STORAGE)}
                    />
                </>
            )}
        />
    );
};

const VisualizationForArea: React.FunctionComponent<{
    area: ProductArea,
    projectId: string,
    usageResponse: APICallState<UsageResponse>,
    balance: APICallState<RetrieveBalanceResponse>,
    durationOption: Duration,
    includeInCharts: Record<string, Record<string, boolean>>,
    onIncludeInChart: (provider: string, lineName: string) => void
}> = ({area, projectId, usageResponse, balance, durationOption, includeInCharts, onIncludeInChart}) => {
    const charts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, area));

    const remainingBalance = balance.data.wallets.reduce((sum, wallet) => {
        if (wallet.area === area && wallet.wallet.id === projectId) return sum + wallet.balance;
        else return sum;
    }, 0);

    const balanceAllocatedToChildren = balance.data.wallets.reduce((sum, wallet) => {
        if (wallet.area === area && wallet.wallet.id !== projectId) return sum + wallet.balance;
        else return sum;
    }, 0);

    // provider -> lineName -> usage
    const creditsUsedByWallet: Record<string, Record<string, number>> = {};
    let creditsUsedInPeriod = 0;

    for (const chart of charts) {
        const usageByCurrentProvider: Record<string, number> = {};
        creditsUsedByWallet[chart.provider] = usageByCurrentProvider;

        for (let i = 0; i < chart.points.length; i++) {
            let point = chart.points[i];
            for (const category of Object.keys(point)) {
                if (category === "time") continue;

                const currentUsage = usageByCurrentProvider[category] ?? 0;
                usageByCurrentProvider[category] = currentUsage + point[category];
                creditsUsedInPeriod += point[category];
            }
        }
    }

    return (
        <Box>
            <SummaryCard
                title={productAreaTitle(area)}
                balance={remainingBalance}
                creditsUsed={creditsUsedInPeriod}
                allocatedToChildren={balanceAllocatedToChildren}
            />

            <Box m={35}>
                {charts.map(chart => (
                    <React.Fragment key={chart.provider}>
                        {chart.lineNames.length === 0 ? null : (
                            <>
                                <Heading.h5>Usage {durationOption.bucketSizeText} for {durationOption.text.toLowerCase()} (Provider: {chart.provider})</Heading.h5>
                                <Box mt={20} mb={20}>
                                    <ResponsiveContainer width="100%" height={200}>
                                        <BarChart
                                            syncId="someId"
                                            data={chart.points}
                                            margin={{
                                                top: 10, right: 30, left: 0, bottom: 0,
                                            }}
                                        >
                                            <CartesianGrid strokeDasharray="3 3"/>
                                            <XAxis dataKey="time" tickFormatter={getDateFormatter(durationOption)}/>
                                            <YAxis width={150} tickFormatter={creditFormatter}/>
                                            <Tooltip labelFormatter={getDateFormatter(durationOption)}
                                                     formatter={n => creditFormatter(n as number, 2)}
                                            />
                                            {chart.lineNames.map((id, idx) => {
                                                if ((includeInCharts[chart.provider] ?? {})[id] ?? true) {
                                                    return <Bar
                                                        key={id}
                                                        dataKey={id}
                                                        fill={theme.chartColors[idx]}
                                                        barSize={24}
                                                    />;
                                                } else {
                                                    return null;
                                                }
                                            })}
                                        </BarChart>
                                    </ResponsiveContainer>
                                </Box>

                                <Box mb={40}>
                                    <Table>
                                        <TableHeader>
                                            <TableRow>
                                                <TableHeaderCell width={30}/>
                                                <TableHeaderCell/>
                                                <TableHeaderCell textAlign="right">
                                                    Credits Used In Period
                                                </TableHeaderCell>
                                                <TableHeaderCell textAlign="right">Remaining</TableHeaderCell>
                                                <TableHeaderCell textAlign={"right"}>Include In Chart</TableHeaderCell>
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
                                                <TableCell textAlign="right">
                                                    {creditFormatter(creditsUsedByWallet[chart.provider]![p]!)}
                                                </TableCell>
                                                <TableCell textAlign="right">
                                                    {creditFormatter(
                                                        balance.data.wallets.find(it =>
                                                            it.wallet.id === chart.lineNameToWallet[p].id &&
                                                            it.wallet.paysFor.provider === chart.lineNameToWallet[p].paysFor.provider &&
                                                            it.wallet.paysFor.id === chart.lineNameToWallet[p].paysFor.id
                                                        )?.balance ?? 0
                                                    )}
                                                </TableCell>
                                                <TableCell textAlign={"right"}>
                                                    <Toggle
                                                        onChange={() => onIncludeInChart(chart.provider, p)}
                                                        scale={1.5}
                                                        activeColor={"green"}
                                                        checked={(includeInCharts[chart.provider] ?? {})[p] ?? true}
                                                    />
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                        </tbody>
                                    </Table>
                                </Box>
                            </>
                        )}
                    </React.Fragment>
                ))}

            </Box>
        </Box>
    );
};


const SummaryStat = styled.figure`
    flex-grow: 1;
    text-align: center;
    margin: 0;
    
    figcaption {
        display: block;
        color: var(--gray, #ff0);
        text-transform: uppercase;
        font-size: 12px;
    }
`;

const SummaryWrapper = styled(Card)`
    display: flex;
    padding: 15px;
    margin: 0 15px;
    align-items: center;
    
    h4 {
        flex-grow: 2;
    }
`;

const PercentageDisplay: React.FunctionComponent<{
    numerator: number,
    denominator: number,
    // Note this must be sorted ascending by breakpoint
    colorRanges: { breakpoint: number, color: ThemeColor }[]
}> = props => {
    if (props.denominator === 0) {
        return null;
    }

    const percentage = (props.numerator / props.denominator) * 100;
    let color: ThemeColor = "black";
    for (const cRange of props.colorRanges) {
        if (percentage >= cRange.breakpoint) {
            color = cRange.color;
        }
    }

    return <Text as="span" color={getCssVar(color)}>({percentage.toFixed(2)}%)</Text>;
};

const SummaryCard: React.FunctionComponent<{
    title: string,
    creditsUsed: number,
    balance: number,
    allocatedToChildren: number
}> = props => {
    return <SummaryWrapper>
        <Heading.h4>{props.title}</Heading.h4>

        <SummaryStat>
            {creditFormatter(props.creditsUsed)}
            <figcaption>Credits used in period</figcaption>
        </SummaryStat>
        <SummaryStat>
            {creditFormatter(props.balance)}
            <figcaption>Credits remaining</figcaption>
        </SummaryStat>
        {Client.hasActiveProject ? <SummaryStat>
            {creditFormatter(props.allocatedToChildren)}{" "}
            <PercentageDisplay
                numerator={props.allocatedToChildren}
                denominator={props.balance}
                colorRanges={[
                    {breakpoint: 80, color: "green"},
                    {breakpoint: 100, color: "yellow"},
                    {breakpoint: 175, color: "red"}
                ]}
            />
            <figcaption>Allocated to subprojects</figcaption>
        </SummaryStat> : null}
    </SummaryWrapper>;
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
