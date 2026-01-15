import * as React from "react";
import {
    AccountingFrequency,
    AccountingUnit,
    balanceToStringFromUnit,
    explainUnitEx,
    ProductCategoryId
} from "@/Accounting/index";
import {apiRetrieve, callAPI} from "@/Authentication/DataHook";
import {useCallback, useEffect, useMemo} from "react";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Box, Button, Card, Flex, Icon, Input, MainContainer} from "@/ui-components";
import {doNothing, errorMessageOrDefault, looksLikeUUID, shortUUID, stopPropagation} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as Heading from "@/ui-components/Heading";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useImmerState} from "@/Utilities/Immer";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {useMaxContentWidth} from "@/Utilities/StylingUtilities";
import {useProjectInfos} from "@/Project/InfoCache";
import {useDeltaOverTimeChart} from "@/Accounting/Diagrams/DeltaOverTime";
import {useBreakdownChart} from "@/Accounting/Diagrams/UsageBreakdown";
import {useUtilizationOverTimeChart} from "@/Accounting/Diagrams/UtilizationOverTime";
import {TooltipV2} from "@/ui-components/Tooltip";
import {getStartOfDay} from "@/Utilities/DateUtilities";

interface UsageRetrieveRequest {
    start: number;
    end: number;
}

interface UsageRetrieveResponse {
    reports: UsageReport[];
}

export interface UsageReport {
    title: string;
    productsCovered: ProductCategoryId[];
    unitAndFrequency: {
        unit: AccountingUnit;
        frequency: AccountingFrequency;
    };

    validFrom: number;
    validUntil: number;

    kpis: {
        quotaAtStart: number;
        quotaAtEnd: number;

        activeQuotaAtStart: number;
        activeQuotaAtEnd: number;

        maxUsableAtStart: number;
        maxUsableAtEnd: number;

        localUsageAtStart: number;
        localUsageAtEnd: number;

        totalUsageAtStart: number;
        totalUsageAtEnd: number;

        totalAllocatedAtStart: number;
        totalAllocatedAtEnd: number;

        nextMeaningfulExpiration?: number | null;
    };

    subProjectHealth: {
        subProjectCount: number;

        ok: number;
        underUtilized: number;
        atRisk: number;

        idle: number;
    };

    usageOverTime: {
        delta: {
            timestamp: number;
            change: number;
            child: string | null;
        }[];

        absolute: {
            timestamp: number;
            usage: number;
            utilizationPercent100: number;
        }[];
    };
}

export type UsageReportKpis = UsageReport["kpis"];
export type UsageReportSubProjectHealth = UsageReport["subProjectHealth"];
export type UsageReportOverTime = UsageReport["usageOverTime"];
export type UsageReportDeltaDataPoint = UsageReport["usageOverTime"]["delta"][0];
export type UsageReportAbsoluteDataPoint = UsageReport["usageOverTime"]["absolute"][0];

function usageReportRetrieve(request: UsageRetrieveRequest): APICallParameters<UsageRetrieveRequest, UsageRetrieveResponse> {
    return apiRetrieve(request, "/api/accounting/v2/usage");
}

type Period =
    | { type: "relative", distance: number, unit: "day" | "month" }
    | { type: "absolute", start: number, end: number }
    ;

interface UsagePageState {
    period: Period;
    reports: UsageReport[];
    openReport?: UsageReport;
    error: string | null;
    loading: boolean;
}

const defaultState: UsagePageState = {
    period: {type: "relative", distance: 7, unit: "day"},
    reports: [],
    error: null,
    loading: false,
}

