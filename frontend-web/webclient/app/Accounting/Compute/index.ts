import {APICallParameters} from "Authentication/DataHook";
import {Dictionary, PaginationRequest} from "Types";
import {buildQueryString} from "Utilities/URIUtilities";
import {Client} from "Authentication/HttpClientInstance";

export type AccountType = "USER" | "PROJECT";

export interface ProductCategoryId {
    id: string;
    provider: string;
}

export enum ProductArea {
    COMPUTE = "COMPUTE",
    STORAGE = "STORAGE"
}

export function productAreaTitle(area: ProductArea): string {
    switch (area) {
        case ProductArea.COMPUTE:
            return "Compute";
        case ProductArea.STORAGE:
            return "Storage";
    }
}

export interface WalletBalance {
    wallet: Wallet;
    balance: number;
    area: ProductArea;
}

export interface Wallet {
    id: string;
    type: AccountType;
    paysFor: ProductCategoryId;
}

export function walletEquals(a: Wallet, b: Wallet): boolean {
    return a.id === b.id && a.type === b.type && productCategoryEquals(a.paysFor, b.paysFor);
}

export function productCategoryEquals(a: ProductCategoryId, b: ProductCategoryId) {
    return a.provider === b.provider && a.id === b.id;
}

export interface RetrieveBalanceRequest {
    id?: string;
    type?: AccountType;
    includeChildren?: boolean;
}

export interface RetrieveBalanceResponse {
    wallets: WalletBalance[];
}

export function retrieveBalance(request: RetrieveBalanceRequest): APICallParameters<RetrieveBalanceRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/wallets/balance", request),
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
    availability: { type: "available" | "unavailable"; reason?: string };
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

export interface TimeRangeQuery {
    bucketSize: number;
    periodStart: number;
    periodEnd: number;
}

export interface UsageRequest extends TimeRangeQuery {
}

export interface UsagePoint {
    timestamp: number;
    creditsUsed: number;
}

export interface UsageLine {
    area: ProductArea;
    category: string;
    projectPath?: string;
    projectId?: string;
    points: UsagePoint[];
}

export interface UsageChart {
    provider: string;
    lines: UsageLine[];
}

export interface UsageResponse {
    charts: UsageChart[];
}

export type CumulativeUsageRequest = UsageRequest;
export type CumulativeUsageResponse = UsageResponse;

export function usage(request: UsageRequest): APICallParameters<UsageRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/visualization/usage", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export function cumulativeUsage(request: CumulativeUsageRequest): APICallParameters<CumulativeUsageRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/visualization/cumulative-usage", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface NativeChartPoint extends Dictionary<number> {
    time: number;
}

export interface NativeChart {
    provider: string;
    lineNames: string[];
    points: NativeChartPoint[];
    lineNameToWallet: Dictionary<Wallet>;
}

export function transformUsageChartForCharting(
    chart: UsageChart,
    type: ProductArea
): NativeChart {
    const builder: Dictionary<NativeChartPoint> = {};
    const lineNames: string[] = [];
    const lineNameToWallet: Dictionary<Wallet> = {};

    for (const line of chart.lines) {
        if (type !== line.area) continue;

        const lineId = line.projectPath ? `${line.projectPath} (${line.category})` : line.category;
        lineNames.push(lineId);
        lineNameToWallet[lineId] = {
            id: line.projectId ?? Client.username ?? "",
            type: line.projectId ? "PROJECT" : "USER",
            paysFor: {
                id: line.category,
                provider: chart.provider
            }
        };

        for (const point of line.points) {
            const dataPoint = builder[`${point.timestamp}`] ?? {time: point.timestamp};
            dataPoint[lineId] = point.creditsUsed;
            builder[`${point.timestamp}`] = dataPoint;
        }
    }

    return {provider: chart.provider, lineNames, points: Object.values(builder), lineNameToWallet};
}
