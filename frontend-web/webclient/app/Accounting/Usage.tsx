import * as React from "react";
import * as Accounting from ".";
import Chart, {Props as ChartProps} from "react-apexcharts";
import {classConcat, injectStyle} from "@/Unstyled";
import theme, {ThemeColor} from "@/ui-components/theme";
import {Flex, Icon, Radio, Select} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {dateToString} from "@/Utilities/DateUtilities";
import {useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {ProductType, translateBinaryProductCategory} from ".";
import {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as AccountingB from "./AccountingBinary";
import {useProjectId} from "@/Project/Api";
import {formatDistance} from "date-fns";

// State
// =====================================================================================================================
interface State {
    remoteData: {
        chartData?: AccountingB.Charts;
    },

    summaries: {
        usage: number,
        quota: number,
        category: Accounting.ProductCategoryV2,
        chart: UsageChart,
        categoryIdx: number,
    }[],

    activeDashboard?: {
        category: Accounting.ProductCategoryV2,
        currentAllocation: {
            usage: number,
            quota: number,
            expiresAt: number,
        },
        nextAllocation?: {
            startsAt: number,
            quota: number,
        },
        usageOverTime: UsageChart,
    },

    selectedPeriod: Period,
}

type Period =
    { type: "relative", distance: number }
    | { type: "absolute", start: number, end: number }
    ;

// State reducer
// =====================================================================================================================
type UIAction =
    { type: "LoadCharts", charts: AccountingB.Charts, }
    | { type: "SelectTab", tabIndex: number }
    ;

function stateReducer(state: State, action: UIAction): State {
    switch (action.type) {
        case "LoadCharts": {
            function translateChart(category: Accounting.ProductCategoryV2, chart: AccountingB.UsageOverTime): UsageChart {
                const {name, priceFactor} = Accounting.explainUnit(category);
                const dataPoints: UsageChart["dataPoints"] = [];

                const dataPointsLength = chart.data.count;
                for (let i = 0; i < dataPointsLength; i++) {
                    const dataPoint = chart.data.get(i);
                    dataPoints.push({
                        usage: Number(dataPoint.usage) * priceFactor,
                        quota: Number(dataPoint.quota) * priceFactor,
                        timestamp: Number(dataPoint.timestamp)
                    });
                }

                return {unit: name, dataPoints};
            }

            const data = action.charts;
            const newSummaries: State["summaries"] = [];
            const now = BigInt(timestampUnixMs());

            for (let i = 0; i < data.allocations.count; i++) {
                const allocation = data.allocations.get(i);
                console.log("Looking at allocation", i, allocation.encodeToJson());
                if (now < allocation.startDate || now > allocation.endDate) continue;
                console.log("Valid!");
                const category = data.categories.get(allocation.categoryIndex);

                const existingIndex = newSummaries.findIndex(it =>
                    it.category.name === category.name && it.category.provider === category.provider
                );

                let summary: State["summaries"][0];
                if (existingIndex === -1) {
                    summary = {
                        usage: 0,
                        quota: 0,
                        category: translateBinaryProductCategory(category),
                        chart: emptyChart,
                        categoryIdx: allocation.categoryIndex,
                    };
                    newSummaries.push(summary);
                } else {
                    summary = newSummaries[existingIndex];
                }

                summary.usage += Number(allocation.usage);
                summary.quota += Number(allocation.quota);
            }

            for (let i = 0; i < data.charts.count; i++) {
                const chart = data.charts.get(i);
                const summary = newSummaries.find(it => it.categoryIdx === chart.categoryIndex);
                if (!summary) continue;
                console.log("Chart for", chart.encodeToJson());
                summary.chart = translateChart(summary.category, chart.overTime);
            }

            const currentlySelectedCategory = state.activeDashboard?.category;
            let selectedIndex = 0;
            if (currentlySelectedCategory) {
                const selectedSummary = newSummaries.find(it =>
                    it.category.name === currentlySelectedCategory.name &&
                    it.category.provider === currentlySelectedCategory.provider
                );

                if (selectedSummary) selectedIndex = selectedSummary.categoryIdx;
            }

            console.log("Selecting stuff!", selectedIndex);

            return selectChart({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    chartData: data,
                },
                summaries: newSummaries,
            }, selectedIndex);
        }

        case "SelectTab": {
            return selectChart(state, action.tabIndex);
        }
    }

    function selectChart(state: State, categoryIndex: number): State {
        const chartData = state.remoteData.chartData;
        if (!chartData) return {...state, activeDashboard: undefined};
        if (categoryIndex < 0 || categoryIndex > chartData.categories.count) return {...state, activeDashboard: undefined};

        const summary = state.summaries.find(it => it.categoryIdx === categoryIndex);
        if (!summary) return {...state, activeDashboard: undefined};

        let earliestNextAllocation: number | null = null;
        let earliestExpiration: number | null = null;

        const now = BigInt(timestampUnixMs());
        for (let i = 0; i < chartData.allocations.count; i++) {
            const alloc = chartData.allocations.get(i);
            if (alloc.startDate >= now) {
                // Starts in the future
                if (earliestNextAllocation === null) {
                    earliestNextAllocation = Number(alloc.startDate);
                } else if (alloc.startDate < earliestNextAllocation) {
                    earliestNextAllocation = Number(alloc.startDate);
                }
            } else if (now >= alloc.startDate && now <= alloc.endDate) {
                // Active now
                if (earliestExpiration === null) {
                    earliestExpiration = Number(alloc.endDate);
                } else if (alloc.endDate < earliestExpiration) {
                    earliestExpiration = Number(alloc.endDate);
                }
            }
        }

        let nextQuota = 0;
        if (earliestNextAllocation !== null) {
            for (let i = 0; i < chartData.allocations.count; i++) {
                const alloc = chartData.allocations.get(i);
                if (earliestNextAllocation < alloc.startDate || earliestNextAllocation > alloc.endDate) {
                    continue;
                }

                nextQuota += Number(alloc.quota);
            }
        }

        return {
            ...state,
            activeDashboard: {
                category: summary.category,
                currentAllocation: {
                    usage: summary.usage,
                    quota: summary.quota,
                    expiresAt: earliestExpiration ?? timestampUnixMs(),
                },
                nextAllocation: earliestNextAllocation === null ? undefined : {
                    startsAt: earliestNextAllocation,
                    quota: nextQuota,
                },
                usageOverTime: summary.chart,
            }
        };
    }
}

// State reducer middleware
// =====================================================================================================================
type UIEvent =
    UIAction
    | { type: "Init" }
    ;

function useStateReducerMiddleware(doDispatch: (action: UIAction) => void): (event: UIEvent) => unknown {
    const didCancel = useDidUnmount();
    return useCallback(async (event: UIEvent) => {
        function dispatch(ev: UIAction) {
            if (didCancel.current === true) return;
            doDispatch(ev);
        }

        switch (event.type) {
            case "Init": {
                const now = timestampUnixMs();
                const charts = await callAPI(Accounting.retrieveChartsV2({
                    start: now - (1000 * 60 * 60 * 24 * 180),
                    end: now
                }));
                dispatch({ type: "LoadCharts", charts });
                console.log(charts.encodeToJson());
                break;
            }

            default: {
                dispatch(event);
                break;
            }
        }
    }, [doDispatch]);
}

// Styling
// =====================================================================================================================
const VisualizationStyle = injectStyle("visualization", k => `
    ${k} header {
        position: fixed;
        top: 0;
        left: var(--currentSidebarWidth);
        
        background: var(--white);
        
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        
        height: 50px;
        width: calc(100vw - var(--currentSidebarWidth));
        
        padding: 0 16px;
        z-index: 10;
        
        box-shadow: ${theme.shadows.sm};
    }

    ${k} header.at-top {
        box-shadow: unset;
    }
    
    ${k} header h3 {
        margin: 0;
    }

    ${k} header .duration-select {
        width: 150px;
    }

    ${k} h1, ${k} h2, ${k} h3, ${k} h4 {
        margin: 19px 0;
    }

    ${k} h3:first-child {
        margin-top: 0;
    }

    ${k} .panel-grid {
        display: flex;
        gap: 16px;
        flex-direction: row;
        width: 100%;
        padding: 16px 0;
        height: calc(100vh - 195px);
    }
    
    ${k} .primary-grid-2-2 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 7fr 3fr;
        gap: 16px;
        width: 100%;
        height: 100%;
    }
    
    ${k} .primary-grid-2-1 {
        display: grid;
        grid-template-columns: 1fr 1fr;
        grid-template-rows: 1fr;
        gap: 16px;
        width: 100%;
        height: 100%;
    }
    
    ${k} table {
        width: 100%;
        border-collapse: separate;
        border-spacing: 0;
    }
    
    ${k} tr > td:first-child,
    ${k} tr > th:first-child {
        border-left: 2px solid var(--midGray);
    }

    ${k} td, 
    ${k} th {
        padding: 0 8px;
        border-right: 2px solid var(--midGray);
    }

    ${k} tbody > tr:last-child > td {
        border-bottom: 2px solid var(--midGray);
    }

    ${k} th {
        text-align: left;
        border-top: 2px solid var(--midGray);
        border-bottom: 2px solid var(--midGray);
        position: sticky;
        top: 0;
        background: var(--lightGray); /* matches card background */
    }
    
    ${k} .change > span:nth-child(1) {
        float: left;
    }
    
    ${k} .change > span:nth-child(2) {
        float: right;
    }
    
    ${k} .change.positive {
        color: var(--green);
    }
    
    ${k} .change.negative {
        color: var(--red);
    }
    
    ${k} .change.unchanged {
        color: var(--midGray);
    }
    
    ${k} .apexcharts-tooltip {
        color: var(--black);
    }
    
    ${k} .apexcharts-yaxis-title text,
    ${k} .apexcharts-yaxis-texts-g text,
    ${k} .apexcharts-xaxis-texts-g text {
        fill: var(--black);
    }
`);

// User-interface
// =====================================================================================================================
const Visualization: React.FunctionComponent = props => {
    const [activeCard, setActiveCard] = useState("u1-standard");
    const projectId = useProjectId();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(rawDispatch);

    useEffect(() => {
        dispatchEvent({type: "Init"});
    }, [projectId]);

    console.log(state);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.

    useLayoutEffect(() => {
        const wrappers = document.querySelectorAll(`.${VisualizationStyle} .table-wrapper`);
        const listeners: [Element, EventListener][] = [];
        wrappers.forEach(wrapper => {
            if (wrapper.scrollTop === 0) {
                wrapper.classList.add("at-top");
            }

            if (wrapper.scrollTop + wrapper.clientHeight >= wrapper.scrollHeight) {
                wrapper.classList.add("at-bottom");
            }

            const listener = () => {
                if (wrapper.scrollTop < 1) {
                    wrapper.classList.add("at-top");
                } else {
                    wrapper.classList.remove("at-top");
                }

                if (Math.ceil(wrapper.scrollTop) + wrapper.clientHeight >= wrapper.scrollHeight) {
                    wrapper.classList.add("at-bottom");
                } else {
                    wrapper.classList.remove("at-bottom");
                }
            };

            wrapper.addEventListener("scroll", listener);

            listeners.push([wrapper, listener]);
        });

        return () => {
            for (const [elem, listener] of listeners) {
                elem.removeEventListener("scroll", listener);
            }
        };
    });

    const setActiveCategory = useCallback((key: any) => {
        dispatchEvent({ type: "SelectTab", tabIndex: key });
    }, [dispatchEvent]);

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    // NOTE(Dan): We are not using a <MainContainer/> here on purpose since
    // we want to use _all_ of the space.
    return <div className={VisualizationStyle}>
        <header className="at-top">
            <h3>Resource usage in the past</h3>
            <div className="duration-select">
                <Select slim>
                    <option>7 days</option>
                    <option>30 days</option>
                    <option>90 days</option>
                    <option>365 days</option>
                </Select>
            </div>
            <div style={{flexGrow: "1"}}/>
            <ContextSwitcher/>
        </header>

        <div style={{padding: "13px 16px 16px 16px"}}>
            <h3>Resource usage</h3>

            <Flex flexDirection="row" gap="16px">
                {state.summaries.map(s =>
                    <SmallUsageCard
                        key={s.categoryIdx}
                        categoryName={s.category.name}
                        usageText1={usageToString(s.category, s.usage, s.quota, false)}
                        usageText2={usageToString(s.category, s.usage, s.quota, true)}
                        change={0}
                        changeText={"7d change"}
                        chart={s.chart}
                        active={
                            s.category.name === state.activeDashboard?.category?.name &&
                                s.category.provider === state.activeDashboard?.category?.provider
                        }
                        activationKey={s.categoryIdx}
                        onActivate={setActiveCategory}
                    />
                )}
            </Flex>

            {state.activeDashboard &&
                <div className="panels">
                    <div className="panel-grid">
                        <CategoryDescriptorPanel
                            category={state.activeDashboard.category}
                            usage={state.activeDashboard.currentAllocation.usage}
                            quota={state.activeDashboard.currentAllocation.quota}
                            expiresAt={state.activeDashboard.currentAllocation.expiresAt}
                            nextAllocationAt={state.activeDashboard.nextAllocation?.startsAt}
                            nextAllocation={state.activeDashboard.nextAllocation?.quota}
                        />

                        <BreakdownPanel/>

                        <div className="primary-grid-2-2">
                            <UsageOverTimePanel chart={state.activeDashboard.usageOverTime}/>
                            <LargeJobsPanel/>
                            <MostUsedApplicationsPanel/>
                            <JobSubmissionPanel/>
                        </div>
                    </div>
                </div>
            }
        </div>
    </div>;
};

// Panel components
// =====================================================================================================================
// Components for the various panels used in the dashboard.
const CategoryDescriptorPanelStyle = injectStyle("category-descriptor", k => `
    ${k} {
        display: flex;
        flex-direction: column;

        width: 300px !important;
        flex-shrink: 0;
    }

    ${k} figure {
        width: 128px;
        margin: 0 auto;
        margin-bottom: 14px; /* make size correct */
    }

    ${k} figure > *:nth-child(2) > *:first-child {
        position: absolute;
        top: -50px;
        left: 64px;
    }

    ${k} h1 {
        text-align: center;
    }

    ${k} .stat-container {
        display: flex;
        gap: 12px;
        flex-direction: column;
    }
    
    ${k} .stat > *:first-child {
        font-size: 14px;
    }
    
    ${k} .stat > *:nth-child(2) {
        font-size: 16px;
    }
`);

const CategoryDescriptorPanel: React.FunctionComponent<{
    category: Accounting.ProductCategoryV2;
    usage: number;
    quota: number;
    expiresAt: number;
    nextAllocationAt?: number | null;
    nextAllocation?: number | null;
}> = props => {
    const now = timestampUnixMs();
    return <div className={classConcat(CardClass, CategoryDescriptorPanelStyle)}>
        <figure>
            <Icon name={Accounting.productTypeToIcon(props.category.productType)} size={128}/>
            <div style={{position: "relative"}}>
                <ProviderLogo providerId={props.category.provider} size={64}/>
            </div>
        </figure>

        <h1><code>{props.category.name}</code></h1>
        <p>{Accounting.guestimateProductCategoryDescription(props.category.name, props.category.provider)}</p>

        <div style={{flexGrow: 1}}/>

        <div className="stat-container">
            <div className="stat">
                <div>Current allocation</div>
                <div>{usageToString(props.category, props.usage, props.quota, false)}</div>
            </div>

            <div className="stat">
                <div>Allocation expires in</div>
                <div>
                    <TooltipV2 tooltip={Accounting.utcDate(props.expiresAt)}>
                        {formatDistance(props.expiresAt, now)}
                    </TooltipV2>
                </div>
            </div>

            <div className="stat">
                <div>Next allocation</div>
                <div>
                    {props.nextAllocation && props.nextAllocationAt ? <>
                        {Accounting.balanceToString(props.category, props.nextAllocation)}
                        {" in "}
                        <TooltipV2 tooltip={Accounting.utcDate(props.nextAllocationAt)}>
                            {formatDistance(props.nextAllocationAt, now)}
                        </TooltipV2>
                    </> : <>
                        None (<a href="#">apply</a>)
                    </>}
                </div>
            </div>
        </div>
    </div>;
};
const BreakdownStyle = injectStyle("breakdown", k => `
    ${k} {
        /* This places the card aligned with a potential third summary card */
        width: calc(450px + 8px) !important; 
        flex-shrink: 0;
    }
    
    ${k} .pie-wrapper {
        width: 350px;
        margin: 20px auto;
    }

    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
        font-family: var(--monospace);
    }
`);

const BreakdownPanel: React.FunctionComponent<{ productType?: ProductType }> = props => {
    const type = props.productType ?? "COMPUTE";
    const unit = type === "STORAGE" ? "GB" : "DKK";

    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown by</h4>
            <div>
                <Select slim>
                    <option>Project</option>
                    <option>Field of research</option>
                    <option>Machine type</option>
                </Select>
            </div>
        </div>

        <div className="pie-wrapper">
            <Chart
                type="pie"
                series={[10, 20, 30, 30]}
                options={{
                    chart: {
                        animations: {
                            enabled: false,
                        },
                    },
                    labels: ["A", "B", "C", "D"],
                    dataLabels: {
                        enabled: false,
                    },
                    stroke: {
                        show: false,
                    },
                    legend: {
                        show: false,
                    },
                    colors: ["var(--blue)", "var(--green)", "var(--red)", "var(--purple)", "var(--yellow)"],
                }}
                height={350}
                width={350}
            />
        </div>

        <table>
            <thead>
            <tr>
                <th>Project</th>
                <th>Usage</th>
                <th>Change</th>
            </tr>
            </thead>
            <tbody>
            {Array(29).fill(undefined).map((_, idx) => {
                const usage = Math.floor(Math.random() * 13000);
                let change = 250 - Math.random() * 500;
                if (Math.random() < 0.1) {
                    change = 0;
                }

                let changeClass = "unchanged";
                if (change < 0) changeClass = "negative";
                else if (change > 0) changeClass = "positive";

                return <tr key={idx}>
                    <td>DeiC-{Math.floor(Math.random() * 10000).toString().padStart(4, "0")}</td>
                    <td>{Accounting.addThousandSeparators(usage)} {unit}</td>
                    <td className={`change ${changeClass}`}>
                        <span>{change >= 0 ? "+" : "-"}</span>
                        <span>{Math.abs(change).toFixed(2)}%</span>
                    </td>
                </tr>
            })}
            </tbody>
        </table>
    </div>;
};

const MostUsedApplicationsStyle = injectStyle("most-used-applications", k => `
   
    ${k} table tr > td:nth-child(2),
    ${k} table tr > td:nth-child(3) {
        font-family: var(--monospace);
        text-align: right;
    }
`);

const MostUsedApplicationsPanel: React.FunctionComponent = () => {
    return <div className={classConcat(CardClass, PanelClass, MostUsedApplicationsStyle)}>
        <div className="panel-title">
            <h4>Most used applications</h4>
        </div>

        <div className="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Application</th>
                    <th>Number of jobs</th>
                    <th>Change</th>
                </tr>
                </thead>
                <tbody>
                {Array(100).fill(undefined).map((_, i) =>
                    <React.Fragment key={i}>
                        <tr>
                            <td>Visual Studio Code</td>
                            <td>42</td>
                            <td className={"change positive"}>
                                <span>+</span>
                                <span>23,00%</span>
                            </td>
                        </tr>
                        <tr>
                            <td>RStudio</td>
                            <td>32</td>
                            <td className={"change negative"}>
                                <span>-</span>
                                <span>23,00%</span>
                            </td>
                        </tr>
                        <tr>
                            <td>RStudio</td>
                            <td>32</td>
                            <td className={"change unchanged"}>
                                <span>+</span>
                                <span>0,00%</span>
                            </td>
                        </tr>
                    </React.Fragment>
                )}
                </tbody>
            </table>
        </div>
    </div>;
};

const JobSubmissionStyle = injectStyle("job-submission", k => `

    ${k} table tr > td:nth-child(2),
    ${k} table tr > td:nth-child(3),
    ${k} table tr > td:nth-child(4),
    ${k} table tr > td:nth-child(5) {
        font-family: var(--monospace);
    }
    
    ${k} table tr > td:nth-child(3),
    ${k} table tr > td:nth-child(4),
    ${k} table tr > td:nth-child(5) {
        text-align: right;
    }
    
    ${k} table tr > *:nth-child(1) {
        width: 100px;
    }
    
    ${k} table tr > *:nth-child(2) {
        width: 130px;
    }
    
    ${k} table tr > *:nth-child(4),
    ${k} table tr > *:nth-child(5) {
        width: 120px;
    }
`);

const dayNames = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];
const periods = Array(4).fill(undefined).map((_, i) => {
    const start = i * 6;
    const end = (i + 1) * 6;
    return `${start.toString().padStart(2, "0")}:00-${end.toString().padStart(2, "0")}:00`;
});

