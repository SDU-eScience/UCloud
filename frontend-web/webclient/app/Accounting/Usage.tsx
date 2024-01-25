import * as React from "react";
import * as Accounting from ".";
import Chart, {Props as ChartProps} from "react-apexcharts";
import {classConcat, injectStyle} from "@/Unstyled";
import {Flex, Icon, Input, Link, Radio} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {dateToString} from "@/Utilities/DateUtilities";
import {CSSProperties, useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {translateBinaryProductCategory} from ".";
import {TooltipV2} from "@/ui-components/Tooltip";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as AccountingB from "./AccountingBinary";
import * as Jobs from "@/Applications/Jobs";
import {useProjectId} from "@/Project/Api";
import {formatDistance} from "date-fns";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {deviceBreakpoint} from "@/ui-components/Hide";
import {CSSVarCurrentSidebarWidth} from "@/ui-components/List";
import Warning from "@/ui-components/Warning";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {useTitle} from "@/Navigation/Redux";
import AppRoutes from "@/Routes";

// State
// =====================================================================================================================
interface State {
    remoteData: {
        chartData?: AccountingB.Charts;
        jobStatistics?: Jobs.JobStatistics;
        requestsInFlight: number;
    },

    summaries: {
        usage: number,
        quota: number,
        category: Accounting.ProductCategoryV2,
        chart: UsageChart,
        breakdownByProject: BreakdownChart,
        categoryIdx: number,
    }[],

    activeDashboard?: {
        idx: number,
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
        breakdownByProject: BreakdownChart,

        jobUsageByUsers?: JobUsageByUsers,
        mostUsedApplications?: MostUsedApplications,
        submissionStatistics?: SubmissionStatistics,
    },

    selectedPeriod: Period,
}

type Period =
    {type: "relative", distance: number, unit: "day" | "month"}
    | {type: "absolute", start: number, end: number}
    ;

// State reducer
// =====================================================================================================================
type UIAction =
    {type: "LoadCharts", charts: AccountingB.Charts, }
    | {type: "LoadJobStats", statistics: Jobs.JobStatistics, }
    | {type: "SelectTab", tabIndex: number}
    | {type: "UpdateSelectedPeriod", period: Period}
    | {type: "UpdateRequestsInFlight", delta: number}
    ;

function stateReducer(state: State, action: UIAction): State {
    switch (action.type) {
        case "UpdateRequestsInFlight": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    requestsInFlight: state.remoteData.requestsInFlight + action.delta
                }
            };
        }

        case "LoadCharts": {
            // TODO Move this into selectChart
            function translateBreakdown(category: Accounting.ProductCategoryV2, chart: AccountingB.BreakdownByProject): BreakdownChart {
                const {name, priceFactor} = Accounting.explainUnit(category);
                const dataPoints: BreakdownChart["dataPoints"] = [];

                const dataPointsLength = chart.data.count;
                for (let i = 0; i < dataPointsLength; i++) {
                    const dataPoint = chart.data.get(i);
                    dataPoints.push({
                        usage: Number(dataPoint.usage) * priceFactor,
                        projectId: dataPoint.projectId,
                        title: dataPoint.title
                    });
                }

                return {unit: name, dataPoints};
            }

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
                if (now < allocation.startDate || now > allocation.endDate) continue;
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
                        breakdownByProject: emptyBreakdownChart,
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
                summary.chart = translateChart(summary.category, chart.overTime);
                summary.breakdownByProject = translateBreakdown(summary.category, chart.breakdownByProject);
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

            return selectChart({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    chartData: data,
                },
                summaries: newSummaries,
            }, selectedIndex);
        }

        case "LoadJobStats": {
            return selectChart({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    jobStatistics: action.statistics
                }
            });
        }

        case "SelectTab": {
            return selectChart(state, action.tabIndex);
        }

        case "UpdateSelectedPeriod": {
            return {
                ...state,
                selectedPeriod: action.period
            };
        }
    }

    function selectChart(state: State, categoryIndex?: number): State {
        const chartData = state.remoteData.chartData;
        if (!chartData) return {...state, activeDashboard: undefined};
        let catIdx = categoryIndex === undefined ? state.activeDashboard?.idx : categoryIndex;
        if (catIdx === undefined || catIdx < 0 || catIdx > chartData.categories.count) {
            return {
                ...state,
                activeDashboard: undefined
            };
        }

        const summary = state.summaries.find(it => it.categoryIdx === catIdx);
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

        let jobUsageByUsers: JobUsageByUsers | undefined = undefined;
        let mostUsedApplications: MostUsedApplications | undefined = undefined;
        let submissionStatistics: SubmissionStatistics | undefined = undefined;
        if (summary.category.productType === "COMPUTE" && state.remoteData.jobStatistics) {
            const stats = state.remoteData.jobStatistics;
            let catIdx: number = -1;
            const catCount = stats.categories.count;
            for (let i = 0; i < catCount; i++) {
                const cat = stats.categories.get(i);
                if (cat.name === summary.category.name && cat.provider === summary.category.provider) {
                    catIdx = i;
                    break;
                }
            }

            const unit = Accounting.explainUnit(summary.category);

            if (catIdx !== -1) {
                const usageCount = stats.usageByUser.count;
                for (let i = 0; i < usageCount; i++) {
                    const usage = stats.usageByUser.get(i);
                    if (usage.categoryIndex !== catIdx) continue;

                    const result: JobUsageByUsers = {unit: unit.name, dataPoints: []};

                    const pointCount = usage.dataPoints.count;
                    for (let j = 0; j < pointCount; j++) {
                        const dataPoint = usage.dataPoints.get(j);
                        result.dataPoints.push(({
                            usage: Number(dataPoint.usage) * unit.priceFactor,
                            username: dataPoint.username
                        }));
                    }

                    jobUsageByUsers = result;
                }

                const appCount = stats.mostUsedApplications.count;
                for (let i = 0; i < appCount; i++) {
                    const appStats = stats.mostUsedApplications.get(i);
                    if (appStats.categoryIndex !== catIdx) continue;

                    const result: MostUsedApplications = {dataPoints: []};
                    const pointCount = appStats.dataPoints.count;
                    for (let j = 0; j < pointCount; j++) {
                        const dataPoint = appStats.dataPoints.get(j);
                        result.dataPoints.push(({
                            applicationTitle: dataPoint.applicationName,
                            count: dataPoint.numberOfJobs
                        }));
                    }

                    mostUsedApplications = result;
                }

                const submissionCount = stats.jobSubmissionStatistics.count;
                for (let i = 0; i < submissionCount; i++) {
                    const submissionStats = stats.jobSubmissionStatistics.get(i);
                    if (submissionStats.categoryIndex !== catIdx) continue;

                    const result: SubmissionStatistics = {dataPoints: []};
                    const pointCount = submissionStats.dataPoints.count;
                    for (let j = 0; j < pointCount; j++) {
                        const dataPoint = submissionStats.dataPoints.get(j);
                        result.dataPoints.push({
                            day: dataPoint.day,
                            hourOfDayStart: dataPoint.hourOfDayStart,
                            hourOfDayEnd: dataPoint.hourOfDayEnd,
                            numberOfJobs: dataPoint.numberOfJobs,
                            averageQueueInSeconds: dataPoint.averageQueueInSeconds,
                            averageDurationInSeconds: dataPoint.averageDurationInSeconds,
                        });
                    }

                    submissionStatistics = result;
                }
            }
        }

        return {
            ...state,
            activeDashboard: {
                idx: catIdx,
                category: summary.category,
                currentAllocation: {
                    usage: summary.usage,
                    quota: summary.quota,
                    expiresAt: earliestExpiration ?? timestampUnixMs(),
                },
                // TODO This doesn't actually work since we only get allocations for the selected period
                nextAllocation: earliestNextAllocation === null ? undefined : {
                    startsAt: earliestNextAllocation,
                    quota: nextQuota,
                },
                usageOverTime: summary.chart,
                breakdownByProject: summary.breakdownByProject,
                jobUsageByUsers,
                mostUsedApplications,
                submissionStatistics,
            }
        };
    }
}

