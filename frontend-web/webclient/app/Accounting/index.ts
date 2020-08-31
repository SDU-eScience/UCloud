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

export function productCategoryEquals(a: ProductCategoryId, b: ProductCategoryId): boolean {
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

export interface Product {
    id: string;
    pricePerUnit: number;
    category: ProductCategoryId;
    description: string;
    availability: { type: "available" | "unavailable"; reason?: string };
    priority: number;
    cpu?: number;
    memoryInGigs?: number;
    gpu?: number;
    type: "compute" | "storage";
}

export interface ListProductsRequest extends PaginationRequest {
    provider: string;
}

export interface ListProductsByAreaRequest extends PaginationRequest{
    provider: string;
    area: string
}

export type ListProductsResponse = Product[];

export function listProducts(request: ListProductsRequest): APICallParameters<ListProductsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/list", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export function listByProductArea(request: ListProductsByAreaRequest): APICallParameters<ListProductsByAreaRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/listByArea", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface RetrieveFromProviderRequest { provider: string; }
export type RetrieveFromProviderResponse = Product[];
export function retrieveFromProvider(
    request: RetrieveFromProviderRequest
): APICallParameters<RetrieveFromProviderRequest> {
    return {
        method: "GET",
        path: buildQueryString("/products/retrieve", request),
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

export function usage(request: UsageRequest): APICallParameters<UsageRequest> {
    return {
        method: "GET",
        path: buildQueryString("/accounting/visualization/usage", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface NativeChartPoint extends Record<string, number> {
    time: number;
}

export interface NativeChart {
    provider: string;
    lineNames: string[];
    points: NativeChartPoint[];
    lineNameToWallet: Record<string, Wallet>;
}

export function transformUsageChartForCharting(
    chart: UsageChart,
    type: ProductArea
): NativeChart {
    const builder: Record<string, NativeChartPoint> = {};
    const lineNames: string[] = [];
    const lineNameToWallet: Record<string, Wallet> = {};

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


export interface RetrieveQuotaRequest {
    path: string;
    includeUsage?: boolean;
}

export interface RetrieveQuotaResponse {
    quotaInBytes: number;
    quotaUsed?: number;
}

export function retrieveQuota(request: RetrieveQuotaRequest): APICallParameters<RetrieveQuotaRequest> {
    return {
        method: "GET",
        path: buildQueryString("/files/quota", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface UpdateQuotaRequest {
    path: string;
    quotaInBytes: number;
}

export type UpdateQuotaResponse = {};

export function updateQuota(request: UpdateQuotaRequest): APICallParameters<UpdateQuotaRequest> {
    return {
        method: "POST",
        path: "/files/quota",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export const UCLOUD_PROVIDER = "ucloud";

export function isQuotaSupported(category: ProductCategoryId): boolean {
    return category.provider === UCLOUD_PROVIDER && category.id === "u1-cephfs";
}
