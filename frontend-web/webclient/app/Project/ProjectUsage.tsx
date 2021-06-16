import {useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useState} from "react";
import {Box, Flex, Icon, Input, Relative, Text} from "ui-components";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {dispatchSetProjectAction} from "Project/Redux";
import {Area, AreaChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis} from "recharts";
import Table, {TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import {
    ProductArea,
    retrieveBalance,
    RetrieveBalanceResponse,
    retrieveQuota,
    RetrieveQuotaResponse,
    transformUsageChartForCharting,
    usage,
    UsageResponse
} from "Accounting";
import {groupSummaryRequest, membershipSearch, ProjectMember, useProjectManagementStatus} from "Project";
import {ProjectBreadcrumbs} from "Project/Breadcrumbs";
import styled from "styled-components";
import {ThemeColor} from "ui-components/theme";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Client} from "Authentication/HttpClientInstance";
import {getCssVar} from "Utilities/StyledComponentsUtilities";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "ui-components/Sidebar";
import {Dropdown} from "ui-components/Dropdown";
import {capitalized} from "UtilityFunctions";
import Grid from "ui-components/Grid";
import {HighlightedCard} from "Dashboard/Dashboard";
import {Spacer} from "ui-components/Spacer";
import {useHistory, useRouteMatch} from "react-router";
import {PaginationButtons} from "Pagination";
import * as UCloud from "UCloud";
import {emptyPage} from "DefaultObjects";
import {computeUsageInPeriod} from "./ProjectDashboard";
import {sizeToString} from "Utilities/FileUtilities";
import {GroupWithSummary} from "./GroupList";
import {format} from "date-fns";

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

const UsageHeader = styled(Flex)`
    ${Dropdown} {
        flex-shrink: 0;
    }
`;

const ProjectUsage: React.FunctionComponent<ProjectUsageOperations> = props => {
    // Currently required for swapping project/username when in use.
    const {projectId, reload} = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});


    const {field} = useRouteMatch<{field?: string}>().params;

    useTitle("Usage");
    useSidebarPage(SidebarPages.Projects);

    const duration = useState<Duration>(durationOptions[3]);
    const [durationOption, setDurationOption] = duration;
    return (
        <MainContainer
            header={
                <Box minWidth={600} width="80%" mt={30} marginLeft="auto" marginRight="auto">
                    <UsageHeader>
                        <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Usage"}]} />
                        {field ? null : <ClickableDropdown
                            trigger={
                                <BorderedFlex width="180px">
                                    <Heading.h4 ml="8px">{durationOption.text}</Heading.h4>
                                    <Icon mr="4px" ml="auto" name="chevronDown" size={16} />
                                </BorderedFlex>
                            }
                            onChange={opt => setDurationOption(durationOptions[opt])}
                            options={durationOptions.map((it, idx) => ({text: it.text, value: idx}))}
                        />}
                    </UsageHeader>
                </Box>
            }
            sidebar={null}
            main={
                <Box minWidth={600} width="80%" mt={30} marginLeft="auto" marginRight="auto">
                    <UsageVisualization duration={duration} />
                </Box>
            }
        />
    );
};

interface ValueNamePair {
    value: number;
    name: string;
}

