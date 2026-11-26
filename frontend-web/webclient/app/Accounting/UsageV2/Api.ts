import {
    AccountingFrequency,
    AccountingModel,
    AccountingUnit,
    explainUnitEx,
    FrontendAccountingUnit,
    ProductCategoryId,
    ProductCategoryV2,
    ProductType,
} from "@/Accounting/index";
import {formatAmount} from "@/Accounting/Allocations/Model";
import {apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {formatNumber} from "@/Utilities/NumberFormatting";

export interface UsageOverviewRequest {
    start: number;
    end: number;
    initialUnitGroupKey?: string;
}

export interface UsageSparklinePoint {
    timestamp: number;
    value: number;
    quota: number;
}

export interface UsageQuotaDrop {
    timestamp: number;
    quotaBefore: number;
    quotaAfter: number;
    wouldExceed: boolean;
}

export interface UsageProductSummary {
    category: ProductCategoryV2;
    periodUsage: number;
    activeUsage: number;
    activeQuota: number;
    retiredUsage: number;
    quota: number;
    totalUsage: number;
    nextMeaningfulExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
    sparkline: UsageSparklinePoint[];
}

export interface UsageUnitGroup {
    key: string;
    title: string;
    accountingModel: AccountingModel;
    productType: ProductType;
    unitAndFrequency: {
        unit: AccountingUnit;
        frequency: AccountingFrequency;
    };
    periodUsage: number;
    activeUsage: number;
    activeQuota: number;
    retiredUsage: number;
    quota: number;
    totalUsage: number;
    nextMeaningfulExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
    sparkline: UsageSparklinePoint[];
    products: UsageProductSummary[];
}

export interface UsageOverviewResponse {
    hasData: boolean;
    unitGroups: UsageUnitGroup[];
    initialDetail?: UsageDetailResponse | null;
}

export interface UsageDetailRequest {
    start: number;
    end: number;
    unitGroupKey: string;
    products: ProductCategoryId[];
    pinnedChildren: string[];
}

export interface UsageDetailKpis {
    periodUsage: number;
    periodLocalUsage: number;
    periodChildrenUsage: number;
    activeUsageAtEnd: number;
    activeQuotaAtEnd: number;
    retiredUsageAtEnd: number;
    totalUsageAtEnd: number;
    quotaAtEnd: number;
    localUsageAtEnd: number;
    maxUsableAtEnd: number;
    activeAllocatedAtEnd: number;
    totalAllocatedAtEnd: number;
    avgUsage: number;
    upstreamCapped: boolean;
    nextMeaningfulExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
}

export interface UsageSeriesValue {
    key: string;
    change: number;
    usage: number;
}

export interface UsageTimelineBucket {
    timestamp: number;
    endTimestamp: number;
    usageChange: number;
    flowShift: number;
    activeUsage: number;
    retiredUsage: number;
    activeQuota: number;
    quota: number;
    maxUsable: number;
    peakUsage: number;
    byProduct: UsageSeriesValue[];
    byChild: UsageSeriesValue[];
}

export interface UsageDailyActivityPoint {
    timestamp: number;
    usageChange: number;
}

export interface UsageLifecycleEvent {
    timestamp: number;
    type: "start" | "expire" | "retire";
    quotaDelta: number;
}

export interface UsageHistogramBucket {
    key: string;
    label: string;
    count: number;
}

export interface UsageSubAllocatorAggregate {
    downstreamActiveAllocated: number;
    directUsage: number;
    downstreamUsage: number;
}

export interface UsageQuotaExpiration {
    timestamp: number;
    quota: number;
}

export interface UsageChildRow {
    child: string;
    isLocal: boolean;
    periodUsage: number;
    activeUsage: number;
    activeQuota: number;
    activeQuotaKnown?: boolean;
    quota?: number;
    quotaKnown?: boolean;
    retiredUsage: number;
    totalUsage: number;
    nextExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
    upcomingExpirations?: UsageQuotaExpiration[];
    subAllocator?: UsageSubAllocatorAggregate | null;
}

export interface UsageInferenceCostBreakdown {
    cachedInput: number;
    input: number;
    output: number;
    image: number;
    audio: number;
    video: number;
}

export interface UsageInferenceTokenBreakdown {
    cachedInput: number;
    input: number;
    output: number;
}

export interface UsageInferenceModelBucketValue {
    model: string;
    estimatedCost: UsageInferenceCostBreakdown;
    tokens: UsageInferenceTokenBreakdown;
}

export interface UsageInferenceTimelineBucket {
    timestamp: number;
    endTimestamp: number;
    byModel: UsageInferenceModelBucketValue[];
}

export interface UsageInferenceModelSummary {
    model: string;
    title: string;
    priced: boolean;
    estimatedCost: number;
    tokens: UsageInferenceTokenBreakdown;
    imageCount: number;
    imagePixels: number;
    audioMilliseconds: number;
    videoSeconds: number;
}

export interface UsageInferenceBreakdown {
    available: boolean;
    bucketDurationMs?: number;
    coFundedWorkspaceCount: number;
    hasUnpricedModels: boolean;
    hasVideoEstimate: boolean;
    models: UsageInferenceModelSummary[];
    timeline: UsageInferenceTimelineBucket[];
}

export interface UsageJobOutcomeCounts {
    completed: number;
    failed: number;
    expired: number;
    active: number;
}

export interface UsageJobSliceBucketValue {
    application: string;
    productKey: string;
    estimatedHours: number;
    requestedHours: number;
    outcomes: UsageJobOutcomeCounts;
}

export interface UsageJobTimelineBucket {
    timestamp: number;
    endTimestamp: number;
    queueSamples: number;
    avgQueueSeconds: number;
    p50QueueSeconds: number;
    p90QueueSeconds: number;
    bySlice: UsageJobSliceBucketValue[];
}

export interface UsageJobApplicationSummary {
    application: string;
    estimatedHours: number;
    outcomes: UsageJobOutcomeCounts;
    topProductKeys: string[];
}

export interface UsageJobProductSummary {
    product: string;
    provider: string;
    cpu: number;
    gpu: number;
    memoryInGigs: number;
    jobs: number;
    estimatedHours: number;
    requestedHours: number;
    queueSamples: number;
    avgQueueSeconds: number;
    p50QueueSeconds: number;
    p90QueueSeconds: number;
    maxQueueSeconds: number;
}

export interface UsageJobBreakdown {
    available: boolean;
    bucketDurationMs?: number;
    coFundedWorkspaceCount: number;
    applications: UsageJobApplicationSummary[];
    products: UsageJobProductSummary[];
    timeline: UsageJobTimelineBucket[];
}

export interface UsageDetailResponse {
    hasData: boolean;
    unitGroupKey: string;
    title: string;
    accountingModel: AccountingModel;
    productType: ProductType;
    unitAndFrequency: {
        unit: AccountingUnit;
        frequency: AccountingFrequency;
    };
    productsCovered: ProductCategoryV2[];
    bucketDurationMs: number;
    validFrom: number;
    validUntil: number;
    kpis: UsageDetailKpis;
    timeline: UsageTimelineBucket[];
    dailyActivity?: UsageDailyActivityPoint[];
    events: UsageLifecycleEvent[];
    children: UsageChildRow[];
    inferenceBreakdown?: UsageInferenceBreakdown | null;
    jobBreakdown?: UsageJobBreakdown | null;
}

export function usageOverviewRetrieve(
    request: UsageOverviewRequest,
): APICallParameters<UsageOverviewRequest, UsageOverviewResponse> {
    return apiRetrieve(request, "/api/accounting/v2/usage", "overview");
}

export function usageDetailRetrieve(
    request: UsageDetailRequest,
): APICallParameters<UsageDetailRequest, UsageDetailResponse> {
    return apiUpdate(request, "/api/accounting/v2/usage", "detail");
}

export function usageProductKey(category: ProductCategoryId): string {
    return `${category.provider}/${category.name}`;
}

export function usageExplainUnit(
    unitAndFrequency: {unit: AccountingUnit; frequency: AccountingFrequency},
    productType: ProductType,
): FrontendAccountingUnit {
    return explainUnitEx(unitAndFrequency.unit, unitAndFrequency.frequency, productType);
}

export function usageFormatNumber(
    unit: FrontendAccountingUnit,
    rawValue: number,
): string {
    return formatAmount(rawValue * unit.balanceFactor);
}

export function usageFormatValue(
    unit: FrontendAccountingUnit,
    rawValue: number,
): string {
    return `${usageFormatNumber(unit, rawValue)} ${unit.name}`;
}

export function usageFormatPercent(value: number, precision = 1): string {
    return `${formatNumber(value, {precision})}%`;
}

export function usageFormatDays(days: number): string {
    return `${formatNumber(days)} ${days === 1 ? "day" : "days"}`;
}

export function usageFormatShortDate(timestamp: number, timeZone?: string): string {
    return new Date(timestamp).toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
        timeZone,
    });
}