// State reducer middleware
// =====================================================================================================================
type UIEvent =
    UIAction
    | {type: "Init"}
    ;

function useStateReducerMiddleware(doDispatch: (action: UIAction) => void): (event: UIEvent) => unknown {
    const didCancel = useDidUnmount();
    return useCallback(async (event: UIEvent) => {
        function dispatch(ev: UIAction) {
            if (didCancel.current === true) return;
            doDispatch(ev);
        }

        async function invokeAPI<T>(
            parameters: (APICallParameters<unknown, T> | APICallParametersBinary<T>)
        ): Promise<T> {
            dispatch({type: "UpdateRequestsInFlight", delta: 1});
            return callAPI(parameters).finally(() => {
                dispatch({type: "UpdateRequestsInFlight", delta: -1});
            });
        }

        async function doLoad(start: number, end: number) {
            invokeAPI(Jobs.retrieveStatistics({start, end})).then(statistics => {
                dispatch({type: "LoadJobStats", statistics});
            });

            invokeAPI(Accounting.retrieveChartsV2({start, end})).then(charts => {
                dispatch({type: "LoadCharts", charts});
            });
        }

        switch (event.type) {
            case "Init": {
                const [start, end] = normalizePeriod(initialState.selectedPeriod);
                await doLoad(start, end);
                break;
            }

            case "UpdateSelectedPeriod": {
                dispatch(event);
                const [start, end] = normalizePeriod(event.period);
                await doLoad(start, end);
                break;
            }

            default: {
                dispatch(event);
                break;
            }
        }
    }, [doDispatch]);
}


