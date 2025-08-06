import * as React from "react";
import * as Accounting from ".";
import Chart, {Props as ChartProps} from "react-apexcharts";
import ApexCharts from "apexcharts";
import {classConcat, injectStyle, makeClassName} from "@/Unstyled";
import {Flex, Icon, Input, Text, MainContainer, Box, Truncate, Button, Label} from "@/ui-components";
import {CardClass} from "@/ui-components/Card";
import {ProjectSwitcher, projectTitle} from "@/Project/ProjectSwitcher";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {dateToString} from "@/Utilities/DateUtilities";
import {CSSProperties, useCallback, useEffect, useLayoutEffect, useMemo, useReducer, useRef, useState} from "react";
import {BreakdownByProjectAPI, categoryComparator, ChartsAPI, UsageOverTimeAPI} from ".";
import {TooltipV2} from "@/ui-components/Tooltip";
import {threadDeferLike, stopPropagation, timestampUnixMs, displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI, noopCall} from "@/Authentication/DataHook";
import * as Jobs from "@/Applications/Jobs";
import * as Config from "../../site.config.json";
import {useProjectId} from "@/Project/Api";
import {differenceInCalendarDays, formatDate, formatDistance} from "date-fns";
import {GradientWithPolygons} from "@/ui-components/GradientBackground";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {deviceBreakpoint} from "@/ui-components/Hide";
import Warning from "@/ui-components/Warning";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useProject} from "@/Project/cache";
import * as Heading from "@/ui-components/Heading";
import {RichSelect, RichSelectChildComponent} from "@/ui-components/RichSelect";
import {Feature, hasFeature} from "@/Features";
import {IconName} from "@/ui-components/Icon";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {groupBy} from "@/Utilities/CollectionUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {DATE_FORMAT} from "@/Admin/NewsManagement";
import {dialogStore} from "@/Dialog/DialogStore";
import {slimModalStyle} from "@/Utilities/ModalUtilities";
import {Toggle} from "@/ui-components/Toggle";

// Constants
// =====================================================================================================================
const JOBS_UNIT_NAME = "Job statistics";

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
        category: Accounting.ProductCategoryV2,
        currentAllocation: {
            usage: number,
            quota: number,
            expiresAt: number,
        },
        selectedBreakdown: string;
        usageOverTime: UsageChart[];
        breakdownByProject: BreakdownChart[];

        jobUsageByUsers?: JobUsageByUsers,
        mostUsedApplications?: MostUsedApplications,
        submissionStatistics?: SubmissionStatistics,

        activeUnit: string;
        availableUnits: string[];
    },

    selectedPeriod: Period,
}

interface ExportHeader<T> {
    key: keyof T;
    value: string;
    defaultChecked: boolean;
};

function exportUsage<T extends object>(chartData: T[] | undefined, headers: ExportHeader<T>[], projectTitle: string | undefined): void {
    if (!chartData?.length) {
        snackbarStore.addFailure("No data to export found", false);
        return;
    }

    dialogStore.addDialog(<UsageExport chartData={chartData} headers={headers} projectTitle={projectTitle} />, () => {}, true, slimModalStyle);
}

