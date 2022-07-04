import {browseWallets, ChargeType, explainAllocation, normalizeBalanceForFrontend, ProductPriceUnit, productTypes, productTypeToIcon, usageExplainer, Wallet, WalletAllocation} from "@/Accounting";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import MainContainer from "@/MainContainer/MainContainer";
import {useRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {PageV2} from "@/UCloud";
import {Box, Flex, Grid, Icon, Link, Text} from "@/ui-components";
import HighlightedCard from "@/ui-components/HighlightedCard";
import * as React from "react";
import {useCallback, useState} from "react";
import {browseSubAllocations, searchSubAllocations, SubAllocation, useProjectManagementStatus} from ".";
import * as Heading from "@/ui-components/Heading";
import {SubAllocationViewer} from "./SubAllocations";
import format from "date-fns/format";
import {Accordion} from "@/ui-components/Accordion";
import {prettierString, timestampUnixMs} from "@/UtilityFunctions";
import {ResourceProgress} from "@/ui-components/ResourcesProgress";
import styled from "styled-components";
import {VisualizationSection} from "./Resources";
import formatDistance from "date-fns/formatDistance";

const FORMAT = "dd/MM/yyyy";

function Allocations(): JSX.Element {
    const managementStatus = useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});

    useTitle("Allocations");

    const [filters, setFilters] = useState<Record<string, string>>({showSubAllocations: "true"});
    const [allocations, fetchAllocations] = useCloudAPI<PageV2<SubAllocation>>({noop: true}, emptyPageV2);
    const [allocationGeneration, setAllocationGeneration] = useState(0);
    const [wallets, fetchWallets] = useCloudAPI<PageV2<Wallet>>({noop: true}, emptyPageV2);

    const loadMoreAllocations = useCallback(() => {
        fetchAllocations(browseSubAllocations({itemsPerPage: 250, next: allocations.data.next}));
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

    const reloadPage = useCallback(() => {
        fetchWallets(browseWallets({itemsPerPage: 50, ...filters}));
        fetchAllocations(browseSubAllocations({itemsPerPage: 250, ...filters}));
        setAllocationGeneration(prev => prev + 1);
    }, [filters]);

    React.useEffect(() => {
        reloadPage();
    }, [managementStatus.projectId])

    useRefreshFunction(reloadPage);

    const onSubAllocationQuery = useCallback((query: string) => {
        fetchAllocations(searchSubAllocations({query, itemsPerPage: 250}));
        setAllocationGeneration(prev => prev + 1);
    }, []);

    return <MainContainer
        main={<>
            <Grid gridGap="0px">
                <Wallets wallets={wallets.data.items} />
            </Grid>
            {managementStatus.allowManagement ?
                <SubAllocationViewer
                    allocations={allocations}
                    generation={allocationGeneration}
                    loadMore={loadMoreAllocations}
                    filterByAllocation={filterByAllocation}
                    filterByWorkspace={filterByWorkspace} wallets={wallets}
                    onQuery={onSubAllocationQuery} />
                : null}
        </>}
    />
}


type WalletStore = Record<keyof typeof productTypes, Wallet[]>;

const VERY_HIGH_DATE_VALUE = 99999999999999;
function Wallets(props: {wallets: Wallet[]}): JSX.Element | null {
    const [wallets, setWallets] = React.useState<WalletStore>({} as WalletStore);
    React.useEffect(() => {
        const dividedWallets = {};
        productTypes.forEach(key => dividedWallets[key] = []);
        props.wallets.forEach(wallet => {
            const productType = wallet.productType;
            dividedWallets[productType].push(wallet);
        });
        setWallets(dividedWallets as WalletStore);
    }, [props.wallets]);

    if (Object.keys(wallets).length === 0) return null;
    const nonEmptyWallets = productTypes.filter(key => wallets[key].length > 0);
    return <>
        <Heading.h3 mt="32px">Allocations by product type</Heading.h3>
        {nonEmptyWallets.length > 0 ? null : <Heading.h4 mb="12px">No Results.</Heading.h4>}
        {nonEmptyWallets.map((key, index) => {
            const walletsList: Wallet[] = wallets[key];

            let earliestExpiration = VERY_HIGH_DATE_VALUE;
            walletsList.forEach(it => it.allocations.forEach(alloc => {
                if (alloc.endDate && alloc.endDate < earliestExpiration) earliestExpiration = alloc.endDate;
            }));

            const expirationText = earliestExpiration === VERY_HIGH_DATE_VALUE ?
                "" : `Earliest expiration: ${format(earliestExpiration, FORMAT)}`;

            return <Accordion
                key={key}
                icon={productTypeToIcon(key)}
                title={prettierString(key)}
                noBorder={nonEmptyWallets.length - 1 === index}
                titleContent={<>
                    <Text color="text" mt="-2px" mr="16px">{expirationText}</Text>
                    <ProductTypeProgressBars walletsByProductTypes={walletsList} />
                </>}
            >
                <Border>
                    <SimpleWalletView wallets={walletsList} />
                </Border>
            </Accordion>
        })}
    </>;
}

function ProductTypeProgressBars(props: {walletsByProductTypes: Wallet[]}) {
    const wallets = React.useMemo(() => {
        const result = {
            DIFFERENTIAL_QUOTA: {
                PER_UNIT: [] as Wallet[],
                CREDITS_PER_MINUTE: [] as Wallet[],
                CREDITS_PER_HOUR: [] as Wallet[],
                CREDITS_PER_DAY: [] as Wallet[],
                UNITS_PER_MINUTE: [] as Wallet[],
                UNITS_PER_HOUR: [] as Wallet[],
                UNITS_PER_DAY: [] as Wallet[],
            },
            ABSOLUTE: {
                PER_UNIT: [] as Wallet[],
                CREDITS_PER_MINUTE: [] as Wallet[],
                CREDITS_PER_HOUR: [] as Wallet[],
                CREDITS_PER_DAY: [] as Wallet[],
                UNITS_PER_MINUTE: [] as Wallet[],
                UNITS_PER_HOUR: [] as Wallet[],
                UNITS_PER_DAY: [] as Wallet[],
            }
        };

        for (const w of props.walletsByProductTypes) {
            result[w.chargeType][w.unit].push(w);
        }
        return result;
    }, [props.walletsByProductTypes]);

    return <>
        <ResourceBarsByChargeType chargeType="ABSOLUTE" wallets={wallets} />
        <ResourceBarsByChargeType chargeType="DIFFERENTIAL_QUOTA" wallets={wallets} />
    </>
}

function ResourceBarsByChargeType(props: {chargeType: ChargeType; wallets: Record<ChargeType, Record<ProductPriceUnit, Wallet[]>>;}) {
    const nonEmptyWallets = React.useMemo(() => {
        const keys = Object.keys(props.wallets[props.chargeType]) as ProductPriceUnit[];
        const result: ProductPriceUnit[] = [];
        for (const key of keys) {
            if (props.wallets[props.chargeType][key].length > 0) {
                result.push(key);
            }
        }
        return result;
    }, [props.wallets]);

    const wallets = props.wallets[props.chargeType];

    return <>{nonEmptyWallets.map((it, idx, {length}) => {
        if (wallets[it].length === 0) return null;
        const total = totalUsageFromMultipleWallets(wallets[it]);
        const {unit, productType, chargeType} = wallets[it][0];
        const asPercent = resultAsPercent(total);
        const used = normalizeBalanceForFrontend(total.initialBalance - total.balance, productType, chargeType, unit, false); 
        const initial = normalizeBalanceForFrontend(total.initialBalance, productType, chargeType, unit, false); 
        const resourceProgress = `${used} / ${initial} ${explainAllocation(productType, props.chargeType, unit)} (${Math.round(asPercent)}%)`;
        return <Box key={idx} mr={idx !== length - 1 ? "4px" : undefined}>
            <ResourceProgress width={resourceProgress.length * 7.3 + "px"} height="20px" value={Math.round(asPercent)} text={resourceProgress} />
        </Box>
    })}</>
}

const Border = styled.div`
    &:not(:last-child) {
        border-bottom: 1px solid lightGrey;
    }
    padding: 12px;
`;

function SimpleWalletView(props: {wallets: Wallet[];}): JSX.Element {
    return <SimpleWalletRowWrapper>
        {props.wallets.map(wallet => {
            const total = totalUsageFromWallet(wallet);
            const asPercent = resultAsPercent(total);
            const expiration = wallet.allocations.reduce((lowest, wallet) =>
                wallet.endDate && wallet.endDate < lowest ? wallet.endDate! : lowest, VERY_HIGH_DATE_VALUE
            );
            const used = normalizeBalanceForFrontend(total.initialBalance - total.balance, wallet.productType, wallet.chargeType, wallet.unit, false);
            const initial = normalizeBalanceForFrontend(total.initialBalance, wallet.productType, wallet.chargeType, wallet.unit, false);
            const resourceProgress = `${used} / ${initial} ${explainAllocation(wallet.productType, wallet.chargeType, wallet.unit)} (${Math.round(asPercent)}%)`;
            const expirationText = expiration === VERY_HIGH_DATE_VALUE ? "" : `Earliest expiration: ${format(expiration, FORMAT)}`;
            return (
                <Accordion
                    key={wallet.paysFor.name + wallet.paysFor.provider + wallet.paysFor.title}
                    title={<Text color="text">{wallet.paysFor.name} @ {wallet.paysFor.provider}</Text>}
                    titleContent={<>
                        <Text color="text" mt="-2px" mr="12px">{expirationText}</Text>
                        <ResourceProgress width={resourceProgress.length * 7.3 + "px"} value={Math.round(asPercent)} text={resourceProgress} />
                    </>}
                >
                    <VisualizationSection><WalletViewer wallet={wallet} /></VisualizationSection>
                </Accordion>
            );
        })}
    </SimpleWalletRowWrapper>;
}

const SimpleAllocationRowWrapper = styled.div``;
const SimpleWalletRowWrapper = styled.div`
    & > ${SimpleAllocationRowWrapper}:not(&:last-child) {
        vertical-align: center;
        border-bottom: 1px solid #d3d3d3;
    }

    & > ${SimpleAllocationRowWrapper} {
        margin-top: 12px;
        padding-bottom: 10px;
        border-bottom: 0px solid black;
    }
`;

interface UsageFromWallet {
    balance: number;
    initialBalance: number;
}

function totalUsageFromMultipleWallets(wallets: Wallet[]): UsageFromWallet {
    return wallets.reduce((acc, wallet) => {
        const usage = totalUsageFromWallet(wallet);
        acc.balance += usage.balance;
        acc.initialBalance += usage.initialBalance;
        return acc;
    }, {balance: 0, initialBalance: 0});
}

export function resultAsPercent(usage: UsageFromWallet): number {
    return 100 - (usage.balance / usage.initialBalance * 100);
}

function totalUsageFromWallet(wallet: Wallet): UsageFromWallet {
    return wallet.allocations.reduce(
        (acc, it) => ({balance: acc.balance + it.balance, initialBalance: acc.initialBalance + it.initialBalance}),
        {balance: 0, initialBalance: 0}
    );
}

const WalletViewer: React.FunctionComponent<{wallet: Wallet}> = ({wallet}) => {
    return <>
        {wallet.allocations.map((it, idx) => <AllocationViewer key={idx} wallet={wallet} allocation={it} />)}
    </>
}

export const AllocationViewer: React.FunctionComponent<{
    wallet: Wallet;
    allocation: WalletAllocation;
    simple?: boolean;
}> = ({wallet, allocation, simple = true}) => {
    const url = "/project/grants/view/" + allocation.grantedIn;
    return <HighlightedCard color={"red"} width={"400px"} height="100%">
        <Flex flexDirection={"row"} alignItems={"center"} height={"100%"}>
            <Icon name={wallet.productType ? productTypeToIcon(wallet.productType) : "cubeSolid"}
                size={"54px"} mr={"16px"} />
            <Flex flexDirection={"column"} height={"100%"} width={"100%"}>
                {simple ? <Flex alignItems={"center"} mr={"-16px"}>
                    <div><b>Allocation ID: {allocation.id}</b></div>
                    <Box flexGrow={1} />
                </Flex> : <Flex alignItems={"center"} mr={"-16px"}>
                    <div><b>{wallet.paysFor.name} @ {wallet.paysFor.provider} [{allocation.id}]</b></div>
                    <Box flexGrow={1} />
                </Flex>}
                <div>{usageExplainer(allocation.balance, wallet.productType, wallet.chargeType, wallet.unit)} remaining</div>
                <div>{usageExplainer(allocation.initialBalance, wallet.productType, wallet.chargeType, wallet.unit)} allocated</div>
                <Box flexGrow={1} mt={"8px"} />
                <div><ExpiresIn startDate={allocation.startDate} endDate={allocation.endDate} /></div>
                <div> {allocation.grantedIn != null ? <><Link to={url}> Show Grant </Link> </> : null}  </div>
            </Flex>
        </Flex>
    </HighlightedCard>;
};

const ExpiresIn: React.FunctionComponent<{startDate: number, endDate?: number | null;}> = ({startDate, endDate}) => {
    const now = timestampUnixMs();
    if (now < startDate) {
        return <>Starts in {formatDistance(new Date(startDate), new Date(now))}</>;
    } else if (endDate == null) {
        return <>No expiration</>;
    } else if (now < endDate) {
        return <>Expires in {formatDistance(new Date(endDate), new Date(now))}</>;
    } else if (now > endDate) {
        return <>Expired</>;
    } else {
        return <>Expires soon</>;
    }
};

export default Allocations;