// User-interface
// =====================================================================================================================
const Visualization: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(rawDispatch);

    useTitle("Usage");

    useEffect(() => {
        dispatchEvent({type: "Init"});
    }, [projectId]);

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
        dispatchEvent({type: "SelectTab", tabIndex: key});
    }, [dispatchEvent]);

    const setPeriod = useCallback((period: Period) => {
        dispatchEvent({type: "UpdateSelectedPeriod", period});
    }, [dispatchEvent]);


    // Short-hands
    // -----------------------------------------------------------------------------------------------------------------
    const activeCategory = state.activeDashboard?.category;
    const hasChart3And4 = activeCategory?.productType === "COMPUTE";
    const isAnyLoading = state.remoteData.requestsInFlight !== 0;
    const hasNoMeaningfulData =
        state.activeDashboard === undefined ||
        state.activeDashboard.usageOverTime.dataPoints.every(it => it.usage === 0);

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    // NOTE(Dan): We are not using a <MainContainer/> here on purpose since
    // we want to use _all_ of the space.
    return <div
        className={classConcat(
            VisualizationStyle,
            hasChart3And4 ? undefined : AccountingPanelsOnlyStyle
        )}
    >
        <header className="at-top">
            <h3>Resource usage</h3>
            <div className="duration-select">
                <PeriodSelector value={state.selectedPeriod} onChange={setPeriod} />
            </div>
            <div style={{flexGrow: "1"}} />
            <ContextSwitcher />
        </header>

        <div style={{padding: "13px 16px 16px 16px"}}>
            <h3>Resource usage</h3>

            {!state.remoteData.chartData && !state.remoteData.jobStatistics && state.remoteData.requestsInFlight && <>
                <HexSpin size={64} />
            </>}

            {state.summaries.length === 0 && state.remoteData.requestsInFlight === 0 && <>
                Could not find any usage data!
            </>}

            <Flex flexDirection="row" gap="16px" overflowX={"auto"} paddingBottom={"26px"}>
                {state.summaries.map(s =>
                    <SmallUsageCard
                        key={s.categoryIdx}
                        categoryName={s.category.name}
                        usageText1={usageToString(s.category, s.usage, s.quota, false)}
                        usageText2={usageToString(s.category, s.usage, s.quota, true)}
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
                <>
                    {hasNoMeaningfulData ? "No usage data found" :
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
                                <BreakdownPanel period={state.selectedPeriod} chart={state.activeDashboard.breakdownByProject} />
                                <UsageOverTimePanel chart={state.activeDashboard.usageOverTime} />
                                {activeCategory?.productType === "COMPUTE" && <>
                                    <UsageByUsers loading={isAnyLoading} data={state.activeDashboard.jobUsageByUsers} />
                                    <MostUsedApplicationsPanel data={state.activeDashboard.mostUsedApplications} />
                                    <JobSubmissionPanel data={state.activeDashboard.submissionStatistics} />
                                </>}
                            </div>
                        </div>
                    }
                </>
            }
        </div>
    </div>;
};

