import {extractDataTags, injectStyle} from "@/Unstyled";
import * as React from "react";
import {
    Accordion,
    Box,
    Button,
    Flex,
    Icon,
    Input,
    Link,
    MainContainer,
    ProgressBarWithLabel,
    Select
} from "@/ui-components";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import * as Accounting from "@/Accounting";
import {ProductType} from "@/Accounting";
import {fuzzySearch, groupBy} from "@/Utilities/CollectionUtilities";
import {ChangeEvent, useCallback, useEffect, useReducer, useRef} from "react";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import {fetchAll} from "@/Utilities/PageUtilities";
import AppRoutes from "@/Routes";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {Avatar} from "@/AvataaarLib";
import {defaultAvatar} from "@/UserSettings/Avataaar";
import {TooltipV2} from "@/ui-components/Tooltip";
import {IconName} from "@/ui-components/Icon";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {addStandardInputDialog} from "@/UtilityComponents";
import {useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {useAvatars} from "@/AvataaarLib/hook";
import {bulkRequestOf} from "@/DefaultObjects";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Tree, TreeApi, TreeNode} from "@/ui-components/Tree";

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
        periodSize: PeriodSize;
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
                    note?: AllocationNote;
                }[];
            }[];
        }
    };

    subAllocations: {
        searchQuery: string;
        searchInflight: number;

        recipients: {
            owner: {
                title: string;
                primaryUsername: string;
                reference: Accounting.WalletOwner;
            };

            usageAndQuota: (UsageAndQuota & { type: Accounting.ProductType })[];

            allocations: {
                allocationId: string;
                usageAndQuota: UsageAndQuota;
                category: Accounting.ProductCategoryV2;
                note?: AllocationNote;
                isEditing: boolean;
            }[];
        }[];
    };

    editControlsDisabled: boolean;
}

interface AllocationNote {
    rowShouldBeGreyedOut: boolean;
    hideIfZeroUsage?: boolean;
    icon: IconName;
    iconColor: ThemeColor;
    text: string;
}

enum PeriodSize {
    MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY
}

interface UsageAndQuota {
    usage: number;
    quota: number;
    unit: string;
}

interface Period {
    start: number;
    end: number;
    title?: string;
}

