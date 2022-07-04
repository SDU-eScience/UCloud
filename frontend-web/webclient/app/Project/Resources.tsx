import {Area, AreaChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis} from "recharts";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {PageV2} from "@/UCloud";
import {DateRangeFilter, EnumFilter, FilterWidgetProps, PillProps, ResourceFilter, ValuePill} from "@/Resource/Filter";
import {capitalized, doNothing, prettierString, timestampUnixMs} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {Box, Flex, Grid, Heading, Icon, Text} from "@/ui-components";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import styled from "styled-components";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {
    browseWallets,
    ChargeType,
    productAreaTitle,
    ProductPriceUnit,
    ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    retrieveBreakdown,
    retrieveUsage,
    UsageChart,
    usageExplainer,
    Wallet,
} from "@/Accounting";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {BrowseType} from "@/Resource/BrowseType";
import {format} from "date-fns/esm";
import {Spacer} from "@/ui-components/Spacer";


const ANIMATION_DURATION = 1000;

function dateFormatter(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} ` +
        `${date.getHours().toString().padStart(2, "0")}:` +
        `${date.getMinutes().toString().padStart(2, "0")}`;
}

const filterWidgets: React.FunctionComponent<FilterWidgetProps>[] = [];
const filterPills: React.FunctionComponent<PillProps>[] = [];

function registerFilter([w, p]: [React.FunctionComponent<FilterWidgetProps>, React.FunctionComponent<PillProps>]) {
    filterWidgets.push(w);
    filterPills.push(p);
}

registerFilter(DateRangeFilter("calendar", "Usage period", "filterEndDate", "filterStartDate"));
registerFilter(EnumFilter("cubeSolid", "filterType", "Product type", productTypes.map(t => ({
    icon: productTypeToIcon(t),
    title: productTypeToTitle(t),
    value: t
}))));

filterPills.push(props =>
    <ValuePill {...props} propertyName={"filterWorkspace"} secondaryProperties={["filterWorkspaceProject"]}
        showValue={true} icon={"projects"} title={"Workspace"} />);

filterPills.push(props =>
    <ValuePill {...props} propertyName={"filterAllocation"} showValue={false} icon={"grant"} title={"Allocation"} />);

const ResourcesGrid = styled.div`
    display: grid;
    grid-template-columns: 1fr;
    grid-gap: 16px;