const JobSubmissionPanel: React.FunctionComponent = () => {
    return <div className={classConcat(CardClass, PanelClass, JobSubmissionStyle)}>
        <div className="panel-title">
            <h4>When are your jobs being submitted?</h4>
        </div>

        <div className="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Day</th>
                    <th>Time of day</th>
                    <th>Count</th>
                    <th>Avg duration</th>
                    <th>Avg queue</th>
                </tr>
                </thead>
                <tbody>
                {Array(4 * 7).fill(undefined).map((_, i) => {
                    const day = dayNames[Math.floor(i / 4)];
                    const period = periods[i % 4];
                    return <tr key={i}>
                        <td>{day}</td>
                        <td>{period}</td>
                        <td>{Math.floor(Math.random() * 30)}</td>
                        <td>{Math.floor(Math.random() * 12).toString().padStart(2, "0")}H {Math.floor(Math.random() * 60).toString().padStart(2, "0")}M</td>
                        <td>{Math.floor(Math.random()).toString().padStart(2, "0")}H {Math.floor(Math.random() * 15).toString().padStart(2, "0")}M {Math.floor(Math.random() * 60).toString().padStart(2, "0")}S</td>
                    </tr>;
                })}
                </tbody>
            </table>
        </div>
    </div>;
}

const UsageOverTimeStyle = injectStyle("usage-over-time", k => `
    ${k} table tbody tr > td:nth-child(1),
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        font-family: var(--monospace);
    }
    
    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
    }
    
    ${k} table tbody tr > td:nth-child(1) {
        width: 160px;
    }
    
    ${k} table.has-change tbody tr > td:nth-child(1) {
        width: unset;
    }
    
    ${k} table.has-change tbody tr > td:nth-child(2),
    ${k} table.has-change tbody tr > td:nth-child(3) {
        width: 100px;
    }
`);