// State reducer
// =====================================================================================================================
type UIAction =
    { type: "WalletsLoaded", wallets: Accounting.WalletV2[]; }
    | { type: "PeriodUpdated", selectedIndex: number }
    | { type: "PeriodSizeUpdated", selectedIndex: number }
    | { type: "SubAllocationsLoaded", subAllocations: Accounting.SubAllocationV2[] }
    | { type: "UpdateSearchQuery", newQuery: string }
    | { type: "SetEditing", recipientIdx: number, allocationIdx: number, isEditing: boolean }
    | { type: "UpdateAllocation", allocationIdx: number, recipientIdx: number, newQuota: number }
    | { type: "MergeSearchResults", subAllocations: Accounting.SubAllocationV2[] }
    | { type: "UpdateSearchInflight", delta: number }
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

            return rebuildTree(newState);
        }

        case "SubAllocationsLoaded": {
            const newState = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    subAllocations: action.subAllocations,
                }
            };

            return rebuildTree(newState);
        }

        case "PeriodUpdated": {
            return selectPeriod(state, action.selectedIndex);
        }

        case "PeriodSizeUpdated": {
            return rebuildTree({
                ...state,
                periodSelection: {
                    ...state.periodSelection,
                    periodSize: action.selectedIndex
                }
            });
        }

        case "UpdateSearchQuery": {
            const newState = {
                ...state,
                subAllocations: {
                    ...state.subAllocations,
                    searchQuery: action.newQuery
                },
            };

            return rebuildTree(newState);
        }

        case "MergeSearchResults": {
            const newState: State = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    subAllocations: [...state.remoteData.subAllocations]
                }
            };

            const subAllocations = newState.remoteData.subAllocations;
            for (const newResult of action.subAllocations) {
                if (subAllocations.some(it => it.id === newResult.id)) continue;
                subAllocations.push(newResult);
            }

            return rebuildTree(newState);
        }

        case "UpdateSearchInflight": {
            return {
                ...state,
                subAllocations: {
                    ...state.subAllocations,
                    searchInflight: state.subAllocations.searchInflight + action.delta
                }
            };
        }

        case "SetEditing": {
            const recipient = getOrNull(state.subAllocations.recipients, action.recipientIdx);
            if (!recipient) return state;
            const allocation = getOrNull(recipient.allocations, action.allocationIdx);
            if (!allocation) return state;

            const newState: State = {
                ...state,
                subAllocations: {
                    ...state.subAllocations,
                    recipients: [...state.subAllocations.recipients]
                }
            };

            const newRecipient = newState.subAllocations.recipients[action.recipientIdx];
            newRecipient.allocations = [...newRecipient.allocations];
            newRecipient.allocations[action.allocationIdx] = {
                ...allocation,
                isEditing: action.isEditing
            };

            newState.editControlsDisabled = action.isEditing;

            return newState;
        }

        case "UpdateAllocation": {
            const recipient = getOrNull(state.subAllocations.recipients, action.recipientIdx);
            if (!recipient) return state;
            const allocation = getOrNull(recipient.allocations, action.allocationIdx);
            if (!allocation) return state;

            const allocationId = allocation.allocationId;
            const newState: State = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    subAllocations: [...state.remoteData.subAllocations]
                }
            };
            const idx = newState.remoteData.subAllocations.findIndex(it => it.id === allocationId);
            if (idx === -1) return state;

            newState.remoteData.subAllocations[idx] = {
                ...newState.remoteData.subAllocations[idx],
                quota: action.newQuota
            };

            return rebuildTree(newState);
        }
    }

    // Utility functions for mutating state
    // -----------------------------------------------------------------------------------------------------------------
    function getOrNull<T>(arr: T[], idx: number): T | null {
        if (idx >= 0 && idx < arr.length) return arr[idx];
        return null;
    }

    function calculateIdealPeriods(size: PeriodSize, until: number): Period[] {
        const result: Period[] = [];

        const now = new Date();
        now.setUTCHours(0, 0, 0, 0); // Always reset to start of day

        switch (size) {
            case PeriodSize.MONTHLY: {
                now.setUTCDate(1); // Go to the start of this month before we change anything

                while (now.getTime() < until && result.length < 120) {
                    const title = `${now.getUTCFullYear()} ${monthNames[now.getUTCMonth()]}`;
                    const start = now.getTime();
                    now.setUTCMonth(now.getUTCMonth() + 1);
                    const end = now.getTime() - 1;

                    result.push({start, end, title});
                }
                break;
            }

            case PeriodSize.QUARTERLY: {
                now.setUTCMonth(((now.getUTCMonth() / 3) | 0) * 3);
                now.setUTCDate(1);

                while (now.getTime() < until && result.length < 40) {
                    const quarter = ((now.getUTCMonth() / 3) | 0) + 1;
                    const title = `${now.getUTCFullYear()} Q${quarter}`;
                    const start = now.getTime();
                    now.setUTCMonth(now.getUTCMonth() + 3);
                    const end = now.getTime() - 1;

                    result.push({start, end, title});
                }

                break;
            }

            case PeriodSize.HALF_YEARLY: {
                now.setUTCMonth(((now.getUTCMonth() / 6) | 0) * 6);
                now.setUTCDate(1);

                while (now.getTime() < until && result.length < 20) {
                    const half = ((now.getUTCMonth() / 6) | 0) + 1;
                    const title = `${now.getUTCFullYear()} H${half}`;
                    const start = now.getTime();
                    now.setUTCMonth(now.getUTCMonth() + 6);
                    const end = now.getTime() - 1;

                    result.push({start, end, title});
                }
                break;
            }

            case PeriodSize.YEARLY: {
                now.setUTCMonth(0);
                now.setUTCDate(1);

                while (now.getTime() < until && result.length < 10) {
                    const title = `${now.getUTCFullYear()}`;
                    const start = now.getTime();
                    now.setUTCFullYear(now.getUTCFullYear() + 1);
                    const end = now.getTime() - 1;

                    result.push({start, end, title});
                }
                break;
            }
        }

        return result;
    }

    function rebuildTree(state: State): State {
        const maxEndDate = state.remoteData.wallets
            .flatMap(w => w.allocations.map(a => a.endDate ?? Number.MAX_SAFE_INTEGER))
            .reduce((p, a) => Math.max(p, a), Number.MIN_SAFE_INTEGER);

        const idealPeriods = calculateIdealPeriods(state.periodSelection.periodSize, maxEndDate);
        const periods = idealPeriods.filter(p => {
            return state.remoteData.wallets.some(w => w.allocations.some(a => {
                return periodsOverlap(p, allocationToPeriod(a));
            }));
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

        let now = timestampUnixMs();
        if (period && period.start > now) {
            // Assume that 'now' is at the start of the period, if the period itself is in the future.
            // This means that we do not grey out rows which start when the period starts.
            now = period.start;
        }

        function allocationNote(
            alloc: Accounting.WalletAllocationV2 | Accounting.SubAllocationV2
        ): AllocationNote | undefined {
            if (!period) return undefined;
            const p = normalizePeriodForComparison(period);

            // NOTE(Dan): We color code and potentially grey out rows when the end-user should be aware of something
            // on the allocation.
            //
            // - If a row should be greyed out, then it means that the row is not currently active (and is not counted
            //   in summaries).
            // - If the accompanying row has a red calendar, then the note is about something which has happened.
            // - If the accompanying row has a blue calendar, then the note is about something which will happen.
            const icon: IconName = "heroCalendarDays";
            const colorInThePast: ThemeColor = "red";
            const colorForTheFuture: ThemeColor = "blue";

            const allocPeriod = normalizePeriodForComparison(allocationToPeriod(alloc));
            if (now > allocPeriod.end) {
                return {
                    rowShouldBeGreyedOut: true,
                    icon,
                    iconColor: colorInThePast,
                    text: `Already expired (${Accounting.utcDate(allocPeriod.end)})`,
                    hideIfZeroUsage: true,
                };
            }

            if (allocPeriod.start > now) {
                return {
                    rowShouldBeGreyedOut: true,
                    icon,
                    iconColor: colorForTheFuture,
                    text: `Starts in the future (${Accounting.utcDate(allocPeriod.start)})`,
                };
            }

            if (allocPeriod.end < p.end) {
                return {
                    rowShouldBeGreyedOut: false,
                    icon,
                    iconColor: colorForTheFuture,
                    text: `Expires early (${Accounting.utcDate(allocPeriod.end)})`
                };
            }

            return undefined;
        }

        if (!period) {
            return {
                ...state,
                periodSelection: {
                    ...state.periodSelection,
                    currentPeriodIdx: -1,
                },
                yourAllocations: {},
                subAllocations: {
                    ...state.subAllocations,
                    recipients: [],
                },
            };
        }

        const walletsInPeriod = state.remoteData.wallets.map(wallet => {
            const newAllocations = wallet.allocations.filter(alloc =>
                !wallet.paysFor.freeToUse &&
                periodsOverlap(period, allocationToPeriod(alloc))
            );

            return {...wallet, allocations: newAllocations};
        }).filter(it => it.allocations.length > 0);

        let filteredSubAllocations: Accounting.SubAllocationV2[];
        if (state.subAllocations.searchQuery === "") {
            filteredSubAllocations = state.remoteData.subAllocations;
        } else {
            const query = state.subAllocations.searchQuery.toLowerCase();
            filteredSubAllocations = fuzzySearch(
                state.remoteData.subAllocations,
                ["id", "workspaceTitle", "workspaceId", "grantedIn"],
                query
            );
        }

        const subAllocationsInPeriod = filteredSubAllocations.filter(alloc =>
            !alloc.productCategory.freeToUse &&
            periodsOverlap(period, allocationToPeriod(alloc))
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
                    wallet.allocations
                        .filter(alloc => allocationIsActive(alloc, now))
                        .map(alloc => ({balance: alloc.quota, category: wallet.paysFor}))
                );
                const usageBalances = wallets.flatMap(wallet =>
                    wallet.allocations
                        .filter(alloc => allocationIsActive(alloc, now))
                        .map(alloc => ({
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

                for (const wallet of wallets) {
                    const usage = Accounting.combineBalances(
                        wallet.allocations
                            .filter(alloc => allocationIsActive(alloc, now))
                            .map(alloc => ({
                                balance: alloc.treeUsage ?? 0,
                                category: wallet.paysFor
                            }))
                    )
                    const quota = Accounting.combineBalances(
                        wallet.allocations
                            .filter(alloc => allocationIsActive(alloc, now))
                            .map(alloc => ({balance: alloc.quota, category: wallet.paysFor}))
                    );

                    const unit = Accounting.explainUnit(wallet.paysFor);

                    entry.wallets.push({
                        category: wallet.paysFor,

                        usageAndQuota: {
                            usage: usage?.[0]?.normalizedBalance ?? 0,
                            quota: quota?.[0]?.normalizedBalance ?? 0,
                            unit: usage?.[0]?.unit ?? ""
                        },

                        allocations: wallet.allocations.map(alloc => ({
                            id: alloc.id,
                            grantedIn: alloc.grantedIn?.toString() ?? undefined,
                            note: allocationNote(alloc),
                            usageAndQuota: {
                                usage: (alloc.treeUsage ?? 0) * unit.priceFactor,
                                quota: alloc.quota * unit.priceFactor,
                                unit: usage?.[0]?.unit ?? unit.name,
                            }
                        })),
                    });
                }
            }
        }

        // Start building the sub-allocations UI
        const subAllocations: State["subAllocations"] = {
            ...state.subAllocations,
            recipients: []
        };

        {
            for (const alloc of filteredSubAllocations) {
                const allocOwner = Accounting.subAllocationOwner(alloc);
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
            }

            for (const alloc of subAllocationsInPeriod) {
                const allocOwner = Accounting.subAllocationOwner(alloc);
                const productUnit = Accounting.explainUnit(alloc.productCategory);

                const recipient = subAllocations.recipients
                    .find(it => Accounting.walletOwnerEquals(it.owner.reference, allocOwner))!;

                recipient.allocations.push({
                    allocationId: alloc.id,
                    category: alloc.productCategory,
                    usageAndQuota: {
                        usage: alloc.usage * productUnit.priceFactor,
                        quota: alloc.quota * productUnit.priceFactor,
                        unit: productUnit.name
                    },
                    note: allocationNote(alloc),
                    isEditing: false,
                });
            }

            for (const recipient of subAllocations.recipients) {
                const uqBuilder: { type: Accounting.ProductType, unit: string, usage: number, quota: number }[] = [];
                for (const alloc of recipient.allocations) {
                    if (alloc.note && alloc.note.rowShouldBeGreyedOut) continue;

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
    
    ${k} .disabled-alloc {
        filter: opacity(0.5);
    }
    
    ${k} .sub-alloc {
        display: flex;
        align-items: center;
        gap: 8px;
    }
`);

// User-interface
// =====================================================================================================================
const Allocations: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const navigate = useNavigate();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(rawDispatch);
    const avatars = useAvatars();
    const searchTimeout = useRef<number>(0);
    const allocationTree = useRef<TreeApi>(null);
    const suballocationTree = useRef<TreeApi>(null);
    const searchBox = useRef<HTMLInputElement>(null);

    const currentPeriod = state.periodSelection.availablePeriods[state.periodSelection.currentPeriodIdx];
    const currentPeriodStart = currentPeriod?.start ?? timestampUnixMs();
    const currentPeriodEnd = currentPeriod?.end ?? timestampUnixMs();

    useEffect(() => {
        dispatchEvent({type: "Init"});
    }, [projectId]);

    useEffect(() => {
        const users = new Set<string>();
        state.subAllocations.recipients.forEach(it => users.add(it.owner.primaryUsername));
        avatars.updateCache(Array.from(users));
    }, [state.subAllocations.recipients]);

    // Event handlers
    // -----------------------------------------------------------------------------------------------------------------
    // These event handlers translate, primarily DOM, events to higher-level UIEvents which are sent to
    // dispatchEvent(). There is nothing complicated in these, but they do take up a bit of space. When you are writing
    // these, try to avoid having dependencies on more than just dispatchEvent itself.
    useEffect(() => {
        const handler = (ev: KeyboardEvent) => {
            switch (ev.code) {
                case "Tab": {
                    ev.preventDefault();
                    const allocTreeActive = allocationTree.current?.isActive() === true;
                    const subAllocTreeActive = suballocationTree.current?.isActive() === true;

                    if (allocTreeActive) {
                        suballocationTree.current?.activate();
                    } else if (subAllocTreeActive) {
                        allocationTree.current?.activate();
                    } else {
                        allocationTree.current?.activate();
                    }
                    break;
                }

                case "KeyK": {
                    if (ev.ctrlKey || ev.metaKey) {
                        ev.preventDefault();
                        const box = searchBox.current;
                        console.log(box);
                        if (box) {
                            box.scrollIntoView({ block: "nearest" });
                            box.focus();
                        }
                    }
                    break;
                }
            }
        };

        document.body.addEventListener("keydown", handler);
        return () => document.body.removeEventListener("keydown", handler);
    }, []);
    const onPeriodSelect = useCallback((ev: ChangeEvent) => {
        const target = ev.target as HTMLSelectElement;
        dispatchEvent({type: "PeriodUpdated", selectedIndex: target.selectedIndex});
    }, [dispatchEvent]);

    const onPeriodSizeSelect = useCallback((ev: ChangeEvent) => {
        const target = ev.target as HTMLSelectElement;
        dispatchEvent({type: "PeriodSizeUpdated", selectedIndex: target.selectedIndex});
    }, [dispatchEvent]);

    const onNewSubProject = useCallback(async () => {
        const title = (await addStandardInputDialog({
            title: "What should we call your new sub-project?",
            confirmText: "Create sub-project"
        })).result;

        navigate(AppRoutes.grants.grantGiverInitiatedEditor({
            title,
            start: currentPeriodStart,
            end: currentPeriodEnd,
            piUsernameHint: Client.username ?? "?",
        }));
    }, [currentPeriodStart, currentPeriodEnd]);

    const onEdit = useCallback((elem: HTMLElement) => {
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        if (isNaN(idx) || isNaN(ridx)) return;

        dispatchEvent({ type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: true });
    }, []);

    const onEditKey = useCallback(async (ev: React.KeyboardEvent) => {
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        switch (ev.code) {
            case "Enter": {
                const value = parseInt(elem.value);
                if (!isNaN(value)) {
                    const alloc = state.subAllocations.recipients[ridx].allocations[idx]!;
                    const unit = Accounting.explainUnit(alloc.category);
                    const success = (await callAPIWithErrorHandler(
                        Accounting.updateAllocationV2(bulkRequestOf({
                            allocationId: alloc.allocationId,
                            newQuota: value * unit.invPriceFactor,
                            reason: "Allocation updated with new quota",
                        }))
                    )) !== null;

                    if (success) {
                        dispatchEvent({
                            type: "UpdateAllocation",
                            allocationIdx: idx,
                            recipientIdx: ridx,
                            newQuota: value * unit.invPriceFactor,
                        });
                    }

                    dispatchEvent({ type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false });
                }
                break;
            }

            case "Escape": {
                dispatchEvent({ type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false });
                break;
            }
        }
    }, [state.subAllocations.recipients]);

    const onEditBlur = useCallback((ev: React.SyntheticEvent) => {
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        dispatchEvent({ type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false });
    }, [dispatchEvent]);

    const onSearchInput = useCallback((ev: React.SyntheticEvent) => {
        const input = ev.target as HTMLInputElement;
        const newQuery = input.value;
        dispatchEvent({ type: "UpdateSearchQuery", newQuery });

        window.clearTimeout(searchTimeout.current);
        searchTimeout.current = window.setTimeout(async () => {
            if (input.disabled) return;
            dispatchEvent({ type: "UpdateSearchInflight", delta: 1 });
            try {
                const page = await callAPI(Accounting.searchSubAllocations({
                    query: newQuery,
                    itemsPerPage: 250,
                }));

                dispatchEvent({type: "MergeSearchResults", subAllocations: page.items});
            } finally {
                dispatchEvent({ type: "UpdateSearchInflight", delta: -1 });
            }
        }, 200);
    }, [dispatchEvent]);

    const onSearchKey = useCallback<React.KeyboardEventHandler>(ev => {
        const input = ev.target as HTMLInputElement;
        if (ev.code === "Escape") {
            input.blur();
            suballocationTree?.current?.activate();
        }
    }, []);

    const onSubAllocationShortcut = useCallback((target: HTMLElement, ev: KeyboardEvent) => {
        if (ev.code === "KeyE") {
            ev.preventDefault();
            onEdit(target);
        }
    }, [onEdit]);

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

    // Actual user-interface
    // -----------------------------------------------------------------------------------------------------------------
    return <MainContainer
        headerSize={0}
        main={<div className={AllocationsStyle}>
            <header>
                <h2>Resource allocations</h2>
                <Box flexGrow={1}/>
                <ContextSwitcher/>
            </header>

            <h3>Filters</h3>
            <Flex alignItems={"center"} flexDirection={"row"} gap={"4px"}>
                <Box width={"200px"}>
                    <Select slim value={state.periodSelection.periodSize} onChange={onPeriodSizeSelect}>
                        <option value={PeriodSize.MONTHLY}>Month</option>
                        <option value={PeriodSize.QUARTERLY}>Quarter</option>
                        <option value={PeriodSize.HALF_YEARLY}>Half-year</option>
                        <option value={PeriodSize.YEARLY}>Year</option>
                    </Select>
                </Box>

                <Box width={"200px"}>
                    <Select slim value={state.periodSelection.currentPeriodIdx} onChange={onPeriodSelect}>
                        {state.periodSelection.availablePeriods.length === 0 &&
                            <option>{new Date().getUTCFullYear()}</option>}

                        {state.periodSelection.availablePeriods.map((it, idx) =>
                            <option key={idx} value={idx.toString()}>{it.title}</option>
                        )}
                    </Select>
                </Box>
            </Flex>

            <h3>Your allocations</h3>
            <Tree apiRef={allocationTree}>
                {sortedAllocations.map(([rawType, tree]) => {
                    const type = rawType as ProductType;

                    return <TreeNode
                        key={rawType}
                        left={<Flex gap={"4px"}>
                            <Icon name={Accounting.productTypeToIcon(type)} size={20}/>
                            {Accounting.productAreaTitle(type)}
                        </Flex>}
                        right={<Flex gap={"8px"}>
                            {tree.usageAndQuota.map((uq, idx) =>
                                <ProgressBarWithLabel
                                    key={idx}
                                    value={(uq.usage / uq.quota) * 100}
                                    text={progressText(type, uq)}
                                    width={`${baseProgress}px`}
                                />
                            )}
                        </Flex>}
                        indent={indent}
                    >
                        {tree.wallets.map((wallet, idx) =>
                            <TreeNode
                                key={idx}
                                left={<Flex gap={"4px"}>
                                    <ProviderLogo providerId={wallet.category.provider} size={20}/>
                                    <code>{wallet.category.name}</code>
                                </Flex>}
                                right={<Box ml={"32px"}>
                                    <ProgressBarWithLabel
                                        value={(wallet.usageAndQuota.usage / wallet.usageAndQuota.quota) * 100}
                                        text={progressText(type, wallet.usageAndQuota)}
                                        width={`${baseProgress}px`}
                                    />
                                </Box>}
                                indent={indent * 2}
                            >
                                {wallet.allocations
                                    .filter(alloc => !alloc.note || !alloc.note.hideIfZeroUsage || alloc.usageAndQuota.usage > 0)
                                    .map(alloc =>
                                        <TreeNode
                                            key={alloc.id}
                                            className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                            left={<>
                                                <Icon name={"heroBanknotes"} mr={4} />
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
                                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                                {alloc.note && <>
                                                    <TooltipV2 tooltip={alloc.note.text}>
                                                        <Icon name={alloc.note.icon} color={alloc.note.iconColor} />
                                                    </TooltipV2>
                                                </>}
                                                <ProgressBarWithLabel
                                                    value={(alloc.usageAndQuota.usage / alloc.usageAndQuota.quota) * 100}
                                                    text={progressText(type, alloc.usageAndQuota)}
                                                    width={`${baseProgress}px`}
                                                />
                                            </Flex>}
                                        />
                                    )
                                }
                            </TreeNode>
                        )}
                    </TreeNode>;
                })}
            </Tree>

            <Flex mt={32} mb={10} alignItems={"center"} gap={"8px"}>
                <h3 style={{margin: 0}}>Sub-allocations</h3>
                <Box flexGrow={1} />
                <Button height={35} onClick={onNewSubProject}>
                    <Icon name={"heroPlus"} mr={8} />
                    New sub-project
                </Button>

                <Box width={"300px"}>
                    <Input
                        placeholder={"Search in your sub-allocations"}
                        height={35}
                        value={state.subAllocations.searchQuery}
                        onInput={onSearchInput}
                        onKeyDown={onSearchKey}
                        disabled={state.editControlsDisabled}
                        inputRef={searchBox}
                    />
                    <div style={{position: "relative"}}>
                        <div style={{position: "absolute", top: "-30px", right: "11px"}}>
                            {state.subAllocations.searchInflight === 0 ?
                                <Icon name={"heroMagnifyingGlass"} />
                                : <HexSpin size={18} margin={"0"} />
                            }
                        </div>
                    </div>
                </Box>
            </Flex>

            <Tree apiRef={suballocationTree} unhandledShortcut={onSubAllocationShortcut}>
                {state.subAllocations.recipients.map((recipient, recipientIdx) =>
                    <TreeNode
                        key={recipientIdx}
                        left={<Flex gap={"4px"} alignItems={"center"}>
                            <TooltipV2 tooltip={`Workspace PI: ${recipient.owner.primaryUsername}`}>
                                <Avatar {...avatars.avatar(recipient.owner.primaryUsername)} style={{height: "32px", width: "auto", marginTop: "-4px"}}
                                        avatarStyle={"Circle"}/>
                            </TooltipV2>
                            {recipient.owner.title}
                        </Flex>}
                        right={<div className={"sub-alloc"}>
                            {recipient.owner.reference.type === "project" &&
                                <Link
                                    to={AppRoutes.grants.grantGiverInitiatedEditor({
                                        title: recipient.owner.title,
                                        piUsernameHint: recipient.owner.primaryUsername,
                                        projectId: recipient.owner.reference.projectId,
                                        start: currentPeriodStart,
                                        end: currentPeriodEnd,
                                    })}
                                >
                                    <SmallIconButton icon={"heroBanknotes"} subIcon={"heroPlusCircle"}
                                                     subColor1={"white"} subColor2={"white"} />
                                </Link>
                            }

                            {recipient.usageAndQuota.map((uq, idx) =>
                                <ProgressBarWithLabel
                                    key={idx}
                                    value={(uq.usage / uq.quota) * 100}
                                    text={progressText(uq.type, uq)}
                                    width={`${baseProgress}px`}
                                />
                            )}
                        </div>}
                    >
                        {recipient.allocations
                            .filter(alloc => !alloc.note || !alloc.note.hideIfZeroUsage || alloc.usageAndQuota.usage > 0)
                            .map((alloc, idx) =>
                                <TreeNode
                                    key={idx}
                                    className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                    data-ridx={recipientIdx} data-idx={idx}
                                    left={<Flex gap={"4px"}>
                                        <Flex gap={"4px"} width={"200px"}>
                                            <ProviderLogo providerId={alloc.category.provider} size={20}/>
                                            <Icon name={Accounting.productTypeToIcon(alloc.category.productType)}
                                                  size={20}/>
                                            <code>{alloc.category.name}</code>
                                        </Flex>

                                        {alloc.allocationId && <span> <b>Allocation ID:</b> {alloc.allocationId}</span>}
                                    </Flex>}
                                    right={<Flex flexDirection={"row"} gap={"8px"}>
                                        {alloc.note?.rowShouldBeGreyedOut !== true && !alloc.isEditing &&
                                            <SmallIconButton
                                                icon={"heroPencil"} onClick={onEdit}
                                                disabled={state.editControlsDisabled}
                                                data-ridx={recipientIdx} data-idx={idx} />
                                        }
                                        {alloc.note && <>
                                            <TooltipV2 tooltip={alloc.note.text}>
                                                <Icon name={alloc.note.icon} color={alloc.note.iconColor} />
                                            </TooltipV2>
                                        </>}

                                        {alloc.isEditing ?
                                            <Flex gap={"4px"} width={"250px"}>
                                                <Input
                                                    height={"24px"}
                                                    defaultValue={alloc.usageAndQuota.quota}
                                                    autoFocus
                                                    onKeyDown={onEditKey}
                                                    onBlur={onEditBlur}
                                                    data-ridx={recipientIdx} data-idx={idx}
                                                />
                                                {alloc.usageAndQuota.unit}
                                            </Flex>
                                            : <ProgressBarWithLabel
                                                value={(alloc.usageAndQuota.usage / alloc.usageAndQuota.quota) * 100}
                                                text={progressText(alloc.category.productType, alloc.usageAndQuota)}
                                                width={`${baseProgress}px`}
                                            />
                                        }
                                    </Flex>}
                                />
                            )
                        }
                    </TreeNode>
                )}
            </Tree>
        </div>}
    />;
};

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.
const monthNames = [
    "January",
    "February",
    "March",
    "April",
    "May",
    "June",
    "July",
    "August",
    "September",
    "October",
    "November",
    "December"
];

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

function allocationIsActive(
    alloc: Accounting.WalletAllocationV2 | Accounting.SubAllocationV2,
    now: number,
): boolean {
    return periodsOverlap(allocationToPeriod(alloc), { start: now, end: now });
}

function allocationToPeriod(alloc: Accounting.WalletAllocationV2 | Accounting.SubAllocationV2): Period {
    return { start: alloc.startDate, end: alloc.endDate ?? Number.MAX_SAFE_INTEGER };
}

function normalizePeriodForComparison(period: Period): Period {
    return { start: ((period.start / 1000) | 0) * 1000, end: ((period.end / 1000) | 0) * 1000, title: period.title };
}

function periodsOverlap(a: Period, b: Period): boolean {
    return a.start <= b.end && b.start <= a.end;
}

const SmallIconButtonStyle = injectStyle("small-icon-button", k => `
    ${k},
    ${k}:hover {
        color: var(--white) !important;
    }
        
    ${k} {
        height: 25px !important;
        width: 25px !important;
        padding: 12px !important;
    }
    
    ${k} svg {
        margin: 0;
    }
    
    ${k}[data-has-sub=true] svg {
        width: 14px;
        height: 14px;
        position: relative;
        left: -2px;
        top: -1px;
    }
    
    ${k} .sub {
        position: absolute;
    }
    
    ${k} .sub > svg {
        width: 12px;
        height: 12px;
        position: relative;
        left: 5px;
        top: 3px;
    }
`);

const SmallIconButton: React.FunctionComponent<{
    icon: IconName;
    subIcon?: IconName;
    subColor1?: ThemeColor;
    subColor2?: ThemeColor;
    color?: ThemeColor;
    onClick?: (ev: HTMLButtonElement) => void;
    disabled?: boolean;
}> = props => {
    const ref = useRef<HTMLButtonElement>(null);
    const onClick = useCallback(() => {
        props?.onClick?.(ref.current!);
    }, [props.onClick]);

    return <Button
        className={SmallIconButtonStyle}
        onClick={onClick}
        color={props.color}
        disabled={props.disabled}
        btnRef={ref}
        data-has-sub={props.subIcon !== undefined}
        {...extractDataTags(props)}
    >
        <Icon name={props.icon} hoverColor={"white"} />
        {props.subIcon &&
            <div className={"sub"}>
                <Icon name={props.subIcon} hoverColor={props.subColor1} color={props.subColor1}
                      color2={props.subColor2} />
            </div>
        }
    </Button>;
};

// Initial state
// =====================================================================================================================
const initialState: State = {
    periodSelection: {availablePeriods: [], currentPeriodIdx: 0, periodSize: PeriodSize.YEARLY},
    remoteData: {subAllocations: [], wallets: [] },
    subAllocations: {searchQuery: "", searchInflight: 0, recipients: []},
    yourAllocations: {},
    editControlsDisabled: false,
};

export default Allocations;
