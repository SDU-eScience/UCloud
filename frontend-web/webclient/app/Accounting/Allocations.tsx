import {extractDataTags, injectStyle} from "@/Unstyled";
import * as React from "react";
import {useCallback, useEffect, useMemo, useReducer, useRef} from "react";
import {
    Accordion,
    Box,
    Button,
    Checkbox,
    DataList,
    Divider,
    Flex,
    Icon,
    Input,
    Label,
    Link,
    MainContainer,
    Relative,
    Select,
    TextArea,
} from "@/ui-components";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import * as Accounting from "@/Accounting";
import {periodsOverlap, ProductType, WalletOwner} from "@/Accounting";
import {deepCopy, fuzzyMatch, groupBy} from "@/Utilities/CollectionUtilities";
import {useProjectId} from "@/Project/Api";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
import {fetchAll} from "@/Utilities/PageUtilities";
import AppRoutes from "@/Routes";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {TooltipV2} from "@/ui-components/Tooltip";
import {IconName} from "@/ui-components/Icon";
import {
    bulkRequestOf,
    chunkedString,
    doNothing,
    extractErrorMessage,
    stopPropagation,
    timestampUnixMs
} from "@/UtilityFunctions";
import {ThemeColor} from "@/ui-components/theme";
import {useNavigate} from "react-router";
import {Client} from "@/Authentication/HttpClientInstance";
import {useAvatars} from "@/AvataaarLib/hook";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {Tree, TreeApi, TreeNode} from "@/ui-components/Tree";
import ProvidersApi from "@/UCloud/ProvidersApi";
import WAYF from "@/Grants/wayf-idps.json";
import {MandatoryField} from "@/Applications/Jobs/Widgets";
import * as Gifts from "./Gifts";
import {removePrefixFrom} from "@/Utilities/TextUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {dialogStore} from "@/Dialog/DialogStore";
import * as Heading from "@/ui-components/Heading";
import {checkCanConsumeResources} from "@/ui-components/ResourceBrowser";
import Avatar from "@/AvataaarLib/avatar";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {usePage} from "@/Navigation/Redux";
import {dateToStringNoTime} from "@/Utilities/DateUtilities";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {useProject} from "@/Project/cache";
import {OldProjectRole} from "@/Project";

const wayfIdpsPairs = WAYF.wayfIdps.map(it => ({value: it, content: it}));

// State
// =====================================================================================================================
interface State {
    remoteData: {
        wallets: Accounting.WalletV2[];
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
                    id: number;
                    grantedIn?: number;
                    quota: number;
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

            groups: {
                category: Accounting.ProductCategoryV2;
                usageAndQuota: UsageAndQuota;

                allocations: {
                    allocationId: number;
                    quota: number;
                    note?: AllocationNote;
                    isEditing: boolean;
                    grantedIn?: number;

                    start: number;
                    end: number;
                }[];
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
    icon: IconName;
    iconColor: ThemeColor;
    text: string;
}

interface UsageAndQuota {
    usage: number;
    quota: number;
    unit: string;
    maxUsable: number;
}

// State reducer
// =====================================================================================================================
type UIAction =
    { type: "WalletsLoaded", wallets: Accounting.WalletV2[]; }
    | { type: "ManagedProvidersLoaded", providerIds: string[] }
    | { type: "ManagedProductsLoaded", products: Record<string, Accounting.ProductCategoryV2[]> }
    | { type: "GiftsLoaded", gifts: Gifts.GiftWithCriteria[] }
    | { type: "UpdateSearchQuery", newQuery: string }
    | { type: "SetEditing", recipientIdx: number, groupIdx: number, allocationIdx: number, isEditing: boolean }
    | { type: "UpdateAllocation", allocationIdx: number, groupIdx: number, recipientIdx: number, newQuota: number }
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

        /*
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
         */

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
            const group = getOrNull(recipient.groups, action.groupIdx);
            if (!group) return state;
            const allocation = getOrNull(group.allocations, action.allocationIdx);
            if (!allocation) return state;

            const newState: State = {
                ...state,
                subAllocations: {
                    ...state.subAllocations,
                    recipients: [...state.subAllocations.recipients]
                }
            };

            const newRecipient = newState.subAllocations.recipients[action.recipientIdx];
            newRecipient.groups = [...newRecipient.groups];
            newRecipient.groups[action.groupIdx].allocations = [...newRecipient.groups[action.groupIdx].allocations];
            newRecipient.groups[action.groupIdx].allocations[action.allocationIdx] = {
                ...allocation,
                isEditing: action.isEditing
            };

            newState.editControlsDisabled = action.isEditing;

            return newState;
        }