const UsageOverTimePanel: React.FunctionComponent<{ chart: UsageChart }> = ({chart}) => {
    let sum = 0;
    const chartCounter = useRef(0); // looks like apex charts has a rendering bug if the component isn't completely thrown out
    const chartProps = useMemo(() => {
        chartCounter.current++;
        return usageChartToChart(chart, {
            valueFormatter: val => Accounting.addThousandSeparators(val.toFixed(0)),
        });
    }, [chart]);

    return <div className={classConcat(CardClass, PanelClass, UsageOverTimeStyle)}>
        <div className="panel-title">
            <h4>Usage over time</h4>
        </div>

        <Chart key={chartCounter.current} {...chartProps} />

        <div className="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Timestamp</th>
                    <th>Usage</th>
                    <th>Change</th>
                </tr>
                </thead>
                <tbody>
                {chart.dataPoints.map((point, idx) => {
                    if (idx == 0) return null;
                    const change = point.usage - chart.dataPoints[idx - 1].usage;
                    sum += change;
                    return <tr key={idx}>
                        <td>{dateToString(point.timestamp)}</td>
                        <td>{Accounting.addThousandSeparators(point.usage.toFixed(2))} {chart.unit}</td>
                        <td>{change >= 0 ? "+" : ""}{change.toFixed(2)} {chart.unit}</td>
                    </tr>;
                })}
                </tbody>
            </table>
        </div>
    </div>;
};

