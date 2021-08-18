import {Area, AreaChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis} from "recharts";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {useProjectManagementStatus} from "Project";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";
import {accounting, PageV2, PaginationRequestV2} from "UCloud";
import Product = accounting.Product;
import {DateRangeFilter, EnumFilter, FilterWidgetProps, PillProps, ResourceFilter} from "Resource/Filter";
import {useCallback, useEffect, useMemo, useState} from "react";
import {capitalized, doNothing, timestampUnixMs} from "UtilityFunctions";
import {DashboardCard} from "Dashboard/Dashboard";
import {useHistory} from "react-router";
import {Client} from "Authentication/HttpClientInstance";
import {ThemeColor} from "ui-components/theme";
import {Box, Flex, Grid, Icon, OutlineButton, Text} from "ui-components";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {ProductArea} from "Accounting";
import {IconName} from "ui-components/Icon";
import {Spacer} from "ui-components/Spacer";
import ClickableDropdown from "ui-components/ClickableDropdown";
import styled from "styled-components";
import ProductCategoryId = accounting.ProductCategoryId;
import {formatDistance} from "date-fns";
import {apiBrowse, apiRetrieve, useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {emptyPageV2} from "DefaultObjects";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";

function dateFormatter(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} ` +
        `${date.getHours().toString().padStart(2, "0")}:` +
        `${date.getMinutes().toString().padStart(2, "0")}`;
}

function dateFormatterDay(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getDate()}/${date.getMonth() + 1} `;
}

function dateFormatterMonth(timestamp: number): string {
    const date = new Date(timestamp);
    return `${date.getMonth() + 1}/${date.getFullYear()} `;
}

export function priceExplainer(product: Product): string {
    switch (product.type) {
        case "compute":
            return `${creditFormatter(product.pricePerUnit * 60, 4)}/hour`;
        case "storage":
            return `${creditFormatter(product.pricePerUnit * 30)}/GB per month`;
        default:
            return `${creditFormatter(product.pricePerUnit)}/unit`;
    }
}

export function creditFormatter(credits: number, precision = 2): string {
    if (precision < 0 || precision > 6) throw Error("Precision must be in 0..6");

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

const productTypes: ProductArea[] = ["STORAGE", "COMPUTE", "INGRESS", "NETWORK_IP", "LICENSE"];

function productTypeToTitle(type: ProductArea): string {
    switch (type) {
        case "INGRESS":
            return "Public Link"
        case "COMPUTE":
            return "Compute";
        case "STORAGE":
            return "Storage";
        case "NETWORK_IP":
            return "Public IP";
        case "LICENSE":
            return "Software License";
    }
}

function productTypeToIcon(type: ProductArea): IconName {
    switch (type) {
        case "INGRESS":
            return "globeEuropeSolid"
        case "COMPUTE":
            return "cpu";
        case "STORAGE":
            return "hdd";
        case "NETWORK_IP":
            return "networkWiredSolid";
        case "LICENSE":
            return "apps";
    }
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

interface VisualizationFlags {
    filterStartDate?: number | null;
    filterEndDate?: number | null;
    filterType?: ProductArea | null;
    filterProvider?: string | null;
    filterProductCategory?: string | null;
    filterAllocation?: string | null;
}

function retrieveUsage(request: VisualizationFlags): APICallParameters {
    return apiRetrieve(request, "/api/accounting/visualization", "usage");
}

function retrieveBreakdown(request: VisualizationFlags): APICallParameters {
    return apiRetrieve(request, "/api/accounting/visualization", "breakdown");
}

function browseWallets(request: PaginationRequestV2): APICallParameters {
    return apiBrowse(request, "/api/accounting/wallets");
}

const Resources: React.FunctionComponent = props => {
    const {projectId, reload} = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});
    const [properties, setProperties] = useState<Record<string, string>>({});
    const [usage, fetchUsage] = useCloudAPI<{charts: UsageChart[]}>({noop: true}, {charts: []});
    const [breakdowns, fetchBreakdowns] = useCloudAPI<{charts: BreakdownChart[]}>({noop: true}, {charts: []});
    const [wallets, fetchWallets] = useCloudAPI<PageV2<Wallet>>({noop: true}, emptyPageV2);

    const reloadPage = useCallback(() => {
        fetchUsage(retrieveUsage({}));
        fetchBreakdowns(retrieveBreakdown({}));
        fetchWallets(browseWallets({itemsPerPage: 50}));
    }, []);

    useTitle("Usage");
    useSidebarPage(SidebarPages.Projects);
    useRefreshFunction(reloadPage);
    useEffect(reloadPage, [reloadPage]);
    useLoading(usage.loading || breakdowns.loading || wallets.loading);

    const usageClassName = usage.data.charts.length > 3 ? "large" : "slim";
    const walletsClassName = wallets.data.items.reduce((prev, current) => prev + current.allocations.length, 0) > 3 ?
        "large": "slim";
    const breakdownClassName = breakdowns.data.charts.length > 3 ? "large" : "slim";

    return (
        <MainContainer
            header={
                <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Resources"}]}/>
            }
            headerSize={60}
            sidebar={<>
                <ResourceFilter
                    embedded={false}
                    pills={filterPills}
                    filterWidgets={filterWidgets}
                    sortEntries={[]}
                    properties={properties}
                    setProperties={setProperties}
                    sortDirection={"ascending"}
                    onSortUpdated={doNothing}
                    onApplyFilters={doNothing}
                />
            </>}
            main={<Grid gridGap={"16px"}>
                <VisualizationSection className={usageClassName}>
                    {usage.data.charts.map((it, idx) =>
                        <UsageChartViewer key={idx} c={it}/>
                    )}
                </VisualizationSection>
                <VisualizationSection className={walletsClassName}>
                    {wallets.data.items.map((it, idx) =>
                        <WalletViewer key={idx} wallet={it}/>
                    )}
                </VisualizationSection>
                <VisualizationSection className={breakdownClassName}>
                    {breakdowns.data.charts.map((it, idx) =>
                        <DonutChart key={idx} chart={it}/>
                    )}
                </VisualizationSection>
            </Grid>}
        />
    );
};

