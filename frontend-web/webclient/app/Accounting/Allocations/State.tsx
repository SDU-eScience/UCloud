import * as Accounting from "@/Accounting";
import * as Gifts from "@/Accounting/Gifts";
import {deepCopy, newFuzzyMatchFuse} from "@/Utilities/CollectionUtilities";
import * as React from "react";
import {useCallback} from "react";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import ProvidersApi from "@/UCloud/ProvidersApi";
import {AllocationDisplayTreeRecipient, ProductType} from "@/Accounting";
import {ProjectInfo, projectInfoPi, projectInfoTitle} from "@/Project/InfoCache";
import {Client} from "@/Authentication/HttpClientInstance";

const fuzzyMatcher = newFuzzyMatchFuse<{title: string}, "title">(["title"]);

// State
// =====================================================================================================================
export interface State extends Accounting.AllocationDisplayTree {
    remoteData: {
        wallets?: Accounting.WalletV2[];
        managedProviders?: string[];
        managedProducts?: Record<string, Accounting.ProductCategoryV2[]>;
        gifts?: Gifts.GiftWithCriteria[];
    };

    searchQuery: string;

    subprojectSortBy?: string;
    subprojectSortByAscending: boolean;
    subprojectInfo: Record<string, ProjectInfo | null>;

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
    viewOnlyProjects: boolean;

    filteredSubProjectIndices: number[]; // Indices into the subAllocations.recipients array
}

// State reducer
// =====================================================================================================================
export type UIAction =
    | { type: "Reset" }
    | { type: "WalletsLoaded", wallets: Accounting.WalletV2[]; }
    | { type: "ManagedProvidersLoaded", providerIds: string[] }
    | { type: "ManagedProductsLoaded", products: Record<string, Accounting.ProductCategoryV2[]> }
    | { type: "GiftsLoaded", gifts: Gifts.GiftWithCriteria[] }
    | { type: "UpdateSearchQuery", newQuery: string }
    | { type: "SetEditing", recipientIdx: number, groupIdx: number, allocationIdx: number, isEditing: boolean }
    | { type: "UpdateAllocation", allocationIdx: number, groupIdx: number, recipientIdx: number, newQuota: number }
    | { type: "UpdateGift", data: Partial<State["gifts"]> }
    | { type: "GiftCreated", gift: Gifts.GiftWithCriteria }
    | { type: "GiftDeleted", id: number }
    | { type: "UpdateRootAllocations", data: Partial<State["rootAllocations"]> }
    | { type: "ResetRootAllocation" }
    | { type: "ToggleViewOnlyProjects" }
    | { type: "SortSubprojects", sortBy?: string, ascending: boolean }
    | { type: "SubProjectData", projects: Record<string, ProjectInfo | null> }
    ;

function recipientTitle(recipient: AllocationDisplayTreeRecipient, state: State): string {
    return recipient.owner.reference.type === "user" ?
        recipient.owner.primaryUsername :
        projectInfoTitle(state.subprojectInfo[recipient.owner.reference.projectId], recipient.owner.title) ?? "-";
}

function recipientPrimaryUsername(recipient: AllocationDisplayTreeRecipient, state: State): string {
    return recipient.owner.reference.type === "user" ?
        recipient.owner.primaryUsername :
        projectInfoPi(state.subprojectInfo[recipient.owner.reference.projectId], recipient.owner.primaryUsername) ?? "-";
}

function searchQueryMatches(recipient: AllocationDisplayTreeRecipient, state: State, query: string) {
    if (recipient.owner.reference.type === "user" && state.viewOnlyProjects) return false;
    if (recipient.owner.reference.type === "project" && !state.viewOnlyProjects) return false;
    if (query === "") return true;
    const title = recipientTitle(recipient, state);

    fuzzyMatcher.setCollection([{title}]);
    return fuzzyMatcher.search(query).length > 0;
}