const LargeJobsStyle = injectStyle("large-jobs", k => `
    ${k} table tbody tr > td:nth-child(2) {
        font-family: var(--monospace);
        text-align: right;
    }
    
    ${k} table thead tr > th:nth-child(2) > div {
        /* styling fix for the icon */
        display: inline-block;
        margin-left: 4px;
    }
    
    ${k} table tbody tr > td:nth-child(2) {
        width: 155px;
    }
`);

const LargeJobsPanel: React.FunctionComponent = () => {
    const fakeJobs: {
        usage: number,
        username: string,
    }[] = [];

    for (let i = 0; i < 50; i++) {
        const d = new Date();
        d.setHours(d.getHours() - Math.floor(Math.random() * 7 * 24));
        fakeJobs.push({
            usage: Math.random() * 300,
            username: `User${i}`
        });
    }

    fakeJobs.sort((a, b) => {
        if (a.usage > b.usage) return -1;
        if (a.usage < b.usage) return 1;
        return 0;
    });

    const dataPoints = fakeJobs.map(it => ({ key: it.username, value: it.usage }));
    const formatter = useCallback((val: number) => {
        return Accounting.addThousandSeparators(val.toFixed(2)) + " DKK";
    }, []);

    return <div className={classConcat(CardClass, PanelClass, LargeJobsStyle)}>
        <div className="panel-title">
            <h4>Jobs by usage</h4>
        </div>

        <div style={{display: "flex", justifyContent: "center"}}>
            <PieChart dataPoints={dataPoints} valueFormatter={formatter} />
        </div>

        <div className="table-wrapper">
            <table>
                <thead>
                <tr>
                    <th>Username</th>
                    <th>
                        Estimated usage
                        {" "}
                        <TooltipV2 tooltip={"This is an estimate based on the values stored in UCloud. Actual usage reported by the provider may differ from the numbers shown here."}>
                            <Icon name={"heroQuestionMarkCircle"} />
                        </TooltipV2>
                    </th>
                </tr>
                </thead>
                <tbody>
                {fakeJobs.map(it => <tr key={it.username}>
                    <td>{it.username}</td>
                    <td>{Accounting.addThousandSeparators(it.usage.toFixed(2))} DKK</td>
                </tr>)}
                </tbody>
            </table>
        </div>
    </div>;
};