function UsageVisualization({duration}: {duration: [Duration, React.Dispatch<React.SetStateAction<Duration>>]}) {
    const {field} = useRouteMatch<{field?: string}>().params;

    const [durationOption, setDuration] = duration;

    const [balance, fetchBalance] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {wallets: []}
    );

    const [usageResponse, fetchUsageParams] = useCloudAPI<UsageResponse>(
        {noop: true},
        {charts: []}
    );

    const [subprojects, fetchSubprojects] = useCloudAPI<Page<UCloud.project.Project>>({
        noop: true
    }, emptyPage);

    const [quota, fetchQuotaParams] = useCloudAPI<RetrieveQuotaResponse>(
        {noop: true},
        {
            quotaInBytes: 0,
            quotaInTotal: 0,
        }
    );

    const [storageView, setStorageView] = useState<"storage_gb" | "storage_price">("storage_price");

    React.useEffect(() => {
        const currentTime = new Date();
        const now = periodStartFunction(currentTime, durationOption);

        fetchBalance(retrieveBalance({includeChildren: true}));
        fetchSubprojects(UCloud.project.listSubProjects({
            itemsPerPage: 100,
            page: 0,

        }));
        fetchQuotaParams(retrieveQuota({
            path: Client.activeHomeFolder,
            includeUsage: true
        }));
        fetchUsageParams(usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }));
    }, [durationOption, Client.projectId]);

    const [projects] = useCloudAPI<Page<UCloud.project.UserProjectSummary>>(
        UCloud.project.listProjects({
            archived: true,
            itemsPerPage: 100,
            noFavorites: false,
            page: 0,
            showAncestorPath: true
        }),
        emptyPage
    );

    if (field) return (
        <DetailedView
            projects={subprojects.data}
            wallets={balance.data.wallets}
            setDuration={setDuration}
            durationOption={durationOption}
            toPage={page => fetchSubprojects(UCloud.project.listSubProjects({
                itemsPerPage: 10,
                page,
            }))}
        />
    );

    const activeWorkspace = getActiveWorkspace(projects.data);

    const computeCreditsRemaining = balance.data.wallets.filter(it => it.area === "COMPUTE").filter(it => it.wallet.id === Client.projectId).reduce((acc, it) => it.allocated + acc, 0);
    const computeCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, "COMPUTE"));
    const computeCreditsUsedInPeriod = computeUsageInPeriod(computeCharts);

    const storageCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, "STORAGE"));
    const storageUsageInPeriod = computeUsageInPeriod(storageCharts);
    const storageCreditsRemaining = balance.data.wallets.filter(it => it.area === "STORAGE").filter(it => it.wallet.id === Client.projectId).reduce((acc, it) => it.allocated + acc, 0);


    // Fill timestamps;
    const usageComputeData = usageResponse.data.charts[0]?.lines.filter(it => it.area === "COMPUTE")[0]?.points.map(it => ({time: it.timestamp})) ?? [];

    for (const chart of usageResponse.data.charts) {
        for (const line of chart.lines.filter(it => it.projectId === Client.projectId && it.area === "COMPUTE")) {
            for (const [i, point] of line.points.entries()) {
                if (usageComputeData[i] === undefined) break;
                usageComputeData[i][line.projectPath!] = (usageComputeData[i][line.projectPath!] ?? 0) + point.creditsUsed;
            }
        }
    }


    // Remove timestamps so they don't show up as a machine.
    const computeEntries = Object.keys(usageComputeData[0] ?? {}).filter(it => it !== "time");

    const storageChartPoints = storageCharts[0]?.points ?? [];

    return (
        <Grid px="auto" style={{gap: "30px 30px", justifyContent: "center", alignContent: "center"}} gridTemplateColumns="435px 435px">
            <HighlightedCard px={0} height="437px" color="green">
                {/* Storage */}
                <Spacer
                    left={
                        <Box ml="8px">
                            <Text color="gray">Storage</Text>
                            <Text bold my="-6px" fontSize="24px">{storageView === "storage_gb" ? sizeToString(quota.data.quotaUsed ?? 0) : creditFormatter(storageUsageInPeriod)} used</Text>
                            <Text fontSize="14px">Remaining {storageView === "storage_gb" ? sizeToString(quota.data.quotaInBytes) : creditFormatter(storageCreditsRemaining)}</Text>
                        </Box>
                    }
                    right={
                        <ClickableDropdown
                            trigger={<Box mr="4px" mt="4px"><Icon rotation={90} name="ellipsis" /></Box>}
                            left="-110px"
                            top="-4px"
                            options={[{text: "Storage (Size)", value: "storage_gb" as const}, {text: "Storage (DKK)", value: "storage_price" as const}]}
                            onChange={it => setStorageView(it)}
                        />
                    }
                />
                {storageChartPoints.length === 0 ? <NoEntries /> : <ResponsiveContainer height={360}>
                    <AreaChart
                        margin={{
                            left: 0,
                            top: 4,
                            right: 0,
                            bottom: -28
                        }}
                        data={storageChartPoints}
                    >
                        <XAxis dataKey="time" />
                        <Tooltip labelFormatter={getDateFormatter(durationOption)} formatter={creditFormatter} />
                        <Area type="linear" opacity={1} dataKey={activeWorkspace} strokeWidth="2px" stroke={getCssVar("darkBlue")} fill={getCssVar("blue")} />
                    </AreaChart>
                </ResponsiveContainer>}
            </HighlightedCard>
            <HighlightedCard px={0} height="437px" color="green">
                {/* Compute */}
                <Spacer
                    left={
                        <Box ml="8px">
                            <Text color="gray">Compute</Text>
                            <Text bold my="-6px" fontSize="24px">{creditFormatter(computeCreditsUsedInPeriod)} used</Text>
                            <Text fontSize="14px">Remaining {creditFormatter(computeCreditsRemaining)}</Text>
                        </Box>
                    }
                    right={null}
                />
                {computeEntries.length === 0 ? <NoEntries /> : <ResponsiveContainer height={360}>
                    <AreaChart
                        margin={{
                            left: 0,
                            top: 4,
                            right: 0,
                            bottom: -28
                        }}
                        data={usageComputeData}
                    >
                        <XAxis dataKey="time" />
                        <Tooltip labelFormatter={getDateFormatter(durationOption)} formatter={creditFormatter} />
                        {computeEntries.map((it, index) =>
                            <Area
                                key={index}
                                type="linear"
                                opacity={0.8}
                                dataKey={it}
                                strokeWidth="2px"
                                stroke={getCssVar(("dark" + capitalized(COLORS[index % COLORS.length]) as ThemeColor))}
                                fill={getCssVar(COLORS[index % COLORS.length])}
                            />
                        )}
                    </AreaChart>
                </ResponsiveContainer>}
            </HighlightedCard>
            {["STORAGE", "COMPUTE"].map(area => {
                const data = findUsageFromWallet(balance.data.wallets, area);
                return (
                    <DonutChart key={area} data={data} area={area} />
                )
            })}
            <DonutChart area="Subprojects" data={findUsageFromSubprojects(balance.data.wallets, subprojects.data)} />
            <DonutChart area="Capacity" data={[{value: quota.data.quotaInTotal, name: "Capacity"}, {value: quota.data.quotaUsed ?? 0, name: "Used"}]} />
        </Grid>
    );
}

