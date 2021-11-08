import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

import * as CONF from "../../site.config.json";
import {
    Area,
    AreaChart,
    Cell,
    Pie,
    PieChart,
    ResponsiveContainer,
    Tooltip,
    XAxis,
} from "recharts";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useProjectManagementStatus} from "@/Project";
import {ProjectBreadcrumbs} from "@/Project/Breadcrumbs";
import {useLoading, useTitle} from "@/Navigation/Redux/StatusActions";
import {useSidebarPage, SidebarPages} from "@/ui-components/Sidebar";
import {accounting, PageV2, PaginationRequestV2} from "@/UCloud";
import {
    CheckboxFilterWidget,
    DateRangeFilter,
    EnumFilter,
    FilterWidgetProps,
    PillProps,
    ResourceFilter,
    ValuePill
} from "@/Resource/Filter";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {capitalized, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {Box, Button, Flex, Grid, Icon, Input, Label, Link, Text} from "@/ui-components";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import styled from "styled-components";
import ProductCategoryId = accounting.ProductCategoryId;
import {formatDistance} from "date-fns";
import {apiBrowse, APICallState, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {Operation, Operations, useOperationOpener} from "@/ui-components/Operation";
import * as Pagination from "@/Pagination";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "@/ui-components/Table";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import ReactDatePicker from "react-datepicker";
import {enGB} from "date-fns/locale";
import {SlimDatePickerWrapper} from "@/ui-components/DatePicker";
import {getStartOfDay} from "@/Utilities/DateUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {deviceBreakpoint} from "@/ui-components/Hide";
import {
    browseWallets,
    ChargeType, deposit,
    explainAllocation,
    normalizeBalanceForBackend,
    normalizeBalanceForFrontend,
    ProductPriceUnit,
    ProductType,
    productTypes,
    productTypeToIcon,
    productTypeToTitle,
    retrieveBreakdown, retrieveRecipient,
    retrieveUsage, transfer, TransferRecipient,
    updateAllocation, UsageChart,
    usageExplainer,
    Wallet,
    WalletAllocation,
} from "@/Accounting";
import {InputLabel} from "@/ui-components/Input";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {BrowseType} from "@/Resource/BrowseType";

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
    useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    const [filters, setFilters] = useState<Record<string, string>>({showSubAllocations: "true"});
    const [usage, fetchUsage] = useCloudAPI<{charts: UsageChart[]}>({noop: true}, {charts: []});
    const [breakdowns, fetchBreakdowns] = useCloudAPI<{charts: BreakdownChart[]}>({noop: true}, {charts: []});
    const [wallets, fetchWallets] = useCloudAPI<PageV2<Wallet>>({noop: true}, emptyPageV2);
    const [allocations, fetchAllocations] = useCloudAPI<PageV2<SubAllocation>>({noop: true}, emptyPageV2);
    const [allocationGeneration, setAllocationGeneration] = useState(0);

    const [maximizedUsage, setMaximizedUsage] = useState<number | null>(null);

    const onUsageMaximize = useCallback((idx: number) => {
        if (maximizedUsage == null) setMaximizedUsage(idx);
        else setMaximizedUsage(null);
    }, [maximizedUsage]);

    const reloadPage = useCallback(() => {
        fetchUsage(retrieveUsage({...filters}));
        fetchBreakdowns(retrieveBreakdown({...filters}));
        fetchWallets(browseWallets({itemsPerPage: 50, ...filters}));
        fetchAllocations(browseSubAllocations({itemsPerPage: 50, ...filters}));
        setAllocationGeneration(prev => prev + 1);
        setMaximizedUsage(null);
    }, [filters]);

    const loadMoreAllocations = useCallback(() => {
        fetchAllocations(browseSubAllocations({itemsPerPage: 50, next: allocations.data.next}));
    }, [allocations.data]);

    const filterByAllocation = useCallback((allocationId: string) => {
        setFilters(prev => ({...prev, "filterAllocation": allocationId}))
    }, [setFilters]);
    const filterByWorkspace = useCallback((workspaceId: string, workspaceIsProject: boolean) => {
        setFilters(prev => ({
            ...prev,
            "filterWorkspace": workspaceId,
            "filterWorkspaceProject": workspaceIsProject.toString()
        }));
    }, [setFilters]);

    useTitle("Usage");
    useSidebarPage(SidebarPages.Projects);
    useRefreshFunction(reloadPage);
    useEffect(reloadPage, []);
    useLoading(usage.loading || breakdowns.loading || wallets.loading);

    const usageClassName = usage.data.charts.length > 3 ? "large" : "slim";
    const walletsClassName = wallets.data.items.reduce((prev, current) => prev + current.allocations.length, 0) > 3 ?
        "large" : "slim";
    const breakdownClassName = breakdowns.data.charts.length > 3 ? "large" : "slim";

    return (
        <MainContainer
            header={
                <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Resources"}]} />
            }
            headerSize={60}
            sidebar={<>
                <ResourceFilter
                    browseType={BrowseType.MainContent}
                    pills={filterPills}
                    filterWidgets={filterWidgets}
                    sortEntries={[]}
                    properties={filters}
                    setProperties={setFilters}
                    sortDirection={"ascending"}
                    onSortUpdated={doNothing}
                    onApplyFilters={reloadPage}
                />
            </>}
            main={<ResourcesGrid>
                <Grid gridGap={"16px"}>
                    {maximizedUsage == null ? null : <>
                        <UsageChartViewer maximized c={usage.data.charts[maximizedUsage]}
                            onMaximizeToggle={() => onUsageMaximize(maximizedUsage)} />
                    </>}
                    {maximizedUsage != null ? null :
                        <>
                            <VisualizationSection className={usageClassName}>
                                {usage.data.charts.map((it, idx) =>
                                    <UsageChartViewer key={idx} c={it} onMaximizeToggle={() => onUsageMaximize(idx)} />
                                )}
                            </VisualizationSection>
                            <VisualizationSection className={walletsClassName}>
                                {wallets.data.items.map((it, idx) =>
                                    <WalletViewer key={idx} wallet={it} />
                                )}
                            </VisualizationSection>
                            <VisualizationSection className={breakdownClassName}>
                                {breakdowns.data.charts.map((it, idx) =>
                                    <DonutChart key={idx} chart={it} />
                                )}
                            </VisualizationSection>

                            <SubAllocationViewer allocations={allocations} generation={allocationGeneration}
                                                 loadMore={loadMoreAllocations} filterByAllocation={filterByAllocation}
                                                 filterByWorkspace={filterByWorkspace} />
                        </>
                    }
                </Grid>
            </ResourcesGrid>}
        />
    );
};


const WalletViewer: React.FunctionComponent<{wallet: Wallet}> = ({wallet}) => {
    return <>
        {wallet.allocations.map((it, idx) => <AllocationViewer key={idx} wallet={wallet} allocation={it} />)}
    </>
}

const AllocationViewer: React.FunctionComponent<{
    wallet: Wallet;
    allocation: WalletAllocation;
}> = ({wallet, allocation}) => {
    const [opRef, onContextMenu] = useOperationOpener();
    const [isDeposit, setIsDeposit] = useState(false);
    const [isMoving, setIsMoving] = useState(false);
    const closeDepositing = useCallback(() => setIsMoving(false), []);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const openMoving = useCallback((isDeposit: boolean) => {
        setIsDeposit(isDeposit);
        setIsMoving(true);
    }, []);
    const callbacks = useMemo(() => ({
        openMoving
    }), [openMoving]);

    const onTransferSubmit = useCallback(async (workspaceId: string, isProject: boolean, amount: number,
        startDate: number, endDate: number) => {
        if (isDeposit) {
            await invokeCommand(deposit(bulkRequestOf({
                amount,
                startDate,
                endDate,
                recipient: {
                    type: isProject ? "project" : "user",
                    projectId: workspaceId,
                    username: workspaceId
                },
                description: "Manually initiated " + isDeposit ? "deposit" : "transfer",
                sourceAllocation: allocation.id
            })));
        } else {
            await invokeCommand(transfer(bulkRequestOf({
                amount,
                startDate,
                endDate,
                source: wallet.owner,
                categoryId: wallet.paysFor,
                target: {
                    type: isProject ? "project" : "user",
                    projectId: workspaceId,
                    username: workspaceId
                },

            })));
        }

        setIsMoving(false);
    }, [isDeposit]);
    const url = "/project/grants/view/" + allocation.grantedIn;
    return <HighlightedCard color={"red"} width={"400px"} onContextMenu={isMoving ? undefined : onContextMenu}>
        <TransferDepositModal isDeposit={isDeposit} isOpen={isMoving} onRequestClose={closeDepositing}
            onSubmit={onTransferSubmit} wallet={wallet} />
        <Flex flexDirection={"row"} alignItems={"center"} height={"100%"}>
            <Icon name={wallet.productType ? productTypeToIcon(wallet.productType) : "cubeSolid"}
                size={"54px"} mr={"16px"} />
            <Flex flexDirection={"column"} height={"100%"} width={"100%"}>
                <Flex alignItems={"center"} mr={"-16px"}>
                    <div><b>{wallet.paysFor.name} / {wallet.paysFor.provider}</b></div>
                    <Box flexGrow={1} />
                    <Operations
                        openFnRef={opRef}
                        location={"IN_ROW"}
                        operations={allocationOperations}
                        selected={[]}
                        row={{wallet, allocation}}
                        extra={callbacks}
                        entityNameSingular={"Allocation"}
                    />
                </Flex>
                <div>{usageExplainer(allocation.balance, wallet.productType, wallet.chargeType, wallet.unit)} remaining</div>
                <div>{usageExplainer(allocation.initialBalance, wallet.productType, wallet.chargeType, wallet.unit)} allocated</div>
                <Box flexGrow={1} mt={"8px"} />
                <div><ExpiresIn startDate={allocation.startDate} endDate={allocation.endDate} /></div>
                <div> { allocation.grantedIn != null ? <><Link to={url} > Show Grant </Link> </> : <> Unknown Grant </>}  </div>
            </Flex>
        </Flex>
    </HighlightedCard>;
};

interface AllocationCallbacks {
    openMoving: (isDeposit: boolean) => void;
}

const allocationOperations: Operation<{wallet: Wallet, allocation: WalletAllocation}, AllocationCallbacks>[] = [{
    text: "Transfer to...",
    icon: "move",
    enabled: selected => selected.length === 1,
    onClick: (selected, cb) => cb.openMoving(false)
}, {
    text: "Deposit into...",
    icon: "grant",
    enabled: selected => selected.length === 1,
    onClick: (selected, cb) => cb.openMoving(true)
}];

const transferModalStyle = {content: {...defaultModalStyle.content, width: "480px", height: "550px"}};

const TransferDepositModal: React.FunctionComponent<{
    isDeposit: boolean;
    isOpen: boolean;
    onRequestClose: () => void;
    wallet: Wallet;
    onSubmit: (recipientId: string, recipientIsProject: boolean, amount: number, startDate: number, endDate: number) => void;
}> = ({isDeposit, isOpen, onRequestClose, wallet, onSubmit}) => {
    const [recipient, setRecipient] = useState<TransferRecipient | null>(null);
    const [lookingForRecipient, setLookingForRecipient] = useState(false);
    const [recipientQuery, fetchRecipient] = useCloudAPI<TransferRecipient | null>({noop: true}, null);
    const recipientQueryField = useRef<HTMLInputElement>(null);
    const amountField = useRef<HTMLInputElement>(null);
    const onRecipientQuery = useCallback((e) => {
        e.preventDefault();
        fetchRecipient(retrieveRecipient({query: recipientQueryField.current?.value ?? ""}));
    }, []);
    const onRecipientConfirm = useCallback(() => {
        if (recipientQuery.data) {
            setRecipient(recipientQuery.data);
            setLookingForRecipient(false);
        }
    }, [recipientQuery]);
    const close = useCallback(() => {
        setRecipient(null);
        setLookingForRecipient(false);
        onRequestClose();
    }, [onRequestClose]);

    const [createdAfter, setCreatedAfter] = useState(getStartOfDay(new Date()).getTime());
    const [createdBefore, setCreatedBefore] = useState<number | undefined>(undefined);
    const updateDates = useCallback((dates: [Date, Date] | Date) => {
        if (Array.isArray(dates)) {
            const [start, end] = dates;
            const newCreatedAfter = start.getTime();
            const newCreatedBefore = end?.getTime();
            setCreatedAfter(newCreatedAfter);
            setCreatedBefore(newCreatedBefore);
        } else {
            const newCreatedAfter = dates.getTime();
            setCreatedAfter(newCreatedAfter);
            setCreatedBefore(undefined);
        }
    }, []);

    const doSubmit = useCallback(() => {
        if (recipient && createdBefore) {
            const amount = normalizeBalanceForBackend(parseInt(amountField.current?.value ?? "0"),
                wallet.productType, wallet.chargeType, wallet.unit);
            onSubmit(recipient.id, recipient.isProject, amount, createdAfter, createdBefore);
        } else {
            if (!recipient) snackbarStore.addFailure("Missing recipient", false);
            if (!createdBefore) snackbarStore.addFailure("The allocation is missing an end-date", false);
        }
    }, [onSubmit, createdAfter, createdBefore, recipient]);

    return <ReactModal
        isOpen={isOpen}
        onRequestClose={close}
        shouldCloseOnEsc
        ariaHideApp={false}
        style={transferModalStyle}
    >
        {lookingForRecipient ? null :
            <Grid gridGap={16}>
                <div>
                    <Label>Recipient:</Label>
                    {recipient == null ? "None" : <>
                        <Icon name={recipient.isProject ? "projects" : "user"} mr={8}
                            color={"iconColor"} color2={"iconColor2"} />
                        {recipient.title}
                    </>}
                    <Icon name={"edit"} color={"iconColor"} color2={"iconColor2"} size={16} cursor={"pointer"}
                        onClick={() => setLookingForRecipient(true)} ml={8} />
                </div>

                <Label>
                    Amount:
                    <Flex>
                        <Input ref={amountField} rightLabel />
                        <InputLabel rightLabel>
                            {explainAllocation(wallet.productType, wallet.chargeType, wallet.unit)}
                        </InputLabel>
                    </Flex>
                </Label>

                <div>
                    <Label>Allocation Period:</Label>
                    <SlimDatePickerWrapper>
                        <ReactDatePicker
                            locale={enGB}
                            startDate={new Date(createdAfter)}
                            endDate={createdBefore ? new Date(createdBefore) : undefined}
                            onChange={updateDates}
                            selectsRange={true}
                            inline
                            dateFormat="dd/MM/yy HH:mm"
                        />
                    </SlimDatePickerWrapper>
                </div>

                <ConfirmationButton actionText={isDeposit ? "Deposit" : "Transfer"} icon={isDeposit ? "grant" : "move"}
                    onAction={doSubmit} />
            </Grid>
        }
        {!lookingForRecipient ? null : <Grid gridGap={16}>
            <div>
                <p>
                    Enter the
                    <Icon name={"id"} size={16} mx={8} color={"iconColor"} color2={"iconColor2"} />
                    of the user, if the recipient is a personal workspace. Otherwise, enter the
                    <Icon name={"projects"} size={16} mx={8} color={"iconColor"} color2={"iconColor2"} />.
                </p>

                <p>
                    The recipient can find this information in the lower-left corner of the
                    {" "}{CONF.PRODUCT_NAME} interface.
                </p>
            </div>

            <form onSubmit={onRecipientQuery}>
                <Label>
                    Recipient:
                    <Input ref={recipientQueryField} />
                </Label>
                <Button my={16} fullWidth type={"submit"}>Validate</Button>
            </form>

            {!recipientQuery.error ? null : <>
                {recipientQuery.error.why}
            </>}

            {!recipientQuery.data ? null : <>
                <div><b>Workspace: </b> {recipientQuery.data?.title}</div>
                <div><b>Principal Investigator: </b> {recipientQuery.data?.principalInvestigator}</div>
                <div><b>Number of members: </b> {recipientQuery.data?.numberOfMembers}</div>
                <Button fullWidth color={"green"} onClick={onRecipientConfirm}>Use this recipient</Button>
            </>}
        </Grid>}
    </ReactModal>
}

const ExpiresIn: React.FunctionComponent<{startDate: number, endDate?: number | null;}> = ({startDate, endDate}) => {
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

    return <HighlightedCard color={"blue"} width={maximized ? "100%" : "400px"} height={maximized ? "900px" : undefined}>
        <UsageChartStyle>
            <Flex alignItems={"center"}>
                <div>
                    <Text color="gray">{productTypeToTitle(c.type)}</Text>
                    <Text bold my="-6px"
                        fontSize="24px">{usageExplainer(c.periodUsage, c.type, c.chargeType, c.unit)} used</Text>
                </div>
                <Box flexGrow={1} />
                <Icon name={"fullscreen"} cursor={"pointer"} onClick={onMaximizeToggle} />
            </Flex>

            <ResponsiveContainer className={"usage-chart"} height={maximized ? 800 : 170}>
                <AreaChart
                    margin={{
                        left: 0,
                        top: 4,
                        right: 0,
                        bottom: -28
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
                            strokeWidth={"2px"}
                            stroke={getCssVar(("dark" + capitalized(COLORS[index % COLORS.length]) as ThemeColor))}
                            fill={getCssVar(COLORS[index % COLORS.length])}
                        />
                    )}
                </AreaChart>
            </ResponsiveContainer>
        </UsageChartStyle>
    </HighlightedCard>
};

const COLORS: [ThemeColor, ThemeColor, ThemeColor, ThemeColor, ThemeColor] = ["green", "red", "blue", "orange", "yellow"];

interface BreakdownChart {
    type: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;
    chart: {points: {name: string, value: number}[]}
}

function toPercentageString(value: number) {
    return `${Math.round(value * 10_000) / 100} %`
}

const DonutChart: React.FunctionComponent<{chart: BreakdownChart}> = props => {
    const totalUsage = props.chart.chart.points.reduce((prev, current) => prev + current.value, 0);
    return (
        <HighlightedCard
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
                            <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])} />
                        ))}
                    </Pie>
                </PieChart>
            </Flex>

            <Flex pb="12px" style={{overflowX: "auto"}} justifyContent={"center"}>
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
        </HighlightedCard>
    )
}