const TableStyle = injectStyle("usage-table", k => `
    ${k} {
        flex-grow: 1;
        overflow-y: scroll;
        min-height: 200px;
        max-height: 400px;
    }
    
    ${k}::before {
        display: block;
        content: " ";
        width: 100%;
        height: 1px;
        position: sticky;
        top: 24px;
    }
    
    ${k} table {
        width: 100%;
        border-collapse: separate;
        border-spacing: 0;
        border-color: var(--borderColor);
    }
    
    ${k} tr:nth-child(even) {
        background-color: var(--rowActive);
    }

    ${k} tr:hover {
        background-color: var(--rowHover);
    }
    
    ${k} tr > td:first-child,
    ${k} tr > th:first-child {
        border-left: 1px solid var(--borderColor);
    }

    ${k} td, 
    ${k} th {
        padding: 0 8px;
        border-right: 1px solid var(--borderColor);
    }

    ${k} tbody > tr:last-child > td {
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} tr > th:first-child {
        border-radius: 6px 0 0 0;
    }

    ${k} tr > th:last-child {
        border-radius: 0 6px 0 0;
    }

    ${k} th {
        text-align: left;
        border-top: 1px solid var(--borderColor);
        border-bottom: 2px solid var(--borderColor);
        position: sticky;
        top: 0;
        background: var(--backgroundCard); /* matches card background */
    }
`);

