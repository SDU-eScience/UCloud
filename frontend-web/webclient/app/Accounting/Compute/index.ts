import {APICallParameters} from "Authentication/DataHook";
import {Dictionary} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export type MachineType = "STANDARD" | "HIGH_MEMORY" | "GPU";
export type AccountType = "USER" | "PROJECT";

export interface CreditsAccount {
    id: string;
    type: AccountType;
    machineType: MachineType;
}

export interface ComputeBalance {
    type: MachineType;
    creditsRemaining: number;
}

export interface ComputeChartPoint {
    timestamp: number;
    creditsUsed: number;
}

export interface DailyUsageRequest {
    project?: string;
    group?: string;
    pStart?: number;
    pEnd?: number;
}


export interface DailyUsageResponse {
    chart: Dictionary<ComputeChartPoint>; // Keys are MachineType
}

export function dailyUsage(request: DailyUsageRequest): APICallParameters<DailyUsageRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/daily", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export type CumulativeUsageRequest = DailyUsageRequest;
export type CumulativeUsageResponse = DailyUsageResponse;

export function cumulativeUsage(request: CumulativeUsageRequest): APICallParameters<CumulativeUsageRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/cumulative", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface RetrieveBalanceRequest {
    id: string;
    type: AccountType;
}

export interface RetrieveBalanceResponse {
    balance: ComputeBalance[];
}

export function retrieveBalance(request: RetrieveBalanceRequest): APICallParameters<RetrieveBalanceRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface BreakdownPoint {
    username: string;
    creditsUsed: number;
}

export interface UsageBreakdownRequest {
    project: string;
}

export interface UsageBreakdownResponse {
    chart: Dictionary<BreakdownPoint>; // Keys are MachineType
}

export function usageBreakdown(request: UsageBreakdownRequest): APICallParameters<UsageBreakdownRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/breakdown", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface GrantCreditsRequest {
    account: CreditsAccount;
    credits: number;
}

export type GrantCreditsResponse = {};

export function grantCredits(request: GrantCreditsRequest): APICallParameters<GrantCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/compute/add-credits",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface SetCreditsRequest {
    account: CreditsAccount;
    lastKnownBalance: number;
    newBalance: number;
}

export type SetCreditsResponse = {};

export function setCredits(request: SetCreditsRequest): APICallParameters<SetCreditsRequest> {
    return {
        method: "POST",
        path: "/accounting/compute/set-balance",
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
    balance: ComputeBalance[];
}

export function retrieveCredits(request: RetrieveCreditsRequest): APICallParameters<RetrieveCreditsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/compute/balance", request),
        parameters: request,
        reloadId: Math.random()
    };
}
