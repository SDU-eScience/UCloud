import {injectStyle} from "@/Unstyled";
import * as React from "react";
import {Accordion, Box, Flex, MainContainer, ProgressBarWithLabel, Select} from "@/ui-components";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import * as Accounting from "@/Accounting";
import {doNothing, errorMessageOrDefault} from "@/UtilityFunctions";
import {ProductType} from "@/Accounting";
import {groupBy} from "@/Utilities/CollectionUtilities";
import {useCallback, useEffect, useReducer} from "react";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import * as Grants from "@/Grants";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import * as Projects from "@/Project/Api";
import {fetchAll} from "@/Utilities/PageUtilities";
import {PageV2} from "@/UCloud";

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
            open?: boolean;
            usageAndQuota: UsageAndQuota[];
            wallets: {
                open?: boolean;
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
                const p: Period = { start: alloc.startDate, end: alloc.endDate ?? Number.MAX_SAFE_INTEGER };
                if (!periods.some(it => it.start === p.start && it.end === p.end)) {
                    periods.push(p);
                }
            }
        }

        const oldPeriod = getOrNull(state.periodSelection.availablePeriods, state.periodSelection.currentPeriodIdx);

        let selectedIndex = -1;
        if (oldPeriod) selectedIndex = periods.findIndex(it => it.start === oldPeriod.start && it.end === oldPeriod.end);
        if (selectedIndex === -1 && periods.length > 0) selectedIndex = 0;

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
                },
            };
        }

        const walletsInPeriod = state.remoteData.wallets.map(wallet => {
            const newAllocations = wallet.allocations.filter(alloc =>
                !wallet.paysFor.freeToUse &&
                alloc.startDate >= period.start &&
                (alloc.endDate ?? Number.MAX_SAFE_INTEGER) <= period.end);

            return { ...wallet, allocations: newAllocations };
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
                    wallet.allocations.map(alloc => ({ balance: alloc.quota, category: wallet.paysFor }))
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

        return {
            ...state,
            yourAllocations,
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
                const wallets = await fetchAll(next =>
                    callAPI(Accounting.browseWalletsV2({
                        itemsPerPage: 250,
                        next
                    }))
                );

                dispatch({ type: "WalletsLoaded", wallets });
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
        dispatchEvent({ type: "Init" });
    }, [projectId]);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.

    // Short-hands used in the user-interface
    // -----------------------------------------------------------------------------------------------------------------
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

    console.log(state.yourAllocations);

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    return <MainContainer
        headerSize={0}
        main={<div className={AllocationsStyle}>
            <header>
                <h2>Resource allocations for</h2>

                <Box width={"120px"}>
                    <Select slim value={state.periodSelection.currentPeriodIdx}>
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
                function progressText(uq: UsageAndQuota): string {
                    let text = "";
                    text += Accounting.balanceToStringFromUnit(type, uq.unit, uq.usage, { removeUnitIfPossible: true });
                    text += " / ";
                    text += Accounting.balanceToStringFromUnit(type, uq.unit, uq.quota);

                    text += " (";
                    text += Math.round(uq.usage / uq.quota);
                    text += "%)";
                    return text;
                }

                return <Accordion
                    key={rawType}
                    title={Accounting.productAreaTitle(type)}
                    icon={Accounting.productTypeToIcon(type)}
                    titleContent={<div>
                        {tree.usageAndQuota.map((uq, idx) =>
                            <ProgressBarWithLabel
                                key={idx}
                                value={uq.usage / uq.quota}
                                text={progressText(uq)}
                            />
                        )}
                    </div>}
                >
                    <Box ml={"32px"}>
                        {tree.wallets.map((wallet, idx) =>
                            <Accordion
                                key={idx}
                                title={wallet.category.name}
                                titleContent={<>
                                    <ProgressBarWithLabel
                                        value={wallet.usageAndQuota.usage / wallet.usageAndQuota.quota}
                                        text={progressText(wallet.usageAndQuota)}
                                    />
                                </>}
                            >
                                <Box ml={"32px"}>
                                    {wallet.allocations.map(alloc =>
                                        <Accordion
                                            key={alloc.id}
                                            omitChevron
                                            title={`${alloc.id}`}
                                            titleContent={<>
                                                <ProgressBarWithLabel
                                                    value={alloc.usageAndQuota.usage / alloc.usageAndQuota.quota}
                                                    text={progressText(alloc.usageAndQuota)}
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


// Initial state
// =====================================================================================================================
const initialState: State = {
    periodSelection: {availablePeriods: [], currentPeriodIdx: 0},
    remoteData: {subAllocations: [], wallets: []},
    subAllocations: {searchQuery: ""},
    yourAllocations: {}
};

export default Allocations;