interface MappedUsage {
    name: string;
    id: string;
    data: ValueNamePair[];
    mostUsed: string;
    balanceUsed: number;
    balanceRemaining: number;
}

function getActiveWorkspace(projects: Page<UCloud.project.UserProjectSummary>) {
    const activeProject = Client.hasActiveProject ? projects.items.find(p => p.projectId === Client.projectId ?? "") : undefined;
    return activeProject ? (activeProject.ancestorPath ? `${activeProject.ancestorPath}/` : "") + activeProject.title : Client.username!;
}

function findUsageFromSubprojectsByAreaAndMachine(wallets: UCloud.accounting.WalletBalance[], subprojects: Page<UCloud.project.Project>, area: ProductArea): MappedUsage[] {
    const walletType = Client.hasActiveProject ? "PROJECT" : "USER";
    const subprojectData: MappedUsage[] = subprojects.items.map(it => ({
        name: it.title,
        id: it.id,
        data: [] as ValueNamePair[],
        mostUsed: "None",
        balanceUsed: 0,
        balanceRemaining: 0,
    }));
    const filteredWallets = wallets.filter(it => it.area === area && it.wallet.type === walletType);

    for (const sub of subprojectData) {
        const walletsUsedByProject = filteredWallets.filter(wallet => wallet.wallet.id === sub.id);
        for (const wallet of walletsUsedByProject) {
            sub.balanceRemaining += wallet.balance;
            sub.balanceUsed += wallet.used;
            sub.data.push({name: wallet.wallet.paysFor.id, value: wallet.used});
        }
        if (sub.data.length === 0) continue;
        sub.mostUsed = sub.data.reduce((current, it) => it.value > current.value ? it : current, {name: "None", value: 0}).name;
    }
    return subprojectData;
}

