import WAYF from "@/Grants/wayf-idps.json";
import * as Accounting from "@/Accounting";
import * as Gifts from "@/Accounting/Gifts";
import {deepCopy, newFuzzyMatchFuse} from "@/Utilities/CollectionUtilities";
import * as React from "react";
import {useCallback} from "react";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import ProvidersApi from "@/UCloud/ProvidersApi";
import {AllocationDisplayTree, AllocationDisplayTreeRecipient} from "@/Accounting";

const fuzzyMatcher = newFuzzyMatchFuse<Accounting.AllocationDisplayTreeRecipientOwner, "title" | "primaryUsername">(["title", "primaryUsername"]);

// State
// =====================================================================================================================
export interface State extends Accounting.AllocationDisplayTree {
    remoteData: {
        wallets: Accounting.WalletV2[];
        managedProviders: string[];
        managedProducts: Record<string, Accounting.ProductCategoryV2[]>;
        gifts: Gifts.GiftWithCriteria[];
    };

    searchQuery: string;

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
    | { type: "ToggleViewOnlyProjects", viewOnlyProjects: boolean }
    ;

function searchQueryMatches(recipient: AllocationDisplayTreeRecipient, state: State, query: string) {
    if (recipient.owner.reference.type === "user" && state.viewOnlyProjects) return false;
    if (query === "") return true;
    fuzzyMatcher.setCollection([recipient.owner]);
    return fuzzyMatcher.search(query).length > 0;
}

export function stateReducer(state: State, action: UIAction): State {
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
            return rebuildTree({...state, viewOnlyProjects: action.viewOnlyProjects});
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
        const newTree = Accounting.buildAllocationDisplayTree(state.remoteData.wallets);
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
    const items = await fetchAll(next => callAPI(ProvidersApi.browse({itemsPerPage: 250, next})));
    return items.map(it => it.specification.id);
}

// Initial state
// =====================================================================================================================
export function initialState(): State {
    return {
        remoteData: {wallets: [], managedProviders: [], managedProducts: {}, gifts: []},
        subAllocations: {recipients: []},
        searchQuery: "",
        yourAllocations: {},
        editControlsDisabled: false,
        viewOnlyProjects: true,
        filteredSubProjectIndices: [],
    };
}