// Panel components
// =====================================================================================================================
// Components for the various panels used in the dashboard.
const CategoryDescriptorPanelStyle = injectStyle("category-descriptor", k => `
    
    ${k} .stat > *:first-child {
        font-size: 14px;
    }
    
    ${k} .stat > *:nth-child(2) {
        font-size: 16px;
    }
    
    ${deviceBreakpoint({minWidth: "1901px"})} {
         ${k} {
            display: flex;
            flex-direction: column;
            flex-shrink: 0;
        }   
        
        ${k} > p {
            flex-grow: 1;
        }
        
        ${k} .stat-container {
            display: flex;
            gap: 12px;
            flex-direction: column;
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
            margin: 0 !important;
            margin-top: 19px !important;
            text-wrap: pretty;
        }
    }
    
    ${deviceBreakpoint({maxWidth: "1900px"})} {
        ${k} {
            display: grid;
            grid-template-areas:
                "fig description"
                "fig stats";
            grid-template-columns: 150px 1fr;
            grid-template-rows: auto 50px;
            gap: 20px;
        }
        
        ${k} .figure-and-title {
            grid-area: fig;
            display: flex;
            flex-direction: column;
            justify-content: center;
        }
        
        ${k} p {
            grid-area: description;
        }
        
        ${k} .stat-container {
            grid-area: stats;
            
            display: flex;
            gap: 30px;
            flex-direction: row;
        }
        
        ${k} figure {
            width: 64px;
            margin: 0 auto;
            margin-bottom: 7px; /* make size correct */
        }

        ${k} figure > *:nth-child(2) > *:first-child {
            position: absolute;
            top: -25px;
            left: 32px;
        }
        
        ${k} figure > svg {
            width: 64px;
            height: 64px;
        }
        
        ${k} figure > div > div {
            /* TODO fragile */
            --wrapper-size: 32px !important;
        }

        ${k} h1 {
            font-size: 1.3em;
            text-wrap: pretty;
            text-align: center;
            margin: 0 !important;
            margin-top: 8px !important;
        }
    }

    ${deviceBreakpoint({maxWidth: "799px"})} {
        /* On small width-screens, show descriptions vertically instead of horizontal */
        ${k} {
            display: block;
        }
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
    const description = Accounting.guestimateProductCategoryDescription(props.category.name, props.category.provider);
    return <div className={classConcat(CardClass, CategoryDescriptorPanelStyle)}>
        <div className={"figure-and-title"}>
            <figure>
                <Icon name={Accounting.productTypeToIcon(props.category.productType)} size={128} />
                <div style={{position: "relative"}}>
                    <ProviderLogo providerId={props.category.provider} size={64} />
                </div>
            </figure>
            <h1><code>{props.category.name}</code></h1>
        </div>

        <p>
            {description ? description : <i>No description provided for this product</i>}
        </p>

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
                        None (<Link to={AppRoutes.grants.editor()} href="#">apply</Link>)
                    </>}
                </div>
            </div>
        </div>
    </div>;
};
const BreakdownStyle = injectStyle("breakdown", k => `
    ${k} .pie-wrapper {
        width: 350px;
        height: 350px;
        margin: 20px auto;
        display: flex;
    }

    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
        font-family: var(--monospace);
    }