const UsagePage: React.FunctionComponent = () => {
    const project = useProject();
    const projectId = useProjectId();
    const maxContentWidth = useMaxContentWidth();

    // TODO Empty page behavior

    usePage("Usage", SidebarTabId.PROJECT);

    const [state, updateState] = useImmerState<UsagePageState>(defaultState);

    const reload = useCallback(async () => {
        updateState(s => s.loading = true);

        try {
            const result = await callAPI(usageReportRetrieve(normalizePeriod(state.period)));
            updateState(s => {
                s.reports = result.reports;
                s.loading = false;

                let foundOldReport = false;
                if (s.openReport !== undefined) {
                    for (const r of s.reports) {
                        if (s.openReport.title === r.title) {
                            foundOldReport = true;
                            s.openReport = r;
                            break;
                        }
                    }
                }

                if (!foundOldReport) {
                    if (s.reports.length > 0) {
                        s.openReport = s.reports[0];
                    } else {
                        s.openReport = undefined;
                    }
                }
            });
        } catch (e) {
            updateState(s => {
                s.error = errorMessageOrDefault(e, "Could not load usage data");
                s.reports = [];
                s.openReport = undefined;
                s.loading = false;
            });
        }
    }, [state.period]);

    useEffect(() => {
        reload().then(doNothing);
    }, [projectId, state.period]);

    const setPeriod = useCallback((newPeriod: Period) => {
        updateState(s => {
            s.period = newPeriod;
        });
    }, []);

    const setSelectedReport = useCallback((report: UsageReport) => {
        updateState(s => {
            s.openReport = report;
        });
    }, []);

    const fullChartWidth = maxContentWidth - 40; // compensate for padding in <Card>
    const chartHeight = (width: number, chartAspectRatio: number = 16 / 5): number => {
        return width * (1 / chartAspectRatio);
    };

    const deltaChartWidth = fullChartWidth;
    let breakdownOnSingleRow = fullChartWidth > 900;
    let breakdownChartWidth = breakdownOnSingleRow ? Math.min(fullChartWidth - 550, 600) : fullChartWidth;
    const breakdownChartHeight = Math.max(300, chartHeight(breakdownChartWidth, 1.8));

    let utilizationOnSingleRow = fullChartWidth > 1100;
    let utilizationChartWidth = utilizationOnSingleRow ? fullChartWidth - 400 : fullChartWidth;
    const utilizationChartHeight = chartHeight(utilizationChartWidth, 16 / 6);

    const childProjectIds: string[] = useMemo(() => {
        const r = state.openReport;
        if (r === undefined) return [];

        const projectIds: Record<string, true> = {};
        for (const dataPoint of r.usageOverTime.delta) {
            if (dataPoint.child !== null && looksLikeUUID(dataPoint.child)) {
                projectIds[dataPoint.child] = true;
            }
        }

        return Object.keys(projectIds);
    }, [state.openReport]);

    const childProjectInfo = useProjectInfos(childProjectIds);

    const childToLabel = useCallback((child: string | null): string => {
        child = child ?? "";
        if (child === "") child = "Local";

        if (child != null && looksLikeUUID(child)) {
            const pinfo = childProjectInfo.data[child];
            if (pinfo != null) {
                return pinfo.title;
            } else {
                return shortUUID(child);
            }
        }

        return child;
    }, [childProjectInfo]);

    const unit = useMemo(() => {
        const r = state.openReport;
        if (r) {
            return explainUnitEx(r.unitAndFrequency.unit, r.unitAndFrequency.frequency, null);
        } else {
            return null;
        }
    }, [state.openReport]);

    const valueFormatter = useCallback((value: number) => {
        const r = state.openReport;
        if (r == null) return value.toString();

        const unit = explainUnitEx(r.unitAndFrequency.unit, r.unitAndFrequency.frequency, null);
        const balanceToString = (balance: number): string => {
            const normalizedBalance = balance * unit.balanceFactor;
            return balanceToStringFromUnit(null,
                unit.name,
                normalizedBalance,
                {referenceBalance: 1000, removeUnitIfPossible: true});
        };

        return balanceToString(value);
    }, [state.openReport]);

    const utilizationOverTime = useUtilizationOverTimeChart(state.openReport, utilizationChartWidth, utilizationChartHeight, unit);
    const deltaOverTime = useDeltaOverTimeChart(state.openReport, deltaChartWidth, chartHeight(deltaChartWidth), unit, childToLabel);
    const breakdownChart = useBreakdownChart(state.openReport, breakdownChartWidth,
        breakdownChartHeight, childToLabel, valueFormatter);

    const childConsumption = useMemo(() => {
        const r = state.openReport;
        if (r == null) {
            return 0;
        } else {
            return r.usageOverTime.delta.reduce((prev, next) => prev + (next.child != null ? next.change : 0), 0);
        }
    }, [state.openReport]);

    // User-interface
    // -----------------------------------------------------------------------------------------------------------------
    if (project.fetch().status.personalProviderProjectFor != null) {
        return <MainContainer
            main={
                <>
                    <Heading.h2>Unavailable for this project</Heading.h2>
                    <p>
                        This project belongs to a provider which does not support the accounting and project management
                        features of UCloud. Try again with a different project.
                    </p>
                </>
            }
        />
    }

    let reportNode: React.ReactNode = null;
    if (state.openReport !== undefined) {
        const r = state.openReport;
        const unit = explainUnitEx(r.unitAndFrequency.unit, r.unitAndFrequency.frequency, null);
        const balanceToString = (balance: number, remove?: boolean, reference?: number): string => {
            const normalizedBalance = balance * unit.balanceFactor;
            return balanceToStringFromUnit(null, unit.name, normalizedBalance, {
                referenceBalance: reference ?? 1000,
                removeUnitIfPossible: remove
            });
        };

        const period = normalizePeriod(state.period);
        const alignedStart = getStartOfDay(new Date(period.start)).getTime();
        const alignedEnd = getStartOfDay(new Date(period.end)).getTime();
        const periodLengthInDays = Math.max(1, Math.floor((alignedEnd - alignedStart) / (1000 * 60 * 60 * 24)));

        const daysUntilExpiration = r.kpis.nextMeaningfulExpiration == null ?
            0 :
            Math.max(0, Math.floor((r.kpis.nextMeaningfulExpiration - period.end) / (1000 * 60 * 60 * 24)));

        const combinedUsage = r.kpis.totalUsageAtEnd - r.kpis.totalUsageAtStart;
        const burnRate = (combinedUsage) / periodLengthInDays;
        const daysUntilDepletion = burnRate <= 0 ? null : Math.floor((r.kpis.quotaAtEnd - r.kpis.totalUsageAtEnd) / burnRate);

        const overCommitRatio = r.kpis.quotaAtEnd != 0 ? r.kpis.totalAllocatedAtEnd / r.kpis.quotaAtEnd : 0;
        const childBurnRate = childConsumption / periodLengthInDays;
        const localBurnRate = (combinedUsage - childConsumption) / periodLengthInDays;
        const baselineChildBurnRate = overCommitRatio != 0 ? childBurnRate / overCommitRatio : 0;

        // NOTE(Dan): I am pretty sure I accidentally made this more complicated than it needs to be. This is intended
        // to give the over-commit ratio required to hit 90% usage when the next meaningful expiration hits.
        const recommendedOverCommit = baselineChildBurnRate == 0 || daysUntilExpiration == 0 ? 0 :
            ((((r.kpis.quotaAtEnd * 0.9) - combinedUsage) / daysUntilExpiration) - localBurnRate) / baselineChildBurnRate;

        const BalanceDisplay: (props: {
            value: number,
            children?: React.ReactNode
        }) => React.ReactNode = ({value, children}) => {
            return <div style={{display: "inline-block"}}>
                <TooltipV2 tooltip={balanceToString(value, false, 1)}>
                    {balanceToString(value, true)}{children}
                </TooltipV2>
            </div>;
        }

        reportNode = <>
            <Flex gap={"16px"} flexWrap={"wrap"}>
                <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                    <Flex flexDirection={"column"} height={"100%"}>
                        <Box flexGrow={1}><b>Usage summary</b></Box>

                        <table width={"100%"}>
                            <tbody>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={<>
                                        Your current usage in the project. This includes the use from your project
                                        and all of your children. Note that the usage is since the beginning of
                                        your project.
                                    </>}>
                                        Use:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>
                                    <BalanceDisplay value={r.kpis.totalUsageAtEnd}/>
                                </td>
                            </tr>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={<>
                                        Your current quota in the project. If the usage ever exceeds the quota, then
                                        you will not be able to continue consuming resources.
                                    </>}>
                                        Quota:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>
                                    <BalanceDisplay value={r.kpis.quotaAtEnd}/>
                                </td>
                            </tr>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={<>
                                        The change, during the period, in usage.
                                    </>}>
                                        Change:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>
                                    <BalanceDisplay value={r.kpis.totalUsageAtEnd - r.kpis.totalUsageAtStart}/>
                                </td>
                            </tr>
                            </tbody>
                        </table>
                    </Flex>
                </Card>

                <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                    <Flex flexDirection={"column"} height={"100%"}>
                        <Box flexGrow={1}><b>Depletion forecast</b></Box>

                        <table width={"100%"}>
                            <tbody>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={"Average daily rate of use."}>
                                        Burn-rate:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>
                                    <BalanceDisplay value={burnRate}>
                                        /day
                                    </BalanceDisplay>
                                </td>
                            </tr>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={<>
                                        Duration estimated to run out of resources on your <i>currently active</i>
                                        {" "}allocations at your current burn-rate.
                                        <br/><br/>
                                        The duration is counted from the end of the selected period.
                                    </>}>
                                        Est. depletion in:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>
                                    {daysUntilDepletion === null || daysUntilDepletion <= 0 ? "-" : <>{daysUntilDepletion} days</>}
                                </td>
                            </tr>
                            <tr>
                                <th align={"left"}>
                                    <TooltipV2 tooltip={<>
                                        Duration until one of your allocations expires. Allocations which contribute
                                        less than 10% of your total quota are not counted.
                                        <br/><br/>
                                        The duration is counted from the end of the selected period.
                                    </>}>
                                        Alloc exp. in:
                                    </TooltipV2>
                                </th>
                                <td align={"right"}>{daysUntilExpiration} days</td>
                            </tr>
                            </tbody>
                        </table>
                    </Flex>
                </Card>

                {r.subProjectHealth.subProjectCount == 0 ? null :
                    <>
                        <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                            <Box mb={"8px"}><b>Sub-project allocation summary</b></Box>

                            <table width={"100%"}>
                                <tbody>
                                <tr>
                                    <th align={"left"}>
                                        <TooltipV2 tooltip={<>
                                            The total amount of resources allocated to your sub-projects.

                                            <br/><br/>

                                            This number counts all allocations which contributes towards your usage.
                                            Because of this, allocations which have expired may still contribute some
                                            amount towards this number.
                                        </>}>
                                            Allocated:
                                        </TooltipV2>
                                    </th>
                                    <td align={"right"}>
                                        <BalanceDisplay value={r.kpis.totalAllocatedAtEnd}/>
                                    </td>
                                </tr>
                                <tr>
                                    <th align={"left"}>
                                        <TooltipV2 tooltip={<>
                                            The ratio between your allocated resources versus your quota.
                                            <br/><br/>
                                            Calculated as:{" "}
                                            <pre style={{display: "inline-block", margin: "0"}}>Allocated / Quota</pre>
                                            .
                                        </>}>
                                            Over-commit:
                                        </TooltipV2>
                                    </th>
                                    <td align={"right"}>{overCommitRatio.toFixed(1)}x</td>
                                </tr>
                                <tr>
                                    <th align={"left"}>
                                        <TooltipV2 tooltip={<>
                                            The recommended over-commit ratio to reach 90% utilization at the next
                                            expiry date given your current burn-rate.
                                        </>}>
                                            Rec. over-commit:
                                        </TooltipV2>
                                    </th>
                                    <td align={"right"}>{recommendedOverCommit === 0 ? "-" : <>{recommendedOverCommit.toFixed(1)}x</>}</td>
                                </tr>
                                </tbody>
                            </table>
                        </Card>

                        <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                            <Box mb={"8px"}><b>Sub-project health</b></Box>
                            <table width={"100%"}>
                                <tbody>
                                <tr>
                                    <th align={"left"}>Healthy:</th>
                                    <td align={"right"} width={"42px"}>
                                        {((r.subProjectHealth.ok / r.subProjectHealth.subProjectCount) * 100).toFixed(2)}%
                                    </td>
                                </tr>
                                <tr>
                                    <th align={"left"}>Underutilized:</th>
                                    <td align={"right"} width={"42px"}>
                                        {((r.subProjectHealth.underUtilized / r.subProjectHealth.subProjectCount) * 100).toFixed(2)}%
                                    </td>
                                </tr>
                                <tr>
                                    <th align={"left"}>At risk:</th>
                                    <td align={"right"} width={"42px"}>
                                        {((r.subProjectHealth.atRisk / r.subProjectHealth.subProjectCount) * 100).toFixed(2)}%
                                    </td>
                                </tr>
                                </tbody>
                            </table>
                        </Card>
                    </>
                }
            </Flex>


            {r.subProjectHealth.subProjectCount === 0 || breakdownChart.table.length === 0 ? null :
                <Card>
                    <h3>Usage breakdown</h3>
                    <Flex flexWrap={"wrap"} gap={"16px"}>
                        <svg ref={breakdownChart.chartRef} width={breakdownChartWidth} height={breakdownChartHeight}
                             style={{flexShrink: 0, flexBasis: breakdownChartWidth}}/>

                        <div className={TableStyle} style={{flexBasis: "500px"}}>
                            <table>
                                <thead>
                                <tr>
                                    <th/>
                                    <th>Project</th>
                                    <th>Usage</th>
                                </tr>
                                </thead>
                                <tbody>
                                {breakdownChart.table.map(row => <tr key={row.child}>
                                    <td align={"center"}>
                                        <Box width={14} height={14} flexShrink={0} style={{background: row.color}}/>
                                    </td>
                                    <td>{childToLabel(row.child)}</td>
                                    <td align={"right"}>{balanceToString(row.value)}</td>
                                </tr>)}

                                </tbody>
                            </table>
                        </div>
                    </Flex>
                </Card>
            }

            {r.usageOverTime.delta.length <= 1 ? null :
                <Card>
                    <h3>Change in usage over time</h3>
                    <svg ref={deltaOverTime.chartRef} width={deltaChartWidth} height={chartHeight(deltaChartWidth)}/>
                    <Flex flexWrap={"wrap"} gap={"16px"} ml={40} fontSize={"80%"}>
                        {deltaOverTime.labels.map(label =>
                            <Flex key={label.child} gap={"4px"} alignItems={"center"}>
                                <Box width={14} height={14} flexShrink={0} style={{background: label.color}}/>
                                <div>{childToLabel(label.child)}</div>
                            </Flex>
                        )}
                    </Flex>
                </Card>
            }

            {r.usageOverTime.absolute.length <= 1 ? null :
                <Card>
                    <h3>Utilization over time</h3>
                    <Flex flexWrap={"wrap"} gap={"16px"}>
                        <svg ref={utilizationOverTime.chartRef} width={utilizationChartWidth}
                             height={utilizationChartHeight}/>

                        <div className={TableStyle} style={{flexBasis: "380px"}}>
                            <table>
                                <thead>
                                <tr>
                                    <th style={{width: "60px"}}/>
                                    {utilizationOverTime.rows.map(row => <th key={row.title} style={{width: "150px"}}>
                                        <Flex gap={"8px"} alignItems={"center"}>
                                            <Box width={14} height={14} flexShrink={0} style={{background: row.color}}/>
                                            {row.title}
                                        </Flex>
                                    </th>)}
                                </tr>
                                </thead>
                                <tbody>
                                <tr>
                                    <td><b>Min</b></td>
                                    {utilizationOverTime.rows.map(row => <td key={row.title} align={"right"}>
                                        {row.min}
                                    </td>)}
                                </tr>
                                <tr>
                                    <td><b>Max</b></td>
                                    {utilizationOverTime.rows.map(row => <td key={row.title} align={"right"}>
                                        {row.max}
                                    </td>)}
                                </tr>
                                <tr>
                                    <td><b>Mean</b></td>
                                    {utilizationOverTime.rows.map(row => <td key={row.title} align={"right"}>
                                        {row.mean}
                                    </td>)}
                                </tr>
                                </tbody>
                            </table>
                        </div>
                    </Flex>
                </Card>
            }
        </>
    }

    return <MainContainer
        main={<Flex flexDirection={"column"} gap={"32px"}>
            <Flex>
                <h3 className="title" style={{marginTop: "auto", marginBottom: "auto"}}>Usage report</h3>
                <Box flexGrow={1}/>
                <ProjectSwitcher/>
            </Flex>

            <Box>
                <Flex gap={"16px"} flexWrap={"wrap"}>
                    <Box flexBasis={300} flexGrow={1} flexShrink={0}>
                        <div><b>Period</b></div>
                        <PeriodSelector value={state.period} onChange={setPeriod}/>
                    </Box>

                    <Box flexBasis={930} flexShrink={1} flexGrow={3}>
                        <div><b>Page</b></div>
                        <RichSelect
                            items={state.reports}
                            keys={["title"]}
                            RenderRow={RenderReportSelector}
                            RenderSelected={RenderReportSelector}
                            onSelect={setSelectedReport}
                            fullWidth
                            selected={state.openReport}
                        />
                    </Box>
                </Flex>

                {normalizePeriod(state.period).start < new Date("2026-01-28").getTime() ||
                normalizePeriod(state.period).end < new Date("2026-01-28").getTime() ? <>
                    <Card
                        borderRadius="6px"
                        height="auto"
                        mt={"32px"}
                        color="textPrimary"
                        style={{background: `var(--warningMain)`}}
                    >
                        Data prior to 28/01/2026 is incomplete due to the historic data being produced by an older version of UCloud.
                    </Card>
                </> : null}
            </Box>

            {state.loading ? <HexSpin/> :
                state.error ? <>{state.error}</> :
                    state.openReport === undefined ? <>No reports available</> :
                        reportNode
            }
        </Flex>}
    />
};