        case "UpdateAllocation": {
            const recipient = getOrNull(state.subAllocations.recipients, action.recipientIdx);
            if (!recipient) return state;
            const group = getOrNull(recipient.groups, action.groupIdx);
            if (!group) return state;
            const allocation = getOrNull(group.allocations, action.allocationIdx);
            if (!allocation) return state;

            const allocationId = allocation.allocationId;
            const newWallets = deepCopy(state.remoteData.wallets);
            const newState: State = {
                ...state,
                remoteData: {
                    ...state.remoteData,
                    wallets: newWallets,
                },
            };

            outer: for (const wallet of newState.remoteData.wallets) {
                const allChildren = wallet.children ?? [];
                for (const childGroup of allChildren) {
                    for (const alloc of childGroup.group.allocations) {
                        if (alloc.id === allocationId) {
                            alloc.quota = action.newQuota;
                            break outer;
                        }
                    }
                }
            }

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
            alloc: Accounting.Allocation
        ): AllocationNote | undefined {
            // NOTE(Dan): We color code and potentially grey out rows when the end-user should be aware of something
            // on the allocation.
            //
            // - If a row should be greyed out, then it means that the row is not currently active (and is not counted
            //   in summaries).
            // - If the accompanying row has a red calendar, then the note is about something which has happened.
            // - If the accompanying row has a blue calendar, then the note is about something which will happen.
            const icon: IconName = "heroCalendarDays";
            const colorInThePast: ThemeColor = "errorMain";
            const colorForTheFuture: ThemeColor = "primaryMain";

            const allocPeriod = normalizePeriodForComparison(allocationToPeriod(alloc));
            if (now > allocPeriod.end) {
                return {
                    rowShouldBeGreyedOut: true,
                    icon,
                    iconColor: colorInThePast,
                    text: `Already expired (${Accounting.utcDate(allocPeriod.end)})`,
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

        const walletsInPeriod = state.remoteData.wallets.filter(wallet => {
            return !wallet.paysFor.freeToUse
        });

        const filteredSubAllocations: {
            wallet: Accounting.WalletV2,
            childGroup: Accounting.AllocationGroupWithChild
        }[] = [];
        for (const wallet of state.remoteData.wallets) {
            if (wallet.paysFor.freeToUse) continue;

            const children = wallet.children ?? [];
            for (const childGroup of children) {
                const query = state.subAllocations.searchQuery;
                let matches = query === "";

                if (!matches) {
                    matches = fuzzyMatch(
                        childGroup.child,
                        ["projectId", "projectTitle"],
                        query,
                    );
                }

                // TODO more checks?
                if (matches) {
                    filteredSubAllocations.push({wallet, childGroup});
                }
            }
        }

        // Build the "Your allocations" tree
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
                    ({balance: wallet.quota, category: wallet.paysFor})
                );
                const usageBalances = wallets.flatMap(wallet =>
                    ({balance: wallet.totalUsage, category: wallet.paysFor})
                );

                const maxUsableBalances = wallets.map(wallet =>
                    ({balance: wallet.maxUsable, category: wallet.paysFor})
                );

                const combinedQuotas = Accounting.combineBalances(quotaBalances);
                const combinedUsage = Accounting.combineBalances(usageBalances);
                const combineMaxUsable = Accounting.combineBalances(maxUsableBalances);

                for (let i = 0; i < combinedQuotas.length; i++) {
                    const usage = combinedUsage[i];
                    const quota = combinedQuotas[i];
                    const maxUsable = combineMaxUsable[i]

                    entry.usageAndQuota.push({
                        usage: usage.normalizedBalance,
                        quota: quota.normalizedBalance,
                        unit: usage.unit,
                        maxUsable: maxUsable.normalizedBalance
                    });
                }

                for (const wallet of wallets) {
                    const usage = Accounting.combineBalances([{balance: wallet.totalUsage, category: wallet.paysFor}]);
                    const quota = Accounting.combineBalances([{balance: wallet.quota, category: wallet.paysFor}]);
                    const maxUsable = Accounting.combineBalances([{
                        balance: wallet.maxUsable,
                        category: wallet.paysFor
                    }]);

                    entry.wallets.push({
                        category: wallet.paysFor,

                        usageAndQuota: {
                            usage: usage?.[0]?.normalizedBalance ?? 0,
                            quota: quota?.[0]?.normalizedBalance ?? 0,
                            unit: usage?.[0]?.unit ?? "",
                            maxUsable: maxUsable?.[0]?.normalizedBalance ?? 0
                        },

                        allocations: wallet.allocationGroups.flatMap(({group}) =>
                            group.allocations.map(alloc => ({
                                id: alloc.id,
                                grantedIn: alloc.grantedIn ?? undefined,
                                note: allocationNote(alloc),
                                quota: alloc.quota,
                                start: alloc.startDate,
                                end: alloc.endDate ?? NO_EXPIRATION_FALLBACK,
                            }))
                        ),
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
            for (const {childGroup} of filteredSubAllocations) {
                let allocOwner: WalletOwner;
                if (childGroup.child.projectId) {
                    allocOwner = {type: "project", projectId: childGroup.child.projectId};
                } else {
                    allocOwner = {type: "user", username: childGroup.child.projectTitle};
                }

                let recipient = subAllocations.recipients
                    .find(it => Accounting.walletOwnerEquals(it.owner.reference, allocOwner));
                if (!recipient) {
                    recipient = {
                        owner: {
                            reference: allocOwner,
                            primaryUsername: childGroup.child.projectTitle, // TODO!
                            title: childGroup.child.projectTitle,
                        },
                        groups: [],
                        usageAndQuota: []
                    };

                    subAllocations.recipients.push(recipient);
                }
            }

            for (const {childGroup, wallet} of filteredSubAllocations) {
                let allocOwner: WalletOwner;
                if (childGroup.child.projectId) {
                    allocOwner = {type: "project", projectId: childGroup.child.projectId};
                } else {
                    allocOwner = {type: "user", username: childGroup.child.projectTitle};
                }

                const recipient = subAllocations.recipients
                    .find(it => Accounting.walletOwnerEquals(it.owner.reference, allocOwner))!;

                const combinedUsage = childGroup.group.usage;
                const combinedQuota = childGroup.group.allocations.reduce((acc, val) => acc + val.quota, 0);

                const usage = Accounting.combineBalances([{balance: combinedUsage, category: wallet.paysFor}]);
                const quota = Accounting.combineBalances([{balance: combinedQuota, category: wallet.paysFor}]);

                const newGroup: State["subAllocations"]["recipients"][0]["groups"][0] = {
                    category: wallet.paysFor,
                    usageAndQuota: {
                        usage: usage?.[0]?.normalizedBalance ?? 0,
                        quota: quota?.[0]?.normalizedBalance ?? 0,
                        maxUsable: 0,
                        unit: usage?.[0]?.unit ?? ""
                    },
                    allocations: [],
                };

                const uq = newGroup.usageAndQuota;
                uq.maxUsable = uq.quota - uq.usage;

                for (const alloc of childGroup.group.allocations) {
                    newGroup.allocations.push({
                        allocationId: alloc.id,
                        quota: alloc.quota,
                        note: allocationNote(alloc),
                        isEditing: false,
                        start: alloc.startDate,
                        end: alloc.endDate,
                        grantedIn: alloc.grantedIn ?? undefined,
                    });
                }

                recipient.groups.push(newGroup);
            }

            for (const recipient of subAllocations.recipients) {
                const uqBuilder: {
                    type: Accounting.ProductType,
                    unit: string,
                    usage: number,
                    quota: number,
                    maxUsable: number
                }[] = [];
                for (const group of recipient.groups) {
                    const existing = uqBuilder.find(it =>
                        it.type === group.category.productType && it.unit === group.usageAndQuota.unit);

                    if (existing) {
                        existing.usage += group.usageAndQuota.usage;
                        existing.quota += group.usageAndQuota.quota;
                    } else {
                        uqBuilder.push({
                            type: group.category.productType,
                            usage: group.usageAndQuota.usage,
                            quota: group.usageAndQuota.quota,
                            unit: group.usageAndQuota.unit,
                            maxUsable: group.usageAndQuota.maxUsable
                        });
                    }
                }

                recipient.groups.sort((a, b) => {
                    const providerCmp = a.category.provider.localeCompare(b.category.provider);
                    if (providerCmp !== 0) return providerCmp;
                    const categoryCmp = a.category.name.localeCompare(b.category.name);
                    if (categoryCmp !== 0) return categoryCmp;
                    return 0;
                });

                for (const b of uqBuilder) {
                    // NOTE(Dan): We do not know how much is usable locally since this depends on other allocations which
                    // might not be coming from us. The backend doesn't tell us this since it would leak information we do
                    // not have access to.
                    //
                    // As a result, we set the maxUsable to be equivalent to the remaining balance. That is, we tell the UI
                    // that we can use the entire quota (even if we cannot).
                    b.maxUsable = b.quota - b.usage;
                }

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

function useStateReducerMiddleware(didCancel: React.RefObject<boolean>, doDispatch: (action: UIAction) => void): (event: UIEvent) => unknown {
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
                        next,
                        includeChildren: true,
                    }))
                ).then(wallets => {
                    dispatch({type: "WalletsLoaded", wallets});
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
    const didCancel = useDidUnmount();
    const projectId = useProjectId();
    const navigate = useNavigate();
    const [state, rawDispatch] = useReducer(stateReducer, initialState);
    const dispatchEvent = useStateReducerMiddleware(didCancel, rawDispatch);
    const avatars = useAvatars();
    const searchTimeout = useRef<number>(0);
    const allocationTree = useRef<TreeApi>(null);
    const suballocationTree = useRef<TreeApi>(null);
    const searchBox = useRef<HTMLInputElement>(null);
    const projectState = useProject();
    const projectRole = projectState.fetch().status.myRole ?? OldProjectRole.USER;

    usePage("Allocations", SidebarTabId.PROJECT);

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
        dialogStore.addDialog(
            <form onSubmit={ev => {
                ev.preventDefault();

                const name = document.querySelector<HTMLInputElement>("#subproject-name");
                if (!name) return;
                if (!name.value) {
                    snackbarStore.addFailure("Missing name", false);
                    return;
                }

                const subAllocatorCheckbox = document.querySelector<HTMLInputElement>("#subproject-suballocator");
                const subAllocator = subAllocatorCheckbox?.checked === true;

                dialogStore.success();
                navigate(AppRoutes.grants.grantGiverInitiatedEditor({
                    title: name.value,
                    start: timestampUnixMs(),
                    end: currentPeriodEnd,
                    piUsernameHint: Client.username ?? "?",
                    subAllocator,
                }));
            }}>
                <div>
                    <Heading.h3>New sub-project</Heading.h3>
                    <Divider/>
                    <Label>
                        Project title
                        <Input id={"subproject-name"} autoFocus/>
                    </Label>
                    {state.remoteData.managedProviders.length > 0 || !checkCanConsumeResources(Client.projectId ?? null, {api: {isCoreResource: false}}) ?
                        <Label>
                            <Checkbox id={"subproject-suballocator"}/>
                            This sub-project is a sub-allocator
                        </Label> : null
                    }
                </div>
                <Flex mt="20px">
                    <Button type={"button"} onClick={dialogStore.failure.bind(dialogStore)} color={"errorMain"}
                            mr="5px">Cancel</Button>
                    <Button type={"submit"} color={"successMain"}>Create sub-project</Button>
                </Flex>
            </form>,
            doNothing
        );
    }, [currentPeriodEnd, state.remoteData.managedProviders.length]);

    const onEdit = useCallback((elem: HTMLElement) => {
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        if (isNaN(idx) || isNaN(ridx) || isNaN(gidx)) return;

        dispatchEvent({type: "SetEditing", allocationIdx: idx, groupIdx: gidx, recipientIdx: ridx, isEditing: true});
    }, []);

    const onEditKey = useCallback(async (ev: React.KeyboardEvent) => {
        ev.stopPropagation();
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        switch (ev.code) {
            case "Enter": {
                const value = parseInt(elem.value);
                if (!isNaN(value)) {
                    const group = state.subAllocations.recipients[ridx].groups[gidx];
                    const alloc = group.allocations[idx]!;
                    const unit = Accounting.explainUnit(group.category);
                    const success = (await callAPIWithErrorHandler(
                        Accounting.updateAllocationV2(bulkRequestOf({
                            allocationId: alloc.allocationId,
                            newQuota: value * unit.invBalanceFactor,
                            reason: "Allocation updated with new quota",
                        }))
                    )) !== null;

                    if (success) {
                        dispatchEvent({
                            type: "UpdateAllocation",
                            allocationIdx: idx,
                            recipientIdx: ridx,
                            groupIdx: gidx,
                            newQuota: value * unit.invBalanceFactor,
                        });
                    }

                    dispatchEvent({
                        type: "SetEditing", allocationIdx: idx, groupIdx: gidx, recipientIdx: ridx,
                        isEditing: false
                    });
                }
                break;
            }

            case "Escape": {
                dispatchEvent({
                    type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, groupIdx: gidx,
                    isEditing: false
                });
                break;
            }
        }
    }, [state.subAllocations.recipients]);

    const onEditBlur = useCallback((ev: React.SyntheticEvent) => {
        const elem = ev.target as HTMLInputElement;
        const idx = parseInt(elem.getAttribute("data-idx") ?? "");
        const ridx = parseInt(elem.getAttribute("data-ridx") ?? "");
        const gidx = parseInt(elem.getAttribute("data-gidx") ?? "");
        dispatchEvent({
            type: "SetEditing", allocationIdx: idx, recipientIdx: ridx, groupIdx: gidx,
            isEditing: false
        });
    }, [dispatchEvent]);

    const onSearchInput = useCallback((ev: React.SyntheticEvent) => {
        const input = ev.target as HTMLInputElement;
        const newQuery = input.value;
        dispatchEvent({type: "UpdateSearchQuery", newQuery});
    }, [dispatchEvent]);

    const onSearchKey = useCallback<React.KeyboardEventHandler>(ev => {
        ev.stopPropagation();
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
                    data: {year}
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
                    category: {
                        name: category,
                        provider,
                    },
                    quota: amount * unit.invBalanceFactor,
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
            renewEvery: state.gifts.renewEvery
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
            const actualAmount = amount * unit.invBalanceFactor;
            if (actualAmount === 0) continue;

            gift.resources.push({
                balanceRequested: actualAmount,
                category: category,
                provider: provider,
                grantGiver: "",
                period: {start: 0, end: 0},
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
            snackbarStore.addSuccess("Gift Created", false);
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
            await callAPI(Gifts.remove({giftId: id}));
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
                            {Object.entries(state.remoteData.managedProducts).map(([providerId, page]) =>
                                <React.Fragment
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
                                                                return <li key={c.org + "wayf"}>Users
                                                                    from <i>{c.org}</i></li>
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
                                                    {g.resources.map((r, idx) => {
                                                        const pc = state.remoteData.managedProducts[r.provider]?.find(it => it.name === r.category);
                                                        if (!pc) return null;
                                                        return <li
                                                            key={idx}>{r.category} / {r.provider}: {Accounting.balanceToString(pc, r.balanceRequested)}</li>
                                                    })}
                                                </ul>
                                            </td>
                                        </tr>
                                        <tr>
                                            <th>Granted</th>
                                            <td>
                                                {g.renewEvery == 0 ? "Once" : (g.renewEvery == 1 ? "Every month" : "Every " + g.renewEvery.toString() + " months")}
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
                        right={<Flex flexDirection={"row"} gap={"8px"}>
                            {tree.usageAndQuota.map((uq, idx) => <React.Fragment key={idx}>
                                    <ProgressBar uq={uq} type={type}/>
                                </React.Fragment>
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
                                right={<Flex flexDirection={"row"} gap={"8px"}>
                                    <ProgressBar uq={wallet.usageAndQuota} type={type}/>
                                </Flex>}
                                indent={indent * 2}
                            >
                                {wallet.allocations
                                    .map(alloc =>
                                        <TreeNode
                                            key={alloc.id}
                                            className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                            left={<Flex gap={"32px"}>
                                                <Flex width={"200px"}>
                                                    <Icon name={"heroBanknotes"} ml={"8px"} mr={4}/>
                                                    <div>
                                                        <b>Allocation ID:</b>
                                                        {" "}
                                                        <code>{chunkedString(alloc.id.toString().padStart(6, "0"), 3, false).join(" ")}</code>
                                                    </div>
                                                </Flex>

                                                <Flex width={"250px"}>
                                                    Period:
                                                    {" "}
                                                    {dateToStringNoTime(alloc.start)}
                                                    {" "}&mdash;{" "}
                                                    {dateToStringNoTime(alloc.end)}
                                                </Flex>

                                                {alloc.grantedIn && <>
                                                    <Link target={"_blank"}
                                                          to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                        View grant application{" "}
                                                        <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                                    </Link>
                                                </>}
                                            </Flex>}
                                            right={<Flex flexDirection={"row"} gap={"8px"}>
                                                {alloc.note && <>
                                                    <TooltipV2 tooltip={alloc.note.text}>
                                                        <Icon name={alloc.note.icon} color={alloc.note.iconColor}/>
                                                    </TooltipV2>
                                                </>}
                                                {Accounting.balanceToString(wallet.category, alloc.quota, {precision: 0})}
                                            </Flex>}
                                        />
                                    )
                                }
                            </TreeNode>
                        )}
                    </TreeNode>;
                })}
            </Tree>

            {projectId !== undefined && <>
                <Flex mt={32} mb={10} alignItems={"center"} gap={"8px"}>
                    <h3 style={{margin: 0}}>Sub-projects</h3>
                    <Box flexGrow={1}/>
                    <Button height={35} onClick={onNewSubProject} disabled={projectRole == OldProjectRole.USER}>
                        <Icon name={"heroPlus"} mr={8}/>
                        New sub-project
                    </Button>

                    <Box width={"300px"}>
                        <Input
                            placeholder={"Search in your sub-projects"}
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
                    You do not have any sub-allocations at the moment.
                    {projectRole === OldProjectRole.USER ? null : <>
                        You can create a sub-project by clicking <a href="#" onClick={onNewSubProject}>here</a>.
                    </>}
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
                                            end: timestampUnixMs() + (1000 * 60 * 60 * 24 * 365),
                                            subAllocator: false,
                                        })}
                                    >
                                        <SmallIconButton icon={"heroBanknotes"} subIcon={"heroPlusCircle"}
                                                         subColor1={"primaryContrast"} subColor2={"primaryContrast"}/>
                                    </Link>
                                }

                                {recipient.usageAndQuota.map((uq, idx) => {
                                        if (idx > 2) return null;
                                        return <ProgressBar key={idx} uq={uq} type={uq.type}/>;
                                    }
                                )}
                            </div>}
                        >
                            {recipient.groups.map((g, gidx) => <React.Fragment key={gidx}>
                                <TreeNode
                                    left={<Flex gap={"4px"}>
                                        <Flex gap={"4px"} width={"200px"}>
                                            <ProviderLogo providerId={g.category.provider} size={20}/>
                                            <Icon name={Accounting.productTypeToIcon(g.category.productType)}
                                                  size={20}/>
                                            <code>{g.category.name}</code>
                                        </Flex>
                                    </Flex>}
                                    right={
                                        <ProgressBar uq={g.usageAndQuota} type={g.category.productType}/>
                                    }
                                >
                                    {g.allocations
                                        .map((alloc, idx) =>
                                            <TreeNode
                                                key={idx}
                                                className={alloc.note?.rowShouldBeGreyedOut ? "disabled-alloc" : undefined}
                                                data-ridx={recipientIdx} data-idx={idx} data-gidx={gidx}
                                                left={<Flex>
                                                    <Flex width={"200px"}>
                                                        <Icon name={"heroBanknotes"} ml="8px" mr={4}/>
                                                        <div>
                                                            <b>Allocation ID:</b>
                                                            {" "}
                                                            <code>{chunkedString(alloc.allocationId.toString().padStart(6, "0"), 3, false).join(" ")}</code>
                                                        </div>
                                                    </Flex>

                                                    <Flex width={"250px"}>
                                                        Period:
                                                        {" "}
                                                        {dateToStringNoTime(alloc.start)}
                                                        {" "}&mdash;{" "}
                                                        {dateToStringNoTime(alloc.end)}
                                                    </Flex>

                                                    {alloc.grantedIn && <>
                                                        <Link target={"_blank"}
                                                              to={AppRoutes.grants.editor(alloc.grantedIn)}>
                                                            View grant application{" "}
                                                            <Icon name={"heroArrowTopRightOnSquare"} mt={-6}/>
                                                        </Link>
                                                    </>}
                                                </Flex>}
                                                right={<Flex flexDirection={"row"} gap={"8px"}>
                                                    {alloc.note?.rowShouldBeGreyedOut !== true && !alloc.isEditing &&
                                                        <SmallIconButton
                                                            icon={"heroPencil"} onClick={onEdit}
                                                            disabled={state.editControlsDisabled}
                                                            data-ridx={recipientIdx} data-idx={idx} data-gidx={gidx}/>
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
                                                                defaultValue={Accounting.explainUnit(g.category).priceFactor * alloc.quota}
                                                                autoFocus
                                                                onKeyDown={onEditKey}
                                                                onBlur={onEditBlur}
                                                                data-ridx={recipientIdx} data-idx={idx}
                                                                data-gidx={gidx}
                                                            />
                                                            {Accounting.explainUnit(g.category).name}
                                                        </Flex>
                                                        : <>
                                                            {Accounting.balanceToString(g.category, alloc.quota)}
                                                        </>
                                                    }
                                                </Flex>}
                                            />
                                        )
                                    }
                                </TreeNode>
                            </React.Fragment>)}
                        </TreeNode>
                    )}
                </Tree>
            </>}
        </div>}
    />;
};