const StorageUsageExplorerStyle = injectStyle("storage-usage-explorer", k => `
    ${k} .entry:first-child {
        margin-left: 0;
    }
    
    ${k} .entry {
        margin-left: 27px;
        user-select: none;
    }
    
    ${k} .entry .row {
        cursor: pointer;
        display: flex;
        flex-direction: row;
        gap: 8px;
        margin-bottom: 8px;
    }
    
    ${k} .bar {
        width: 150px;
        height: 25px;
        border-radius: 5px;
        border: 1px solid var(--midGray);
    }
    
    ${k} .bar-fill {
        background: var(--green);
        height: 100%;
    }
`);

const UsageEntry: React.FunctionComponent<{
    icon: IconName;
    title: string;
    usage: string;
    percentage: number;
    depth: number;
    children?: React.ReactNode;
}> = props => {
    const [open, setOpen] = useState(props.depth < 3);
    const toggleOpen = useCallback(() => {
        setOpen(prev => !prev);
    }, []);
    return <div className={"entry"}>
        <div className="row" onClick={toggleOpen}>
            {props.depth === 0 ? null : props.children === undefined ?
                <div style={{width: "16px"}} /> :
                <Icon name={open ? "heroMinusSmall" : "heroPlusSmall"} />
            }
            <Icon name={props.icon} color={"gray"}/>
            <div style={{flexGrow: 1}}>{props.title}</div>
            <div>{props.usage}</div>
            <div className={"bar"} style={{width: `${150 - (10 * props.depth)}px`}}>
                <div className="bar-fill" style={{width: `${props.percentage}%`}}/>
            </div>
        </div>

        {open && props.children}
    </div>;
};

