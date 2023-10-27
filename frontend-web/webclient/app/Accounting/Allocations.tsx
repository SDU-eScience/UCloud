import { injectStyle } from "@/Unstyled";
import * as React from "react";
import { Accordion, Box, Flex, Icon, Link, MainContainer, ProgressBarWithLabel, Select } from "@/ui-components";
import { ContextSwitcher } from "@/Project/ContextSwitcher";
import * as Accounting from "@/Accounting";
import { ProductCategoryV2, ProductType } from "@/Accounting";
import { groupBy } from "@/Utilities/CollectionUtilities";
import { ChangeEvent, useCallback, useEffect, useReducer } from "react";
import { useProjectId } from "@/Project/Api";
import { useDidUnmount } from "@/Utilities/ReactUtilities";
import { callAPI, callAPIWithErrorHandler } from "@/Authentication/DataHook";
import { fetchAll } from "@/Utilities/PageUtilities";
import AppRoutes from "@/Routes";
import { ProviderLogo } from "@/Providers/ProviderLogo";
import { Avatar } from "@/AvataaarLib";
import { defaultAvatar } from "@/UserSettings/Avataaar";
import { TooltipV2 } from "@/ui-components/Tooltip";

// State
// =====================================================================================================================
interface State {
    remoteData: {
        wallets: Accounting.WalletV2[];
        subAllocations: Accounting.SubAllocationV2[];
    };

    periodSelection: {
        currentPeriodIdx: number;
        availablePeriods: Period[];
    };

    yourAllocations: {
        [P in ProductType]?: {
            usageAndQuota: UsageAndQuota[];
            wallets: {
                category: Accounting.ProductCategoryV2;
                usageAndQuota: UsageAndQuota;

                allocations: {
                    id: string;
                    grantedIn?: string;
                    usageAndQuota: UsageAndQuota;
                }[];
            }[];
        }
    };

    subAllocations: {
        searchQuery: string;

        recipients: {
            owner: {
                title: string;
                primaryUsername: string;
                reference: Accounting.WalletOwner;
            };

            usageAndQuota: (UsageAndQuota & { type: Accounting.ProductType })[];

            allocations: {
                // NOTE(Dan): If allocationId is undefined, then the allocation doesn't exist and the usage number
                // from usageAndQuota should be ignored as a result.
                allocationId?: string;
                usageAndQuota: UsageAndQuota;
                category: Accounting.ProductCategoryV2;
            }[];
        }[];
    };
}

interface UsageAndQuota {
    usage: number;
    quota: number;
    unit: string;
}

interface Period {
    start: number;
    end: number;
}

function periodToString(period: Period): string {
    const start = new Date(period.start);
    const end = new Date(period.end);

    if (start.getUTCMonth() === 0 && start.getUTCDate() === 1 && end.getUTCMonth() === 11 && end.getUTCDate() === 31) {
        if (start.getUTCFullYear() === end.getUTCFullYear()) {
            return start.getFullYear().toString();
        } else {
            return `${start.getUTCFullYear()}-${end.getUTCFullYear()}`;
        }
    }

    return Accounting.utcDate(period.start) + "-" + Accounting.utcDate(period.end);
}

// State reducer
// =====================================================================================================================
type UIAction =
    { type: "WalletsLoaded", wallets: Accounting.WalletV2[]; }
    | { type: "PeriodUpdated", selectedIndex: number }
    | { type: "SubAllocationsLoaded", subAllocations: Accounting.SubAllocationV2[] }
    | { type: "UpdateSearchQuery", newQuery: string }
    ;

