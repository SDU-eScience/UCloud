import {extractDataTags, injectStyle} from "@/Unstyled";
import * as React from "react";
import {
    Accordion,
    Box,
    Button, DataList,
    Flex,
    Icon,
    Input, Label,
    Link,
    MainContainer,
    ProgressBarWithLabel, Select, TextArea,
} from "@/ui-components";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import * as Accounting from "@/Accounting";
import {periodsOverlap, ProductType} from "@/Accounting";
import {fuzzySearch, groupBy} from "@/Utilities/CollectionUtilities";
import {useCallback, useEffect, useMemo, useReducer, useRef} from "react";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import {fetchAll} from "@/Utilities/PageUtilities";
import AppRoutes from "@/Routes";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {Avatar} from "@/AvataaarLib";
import {TooltipV2} from "@/ui-components/Tooltip";
import {IconName} from "@/ui-components/Icon";
import {extractErrorMessage, stopPropagation, timestampUnixMs} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {addStandardInputDialog} from "@/UtilityComponents";
import {useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {useAvatars} from "@/AvataaarLib/hook";
import {bulkRequestOf} from "@/DefaultObjects";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Tree, TreeApi, TreeNode} from "@/ui-components/Tree";
import ProvidersApi from "@/UCloud/ProvidersApi";
import WAYF from "@/Grants/wayf-idps.json";
import {MandatoryField} from "@/Applications/Jobs/Widgets";
import * as Gifts from "./Gifts";
import {removePrefixFrom} from "@/Utilities/TextUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

// State
// =====================================================================================================================
interface State {
    remoteData: {
        wallets: Accounting.WalletV2[];
        subAllocations: Accounting.SubAllocationV2[];
        managedProviders: string[];
        managedProducts: Record<string, Accounting.ProductCategoryV2[]>;
        gifts: Gifts.GiftWithCriteria[];
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

                    start: number;
                    end: number;
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

                start: number;
                end: number;
            }[];
        }[];
    };

    gifts?: {
        title: string;
        description: string;
        renewEvery: number;
        domainAllow: string;
        orgAllow: string;
        resources: Record<string, number>;
    }

    rootAllocations?: {
        year: number;
        resources: Record<string, number>;
    }

    editControlsDisabled: boolean;
}

interface AllocationNote {
    rowShouldBeGreyedOut: boolean;
    hideIfZeroUsage?: boolean;
    icon: IconName;
    iconColor: ThemeColor;
    text: string;
}

interface UsageAndQuota {
    usage: number;
    quota: number;
    unit: string;
}