`;

const Resources: React.FunctionComponent = () => {

    const pastMonthEnd = new Date(timestampUnixMs()).getTime();
    const pastMonthStart = pastMonthEnd - (30 * 1000 * 60 * 60 * 24);
    const [filters, setFilters] = useState<Record<string, string>>({showSubAllocations: "true"});

    React.useEffect(() => {
        if (filters.filterStartDate == null || filters.filterEndDate == null) {
            setFilters({
                ...filters,
                filterStartDate: pastMonthStart.toString(),
                filterEndDate: pastMonthEnd.toString()
            });
        }
    }, [filters]);

    const filterStart = format(parseInt(filters.filterStartDate ?? pastMonthStart), "dd/MM/yyyy");
    const filterEnd = format(parseInt(filters.filterEndDate ?? pastMonthEnd), "dd/MM/yyyy");
    const [usage, fetchUsage] = useCloudAPI<{charts: UsageChart[]}>({noop: true}, {charts: []});
    const [breakdowns, fetchBreakdowns] = useCloudAPI<{charts: BreakdownChart[]}>({noop: true}, {charts: []});
    const [wallets, fetchWallets] = useCloudAPI<PageV2<Wallet>>({noop: true}, emptyPageV2);

    const [maximizedUsage, setMaximizedUsage] = useState<number | null>(null);

    const onUsageMaximize = useCallback((idx: number) => {
        if (maximizedUsage == null) setMaximizedUsage(idx);
        else setMaximizedUsage(null);
    }, [maximizedUsage]);

    const reloadPage = useCallback(() => {
        fetchUsage(retrieveUsage({...filters}));
        fetchBreakdowns(retrieveBreakdown({...filters}));
        fetchWallets(browseWallets({itemsPerPage: 50, ...filters}));
        setMaximizedUsage(null);
    }, [filters]);

    useTitle("Resources");
    useSidebarPage(SidebarPages.Projects);
    useRefreshFunction(reloadPage);
    useEffect(() => {
        if (filters.filterStartDate != null && filters.filterEndDate != null) {
            reloadPage();
        }
    }, [reloadPage]);
    useLoading(usage.loading || breakdowns.loading || wallets.loading);

    const unusedProductTypes = React.useMemo(() =>
        usage.data.charts.map(it => it.type).reduce((remaining, currentProductType) => remaining.filter(productType => productType !== currentProductType), [...productTypes])
        , [usage.data]);

    return (
        <MainContainer
            header={<Spacer
                width={"calc(100% - var(--sidebarWidth))"}
                left={<ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Resources"}]} />}
                right={<Box ml="12px" width="512px">Viewing usage from {filterStart} to {filterEnd}</Box>}
            />}
            sidebar={<ResourceFilter
                browseType={BrowseType.MainContent}
                pills={filterPills}
                filterWidgets={filterWidgets}
                sortEntries={[]}
                properties={filters}
                setProperties={setFilters}
                sortDirection={"ascending"}
                onSortUpdated={doNothing}
            />}
            main={<ResourcesGrid>
                <Grid gridGap={"16px"}>
                    {maximizedUsage == null ? null :
                        <UsageChartViewer maximized c={usage.data.charts[maximizedUsage]}
                            onMaximizeToggle={() => onUsageMaximize(maximizedUsage)} />
                    }
                    {maximizedUsage != null ? null :
                        <VisualizationSection>
                            {usage.data.charts.map((it, idx) => {
                                const count = breakdowns.data.charts.filter(bd => bd.type == it.type).length;
                                const donuts = breakdowns.data.charts.filter(bd =>
                                    bd.type === it.type &&
                                    bd.chargeType == it.chargeType &&
                                    bd.unit == it.unit
                                ).map((it, idx) => <DonutChart key={idx} chart={it} />);
                                return <HighlightedCard
                                    key={it.type}
                                    title={`${productAreaTitle(it.type)}`}
                                    icon={productTypeToIcon(it.type)}
                                    color="blue"
                                    width="400px"
                                >
                                    <Flex style={{flexDirection: "column", height: "calc(100% - 36px)"}}>
                                        <Box color={"var(--gray)"} height="20px">{count > 1 ? prettierString(it.unit) : null}</Box>
                                        {donuts}
                                        <Flex style={{flexGrow: 1}} />
                                        <UsageChartViewer key={idx} c={it} onMaximizeToggle={() => onUsageMaximize(idx)} />
                                    </Flex>
                                </HighlightedCard>
                            })}
                            {unusedProductTypes.map(pt =>
                                <HighlightedCard
                                    key={pt}
                                    title={`${productAreaTitle(pt)}`}
                                    icon={productTypeToIcon(pt)}
                                    color="blue"
                                    width="400px"
                                >
                                    <Flex style={{flexDirection: "column", height: "calc(100% - 36px)"}}>
                                        <Box mb="auto" />
                                        <Heading ml="auto" mr="auto">No usage found</Heading>
                                        <Box mt="auto" />
                                    </Flex>
                                </HighlightedCard>
                            )}
                        </VisualizationSection>
                    }
                </Grid>
            </ResourcesGrid>}
        />
    );
};

export const VisualizationSection = styled.div`
    display: grid;
    grid-gap: 16px;
    padding: 10px 0;
    grid-template-columns: repeat(auto-fill, 400px);
`;

const UsageChartStyle = styled.div`
    .usage-chart {
        width: calc(100% + 32px) !important;
        margin: -16px;
        margin-bottom: -4px;
    }
