import {APICallParameters} from "Authentication/DataHook";
import {PaginationRequest} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export type AccountType = "USER" | "PROJECT";

export interface ProductCategoryId {
    id: string;
    provider: string
}

export interface WalletBalance {
    category: ProductCategoryId;
    balance: number;
}

export interface Wallet {
    id: string;
    type: AccountType;
    paysFor: ProductCategoryId;
}

export interface RetrieveBalanceRequest {
    id: string;
    type: AccountType;
}

export interface RetrieveBalanceResponse {
    wallets: WalletBalance[];
}

export function retrieveBalance(request: RetrieveBalanceRequest): APICallParameters<RetrieveBalanceRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface GrantCreditsRequest {
    wallet: Wallet;
    credits: number;
}

export type GrantCreditsResponse = {};

export function grantCredits(request: GrantCreditsRequest): APICallParameters<GrantCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/wallets/add-credits",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface SetCreditsRequest {
    wallet: Wallet;
    lastKnownBalance: number;
    newBalance: number;
}

export type SetCreditsResponse = {};

export function setCredits(request: SetCreditsRequest): APICallParameters<SetCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/wallets/set-balance",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface RetrieveCreditsRequest {
    id: string;
    type: AccountType;
}

export interface RetrieveCreditsResponse {
    wallets: WalletBalance[];
}

export function retrieveCredits(request: RetrieveCreditsRequest): APICallParameters<RetrieveCreditsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/wallets/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}

// Machines

export interface MachineReservation {
    id: string;
    pricePerUnit: number;
    category: ProductCategoryId;
    description: string;
    availability: { type: "available" | "unavailable", reason?: string };
    priority: number;
    cpu?: number;
    memoryInGigs?: number;
    gpu?: number;
}

export interface ListMachinesRequest extends PaginationRequest {
    provider: string;
    productCategory: string;
}
export type ListMachinesResponse = MachineReservation[];

export function listMachines(request: ListMachinesRequest): APICallParameters<ListMachinesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/list", request),
        parameters: request,
        reloadId: Math.random()
    };
}