const StorageUsageExplorerPanel: React.FunctionComponent = props => {
    return <div className={classConcat(PanelClass, CardClass, StorageUsageExplorerStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown</h4>
        </div>

        <div>
            You are viewing an old snapshot from <code>23/11/2023 12:25</code>{" "}
            (click <a href="#">here</a> to generate a new one). This breakdown can only show you usage from your own
            drives. Usage from your sub-allocations are not shown here.
        </div>

        <div>
            <UsageEntry icon={"heroBanknotes"} title={"Allocation"} usage={"340TB"} percentage={34} depth={0}>
                <UsageEntry icon={"heroServer"} title={"Home/"} usage={"300TB"} percentage={88.23} depth={1}>
                    <UsageEntry icon={"ftFolder"} title={"Folder 1/"} usage={"100TB"} percentage={33.33} depth={2}/>
                    <UsageEntry icon={"ftFolder"} title={"Folder 2/"} usage={"200TB"} percentage={66.66} depth={2}>
                        <UsageEntry icon={"ftFolder"} title={"A/B/C/D/"} usage={"200TB"} percentage={100} depth={3}>
                            <UsageEntry icon={"ftFolder"} title={"1/"} usage={"50TB"} percentage={25} depth={4}/>
                            <UsageEntry icon={"ftFolder"} title={"2/"} usage={"50TB"} percentage={25} depth={4}/>
                            <UsageEntry icon={"ftFolder"} title={"3/"} usage={"50TB"} percentage={25} depth={4}/>
                            <UsageEntry icon={"ftFolder"} title={"4/"} usage={"50TB"} percentage={25} depth={4}/>
                        </UsageEntry>
                    </UsageEntry>
                </UsageEntry>

                <UsageEntry icon={"heroServer"} title={"Code/"} usage={"40TB"} percentage={11.76} depth={1}>
                    <UsageEntry icon={"ftFolder"} title={"Folder 1/"} usage={"10TB"} percentage={25} depth={2}/>
                    <UsageEntry icon={"ftFolder"} title={"Folder 2/"} usage={"20TB"} percentage={50} depth={2}/>
                    <UsageEntry icon={"ftFolder"} title={"Folder 3/"} usage={"10TB"} percentage={25} depth={2}/>
                </UsageEntry>
            </UsageEntry>
        </div>
    </div>;
};

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.
function dummyChart(): UsageChart {
    const result: UsageChart = {dataPoints: [], unit: "DKK"};
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 30000;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({timestamp: d.getTime(), usage: currentUsage, quota: 0});
        if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += Math.random() * 5000;
        } else if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += Math.random() * 400;
        } else if (i % 2 === 1 && Math.random() >= 0.3) {
            currentUsage += Math.random() * 200;
        }
        d.setHours(d.getHours() + 6);
    }
    return result;
}

function dummyChartQuota() {
    const result: UsageChart = {dataPoints: [], unit: "GB"};
    const d = new Date();
    d.setHours(0, 0, 0);
    d.setDate(d.getDate() - 7);
    let currentUsage = 50;
    for (let i = 0; i < 4 * 7; i++) {
        result.dataPoints.push({timestamp: d.getTime(), usage: currentUsage, quota: 0});
        if (i % 2 === 0 && Math.random() >= 0.5) {
            currentUsage += 20 - (Math.random() * 40);
            currentUsage = Math.max(10, currentUsage);
        }
        d.setHours(d.getHours() + 6);
    }
    return result;
}

const cpuChart1 = dummyChart();
const cpuChart2 = dummyChart();
const storageChart1 = dummyChartQuota();