function UsageExport<T extends object>({chartData, headers, projectTitle}: {chartData: T[]; headers: ExportHeader<T>[]; projectTitle?: string}): React.ReactNode {
    const [checked, setChecked] = useState(headers.map(it => it.defaultChecked));

    const startExport = useCallback((format: "json" | "csv") => {
        doExport(chartData, headers.filter((_, idx) => checked[idx]), ';', format, projectTitle);
        dialogStore.success();
    }, [checked]);

    return <Box>
        <h2>Export which usage data rows?</h2>
        <Box mt="12px" mb="36px">
            {headers.map((h, i) =>
                <Label key={h.value} my="4px" style={{display: "flex"}}>
                    <Toggle height={20} checked={checked[i]} onChange={prevValue => {
                        setChecked(ch => {
                            ch[i] = !prevValue
                            return [...ch];
                        })
                    }} />
                    <Text ml="8px">{h.value}</Text>
                </Label>
            )}
        </Box>
        <Flex justifyContent="end" px={"20px"} py={"12px"} margin={"-20px"} background={"var(--dialogToolbar)"} gap={"8px"}>
            <Button onClick={() => dialogStore.failure()} color="errorMain">Cancel</Button>
            <Button onClick={() => startExport("json")} color="successMain">Export JSON</Button>
            <Button onClick={() => startExport("csv")} color="successMain">Export CSV</Button>
        </Flex>
    </Box>

    function doExport<T extends object>(chartData: T[], headers: ExportHeader<T>[], delimiter: string, format: "json" | "csv", projectTitle?: string) {
        let text = "";
        switch (format) {
            case "csv": {

                text = headers.map(it => it.value).join(delimiter) + "\n";

                for (const el of chartData) {
                    for (const [idx, header] of headers.entries()) {
                        text += `"${el[header.key]}"`;
                        if (idx !== headers.length - 1) text += delimiter;
                    }
                    text += "\n";
                }

                break;
            };
            case "json": {
                const h = headers.map(it => it.key);
                const data: T[] = JSON.parse(JSON.stringify(chartData));

                for (const row of data) {
                    for (const k of Object.keys(row)) {
                        if (!h.includes(k as keyof T)) {
                            delete row[k];
                        }
                    }
                }

                text = JSON.stringify(data);
                break;
            }
        }

        const a = document.createElement("a");
        a.href = "data:text/plain;charset=utf-8," + encodeURIComponent(text);
        a.download = `${Config.PRODUCT_NAME} - ${projectTitle ? projectTitle : "personal workspace"} - ${formatDate(new Date(), DATE_FORMAT)}.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
    }
}


type Period =
    | {type: "relative", distance: number, unit: "day" | "month"}
    | {type: "absolute", start: number, end: number}
    ;

// State reducer
// =====================================================================================================================
type UIAction =
    | {type: "LoadCharts", charts: ChartsAPI}
    | {type: "LoadJobStats", statistics: Jobs.JobStatistics}
    | {type: "UpdateSelectedPeriod", period: Period}
    | {type: "UpdateRequestsInFlight", delta: number}
    | {type: "UpdateActiveUnit", unit: string}
    | {type: "SetActiveBreakdown", projectId: string}
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
            return selectUnit(state, action.unit);
        }

        case "SetActiveBreakdown": {
            if (!state.activeDashboard) return state;
            const breakdown = action.projectId === state.activeDashboard.selectedBreakdown ? "" : action.projectId;
            return {...state, activeDashboard: {...state.activeDashboard, selectedBreakdown: breakdown}};
        }

        case "LoadCharts": {
            // TODO Move this into selectChart
            function translateBreakdown(chart: BreakdownByProjectAPI, nameAndProvider: string): BreakdownChart {
                const dataPoints = chart.data;
                return {dataPoints, nameAndProvider};
            }

            function translateChart(category: Accounting.ProductCategoryV2, chart: UsageOverTimeAPI): UsageChart {
                const dataPoints = chart.data;
                return {dataPoints, name: category.name, provider: category.provider, future: chart.future};
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
                summary.breakdownByProject = translateBreakdown(chart.breakdownByProject, summary.title);
            }

            const availableUnits = unitsFromSummaries(newSummaries);
            const [activeUnit] = availableUnits;

            return selectUnit({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    chartData: data,
                },
                summaries: newSummaries,
            }, activeUnit);
        }

        case "LoadJobStats": {
            return selectUnit({
                ...state,
                remoteData: {
                    ...state.remoteData,
                    jobStatistics: action.statistics,
                },
            }, state.activeDashboard?.activeUnit ?? JOBS_UNIT_NAME);
        }

        case "UpdateSelectedPeriod": {
            return {
                ...state,
                selectedPeriod: action.period
            };
        }
    }

    function selectUnit(state: State, unit: string): State {
        const chartData = state.remoteData.chartData;
        if (!chartData) return {...state, activeDashboard: undefined};
        const summaries = state.summaries.filter(it => unit === JOBS_UNIT_NAME || Accounting.explainUnit(it.category).name === unit);
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
                        // What is the Number-boxing here for?
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

        const availableUnitsSet = unitsFromSummaries(state.summaries);
        if (state.remoteData.jobStatistics) {
            availableUnitsSet.push(JOBS_UNIT_NAME);
        }

        const availableUnits = [...availableUnitsSet];

        return {
            ...state,
            activeDashboard: {
                category: summaries[0].category,
                /* TODO: Remove? Hidden behind flag */
                currentAllocation: {
                    usage: summaries[0].usage,
                    quota: summaries[0].quota,
                    expiresAt: earliestExpiration ?? timestampUnixMs(),
                },
                selectedBreakdown: "",
                usageOverTime: summaries.map(it => it.chart),
                breakdownByProject: summaries.map(it => it.breakdownByProject),
                jobUsageByUsers,
                mostUsedApplications,
                submissionStatistics,
                availableUnits: availableUnits,
                activeUnit: unit
            },
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
            }).catch(e => displayErrorMessageOrDefault(e, "Failed to fetch job statistics."));

            invokeAPI(Accounting.retrieveChartsV2({start, end})).then(charts => {
                dispatch({type: "LoadCharts", charts});
            }).catch(e => displayErrorMessageOrDefault(e, "Failed to fetch charts."));
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
function Visualization(): React.ReactNode {
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
        const wrappers = document.querySelectorAll(`.${VisualizationStyle} ${TableWrapper.dot}`);
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

    const setPeriod = useCallback((period: Period) => {
        dispatchEvent({type: "UpdateSelectedPeriod", period});
    }, [dispatchEvent]);

    const setActiveUnit = useCallback((unit: string) => {
        dispatchEvent({type: "UpdateActiveUnit", unit});
    }, [dispatchEvent]);

    const setBreakdown = useCallback((projectId: string) => {
        dispatchEvent({type: "SetActiveBreakdown", projectId});
    }, [state.activeDashboard?.selectedBreakdown]);

    const unitsForRichSelect = React.useMemo(() => {
        const all = state.activeDashboard?.availableUnits.map(it => ({unit: it})) ?? [];
        return all.filter(it => !["IPs", "Licenses"].includes(it.unit)); // Note(Jonas): Maybe just don't fetch the IPs, Licenses (and Links?)
    }, [state.activeDashboard?.availableUnits]);

    // Short-hands
    // -----------------------------------------------------------------------------------------------------------------
    const activeCategory = state.activeDashboard?.category;
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
        main={<div className={VisualizationStyle}>
            <header>
                <h3 className="title" style={{marginTop: "auto", marginBottom: "auto"}}>Resource usage</h3>
                <div className="duration-select">
                    <PeriodSelector value={state.selectedPeriod} onChange={setPeriod} />
                </div>
                <div style={{flexGrow: "1"}} />
                <ProjectSwitcher />
            </header>

            <div style={{padding: "13px 16px 16px 16px", zIndex: -1}}>
                {!state.remoteData.chartData && !state.remoteData.jobStatistics && state.remoteData.requestsInFlight || isAnyLoading ? <>
                    <HexSpin size={64} />
                </> : null}

                {hasNoMeaningfulData ? <NoData loading={isAnyLoading} productType={activeCategory?.productType} /> : null}

                {!hasFeature(Feature.ALTERNATIVE_USAGE_SELECTOR) ? null : <Box pb={32}>
                    {isAnyLoading ? null :
                        state.activeDashboard == null ? null : unitsForRichSelect.length === 0 ? <div>No data available</div> : <>
                            <RichSelect
                                items={unitsForRichSelect}
                                keys={["unit"]}
                                RenderRow={RenderUnitSelector}
                                RenderSelected={RenderUnitSelector}
                                onSelect={el => setActiveUnit(el.unit)}
                                fullWidth
                                selected={({unit: state.activeDashboard.activeUnit})}
                            />
                        </>}
                </Box>}

                {state.activeDashboard ?
                    <div className={PanelGrid.class}>
                        {state.activeDashboard.activeUnit === JOBS_UNIT_NAME ? <>
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
                            <UsageBreakdownPanel
                                setBreakdown={setBreakdown}
                                selectedBreakdown={state.activeDashboard.selectedBreakdown}
                                isLoading={isAnyLoading}
                                unit={state.activeDashboard.activeUnit}
                                period={state.selectedPeriod}
                                charts={state.activeDashboard.breakdownByProject} />
                            <UsageOverTimePanel
                                unit={state.activeDashboard.activeUnit}
                                period={state.selectedPeriod}
                                isLoading={isAnyLoading}
                                charts={state.activeDashboard.usageOverTime}
                            />
                        </>}
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

const PieWrapper = makeClassName("pie-wrapper");
const BreakdownStyle = injectStyle("breakdown", k => `
    ${k} ${PieWrapper.dot} {
        width: 50%;
        height: 100%;
        display: flex;
        margin-top: auto;
        margin-bottom: auto;
    }

@media screen and (max-width: 1337px) {
    ${k} ${PieWrapper.dot} {
        height: 50%;
        width: 50%;
        max-width: 500px;
        margin-left: auto;
        margin-right: auto;
    }    
}

    ${k} table tbody tr > td:nth-child(2),
    ${k} table tbody tr > td:nth-child(3) {
        text-align: right;
        font-family: var(--monospace);
    }
`);

function mergedCharts(unit: string, charts: BreakdownChart[]): {
    key: string;
    value: number;
    nameAndProvider: string;
}[] {
    const mergedCharts = {
        unit: unit,
        dataPoints: charts.flatMap(it => it.dataPoints.map(d => ({...d, nameAndProvider: it.nameAndProvider})))
    };

    return mergedCharts.dataPoints
        .map(it => ({key: it.title, value: it.usage, nameAndProvider: it.nameAndProvider}))
        .sort((a, b) => a.value - b.value)
}

const UsageBreakdownChartId = "UsageBreakdown";
const UsageBreakdownPanel: React.FunctionComponent<{
    setBreakdown(projectId: string): void;
    selectedBreakdown: string;
    isLoading: boolean;
    unit: string;
    period: Period;
    charts: BreakdownChart[];
}> = props => {
    const {selectedBreakdown} = props;

    const fullyMergedChart = React.useMemo(() => ({
        unit: props.unit,
        dataPoints: props.charts.flatMap(it => it.dataPoints.map(d => ({...d, nameAndProvider: it.nameAndProvider})))
    }), [props.charts, props.unit]);

    const dataPoints = useMemo(() => {
        const unsorted = fullyMergedChart.dataPoints.map(it => ({key: it.title, value: it.usage, nameAndProvider: it.nameAndProvider})) ?? [];
        return unsorted.sort((a, b) => a.value - b.value);
    }, [props.charts]);

    const dataPointsByProject = React.useMemo(() => {
        const summed = groupBy(dataPoints, el => el.key);

        return Object.keys(summed).map(it => {
            return {
                key: it,
                value: summed[it].reduce((acc, it) => it.value + acc, 0),
                nameAndProvider: ""
            }
        }).sort((a, b) => a.value - b.value);
    }, [dataPoints]);

    const formatter = useCallback((val: number) => Accounting.addThousandSeparators(val.toFixed(0)) + " " + props.unit, [props.unit]);

    const showWarning = (() => {
        const {start, end} = normalizePeriod(props.period);
        const startDate = new Date(start);
        const endDate = new Date(end);
        return startDate.getUTCFullYear() !== endDate.getUTCFullYear();
    })();

    const filteredDataPoints = useMemo(() => {
        if (!selectedBreakdown) return dataPointsByProject.slice();
        return dataPoints.filter(it => it.key === selectedBreakdown);
    }, [selectedBreakdown, dataPoints, dataPointsByProject]);

    const datapointSum = useMemo(() => dataPoints.reduce((a, b) => a + b.value, 0), [dataPoints]);

    const sorted = useSorting(filteredDataPoints, "value", "asc", true);

    const updateSelectedBreakdown = React.useCallback((dataPoint: {key: string}) => {
        props.setBreakdown(dataPoint.key);
    }, []);

    const project = useProject().fetch();

    const startExport = useCallback(() => {

        const points = selectedBreakdown ?
            fullyMergedChart.dataPoints.filter(it => it.title === selectedBreakdown) :
            fullyMergedChart.dataPoints;

        const datapoints = points.map(it => {
            const [product, provider] = it.nameAndProvider.split("/");
            return {
                product,
                provider,
                projectId: it.projectId,
                title: it.title,
                usage: it.usage,
            };
        });

        exportUsage(datapoints, [
            header("title", "Project", true),
            header("product", "Product", true),
            header("provider", "Provider", true),
            header("usage", "Usage (" + props.unit + ")", true),
            header("projectId", "Project ID", false)
        ], projectTitle(project));

    }, [fullyMergedChart, project, selectedBreakdown]);

    if (props.isLoading) return null;

    return <div className={classConcat(CardClass, PanelClass, BreakdownStyle)}>
        <div className={PanelTitle.class}>
            <div>Usage breakdown by sub-projects </div>
            <TooltipV2 tooltip={"Click on a project name to view more detailed usage"}>
                <Icon name={"heroQuestionMarkCircle"} />
            </TooltipV2>
            <Button onClick={startExport}>
                Export
            </Button>
        </div>

        {showWarning ? <>
            <Warning>This panel is currently unreliable when showing data across multiple allocation periods.</Warning>
        </> : null}

        <div className={ChartAndTable}>
            {datapointSum === 0 ? null : <div key={UsageBreakdownChartId} className={PieWrapper.class}>
                <PieChart chartId={UsageBreakdownChartId} dataPoints={dataPoints} valueFormatter={formatter} onDataPointSelection={updateSelectedBreakdown} />
            </div>}
            {/* Note(Jonas): this is here, otherwise <tbody> y-overflow will not be respected */}
            {dataPoints.length === 0 ? "No usage data found" :
                <div className={TableWrapper.class}>
                    <table>
                        <thead>
                            <tr>
                                <SortTableHeader width="30%" sortKey={"key"} sorted={sorted}>Project</SortTableHeader>
                                {selectedBreakdown ? <SortTableHeader width="40%" sortKey={"nameAndProvider"} sorted={sorted}>Name - Provider</SortTableHeader> : null}
                                <SortTableHeader width="30%" sortKey={"value"} sorted={sorted}>Usage</SortTableHeader>
                            </tr>
                        </thead>
                        <tbody>
                            {sorted.data.map((point, idx) => {
                                const usage = point.value;
                                const [name, provider] = point.nameAndProvider ? point.nameAndProvider.split("/") : ["", ""];
                                return <tr key={idx} style={{cursor: "pointer"}}>
                                    <td title={point.key} onClick={() => {
                                        const chart = ApexCharts.getChartByID(UsageBreakdownChartId);
                                        const labels: string[] = chart?.["opts"]["labels"] ?? [];
                                        const idx = labels.findIndex(it => it === point.key);
                                        if (idx !== -1 && chart) {
                                            chart.toggleDataPointSelection(idx);
                                        }
                                    }}><Truncate maxWidth={"250px"}>{point.key}</Truncate></td>
                                    {name && provider ? <td>{name} - {getShortProviderTitle(provider)}</td> : null}
                                    <td>{Accounting.addThousandSeparators(Math.round(usage))} {props.unit}</td>
                                </tr>
                            })}
                        </tbody>
                    </table>
                </div>
            }
        </div>
    </div>;
};

function header<T>(key: keyof T, value: string, defaultChecked?: boolean): ExportHeader<T> {
    return {key, value, defaultChecked: !!defaultChecked};
}


const MostUsedApplicationsStyle = injectStyle("most-used-applications", k => `
    ${k} table tr > td:nth-child(2),
    ${k} table tr > td:nth-child(3) {
        font-family: var(--monospace);
        text-align: right;
    }
`);

function thStyling(isBold: boolean, width: string): CSSProperties | undefined {
    return {
        fontWeight: isBold ? "bold" : undefined,
        width,
        cursor: "pointer",
    };
}

type PercentageWidth = `${number}%` | `${number}px`;
function SortTableHeader<DataType>({sortKey, sorted, children, width}: React.PropsWithChildren<{
    sortKey: keyof DataType; sorted: ReturnType<typeof useSorting<DataType>>; width: PercentageWidth;
}>) {
    const isActive = sortKey === sorted.sortByKey;
    return <th style={thStyling(isActive, width)} onClick={() => sorted.doSortBy(sortKey)}>
        <Flex>{children} {isActive ? <Icon ml="auto" my="auto" name="chevronDownLight" rotation={sorted.sortOrder === "asc" ? 180 : 0} /> : null}</Flex>
    </th>
}

type SortOrder = "asc" | "desc";
function useSorting<DataType>(originalData: DataType[], sortByKey: keyof DataType, initialSortOrder?: SortOrder, sortOnDataChange?: boolean): {
    data: DataType[];
    sortOrder: SortOrder;
    sortByKey: typeof sortByKey;
    doSortBy(key: keyof DataType): void;
} {
    const [_data, setData] = useState(originalData);
    const [_sortByKey, setSortByKey] = useState(sortByKey)
    const [_sortOrder, setSortOrder] = useState(initialSortOrder ?? "asc");

    React.useEffect(() => {
        if (sortOnDataChange) doSortBy(originalData, sortByKey, initialSortOrder);
        else setData(originalData);
    }, [originalData]);

    const doSortBy = React.useCallback((data: DataType[], sortBy: keyof DataType, sortOrder?: SortOrder) => {
        const newSortOrder = sortOrder ?? (_sortByKey === sortBy ? (_sortOrder === "asc" ? "desc" : "asc") : _sortOrder);
        if (data.length === 0) return;
        const type = typeof data[0][sortBy];
        switch (type) {
            case "string": {
                if (newSortOrder === "asc") {
                    data.sort((a, b) => (a[sortBy] as string).localeCompare(b[sortBy] as string));
                } else {
                    data.sort((a, b) => (b[sortBy] as string).localeCompare(a[sortBy] as string));
                }
                break;
            }
            case "number": {
                if (newSortOrder === "asc") {
                    data.sort((a, b) => (a[sortBy] as number) - (b[sortBy] as number));
                } else {
                    data.sort((a, b) => (b[sortBy] as number) - (a[sortBy] as number));
                }
                break;
            }
            default: {
                console.warn("Unhandled type in `useSorting`:", type);
                break;
            }
        }
        setData(data);
        setSortOrder(newSortOrder);
        setSortByKey(sortBy);
    }, [_sortByKey, _sortOrder]);

    return {data: _data, sortOrder: _sortOrder, doSortBy: sortBy => doSortBy(_data, sortBy), sortByKey: _sortByKey};

}

const MostUsedApplicationsPanel: React.FunctionComponent<{data?: MostUsedApplications}> = ({data}) => {
    const sorted = useSorting(data?.dataPoints ?? [], "count", "desc");

    const project = useProject().fetch()

    const startExport = useCallback(() => {
        exportUsage(
            data?.dataPoints,
            [header("applicationTitle", "Application", true), header("count", "Count", true)],
            projectTitle(project)
        );
    }, [data?.dataPoints, project]);

    return <div className={classConcat(CardClass, PanelClass, MostUsedApplicationsStyle)}>
        <Flex>
            <div className="panel-title">
                <h4>Most used applications</h4>
            </div>
            <Button ml="auto" onClick={startExport}>
                Export
            </Button>
        </Flex>

        {sorted.data === undefined || sorted.data.length === 0 ? "No usage data found" :
            <div className={TableWrapper.class}>
                <table>
                    <thead>
                        <tr>
                            <SortTableHeader width="70%" sortKey="applicationTitle" sorted={sorted}>Application</SortTableHeader>
                            <SortTableHeader width="30%" sortKey="count" sorted={sorted}>Number of jobs</SortTableHeader>
                        </tr>
                    </thead>
                    <tbody>
                        {sorted.data.map(it =>
                            <tr key={it.applicationTitle}>
                                <td>{it.applicationTitle}</td>
                                <td>{it.count}</td>
                            </tr>
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
    const sorted = useSorting(data?.dataPoints ?? [], "day");
    const project = useProject().fetch();

    const startExport = useCallback(() => {
        exportUsage(data?.dataPoints.map(it => ({...it, day: dayNames[it.day], hourOfDayStart: formatHours(it.hourOfDayStart, it.hourOfDayEnd)})), [
            header("day", "Day", true),
            header("hourOfDayStart", "Time of day", true),
            header("numberOfJobs", "Count", true),
            header("averageDurationInSeconds", "Avg duration", true),
            header("averageQueueInSeconds", "Avg queue", true),
        ], projectTitle(project))
    }, [data?.dataPoints, project]);

    return <div className={classConcat(CardClass, PanelClass, JobSubmissionStyle)}>
        <Flex>
            <div className="panel-title">
                <h4>When are your jobs being submitted?</h4>
            </div>
            <Button ml="auto" onClick={startExport}>
                Export
            </Button>
        </Flex>

        {sorted.data == null || sorted.data.length === 0 ? "No job data found" :
            <div className={TableWrapper.class}>
                <table>
                    <thead>
                        <tr>
                            <SortTableHeader width="20%" sorted={sorted} sortKey={"day"}>Day</SortTableHeader>
                            <SortTableHeader width="20%" sorted={sorted} sortKey={"hourOfDayStart"}>Time of day</SortTableHeader>
                            <SortTableHeader width="20%" sorted={sorted} sortKey={"numberOfJobs"}>Count</SortTableHeader>
                            <SortTableHeader width="20%" sorted={sorted} sortKey={"averageDurationInSeconds"}>Avg duration</SortTableHeader>
                            <SortTableHeader width="20%" sorted={sorted} sortKey={"averageQueueInSeconds"}>Avg queue</SortTableHeader>
                        </tr>
                    </thead>
                    <tbody>
                        {sorted.data.map((dp, i) => {
                            const day = dayNames[dp.day];
                            return <tr key={i}>
                                <td>{day}</td>
                                <td>{formatHours(dp.hourOfDayStart, dp.hourOfDayEnd)}</td>
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

    function formatHours(start: number, end: number) {
        return `${start.toString().padStart(2, '0')}:00-${end.toString().padStart(2, '0')}:00`;
    }
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
    maxWidth?: number,
}> = ({chart, maxWidth, ...props}) => {
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

    const styleForLayoutTest: CSSProperties = {flexGrow: 2, flexShrink: 1, flexBasis: "400px"};

    return <div style={styleForLayoutTest}>
        <Chart {...chart} height="400px" {...props} />
    </div>;
}

const UsageOverTimePanel: React.FunctionComponent<{
    charts: UsageChart[];
    isLoading: boolean;
    unit: string;
    period: Period;
}> = ({charts, isLoading, ...props}) => {

    const chartCounter = useRef(0); // looks like apex charts has a rendering bug if the component isn't completely thrown out
    const [chartEntries, updateChartEntries] = useState<{name: string; shown: boolean;}[]>([]);
    const ChartID = "UsageOverTime";

    const exportRef = React.useRef(noopCall);

    // HACK(Jonas): Used to change contents of table, based on what series are active in the chart
    const shownRef = React.useRef(chartEntries);
    React.useEffect(() => {
        shownRef.current = chartEntries;
    }, [chartEntries]);

    const toggleShownEntries = React.useCallback((value: boolean | string[]) => {
        updateChartEntries(entries => {
            if (typeof value === "boolean") {
                return [...entries.map(it => ({name: it.name, shown: value}))];
            } else {
                for (const entry of entries) {
                    if (value.includes(entry.name)) {
                        entry.shown = !entry.shown;
                    }
                }
                return [...entries];
            }
        });
    }, [charts]);

    const [showingQuota, setShowingQuota] = React.useState(false);

    const chartProps: ChartProps = useMemo(() => {
        chartCounter.current++;
        updateChartEntries(charts.map(it => ({name: it.name, shown: true})));
        setShowingQuota(false);
        const maxPeriodInDays = maxDaysDifference(charts);
        return usageChartsToChart(charts, shownRef, {
            valueFormatter: val => Accounting.addThousandSeparators(val.toFixed(1)),
            toggleShown: value => toggleShownEntries(value),
            id: ChartID + chartCounter.current,
        }, maxPeriodInDays);
    }, [charts, props.period, props.unit]);

    const toggleQuotaShown = React.useCallback((active: boolean) => {
        const chart = ApexCharts.getChartByID(ChartID + chartCounter.current);
        if (!chart) return;
        setShowingQuota(active);
        if (active) {
            updateChartEntries(c => {
                for (const quotaSeries of charts.map(it => quotaSeriesFromDataPoints(it))) {
                    /* Note(Jonas): Casting seems necessary as the parameter requires something different from what is actually used in the library code */
                    chart.appendSeries(quotaSeries as unknown as ApexAxisChartSeries);
                    c.push({name: quotaSeries.name!, shown: true});
                }
                return [...c];
            });
        } else {
            chart.updateSeries(chartProps.series!);
            const chartNames = charts.map(it => ({name: it.name, shown: true}));
            updateChartEntries(chartNames);
        }
    }, [charts, chartProps]);

    // HACK(Jonas): Self-explanatory hack
    const anyData = chartProps.series?.find(it => it["data"]["length"]) != null;

    if (isLoading) return null;

    return <div className={classConcat(CardClass, PanelClass, UsageOverTimeStyle)}>
        <Flex>
            <div className="panel-title">
                <Box>
                    <div>Usage over time</div>
                    <Label style={{display: "flex", marginTop: "6px", fontSize: "14px", gap: "8px"}} width="250px">
                        <Toggle checked={showingQuota} height={20} onChange={(prevValue: boolean) => toggleQuotaShown(!prevValue)} />
                        With quotas
                    </Label>
                </Box>
            </div>
            <Button ml="auto" onClick={() => exportRef.current()}>Export</Button>
        </Flex>

        {!anyData ? <Text>No usage data found</Text> : (
            <div className={ChartAndTable}>
                <DynamicallySizedChart key={ChartID + chartCounter.current} chart={chartProps} />
                <DifferenceTable chartId={ChartID + chartCounter.current} unit={props.unit} charts={charts} updateShownEntries={toggleShownEntries} chartEntries={chartEntries} exportRef={exportRef} />
            </div>
        )}
    </div >;
};

function maxDaysDifference(charts: UsageChart[]) {
    let maxDateRange = 0;
    for (const chart of charts) {
        const minDate = chart.dataPoints.reduce((lowestDate, d) => Math.min(lowestDate, d.timestamp), 999999999999999);
        const maxDate = chart.dataPoints.reduce((highestDate, d) => Math.max(highestDate, d.timestamp), 0);
        maxDateRange = Math.max(differenceInCalendarDays(maxDate, minDate), maxDateRange);
    }
    return maxDateRange === 0 ? 0 : maxDateRange + 1;
}

function DifferenceTable({charts, chartEntries, exportRef, chartId, updateShownEntries, ...props}: {
    updateShownEntries: (args: boolean | string[]) => void;
    charts: UsageChart[];
    chartEntries: {name: string; shown: boolean;}[];
    exportRef: React.RefObject<() => void>;
    chartId: string;
    unit: string;
}) {
    /* TODO(Jonas): Provider _should_ also be here, right? */
    const shownProducts = React.useMemo(() => {
        return charts.filter(chart => {
            return chartEntries.find(it => it.name === chart.name)?.shown;
        })
    }, [charts, chartEntries]);

    const tableContent = React.useMemo(() => {
        const result: {name: string; timestamp: number; usage: number; quota: number}[] = [];
        for (const chart of shownProducts) {
            for (let idx = 0; idx < chart.dataPoints.length; idx++) {
                const point = chart.dataPoints[idx];
                result.push({name: chart.name, timestamp: point.timestamp, usage: point.usage, quota: point.quota});
            }
        }
        return result;
    }, [shownProducts]);

    const project = useProject().fetch();
    const startExport = useCallback(() => {
        exportUsage(tableContent.map(it => ({...it, timestamp: formatDate(it.timestamp, DATE_FORMAT)})), [
            header("name", "Name", true),
            header("timestamp", "Time", true),
            header("usage", "Usage", true),
            header("quota", "Quota", true),
        ], projectTitle(project));
    }, [tableContent, project]);

    React.useEffect(() => {
        exportRef.current = startExport;
    }, [startExport]);

    const toggleChart = React.useCallback((productName: string) => {
        const chart = ApexCharts.getChartByID(chartId);
        toggleSeriesEntry(chart, productName, {current: chartEntries}, updateShownEntries);
    }, [chartId, charts, shownProducts]);

    const sorted = useSorting(tableContent, "usage", "desc");

    const shownProductNames = shownProducts.map(it => it.name);

    return <div className={TableWrapper.class}>
        <table>
            <thead>
                <tr>
                    <SortTableHeader width="30%" sortKey="name" sorted={sorted}>Name</SortTableHeader>
                    <SortTableHeader width="160px" sortKey="timestamp" sorted={sorted}>Timestamp</SortTableHeader>
                    <SortTableHeader width="40%" sortKey="usage" sorted={sorted}>Usage / Quota</SortTableHeader>
                </tr>
            </thead>
            <tbody>
                {sorted.data.map((point, idx, items) => {
                    if (!shownProductNames.includes(point.name)) return null;
                    const matchesNextEntry = items[idx + 1]?.name === point.name;
                    const change = matchesNextEntry ? point.usage - (items[idx + 1]?.usage ?? 0) : 0;
                    if (change === 0 && matchesNextEntry) return null;
                    return <tr key={idx} style={{cursor: "pointer"}}>
                        <td onClick={() => toggleChart(point.name)}>{point.name}</td>
                        <td>{dateToString(point.timestamp)}</td>
                        <td style={{whiteSpace: "pre"}}>{Accounting.formatUsageAndQuota(point.usage, point.quota, props.unit === "GB", props.unit, {precision: 2})}</td>
                    </tr>;
                })}
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
        return data?.dataPoints?.map(it => ({key: it.username, value: it.usage})) ?? [];
    }, [data?.dataPoints]);
    const formatter = useCallback((val: number) => {
        if (!data) return "";
        return Accounting.addThousandSeparators(val.toFixed(2)) + " " + data.unit;
    }, [data?.unit]);

    const project = useProject().fetch();
    const startExport = useCallback(() => {
        exportUsage(
            dataPoints,
            [header("key", "Username", true), header("value", "Estimated usage", true)],
            projectTitle(project)
        );
    }, [dataPoints, project]);

    const sorted = useSorting(dataPoints, "key");

    return <div className={classConcat(CardClass, PanelClass, LargeJobsStyle)}>
        <Flex>
            <div className="panel-title">
                <h4>Usage by users</h4>
            </div>
            <Button ml="auto" onClick={startExport}>
                Export
            </Button>
        </Flex>

        {data !== undefined && sorted.data.length !== 0 ? <>
            <PieChart chartId="UsageByUsers" dataPoints={sorted.data} valueFormatter={formatter} onDataPointSelection={() => {}} />

            <div className={TableWrapper.class}>
                <table>
                    <thead>
                        <tr>
                            <SortTableHeader sortKey="key" sorted={sorted} width="75%">Username</SortTableHeader>
                            <SortTableHeader sortKey="value" sorted={sorted} width="25%">
                                Estimated usage
                                {" "}
                                <TooltipV2
                                    tooltip={"This is an estimate based on the values stored in UCloud. Actual usage reported by the provider may differ from the numbers shown here."}>
                                    <Icon name={"heroQuestionMarkCircle"} />
                                </TooltipV2>
                            </SortTableHeader>
                        </tr>
                    </thead>
                    <tbody>
                        {sorted.data.map(it => <tr key={it.key}>
                            <td>{it.key}</td>
                            <td>{Accounting.addThousandSeparators(it.value.toFixed(0))} {data.unit}</td>
                        </tr>)}
                    </tbody>
                </table>
            </div>
        </> : loading ? <HexSpin size={32} /> : "No usage data found"}
    </div>;
};

// Utility components
// =====================================================================================================================

const PieChart: React.FunctionComponent<{
    dataPoints: {key: string, value: number}[],
    valueFormatter: (value: number) => string,
    onDataPointSelection: (dataPointIndex: {key: string; value: number;}) => void;
    chartId: string;
}> = props => {
    const otherKeys = React.useRef<string[]>([]);
    const filteredList = useMemo(() => {
        otherKeys.current = [];
        const all = [...props.dataPoints];

        all.sort((a, b) => {
            if (a.value > b.value) return -1;
            if (a.value < b.value) return 1;
            return 0;
        });

        return all;
    }, [props.dataPoints]);

    const {series, labels} = React.useMemo(() => {
        const series: number[] = [];
        const labels: string[] = [];

        for (const element of filteredList) {
            const idx = labels.findIndex(it => it === element.key);
            if (idx !== -1) {
                series[idx] += element.value;
            } else {
                labels.push(element.key);
                series.push(element.value);
            }
        }

        return {series, labels}
    }, [filteredList]);

    const chartProps = useMemo((): ChartProps => {
        const chartProps: ChartProps = {};
        chartProps.type = "pie";
        chartProps.series = series;
        const chart: ApexChart = {
            id: props.chartId,
            width: "1200px",
            animations: {
                enabled: false,
            },
            events: {
                dataPointSelection: (e: any, chart?: any, options?: any) => {
                    const dataPointIndex = options?.dataPointIndex;
                    if (dataPointIndex != null) {
                        props.onDataPointSelection({value: series[dataPointIndex], key: labels[dataPointIndex]});
                    }
                },
                legendClick(chart, seriesIndex, options) {
                    if (seriesIndex != null) {
                        chart.toggleDataPointSelection(seriesIndex);
                        props.onDataPointSelection({value: series[seriesIndex], key: labels[seriesIndex]})
                    }

                },
            },
        };

        chartProps.selection = {enabled: true};

        chartProps.options = {
            chart,
            legend: {
                position: "bottom",
                height: 100
            },
            responsive: [],
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
        };

        threadDeferLike(() => {
            const chart = ApexCharts.getChartByID(props.chartId);
            if (chart) {
                chart.updateSeries(series)
            }
        });

        return chartProps;
    }, [series]);

    return <DynamicallySizedChart chart={chartProps} />;
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
    dataPoints: {projectId?: string | null, title: string, usage: number}[];
    nameAndProvider: string;
}

const emptyBreakdownChart: BreakdownChart = {
    nameAndProvider: "",
    dataPoints: [],
};

interface UsageChart {
    name: string;
    provider: string;
    dataPoints: {timestamp: number, usage: number, quota: number}[];
    future?: Accounting.WalletPrediction;
}

const emptyChart: UsageChart = {
    provider: "",
    dataPoints: [],
    name: "",
};

function toSeriesChart(chart: UsageChart, forecastCount: number): ApexAxisChartSeries[0] {
    let data = chart.dataPoints.map(it => ({x: it.timestamp, y: it.usage}));
    if (data.length === 0 || data.every(it => it.x == 0)) {
        data = [];
    } else if (chart.future?.predictions.length && hasFeature(Feature.USAGE_PREDICTION)) {
        for (const pred of chart.future.predictions.slice(0, forecastCount)) {
            data.push({x: pred.timestamp, y: pred.value});
        }
    }

    /* TODO(Jonas): I don't understand why this should be necessary.  */
    data.sort((a, b) => a.x - b.x);

    return {
        data,
        type: "line",
        name: chart.name,
    }
}

function quotaSeriesFromDataPoints(chart: UsageChart): ApexAxisChartSeries[0] {
    const data = chart.dataPoints.map(it => ({x: it.timestamp, y: it.quota})).sort((a, b) => a.x - b.x);

    return {
        data,
        type: "line",
        name: `${chart.name} quota`,
    };
}

const DEFAULT_FUTURE_COUNT = 30;
function usageChartsToChart(
    charts: UsageChart[],
    chartRef: React.RefObject<{name: string; shown: boolean;}[]>,
    chartOptions: {
        valueFormatter?: (value: number) => string;
        removeDetails?: boolean;
        toggleShown?: (indexOrAllState: string[] | true | false) => void;
        id?: string;
    } = {},
    // Note(Jonas): This is to reduce amount of future points. If period is 7 days, then we should cap future to 7 days.
    maxFuturePoints: number,
): ChartProps {
    const result: ChartProps = {};
    const anyFuture = charts.find(it => it.future) != null;
    const forecastCount = !hasFeature(Feature.USAGE_PREDICTION) || !anyFuture ? 0 : Math.min(
        maxFuturePoints,
        DEFAULT_FUTURE_COUNT,
        charts.reduce((max, chart) => Math.max(max, chart.dataPoints.length), 0)
    );
    result.series = charts.map(it => toSeriesChart(it, forecastCount));

    result.options = {
        legend: {
            position: "bottom",
            onItemClick: {
                toggleDataSeries: true,
            },
        },
        forecastDataPoints: {
            count: forecastCount,
        },
        /* Again, very cool ApexCharts! https://github.com/apexcharts/apexcharts.js/issues/4447 */
        chart: {
            id: chartOptions.id,
            events: {
                // Note(Jonas): Very cool, ApexCharts https://github.com/apexcharts/apexcharts.js/issues/3725
                legendClick(chart: ApexCharts, seriesIndex?: number | null, options?: any) {
                    if (seriesIndex == null) {
                        return;
                    }

                    const seriesName = chartRef.current[seriesIndex].name;
                    toggleSeriesEntry(chart, seriesName, chartRef, chartOptions.toggleShown);
                }
            },
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
        colors: [
            "var(--primaryMain)",
            "var(--green-40)",
            "var(--red-60)",
            "var(--purple-70)",
            "var(--gray-50)",
            "var(--orange-40)",
            "var(--pink-40)",
            "var(--yellow-30)",
            "var(--blue-40)",
            "var(--green-70)",
            "var(--red-40)",
            "var(--purple-40)",
            "var(--gray-30)",
            "var(--orange-70)",
            "var(--pink-80)",
            "var(--yellow-70)"
        ].slice(0, result.series.length),
        markers: {
            size: 0,
        },
        stroke: {
            curve: "straight"
        },
        yaxis: {
            labels: {
                formatter: function (val) {
                    if (chartOptions.valueFormatter) {
                        return chartOptions.valueFormatter(val);
                    } else {
                        return val.toString();
                    }
                },
            },
            title: {
                text: "Usage"
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
                    if (chartOptions.valueFormatter) {
                        return chartOptions.valueFormatter(val);
                    } else {
                        return val.toString();
                    }
                }
            }
        },
    };

    if (chartOptions.removeDetails === true) {
        delete result.options.title;
        result.options.tooltip = {enabled: false};
        const c = result.options.chart!;
        c.sparkline = {enabled: true};
        c.zoom!.enabled = false;
    }

    return result;
}

function toggleSeriesEntry(chart: ApexCharts | undefined, seriesName: string, chartsRef: React.RefObject<{name: string; shown: boolean;}[]>, toggleShown?: (val: boolean | string[]) => void) {
    /* TODO(Jonas): Handle when quota is shown. */
    if (chart != null) {
        const allShown = chartsRef.current.every(it => it.shown);
        const shownCount = chartsRef.current.reduce((acc, it) => acc + (+it.shown), 0);
        const allWillBeHidden = chartsRef.current.find(it => it.name === seriesName)?.shown && shownCount === 1;

        if (allShown) {
            const isQuotaEntry = seriesName.endsWith(" quota");
            const seriesWithoutQuotaSuffix = isQuotaEntry ? seriesName.replace(" quota", "") : seriesName;
            const singleProduct = chartsRef.current.length === 2 && chartsRef.current.find(it => it.name.endsWith(" quota")) != null;

            if (singleProduct) {
                toggleShown?.([seriesName]);
                return;
            }

            for (const shownEntry of chartsRef.current) {
                if (shownEntry.name === seriesName) {
                    /* 
                        Hack(Jonas): It seems that `showSeries` is called before
                        the chart toggles the legend entry otherwise, so this has to be "deferred" 
                    */
                    threadDeferLike(() => {
                        chart.showSeries(seriesName);
                        if (isQuotaEntry) chart.showSeries(seriesWithoutQuotaSuffix);
                    });
                    continue;
                }

                chart.hideSeries(shownEntry.name);
            }

            const toToggle = chartsRef.current.map(it => it.name).filter(it => it !== seriesName && it !== seriesWithoutQuotaSuffix);
            toggleShown?.(toToggle);
        } else if (allWillBeHidden) {
            toggleShown?.(true);
            threadDeferLike(() => {
                chart.resetSeries();
            });
        } else {
            chart.toggleSeries(seriesName);
            toggleShown?.([seriesName]);
        }
    }
}

const RenderUnitSelector: RichSelectChildComponent<{unit: string}> = ({element, onSelect, dataProps}) => {

    if (element === undefined) {
        return <Flex height={40} alignItems={"center"} pl={12}>No unit selected</Flex>
    }

    const unit = element.unit;
    return <Flex gap={"16px"} height="40px" {...dataProps} alignItems={"center"} py={4} px={8} mr={48} onClick={onSelect}>
        <Icon name={toIcon(unit)} />
        <div><b>{unitTitle(unit)}</b></div>
    </Flex>;
}

function unitTitle(unit: string) {
    switch (unit) {
        case JOBS_UNIT_NAME: return JOBS_UNIT_NAME;
        case "DKK": return "Usage in " + unit;
        default: return unit + " usage";
    }
}

function toIcon(unit: string): IconName {
    switch (unit) {
        case "DKK":
            return "heroBanknotes";
        case "GPU-hours":
            return "heroServerStack";
        case "Core-hours":
            return "cpu"
        case "GB":
            return "hdd";
        case "IPs":
            return "heroGlobeEuropeAfrica"
        case "Licenses":
            return "heroDocumentCheck";
        case JOBS_UNIT_NAME:
            return "heroServer"
        default:
            return "broom";
    }
}

const TableWrapper = makeClassName("table-wrapper");
const PanelTitle = makeClassName("panel-title");
const ChartAndTable = injectStyle("chart-and-table", k => `
    ${k} {
        display: flex;
        flex-direction: row;
    }

@media screen and (min-width: 1338px) {
    ${k} > div:first-child {
        margin-top: auto;
        margin-bottom: auto;
    }
}

@media screen and (max-width: 1337px) {
    ${k} {
        flex-direction: column;
    }

    ${k} > div:first-child {
        width: 100%;
        margin-left: auto;
        margin-right: auto;
        max-height: 400px;
    }

    ${k} ${TableWrapper.dot} {
        max-height: 400px;
    }
}
`);

const PanelClass = injectStyle("panel", k => `
    ${k} {
        height: 100%;
        width: 100%;
        padding-bottom: 20px;
        
        /* Required for flexible cards to ensure that they are not allowed to grow their height based on their 
           content */
        min-height: 100px; 
        max-height: 1000px;
    }

    ${k} ${PanelTitle.dot} {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin-bottom: 10px;
        z-index: 1; /* HACK(Jonas): Why is this needed for smaller widths? */
    }

    ${k} ${PanelTitle.dot} > *:nth-child(1) {
        font-size: 18px;
        margin: 0;
    }

    ${k} ${PanelTitle.dot} > *:nth-child(2) {
        flex-grow: 1;
    }
    
    ${k} ${TableWrapper.dot} {
        flex-grow: 1;
        overflow-y: scroll;
        min-width: 400px;
        min-height: 200px;
        max-height: 400px;
    }

@media screen and (max-width: 1337px) {
    ${k} ${TableWrapper.dot} {
        margin-top: 12px;
        max-height: 300px;
    }
}
    
    html.light ${k} ${TableWrapper.dot} {
        box-shadow: inset 0px -11px 8px -10px #ccc;
    }
    
    html.dark ${k} ${TableWrapper.dot} {
        box-shadow: inset 0px -11px 8px -10px rgba(255, 255, 255, 0.5);
    }
    
    ${k} ${TableWrapper.dot}.at-bottom {
        box-shadow: unset !important;
    }
    
    html.light ${k} ${TableWrapper.dot}::before {
        box-shadow: 0px -11px 8px 11px #ccc;
    }
    
    html.dark ${k} ${TableWrapper.dot}::before {
        box-shadow: 0px -11px 8px 11px rgba(255, 255, 255, 0.5);
    }
    
    ${k} ${TableWrapper.dot}::before {
        display: block;
        content: " ";
        width: 100%;
        height: 1px;
        position: sticky;
        top: 24px;
    }
    
    ${k} ${TableWrapper.dot}.at-top::before {
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
        paddingControlledByContent={true}
        noYPadding={true}
        onOpeningTriggerClick={() => setPeriod(normalizePeriod(props.value))}
        trigger={
            <div className={PeriodStyle}>
                <div style={{width: "182px"}}>{periodToString(props.value)}</div>
                <Icon name="chevronDownLight" size="14px" ml="4px" mt="4px" />
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
                        <Input className={"start"} onChange={onChange} type={"date"} value={formatTs(start)} />
                    </label>
                    <label>
                        To
                        <Input className={"end"} onChange={onChange} type={"date"} value={formatTs(end)} />
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

function unitsFromSummaries(summaries: State["summaries"]): string[] {
    return [...new Set(summaries.map(it => Accounting.explainUnit(it.category).name))];
}

// Styling
// =====================================================================================================================

const PanelGrid = makeClassName("panel-grid");
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
    ${k} ${PanelGrid.dot} {
        display: flex;
        flex-direction: column;
        gap: 16px;
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
};

export default Visualization;