`);

const BreakdownPanel: React.FunctionComponent<{period: Period, chart: BreakdownChart}> = props => {
    const unit = props.chart.unit;

    const dataPoints = useMemo(
        () => {
            const unsorted = props.chart.dataPoints.map(it => ({key: it.title, value: it.usage}));
            return unsorted.sort((a, b) => {
                // Note(Jonas): Wouldn't `return a.value - b.value` work the same? 
                if (a.value < b.value) return 1;
                if (a.value > b.value) return -1;
                return 0;
            });
        },
        [props.chart]
    );

    const formatter = useCallback((val: number) => {
        return Accounting.addThousandSeparators(val.toFixed(0)) + " " + unit;
    }, [unit]);

    const showWarning = (() => {
        const [start, end] = normalizePeriod(props.period);
        const startDate = new Date(start);
        const endDate = new Date(end);
        return startDate.getUTCFullYear() !== endDate.getUTCFullYear();
    })();

    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown by sub-projects</h4>
        </div>

        {showWarning && <>
            <Warning>This panel is currently unreliable when showing data across multiple allocation periods.</Warning>
        </>}

        <div className="pie-wrapper">
            <PieChart dataPoints={dataPoints} valueFormatter={formatter} />
        </div>

        <table>
            <thead>
                <tr>
                    <th>Project</th>
                    <th>Usage</th>
                </tr>
            </thead>
            <tbody>
                {dataPoints.map((point, idx) => {
                    const usage = point.value;

                    return <tr key={idx}>
                        <td>{point.key}</td>
                        <td>{Accounting.addThousandSeparators(Math.floor(usage))} {unit}</td>
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

const MostUsedApplicationsPanel: React.FunctionComponent<{data?: MostUsedApplications}> = ({data}) => {
    if (data === undefined) return null;

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
                    </tr>
                </thead>
                <tbody>
                    {data.dataPoints.map(it =>
                        <React.Fragment key={it.applicationTitle}>
                            <tr>
                                <td>{it.applicationTitle}</td>
                                <td>{it.count}</td>
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

const DurationOfSeconds: React.FunctionComponent<{duration: number}> = ({duration}) => {
    if (duration > 3600) {
        const hours = Math.floor(duration / 3600);
        const minutes = Math.floor((duration % 3600) / 60);
        return <>{hours.toString().padStart(2, '0')}H {minutes.toString().padStart(2, '0')}M</>;
    } else {
        const minutes = Math.floor(duration / 60);
        const seconds = duration % 60;
        return <>{minutes.toString().padStart(2, '0')}M {seconds.toString().padStart(2, '0')}S</>;
    }
}

const JobSubmissionPanel: React.FunctionComponent<{data?: SubmissionStatistics}> = ({data}) => {
    if (data === undefined) return null;
    const dataPoints = data.dataPoints;
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
                    {dataPoints.map((dp, i) => {
                        const day = dayNames[dp.day];
                        return <tr key={i}>
                            <td>{day}</td>
                            <td>
                                {dp.hourOfDayStart.toString().padStart(2, '0')}:00-
                                {dp.hourOfDayEnd.toString().padStart(2, '0')}:00
                            </td>
                            <td>{dp.numberOfJobs}</td>
                            <td><DurationOfSeconds duration={dp.averageDurationInSeconds} /></td>
                            <td><DurationOfSeconds duration={dp.averageQueueInSeconds} /></td>
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

const ASPECT_RATIO_LINE_CHART: [number, number] = [1 / (16 / 9), 1 / (24 / 9)];
const ASPECT_RATIO_PIE_CHART: [number, number] = [1, 1];

const DynamicallySizedChart: React.FunctionComponent<{
    Component: React.ComponentType<any>,
    chart: any,
    aspectRatio: [number, number],
    maxWidth?: number,
}> = ({Component, chart, aspectRatio, maxWidth}) => {
    // NOTE(Dan): This react component works around the fact that Apex charts needs to know its concrete size to
    // function. This does not play well with the fact that we want to dynamically size the chart based on a combination
    // of a grid and a flexbox.
    //
    // The idea of this component is as follows:
    // 1. Render an empty box (without the chart) to determine the allocated size from of flexbox
    // 2. Tell the chart exactly this size
    //
    // We cannot render the chart without affecting the allocated size. As a result, every time a resize event occurs
    // we temporarily turn off the chart. This allows us to re-record the size of the flexbox and re-render the chart
    // with the correct size.

    // NOTE(Dan): The wrapper is required to ensure the useEffect runs every time.
    const [dimensions, setDimensions] = useState<{height?: string, width?: string}>({});
    const mountPoint = useRef<HTMLDivElement>(null);
    const styleForLayoutTest: CSSProperties = {flexGrow: 2, flexShrink: 1, flexBasis: "400px"};

    useLayoutEffect(() => {
        const listener = () => {
            setDimensions({});
        };
        window.addEventListener("resize", listener);
        return () => {
            window.removeEventListener("resize", listener);
        }
    }, []);

    useLayoutEffect(() => {
        const wrapper = mountPoint.current;
        if (!wrapper) return;
        if (dimensions.height || dimensions.width) return;

        // NOTE(Dan): If we do not add a bit of a delay, then we risk that this API sometimes gives us back a result
        // which is significantly larger than it should be.
        window.setTimeout(() => {
            const [minRatio, maxRatio] = aspectRatio;

            const boundingRect = wrapper.getBoundingClientRect();
            const brWidth = boundingRect.width;
            const brHeight = boundingRect.height;

            // Use full width of either ratio
            // If neither can do full width, go for the smallest ratio

            let width = Math.min(brWidth, maxWidth ?? Number.MAX_SAFE_INTEGER);
            let height = width * minRatio;
            if (height > brHeight) {
                height = width * maxRatio;

                if (height > brHeight) {
                    height = brHeight;
                    width = height / minRatio;
                }
            }

            setDimensions({width: `${width}px`, height: `${height}px`});
        }, 50);
    }, [dimensions]);

    return <div
        style={dimensions.height ? {...dimensions, width: "100%", display: "flex", justifyContent: "center"} : styleForLayoutTest}
        ref={mountPoint}
    >
        {dimensions.height && dimensions.width &&
            <Component
                {...chart}
                height={dimensions.height}
                width={dimensions.width}
            />
        }
    </div>;
}

const UsageOverTimePanel: React.FunctionComponent<{chart: UsageChart}> = ({chart}) => {
    let sum = 0;
    const chartCounter = useRef(0); // looks like apex charts has a rendering bug if the component isn't completely thrown out
    const chartProps = useMemo(() => {
        chartCounter.current++;
        return usageChartToChart(chart, {
            valueFormatter: val => Accounting.addThousandSeparators(val.toFixed(0)),
        });
    }, [chart]);

    const showWarning = (() => {
        const initialUsage = chart.dataPoints[0].usage;
        const initialQuota = chart.dataPoints[0].quota;
        if (initialUsage !== 0) {
            if (chart.dataPoints.some(it => it.usage === 0)) {
                return true;
            }

            if (chart.dataPoints.some(it => it.quota !== initialQuota)) {
                return true;
            }
        }
        return false;
    })();

    return <div className={classConcat(CardClass, PanelClass, UsageOverTimeStyle)}>
        <div className="panel-title">
            <h4>Usage over time</h4>
        </div>

        <DynamicallySizedChart Component={Chart} chart={chartProps} aspectRatio={ASPECT_RATIO_LINE_CHART} />

        {showWarning && <>
            <Warning>
                It looks like the graph is showing data from multiple allocations.
                Fluctuations in usage normally indicate that an allocation has expired.
            </Warning>
        </>}

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
                        if (change === 0) return null;
                        return <tr key={idx}>
                            <td>{dateToString(point.timestamp)}</td>
                            <td>{Accounting.addThousandSeparators(point.usage.toFixed(0))}</td>
                            <td>{change >= 0 ? "+" : ""}{Accounting.addThousandSeparators(change.toFixed(0))}</td>
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
        width: 200px;
    }
`);

