import * as React from "react";
import * as Accounting from ".";
import Chart, {Props as ChartProps} from "react-apexcharts";
import {classConcat, injectStyle, makeClassName} from "@/Unstyled";
import {Flex, Icon, Input, Radio, Text, MainContainer, Box, Select} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {dateToString} from "@/Utilities/DateUtilities";
import {CSSProperties, useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {BreakdownByProjectAPI, categoryComparator, ChartsAPI, UsageOverTimeAPI} from ".";
import {TooltipV2} from "@/ui-components/Tooltip";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as Jobs from "@/Applications/Jobs";
import {useProjectId} from "@/Project/Api";
import {formatDistance} from "date-fns";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {deviceBreakpoint} from "@/ui-components/Hide";
import Warning from "@/ui-components/Warning";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useProject} from "@/Project/cache";
import * as Heading from "@/ui-components/Heading";
import {RichSelectChildComponent} from "@/ui-components/RichSelect";
import {Feature, hasFeature} from "@/Features";

// Constants
// =====================================================================================================================
const JOBS_UNIT_NAME = "Jobs";

// State
// =====================================================================================================================
interface State {
    remoteData: {
        chartData?: ChartsAPI;
        jobStatistics?: Jobs.JobStatistics;
        requestsInFlight: number;
        initialLoadDone: boolean;
    },

    summaries: {
        title: string,
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
        usageOverTime: UsageChart[];
        breakdownByProject: BreakdownChart[];

        jobUsageByUsers?: JobUsageByUsers,
        mostUsedApplications?: MostUsedApplications,
        submissionStatistics?: SubmissionStatistics,
    },

    selectedPeriod: Period,
    availableUnits: string[];
    activeUnit?: string;
}

type Period =
    | {type: "relative", distance: number, unit: "day" | "month"}
    | {type: "absolute", start: number, end: number}
    ;

// State reducer
// =====================================================================================================================
type UIAction =
    | {type: "LoadCharts", charts: ChartsAPI, }
    | {type: "LoadJobStats", statistics: Jobs.JobStatistics, }
    | {type: "SelectTab", tabIndex: number}
    | {type: "UpdateSelectedPeriod", period: Period}
    | {type: "UpdateRequestsInFlight", delta: number}
    | {type: "UpdateActiveUnit", unit: string}
    ;