function findUsageFromSubprojects(wallets: UCloud.accounting.WalletBalance[], subprojects: Page<UCloud.project.Project>) {
    const subProjectNames = subprojects.items.map(it => ({title: it.title, id: it.id}));
    const subProjectsUsage = wallets.filter(it => it.wallet.type === (Client.hasActiveProject ? "PROJECT" : "USER") && it.wallet.id !== (Client.hasActiveProject ? Client.projectId : Client.username));
    const transformedUsage: ValueNamePair[] = [];
    for (const wallet of subProjectsUsage) {
        const subprojectTitle = subProjectNames.find(it => it.id === wallet.wallet.id)?.title ?? "";
        const index = transformedUsage.findIndex(it => it.name === subprojectTitle);
        if (index === -1) {
            transformedUsage.push({name: subprojectTitle, value: wallet.used});
        } else {
            transformedUsage[index].value += wallet.used;
        }
    }
    return transformedUsage;
}

function findUsageFromWallet(wallets: UCloud.accounting.WalletBalance[], area: string) {
    const activeProject = Client.hasActiveProject;
    return wallets
        .filter(it => it.area === area && it.wallet.type === (activeProject ? "PROJECT" : "USER") &&
            it.wallet.id === (activeProject ? Client.projectId : Client.username)
        ).map(it => ({name: it.wallet.paysFor.id, value: it.used}));
}

function NoEntries() {
    return <Flex mt="40px" justifyContent="center">
        <Text color="gray" fontSize="24px">
            No usage found
        </Text>
    </Flex>
}

const COLORS: [ThemeColor, ThemeColor, ThemeColor, ThemeColor, ThemeColor] = ["green", "red", "blue", "orange", "yellow"];

function DonutChart({area, data}: {area: string; data: ValueNamePair[]}): JSX.Element | null {
    const isSubprojects = area === "Subprojects" && Client.hasActiveProject;
    const history = useHistory();

    const totalUsage = data.reduce((acc, it) => it.value + acc, 0);
    return (
        <HighlightedCard
            key={area}
            height="437px"
            onClick={isSubprojects ? () => history.push("/project/usage/subprojects") : undefined}
            color="green"
            cursor={isSubprojects ? "pointer" : undefined}
        >
            <Flex mt="14px"><Box mr="auto" /><Text fontSize="26px">{capitalized(area)}</Text><Box ml="auto" /></Flex>
            {data.length === 0 || totalUsage === 0 ? <NoEntries /> :
                <>
                    <Flex style={isSubprojects ? {cursor: "pointer"} : undefined}>
                        <Box mr="auto" />
                        <PieChart key={area} width={300} height={300}>
                            <Pie
                                data={data}
                                fill="#8884d8"
                                dataKey="value"
                                innerRadius={80}
                            >
                                {data.map((_, index) => (
                                    <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])} />
                                ))}
                            </Pie>
                        </PieChart>
                        <Box ml="auto" />
                    </Flex>
                    <Flex pb="12px" style={{overflowX: "scroll"}}>
                        <Box mr="auto" />
                        {data.map((it, index) =>
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
                        <Box ml="auto" />
                    </Flex>
                </>
            }
        </HighlightedCard>
    )
}