const UsageByUsers: React.FunctionComponent<{loading: boolean, data?: JobUsageByUsers}> = ({loading, data}) => {
    const dataPoints = useMemo(() => {
        return data?.dataPoints?.map(it => ({key: it.username, value: it.usage}));
    }, [data?.dataPoints]);
    const formatter = useCallback((val: number) => {
        if (!data) return "";
        return Accounting.addThousandSeparators(val.toFixed(2)) + " " + data.unit;
    }, [data?.unit]);


    return <div className={classConcat(CardClass, PanelClass, LargeJobsStyle)}>
        <div className="panel-title">
            <h4>Usage by users</h4>
        </div>

        {data !== undefined && dataPoints !== undefined ? <>
            <PieChart dataPoints={dataPoints} valueFormatter={formatter} />

            <div className="table-wrapper">
                <table>
                    <thead>
                        <tr>
                            <th>Username</th>
                            <th>
                                Estimated usage
                                {" "}
                                <TooltipV2
                                    tooltip={"This is an estimate based on the values stored in UCloud. Actual usage reported by the provider may differ from the numbers shown here."}>
                                    <Icon name={"heroQuestionMarkCircle"} />
                                </TooltipV2>
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {data.dataPoints.map(it => <tr key={it.username}>
                            <td>{it.username}</td>
                            <td>{Accounting.addThousandSeparators(it.usage.toFixed(0))} {data.unit}</td>
                        </tr>)}
                    </tbody>
                </table>
            </div>
        </> : <>
            {loading ? <HexSpin size={32} /> : "No usage data found"}
        </>}
    </div>;
};

// Utility components
// =====================================================================================================================

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
    dataPoints: {key: string, value: number}[],
    valueFormatter: (value: number) => string,
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
            result.push({key: "Other", value: othersSum});
        }

        return result;
    }, [props.dataPoints]);
    const series = useMemo(() => {
        return filteredList.map(it => it.value);
    }, [filteredList]);

    const labels = useMemo(() => {
        return filteredList.map(it => it.key);
    }, [filteredList]);

    const chartProps = useMemo(() => {
        return {
            type: "pie",
            series: series,
            options: {
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
            }
        };
    }, [series]);

    return <DynamicallySizedChart Component={Chart} chart={chartProps} aspectRatio={ASPECT_RATIO_PIE_CHART} maxWidth={350} />;
};

