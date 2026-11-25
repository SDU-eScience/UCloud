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
    peakUsage: number;
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
    peakUsage: number;
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
    activeUsageAtStart: number;
    activeUsageAtEnd: number;
    activeQuotaAtStart: number;
    activeQuotaAtEnd: number;
    retiredUsageAtStart: number;
    retiredUsageAtEnd: number;
    totalUsageAtStart: number;
    totalUsageAtEnd: number;
    quotaAtStart: number;
    quotaAtEnd: number;
    localUsageAtStart: number;
    localUsageAtEnd: number;
    maxUsableAtEnd: number;
    activeAllocatedAtEnd: number;
    totalAllocatedAtEnd: number;
    peakUsage: number;
    avgUsage: number;
    upstreamCapped: boolean;
    nextMeaningfulExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
    downstreamActiveAllocated: number;
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
    localUsageChange: number;
    flowShift: number;
    activeUsage: number;
    retiredUsage: number;
    totalUsage: number;
    localUsage: number;
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
    type: "start" | "expire" | "retire" | "upcoming_expire";
    quotaDelta: number;
    count: number;
    productKey: string;
}

export interface UsageHistogramBucket {
    key: string;
    label: string;
    count: number;
    totalPeriodUsage: number;
    totalActiveQuota: number;
}

export interface UsageSubProjectSummary {
    totalCount: number;
    subAllocatorCount: number;
    utilizationBuckets: UsageHistogramBucket[];
}

export interface UsageSubAllocatorAggregate {
    activeDownstreamCount: number;
    downstreamActiveAllocated: number;
    reallocatedPercent: number;
    directUsage: number;
    downstreamUsage: number;
}

export interface UsageChildRow {
    child: string;
    isLocal: boolean;
    isSubAllocator: boolean;
    periodUsage: number;
    currentUsage: number;
    peakUsage: number;
    activeUsage: number;
    activeQuota: number;
    activeQuotaKnown?: boolean;
    quota?: number;
    quotaKnown?: boolean;
    retiredUsage: number;
    totalUsage: number;
    utilizationPercent: number;
    utilizationBucket: string;
    nextExpiration?: number | null;
    nextQuotaDrop?: UsageQuotaDrop | null;
    subAllocator?: UsageSubAllocatorAggregate | null;
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
    subProjectSummary: UsageSubProjectSummary;
    children: UsageChildRow[];
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
    options?: {
        removeUnit?: boolean;
    },
): string {
    const formatted = formatAmount(rawValue * unit.balanceFactor);
    if (options?.removeUnit) {
        return formatted;
    }
    return `${formatted} ${unit.name}`;
}

export function usageFormatShortDate(timestamp: number): string {
    const d = new Date(timestamp);
    return d.toLocaleDateString(undefined, {
        month: "short",
        day: "numeric",
        year: "numeric",
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
    if (accountingModel === "CAPACITY" || !includeRetired) {
        const quotaKnown = row.activeQuotaKnown !== false && row.utilizationBucket !== "unknown";
        if (!quotaKnown) {
            return {
                usage: row.activeUsage,
                quota: 0,
                quotaKnown: false,
                utilizationPercent: 0,
                utilizationBucket: "unknown",
            };
        }
        return {
            usage: row.activeUsage,
            quota: row.activeQuota,
            quotaKnown: true,
            utilizationPercent: row.utilizationPercent,
            utilizationBucket: row.utilizationBucket,
        };
    }

    const usage = row.totalUsage;
    const quotaKnown = row.quotaKnown !== false && row.quota != null;
    if (!quotaKnown) {
        return {
            usage,
            quota: 0,
            quotaKnown: false,
            utilizationPercent: 0,
            utilizationBucket: "unknown",
        };
    }

    const quota = Math.max(row.quota ?? 0, row.activeQuota);
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
    } else if (usage === 0) {
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
        totalPeriodUsage: 0,
        totalActiveQuota: 0,
    }));
    const byKey = new Map(buckets.map(b => [b.key, b]));

    for (const row of rows) {
        if (row.isLocal) continue;
        const metrics = usageChildScopeMetrics(row, accountingModel, includeRetired);
        if (!metrics.quotaKnown) continue;
        const bucket = byKey.get(metrics.utilizationBucket);
        if (!bucket) continue;
        bucket.count++;
        bucket.totalPeriodUsage += row.periodUsage;
        bucket.totalActiveQuota += metrics.quota;
    }

    return buckets;
}