export function usageDaysUntil(targetTimestamp: number | null | undefined, fromTimestamp: number): number | null {
    if (targetTimestamp == null) return null;
    return Math.max(0, Math.floor((targetTimestamp - fromTimestamp) / (1000 * 60 * 60 * 24)));
}

export const USAGE_DROP_WARNING_DAYS = 90;
export const USAGE_DROP_CRITICAL_DAYS = 30;

export function usageDropRiskDays(
    drop: UsageQuotaDrop | null | undefined,
    accountingModel: AccountingModel,
    periodEnd: number,
): number | null {
    if (!drop) return null;
    const hasConsequence = accountingModel === "CAPACITY"
        ? drop.wouldExceed
        : (drop.wouldExceed || (drop.quotaBefore > 0 && drop.quotaAfter === 0));
    if (!hasConsequence) return null;
    const days = usageDaysUntil(drop.timestamp, periodEnd);
    if (days === null || days > USAGE_DROP_WARNING_DAYS) return null;
    return days;
}

export interface UsageChildScopedMetrics {
    usage: number;
    quota: number;
    quotaKnown: boolean;
    utilizationPercent: number;
    utilizationBucket: string;
}

export function usageChildScopeMetrics(
    row: UsageChildRow,
    accountingModel: AccountingModel,
    includeRetired: boolean,
): UsageChildScopedMetrics {
    const useLifetime = accountingModel !== "CAPACITY" && includeRetired;
    const usage = useLifetime ? row.totalUsage : row.activeUsage;
    const quotaKnown = useLifetime ? row.quotaKnown !== false && row.quota != null : row.activeQuotaKnown !== false;
    if (!quotaKnown) {
        return {
            usage,
            quota: 0,
            quotaKnown: false,
            utilizationPercent: 0,
            utilizationBucket: "unknown",
        };
    }

    const quota = useLifetime ? Math.max(row.quota ?? 0, row.activeQuota) : row.activeQuota;
    if (quota <= 0) {
        return {
            usage,
            quota,
            quotaKnown: true,
            utilizationPercent: usage > 0 ? 100 : 0,
            utilizationBucket: usage > 0 ? "over" : "idle",
        };
    }

    const pct = (usage / quota) * 100;
    let bucket = "near_full";
    if (pct >= 100) {
        bucket = "over";
    } else if (usage === 0 && (useLifetime || accountingModel === "CAPACITY" || row.periodUsage === 0)) {
        bucket = "idle";
    } else if (pct < 25) {
        bucket = "low";
    } else if (pct < 75) {
        bucket = "medium";
    } else if (pct < 90) {
        bucket = "high";
    }

    return {
        usage,
        quota,
        quotaKnown: true,
        utilizationPercent: pct,
        utilizationBucket: bucket,
    };
}

export function usageBuildChildHistogramBuckets(
    rows: UsageChildRow[],
    accountingModel: AccountingModel,
    includeRetired: boolean,
): UsageHistogramBucket[] {
    const defs: {key: string; label: string}[] = [
        {key: "idle", label: "0% (Idle)"},
        {key: "low", label: "1–25%"},
        {key: "medium", label: "25–75%"},
        {key: "high", label: "75–90%"},
        {key: "near_full", label: "90–100%"},
        {key: "over", label: "Exhausted / Over"},
    ];
    const buckets: UsageHistogramBucket[] = defs.map(d => ({
        key: d.key,
        label: d.label,
        count: 0,
    }));
    const byKey = new Map(buckets.map(b => [b.key, b]));

    for (const row of rows) {
        if (row.isLocal) continue;
        const metrics = usageChildScopeMetrics(row, accountingModel, includeRetired);
        if (!metrics.quotaKnown) continue;
        const bucket = byKey.get(metrics.utilizationBucket);
        if (!bucket) continue;
        bucket.count++;
    }

    return buckets;
}
