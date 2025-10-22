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
import {scaleBand, scaleLinear, scaleOrdinal, scalePoint} from "d3-scale";
import {axisBottom, axisLeft} from "d3-axis";
import {timeFormat} from "d3-time-format";

interface UsageRetrieveRequest {
    start: number;
    end: number;
}

interface UsageRetrieveResponse {
    reports: UsageReport[];
}

interface UsageReport {
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

type UsageReportKpis = UsageReport["kpis"];
type UsageReportSubProjectHealth = UsageReport["subProjectHealth"];
type UsageReportOverTime = UsageReport["usageOverTime"];
type UsageReportDeltaDataPoint = UsageReport["usageOverTime"]["delta"][0];
type UsageReportAbsoluteDataPoint = UsageReport["usageOverTime"]["absolute"][0];

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

    const chartAspectRatio = 16 / 5;
    const chartWidth = maxContentWidth - 40; // compensate for padding in <Card>
    const chartHeight = chartWidth * (1 / chartAspectRatio);

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

    // child -> color
    const [childrenLabels, setChildrenLabels] = useState<[string, string][]>([]);

    const childToLabel = useCallback((child: string | null): string => {
        if (child != null && looksLikeUUID(child)) {
            const pinfo = childProjectInfo.data[child];
            if (pinfo != null) {
                return pinfo.title;
            } else {
                return shortUUID(child);
            }
        }

        return child ?? "Local";
    }, [childProjectInfo]);

    const deltaOverTime = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = state.openReport;
        if (r === undefined) return;
        const data = r.usageOverTime.delta.filter(it => it.timestamp > 0); // TODO backend shouldn't return this
        if (data.length === 0) return;

        // Dimensions and margin
        // -------------------------------------------------------------------------------------------------------------
        const margin = {
            top: 16,
            bottom: 70,
            left: 40,
            right: 16,
        };

        const innerW = chartWidth - margin.left - margin.right;
        const innerH = chartHeight - margin.top - margin.bottom;

        // X-axis
        // -------------------------------------------------------------------------------------------------------------
        const timestamps: number[] = [];
        let prevTimestamp = 0;
        for (const d of data) {
            if (d.timestamp !== prevTimestamp) {
                timestamps.push(d.timestamp);
                prevTimestamp = d.timestamp;
            }
        }

        const startOfDay = (t: number) => {
            const d = new Date(t);
            d.setHours(0, 0, 0, 0);
            return +d;
        };

        // day -> sorted timestamps of that day
        const dayMap = new Map<number, number[]>();
        for (const d of data) {
            const key = startOfDay(d.timestamp);
            const arr = dayMap.get(key) ?? [];
            arr.push(d.timestamp);
            dayMap.set(key, arr);
        }

        for (const [, arr] of dayMap) {
            arr.sort((a, b) => a - b);
        }

        const days = Array.from(dayMap.keys()).sort((a, b) => a - b);
        const maxPerDay = Math.max(...Array.from(dayMap.values(), v => v.length), 0);

        // For quick lookup: timestamp -> slot index within its day
        const slotIndexByTs = new Map<number, number>();
        for (const [, arr] of dayMap) {
            arr.forEach((ts, i) => slotIndexByTs.set(ts, i));
        }

        const xDay = scaleBand<number>()
            .domain(days)
            .range([0, innerW])
            .paddingOuter(0.01)
            .paddingInner(0.10);

        const xSlotDomain = Array.from({length: maxPerDay}, (_, i) => i);
        const xSlot = scaleBand<number>()
            .domain(xSlotDomain)
            .range([0, xDay.bandwidth()])
            .paddingInner(0.15);  // spacing between the bars inside a single day

        const xAxisScale = scalePoint<number>()
            .domain(timestamps)
            .range([0, innerW]);

        const xCenterByTs = new Map<number, number>();
        timestamps.forEach(ts => {
            const dayKey = startOfDay(ts);
            const slot = slotIndexByTs.get(ts) ?? 0;
            const center = (xDay(dayKey) ?? 0) + (xSlot(slot) ?? 0) + xSlot.bandwidth() / 2;
            xCenterByTs.set(ts, center);
        });

        // Series
        // -------------------------------------------------------------------------------------------------------------
        const byTimestampKey = index(data, d => d.timestamp, d => d.child);

        const seriesGenerator = stack<number>()
            .keys(union(data.map(it => it.child ?? "")))
            .value((ts, key) => {
                return byTimestampKey.get(ts)?.get(key)?.change ?? 0;
            });

        const series = seriesGenerator(timestamps);

        // Y-axis
        // -------------------------------------------------------------------------------------------------------------
        const yScaleMin = min(series, datum => {
            return min(datum, d => d[0])
        }) ?? 0;

        const yScaleMax = max(series, datum => {
            return max(datum, d => d[1])
        }) ?? 100;

        const yScale = scaleLinear().domain([yScaleMin, yScaleMax]).nice().range([innerH, margin.top]);

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------
        const color = scaleOrdinal<string>()
            .domain(series.map(d => d.key ?? ""))
            .range(["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f",
                "#bcbd22", "#17becf"])
            .unknown("#ccc");

        setChildrenLabels(series.map(d => {
            return [d.key, color(d.key ?? "")];
        }));

        // SVG
        // -------------------------------------------------------------------------------------------------------------
        const svg = select(node);
        svg.selectAll("*").remove();

        svg
            .attr("style", "max-width: 100%; height: auto;")

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        g.selectAll()
            .data(series)
            .join("g")
            .attr("fill", d => color(d.key))
            .selectAll("rect")
            .data(D => D.map(d => d))
            .join("rect")
            .attr("x", d => {
                const t = d.data;
                const dayKey = startOfDay(t);
                const dayX = xDay(dayKey) ?? 0;
                const slot = slotIndexByTs.get(t) ?? 0;
                const slotX = xSlot(slot) ?? 0;
                return (dayX + slotX) - xSlot.bandwidth() / 2;
            })
            .attr("y", d => yScale(d[1]))
            .attr("height", d => yScale(d[0]) - yScale(d[1]))
            .attr("width", xSlot.bandwidth() * 2);

        const tsFormatter = timeFormat("%b %d %H:%M");

        const gXAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${innerH + margin.top})`)
            .call(
                axisBottom(xAxisScale)
                    .tickFormat(d => tsFormatter(new Date(d)))
            );

        gXAxis.selectAll(".tick")
            .attr("transform", (d: any) => `translate(${xCenterByTs.get(+d)},0)`);

        gXAxis.selectAll(".tick > text")
            .attr("style", "transform: translate(-20px, 20px) rotate(-45deg)")

        const gYAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${margin.top})`)
            .call(axisLeft(yScale).ticks(null, "s"));

        for (const gAxis of [gXAxis, gYAxis]) {
            gAxis.selectAll("path, line")
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round")
                .style("stroke", "var(--borderColor)");
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
        const balanceToString = (balance: number): string => {
            const normalizedBalance = balance * unit.balanceFactor;
            return balanceToStringFromUnit(null, unit.name, normalizedBalance, {});
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
                <h3>Usage over time</h3>
                <svg ref={deltaOverTime} width={chartWidth} height={chartHeight}/>
                <Flex flexWrap={"wrap"} gap={"16px"} ml={40} fontSize={"80%"}>
                    {childrenLabels.map(([child, color]) =>
                        <Flex key={child} gap={"8px"} alignItems={"center"}>
                            <Box width={14} height={14} flexShrink={0} style={{background: color}} />
                            <div>{childToLabel(child)}</div>
                        </Flex>
                    )}
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