type WalletOwner = { type: "user"; username: string } | { type: "project"; projectId: string; };

interface WalletAllocation {
    id: string;
    allocationPath: string;
    balance: number;
    initialBalance: number;
    localBalance: number;
    startDate: number;
    endDate?: number | null;
}

interface Wallet {
    owner: WalletOwner;
    paysFor: ProductCategoryId;
    allocations: WalletAllocation[];
    chargePolicy: "EXPIRE_FIRST";
    productType?: ProductArea | null;
    chargeType?: string | null;
    unit?: string | null;
}

const WalletViewer: React.FunctionComponent<{ wallet: Wallet }> = ({wallet}) => {
    return <>
        {wallet.allocations.map((it, idx) => <AllocationViewer key={idx} wallet={wallet} allocation={it}/>)}
    </>
}

const AllocationViewer: React.FunctionComponent<{
    wallet: Wallet;
    allocation: WalletAllocation;
}> = ({wallet, allocation}) => {
    return <DashboardCard color={"red"} width={"400px"}>
        <Flex flexDirection={"row"} alignItems={"center"} height={"100%"}>
            <Icon name={wallet.productType ? productTypeToIcon(wallet.productType) : "cubeSolid"}
                  size={"54px"} mr={"16px"}/>
            <Flex flexDirection={"column"} height={"100%"}>
                <div><b>{wallet.paysFor.name} / {wallet.paysFor.provider}</b></div>
                <div>{creditFormatter(allocation.balance)} remaining</div>
                <div>{creditFormatter(allocation.initialBalance)} allocated</div>
                <Box flexGrow={1} mt={"8px"}/>
                <div><ExpiresIn startDate={allocation.startDate} endDate={allocation.endDate}/></div>
            </Flex>
        </Flex>
    </DashboardCard>;
};

const ExpiresIn: React.FunctionComponent<{ startDate: number, endDate?: number | null; }> = ({startDate, endDate}) => {
    const now = timestampUnixMs();
    if (endDate == null) {
        return <>No expiration</>;
    } else if (now < startDate) {
        return <>Starts in {formatDistance(new Date(startDate), new Date(now))}</>;
    } else if (now < endDate) {
        return <>Expires in {formatDistance(new Date(endDate), new Date(now))}</>;
    } else {
        return <>Expires soon</>;
    }
};

interface UsageChart {
    type: ProductArea;
    periodUsage: number;
    chargeType: string;
    unit: string;
    chart: {
        lines: {
            name: string;
            points: {
                timestamp: number;
                value: number;
            }[]
        }[]
    }
}