interface SubAllocation {
    id: string;
    startDate: number;
    endDate?: number | null;

    productCategoryId: ProductCategoryId;
    productType: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;

    workspaceId: string;
    workspaceTitle: string;
    workspaceIsProject: boolean;

    remaining: number;
}

function browseSubAllocations(request: PaginationRequestV2): APICallParameters {
    return apiBrowse(request, "/api/accounting/wallets", "subAllocation");
}

const SubAllocationViewer: React.FunctionComponent<{
    allocations: APICallState<PageV2<SubAllocation>>;
    generation: number;
    loadMore: () => void;
    filterByAllocation: (allocationId: string) => void;
    filterByWorkspace: (workspaceId: string, isProject: boolean) => void;
}> = ({allocations, loadMore, generation, filterByAllocation, filterByWorkspace}) => {
    const [editingAllocation, setEditingAllocation] = useState<SubAllocation | null>(null);
    const closeEditing = useCallback(() => setEditingAllocation(null), []);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const cb: SubAllocationCallbacks = useMemo(() => ({
        editAllocation: (allocation) => setEditingAllocation(allocation),
        filterByAllocation,
        filterByWorkspace
    }), [filterByAllocation, filterByWorkspace])

    const onSubmit: (newBalance: number, newStartDate: number, newEndDate: number) => void =
        useCallback(async (newBalance, newStartDate, newEndDate) => {
            if (editingAllocation) {
                await invokeCommand(updateAllocation(bulkRequestOf({
                    startDate: newStartDate,
                    endDate: newEndDate,
                    balance: newBalance,
                    id: editingAllocation.id,
                    reason: "Manual update by grant giver"
                })));

                closeEditing();
            }
        }, [editingAllocation, closeEditing]);

    return <HighlightedCard color={"green"} title={"Sub-allocations"} icon={"grant"}>
        <Text color="darkGray" fontSize={1}>
            An overview of workspaces which have received a <i>grant</i> or a <i>deposit</i> from you
        </Text>

        {!editingAllocation ? null :
            <SubAllocationEditModal allocation={editingAllocation} onSubmit={onSubmit} onClose={closeEditing} />
        }

        <Pagination.ListV2
            infiniteScrollGeneration={generation}
            loading={allocations.loading}
            page={allocations.data}
            onLoadMore={loadMore}
            customEmptyPage={<Box mt={"8px"}>
                You don't currently have any sub-allocations. You can create one by right-clicking on of your existing
                allocations and selecting <i>"Deposit into..."</i>.
            </Box>}
            pageRenderer={(page: SubAllocation[]) => {
                return <Table mt={"8px"}>
                    <TableHeader>
                        <TableRow>
                            <TableHeaderCell textAlign={"left"}>Workspace</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Category</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Remaining</TableHeaderCell>
                            <TableHeaderCell textAlign={"left"}>Active</TableHeaderCell>
                            <TableHeaderCell width={"35px"} />
                        </TableRow>
                    </TableHeader>
                    <tbody>
                        {page.map((row, idx) => <SubAllocationRow key={idx} row={row} cb={cb} />)}
                    </tbody>
                </Table>;
            }}
        />
    </HighlightedCard>;
};

