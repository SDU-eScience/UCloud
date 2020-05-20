import {APICallParameters} from "Authentication/DataHook";
import {Dictionary} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";

export enum MachineType { STANDARD = "STANDARD", HIGH_MEMORY = "HIGH_MEMORY", GPU = "GPU" }

export type AccountType = "USER" | "PROJECT";

export function humanReadableMachineType(type: MachineType): string {
    switch (type) {
        case "HIGH_MEMORY":
            return "High memory";
        case "STANDARD":
            return "Standard";
        case "GPU":
            return "GPU";
        default:
            return "";
    }
}

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

// Machines

export interface MachineReservation {
    name: string;
    cpu?: number;
    memoryInGigs?: number;
    gpu?: number;
    pricePerHour: number;
    type: MachineType;
}

export type CreateMachineRequest = MachineReservation;
export type CreateMachineResponse = {};

export function createMachine(request: CreateMachineRequest): APICallParameters<CreateMachineRequest> {
    return {
        method: "PUT",
        path: "/accounting/compute/machines",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export type DefaultMachineRequest = {};
export type DefaultMachineResponse = MachineReservation;

export function defaultMachine(request: DefaultMachineRequest): APICallParameters<DefaultMachineRequest> {
    return {
        method: "GET",
        path: "/accounting/compute/machines/default-machine",
        parameters: request,
        reloadId: Math.random()
    };
}

export type ListMachinesRequest = {};
export type ListMachinesResponse = MachineReservation[];

export function listMachines(request: ListMachinesRequest): APICallParameters<ListMachinesRequest> {
    return {
        method: "GET",
        path: "/accounting/compute/machines",
        parameters: request,
        reloadId: Math.random()
    };
}

export interface FindMachineRequest {
    name: string;
}

export type FindMachineResponse = MachineReservation;

export function findMachine(request: FindMachineRequest): APICallParameters<FindMachineRequest> {
    return {
        method: "GET",
        path: `/accounting/compute/machines/${encodeURIComponent(request.name)}`,
        parameters: request,
        reloadId: Math.random()
    };
}

export interface MarkMachineAsInactiveRequest {
    name: string;
}

export type MarkMachineAsInactiveResponse = {};

export function markMachineAsInactive(request: MarkMachineAsInactiveRequest): APICallParameters<MarkMachineAsInactiveRequest> {
    return {
        method: "POST",
        path: "/accounting/compute/machines/mark-as-inactive",
        parameters: request,
        reloadId: Math.random()
    };
}

export interface SetMachineAsDefaultRequest {
    name: string;
}

export type SetMachineAsDefaultResponse = {};

export function setMachineAsDefault(request: SetMachineAsDefaultRequest): APICallParameters<SetMachineAsDefaultRequest> {
    return {
        method: "POST",
        path: "/accounting/compute/machines/set-as-default",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}