const RenderReportSelector: RichSelectChildComponent<UsageReport> = ({element, onSelect, dataProps}) => {
    if (element === undefined) {
        return <Flex height={40} alignItems={"center"} pl={12}>No report selected</Flex>
    }

    return <Flex gap={"16px"} height="40px" {...dataProps} alignItems={"center"} py={4} px={8} mr={48}
                 onClick={onSelect}>
        {/* TODO Icon */}
        <Icon name={"heroCpuChip"}/>
        <div><b>{element.title}</b></div>
    </Flex>;
};

function normalizePeriod(period: Period): { start: number, end: number } {
    switch (period.type) {
        case "relative": {
            let start = new Date();
            const end = start.getTime();
            start.setUTCHours(0, 0, 0, 0);

            switch (period.unit) {
                case "day": {
                    start.setDate(start.getDate() - period.distance);
                    break;
                }

                case "month": {
                    start.setMonth(start.getMonth() - period.distance);
                    break;
                }
            }
            return {start: start.getTime(), end};
        }

        case "absolute": {
            return {start: period.start, end: period.end};
        }
    }
}

const PeriodStyle = injectStyle("period-selector", k => `
    ${k} {
        position: relative;
        cursor: pointer;
        border-radius: 5px;
        border: 1px solid var(--borderColor, #f00);
        width: 100%;
        user-select: none;
        -webkit-user-select: none;
        background: var(--backgroundDefault);
        box-shadow: inset 0 .0625em .125em rgba(10,10,10,.05);
        
        display: flex;
        padding: 4px 8px;
        height: 40px;
        align-items: center;
    }

    ${k}:hover {
        border-color: var(--borderColorHover);
    }

    ${k}[data-omit-border="true"] {
        border: unset;
    }

    ${k} > svg {
        position: absolute;
        bottom: 13px;
        right: 15px;
        height: 16px;
    }
`);