export function stateReducer(state: State, action: UIAction): State {
    switch (action.type) {
        case "SubProjectData": {
            const newState = {
                ...state,
                subprojectInfo: action.projects,
            };
            return rebuildTree(newState);
        }

        case "SortSubprojects": {
            const newState = {
                ...state,
                subprojectSortBy: action.sortBy,
                subprojectSortByAscending: action.ascending,
            };

            return rebuildTree(newState);
        }

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
                    gifts: [...(state.remoteData.gifts ?? []), action.gift]
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
                    gifts: (state.remoteData.gifts ?? []).filter(it => it.id !== action.id)
                }
            };
        }

        case "UpdateSearchQuery": {
            const newState: State = {
                ...state,
                searchQuery: action.newQuery,
                subAllocations: {
                    ...state.subAllocations,
                },
            };

            return rebuildTree(newState);
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

        case "ToggleViewOnlyProjects": {
            return rebuildTree({...state, viewOnlyProjects: !state.viewOnlyProjects});
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

            outer: for (const wallet of (newState.remoteData.wallets ?? [])) {
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

        case "Reset": {
            return initialState();
        }
    }

    // Utility functions for mutating state
    // -----------------------------------------------------------------------------------------------------------------
    function getOrNull<T>(arr: T[], idx: number): T | null {
        if (idx >= 0 && idx < arr.length) return arr[idx];
        return null;
    }

    function rebuildTree(state: State): State {
        const newTree = Accounting.buildAllocationDisplayTree((state.remoteData.wallets ?? []));

        newTree.subAllocations.recipients.sort((a, b) => {
            let naturalOrderResult = (() => {
                switch (state.subprojectSortBy) {
                    case "usagePercentageCompute":
                    case "usagePercentageStorage":
                    case "usagePercentagePublicIP":
                    case "usagePercentageLicence": {
                        let productType: ProductType = "COMPUTE";
                        if (state.subprojectSortBy === "usagePercentageCompute") productType = "COMPUTE";
                        if (state.subprojectSortBy === "usagePercentageStorage") productType = "STORAGE";
                        if (state.subprojectSortBy === "usagePercentagePublicIP") productType = "NETWORK_IP";
                        if (state.subprojectSortBy === "usagePercentageLicence") productType = "LICENSE";

                        let sumOfPercentagesA = 0;
                        for (const uq of a.usageAndQuota) {
                            if (uq.type !== productType || uq.raw.quota === 0) continue;
                            let usage = uq.raw.usage;
                            if (!uq.raw.retiredAmountStillCounts) {
                                usage -= uq.raw.retiredAmount;
                            }
                            sumOfPercentagesA += usage / uq.raw.quota;
                        }

                        let sumOfPercentagesB = 0;
                        for (const uq of b.usageAndQuota) {
                            if (uq.type !== productType || uq.raw.quota === 0) continue;
                            let usage = uq.raw.usage;
                            if (!uq.raw.retiredAmountStillCounts) {
                                usage -= uq.raw.retiredAmount;
                            }
                            sumOfPercentagesB += usage / uq.raw.quota;
                        }

                        let averageUtilizationA = sumOfPercentagesA / a.usageAndQuota.length;
                        let averageUtilizationB = sumOfPercentagesB / b.usageAndQuota.length;

                        if (averageUtilizationA < averageUtilizationB) {
                            return -1;
                        } else if (averageUtilizationA > averageUtilizationB) {
                            return 1;
                        }
                        return 0;
                    }

                    case "age": {
                        let earliestStartA = Number.MAX_SAFE_INTEGER;

                        for (const group of a.groups) {
                            for (const allocation of group.allocations) {
                                if (earliestStartA > allocation.start) {
                                    earliestStartA = allocation.start;
                                }
                            }
                        }

                        let earliestStartB = Number.MAX_SAFE_INTEGER;

                        for (const group of b.groups) {
                            for (const allocation of group.allocations) {
                                if (earliestStartB > allocation.start) {
                                    earliestStartB = allocation.start
                                }
                            }
                        }

                        if (earliestStartA > earliestStartB) {
                            return -1;
                        } else if (earliestStartA < earliestStartB) {
                            return 1;
                        }
                        return 0;
                    }

                    case "PI": {
                        return recipientPrimaryUsername(a, state).localeCompare(recipientPrimaryUsername(b, state))
                    }

                    case "title":
                    default: {
                        return recipientTitle(a, state).localeCompare(recipientTitle(b, state))
                    }
                }
            })();

            if (!state.subprojectSortByAscending) {
                return naturalOrderResult * -1;
            }  else {
                return naturalOrderResult;
            }
        });

        const query = state.searchQuery;

        const filteredSubProjectIndices: number[] = [];
        for (let i = 0; i < newTree.subAllocations.recipients.length; i++) {
            const recipient = newTree.subAllocations.recipients[i];
            if (searchQueryMatches(recipient, state, query)) {
                filteredSubProjectIndices.push(i);
            }
        }

        return {
            ...state,
            yourAllocations: newTree.yourAllocations,
            subAllocations: newTree.subAllocations,
            filteredSubProjectIndices,
        };
    }
}

// State reducer middleware
// =====================================================================================================================
export type UIEvent =
    UIAction
    | { type: "Init" }
    ;

export function useEventReducer(didCancel: React.RefObject<boolean>, doDispatch: (action: UIAction) => void): (event: UIEvent) => unknown {
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

async function fetchManagedProviders(): Promise<string[]> {
    let list: string[] = [];

    try {
        const items = await fetchAll(next => callAPI(ProvidersApi.browse({itemsPerPage: 250, next})));
        list = items.map(it => it.specification.id)
    } catch (e) {}

    const providerProjects = Client.userInfo?.providerProjects ?? {};
    for (const [provider, project] of Object.entries(providerProjects)) {
        if (project === Client.projectId) {
            list.push(provider);
        }
    }
    return list;
}

// Initial state
// =====================================================================================================================
export function initialState(): State {
    return {
        remoteData: {},
        subAllocations: {recipients: []},
        searchQuery: "",
        yourAllocations: {},
        editControlsDisabled: false,
        viewOnlyProjects: true,
        filteredSubProjectIndices: [],
        subprojectSortByAscending: true,
        subprojectInfo: {},
    };
}
