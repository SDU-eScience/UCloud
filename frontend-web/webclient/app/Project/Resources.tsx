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
    DateRangeFilter,
    EnumFilter,
    FilterWidgetProps,
    PillProps,
    ResourceFilter,
    ValuePill
} from "@/Resource/Filter";
import {MutableRefObject, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {capitalized, doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {Box, Button, Flex, Grid, Icon, Input, Label, Link, Text, Tooltip as UITooltip} from "@/ui-components";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import styled from "styled-components";
import ProductCategoryId = accounting.ProductCategoryId;
import {formatDistance} from "date-fns";
import {apiBrowse, APICallState, apiSearch, callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {Operation, Operations, useOperationOpener} from "@/ui-components/Operation";
import {default as ReactModal} from "react-modal";
import {defaultModalStyle} from "@/Utilities/ModalUtilities";
import ReactDatePicker from "react-datepicker";
import {enGB} from "date-fns/locale";
import {SlimDatePickerWrapper} from "@/ui-components/DatePicker";
import {getStartOfDay} from "@/Utilities/DateUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {
    browseWallets,
    ChargeType, deposit, DepositToWalletRequestItem,
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
    updateAllocation, UpdateAllocationRequestItem, UsageChart,
    usageExplainer,
    Wallet,
    WalletAllocation,
} from "@/Accounting";
import {InputLabel} from "@/ui-components/Input";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {BrowseType} from "@/Resource/BrowseType";
import {
    Cell as SheetCell,
    DropdownCell,
    FuzzyCell,
    Sheet, SheetDropdownOption,
    SheetRenderer,
    StaticCell,
    TextCell
} from "@/ui-components/Sheet";
import {Spacer} from "@/ui-components/Spacer";
import {ConfirmCancelButtons} from "@/UtilityComponents";
import {IconName} from "@/ui-components/Icon";

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
               showValue={true} icon={"projects"} title={"Workspace"}/>);

filterPills.push(props =>
    <ValuePill {...props} propertyName={"filterAllocation"} showValue={false} icon={"grant"} title={"Allocation"}/>);

const ResourcesGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr;
  grid-gap: 16px;
`;

const Resources: React.FunctionComponent = () => {
    useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    const [filters, setFilters] = useState<Record<string, string>>({showSubAllocations: "true"});
    const [usage, fetchUsage] = useCloudAPI<{ charts: UsageChart[] }>({noop: true}, {charts: []});
    const [breakdowns, fetchBreakdowns] = useCloudAPI<{ charts: BreakdownChart[] }>({noop: true}, {charts: []});
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

    const onSubAllocationQuery = useCallback((query: string) => {
        fetchAllocations(searchSubAllocations({query, itemsPerPage: 250}));
    }, []);

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
                <ProjectBreadcrumbs allowPersonalProject crumbs={[{title: "Resources"}]}/>
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
                                          onMaximizeToggle={() => onUsageMaximize(maximizedUsage)}/>
                    </>}
                    {maximizedUsage != null ? null :
                        <>
                            <VisualizationSection className={usageClassName}>
                                {usage.data.charts.map((it, idx) =>
                                    <UsageChartViewer key={idx} c={it} onMaximizeToggle={() => onUsageMaximize(idx)}/>
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

                            <SubAllocationViewer allocations={allocations} generation={allocationGeneration}
                                                 loadMore={loadMoreAllocations} filterByAllocation={filterByAllocation}
                                                 filterByWorkspace={filterByWorkspace} wallets={wallets}
                                                 onQuery={onSubAllocationQuery}/>
                        </>
                    }
                </Grid>
            </ResourcesGrid>}
        />
    );
};


const WalletViewer: React.FunctionComponent<{ wallet: Wallet }> = ({wallet}) => {
    return <>
        {wallet.allocations.map((it, idx) => <AllocationViewer key={idx} wallet={wallet} allocation={it}/>)}
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
                              onSubmit={onTransferSubmit} wallet={wallet}/>
        <Flex flexDirection={"row"} alignItems={"center"} height={"100%"}>
            <Icon name={wallet.productType ? productTypeToIcon(wallet.productType) : "cubeSolid"}
                  size={"54px"} mr={"16px"}/>
            <Flex flexDirection={"column"} height={"100%"} width={"100%"}>
                <Flex alignItems={"center"} mr={"-16px"}>
                    <div><b>[{allocation.id}] {wallet.paysFor.name} @ {wallet.paysFor.provider}</b></div>
                    <Box flexGrow={1}/>
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
                <Box flexGrow={1} mt={"8px"}/>
                <div><ExpiresIn startDate={allocation.startDate} endDate={allocation.endDate}/></div>
                <div> {allocation.grantedIn != null ? <><Link to={url}> Show Grant </Link> </> : <> Unknown
                    Grant </>}  </div>
            </Flex>
        </Flex>
    </HighlightedCard>;
};

interface AllocationCallbacks {
    openMoving: (isDeposit: boolean) => void;
}

const allocationOperations: Operation<{ wallet: Wallet, allocation: WalletAllocation }, AllocationCallbacks>[] = [{
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
                              color={"iconColor"} color2={"iconColor2"}/>
                        {recipient.title}
                    </>}
                    <Icon name={"edit"} color={"iconColor"} color2={"iconColor2"} size={16} cursor={"pointer"}
                          onClick={() => setLookingForRecipient(true)} ml={8}/>
                </div>

                <Label>
                    Amount:
                    <Flex>
                        <Input ref={amountField} rightLabel/>
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
                                    onAction={doSubmit}/>
            </Grid>
        }
        {!lookingForRecipient ? null : <Grid gridGap={16}>
            <div>
                <p>
                    Enter the
                    <Icon name={"id"} size={16} mx={8} color={"iconColor"} color2={"iconColor2"}/>
                    of the user, if the recipient is a personal workspace. Otherwise, enter the
                    <Icon name={"projects"} size={16} mx={8} color={"iconColor"} color2={"iconColor2"}/>.
                </p>

                <p>
                    The recipient can find this information in the lower-left corner of the
                    {" "}{CONF.PRODUCT_NAME} interface.
                </p>
            </div>

            <form onSubmit={onRecipientQuery}>
                <Label>
                    Recipient:
                    <Input ref={recipientQueryField}/>
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

const VisualizationSection = styled.div`
  display: grid;
  grid-gap: 16px;
  padding: 10px 0;
  grid-template-columns: repeat(auto-fill, 400px);
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

    return <HighlightedCard color={"blue"} width={maximized ? "100%" : "400px"}
                            height={maximized ? "900px" : undefined}>
        <UsageChartStyle>
            <Flex alignItems={"center"}>
                <div>
                    <Text color="gray">{productTypeToTitle(c.type)}</Text>
                    <Text bold my="-6px"
                          fontSize="24px">{usageExplainer(c.periodUsage, c.type, c.chargeType, c.unit)} used</Text>
                </div>
                <Box flexGrow={1}/>
                <Icon name={"fullscreen"} cursor={"pointer"} onClick={onMaximizeToggle}/>
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
                    <XAxis dataKey={"timestamp"}/>
                    <Tooltip labelFormatter={dateFormatter} formatter={formatter}/>
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
    chart: { points: { name: string, value: number }[] }
}