function stateReducer(state: State, action: UIAction): State {
    switch (action.type) {
        case "WalletsLoaded": {
            const newState = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    wallets: action.wallets,
                }
            };

            return initializePeriods(newState);
        }

        case "SubAllocationsLoaded": {
            const newState = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    subAllocations: action.subAllocations,
                }
            };

            return initializePeriods(newState);
        }

        case "PeriodUpdated": {
            return selectPeriod(state, action.selectedIndex);
        }

        case "UpdateSearchQuery": {
            const newState = {
                ...state,
                subAllocations: {
                    ...state.subAllocations,
                    searchQuery: action.newQuery
                },
            };

            // TODO Do a bit of filtering here.
            return newState;
        }
    }

    // Utility functions for mutating state
    // -----------------------------------------------------------------------------------------------------------------
    function getOrNull<T>(arr: T[], idx: number): T | null {
        if (idx >= 0 && idx < arr.length) return arr[idx];
        return null;
    }

    function initializePeriods(state: State): State {
        const periods: Period[] = [];
        for (const wallet of state.remoteData.wallets) {
            for (const alloc of wallet.allocations) {
                const p: Period = {start: alloc.startDate, end: alloc.endDate ?? Number.MAX_SAFE_INTEGER};
                if (!periods.some(it => it.start === p.start && it.end === p.end)) {
                    periods.push(p);
                }
            }
        }

        // Sort the periods just in case they don't come ordered from the backend
        periods.sort((a, b) => {
            if (a.start < b.start) return -1;
            if (a.start > b.start) return 1;
            if (a.end < b.end) return -1;
            if (a.end > b.end) return 1;
            return 0;
        });

        const oldPeriod = getOrNull(state.periodSelection.availablePeriods, state.periodSelection.currentPeriodIdx);

        let selectedIndex = -1;
        if (oldPeriod) selectedIndex = periods.findIndex(it => it.start === oldPeriod.start && it.end === oldPeriod.end);
        if (selectedIndex === -1 && periods.length > 0) {
            const thisYear = new Date().getUTCFullYear();
            selectedIndex = periods.findIndex(it => new Date(it.start).getUTCFullYear() === thisYear)
            if (selectedIndex === -1) {
                selectedIndex = periods.findIndex(it => new Date(it.end).getUTCFullYear() === thisYear)
                if (selectedIndex === -1) {
                    selectedIndex = 0;
                }
            }
        }

        return selectPeriod(
            {
                ...state,
                periodSelection: {
                    ...state.periodSelection,
                    availablePeriods: periods
                }
            },
            selectedIndex
        );
    }

    function selectPeriod(state: State, periodIndex: number): State {
        const period = getOrNull(state.periodSelection.availablePeriods, periodIndex);
        if (!period) {
            return {
                ...state,
                periodSelection: {
                    ...state.periodSelection,
                    currentPeriodIdx: -1,
                },
                yourAllocations: {},
                subAllocations: {
                    searchQuery: state.subAllocations.searchQuery,
                    recipients: [],
                },
            };
        }

        const walletsInPeriod = state.remoteData.wallets.map(wallet => {
            const newAllocations = wallet.allocations.filter(alloc =>
                !wallet.paysFor.freeToUse &&
                alloc.startDate >= period.start &&
                (alloc.endDate ?? Number.MAX_SAFE_INTEGER) <= period.end);

            return {...wallet, allocations: newAllocations};
        }).filter(it => it.allocations.length > 0);

        const subAllocationsInPeriod = state.remoteData.subAllocations.filter(alloc =>
            !alloc.productCategory.freeToUse &&
            alloc.startDate >= period.start &&
            (alloc.endDate ?? Number.MAX_SAFE_INTEGER) <= period.end
        );

        // Build the "your allocations" tree
        const yourAllocations: State["yourAllocations"] = {};
        {
            const walletsByType = groupBy(walletsInPeriod, it => it.paysFor.productType);
            for (const [type, wallets] of Object.entries(walletsByType)) {
                yourAllocations[type as ProductType] = {
                    usageAndQuota: [],
                    wallets: []
                };
                const entry = yourAllocations[type as ProductType]!;

                const quotaBalances = wallets.flatMap(wallet =>
                    wallet.allocations.map(alloc => ({balance: alloc.quota, category: wallet.paysFor}))
                );
                const usageBalances = wallets.flatMap(wallet =>
                    wallet.allocations.map(alloc => ({
                        balance: alloc.treeUsage ?? 0,
                        category: wallet.paysFor
                    }))
                );

                const combinedQuotas = Accounting.combineBalances(quotaBalances);
                const combinedUsage = Accounting.combineBalances(usageBalances);

                for (let i = 0; i < combinedQuotas.length; i++) {
                    const usage = combinedUsage[i];
                    const quota = combinedQuotas[i];

                    entry.usageAndQuota.push({
                        usage: usage.normalizedBalance,
                        quota: quota.normalizedBalance,
                        unit: usage.unit
                    });
                }

                let allocBaseIdx = 0;
                for (const wallet of wallets) {
                    const usage = Accounting.combineBalances(usageBalances.slice(allocBaseIdx, allocBaseIdx + wallet.allocations.length));
                    const quota = Accounting.combineBalances(quotaBalances.slice(allocBaseIdx, allocBaseIdx + wallet.allocations.length));
                    if (usage.length !== quota.length || usage.length !== 1) throw `unexpected length of usage and quota ${JSON.stringify(usage)} ${JSON.stringify(quota)}`;

                    const unit = Accounting.explainUnit(wallet.paysFor);

                    entry.wallets.push({
                        category: wallet.paysFor,

                        usageAndQuota: {
                            usage: usage[0].normalizedBalance,
                            quota: quota[0].normalizedBalance,
                            unit: usage[0].unit
                        },

                        allocations: wallet.allocations.map(alloc => ({
                            id: alloc.id,
                            grantedIn: alloc.grantedIn?.toString() ?? undefined,
                            usageAndQuota: {
                                usage: (alloc.treeUsage ?? 0) * unit.priceFactor,
                                quota: alloc.quota * unit.priceFactor,
                                unit: usage[0].unit,
                            }
                        })),
                    });

                    allocBaseIdx += wallet.allocations.length;
                }
            }
        }

        // Start building the sub-allocations UI
        const subAllocations: State["subAllocations"] = {
            searchQuery: state.subAllocations.searchQuery,
            recipients: []
        };

        {
            for (const alloc of subAllocationsInPeriod) {
                const allocOwner = Accounting.subAllocationOwner(alloc);
                const productUnit = Accounting.explainUnit(alloc.productCategory);

                let recipient = subAllocations.recipients
                    .find(it => Accounting.walletOwnerEquals(it.owner.reference, allocOwner));
                if (!recipient) {
                    recipient = {
                        owner: {
                            reference: allocOwner,
                            primaryUsername: alloc.projectPI!,
                            title: alloc.workspaceTitle,
                        },
                        allocations: [],
                        usageAndQuota: []
                    };

                    subAllocations.recipients.push(recipient);
                }

                recipient.allocations.push({
                    allocationId: alloc.id,
                    category: alloc.productCategory,
                    usageAndQuota: {
                        usage: alloc.usage * productUnit.priceFactor,
                        quota: alloc.quota * productUnit.priceFactor,
                        unit: productUnit.name
                    }
                });
            }

            for (const recipient of subAllocations.recipients) {
                const uqBuilder: { type: Accounting.ProductType, unit: string, usage: number, quota: number }[] = [];
                for (const alloc of recipient.allocations) {
                    const existing = uqBuilder.find(it =>
                        it.type === alloc.category.productType && it.unit === alloc.usageAndQuota.unit);

                    if (existing) {
                        existing.usage += alloc.usageAndQuota.usage;
                        existing.quota += alloc.usageAndQuota.quota;
                    } else {
                        uqBuilder.push({
                            type: alloc.category.productType,
                            usage: alloc.usageAndQuota.usage,
                            quota: alloc.usageAndQuota.quota,
                            unit: alloc.usageAndQuota.unit
                        });
                    }
                }

                const defaultAllocId = "ZZZZZZZZ";
                recipient.allocations.sort((a, b) => {
                    const providerCmp = a.category.provider.localeCompare(b.category.provider);
                    if (providerCmp !== 0) return providerCmp;
                    const categoryCmp = a.category.name.localeCompare(b.category.name);
                    if (categoryCmp !== 0) return categoryCmp;
                    const allocCmp = (a.allocationId ?? defaultAllocId).localeCompare(b.allocationId ?? defaultAllocId);
                    if (allocCmp !== 0) return allocCmp;
                    return 0;
                });

                recipient.usageAndQuota = uqBuilder;
            }
        }

        return {
            ...state,
            periodSelection: {
                ...state.periodSelection,
                currentPeriodIdx: periodIndex,
            },
            yourAllocations,
            subAllocations,
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
                fetchAll(next =>
                    callAPI(Accounting.browseWalletsV2({
                        itemsPerPage: 250,
                        next
                    }))
                ).then(wallets => {
                    dispatch({type: "WalletsLoaded", wallets});
                });

                fetchAll(next =>
                    callAPI(Accounting.browseSubAllocations({itemsPerPage: 250, next}))
                ).then(subAllocations => {
                    dispatch({type: "SubAllocationsLoaded", subAllocations});
                });

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
const AllocationsStyle = injectStyle("allocations", k => `
    ${k} > header {
        display: flex;
        flex-direction: row;
        align-items: center;
        gap: 8px;
        margin-bottom: 16px;
    }
    
    ${k} > header h2 {
        font-size: 20px;
        margin: 0;
    }
    
    ${k} h1,
    ${k} h2,
    ${k} h3,
    ${k} h4 {
        margin: 15px 0;
    }
`);

// User-interface
// =====================================================================================================================
const Allocations: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(rawDispatch);

    useEffect(() => {
        dispatchEvent({type: "Init"});
    }, [projectId]);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.
    const onPeriodSelect = useCallback((ev: ChangeEvent) => {
        const target = ev.target as HTMLSelectElement;
        dispatchEvent({type: "PeriodUpdated", selectedIndex: target.selectedIndex});
    }, [dispatchEvent]);

    // Short-hands used in the user-interface
    // -----------------------------------------------------------------------------------------------------------------
    const indent = 16;
    const baseProgress = 250;

    const sortedAllocations = Object.entries(state.yourAllocations).sort((a, b) => {
        const aPriority = productTypesByPriority.indexOf(a[0] as ProductType);
        const bPriority = productTypesByPriority.indexOf(b[0] as ProductType);

        if (aPriority === bPriority) return 0;
        if (aPriority === -1) return 1;
        if (bPriority === -1) return -1;
        if (aPriority < bPriority) return -1;
        if (aPriority > bPriority) return 1;
        return 0;
    });

    console.log(state);

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    return <MainContainer
        headerSize={0}
        main={<div className={AllocationsStyle}>
            <header>
                <h2>Resource allocations for</h2>

                <Box width={"120px"}>
                    <Select slim value={state.periodSelection.currentPeriodIdx} onChange={onPeriodSelect}>
                        {state.periodSelection.availablePeriods.length === 0 &&
                            <option>{new Date().getUTCFullYear()}</option>}

                        {state.periodSelection.availablePeriods.map((it, idx) =>
                            <option key={idx} value={idx.toString()}>{periodToString(it)}</option>
                        )}
                    </Select>
                </Box>

                <Box flexGrow={1}/>

                <ContextSwitcher/>
            </header>

            <h3>Your allocations</h3>
            {sortedAllocations.map(([rawType, tree]) => {
                const type = rawType as ProductType;


                return <Accordion
                    key={rawType}
                    noBorder
                    title={<Flex gap={"4px"}>
                        <Icon name={Accounting.productTypeToIcon(type)} size={20}/>
                        {Accounting.productAreaTitle(type)}
                    </Flex>}
                    titleContent={<Flex gap={"8px"}>
                        {tree.usageAndQuota.map((uq, idx) =>
                            <ProgressBarWithLabel
                                key={idx}
                                value={(uq.usage / uq.quota) * 100}
                                text={progressText(type, uq)}
                                width={`${baseProgress}px`}
                            />
                        )}
                    </Flex>}
                >
                    <Box ml={`${indent}px`}>
                        {tree.wallets.map((wallet, idx) =>
                            <Accordion
                                key={idx}
                                noBorder
                                title={<Flex gap={"4px"}>
                                    <ProviderLogo providerId={wallet.category.provider} size={20}/>
                                    <code>{wallet.category.name}</code>
                                </Flex>}
                                titleContent={<Box ml={"32px"}>
                                    <ProgressBarWithLabel
                                        value={(wallet.usageAndQuota.usage / wallet.usageAndQuota.quota) * 100}
                                        text={progressText(type, wallet.usageAndQuota)}
                                        width={`${baseProgress}px`}
                                    />
                                </Box>}
                            >
                                <Box ml={`${indent * 2}px`}>
                                    {wallet.allocations.map(alloc =>
                                        <Accordion
                                            key={alloc.id}
                                            noBorder
                                            icon={"heroBanknotes"}
                                            title={<>
                                                <b>Allocation ID:</b> {alloc.id}
                                                {alloc.grantedIn && <>
                                                    {" "}
                                                    (
                                                    <Link target={"_blank"}
                                                          to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                        View grant application{" "}
                                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                                    </Link>
                                                    )
                                                </>}
                                            </>}
                                            titleContent={<>
                                                <ProgressBarWithLabel
                                                    value={(alloc.usageAndQuota.usage / alloc.usageAndQuota.quota) * 100}
                                                    text={progressText(type, alloc.usageAndQuota)}
                                                    width={`${baseProgress}px`}
                                                />
                                            </>}
                                        />
                                    )}
                                </Box>
                            </Accordion>
                        )}
                    </Box>
                </Accordion>;
            })}

            <h3>Sub-allocations</h3>
            {state.subAllocations.recipients.map((recipient, idx) =>
                <Accordion
                    key={idx}
                    noBorder
                    title={<Flex gap={"4px"} alignItems={"center"}>
                        <TooltipV2 tooltip={`Workspace PI: ${recipient.owner.primaryUsername}`}>
                            <Avatar {...defaultAvatar} style={{height: "28px", width: "auto"}} avatarStyle={"Transparent"} />
                        </TooltipV2>
                        {recipient.owner.title}
                    </Flex>}
                    titleContent={<Flex gap={"8px"}>
                        {recipient.usageAndQuota.map((uq, idx) =>
                            <ProgressBarWithLabel
                                key={idx}
                                value={(uq.usage / uq.quota) * 100}
                                text={progressText(uq.type, uq)}
                                width={`${baseProgress}px`}
                            />
                        )}
                    </Flex>}
                >
                    <Box ml={32}>
                        {recipient.allocations.map((alloc, idx) =>
                            <Accordion
                                key={idx}
                                omitChevron
                                noBorder
                                title={<Flex gap={"4px"}>
                                    <Flex gap={"4px"} width={"200px"}>
                                        <ProviderLogo providerId={alloc.category.provider} size={20}/>
                                        <Icon name={Accounting.productTypeToIcon(alloc.category.productType)} size={20} />
                                        <code>{alloc.category.name}</code>
                                    </Flex>

                                    {alloc.allocationId && <span> <b>Allocation ID:</b> {alloc.allocationId}</span>}
                                </Flex>}
                                titleContent={
                                    <ProgressBarWithLabel
                                        value={(alloc.usageAndQuota.usage / alloc.usageAndQuota.quota) * 100}
                                        text={progressText(alloc.category.productType, alloc.usageAndQuota)}
                                        width={`${baseProgress}px`}
                                    />
                                }
                            />
                        )}
                    </Box>

                </Accordion>
            )}
        </div>}
    />;
};

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.
const productTypesByPriority: ProductType[] = [
    "COMPUTE",
    "STORAGE",
    "NETWORK_IP",
    "INGRESS",
    "LICENSE",
];

function progressText(type: ProductType, uq: UsageAndQuota): string {
    let text = "";
    text += Accounting.balanceToStringFromUnit(type, uq.unit, uq.usage, {
        precision: 0,
        removeUnitIfPossible: true
    });
    text += " / ";
    text += Accounting.balanceToStringFromUnit(type, uq.unit, uq.quota, {precision: 0});

    text += " (";
    text += Math.round((uq.usage / uq.quota) * 100);
    text += "%)";
    return text;
}

// Initial state
// =====================================================================================================================
const initialState: State = {
    periodSelection: {availablePeriods: [], currentPeriodIdx: 0},
    remoteData: {subAllocations: [], wallets: []},
    subAllocations: {searchQuery: "", recipients: []},
    yourAllocations: {}
};

export default Allocations;