interface SubmissionStatistics {
    dataPoints: {
        day: number;
        hourOfDayStart: number;
        hourOfDayEnd: number;
        numberOfJobs: number;
        averageDurationInSeconds: number;
        averageQueueInSeconds: number;
    }[];
}

interface MostUsedApplications {
    dataPoints: {applicationTitle: string; count: number;}[],
}

interface JobUsageByUsers {
    unit: string,
    dataPoints: {username: string; usage: number;}[],
}

interface BreakdownChart {
    unit: string,
    dataPoints: {projectId?: string | null, title: string, usage: number}[];
}

const emptyBreakdownChart: BreakdownChart = {
    unit: "",
    dataPoints: [],
};

interface UsageChart {
    unit: string,
    dataPoints: {timestamp: number, usage: number, quota: number}[];
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
        colors: ['var(--primaryMain)'],
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
            theme: "dark",
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
        --offset: 0px;
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
        top: var(--offset);
        width: 112px;
        height: 1px;
        background: var(--borderColor);
    }

    ${k} .border-left {
        position: absolute;
        top: -63px;
        height: calc(63px + var(--offset));
        width: 1px;
        background: var(--borderColor);
    }
`);

const SmallUsageCard: React.FunctionComponent<{
    categoryName: string;
    usageText1: string;
    usageText2: string;
    chart: UsageChart;
    active: boolean;
    activationKey?: any;
    onActivate: (activationKey?: any) => void;
}> = props => {
    const chartKey = useRef(0);
    const chartProps = useMemo(() => {
        chartKey.current++;
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
                    key={chartKey.current.toString()}
                    {...chartProps}
                    width={112}
                    height={63}
                />
                <div>
                    {props.usageText1} <br />
                    {props.usageText2}
                </div>
            </div>
            <div style={{position: "relative"}}>
                <div className="border-bottom" />
            </div>
            <div style={{position: "relative"}}>
                <div className="border-left" />
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
        z-index: 1; /* HACK(Jonas): Why is this needed for smaller widths? */
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
        min-height: 200px;
        flex-shrink: 5;
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
        return `${Math.ceil((usage / quota) * 100)}% used`;
    } else {
        let builder = "";
        builder += Accounting.balanceToString(category, usage, {removeUnitIfPossible: true, precision: 0})
        builder += " / ";
        builder += Accounting.balanceToString(category, quota, {precision: 0});
        return builder;
    }
}

const PeriodStyle = injectStyle("period-selector", k => `
    ${k} {
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        padding: 6px 12px;
        display: flex;
    }
    
    ${k}:hover {
        border: 1px solid var(--borderColorHover);
    }
    
    .${GradientWithPolygons} ${k} {
        border: 1px solid var(--borderColor);
    }
    
    .${GradientWithPolygons} ${k}:hover {
        border: 1px solid var(--borderColorHover);
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
    const [start, end] = normalizePeriod(props.value);

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

        const newPeriod: Period = {
            type: "absolute",
            start: isStart ? (target.valueAsDate?.getTime() ?? start) : start,
            end: isStart ? end : (target.valueAsDate?.getTime() ?? end),
        };

        props.onChange(newPeriod);
    }, [start, end, props.onChange]);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent={true}
        noYPadding={true}
        trigger={
            <div className={PeriodStyle}>
                <div style={{width: "180px"}}>{periodToString(props.value)}</div>
                <Icon name="heroChevronDown" size="14px" ml="4px" mt="4px" />
            </div>
        }
    >
        <div className={PeriodSelectorBodyStyle}>
            <div>
                <b>Absolute time range</b>

                <label>
                    From
                    <Input className={"start"} onChange={onChange} type={"date"} value={formatTs(start)} />
                </label>
                <label>
                    To
                    <Input className={"end"} onChange={onChange} type={"date"} value={formatTs(end)} />
                </label>
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

function normalizePeriod(period: Period): [number, number] {
    switch (period.type) {
        case "relative": {
            let start = new Date();
            const end = start.getTime();
            start.setHours(0, 0, 0, 0);

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
            return [start.getTime(), end];
        }

        case "absolute": {
            return [period.start, period.end];
        }
    }
}