// Utility components
// =====================================================================================================================
// Various helper components used by the main user-interface.
function withinDelta(quota: number, maxUsable: number, usage: number): boolean {
    if ((maxUsable + usage) == quota) return true;
    const diff = quota - maxUsable - usage;
    return Math.abs(diff) <= 0.0001;
}
function ProgressBar({uq, type}: {
    uq: UsageAndQuota,
    type: ProductType,
}) {
    return <NewAndImprovedProgress
        limitPercentage={uq.quota === 0 ? 100 : ((uq.maxUsable + uq.usage) / uq.quota) * 100}
        label={progressText(type, uq)}
        percentage={uq.quota === 0 ? 0 : (uq.usage / uq.quota) * 100}
        withWarning={!withinDelta(uq.quota, uq.maxUsable, uq.usage)}
    />;
}

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

    if (uq.quota !== 0) {
        text += " (";
        text += Math.round((uq.usage / uq.quota) * 100);
        text += "%)";
    }
    return text;
}

function allocationIsActive(
    alloc: Accounting.Allocation,
    now: number,
): boolean {
    return periodsOverlap(allocationToPeriod(alloc), {start: now, end: now});
}

interface Period {
    start: number;
    end: number;
}

function allocationToPeriod(alloc: Accounting.Allocation): Period {
    return {start: alloc.startDate, end: alloc.endDate ?? NO_EXPIRATION_FALLBACK};
}

function normalizePeriodForComparison(period: Period): Period {
    return {start: Math.floor(period.start / 1000) * 1000, end: Math.floor(period.end / 1000) * 1000};
}

const SmallIconButtonStyle = injectStyle("small-icon-button", k => `
    ${k},
    ${k}:hover {
        color: var(--primaryContrast) !important;
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
        left: -15px;
        top: -10px;
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
        <Icon name={props.icon} hoverColor={"primaryContrast"}/>
        {props.subIcon &&
            <Relative>
                <div className={"sub"}>
                    <Icon name={props.subIcon} hoverColor={props.subColor1} color={props.subColor1}
                          color2={props.subColor2}/>
                </div>
            </Relative>
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
    remoteData: {wallets: [], managedProviders: [], managedProducts: {}, gifts: []},
    subAllocations: {searchQuery: "", searchInflight: 0, recipients: []},
    yourAllocations: {},
    editControlsDisabled: false,
};

const NO_EXPIRATION_FALLBACK = 4102444800353;

export default Allocations;