`;

const UsageChartViewer: React.FunctionComponent<{
    c: UsageChart;
    maximized?: boolean;
    onMaximizeToggle: () => void;
}> = ({c, maximized, onMaximizeToggle}) => {
    const [flattenedLines, names] = useMemo(() => {
        const names: string[] = [];
        const work: Record<string, Record<string, any>> = {};
        for (const line of c.chart.lines) {
            names.push(line.name);
            for (const point of line.points) {
                const key = point.timestamp.toString();
                const entry: Record<string, any> = work[key] ?? {};
                entry["timestamp"] = point.timestamp;
                entry[line.name] = point.value;
                work[key] = entry;
            }
        }

        const result: Record<string, any>[] = [];
        Object.keys(work).map(it => parseInt(it)).sort().forEach(bucket => {
            result.push(work[bucket]);
        });

        for (let i = 0; i < result.length; i++) {
            const previousBucket = i > 0 ? result[i - 1] : null;
            const currentBucket = result[i];

            for (const name of names) {
                if (!currentBucket.hasOwnProperty(name)) {
                    currentBucket[name] = previousBucket?.[name] ?? 0;
                }
            }
        }
        return [result, names];
    }, [c.chart]);

    const formatter = useCallback((amount: number) => {
        return usageExplainer(amount, c.type, c.chargeType, c.unit);
    }, [c.type, c.chargeType, c.unit])

    return <UsageChartStyle>
        <Flex alignItems={"center"}>
            <div>
                <Text my="-6px"
                    fontSize="18px">{usageExplainer(c.periodUsage, c.type, c.chargeType, c.unit)} used</Text>
            </div>
            <Box flexGrow={1} />
            <Icon name={"fullscreen"} cursor={"pointer"} onClick={onMaximizeToggle} />
        </Flex>

        <ResponsiveContainer className={"usage-chart"} height={maximized ? 800 : 190}>
            <AreaChart
                margin={{
                    left: 0,
                    top: 4,
                    right: 0,
                    bottom: -38
                }}
                data={flattenedLines}
            >
                <XAxis dataKey={"timestamp"} />
                <Tooltip labelFormatter={dateFormatter} formatter={formatter} />
                {names.map((it, index) =>
                    <Area
                        key={it}
                        type={"linear"}
                        opacity={0.8}
                        dataKey={it}
                        animationDuration={ANIMATION_DURATION}
                        strokeWidth={"2px"}
                        stroke={getCssVar(("dark" + capitalized(COLORS[index % COLORS.length]) as ThemeColor))}
                        fill={getCssVar(COLORS[index % COLORS.length])}
                    />
                )}
            </AreaChart>
        </ResponsiveContainer>
    </UsageChartStyle>
};

const COLORS: [ThemeColor, ThemeColor, ThemeColor, ThemeColor, ThemeColor] = ["green", "red", "blue", "orange", "yellow"];

interface BreakdownChart {
    type: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;
    chart: {points: {name: string, value: number}[]}
}

function toPercentageString(value: number) {
    return `${Math.round(value * 10_000) / 100} %`;
}

const DonutChart: React.FunctionComponent<{chart: BreakdownChart}> = props => {
    const totalUsage = props.chart.chart.points.reduce((prev, current) => prev + current.value, 0);
    if (totalUsage === 0 || props.chart.chart.points.length < 2) return null;
    return (
        <>
            <Text color="darkGray" fontSize={1}>Usage across different products</Text>

            <Flex style={{borderBottom: "solid black 1px", marginBottom: "18px"}}>
                <Flex mt="12px">
                    <PieChart width={215} height={215}>
                        <Pie
                            data={props.chart.chart.points}
                            fill="#8884d8"
                            dataKey="value"
                            innerRadius={55}
                            animationDuration={ANIMATION_DURATION}
                        >
                            {props.chart.chart.points.map((_, index) => (
                                <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])} />
                            ))}
                        </Pie>
                    </PieChart>
                </Flex>

                <Box ml="4px" textAlign="center" width="100%" height="250px" pb="12px" style={{overflowY: "scroll"}} justifyContent={"center"}>
                    {props.chart.chart.points.map((it, index) =>
                        <Box mb="4px" width="100%" style={{whiteSpace: "nowrap"}} key={it.name}>
                            <ChartPointName name={it.name} />
                            <Text textAlign="center" color={getCssVar(COLORS[index % COLORS.length])}>
                                {toPercentageString(it.value / totalUsage)}
                            </Text>
                        </Box>
                    )}
                </Box>
            </Flex>
        </>
    )
}

function ChartPointName({name}: {name: string}): JSX.Element {
    const [first, second, third] = name.split(" / ");
    return (
        <div>
            <Text fontSize="14px">{first}</Text>
            <SubText>{second}{third ? ` / ${third}` : null}</SubText>
        </div>
    );
}

const SubText = styled.div`
    color: var(--gray);
    text-decoration: none;
    font-size: 10px;
`;

export default Resources;