function DetailedView({projects, wallets, toPage, durationOption, setDuration}: {projects: Page<UCloud.project.Project>, wallets: UCloud.accounting.WalletBalance[]; toPage(p: number): void; durationOption: Duration, setDuration: React.Dispatch<React.SetStateAction<Duration>>}): JSX.Element | null {
    const searchRef = React.useRef<HTMLInputElement>(null);
    const [selected, setSelected] = React.useState("");
    const [productArea, setProductArea] = React.useState<"COMPUTE" | "STORAGE">("STORAGE");

    // On Project or Wallet change
    React.useEffect(() => {
        setSelected("");
    }, [projects, wallets]);

    const mappedData = findUsageFromSubprojectsByAreaAndMachine(wallets, projects, productArea);
    const subprojects = selected ? [mappedData.find(it => it.name === selected)!] : mappedData;
    const selectedIndex = subprojects.findIndex(it => it.name === selected);
    const totalUsage = !selected ? 0 : subprojects[selectedIndex].data.reduce((acc, element) => acc + element.value, 0);

    return (
        <>
            <Spacer
                left={
                    <>
                        <RoundedDropdown initialSelection={productArea} options={["STORAGE", "COMPUTE"]} onSelect={val => setProductArea(val)} />
                        <RoundedDropdown initialSelection={durationOption.text} options={durationOptions.map(it => it.text)} onSelect={opt => setDuration(durationOptions.find(it => it.text === opt)!)} />
                    </>
                }
                right={
                    <>
                        {/* TODO */}
                        <BorderedFlex height="38px" width="36px">
                            <Icon ml="2px" name="download" onClick={() => exportAsCSV(mappedData, durationOption.text, productArea, ";")} />
                        </BorderedFlex>
                        {/* TODO */}
                        <Input pl="32px" autoComplete="off" style={{height: "38px", border: "1px solid var(--usageGray)"}} placeholder="TODO" ref={searchRef} width="200px" />
                        <Relative left="-198px">
                            <Icon size="32px" mt="4px" name="search" color="gray" />
                        </Relative>
                    </>
                }
            />
            <Box my="30px" width="100%" borderRadius="4px" style={{border: "2px solid var(--usageGray)"}}>
                <Table>
                    <TableHeader style={{marginLeft: "10px", marginRight: "10px", borderBottom: "1px solid var(--usageGray)", borderRadius: "6px"}}>
                        <TableRow>
                            <TableHeaderCell style={{paddingLeft: "12px"}} textAlign="left">
                                Subproject
                            </TableHeaderCell>
                            <TableHeaderCell textAlign="left">
                                Product breakdown
                            </TableHeaderCell>
                            <TableHeaderCell textAlign="left">
                                Most used product
                            </TableHeaderCell>
                            <TableHeaderCell textAlign="left">
                                Balance used
                            </TableHeaderCell>
                            <TableHeaderCell textAlign="left">
                                Balance remaining
                            </TableHeaderCell>
                            <TableHeaderCell textAlign="left">
                                Active
                            </TableHeaderCell>
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {subprojects.map(it => (
                            <BorderedTableRow onClick={() => setSelected(s => s ? "" : it.name)} key={it.id}>
                                <td style={{paddingLeft: "12px"}}>{it.name}</td>
                                <td>
                                    <Box ml="30px">
                                        <PieChart width={80} height={80}>
                                            <Pie
                                                data={it.data}
                                                fill="#8884d8"
                                                dataKey="value"
                                                innerRadius={18}
                                            >
                                                {it.data.map((_, index) => (
                                                    <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])} />
                                                ))}
                                            </Pie>
                                        </PieChart>
                                    </Box>
                                </td>
                                <td>{it.mostUsed}</td>
                                <td>{creditFormatter(it.balanceUsed)}</td>
                                <td>{creditFormatter(it.balanceRemaining)}</td>
                                <td><Icon name="check" color="green" /></td>
                            </BorderedTableRow>
                        )
                        )}
                    </tbody>
                </Table>
                {!selected ? null :
                    <Flex style={{borderTop: "1px solid var(--usageGray)"}}>
                        <Icon name="close" cursor="pointer" mt="4px" ml="4px" size={24} onClick={() => setSelected("")} />

                        <Box width="33%">
                            <Box ml="calc(50% - 150px)">
                                <PieChart width={300} height={300}>
                                    <Pie
                                        data={subprojects[selectedIndex].data}
                                        fill="#8884d8"
                                        dataKey="value"
                                        innerRadius={80}
                                    >
                                        {subprojects[selectedIndex].data.map((_, index) => (
                                            <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])} />
                                        ))}
                                    </Pie>
                                </PieChart>
                            </Box>
                        </Box>
                        <Box width="33%" mt="30px">
                            <Grid style={{gap: "10px 20px"}} gridTemplateColumns="auto auto">
                                {subprojects[selectedIndex].data.map((it, index) =>
                                    <Box key={it.name}>
                                        <Text textAlign="center" fontSize="14px">{it.name}</Text>
                                        <Text
                                            textAlign="center"
                                            color={getCssVar(COLORS[index % COLORS.length])}
                                        >
                                            {toPercentageString(it.value / totalUsage)}
                                        </Text>
                                    </Box>
                                )}
                            </Grid>
                        </Box>
                        <SubprojectDetails projectId={subprojects[selectedIndex].id} />
                    </Flex>
                }
            </Box>
            <Spacer
                left={null}
                right={selected ? null : <PaginationButtons totalPages={projects.itemsInTotal / projects.itemsPerPage} currentPage={projects.pageNumber} toPage={number => {setSelected(""); toPage(number)}} />}
            />
        </>
    );
}