const PeriodSelectorBodyStyle = injectStyle("period-selector-body", k => `
    ${k} {
        cursor: auto;
        display: flex;
        flex-direction: row;
        width: 422px;
        height: 300px;
    }
    
    ${k} > div {
        flex-grow: 1;
        display: flex;
        flex-direction: column;
        padding: 8px;
    }
    
    ${k} > div:first-child {
        border-right: 1px solid var(--borderColor);
    }
    
    ${k} b {
        display: block;
        margin-bottom: 10px;
    }
    
    ${k} .relative {
        cursor: pointer;
        margin-left: -8px;
        margin-right: -8px;
        padding: 8px;
    }
    
    ${k} .relative:hover {
        background: var(--rowHover);
    }
`);

const PeriodSelector: React.FunctionComponent<{
    value: Period;
    onChange: (period: Period) => void;
}> = props => {
    const [{start, end}, setPeriod] = React.useState(normalizePeriod(props.value));

    function formatTs(ts: number): string {
        const d = new Date(ts);
        return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
    }

    function periodToString(period: Period) {
        switch (period.type) {
            case "relative": {
                return `Last ${period.distance} ${period.unit}s`;
            }

            case "absolute": {
                const pad = (n: number) => n.toString().padStart(2, "0");
                const startDate = new Date(period.start);
                const endDate = new Date(period.end);
                let b = "";

                b += pad(startDate.getDate());
                b += "/";
                b += pad(startDate.getMonth() + 1);
                b += "/";
                b += startDate.getFullYear();

                b += " to ";

                b += pad(endDate.getDate());
                b += "/";
                b += pad(endDate.getMonth() + 1);
                b += "/";
                b += endDate.getFullYear();
                return b;
            }
        }
    }

    const onRelativeUpdated = useCallback((ev: React.SyntheticEvent) => {
        const t = ev.target as HTMLElement;
        const distance = parseInt(t.getAttribute("data-relative") ?? "0");
        const unit = t.getAttribute("data-relative-unit") as any;

        props.onChange({
            type: "relative",
            distance,
            unit
        });
    }, [props.onChange]);

    const onChange = useCallback((ev: React.SyntheticEvent) => {
        const target = ev.target as HTMLInputElement;
        if (!target) return;
        const isStart = target.classList.contains("start");

        const newPeriod = {
            start: isStart ? (target.valueAsDate?.getTime() ?? start) : start,
            end: isStart ? end : (target.valueAsDate?.getTime() ?? end),
        };

        setPeriod(newPeriod);
    }, [start, end]);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent
        fullWidth
        rightAligned
        onOpeningTriggerClick={() => setPeriod(normalizePeriod(props.value))}
        trigger={
            <div className={PeriodStyle}>
                <div>{periodToString(props.value)}</div>
                <Icon name="heroChevronDown" size="16px" ml="4px" mt="4px"/>
            </div>
        }
    >
        <div className={PeriodSelectorBodyStyle}>
            <div onClick={stopPropagation}>
                <form onSubmit={e => {
                    e.preventDefault();
                    props.onChange({start, end, type: "absolute"})
                }}>
                    <b>Absolute time range</b>

                    <label>
                        From
                        <Input className={"start"} onChange={onChange} type={"date"} value={formatTs(start)}/>
                    </label>
                    <label>
                        To
                        <Input className={"end"} onChange={onChange} type={"date"} value={formatTs(end)}/>
                    </label>

                    <Button mt="8px" type="submit">Apply</Button>
                </form>
            </div>

            <div>
                <b>Relative time range</b>

                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                     data-relative={"7"}>Last 7 days
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                     data-relative={"30"}>Last 30 days
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                     data-relative={"90"}>Last 90 days
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                     data-relative={"6"}>Last 6 months
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                     data-relative={"12"}>Last 12 months
                </div>
            </div>
        </div>
    </ClickableDropdown>;
};

export default UsagePage;