const SubAllocationRow: React.FunctionComponent<{row: SubAllocation, cb: SubAllocationCallbacks}> = ({row, cb}) => {
    const [opRef, onContextMenu] = useOperationOpener()

    return <TableRow onContextMenu={onContextMenu} highlightOnHover>
        <TableCell>
            <Icon name={row.workspaceIsProject ? "projects" : "user"} mr={"8px"} color={"iconColor"}
                color2={"iconColor2"} />
            {row.workspaceTitle}
        </TableCell>
        <TableCell>{row.productCategoryId.name} / {row.productCategoryId.provider}</TableCell>
        <TableCell>{usageExplainer(row.remaining, row.productType, row.chargeType, row.unit)}</TableCell>
        <TableCell><ExpiresIn startDate={row.startDate} endDate={row.endDate} /></TableCell>
        <TableCell>
            <Operations
                openFnRef={opRef}
                location={"IN_ROW"}
                row={row}
                operations={subAllocationOperations}
                selected={[]}
                extra={cb}
                entityNameSingular={"Allocation"}
            />
        </TableCell>
    </TableRow>
};

interface SubAllocationCallbacks {
    filterByAllocation: (allocationId: string) => void;
    filterByWorkspace: (workspaceId: string, isProject: boolean) => void;
    editAllocation: (allocation: SubAllocation) => void;
}