function exportAsCSV(
    mappedData: MappedUsage[],
    timeRange: string,
    area: string,
    delimiter: "|" | ";" | ","
): void {
    const filename = `${capitalized(area)} ${timeRange} ${format(new Date().getTime(), "HH.mm-dd-MM-yy")}.csv`;
    const fields = ["Project", "Most Used Product", "Balance used (DKK)", "Balance remaining (DKK)"];
    let text = fields.join(delimiter) + "\n";
    mappedData.forEach(data => {
        const fieldData = [
            data.name,
            data.data.length === 0 ? "None" : data.data.reduce((a, b) => a.value > b.value ? a : b, {value: 0, name: "None"}).name,
            creditFormatter(data.balanceUsed),
            creditFormatter(data.balanceRemaining),
        ];
        text += fieldData.join(delimiter) + "\n";
    });

    const element = document.createElement("a");
    element.href = `data:text/plain;charset=utf-8,${encodeURIComponent(text)}`;
    element.download = filename;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
}

function SubprojectDetails({projectId}: {projectId: string}) {
    const [members] = useCloudAPI<Page<ProjectMember>>(
        {...membershipSearch({itemsPerPage: 10, page: 0, query: ""}), projectOverride: projectId},
        emptyPage
    );

    const [groups] = useCloudAPI<Page<GroupWithSummary>>(
        {...groupSummaryRequest({itemsPerPage: 10, page: 0}), projectOverride: projectId},
        emptyPage
    );

    if (!Client.hasActiveProject) {return null;}

    return (
        <>
            <Box width="34%" style={{borderLeft: "1px solid var(--usageGray)"}}>
                <FixedHeightFlex>
                    <Text pl="12px" m="auto" width="60%">Number of members</Text> <Text m="auto" width="40%">{members.data.itemsInTotal}</Text>
                </FixedHeightFlex>
                <FixedHeightFlex>
                    <Text pl="12px" m="auto" width="60%">Number of groups</Text> <Text m="auto" width="40%">{groups.data.itemsInTotal}</Text>
                </FixedHeightFlex>
                <FixedHeightFlex>
                </FixedHeightFlex>
                <FixedHeightFlex>
                </FixedHeightFlex>
                <FixedHeightFlex>
                </FixedHeightFlex>
            </Box>
        </>
    );
}

const FixedHeightFlex = styled(Flex)`
    &:not(:last-child) {
        border-bottom: 1px solid var(--usageGray);
    }
    height: 20%;
    margin: auto;
`;

const BorderedTableRow = styled(TableRow)`
    cursor: pointer;
    &:not(:last-child) {
        border-bottom: 1px solid var(--usageGray);
    }
`;

function RoundedDropdown<T extends string>({initialSelection, options, onSelect}: {initialSelection: T; options: T[]; onSelect(val: T): void}): JSX.Element {
    const [selection, setSelection] = React.useState(initialSelection);

    return (
        <ClickableDropdown
            trigger={
                <BorderedFlex width="180px">
                    <Text fontSize="19px" ml="6px" color="black" mr={8}>{capitalized(selection)}</Text>
                    <Box mr="auto" />
                    <Icon name="chevronDown" mr="8px" size={12} />
                </BorderedFlex>}
        >
            {options.map(it => <Text key={it} onClick={() => (setSelection(it), onSelect(it))}>{capitalized(it)}</Text>)}
        </ClickableDropdown>
    )
}

const BorderedFlex = styled(Flex) <{width: string}>`
    height: 38px;
    margin-right: 15px;
    width: ${p => p.width};
    border: 1px solid var(--usageGray);
    border-radius: 4px;
    align-items: center;
`;


function toPercentageString(value: number) {
    return `${Math.round(value * 10_000) / 100} %`
}

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