const VisualizationSection = styled.div`
  --gutter: 16px;
  
  display: grid;
  grid-gap: 16px;
  padding: 10px;
  
  &.large {
    grid-auto-columns: 400px;
    grid-template-rows: minmax(100px, 1fr) minmax(100px, 1fr);
    grid-auto-flow: column;
  }
  
  &.slim {
    grid-template-columns: repeat(auto-fit, 400px);
  }
  
  overflow-x: auto;
  scroll-snap-type: x proximity;
  padding-bottom: calc(.75 * var(--gutter));
  margin-bottom: calc(-.25 * var(--gutter));
`;

const UsageChartStyle = styled.div`
  .usage-chart {
    width: calc(100% + 32px) !important;
    margin: -16px;
  }


`;

const UsageChartViewer: React.FunctionComponent<{ c: UsageChart }> = ({c}) => {
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

    return <DashboardCard color={"blue"} width={"400px"}>
        <UsageChartStyle>
            <Spacer
                left={
                    <div>
                        <Text color="gray">{productTypeToTitle(c.type)}</Text>
                        <Text bold my="-6px" fontSize="24px">{creditFormatter(c.periodUsage)} used</Text>
                    </div>
                }
                right={
                    <ClickableDropdown
                        trigger={<Box mr="4px" mt="4px"><Icon rotation={90} name="ellipsis"/></Box>}
                        left="-110px"
                        top="-4px"
                        options={[{text: "Storage (Size)", value: "storage_gb" as const}, {
                            text: "Storage (DKK)",
                            value: "storage_price" as const
                        }]}
                        onChange={doNothing}
                    />
                }
            />

            <ResponsiveContainer className={"usage-chart"} height={170}>
                <AreaChart
                    margin={{
                        left: 0,
                        top: 4,
                        right: 0,
                        bottom: -28
                    }}
                    data={flattenedLines}
                >
                    <XAxis dataKey={"timestamp"}/>
                    <Tooltip labelFormatter={dateFormatter} formatter={creditFormatter}/>
                    {names.map((it, index) =>
                        <Area
                            key={it}
                            type={"linear"}
                            opacity={0.8}
                            dataKey={it}
                            strokeWidth={"2px"}
                            stroke={getCssVar(("dark" + capitalized(COLORS[index % COLORS.length]) as ThemeColor))}
                            fill={getCssVar(COLORS[index % COLORS.length])}
                        />
                    )}
                </AreaChart>
            </ResponsiveContainer>
        </UsageChartStyle>
    </DashboardCard>
};

const COLORS: [ThemeColor, ThemeColor, ThemeColor, ThemeColor, ThemeColor] = ["green", "red", "blue", "orange", "yellow"];

interface BreakdownChart {
    type: ProductArea;
    chargeType: string;
    unit: string;
    chart: { points: { name: string, value: number }[] }
}

function toPercentageString(value: number) {
    return `${Math.round(value * 10_000) / 100} %`
}

const DonutChart: React.FunctionComponent<{ chart: BreakdownChart }> = props => {
    const totalUsage = props.chart.chart.points.reduce((prev, current) => prev + current.value, 0);
    return (
        <DashboardCard
            height="400px"
            width={"400px"}
            color="purple"
            title={productTypeToTitle(props.chart.type)}
            icon={productTypeToIcon(props.chart.type)}
        >
            <Text color="darkGray" fontSize={1}>Usage across different products</Text>

            <Flex justifyContent={"center"}>
                <PieChart width={215} height={215}>
                    <Pie
                        data={props.chart.chart.points}
                        fill="#8884d8"
                        dataKey="value"
                        innerRadius={55}
                    >
                        {props.chart.chart.points.map((_, index) => (
                            <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])}/>
                        ))}
                    </Pie>
                </PieChart>
            </Flex>

            <Flex pb="12px" style={{overflowX: "scroll"}} justifyContent={"center"}>
                {props.chart.chart.points.map((it, index) =>
                    <Box mx="4px" width="auto" style={{whiteSpace: "nowrap"}} key={it.name}>
                        <Text textAlign="center" fontSize="14px">{it.name}</Text>
                        <Text
                            textAlign="center"
                            color={getCssVar(COLORS[index % COLORS.length])}
                        >
                            {toPercentageString(it.value / totalUsage)}
                        </Text>
                    </Box>
                )}
            </Flex>
        </DashboardCard>
    )
}

export default Resources;