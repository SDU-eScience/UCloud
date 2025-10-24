import * as React from "react";
import {
    AccountingFrequency,
    AccountingUnit,
    balanceToStringFromUnit,
    explainUnitEx,
    ProductCategoryId
} from "@/Accounting/index";
import {apiRetrieve, callAPI} from "@/Authentication/DataHook";
import {useCallback, useEffect, useMemo, useState} from "react";
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
import {useD3} from "@/Utilities/d3";
import {select} from "d3-selection";
import {stack} from "d3-shape";
import {index, max, min, union} from "d3-array";
import {useProjectInfos} from "@/Project/InfoCache";
import {scaleBand, scaleLinear, scaleOrdinal, scalePoint, scaleTime} from "d3-scale";
import {axisBottom, axisLeft} from "d3-axis";
import {timeFormat} from "d3-time-format";
import {useDeltaOverTimeChart} from "@/Accounting/Diagrams/DeltaOverTime";
import {useBreakdownChart} from "@/Accounting/Diagrams/UsageBreakdown";
import {useUtilizationOverTimeChart} from "@/Accounting/Diagrams/UtilizationOverTime";

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
    const deltaOverTime = useDeltaOverTimeChart(state.openReport, deltaChartWidth, chartHeight(deltaChartWidth), unit);
    const breakdownChart = useBreakdownChart(state.openReport, breakdownChartWidth,
        breakdownChartHeight, childToLabel, valueFormatter);

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
        const balanceToString = (balance: number, remove?: boolean): string => {
            const normalizedBalance = balance * unit.balanceFactor;
            return balanceToStringFromUnit(null, unit.name, normalizedBalance, { referenceBalance: 1000, removeUnitIfPossible: remove });
        };

        reportNode = <>
            <Flex gap={"16px"} flexWrap={"wrap"}>
                <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                    <Flex flexDirection={"column"} height={"100%"}>
                        <Box flexGrow={1}><b>Usage summary</b></Box>

                        <table width={"100%"}>
                            <tbody>
                            <tr>
                                <th align={"left"}>Use:</th>
                                <td align={"right"}>1.100,0K</td>
                                <td align={"center"}>→</td>
                                <td align={"right"}>1.112,0K</td>
                            </tr>
                            <tr>
                                <th align={"left"}>Quota:</th>
                                <td align={"right"}>10.125,0K</td>
                                <td align={"center"}>→</td>
                                <td align={"right"}>10.125,0K</td>
                            </tr>
                            <tr>
                                <th align={"left"}>Change:</th>
                                <td align={"right"}>13,0K</td>
                                <td align={"center"}>→</td>
                                <td align={"right"}>12,7K</td>
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
                                <th align={"left"}>Burn-rate:</th>
                                <td align={"right"}>1,7K/day</td>
                                <td align={"center"}>
                                    <Box color={"successDark"}>↑</Box>
                                </td>
                            </tr>
                            <tr>
                                <th align={"left"}>Est. depletion in:</th>
                                <td align={"right"}>5.302 days</td>
                                <td align={"center"}>→</td>
                            </tr>
                            <tr>
                                <th align={"left"}>Alloc exp. in:</th>
                                <td align={"right"}>365 days</td>
                                <td align={"center"}>→</td>
                            </tr>
                            </tbody>
                        </table>
                    </Flex>
                </Card>

                <Card flexBasis={300} borderRadius={8} padding={16} flexGrow={1} flexShrink={0}>
                    <Box mb={"8px"}><b>Sub-project allocation summary</b></Box>

                    <table width={"100%"}>
                        <tbody>
                        <tr>
                            <th align={"left"}>Allocated:</th>
                            <td align={"right"}>1.100,0K</td>
                            <td align={"center"}>→</td>
                            <td align={"right"}>1.112,0K</td>
                        </tr>
                        <tr>
                            <th align={"left"}>Over-commit:</th>
                            <td/>
                            <td/>
                            <td align={"right"}>1,3x</td>
                        </tr>
                        <tr>
                            <th align={"left"}>Rec. over-commit:</th>
                            <td/>
                            <td/>
                            <td align={"right"}>2,5x</td>
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
                            <td align={"right"} width={"42px"}>30%</td>
                            <td align={"center"}>→</td>
                            <td align={"right"} width={"42px"}>90%</td>
                            <td align={"center"}><Box color={"successDark"}>↑</Box></td>
                        </tr>
                        <tr>
                            <th align={"left"}>Underutilized:</th>
                            <td align={"right"} width={"42px"}>70%</td>
                            <td align={"center"}>→</td>
                            <td align={"right"} width={"42px"}>5%</td>
                            <td align={"center"}><Box color={"successDark"}>↓</Box></td>
                        </tr>
                        <tr>
                            <th align={"left"}>At risk:</th>
                            <td align={"right"} width={"42px"}>0%</td>
                            <td align={"center"}>→</td>
                            <td align={"right"} width={"42px"}>5%</td>
                            <td align={"center"}><Box color={"errorDark"}>↑</Box></td>
                        </tr>
                        </tbody>
                    </table>
                </Card>
            </Flex>


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

            <Card>
                <h3>Utilization over time</h3>
                <Flex flexWrap={"wrap"} gap={"16px"}>
                    <svg ref={utilizationOverTime.chartRef} width={utilizationChartWidth} height={utilizationChartHeight}/>

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
        </>
    }

    return <MainContainer
        main={<Flex flexDirection={"column"} gap={"32px"}>
            <Flex>
                <h3 className="title" style={{marginTop: "auto", marginBottom: "auto"}}>Usage</h3>
                <Box flexGrow={1}/>
                <ProjectSwitcher/>
            </Flex>

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