function stateReducer(state: State, action: UIAction): State {
    switch (action.type) {
        case "UpdateRequestsInFlight": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    requestsInFlight: state.remoteData.requestsInFlight + action.delta,
                    initialLoadDone: true,
                }
            };
        }

        case "UpdateActiveUnit": {
            return {
                ...state,
                activeDashboard: {
                    ...state.activeDashboard!,
                    usageOverTime: state.summaries.filter(it => it.breakdownByProject.unit === action.unit).map(it => it.chart),
                    breakdownByProject: state.summaries.filter(it => it.breakdownByProject.unit === action.unit).map(it => it.breakdownByProject)
                },
                activeUnit: action.unit
            };
        }

        case "LoadCharts": {
            // TODO Move this into selectChart
            function translateBreakdown(category: Accounting.ProductCategoryV2, chart: BreakdownByProjectAPI, nameAndProvider: string): BreakdownChart {
                const {name} = Accounting.explainUnit(category);
                const dataPoints = chart.data;

                return {unit: name, dataPoints, nameAndProvider};
            }

            function translateChart(category: Accounting.ProductCategoryV2, chart: UsageOverTimeAPI): UsageChart {
                const {name} = Accounting.explainUnit(category);
                const dataPoints = chart.data

                return {unit: name, dataPoints, name: category.name};
            }

            const data = action.charts;
            const newSummaries: State["summaries"] = [];

            const sorted = data.allocGroups.sort((a, b) =>
                categoryComparator(data.categories[a.productCategoryIndex], data.categories[b.productCategoryIndex])
            );

            for (let i = 0; i < data.allocGroups.length; i++) {
                const group = sorted[i];
                //if (now < allocation.startDate || now > allocation.endDate) continue;
                const category = data.categories[group.productCategoryIndex];

                const existingIndex = newSummaries.findIndex(it =>
                    it.category.name === category.name && it.category.provider === category.provider
                );

                let summary: State["summaries"][0];
                if (existingIndex === -1) {
                    summary = {
                        title: category.name + "/" + category.provider,
                        usage: 0,
                        quota: 0,
                        category: category,
                        chart: emptyChart,
                        breakdownByProject: emptyBreakdownChart,
                        categoryIdx: group.productCategoryIndex,
                    };
                    newSummaries.push(summary);
                } else {
                    summary = newSummaries[existingIndex];
                }

                summary.usage += Number(group.group.usage);
                // This could be replaced by this, right?
                // summary.quota += group.group.allocations.reduce((acc, alloc) => acc + alloc.quota, 0);
                let quota = 0;
                group.group.allocations.forEach(alloc => quota += alloc.quota)
                summary.quota += Number(quota);
            }

            for (let i = 0; i < data.charts.length; i++) {
                const chart = data.charts[i];
                const summary = newSummaries.find(it => it.categoryIdx === chart.categoryIndex);
                if (!summary) continue;
                summary.chart = translateChart(summary.category, chart.overTime);
                summary.breakdownByProject = translateBreakdown(summary.category, chart.breakdownByProject, summary.title);
            }

            const currentlySelectedCategory = state.activeDashboard?.category;
            let selectedIndex = sorted[0]?.productCategoryIndex ?? 0;

            if (currentlySelectedCategory) {
                const selectedSummary = newSummaries.find(it =>
                    it.category.name === currentlySelectedCategory.name &&
                    it.category.provider === currentlySelectedCategory.provider
                );

                if (selectedSummary) selectedIndex = selectedSummary.categoryIdx;
            }

            const availableUnits = new Set(newSummaries.map(it => it.breakdownByProject.unit));
            const activeUnit = availableUnits[0];

            return selectChart({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    chartData: data,
                },
                summaries: newSummaries,
                availableUnits: [...availableUnits],
                activeUnit: activeUnit
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
        if (catIdx === undefined || catIdx < 0 || catIdx > chartData.categories.length) {
            return {
                ...state,
                activeDashboard: undefined
            };
        }

        const summaries = state.summaries.filter(it => it.categoryIdx === catIdx);
        if (!summaries.length) return {...state, activeDashboard: undefined};

        let earliestNextAllocation: number | null = null;
        let earliestExpiration: number | null = null;

        const now = BigInt(timestampUnixMs());
        for (let i = 0; i < chartData.allocGroups.length; i++) {

            const group = chartData.allocGroups[i];
            group.group.allocations.forEach(alloc => {
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
            })
        }

        let nextQuota = 0;
        if (earliestNextAllocation !== null) {
            for (let i = 0; i < chartData.allocGroups.length; i++) {
                const group = chartData.allocGroups[i];
                group.group.allocations.forEach(alloc => {
                    // @ts-ignore
                    if (earliestNextAllocation < alloc.startDate || earliestNextAllocation > alloc.endDate) {
                        //nothing
                    } else {
                        nextQuota += Number(alloc.quota);
                    }
                })
            }
        }

        let jobUsageByUsers: JobUsageByUsers | undefined = undefined;
        let mostUsedApplications: MostUsedApplications | undefined = undefined;
        let submissionStatistics: SubmissionStatistics | undefined = undefined;
        for (const summary of summaries) {
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
        }

        const availableUnits = new Set(state.summaries.map(it => it.breakdownByProject.unit))
        if (jobUsageByUsers ||
            mostUsedApplications ||
            submissionStatistics
        ) {
            availableUnits.add(JOBS_UNIT_NAME);
        }

        return {
            ...state,
            activeDashboard: {
                idx: catIdx,
                category: summaries[0].category,
                currentAllocation: {
                    usage: summaries[0].usage,
                    quota: summaries[0].quota,
                    expiresAt: earliestExpiration ?? timestampUnixMs(),
                },
                usageOverTime: summaries.map(it => it.chart),
                breakdownByProject: summaries.map(it => it.breakdownByProject),
                jobUsageByUsers,
                mostUsedApplications,
                submissionStatistics,
            },
            availableUnits: [...availableUnits],
            activeUnit: availableUnits[0]
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
            parameters: (APICallParameters<unknown, T> | APICallParameters<T>)
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
                const {start, end} = normalizePeriod(initialState.selectedPeriod);
                await doLoad(start, end);
                break;
            }

            case "UpdateSelectedPeriod": {
                dispatch(event);
                const {start, end} = normalizePeriod(event.period);
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
    const project = useProject();
    const projectId = useProjectId();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(rawDispatch);

    usePage("Usage", SidebarTabId.PROJECT);

    useEffect(() => {
        dispatchEvent({type: "Init"});
    }, [projectId]);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. As you are writing
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

    const setActiveCategory2 = useCallback((element: State["summaries"][0]) => {
        dispatchEvent({type: "SelectTab", tabIndex: element.categoryIdx});
    }, [dispatchEvent]);

    const setPeriod = useCallback((period: Period) => {
        dispatchEvent({type: "UpdateSelectedPeriod", period});
    }, [dispatchEvent]);

    const setActiveUnit = useCallback((unit: string) => {
        dispatchEvent({type: "UpdateActiveUnit", unit});
    }, [dispatchEvent]);


    // Short-hands
    // -----------------------------------------------------------------------------------------------------------------
    const activeCategory = state.activeDashboard?.category;
    const hasChart3And4 = activeCategory?.productType === "COMPUTE";
    const isAnyLoading = state.remoteData.initialLoadDone && state.remoteData.requestsInFlight !== 0;
    const hasNoMeaningfulData =
        state.activeDashboard === undefined ||
        state.activeDashboard.usageOverTime.every(it => it.dataPoints.every(it => it.usage === 0)) &&
        (state.summaries.length === 0 && state.remoteData.requestsInFlight === 0);

    // Actual user-interface
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

    return <MainContainer
        headerSize={0}
        main={<div
            className={VisualizationStyle}
        >
            <header>
                <h3 className="title" style={{marginTop: "auto", marginBottom: "auto"}}>Resource usage</h3>
                <div className="duration-select">
                    <PeriodSelector value={state.selectedPeriod} onChange={setPeriod} />
                </div>
                <div style={{flexGrow: "1"}} />
                <ProjectSwitcher />
            </header>

            <div style={{padding: "13px 16px 16px 16px", zIndex: -1}}>
                {!state.remoteData.chartData && !state.remoteData.jobStatistics && state.remoteData.requestsInFlight ? <>
                    <HexSpin size={64} />
                </> : null}

                {hasNoMeaningfulData ? <NoData loading={isAnyLoading} productType={activeCategory?.productType} /> : null}

                {!hasFeature(Feature.ALTERNATIVE_USAGE_SELECTOR) ? null : <Box pb={32}>
                    {/* 
                    <div><b>Resource allocation</b></div>
                    <RichSelect
                        items={state.summaries}
                        keys={["title"]}
                        RenderRow={RenderProductSelector}
                        RenderSelected={RenderProductSelector}
                        onSelect={setActiveCategory2}
                        fullWidth
                        selected={!state.activeDashboard ? undefined : state.summaries.find(it => it.category === state.activeDashboard?.category)}
                    /> */}
                    <div><b>Unit selection</b></div>
                    {state.availableUnits.length === 0 ? null : <Select onChange={e => setActiveUnit(e.target.value)} defaultValue={state.availableUnits[0]}>
                        {state.availableUnits.map(it => <option key={it} value={it}>{it}</option>)}
                    </Select>}
                </Box>}

                {hasFeature(Feature.ALTERNATIVE_USAGE_SELECTOR) ? null : <>
                    <Flex flexDirection="row" gap="16px" overflowX={"auto"} paddingY={"26px"} paddingX={"12px"}>
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

                </>}

                {state.activeDashboard ?
                    <div className="panels">
                        <div className={classConcat("panel-grid")}>
                            {state.activeUnit === JOBS_UNIT_NAME ? <>
                                <UsageByUsers loading={isAnyLoading} data={state.activeDashboard.jobUsageByUsers} />
                                <MostUsedApplicationsPanel data={state.activeDashboard.mostUsedApplications} />
                                <JobSubmissionPanel data={state.activeDashboard.submissionStatistics} />
                            </> : <>
                                {hasFeature(Feature.ALTERNATIVE_USAGE_SELECTOR) ? null : <>
                                    <CategoryDescriptorPanel
                                        category={state.activeDashboard.category}
                                        usage={state.activeDashboard.currentAllocation.usage}
                                        quota={state.activeDashboard.currentAllocation.quota}
                                        expiresAt={state.activeDashboard.currentAllocation.expiresAt}
                                    />
                                </>}
                                <UsageBreakdownPanel isLoading={isAnyLoading} unit={state.activeUnit} period={state.selectedPeriod} charts={state.activeDashboard.breakdownByProject} />
                                <FullyMergedUsageBreakdownPanel isLoading={isAnyLoading} unit={state.activeUnit} period={state.selectedPeriod} charts={state.activeDashboard.breakdownByProject} />
                                <UsageOverTimePanel charts={state.activeDashboard.usageOverTime} />
                            </>}
                        </div>
                    </div> : null}
            </div>
        </div>}
    />;
};

const NoDataClass = injectStyle("no-data", k => `
    ${k} {
        background: var(--primaryMain);
        height: 100px;
        width: 100px;
        display: flex;
        border-radius: 100px;
        align-items: center;
        justify-content: center;
    }
`);

function NoData({productType, loading}: {loading: boolean; productType?: Accounting.ProductArea;}): React.ReactNode {
    if (loading) return null;
    return <Flex mx="auto" my="auto" flexDirection="column" alignItems="center" justifyContent="center" width="400px" height="400px">
        <div className={NoDataClass}>
            <Icon name={Accounting.productTypeToIcon(productType ?? "STORAGE")} color2="primaryContrast" color="primaryContrast" size={60} />
        </div>
        <Text mt="16px" fontSize={16}>No usage data found!</Text>
    </Flex>;
}

// Panel components
// =====================================================================================================================
// Components for the various panels used in the dashboard.

const HasAlotOfInfoClass = makeClassName("has-a-lot-of-info");
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

    /* HACK(Jonas): Is fully repeated of the above media-query content. This is very bad. */
    ${k}${HasAlotOfInfoClass.dot} {
        display: grid;
        grid-template-areas:
            "fig description"
            "fig stats";
        grid-template-columns: 150px 1fr;
        grid-template-rows: auto 50px;
        gap: 20px;
    }

    ${k}${HasAlotOfInfoClass.dot} .figure-and-title {
        grid-area: fig;
        display: flex;
        flex-direction: column;
        justify-content: center;
    }

    ${k}${HasAlotOfInfoClass.dot} p {
        grid-area: description;
    }

    ${k}${HasAlotOfInfoClass.dot} .stat-container {
        grid-area: stats;

        display: flex;
        gap: 30px;
        flex-direction: row;
    }

    ${k}${HasAlotOfInfoClass.dot} figure {
        width: 64px;
        margin: 0 auto;
        margin-bottom: 7px; /* make size correct */
    }

    ${k}${HasAlotOfInfoClass.dot} figure > *:nth-child(2) > *:first-child {
        position: absolute;
        top: -25px;
        left: 32px;
    }

    ${k}${HasAlotOfInfoClass.dot} figure > svg {
        width: 64px;
        height: 64px;
    }

    ${k}${HasAlotOfInfoClass.dot} figure > div > div {
        /* TODO fragile */
        --wrapper-size: 32px !important;
    }

    ${k}${HasAlotOfInfoClass.dot} h1 {
        font-size: 1.3em;
        text-wrap: pretty;
        text-align: center;
        margin: 0 !important;
        margin-top: 8px !important;
    }
    /* HACK(Jonas): Is fully repeated of the above media-query content. This is very bad. */

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
}> = props => {
    const now = timestampUnixMs();
    const isCompute = props.category.productType === "COMPUTE";
    const description = Accounting.guesstimateProductCategoryDescription(props.category.name, props.category.provider);
    return <div className={classConcat(CardClass, CategoryDescriptorPanelStyle, isCompute ? HasAlotOfInfoClass.class : undefined)}>
        <div className={"figure-and-title"}>
            <figure>
                <Icon name={Accounting.productTypeToIcon(props.category.productType)} size={128} />
                <div style={{position: "relative"}}>
                    <ProviderLogo providerId={props.category.provider} size={32} />
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


const UsageBreakdownPanel: React.FunctionComponent<{isLoading: boolean; unit?: string; period: Period; charts: BreakdownChart[];}> = props => {
    const unit = props.unit ?? "";

    const [activeNameAndProvider, setActiveNameAndProvider] = useState("");

    React.useEffect(() => {
        const nonEmptyChart = props.charts.find(it => it.dataPoints.length > 0);
        if (nonEmptyChart) setActiveNameAndProvider(nonEmptyChart.nameAndProvider)
        else setActiveNameAndProvider(props.charts[0]?.nameAndProvider ?? "");
    }, [props.charts])

    const dataPoints = useMemo(() => {
        const chart = props.charts.find(it => it.nameAndProvider === activeNameAndProvider);
        const unsorted = chart?.dataPoints.map(it => ({key: it.title, value: it.usage})) ?? [];
        return {points: unsorted.sort((a, b) => a.value - b.value), name: chart?.nameAndProvider ?? ""};
    }, [props.charts, activeNameAndProvider]);

    const formatter = useCallback((val: number) => {
        return Accounting.addThousandSeparators(val.toFixed(0)) + " " + unit;
    }, [unit]);

    const showWarning = (() => {
        const {start, end} = normalizePeriod(props.period);
        const startDate = new Date(start);
        const endDate = new Date(end);
        return startDate.getUTCFullYear() !== endDate.getUTCFullYear();
    })();

    /* TODO(Jonas): re-introduce this */
    const datapointSum = useMemo(() => dataPoints.points.reduce((a, b) => a + b.value, 0), [dataPoints]);

    if (props.isLoading) return null;

    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown by sub-projects (with name and provider selector)</h4>
        </div>

        {datapointSum === 0 ? null : <Select onChange={e => setActiveNameAndProvider(e.target.value)} value={activeNameAndProvider}>
            {props.charts.map(({nameAndProvider}) => <option key={nameAndProvider} value={nameAndProvider}>{nameAndProvider}</option>)}
        </Select>}

        {showWarning && <>
            <Warning>This panel is currently unreliable when showing data across multiple allocation periods.</Warning>
        </>}

        {datapointSum === 0 ? null : <div className="pie-wrapper">
            <PieChart dataPoints={dataPoints.points} valueFormatter={formatter} onDataPointSelection={() => {}} />
        </div>}

        {/* Note(Jonas): this is here, otherwise <tbody> y-overflow will not be respected */}
        {dataPoints.points.length === 0 ? <>No usage data found</> :
            <div style={{overflowY: "scroll"}}>
                <table>
                    <thead>
                        <tr>
                            <th>Project</th>
                            <th>Usage</th>
                        </tr>
                    </thead>
                    <tbody>
                        {dataPoints.points.map((point, idx) => {
                            const usage = point.value;
                            return <tr key={idx}>
                                <td>{point.key}</td>
                                <td>{Accounting.addThousandSeparators(Math.round(usage))} {unit}</td>
                            </tr>
                        })}
                    </tbody>
                </table>
            </div>
        }
    </div>;
};

const FullyMergedUsageBreakdownPanel: React.FunctionComponent<{isLoading: boolean; unit?: string; period: Period; charts: BreakdownChart[];}> = props => {
    const unit = props.unit ?? "";
    const [singleChartSelected, setSingleChartSelected] = useState<string | undefined>();

    const fullyMergedChart = React.useMemo(() => {
        return {
            unit: props.unit ?? "",
            dataPoints: props.charts.flatMap(it => it.dataPoints.map(d => ({...d, nameAndProvider: it.nameAndProvider})))
        }
    }, [props.charts, props.unit]);

    const dataPoints = useMemo(() => {
        const unsorted = fullyMergedChart.dataPoints.map(it => ({key: it.title, value: it.usage, nameAndProvider: it.nameAndProvider})) ?? [];
        return unsorted.sort((a, b) => a.value - b.value);
    }, [props.charts]);

    const formatter = useCallback((val: number) => {
        return Accounting.addThousandSeparators(val.toFixed(0)) + " " + unit;
    }, [unit]);

    const showWarning = (() => {
        const {start, end} = normalizePeriod(props.period);
        const startDate = new Date(start);
        const endDate = new Date(end);
        return startDate.getUTCFullYear() !== endDate.getUTCFullYear();
    })();

    const datapointSum = useMemo(() => dataPoints.reduce((a, b) => a + b.value, 0), [dataPoints]);

    if (props.isLoading) return null;


    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
        <div className="panel-title">
            <h4>Usage breakdown by sub-projects (all are merged)</h4>
        </div>

        {showWarning ? <>
            <Warning>This panel is currently unreliable when showing data across multiple allocation periods.</Warning>
        </> : null}

        {datapointSum === 0 ? null : <div className="pie-wrapper">
            <PieChart dataPoints={dataPoints} valueFormatter={formatter} onDataPointSelection={dataPoint => setSingleChartSelected(existingChart => {
                const newlySelectedChart = dataPoint.key;
                return newlySelectedChart === existingChart ? undefined : newlySelectedChart;
            })} />
        </div>}
        {/* Note(Jonas): this is here, otherwise <tbody> y-overflow will not be respected */}
        {dataPoints.length === 0 ? "No usage data found" :
            <div style={{overflowY: "scroll"}}>
                <table>
                    <thead>
                        <tr>
                            <th>Project</th>
                            <th>Name/Provider</th>
                            <th>Usage</th>
                        </tr>
                    </thead>
                    <tbody>
                        {dataPoints.filter(it => singleChartSelected ? it.key === singleChartSelected : true).map((point, idx) => {
                            const usage = point.value;
                            return <tr key={idx}>
                                <td>{point.key}</td>
                                <td>{point.nameAndProvider}</td>
                                <td>{Accounting.addThousandSeparators(Math.round(usage))} {unit}</td>
                            </tr>
                        })}
                    </tbody>
                </table>
            </div>
        }
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
    return <div className={classConcat(CardClass, PanelClass, MostUsedApplicationsStyle)}>
        <div className="panel-title">
            <h4>Most used applications</h4>
        </div>

        {data === undefined || data.dataPoints.length === 0 ? "No usage data found" :
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
        }
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
    return <div className={classConcat(CardClass, PanelClass, JobSubmissionStyle)}>
        <div className="panel-title">
            <h4>When are your jobs being submitted?</h4>
        </div>

        {data == null || data.dataPoints.length === 0 ? "No job data found" :
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
                        {data.dataPoints.map((dp, i) => {
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
        }
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
    chart: ChartProps,
    aspectRatio: [number, number],
    maxWidth?: number,
    anyChartData: boolean,
}> = ({chart, aspectRatio, maxWidth, ...props}) => {
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

            setDimensions({width: `${width}px`, height: `${Math.max(height, 250)}px`});
        }, 100);
    }, [props.anyChartData, dimensions]);

    return <div
        style={dimensions.height ? {...dimensions, width: "100%", display: "flex", justifyContent: "center"} : styleForLayoutTest}
        ref={mountPoint}
    >
        {dimensions.height && dimensions.width &&
            <Chart
                {...chart}
                height={dimensions.height}
                width={dimensions.width}
            />
        }
    </div>;
}

const UsageOverTimePanel: React.FunctionComponent<{charts: UsageChart[]}> = ({charts}) => {
    const chartCounter = useRef(0); // looks like apex charts has a rendering bug if the component isn't completely thrown out
    const [shownEntries, setShownEntries] = useState<boolean[]>([]);
    const chartProps: ChartProps = useMemo(() => {
        chartCounter.current++;
        setShownEntries(charts.map(() => true));
        return usageChartsToChart(charts, {
            valueFormatter: val => Accounting.addThousandSeparators(val.toFixed(1)),
            toggleShown: index => {
                setShownEntries(shownEntries => {
                    shownEntries[index] = !shownEntries[index];
                    return [...shownEntries];
                });
            }
        });
    }, [charts]);

    const showWarning = (() => {
        for (const chart of charts) {
            if (charts.every(chart => chart.dataPoints.length == 0)) {return false}
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
        }
        return false;
    })();

    // HACK(Jonas): Self-explanatory hack
    const anyData = (chartProps.series?.[0] as any)?.data.length > 0;

    return <div className={classConcat(CardClass, PanelClass, UsageOverTimeStyle)}>
        <div className="panel-title">
            <h4>Usage over time</h4>
        </div>

        <>
            <DynamicallySizedChart anyChartData={anyData} chart={chartProps} aspectRatio={ASPECT_RATIO_LINE_CHART} />

            {showWarning && <>
                <Warning>
                    It looks like the graph is showing data from multiple allocations.
                    Fluctuations in usage normally indicate that an allocation has expired.
                </Warning>
            </>}

            <DifferenceTable charts={charts} shownEntries={shownEntries} />
        </>
    </div>;
};

function DifferenceTable({charts, shownEntries}: {charts: UsageChart[]; shownEntries: boolean[]}) {
    const differences = React.useMemo(() => {
        const differences = charts.map(() => 0.0);
        for (const [chartIdx, chart] of charts.entries()) {
            for (let idx = 0; idx < chart.dataPoints.length; idx++) {
                const point = chart.dataPoints[idx];
                if (idx == 0) continue;
                const change = point.usage - chart.dataPoints[idx - 1].usage;
                differences[chartIdx] += change;
            }
        }
        return differences;
    }, [charts]);


    return <div className="table-wrapper">
        <table>
            <thead>
                <tr>
                    <th>Name</th>
                    <th>Timestamp</th>
                    <th>Usage</th>
                    <th>Change</th>
                </tr>
            </thead>
            <tbody>
                {charts.map((chart, idx) => !shownEntries[idx] ? null :
                    differences[idx] != 0 ? chart.dataPoints.map((point, idx, items) => {
                        const change = (idx + 1 !== items.length) ? point.usage - items[idx + 1].usage : 0;
                        if (change === 0 && idx + 1 < items.length) return null;
                        return <tr key={idx}>
                            <td>{chart.name}</td>
                            <td>{dateToString(point.timestamp)}</td>
                            <td>{Accounting.addThousandSeparators(point.usage.toFixed(2))}</td>
                            <td>{change >= 0 ? "+" : ""}{Accounting.addThousandSeparators(change.toFixed(2))}</td>
                        </tr>;
                    }) : (chart.dataPoints.length === 0 ? null : <tr key={chart.name}>
                        <td>{chart.name}</td>
                        <td>{dateToString(chart.dataPoints[0].timestamp)}</td>
                        <td>{Accounting.addThousandSeparators(chart.dataPoints[0].usage.toFixed(0))}</td>
                        <td>N/A</td>
                    </tr>)
                )}
            </tbody>
        </table>
    </div>
}

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
            <PieChart dataPoints={dataPoints} valueFormatter={formatter} onDataPointSelection={() => {}} />

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

const PieChart: React.FunctionComponent<{
    dataPoints: {key: string, value: number}[],
    valueFormatter: (value: number) => string,
    onDataPointSelection: (dataPointIndex: {key: string; value: number;}) => void;
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

    // FIXME(Jonas): The list is at most 5 entries so it's not a big deal, but it can be done in one iteration instead of two.
    const series = useMemo(() => filteredList.map(it => it.value), [filteredList]);
    const labels = useMemo(() => filteredList.map(it => it.key), [filteredList]);

    const chartProps: ChartProps = useMemo(() => {
        return {
            type: "pie",
            series,
            selection: {enabled: true},
            options: {
                chart: {
                    animations: {
                        enabled: false,
                    },
                    events: {
                        dataPointSelection: (e: any, chart?: any, options?: any) => {
                            const dataPointIndex = options?.dataPointIndex

                            if (dataPointIndex != null) {
                                props.onDataPointSelection(filteredList[dataPointIndex]);
                            }
                        }
                    },
                },
                legend: {
                    onItemClick: {toggleDataSeries: false}, /* Note(Jonas): I'm not sure we can expect same behaviour from this, as clicking on the pie, so disable */
                },
                labels,
                dataLabels: {
                    enabled: false,
                },
                stroke: {
                    show: false,
                },
                tooltip: {
                    shared: false,
                    y: {
                        formatter: function (val: number) {
                            return props.valueFormatter(val);
                        }
                    }
                },
            }
        } as ChartProps;
    }, [series]);

    return <DynamicallySizedChart anyChartData={filteredList.length > 0} chart={chartProps} aspectRatio={ASPECT_RATIO_PIE_CHART} maxWidth={350} />;
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
    nameAndProvider: string;
}

const emptyBreakdownChart: BreakdownChart = {
    unit: "",
    nameAndProvider: "",
    dataPoints: [],
};

interface UsageChart {
    unit: string;
    name: string;
    dataPoints: {timestamp: number, usage: number, quota: number}[];
}

const emptyChart: UsageChart = {
    unit: "",
    dataPoints: [],
    name: "",
};

function toSeriesChart(chart: UsageChart): ApexAxisChartSeries[0] {
    let data = chart.dataPoints.map(it => [it.timestamp, it.usage]);
    if (data.length === 0) {
        const now = timestampUnixMs();
        data = [[now - 1000 * 60 * 60 * 24 * 7, 0], [now, 0]];
    }
    return {
        data,
        name: chart.name,
    }
}

function usageChartsToChart(
    charts: UsageChart[],
    options: {
        valueFormatter?: (value: number) => string;
        removeDetails?: boolean;
        toggleShown?: (index: number) => void;
    } = {}
): ChartProps {
    const result: ChartProps = {};
    result.series = charts.map(it => toSeriesChart(it));
    result.type = "area";
    result.options = {
        legend: {
            position: "right",
            offsetY: 50,
            onItemClick: {
                toggleDataSeries: true,
            }
        },
        chart: {
            events: {
                legendClick(_, seriesIndex, __) {
                    if (seriesIndex != null) options.toggleShown?.(seriesIndex);
                },
            },
            type: "area",
            stacked: true,
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
        // fill: {
        //     type: "gradient",
        //     gradient: {
        //         shadeIntensity: 1,
        //         inverseColors: false,
        //         opacityFrom: 0.4,
        //         opacityTo: 0,
        //         stops: [0, 90, 100]
        //     }
        // },
        //colors: ['var(--primaryMain)'],
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
                    res += charts[0]?.unit ?? "";
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
                        res += charts[0]?.unit;
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

const RenderProductSelector: RichSelectChildComponent<State["summaries"][0]> = ({element, onSelect, dataProps}) => {
    const chartKey = useRef(0);
    const chartProps = useMemo(() => {
        if (element === undefined) return undefined;
        chartKey.current++;
        return usageChartsToChart([element.chart], {removeDetails: true});
    }, [element]);

    if (element === undefined) {
        return <Flex height={40} alignItems={"center"} pl={12}>No script selected</Flex>
    }

    const s = element;
    return <Flex gap={"16px"} {...dataProps} alignItems={"center"} py={4} px={8} mr={48} onClick={onSelect}>
        <Chart
            key={chartKey.current.toString()}
            {...chartProps}
            width={32}
            height={32}
        />
        <ProviderLogo providerId={element.category.provider} size={32} />
        <div><b>{element.category.name}</b></div>
        <Box flexGrow={1} />
        <div>{usageToString(s.category, s.usage, s.quota, false)}</div>
        <div>({usageToString(s.category, s.usage, s.quota, true)})</div>
    </Flex>;
}

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
        return usageChartsToChart([props.chart], {removeDetails: true});
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
        max-height: 1000px;

        
        display: flex;
        flex-direction: column;
    }

    ${k} .panel-title {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin-bottom: 10px;
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
    const {start, end} = normalizePeriod(props.value);

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
                <div style={{width: "182px"}}>{periodToString(props.value)}</div>
                <Icon name="chevronDownLight" size="14px" ml="4px" mt="4px" />
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

function normalizePeriod(period: Period): {start: number, end: number} {
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
            return {start: start.getTime(), end};
        }

        case "absolute": {
            return {start: period.start, end: period.end};
        }
    }
}

// Styling
// =====================================================================================================================
const AccountingPanelsOnlyStyle = injectStyle("accounting-panels-only", () => "");
const VisualizationStyle = injectStyle("visualization", k => `
    ${k} > header {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin-bottom: 16px;
    }

    ${k} header.at-top {
        z-index: 12; /* Note(Jonas): Otherwise, the header is behind the apex-chart buttons */
        box-shadow: unset;
    }
    
    ${k} > header h2 {
        font-size: 20px;
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
        display: flex;
        flex-direction: column;
        gap: 16px;
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
    remoteData: {requestsInFlight: 0, initialLoadDone: false},
    summaries: [],
    selectedPeriod: {
        type: "relative",
        distance: 7,
        unit: "day"
    },
    availableUnits: []
};

export default Visualization;