// State reducer
// =====================================================================================================================
type UIAction =
    { type: "WalletsLoaded", wallets: Accounting.WalletV2[]; }
    | { type: "SubAllocationsLoaded", subAllocations: Accounting.SubAllocationV2[] }
    | { type: "ManagedProvidersLoaded", providerIds: string[] }
    | { type: "ManagedProductsLoaded", products: Record<string, Accounting.ProductCategoryV2[]> }
    | { type: "GiftsLoaded", gifts: Gifts.GiftWithCriteria[] }
    | { type: "UpdateSearchQuery", newQuery: string }
    | { type: "SetEditing", recipientIdx: number, allocationIdx: number, isEditing: boolean }
    | { type: "UpdateAllocation", allocationIdx: number, recipientIdx: number, newQuota: number }
    | { type: "MergeSearchResults", subAllocations: Accounting.SubAllocationV2[] }
    | { type: "UpdateSearchInflight", delta: number }
    | { type: "UpdateGift", data: Partial<State["gifts"]> }
    | { type: "GiftCreated", gift: Gifts.GiftWithCriteria }
    | { type: "GiftDeleted", id: number }
    | { type: "UpdateRootAllocations", data: Partial<State["rootAllocations"]> }
    | { type: "ResetRootAllocation" }
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

        case "ManagedProvidersLoaded": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    managedProviders: action.providerIds,
                },
                rootAllocations: {
                    year: new Date().getUTCFullYear(),
                    resources: {},
                },
            };
        }

        case "ManagedProductsLoaded": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    managedProducts: action.products,
                }
            };
        }

        case "GiftsLoaded": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    gifts: action.gifts
                },
                gifts: {
                    title: "",
                    description: "",
                    renewEvery: 0,
                    domainAllow: "",
                    orgAllow: "",
                    resources: {},
                },
            };
        }

        case "UpdateGift": {
            const currentGifts = (state.gifts ?? {
                title: "",
                description: "",
                renewEvery: 0,
                domainAllow: "",
                orgAllow: "",
                resources: {}
            });

            return {
                ...state,
                gifts: {
                    ...currentGifts,
                    ...action.data,
                    resources: {
                        ...currentGifts.resources,
                        ...(action.data?.resources ?? {}),
                    },
                }
            };
        }

        case "UpdateRootAllocations": {
            const currentRoot = state.rootAllocations ?? {
                year: new Date().getUTCFullYear(),
                resources: {},
            };
            return {
                ...state,
                rootAllocations: {
                    ...currentRoot,
                    ...action.data,
                    resources: {
                        ...currentRoot.resources,
                        ...(action.data?.resources ?? {}),
                    },
                }
            };
        }

        case "ResetRootAllocation": {
            return {
                ...state,
                rootAllocations: {
                    year: new Date().getUTCFullYear(),
                    resources: {},
                }
            };
        }

        case "GiftCreated": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    gifts: [...state.remoteData.gifts, action.gift]
                },
                gifts: {
                    title: "",
                    description: "",
                    renewEvery: 0,
                    domainAllow: "",
                    orgAllow: "",
                    resources: {}
                }
            };
        }

        case "GiftDeleted": {
            return {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    gifts: state.remoteData.gifts.filter(it => it.id !== action.id)
                }
            };
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

    function rebuildTree(state: State): State {
        const now = timestampUnixMs();

        function allocationNote(
            alloc: Accounting.WalletAllocationV2 | Accounting.SubAllocationV2
        ): AllocationNote | undefined {
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

            return undefined;
        }

        const walletsInPeriod = state.remoteData.wallets.map(wallet => {
            const newAllocations = wallet.allocations.filter(alloc =>
                !wallet.paysFor.freeToUse
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
            periodsOverlap({start: now, end: now}, allocationToPeriod(alloc))
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
                            },
                            start: alloc.startDate,
                            end: alloc.endDate ?? NO_EXPIRATION_FALLBACK,
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
                    start: alloc.startDate,
                    end: alloc.endDate ?? NO_EXPIRATION_FALLBACK,
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

                fetchManagedProviders().then(providers => {
                    dispatch({type: "ManagedProvidersLoaded", providerIds: providers});

                    Promise.all(
                        providers.map(provider =>
                            fetchAll(next => callAPI(Accounting.browseProductsV2({
                                filterProvider: provider,
                                next,
                                itemsPerPage: 250,
                            }))).then(it => [provider, it]) as Promise<[string, Accounting.ProductV2[]]>
                        )
                    ).then(results => {
                        const merged: Record<string, Accounting.ProductCategoryV2[]> = {};
                        for (const [providerId, page] of results) {
                            const categories: Accounting.ProductCategoryV2[] = [];
                            for (const product of page) {
                                if (product.category.freeToUse) continue;
                                if (categories.some(it => Accounting.categoryComparator(it, product.category) === 0)) {
                                    continue;
                                }

                                categories.push(product.category);
                            }

                            merged[providerId] = categories;
                        }

                        dispatch({type: "ManagedProductsLoaded", products: merged});
                    });

                    fetchAll(next => callAPI(Gifts.browse({itemsPerPage: 250, next}))).then(gifts => {
                        dispatch({type: "GiftsLoaded", gifts});
                    });
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

const giftClass = injectStyle("gift", k => `
    ${k} th, ${k} td {
        padding-bottom: 10px;
    }

    ${k} th {
        vertical-align: top;
        text-align: left;
        padding-right: 20px;
    }
    
    ${k} ul {
        margin: 0;
        padding: 0;
        padding-left: 1em;
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
                        if (box) {
                            box.scrollIntoView({block: "nearest"});
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

    const currentPeriodEnd = useMemo(() => {
        let currentMinimum = NO_EXPIRATION_FALLBACK;
        for (const v of Object.values(state.yourAllocations)) {
            for (const wallet of v.wallets) {
                for (const alloc of wallet.allocations) {
                    if (alloc.end < currentMinimum) {
                        currentMinimum = alloc.end;
                    }
                }
            }
        }
        return currentMinimum;
    }, [state.yourAllocations]);

    const onNewSubProject = useCallback(async () => {
        try {
            const title = (await addStandardInputDialog({
                title: "What should we call your new sub-project?",
                confirmText: "Create sub-project"
            })).result;

            navigate(AppRoutes.grants.grantGiverInitiatedEditor({
                title,
                start: timestampUnixMs(),
                end: currentPeriodEnd,
                piUsernameHint: Client.username ?? "?",
            }));
        } catch (ignored) {
        }
    }, [currentPeriodEnd]);

    const onEdit = useCallback((elem: HTMLElement) => {
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        if (isNaN(idx) || isNaN(ridx)) return;

        dispatchEvent({type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: true});
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

                    dispatchEvent({type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false});
                }
                break;
            }

            case "Escape": {
                dispatchEvent({type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false});
                break;
            }
        }
    }, [state.subAllocations.recipients]);

    const onEditBlur = useCallback((ev: React.SyntheticEvent) => {
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        dispatchEvent({type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, isEditing: false});
    }, [dispatchEvent]);

    const onSearchInput = useCallback((ev: React.SyntheticEvent) => {
        const input = ev.target as HTMLInputElement;
        const newQuery = input.value;
        dispatchEvent({type: "UpdateSearchQuery", newQuery});

        window.clearTimeout(searchTimeout.current);
        searchTimeout.current = window.setTimeout(async () => {
            if (input.disabled) return;
            dispatchEvent({type: "UpdateSearchInflight", delta: 1});
            try {
                const page = await callAPI(Accounting.searchSubAllocations({
                    query: newQuery,
                    itemsPerPage: 250,
                }));

                dispatchEvent({type: "MergeSearchResults", subAllocations: page.items});
            } finally {
                dispatchEvent({type: "UpdateSearchInflight", delta: -1});
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

    const onRootAllocationInput = useCallback((ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const elem = ev.target as (HTMLInputElement | HTMLSelectElement);
        const name = elem.getAttribute("name");
        if (!name) return;
        const value = elem.value;

        switch (name) {
            case "root-year": {
                const year = parseInt(value);
                dispatchEvent({
                    type: "UpdateRootAllocations",
                    data: { year }
                });
                break;
            }
        }

        if (name.startsWith("root-resource-")) {
            const resourceName = removePrefixFrom("root-resource-", name);
            let amount = parseInt(value);
            if (value === "") amount = 0;
            if (!isNaN(amount)) {
                const data = {resources: {}};
                data.resources[resourceName] = amount;
                dispatchEvent({type: "UpdateRootAllocations", data: data});
            }
        }
    }, []);

    const creatingRootAllocation = useRef(false);
    const onCreateRootAllocation = useCallback(async (ev: React.SyntheticEvent) => {
        ev.preventDefault();
        if (creatingRootAllocation.current) return;
        if (!state.rootAllocations) return;

        const start = new Date();
        const end = new Date();
        {
            const year = state.rootAllocations.year;
            start.setUTCFullYear(year, 0, 1);
            start.setUTCHours(0, 0, 0, 0);

            end.setUTCFullYear(year, 11, 31);
            end.setUTCHours(23, 59, 59, 999);
        }

        try {
            const products = state.remoteData.managedProducts;
            creatingRootAllocation.current = true;

            const requests: Accounting.RootAllocateRequestItem[] = [];
            for (const [categoryAndProvider, amount] of Object.entries(state.rootAllocations.resources)) {
                const [category, provider] = categoryAndProvider.split("/");
                const resolvedCategory = products[provider]?.find(it => it.name === category);
                if (!resolvedCategory) {
                    snackbarStore.addFailure("Internal failure while creating a root allocation. Try reloading the page!", false);
                    return;
                }

                const unit = Accounting.explainUnit(resolvedCategory);

                requests.push({
                    owner: {
                        type: "project",
                        projectId: Client.projectId ?? ""
                    },
                    productCategory: {
                        name: category,
                        provider,
                    },
                    quota: amount * unit.invPriceFactor,
                    start: start.getTime(),
                    end: end.getTime(),
                });
            }

            await callAPI(Accounting.rootAllocate(bulkRequestOf(...requests)));
            dispatchEvent({type: "ResetRootAllocation"});
            dispatchEvent({type: "Init"});
        } catch (e) {
            snackbarStore.addFailure("Failed to create root allocation: " + extractErrorMessage(e), false);
            return;
        } finally {
            creatingRootAllocation.current = false;
        }
    }, [state.rootAllocations]);

    const onGiftInput = useCallback((ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const elem = ev.target as (HTMLInputElement | HTMLSelectElement);
        const name = elem.getAttribute("name");
        if (!name) return;
        const value = elem.value;
        switch (name) {
            case "gift-title": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {title: value}
                });
                break;
            }

            case "gift-description": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {description: value}
                });
                break;
            }

            case "gift-renewal": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {renewEvery: parseInt(value)}
                });
                break;
            }

            case "gift-allow-domain": {
                dispatchEvent({
                    type: "UpdateGift",
                    data: {domainAllow: value}
                });
                break;
            }
        }

        if (name.startsWith("gift-resource-")) {
            const resourceName = removePrefixFrom("gift-resource-", name);
            let amount = parseInt(value);
            if (value === "") amount = 0;
            if (!isNaN(amount)) {
                const data = {resources: {}};
                data.resources[resourceName] = amount;
                dispatchEvent({type: "UpdateGift", data: data});
            }
        }
    }, []);

    const onGiftOrgInput = useCallback((orgId: string) => {
        dispatchEvent({type: "UpdateGift", data: {orgAllow: orgId}});
    }, []);

    const creatingGift = useRef(false);
    const onCreateGift = useCallback(async (ev: React.SyntheticEvent) => {
        ev.preventDefault();
        if (!state.gifts) return;
        if (creatingGift.current) return;
        const gift: Gifts.GiftWithCriteria = {
            id: 0,
            criteria: [],
            description: state.gifts.description,
            resources: [],
            resourcesOwnedBy: Client.projectId ?? "",
            title: state.gifts.title,
        };

        if (state.gifts.domainAllow) {
            const domains = state.gifts.domainAllow.split(",").map(it => it.trim());
            for (const domain of domains) {
                if (domain === "all-ucloud-users") {
                    // NOTE(Dan): undocumented because most people really should not do this
                    gift.criteria.push({type: "anyone"});
                } else {
                    gift.criteria.push({
                        type: "email",
                        domain: domain,
                    });
                }
            }
        }

        if (state.gifts.orgAllow) {
            gift.criteria.push({
                type: "wayf",
                org: state.gifts.orgAllow
            });
        }

        const products = state.remoteData.managedProducts;
        for (const [categoryAndProvider, amount] of Object.entries(state.gifts.resources)) {
            const [category, provider] = categoryAndProvider.split("/");
            const resolvedCategory = products[provider]?.find(it => it.name === category);
            if (!resolvedCategory) {
                snackbarStore.addFailure("Internal failure while creating gift. Try reloading the page!", false);
                return;
            }
            const unit = Accounting.explainUnit(resolvedCategory);
            const actualAmount = amount * unit.invPriceFactor;
            if (actualAmount === 0) continue;

            gift.resources.push({
                balanceRequested: actualAmount,
                category: category,
                provider: provider,
                grantGiver: "",
                period: {start: 0, end: 0},
                sourceAllocation: null,
            });
        }

        if (gift.criteria.length === 0) {
            snackbarStore.addFailure("Missing user criteria. You must define at least one!", false);
            return;
        }

        if (gift.resources.length === 0) {
            snackbarStore.addFailure("A gift must contain at least one resource!", false);
            return;
        }

        try {
            creatingGift.current = true;
            const {id} = await callAPI(Gifts.create(gift));
            gift.id = id;
            dispatchEvent({type: "GiftCreated", gift});
        } catch (e) {
            snackbarStore.addFailure("Failed to create a gift: " + extractErrorMessage(e), false);
        } finally {
            creatingGift.current = false;
        }
    }, [state.gifts]);

    const onDeleteGift = useCallback(async (giftIdString?: string) => {
        if (!giftIdString) return;
        const id = parseInt(giftIdString);
        if (isNaN(id)) return;

        try {
            await callAPI(Gifts.remove({ giftId: id }));
        } catch (e) {
            snackbarStore.addFailure("Failed to delete gift: " + extractErrorMessage(e), false);
            return;
        }

        dispatchEvent({type: "GiftDeleted", id});
    }, []);

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

            {state.remoteData.managedProviders.length > 0 && <>
                {state.rootAllocations && <>
                    <h3>Root allocations</h3>
                    <div>
                        Root allocations are ordinary allocations from which all other allocations are created.

                        <ul>
                            <li>You can see this because you are part of a provider project</li>
                            <li>You must create a root allocation to be able to use your provider</li>
                            <li>Once created, you can see the root allocations in the "Your allocations" panel</li>
                        </ul>
                    </div>

                    <Accordion title={"Create a new root allocation"}>
                        <h4>Step 1: Select a period</h4>
                        <Select
                            slim
                            value={state.rootAllocations.year}
                            onInput={onRootAllocationInput}
                            onKeyDown={stopPropagation}
                            name={"root-year"}
                        >
                            {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(delta => {
                                const year = new Date().getUTCFullYear() + delta;
                                return <option key={delta} value={year.toString()}>{year}</option>;
                            })}
                        </Select>

                        <h4>Step 2: Select allocation size</h4>
                        <Tree>
                            {Object.entries(state.remoteData.managedProducts).map(([providerId, page]) => <React.Fragment
                                key={providerId}>
                                {page.map(cat => <TreeNode
                                    key={cat.name + cat.provider}
                                    left={<Flex gap={"4px"}>
                                        <Icon name={Accounting.productTypeToIcon(cat.productType)} size={20}/>
                                        <code>{cat.name} / {cat.provider}</code>
                                    </Flex>}
                                    right={<Flex gap={"4px"}>
                                        <Input
                                            height={20}
                                            placeholder={"0"}
                                            name={`root-resource-${cat.name}/${cat.provider}`}
                                            value={state.rootAllocations?.resources?.[`${cat.name}/${cat.provider}`] ?? ""}
                                            onInput={onRootAllocationInput}
                                            onKeyDown={stopPropagation}
                                        />
                                        <Box width={"150px"}>{Accounting.explainUnit(cat).name}</Box>
                                    </Flex>}
                                />)}
                            </React.Fragment>)}
                        </Tree>

                        <Button my={16} onClick={onCreateRootAllocation}>Create root allocations</Button>
                    </Accordion>

                    <Box mt={32}/>
                </>}

                {state.gifts && <>
                    <h3>Gifts</h3>
                    <div>
                        Gifts are free resources which are automatically claimed by active UCloud users fulfilling
                        certain
                        criteria.
                        <ul>
                            <li>As a provider, you can see your gifts and define new gifts</li>
                            <li>You can delete gifts, but this will not retract the gifts that have already been claimed
                            </li>
                        </ul>
                    </div>

                    <Accordion title={`View existing gifts (${state.remoteData.gifts.length})`}>
                        {state.remoteData.gifts.length === 0 ? <>This project currently has no active gifts!</> : <Tree>
                            {state.remoteData.gifts.map(g =>
                                <TreeNode
                                    key={g.id}
                                    left={g.title}
                                >
                                    <table className={giftClass}>
                                        <tbody>
                                        <tr>
                                            <th>Description</th>
                                            <td>{g.description}</td>
                                        </tr>
                                        <tr>
                                            <th>Criteria</th>
                                            <td>
                                                <ul>
                                                    {g.criteria.map(c => {
                                                        switch (c.type) {
                                                            case "anyone":
                                                                return <li key={c.type}>All UCloud users</li>
                                                            case "wayf":
                                                                return <li key={c.org + "wayf"}>Users from <i>{c.org}</i></li>
                                                            case "email":
                                                                return <li key={c.domain + "email"}>@{c.domain}</li>
                                                        }
                                                    })}
                                                </ul>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th>Resources</th>
                                            <td>
                                                <ul>
                                                    {g.resources.map(r => {
                                                        const pc = state.remoteData.managedProducts[r.provider]?.find(it => it.name === r.category);
                                                        if (!pc) return null;
                                                        return <li>{r.category} / {r.provider}: {Accounting.balanceToString(pc, r.balanceRequested)}</li>
                                                    })}
                                                </ul>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th>Delete</th>
                                            <td>
                                                <ConfirmationButton
                                                    actionText={"Delete"}
                                                    icon={"heroTrash"}
                                                    onAction={onDeleteGift}
                                                    actionKey={g.id.toString()}
                                                />
                                            </td>
                                        </tr>
                                        </tbody>
                                    </table>
                                </TreeNode>
                            )}
                        </Tree>}
                    </Accordion>

                    <Accordion title={"Create a gift"}>
                        <form onSubmit={onCreateGift}>
                            <Flex gap={"8px"} flexDirection={"column"}>
                                <Label>
                                    Title <MandatoryField/>
                                    <Input
                                        name={"gift-title"}
                                        value={state.gifts.title}
                                        onInput={onGiftInput}
                                        onKeyDown={stopPropagation}
                                        placeholder={"For example: Gift for employees at SDU"}
                                    />
                                </Label>
                                <Label>
                                    Description:

                                    <TextArea
                                        name={"gift-description"}
                                        value={state.gifts.description}
                                        rows={3}
                                        onInput={onGiftInput}
                                        onKeyDown={stopPropagation}
                                    />
                                </Label>
                                <Label>
                                    Is this gift periodically renewed or a one-time grant? <MandatoryField/>
                                    <Select
                                        name={"gift-renewal"}
                                        slim
                                        value={state.gifts.renewEvery}
                                        onInput={onGiftInput}
                                        onKeyDown={stopPropagation}
                                    >
                                        <option value={"0"}>One-time per-user grant</option>
                                        <option value={"1"}>Renew every month</option>
                                        <option value={"6"}>Renew every 6 months</option>
                                        <option value={"12"}>Renew every 12 months</option>
                                    </Select>
                                </Label>
                                <Label>
                                    Allow if user belongs to this organization
                                    <DataList
                                        options={wayfIdpsPairs}
                                        onSelect={onGiftOrgInput}
                                        placeholder={"Type to search..."}
                                    />
                                </Label>
                                <Label>
                                    Allow if email domain matches any of the following (comma-separated)
                                    <Input
                                        name={"gift-allow-domain"}
                                        placeholder={"For example: sdu.dk, cloud.sdu.dk"}
                                        onInput={onGiftInput}
                                        value={state.gifts.domainAllow}
                                        onKeyDown={stopPropagation}
                                    />
                                </Label>

                                <Label>Resources</Label>
                                <Tree>
                                    {Object.entries(state.remoteData.managedProducts).map(([providerId, page]) =>
                                        <React.Fragment
                                            key={providerId}>
                                            {page.map(cat => <TreeNode
                                                key={cat.name + cat.provider}
                                                left={<Flex gap={"4px"}>
                                                    <Icon name={Accounting.productTypeToIcon(cat.productType)}
                                                          size={20}/>
                                                    <code>{cat.name} / {cat.provider}</code>
                                                </Flex>}
                                                right={<Flex gap={"4px"}>
                                                    <Input
                                                        height={20}
                                                        placeholder={"0"}
                                                        name={`gift-resource-${cat.name}/${cat.provider}`}
                                                        onInput={onGiftInput}
                                                        value={state.gifts?.resources?.[`${cat.name}/${cat.provider}`] ?? ""}
                                                        onKeyDown={stopPropagation}
                                                    />
                                                    <Box width={"150px"}>{Accounting.explainUnit(cat).name}</Box>
                                                </Flex>}
                                            />)}
                                        </React.Fragment>)}
                                </Tree>
                            </Flex>
                            <Button type={"submit"}>
                                Create gift
                            </Button>
                        </form>
                    </Accordion>

                    <Box mt={32}/>
                </>}
            </>}

            <h3>Your allocations</h3>
            {sortedAllocations.length !== 0 ? null : <>
                You do not have any allocations at the moment. You can apply for resources{" "}
                <Link to={AppRoutes.grants.editor()}>here</Link>.
            </>}
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
                                                <Icon name={"heroBanknotes"} mr={4}/>
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
                                                        <Icon name={alloc.note.icon} color={alloc.note.iconColor}/>
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
                <Box flexGrow={1}/>
                <Button height={35} onClick={onNewSubProject}>
                    <Icon name={"heroPlus"} mr={8}/>
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
                                <Icon name={"heroMagnifyingGlass"}/>
                                : <HexSpin size={18} margin={"0"}/>
                            }
                        </div>
                    </div>
                </Box>
            </Flex>

            {state.subAllocations.recipients.length !== 0 ? null : <>
                You do not have any sub-allocations at the moment. You can create a sub-project by clicking{" "}
                <a href="#" onClick={onNewSubProject}>here</a>.
            </>}

            <Tree apiRef={suballocationTree} unhandledShortcut={onSubAllocationShortcut}>
                {state.subAllocations.recipients.map((recipient, recipientIdx) =>
                    <TreeNode
                        key={recipientIdx}
                        left={<Flex gap={"4px"} alignItems={"center"}>
                            <TooltipV2 tooltip={`Workspace PI: ${recipient.owner.primaryUsername}`}>
                                <Avatar {...avatars.avatar(recipient.owner.primaryUsername)}
                                        style={{height: "32px", width: "auto", marginTop: "-4px"}}
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
                                        start: timestampUnixMs(),
                                        end: recipient.allocations.reduce((prev, it) => Math.min(prev, it.end), NO_EXPIRATION_FALLBACK),
                                    })}
                                >
                                    <SmallIconButton icon={"heroBanknotes"} subIcon={"heroPlusCircle"}
                                                     subColor1={"white"} subColor2={"white"}/>
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
                                                data-ridx={recipientIdx} data-idx={idx}/>
                                        }
                                        {alloc.note && <>
                                            <TooltipV2 tooltip={alloc.note.text}>
                                                <Icon name={alloc.note.icon} color={alloc.note.iconColor}/>
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
    return periodsOverlap(allocationToPeriod(alloc), {start: now, end: now});
}

interface Period {
    start: number;
    end: number;
}

function allocationToPeriod(alloc: Accounting.WalletAllocationV2 | Accounting.SubAllocationV2): Period {
    return {start: alloc.startDate, end: alloc.endDate ?? NO_EXPIRATION_FALLBACK};
}

function normalizePeriodForComparison(period: Period): Period {
    return {start: Math.floor(period.start / 1000) * 1000, end: Math.floor(period.end / 1000) * 1000};
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
        <Icon name={props.icon} hoverColor={"white"}/>
        {props.subIcon &&
            <div className={"sub"}>
                <Icon name={props.subIcon} hoverColor={props.subColor1} color={props.subColor1}
                      color2={props.subColor2}/>
            </div>
        }
    </Button>;
};

async function fetchManagedProviders(): Promise<string[]> {
    const items = await fetchAll(next => callAPI(ProvidersApi.browse({itemsPerPage: 250, next})));
    return items.map(it => it.specification.id);
}

// Initial state
// =====================================================================================================================
const initialState: State = {
    remoteData: {subAllocations: [], wallets: [], managedProviders: [], managedProducts: {}, gifts: []},
    subAllocations: {searchQuery: "", searchInflight: 0, recipients: []},
    yourAllocations: {},
    editControlsDisabled: false,
};

const NO_EXPIRATION_FALLBACK = 4102444800353;

export default Allocations;