const fieldOfResearch = {
    "sections": [
        {
            "title": "Natural Sciences",
            "children": [
                "Mathematics",
                "Computer and information sciences",
                "Physical sciences",
                "Chemical sciences",
                "Earth and related environmental sciences",
                "Biological sciences",
                "Other natural sciences"
            ]
        },

        {
            "title": "Engineering and Technology",
            "children": [
                "Civil engineering",
                "Electrical engineering, electronic engineering, information engineering",
                "Mechanical engineering",
                "Chemical engineering",
                "Materials engineering",
                "Medical engineering",
                "Environmental engineering",
                "Environmental biotechnology",
                "Industrial Biotechnology",
                "Nano-technology",
                "Other engineering and technologies"
            ]
        },

        {
            "title": "Medical and Health Sciences",
            "children": [
                "Basic medicine",
                "Clinical medicine",
                "Health sciences",
                "Health biotechnology",
                "Other medical sciences"
            ]
        },

        {
            "title": "Agricultural Sciences",
            "children": [
                "Agriculture, forestry, and fisheries",
                "Animal and dairy science",
                "Veterinary science",
                "Agricultural biotechnology",
                "Other agricultural sciences"
            ]
        },

        {
            "title": "Social Sciences",
            "children": [
                "Psychology",
                "Economics and business",
                "Educational sciences",
                "Sociology",
                "Law",
                "Political Science",
                "Social and economic geography",
                "Media and communications",
                "Other social sciences"
            ]
        },

        {
            "title": "Humanities",
            "children": [
                "History and archaeology",
                "Languages and literature",
                "Philosophy, ethics and religion",
                "Art (arts, history of arts, performing arts, music)",
                "Other humanities"
            ]
        }
    ]
};

const PieChart: React.FunctionComponent<{
    dataPoints: { key: string, value: number }[],
    valueFormatter: (value: number) => string,
    size?: number,
}> = props => {
    const filteredList = useMemo(() => {
        const all = [...props.dataPoints];
        all.sort((a, b) => {
            if (a.value > b.value) return -1;
            if (a.value < b.value) return 1;
            return 0;
        });

        const result = all.slice(0, 4);
        if (all.length > result.length) {
            let othersSum = 0;
            for (let i = result.length; i < all.length; i++) {
                othersSum += all[i].value;
            }
            result.push({ key: "Other", value: othersSum });
        }

        return result;
    }, [props.dataPoints]);
    const series = useMemo(() => {
        return filteredList.map(it => it.value);
    }, [filteredList]);

    const labels = useMemo(() => {
        return filteredList.map(it => it.key);
    }, [filteredList]);

    return <Chart
        type="pie"
        series={series}
        options={{
            chart: {
                animations: {
                    enabled: false,
                },
            },
            labels: labels,
            dataLabels: {
                enabled: false,
            },
            stroke: {
                show: false,
            },
            legend: {
                show: false,
            },
            tooltip: {
                shared: false,
                y: {
                    formatter: function (val) {
                        return props.valueFormatter(val);
                    }
                }
            },
        }}
        height={props.size ?? 350}
        width={props.size ?? 350}
    />
};

interface UsageChart {
    unit: string,
    dataPoints: { timestamp: number, usage: number, quota: number }[];
}

const emptyChart: UsageChart = {
    unit: "",
    dataPoints: [],
};

function usageChartToChart(
    chart: UsageChart,
    options: {
        valueFormatter?: (value: number) => string,
        removeDetails?: boolean,
    } = {}
): ChartProps {
    const result: ChartProps = {};
    const data = chart.dataPoints.map(it => [it.timestamp, it.usage]);
    result.series = [{
        name: "",
        data,
    }];
    result.type = "area";
    result.options = {
        chart: {
            type: "area",
            stacked: false,
            height: 350,
            animations: {
                enabled: false,
            },
            zoom: {
                type: "x",
                enabled: true,
                autoScaleYaxis: true,
            },
            toolbar: {
                show: true,
                tools: {
                    reset: true,
                    zoom: true,
                    zoomin: true,
                    zoomout: true,

                    pan: false, // Performance seems pretty bad, let's just disable it
                    download: false,
                    selection: false,
                },
            },
        },
        dataLabels: {
            enabled: false
        },
        markers: {
            size: 0,
        },
        stroke: {
            curve: "straight",
        },
        fill: {
            type: "gradient",
            gradient: {
                shadeIntensity: 1,
                inverseColors: false,
                opacityFrom: 0.4,
                opacityTo: 0,
                stops: [0, 90, 100]
            }
        },
        colors: ['var(--blue)'],
        yaxis: {
            labels: {
                formatter: function (val) {
                    if (options.valueFormatter) {
                        return options.valueFormatter(val);
                    } else {
                        return val.toString();
                    }
                },
            },
            title: {
                text: (() => {
                    let res = "Usage";
                    res += " (";
                    res += chart.unit;
                    res += ")"
                    return res;
                })()
            },
        },
        xaxis: {
            type: 'datetime',
        },
        tooltip: {
            shared: false,
            y: {
                formatter: function (val) {
                    if (options.valueFormatter) {
                        let res = options.valueFormatter(val);
                        res += " ";
                        res += chart.unit;
                        return res;
                    } else {
                        return val.toString();
                    }
                }
            }
        },
    };

    if (options.removeDetails === true) {
        delete result.options.title;
        result.options.tooltip = {enabled: false};
        const c = result.options.chart!;
        c.sparkline = {enabled: true};
        c.zoom!.enabled = false;
    }

    return result;
}