function toPercentageString(value: number) {
    return `${Math.round(value * 10_000) / 100} %`
}

const DonutChart: React.FunctionComponent<{ chart: BreakdownChart }> = props => {
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
                            <Cell key={`cell-${index}`} fill={getCssVar(COLORS[index % COLORS.length])}/>
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
    path: string;
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

function searchSubAllocations(request: { query: string } & PaginationRequestV2): APICallParameters {
    return apiSearch(request, "/api/accounting/wallets", "subAllocation");
}

const Circle = styled(Box)`
  border-radius: 500px;
  width: 20px;
  height: 20px;
  border: 1px solid ${getCssVar("black")};
  margin: 4px 4px 4px 8px;
  cursor: pointer;
`;

function formatTimestampForInput(timestamp: number): string {
    const d = new Date(timestamp);
    let res = "";
    res += d.getUTCFullYear().toString().padStart(4, '0');
    res += "-";
    res += (d.getMonth() + 1).toString().padStart(2, '0');
    res += "-";
    res += (d.getDate()).toString().padStart(2, '0');
    return res;
}

const subAllocationsDirtyKey = "subAllocationsDirty";
const unsavedPrefix = "unsaved";

type RowOrDeleted = "deleted" | ((string | null)[]);

function writeRow(s: SheetRenderer, row: RowOrDeleted, rowNumber: number) {
    if (row !== "deleted") {
        let col = 0;
        for (const value of row) {
            s.writeValue(col++, rowNumber, value ?? undefined);
        }
    }
}

function titleForSubAllocation(alloc: SubAllocation): string {
    return rawAllocationTitleInRow(alloc.productCategoryId.name, alloc.productCategoryId.provider);
}

function titleForWallet(wallet: Wallet): string {
    return rawAllocationTitleInRow(wallet.paysFor.name, wallet.paysFor.provider);
}

function rawAllocationTitleInRow(category: string, provider: string) {
    return `${category} @ ${provider}`;
}

function writeAllocations(
    s: SheetRenderer,
    allocations: SubAllocation[],
    dirtyRowStorage: Record<string, RowOrDeleted>,
    sessionId: number,
    unsavedRowIds: { current: number }
) {
    let row = 0;
    s.clear();

    for (const key of Object.keys(dirtyRowStorage)) {
        s.addRow(key);
        const draftCopy = dirtyRowStorage[key];
        writeRow(s, draftCopy, row);
        s.writeValue(colStatus, row, undefined);
        row++;
    }

    allocations.forEach(alloc => {
        const draftCopy = dirtyRowStorage[alloc.id];
        if (draftCopy !== "deleted") {
            if (draftCopy) return;
            s.addRow(alloc.id);
            alloc.path

            s.writeValue(colRecipientType, row, alloc.workspaceIsProject ? "PROJECT" : "USER");
            s.writeValue(colRecipient, row, alloc.workspaceTitle);
            s.writeValue(colProductType, row, alloc.productType);
            s.writeValue(colProduct, row, titleForSubAllocation(alloc));
            {
                let path = alloc.path.split(".");
                s.writeValue(colAllocation, row, path[path.length - 2]);
            }
            s.writeValue(colStartDate, row, formatTimestampForInput(alloc.startDate));
            if (alloc.endDate) s.writeValue(colEndDate, row, formatTimestampForInput(alloc.endDate));
            s.writeValue(colAmount, row, normalizeBalanceForFrontend(alloc.remaining, alloc.productType,
                alloc.chargeType, alloc.unit, false, 0).replace('.', '').toString());
            s.writeValue(colStatus, row, undefined);

            row++;
        }
    });

    s.addRow(`${unsavedPrefix}-${sessionId}-${unsavedRowIds.current++}`);
    s.writeValue(colRecipientType, row, "PROJECT");
    s.writeValue(colProductType, row, "STORAGE");
    s.writeValue(colStatus, row, undefined);
}

function parseDateFromInput(value: string): number | null {
    const splitValue = value.split("-");
    if (splitValue.length != 3) return null;
    const year = parseInt(splitValue[0], 10);
    const month = parseInt(splitValue[1], 10);
    const date = parseInt(splitValue[2], 10);

    // NOTE(Dan): Time-travel to the past is not supported by this application.
    if (year < 2020) return null;
    // NOTE(Dan): I am assuming that someone will rewrite this code before this date is relevant.
    if (year >= 2100) return null;

    if (month < 1 || month > 12) return null;

    // NOTE(Dan): Only doing some basic validation. The browser should not allow these values. The backend will reject
    // invalid values also.
    if (date < 1 || date > 31) return null;

    return new Date(year, month, date).getTime();
}

const colRecipientType: 0 = 0 as const;
const colRecipient: 1 = 1 as const;
const colProductType : 2= 2 as const;
const colProduct: 3 = 3 as const;
const colAllocation: 4 = 4 as const;
const colStartDate: 5 = 5 as const;
const colEndDate: 6 = 6 as const;
const colAmount: 7 = 7 as const;
const colUnit: 8 = 8 as const;
const colStatus: 9 = 9 as const;

const SubAllocationViewer: React.FunctionComponent<{
    wallets: APICallState<PageV2<Wallet>>;
    allocations: APICallState<PageV2<SubAllocation>>;
    generation: number;
    loadMore: () => void;
    onQuery: (query: string) => void;
    filterByAllocation: (allocationId: string) => void;
    filterByWorkspace: (workspaceId: string, isProject: boolean) => void;
}> = ({allocations, loadMore, generation, filterByAllocation, filterByWorkspace, wallets, onQuery}) => {
    const originalRows = useRef<Record<string, SubAllocation>>({});
    const projectLookupTable = useRef<Record<string, { rows: SubAllocation[]; projectId: string }[]>>({});
    const sessionId = useMemo(() => Math.ceil(Math.random() * 1000000000), []);
    const unsavedRowIds = useRef(0);
    const dirtyRowStorageRef: MutableRefObject<Record<string, RowOrDeleted>> = useRef({});
    const [dirtyRows, setDirtyRows] = useState<string[]>([]);
    const [isSaveQueued, setSaveQueued] = useState<number | null>(null);
    const loadedRecipients = useRef<Record<string, TransferRecipient | null>>({});

    useEffect(() => {
        const dirty = localStorage.getItem(subAllocationsDirtyKey);
        if (dirty != null) dirtyRowStorageRef.current = JSON.parse(dirty);
        setDirtyRows(Object.keys(dirtyRowStorageRef.current));
    }, []);

    // NOTE(Dan): Sheets are not powered by React, as a result, we must wrap the wallets such that we can pass it
    // down into the cells.
    const walletHolder = useRef<APICallState<PageV2<Wallet>>>(wallets);
    useEffect(() => {
        walletHolder.current = wallets;
    }, [wallets]);

    useEffect(() => {
        const table = projectLookupTable.current;
        const original = originalRows.current;
        for (const alloc of allocations.data.items) {
            original[alloc.id] = alloc;

            if (!alloc.workspaceIsProject) continue;
            const existing = table[alloc.workspaceTitle];
            if (existing === undefined) {
                table[alloc.workspaceTitle] = [{rows: [alloc], projectId: alloc.workspaceId}];
            } else {
                let found = false;
                for (const entry of existing) {
                    if (entry.projectId === alloc.workspaceId) {
                        entry.rows.push(alloc);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    existing.push({rows: [alloc], projectId: alloc.workspaceId});
                }
            }
        }
    }, [allocations]);

    const sheet = useRef<SheetRenderer>(null);

    const header: string[] = useMemo(() => (
            ["", "Recipient", "", "Product", "Allocation", "Start Date", "End Date", "Amount", "", ""]),
        []
    );

    const cells: SheetCell[] = useMemo(() => ([
        DropdownCell(
            [
                {icon: "projects", title: "Project", value: "PROJECT"},
                {icon: "user", title: "Personal Workspace", value: "USER"},
            ],
            {width: "50px"}
        ),
        TextCell(),
        DropdownCell(
            productTypes.map(type => ({
                icon: productTypeToIcon(type),
                title: productTypeToTitle(type),
                value: type
            })),
            {width: "50px"}
        ),
        FuzzyCell(
            (query, column, row) => {
                const currentType = sheet.current!.readValue(colProductType, row) as ProductType;
                const lq = query.toLowerCase();
                return walletHolder.current.data.items
                    .filter(it => {
                        return it.productType === currentType && it.paysFor.name.toLowerCase().indexOf(lq) != -1;
                    })
                    .map(it => ({
                        icon: productTypeToIcon(it.productType),
                        title: titleForWallet(it),
                        value: titleForWallet(it),
                    }));
            }
        ),
        FuzzyCell(
            (query, column, row) => {
                const currentProduct = sheet.current!.readValue(colProduct, row) ?? "";
                const entries: SheetDropdownOption[] = [];
                for (const wallet of walletHolder.current.data.items) {
                    let pcTitle = titleForWallet(wallet);
                    const isRelevant = currentProduct === "" ||
                        currentProduct === pcTitle;
                    if (!isRelevant) continue;
                    for (const alloc of wallet.allocations) {
                        let allocTitle = `[${alloc.id}] ${pcTitle}`;
                        if (allocTitle.indexOf(query) === -1) continue;

                        entries.push({
                            icon: productTypeToIcon(wallet.productType),
                            title: allocTitle,
                            value: alloc.id,
                            helpText: normalizeBalanceForFrontend(alloc.balance, wallet.productType, wallet.chargeType,
                                wallet.unit, false, 2) + " " +
                                explainAllocation(wallet.productType,wallet.chargeType, wallet.unit)
                        });
                    }
                }

                return entries;
            }
        ),
        TextCell("Immediately", {fieldType: "date"}),
        TextCell("No expiration", {fieldType: "date"}),
        TextCell(),
        StaticCell("DKK"),
        StaticCell(
            (sheetId, coord) => {
                const rowId = sheet.current?.retrieveRowId(coord.row);
                if (!rowId) return "questionSolid";

                if (rowId.indexOf(unsavedPrefix) === 0) {
                    return {
                        contents: "questionSolid",
                        color: "blue",
                        tooltip: "This allocation is new and has never been saved. It is not active."
                    };
                } else {
                    if (dirtyRowStorageRef.current[rowId]) {
                        return {
                            contents: "edit",
                            color: "orange",
                            tooltip: "This allocation is based on an active allocation but changes has not been saved."
                        };
                    } else {
                        return {
                            contents: "check",
                            color: "green",
                            tooltip: "This allocation is active and you have not made any changes to it."
                        };
                    }
                }
            },
            {
                iconMode: true,
                possibleIcons: ["check", "questionSolid", "edit"],
                width: "30px"
            }
        ),
    ]), []);

    useLayoutEffect(() => {
        const s = sheet.current!;
        writeAllocations(s, allocations.data.items, dirtyRowStorageRef.current, sessionId, unsavedRowIds);
    }, [allocations.data.items]);

    const [showHelp, setShowHelp] = useState(false);
    const toggleShowHelp = useCallback((ev) => {
        ev.preventDefault();
        setShowHelp(prev => !prev);
    }, []);

    const discard = useCallback(() => {
        dirtyRowStorageRef.current = {};
        localStorage.removeItem(subAllocationsDirtyKey);
        setDirtyRows([]);
        writeAllocations(sheet.current!, allocations.data.items, {}, sessionId, unsavedRowIds);
    }, [allocations.data.items]);

    const save = useCallback(() => {
        const dirtyRows = dirtyRowStorageRef.current;
        const lookup = projectLookupTable.current;

        const newRows: { update: DepositToWalletRequestItem, rowId: string }[] = [];
        const updatedRows: { update: UpdateAllocationRequestItem, rowId: string }[] = [];
        const deletedRows: { update: UpdateAllocationRequestItem, rowId: string }[] = [];
        const unknownProjects: string[] = [];

        const original = originalRows.current;
        const s = sheet.current;
        if (s === null) return;

        const nowTs = timestampUnixMs();
        const stableKeys = Object.keys(dirtyRows);
        let allValid = true;
        for (const rowId of stableKeys) {
            const row = dirtyRows[rowId];
            const originalRow = original[rowId];

            if (row === "deleted") {
                deletedRows.push({
                    rowId,
                    update: {
                        id: rowId,
                        endDate: nowTs,
                        reason: "Allocation deleted by grant giver",
                        startDate: originalRow?.startDate ?? nowTs,
                        balance: originalRow?.remaining ?? 0
                    }
                });
            } else {
                // Basic row validation
                const rowNumber = s.retrieveRowNumber(rowId);
                if (rowNumber == null) continue;

                let valid = true;

                const newAmount = row[colAmount] == null ? NaN : parseInt(row[colAmount]!);
                const newAmountValid = !isNaN(newAmount) && newAmount >= 0;
                s.registerValidation(colAmount, rowNumber, !newAmountValid ? "invalid" : undefined);
                valid = valid && newAmountValid;

                const startDate = row[colStartDate] == null ? null : parseDateFromInput(row[colStartDate]!);
                const endDate = row[colEndDate] == null ? null : parseDateFromInput(row[colEndDate]!);
                const startDateValid = startDate != null;
                const endDateValid = row[colEndDate] === "" || row[colEndDate] === null || endDate != null;
                s.registerValidation(colStartDate, rowNumber, !startDateValid ? "invalid" : undefined);
                s.registerValidation(colEndDate, rowNumber, !endDateValid ? "invalid" : undefined);
                valid = valid && startDateValid && endDateValid;

                const isProject = row[colRecipientType] === "PROJECT";
                const recipient = row[colRecipient];
                const recipientIsValid = recipient != null && recipient.length > 0;
                s.registerValidation(colRecipient, rowNumber, !recipientIsValid ? "invalid" : undefined);
                valid = valid && recipientIsValid;

                const productType = row[colProductType] as ProductType;
                const product = row[colProduct];
                const resolvedWallet = walletHolder.current.data.items.find(wallet => {
                    return titleForWallet(wallet) === product && wallet.productType === productType
                });
                const productIsValid = resolvedWallet != null;
                s.registerValidation(colProduct, rowNumber, !productIsValid ? "invalid" : undefined);
                valid = valid && productIsValid;

                const allocation = row[colAllocation];
                const allocationIsValid = resolvedWallet?.allocations?.some(it => it.id === allocation) === true;
                s.registerValidation(colAllocation, rowNumber, !allocationIsValid ? "invalid" : undefined);
                valid = valid && allocationIsValid;

                let resolvedRecipient: string | null = null;
                if (isProject && recipient != null) {
                    if (originalRow?.workspaceTitle === recipient) {
                        resolvedRecipient = originalRow.workspaceId;
                    } else {
                        const entries = projectLookupTable.current[recipient];
                        if (entries === undefined || entries.length === 0) {
                            const loaded = loadedRecipients.current[recipient];
                            if (loaded !== undefined) {
                                if (loaded === null || !loaded.isProject) {
                                    s.registerValidation(colRecipient, rowNumber, "invalid");
                                } else {
                                    resolvedRecipient = loaded.id;
                                    s.registerValidation(colRecipient, rowNumber, "valid");
                                }
                            } else {
                                s.registerValidation(colRecipient, rowNumber, "loading");
                                unknownProjects.push(recipient);
                            }
                        } else if (entries.length === 1) {
                            resolvedRecipient = entries[0].projectId;
                        } else {
                            // NOTE(Dan): In this case, we have a conflict. The user needs to specify the full path to
                            // resolve this conflict.

                            // TODO Communicate this through a tooltip
                            s.registerValidation(colRecipient, rowNumber, "warning");
                        }
                    }
                } else if (!isProject) {
                    resolvedRecipient = recipient;
                }
                valid = valid && resolvedRecipient != null;

                async function lookupRecipients() {
                    const promises: [string, Promise<TransferRecipient>][] = unknownProjects.map(p =>
                        [p, callAPI<TransferRecipient>(retrieveRecipient({query: p}))]);
                    for (const [p, promise] of promises) {
                        try {
                            const result = await promise;
                            loadedRecipients.current[p] = result;
                        } catch (e) {
                            loadedRecipients.current[p] = null;
                        }
                    }
                    setSaveQueued(q => (q ?? 0) + 1);
                }

                if (unknownProjects.length > 0) { // noinspection JSIgnoredPromiseFromCall
                    lookupRecipients();
                }

                if (!valid) {
                    allValid = false;
                    continue;
                }

                const asDeletion: UpdateAllocationRequestItem = {
                    id: rowId,
                    endDate: nowTs,
                    reason: "Allocation deleted by grant giver",
                    startDate: originalRow?.startDate ?? nowTs,
                    balance: originalRow?.remaining ?? 0
                };

                const asCreation: DepositToWalletRequestItem = {
                    recipient: (isProject ?
                            {type: "project", projectId: resolvedRecipient!} :
                            {type: "user", username: resolvedRecipient!}
                    ),
                    startDate: startDate!,
                    endDate: endDate ?? undefined,
                    description: "Allocation created manually by grant giver",
                    sourceAllocation: allocation!,
                    amount: newAmount! // TODO Normalize!
                };

                const asUpdate: UpdateAllocationRequestItem = {
                    id: originalRow?.id ?? "",
                    startDate: startDate!,
                    endDate: endDate ?? undefined,
                    balance: newAmount!, // TODO Normalize!
                    reason: "Allocation updated by grant giver"
                };

                const isNewRow = rowId.indexOf(unsavedPrefix) === 0;
                if (isNewRow) {
                    newRows.push({rowId, update: asCreation});
                } else {
                    const entityDidChange = isProject != originalRow.workspaceIsProject ||
                        resolvedRecipient != originalRow.workspaceId;

                    const oldPath = originalRow.path.split(".");
                    const oldAllocation = oldPath[oldPath.length - 2];
                    const allocationDidChange = oldAllocation !== allocation;

                    if (entityDidChange || allocationDidChange) {
                        deletedRows.push({rowId, update: asDeletion});
                        newRows.push({rowId, update: asCreation});
                    } else {
                        updatedRows.push({rowId, update: asUpdate});
                    }
                }
            }
        }

        if (!allValid) return;

        async function attemptSave(startIdx: number, endIdx: number, dry: boolean): Promise<string | null> {
            const update: UpdateAllocationRequestItem[] = [];
            const create: DepositToWalletRequestItem[] = [];

            for (let i = startIdx; i < endIdx; i++) {
                const rowId = stableKeys[i];
                for (const r of updatedRows) {
                    r.update.dry = dry;
                    if (r.rowId === rowId) update.push(r.update);
                }
                for (const r of deletedRows) {
                    r.update.dry = dry;
                    if (r.rowId === rowId) update.push(r.update);
                }
                for (const r of newRows) {
                    r.update.dry = dry;
                    if (r.rowId === rowId) create.push(r.update);
                }
            }

            const createResult: Promise<any> = create.length > 0 ? callAPI(deposit(bulkRequestOf(...create))) : Promise.resolve();
            const updateResult: Promise<any> = update.length > 0 ? callAPI(updateAllocation(bulkRequestOf(...update))) : Promise.resolve();

            try {
                await createResult;
            } catch (e) {
                return e.response?.why ?? "Update failed";
            }

            try {
                await updateResult;
            } catch (e) {
                return e.response?.why ?? "Update failed";
            }

            return null;
        }

        async function saveOrFail() {
            const rootErrorMessage = await attemptSave(0, stableKeys.length, true);
            if (rootErrorMessage == null) {
                await attemptSave(0, stableKeys.length, false);
                discard();
                // TODO refresh
            } else {
                const batchSize = Math.ceil(Math.max(1, stableKeys.length / 10));
                if (batchSize > 0) {
                    const errors: Promise<string | null>[] = [];
                    for (let i = 0; i < stableKeys.length; i += batchSize) {
                        errors.push(
                            attemptSave(
                                i,
                                Math.min(stableKeys.length, i + batchSize),
                                true
                            )
                        );
                    }
                    console.log("errors", await Promise.all(errors));
                }
            }
        }

        if (updatedRows.length > 0 || deletedRows.length > 0 || newRows.length > 0) {
            // noinspection JSIgnoredPromiseFromCall
            saveOrFail();
        }
    }, []);

    useEffect(() => {
        if (isSaveQueued != null) save();
    }, [isSaveQueued, save]);

    const onSearchInput = useCallback((ev: React.KeyboardEvent) => {
        if (ev.code === "Enter" || ev.code === "NumpadEnter") {
            const target = ev.target as HTMLInputElement;
            ev.preventDefault();
            onQuery(target.value);
        }
    }, [onQuery]);

    return <HighlightedCard
        color={"green"}
        icon={"grant"}
        title={"Sub-allocations"}
        subtitle={
            <SearchInput>
                <Input id={"resource-search"} placeholder={"Search in allocations..."} onKeyDown={onSearchInput}/>
                <Label htmlFor={"resource-search"}>
                    <Icon name={"search"} size={"20px"}/>
                </Label>
            </SearchInput>
        }
    >
        <Text color="darkGray" fontSize={1} mb={"16px"}>
            An overview of workspaces which have received a <i>grant</i> or a <i>deposit</i> from you
        </Text>

        <Flex flexDirection={"row"} mb={"16px"} alignItems={"center"}>
            <div>
                <a href="#" onClick={toggleShowHelp}>Spreadsheet help</a>
                {!showHelp ? null :
                    <ul>
                        <li><b>←, ↑, →, ↓, Home, End:</b> Movement</li>
                        <li><b>Shift + Movement Key:</b> Select multiple</li>
                        <li><b>Ctrl/Cmd + C:</b> Copy</li>
                        <li><b>Ctrl/Cmd + V:</b> Paste</li>
                        <li><b>Right click menu:</b> Insert rows, clone and more...</li>
                    </ul>
                }
            </div>
            <Box flexGrow={1}/>

            {dirtyRows.length === 0 ? <i>No unsaved changes</i> :
                <>
                    <Flex flexDirection={"row"} mr={"32px"} alignItems={"center"}>
                        <i>Viewing local draft</i>
                        <UITooltip
                            tooltipContentWidth="260px"
                            trigger={
                                <Circle>
                                    <Text mt="-3px" ml="5px">?</Text>
                                </Circle>
                            }
                        >
                            <Text textAlign={"left"}>
                                <p>
                                    Your draft has been saved. You can safely leave the page and come
                                    back later.
                                </p>

                                <p>
                                    Your changes won't take effect until you press the green <b>'Save'</b>{" "}
                                    button.
                                </p>
                            </Text>
                        </UITooltip>
                    </Flex>
                    <ConfirmCancelButtons onConfirm={save} onCancel={discard} confirmText={"Save"}
                                          cancelText={"Discard"}/>
                </>
            }
        </Flex>

        <Sheet
            header={header}
            cells={cells}
            renderer={sheet}
            newRowPrefix={unsavedPrefix + sessionId + "-sh"}
            onRowDeleted={(rowId) => {
                const storage = dirtyRowStorageRef.current;
                if (rowId.indexOf(unsavedPrefix) === 0) {
                    // NOTE(Dan): Nothing special to do, we can just delete it from storage
                    delete storage[rowId];
                } else {
                    // NOTE(Dan): We need to keep a record which indicates that we have deleted the entry.
                    storage[rowId] = "deleted";
                    setDirtyRows(rows => {
                        if (rows.indexOf(rowId) !== -1) return rows;
                        return [...rows, rowId];
                    });
                }

                localStorage.setItem(subAllocationsDirtyKey, JSON.stringify(storage));
            }}
            onRowUpdated={(rowId, row, values) => {
                const s = sheet.current!;

                const storage = dirtyRowStorageRef.current;
                storage[rowId] = values;
                localStorage.setItem(subAllocationsDirtyKey, JSON.stringify(storage));

                setDirtyRows(rows => {
                    if (rows.indexOf(rowId) !== -1) return rows;
                    s.writeValue(colStatus, row, undefined);
                    return [...rows, rowId];
                });
            }}
        />

        <Box mt={"16px"}/>

    </HighlightedCard>;
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

const SearchInput = styled.div`
  position: relative;

  label {
    position: absolute;
    left: 250px;
    top: 10px;
  }
`;

export default Resources;