// Styling
// =====================================================================================================================
const AccountingPanelsOnlyStyle = injectStyle("accounting-panels-only", () => "");
const VisualizationStyle = injectStyle("visualization", k => `
    ${k} header {
        position: fixed;
        top: 0;
        left: var(${CSSVarCurrentSidebarWidth});
        
        background: var(--backgroundDefault);
        
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        
        height: 50px;
        width: calc(100vw - var(${CSSVarCurrentSidebarWidth}));
        
        padding: 0 16px;
        z-index: 10;
        
        box-shadow: var(--defaultShadow);
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

    ${k} table {
        width: 100%;
        border-collapse: separate;
        border-spacing: 0;
    }
    
    ${k} tr > td:first-child,
    ${k} tr > th:first-child {
        border-left: 2px solid var(--borderColor);
    }

    ${k} td, 
    ${k} th {
        padding: 0 8px;
        border-right: 2px solid var(--borderColor);
    }

    ${k} tbody > tr:last-child > td {
        border-bottom: 2px solid var(--borderColor);
    }

    ${k} th {
        text-align: left;
        border-top: 2px solid var(--borderColor);
        border-bottom: 2px solid var(--borderColor);
        position: sticky;
        top: 0;
        background: var(--backgroundCard); /* matches card background */
    }
    
    ${k} .change > span:nth-child(1) {
        float: left;
    }
    
    ${k} .change > span:nth-child(2) {
        float: right;
    }
    
    ${k} .change.positive {
        color: var(--successMain);
    }
    
    ${k} .change.negative {
        color: var(--errorMain);
    }
    
    ${k} .change.unchanged {
        color: var(--borderGray);
    }
    
    ${k} .apexcharts-tooltip {
        color: var(--textPrimary);
    }
    
    ${k} .apexcharts-yaxis-title text,
    ${k} .apexcharts-yaxis-texts-g text,
    ${k} .apexcharts-xaxis-texts-g text {
        fill: var(--textPrimary);
    }
    
    /* Panel layouts */
    /* ============================================================================================================== */
    ${k} .panel-grid {
        gap: 16px;
    }
    
    ${deviceBreakpoint({minWidth: "1901px"})} {
        ${k} .panel-grid {
            display: grid;
            grid-template-areas: 
                "category breakdown over-time chart2"
                "category breakdown over-time chart2"
                "category breakdown over-time chart2"
                "category breakdown chart3 chart4"
                "category breakdown chart3 chart4";
            height: calc(100vh - 165px - 50px - 26px);
            grid-template-columns: 300px 450px 1fr 1fr;
        }
    }
    
    ${deviceBreakpoint({maxWidth: "1900px"})} {
        ${k} .panel-grid {
            display: grid;
            grid-template-areas: 
                "category category category"
                "breakdown over-time chart2"
                "breakdown chart3 chart4";
            grid-template-rows: 160px 900px 500px;
        }
    }
    
    ${deviceBreakpoint({maxWidth: "1500px"})} {
        ${k} .panel-grid {
            display: grid;
            grid-template-areas: 
                "category category"
                "over-time over-time"
                "breakdown chart2"
                "chart3 chart4";
            grid-template-rows: 160px 1000px 900px 500px;
        }
    }
    
    ${deviceBreakpoint({maxWidth: "900px"})} {
        ${k} .panel-grid {
            display: flex;
            flex-direction: column;
            gap: 16px;
        }
    }
    
    .${CategoryDescriptorPanelStyle} {
        grid-area: category;
    }
    
    .${BreakdownStyle} {
        grid-area: breakdown;
    }
    
    .${UsageOverTimeStyle} {
        grid-area: over-time;
    }
    
    ${k}.${AccountingPanelsOnlyStyle} .${UsageOverTimeStyle} {
        grid-row-start: over-time;
        grid-row-end: chart4;
        grid-column-start: over-time;
        grid-column-end: chart4;
    }
    
    .${LargeJobsStyle} {
        grid-area: chart2;
    }
    
    .${MostUsedApplicationsStyle} {
        grid-area: chart3;
    }
    
    .${JobSubmissionStyle} {
        grid-area: chart4;
    }
`);

// Initial state
// =====================================================================================================================
const initialState: State = {
    remoteData: {requestsInFlight: 0},
    summaries: [],
    selectedPeriod: {
        type: "relative",
        distance: 7,
        unit: "day"
    }
};

export default Visualization;