const SmallUsageCardStyle = injectStyle("small-usage-card", k => `
    ${k} {
        width: 300px;
    }
    
    ${k} .title-row {
        display: flex;
        flex-direction: row;
        margin-bottom: 10px;
        align-items: center;
        gap: 4px;
        width: calc(100% + 4px); /* deal with bad SVG in checkbox */
    }
    
    ${k} .title-row > *:last-child {
        margin-right: 0;
    }
    

    ${k} strong {
        flex-grow: 1;
    }

    ${k} .body {
        display: flex;
        flex-direction: row;
        gap: 8px;
        align-items: center;
    }

    ${k} .border-bottom {
        position: absolute;
        top: -6px;
        width: 112px;
        height: 1px;
        background: var(--midGray);
    }

    ${k} .border-left {
        position: absolute;
        top: -63px;
        height: calc(63px - 6px);
        width: 1px;
        background: var(--midGray);
    }
`);

const SmallUsageCard: React.FunctionComponent<{
    categoryName: string;
    usageText1: string;
    usageText2: string;
    change: number;
    changeText: string;
    chart: UsageChart;
    active: boolean;
    activationKey?: any;
    onActivate: (activationKey?: any) => void;
}> = props => {
    let themeColor: ThemeColor = "midGray";
    if (props.change < 0) themeColor = "red";
    else if (props.change > 0) themeColor = "green";

    const chartProps = useMemo(() => {
        return usageChartToChart(props.chart, {removeDetails: true});
    }, [props.chart]);

    const onClick = useCallback(() => {
        props.onActivate(props.activationKey);
    }, [props.activationKey, props.onActivate]);

    return <a href={`#${props.categoryName}`} onClick={onClick}>
        <div className={classConcat(CardClass, SmallUsageCardStyle)}>
            <div className={"title-row"}>
                <strong><code>{props.categoryName}</code></strong>
                <Radio checked={props.active} onChange={doNothing} />
            </div>

            <div className="body">
                <Chart
                    {...chartProps}
                    width={112}
                    height={63}
                />
                <div>
                    {props.usageText1} <br/>
                    {props.usageText2} <br/>
                    <span style={{color: `var(--${themeColor})`}}>
                        {props.change < 0 ? "" : "+"}
                        {props.change.toFixed(2)}%
                    </span>
                    {" "}{props.changeText}
                </div>
            </div>
            <div style={{position: "relative"}}>
                <div className="border-bottom"/>
            </div>
            <div style={{position: "relative"}}>
                <div className="border-left"/>
            </div>
        </div>
    </a>;
};

const PanelClass = injectStyle("panel", k => `
    ${k} {
        height: 100%;
        width: 100%;
        padding-bottom: 20px;
        
        /* Required for flexible cards to ensure that they are not allowed to grow their height based on their 
           content */
        min-height: 100px; 
        
        display: flex;
        flex-direction: column;
    }

    ${k} .panel-title {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin: 10px 0;
    }

    ${k} .panel-title > *:nth-child(1) {
        font-size: 18px;
        margin: 0;
    }

    ${k} .panel-title > *:nth-child(2) {
        flex-grow: 1;
    }
    
    ${k} .table-wrapper {
        flex-grow: 1;
        overflow-y: auto;
    }
    
    html.light ${k} .table-wrapper {
        box-shadow: inset 0px -11px 8px -10px #ccc;
    }
    
    html.dark ${k} .table-wrapper {
        box-shadow: inset 0px -11px 8px -10px rgba(255, 255, 255, 0.5);
    }
    
    ${k} .table-wrapper.at-bottom {
        box-shadow: unset !important;
    }
    
    html.light ${k} .table-wrapper::before {
        box-shadow: 0px -11px 8px 11px #ccc;
    }
    
    html.dark ${k} .table-wrapper::before {
        box-shadow: 0px -11px 8px 11px rgba(255, 255, 255, 0.5);
    }
    
    ${k} .table-wrapper::before {
        display: block;
        content: " ";
        width: 100%;
        height: 1px;
        position: sticky;
        top: 24px;
    }
    
    ${k} .table-wrapper.at-top::before {
        box-shadow: unset !important;
    }
`);

function usageToString(category: Accounting.ProductCategoryV2, usage: number, quota: number, asPercentage: boolean): string {
    if (asPercentage) {
        if (quota === 0) return "";
        return `${Math.ceil(usage / quota)}% used`;
    } else {
        let builder = "";
        builder += Accounting.balanceToString(category, usage, { removeUnitIfPossible: true, precision: 0 })
        builder += " / ";
        builder += Accounting.balanceToString(category, quota);
        return builder;
    }
}

const PeriodSelector: React.FunctionComponent<{
    value: Period;
    onChange: (period: Period) => void;
}> = props => {
    return <Select slim>
        <option value={"r-7"}>7 days</option>
        <option value={"r-30"}>30 days</option>
        <option value={"r-90"}>90 days</option>
        <option value={"r-365"}>365 days</option>
    </Select>
};

function normalizePeriod(period: Period): [number, number] {
    switch (period.type) {
        case "relative": {
            const now = timestampUnixMs();
            return [now - period.distance, now];
        }

        case "absolute": {
            return [period.start, period.end];
        }
    }
}

// Initial state
// =====================================================================================================================
const initialState: State = {
    remoteData: {},
    summaries: [],
    selectedPeriod: {
        type: "relative",
        distance: 1000 * 60 * 60 * 24 * 7,
    }
};

export default Visualization;