const subAllocationOperations: Operation<SubAllocation, SubAllocationCallbacks>[] = [{
    icon: "filterSolid",
    text: "Focus on allocation",
    onClick: (selected, cb) => cb.filterByAllocation(selected[0].id),
    enabled: selected => selected.length === 1
}, {
    icon: "filterSolid",
    text: "Focus on workspace",
    onClick: (selected, cb) => cb.filterByWorkspace(selected[0].workspaceId, selected[0].workspaceIsProject),
    enabled: selected => selected.length === 1
}, {
    icon: "edit",
    text: "Edit",
    onClick: (selected, cb) => cb.editAllocation(selected[0]),
    enabled: selected => selected.length === 1
}];

const SubAllocationEditModal: React.FunctionComponent<{
    allocation: SubAllocation;
    onClose: () => void;
    onSubmit: (newBalance: number, newStartDate: number, newEndDate: number) => void;
}> = props => {

    const balanceField = useRef<HTMLInputElement>(null);
    const [startDate, setStartDate] = useState(props.allocation.startDate);
    const [endDate, setEndDate] = useState<number | undefined>(props.allocation.endDate ?? undefined);
    const updateDates = useCallback((dates: [Date, Date] | Date) => {
        if (Array.isArray(dates)) {
            const [start, end] = dates;
            const newCreatedAfter = start.getTime();
            const newCreatedBefore = end?.getTime();
            setStartDate(newCreatedAfter);
            setEndDate(newCreatedBefore);
        } else {
            const newCreatedAfter = dates.getTime();
            setStartDate(newCreatedAfter);
            setEndDate(undefined);
        }
    }, [])

    const doSubmit = useCallback((e?: React.SyntheticEvent) => {
        e?.preventDefault();
        const newBalance = parseInt(balanceField.current?.value ?? "NaN");
        if (isNaN(newBalance)) {
            snackbarStore.addFailure("Invalid balance", false);
            return;
        }
        if (endDate == null) {
            snackbarStore.addFailure("Allocation is missing an end-date", false);
            return;
        }

        const normalizedBalance = normalizeBalanceForBackend(newBalance, props.allocation.productType,
            props.allocation.chargeType, props.allocation.unit);
        props.onSubmit(normalizedBalance, startDate, endDate);
    }, [props.onSubmit, startDate, endDate]);

    return <ReactModal
        isOpen={true}
        onRequestClose={props.onClose}
        shouldCloseOnEsc
        ariaHideApp={false}
        style={transferModalStyle}
    >
        <form onSubmit={doSubmit}>
            <Grid gridGap={"16px"}>
                <Label>
                    Balance:
                    <Flex>
                        <Input
                            rightLabel
                            ref={balanceField}
                            defaultValue={
                                normalizeBalanceForFrontend(
                                    props.allocation.remaining,
                                    props.allocation.productType,
                                    props.allocation.chargeType,
                                    props.allocation.unit,
                                    false,
                                    0
                                )
                            }
                        />
                        <InputLabel rightLabel>
                            {explainAllocation(props.allocation.productType, props.allocation.chargeType,
                                props.allocation.unit)}
                        </InputLabel>
                    </Flex>
                </Label>

                <div>
                    <Label>Allocation Period:</Label>
                    <SlimDatePickerWrapper>
                        <ReactDatePicker
                            locale={enGB}
                            startDate={new Date(startDate)}
                            endDate={endDate ? new Date(endDate) : undefined}
                            onChange={updateDates}
                            selectsRange={true}
                            inline
                            dateFormat="dd/MM/yy HH:mm"
                        />
                    </SlimDatePickerWrapper>
                </div>
            </Grid>

            <Button fullWidth type={"submit"}>
                <Icon name={"edit"} mr={"8px"} size={"16px"} />Update
            </Button>
        </form>
    </ReactModal>
};

export default Resources;
