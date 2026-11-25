import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {
    ProductCategoryId,
    productCategoryDisplayName,
    productTypeToIcon,
} from "@/Accounting/index";
import {callAPI} from "@/Authentication/DataHook";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {Box, Button, Flex, Icon, MainContainer} from "@/ui-components";
import {Toggle} from "@/ui-components/Toggle";
import {TooltipV2} from "@/ui-components/Tooltip";
import Warning from "@/ui-components/Warning";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {errorMessageOrDefault, looksLikeUUID, shortUUID} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {useProject} from "@/Project/cache";
import {useProjectId} from "@/Project/Api";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import * as Heading from "@/ui-components/Heading";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {useMaxContentWidth} from "@/Utilities/StylingUtilities";
import {projectInfoTitle, useProjectInfos} from "@/Project/InfoCache";
import {getStartOfDay} from "@/Utilities/DateUtilities";
import {exportUsage} from "@/Accounting/Usage";
import {PeriodFilter} from "@/Accounting/Allocations/Controls";
import {rangeBounds} from "@/Accounting/Allocations/Model";
import type {DateRange} from "@/Accounting/Allocations/Model";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {
    UsageDetailResponse,
    UsageUnitGroup,
    usageDaysUntil,
    usageDetailRetrieve,
    usageDropRiskDays,
    usageExplainUnit,
    usageFormatNumber,
    usageFormatShortDate,
    usageFormatValue,
    usageOverviewRetrieve,
    usageProductKey,
} from "@/Accounting/UsageV2/Api";
import {UcxTableIconButtonClass} from "@/UCX/UcxBrowser";
import {UsagePortfolioOverview} from "@/Accounting/UsageV2/PortfolioOverview";
import {
    ConsumptionSeriesMode,
    UsageCalendarHeatmap,
    UsageCapacityOccupancyChart,
    UsageConsumptionBurnupChart,
    UsageConsumptionRateChart,
    openUsageChartDialog,
} from "@/Accounting/UsageV2/Charts";
import {UsageSubProjectTable} from "@/Accounting/UsageV2/SubProjectTable";
import {UsageInferenceBreakdownSection} from "@/Accounting/UsageV2/InferenceBreakdown";

function defaultUsageDateRange(now = Date.now()): DateRange {
    const current = new Date(now);
    return {
        start: new Date(current.getFullYear(), current.getMonth() - 2, 1),
        end: new Date(current.getFullYear(), current.getMonth() + 1, 0),
    };
}

const PageStyle = injectStyle("usage-v2-page", k => `
    ${k} .usage-controls {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px 16px;
    }

    ${k} .usage-toggle {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        font-size: 13px;
        color: var(--textPrimary);
    }

    ${k} .usage-toggle-label {
        border: 0;
        padding: 0;
        background: transparent;
        color: var(--textSecondary);
        font-size: 13px;
        cursor: pointer;
    }

    ${k} .usage-toggle-label[data-active="true"] {
        color: var(--textPrimary);
    }

    ${k} .usage-toggle[data-disabled="true"] {
        opacity: 0.5;
        cursor: not-allowed;
    }

    ${k} .usage-toggle[data-disabled="true"] .usage-toggle-label {
        cursor: not-allowed;
    }

    ${k} .usage-nav-bar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px 20px;
        border-bottom: 1px solid var(--borderColor);
    }

    ${k} .usage-tabs {
        display: flex;
        flex-wrap: wrap;
        gap: 4px;
    }

    ${k} .usage-tab {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 8px 12px;
        border: 0;
        border-bottom: 2px solid transparent;
        margin-bottom: -1px;
        background: transparent;
        color: var(--textSecondary);
        font-size: 13.5px;
        font-weight: 500;
        cursor: pointer;
    }

    ${k} .usage-tab:hover {
        color: var(--textPrimary);
    }

    ${k} .usage-tab[data-active="true"] {
        border-bottom-color: var(--primaryMain);
        color: var(--textPrimary);
        font-weight: 600;
    }

    ${k} .usage-product-trigger {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        height: 32px;
        padding: 0 12px;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: transparent;
        color: var(--textPrimary);
        font-size: 13px;
        user-select: none;
    }

    ${k} .usage-product-trigger:hover {
        background: var(--rowHover);
    }

    ${k} .usage-kpi-strip {
        display: grid;
        grid-template-columns: repeat(4, minmax(0, 1fr));
        gap: 12px 0;
        padding: 4px 0;
    }

    ${k} .usage-kpi-strip > * {
        padding: 2px 18px;
        border-right: 1px solid var(--borderColor);
        min-width: 0;
    }

    ${k} .usage-kpi-strip > *:nth-child(4n + 1) {
        padding-left: 0;
    }

    ${k} .usage-kpi-strip > *:nth-child(4n) {
        padding-right: 0;
        border-right: 0;
    }

    ${k} .usage-kpi-strip > *:last-child {
        border-right: 0;
    }

    @media (max-width: 720px) {
        ${k} .usage-kpi-strip {
            grid-template-columns: repeat(2, minmax(0, 1fr));
        }
        ${k} .usage-kpi-strip > *:nth-child(4n + 1) {
            padding-left: 18px;
        }
        ${k} .usage-kpi-strip > *:nth-child(4n) {
            padding-right: 18px;
            border-right: 1px solid var(--borderColor);
        }
        ${k} .usage-kpi-strip > *:nth-child(2n + 1) {
            padding-left: 0;
        }
        ${k} .usage-kpi-strip > *:nth-child(2n) {
            padding-right: 0;
            border-right: 0;
        }
        ${k} .usage-kpi-strip > *:last-child {
            border-right: 0;
        }
    }

    ${k} .usage-kpi-item {
        display: flex;
        flex-direction: column;
        gap: 3px;
        min-width: 0;
    }

    ${k} .usage-kpi-title {
        font-size: 11.5px;
        color: var(--textSecondary);
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    ${k} .usage-kpi-value-row {
        display: flex;
        align-items: baseline;
        gap: 6px;
        flex-wrap: wrap;
        line-height: 1.2;
    }

    ${k} .usage-kpi-primary {
        font-size: 19px;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
    }

    ${k} .usage-kpi-unit {
        font-size: 12px;
        color: var(--textSecondary);
    }

    ${k} .usage-kpi-bar {
        height: 4px;
        border-radius: 999px;
        background: var(--borderColor);
        overflow: hidden;
        margin: 2px 0;
    }

    ${k} .usage-kpi-fill {
        height: 100%;
        border-radius: 999px;
    }

    ${k} .usage-kpi-detail {
        font-size: 11.5px;
        color: var(--textSecondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    ${k} .usage-chart-grid {
        display: grid;
        gap: 24px;
        align-items: start;
    }

    ${k} .usage-chart-col {
        display: flex;
        flex-direction: column;
        gap: 8px;
        min-width: 0;
    }

    ${k} .usage-section {
        display: flex;
        flex-direction: column;
        gap: 10px;
        padding-bottom: 20px;
    }

    ${k} .usage-section-head {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 8px 12px;
    }

    ${k} .usage-section-head h3 {
        margin: 0;
        font-size: 15px;
    }

    ${k} .usage-mode-row {
        display: inline-flex;
        align-items: center;
        gap: 2px;
    }

    ${k} .usage-mode-button {
        border: 0;
        border-radius: 8px;
        padding: 5px 9px;
        background: transparent;
        color: var(--textSecondary);
        font-size: 12px;
        cursor: pointer;
    }

    ${k} .usage-mode-button:hover {
        background: var(--rowHover);
    }

    ${k} .usage-mode-button[aria-pressed="true"] {
        background: color-mix(in srgb, var(--textPrimary) 8%, transparent);
        color: var(--textPrimary);
    }
`);

const ProductMenuStyle = injectStyle("usage-v2-product-menu", k => `
    ${k} {
        padding: 8px;
        max-height: 320px;
        overflow-y: auto;
    }

    ${k} .prod-option {
        display: flex;
        align-items: center;
        gap: 10px;
        padding: 8px;
        cursor: pointer;
        font-size: 13px;
        border-radius: 4px;
    }

    ${k} .prod-option:hover {
        background: var(--rowHover);
    }

    ${k} .prod-option-label {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        line-height: 1.25;
    }

    ${k} .prod-option-sub {
        font-size: 11px;
        color: var(--textSecondary);
    }
`);

const UsagePage: React.FunctionComponent = () => {
    const project = useProject();
    const projectId = useProjectId();
    const maxContentWidth = useMaxContentWidth();

    usePage("Usage", SidebarTabId.PROJECT);

    const [dateRange, setDateRange] = useState<DateRange>(defaultUsageDateRange);
    const [includeRetired, setIncludeRetired] = useState(false);

    const [overviewGroups, setOverviewGroups] = useState<UsageUnitGroup[]>([]);
    const [overviewHasData, setOverviewHasData] = useState(false);
    const [overviewLoading, setOverviewLoading] = useState(true);
    const [overviewError, setOverviewError] = useState<string | null>(null);

    const [selectedUnitKey, setSelectedUnitKey] = useState<string | null>(null);
    const [selectedProductKeys, setSelectedProductKeys] = useState<string[]>([]);
    const [pinnedChildren, setPinnedChildren] = useState<string[]>([]);

    const [detail, setDetail] = useState<UsageDetailResponse | null>(null);
    const [detailLoading, setDetailLoading] = useState(false);
    const [detailError, setDetailError] = useState<string | null>(null);
    const [detailFetchedKey, setDetailFetchedKey] = useState<string | null>(null);

    const [seriesModeOverride, setSeriesModeOverride] = useState<ConsumptionSeriesMode | null>(null);
    const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
    const [bucketFilter, setBucketFilter] = useState<string | null>(null);

    const normalizedPeriod = useMemo(() => {
        const bounds = rangeBounds(dateRange);
        const now = Date.now();
        const start = bounds.start ?? (now - 90 * 24 * 60 * 60 * 1000);
        const end = Math.min(now, bounds.end ?? now);
        return {start, end: Math.max(start, end)};
    }, [dateRange]);

    const detailSignature = useCallback((
        pId: string | undefined,
        start: number,
        end: number,
        unitKey: string,
        products: string[],
        pinned: string[],
    ): string => {
        return `${pId ?? ""}|${start}|${end}|${unitKey}|${products.join(",")}|${pinned.join(",")}`;
    }, []);

    useEffect(() => {
        let cancelled = false;
        setOverviewLoading(true);
        setOverviewError(null);

        callAPI(usageOverviewRetrieve({
            start: normalizedPeriod.start,
            end: normalizedPeriod.end,
            initialUnitGroupKey: selectedUnitKey ?? undefined,
        }))
            .then(res => {
                if (cancelled) return;
                const groups = res.unitGroups ?? [];
                setOverviewGroups(groups);
                setOverviewHasData(res.hasData ?? false);
                setOverviewLoading(false);

                const nextUnitKey = (selectedUnitKey && groups.some(g => g.key === selectedUnitKey))
                    ? selectedUnitKey
                    : (groups.length > 0 ? groups[0].key : null);
                setSelectedUnitKey(nextUnitKey);

                if (res.initialDetail && nextUnitKey && res.initialDetail.unitGroupKey === nextUnitKey) {
                    setDetail(res.initialDetail);
                    setDetailError(null);
                    setDetailLoading(false);
                    setSelectedProductKeys([]);
                    setPinnedChildren([]);
                    setDetailFetchedKey(detailSignature(
                        projectId,
                        normalizedPeriod.start,
                        normalizedPeriod.end,
                        nextUnitKey,
                        [],
                        [],
                    ));
                } else if (!nextUnitKey) {
                    setDetail(null);
                    setDetailFetchedKey(null);
                }
            })
            .catch(err => {
                if (cancelled) return;
                setOverviewError(errorMessageOrDefault(err, "Could not load usage overview"));
                setOverviewGroups([]);
                setOverviewHasData(false);
                setSelectedUnitKey(null);
                setDetail(null);
                setDetailFetchedKey(null);
                setOverviewLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [projectId, normalizedPeriod]);

    useEffect(() => {
        setSelectedProductKeys([]);
        setPinnedChildren([]);
        setSeriesModeOverride(null);
        setHighlightedKey(null);
        setBucketFilter(null);
    }, [projectId]);

    useEffect(() => {
        setPinnedChildren([]);
        setSeriesModeOverride(null);
        setHighlightedKey(null);
        setBucketFilter(null);
    }, [selectedUnitKey]);

    const activeGroup = useMemo(
        () => overviewGroups.find(g => g.key === selectedUnitKey) ?? null,
        [overviewGroups, selectedUnitKey],
    );

    useEffect(() => {
        if (!activeGroup) {
            setDetail(null);
            return;
        }

        const sig = detailSignature(
            projectId,
            normalizedPeriod.start,
            normalizedPeriod.end,
            activeGroup.key,
            selectedProductKeys,
            pinnedChildren,
        );
        if (detailFetchedKey === sig) {
            return;
        }

        let cancelled = false;
        setDetailLoading(true);
        setDetailError(null);

        const productFilters: ProductCategoryId[] = selectedProductKeys.map(key => {
            const separator = key.indexOf("/");
            return {
                provider: key.slice(0, separator),
                name: key.slice(separator + 1),
            };
        });

        callAPI(usageDetailRetrieve({
            start: normalizedPeriod.start,
            end: normalizedPeriod.end,
            unitGroupKey: activeGroup.key,
            products: productFilters,
            pinnedChildren,
        }))
            .then(res => {
                if (cancelled) return;
                setDetail(res);
                setDetailFetchedKey(sig);
                setDetailLoading(false);
            })
            .catch(err => {
                if (cancelled) return;
                setDetailError(errorMessageOrDefault(err, "Could not load usage details"));
                setDetail(null);
                setDetailLoading(false);
            });

        return () => {
            cancelled = true;
        };
    }, [projectId, normalizedPeriod, activeGroup, selectedProductKeys, pinnedChildren, detailFetchedKey, detailSignature]);

    const chartChildProjectIds = useMemo(() => {
        if (!detail) return [];
        const ids = new Set<string>();
        for (const pt of detail.timeline ?? []) {
            for (const c of pt.byChild ?? []) {
                if (looksLikeUUID(c.key)) ids.add(c.key);
            }
        }
        return [...ids];
    }, [detail]);

    const chartProjectInfo = useProjectInfos(chartChildProjectIds).data;

    const unit = useMemo(() => {
        if (!detail) return null;
        return usageExplainUnit(detail.unitAndFrequency, detail.productType);
    }, [detail]);

    const productLabelMap = useMemo(() => {
        const map = new Map<string, string>();
        if (!detail) return map;
        for (const cat of detail.productsCovered ?? []) {
            const key = usageProductKey(cat);
            map.set(key, `${productCategoryDisplayName(cat)} (${getShortProviderTitle(cat.provider)})`);
        }
        return map;
    }, [detail]);

    const labelForKey = useCallback((key: string, mode: ConsumptionSeriesMode): string => {
        if (key === "__total__") return "Total";
        if (key === "Other") return "Other";
        if (mode === "product") {
            return productLabelMap.get(key) ?? key;
        }
        if (key === "") return "Local (this project)";
        if (looksLikeUUID(key)) {
            return projectInfoTitle(chartProjectInfo[key]) ?? shortUUID(key);
        }
        return key;
    }, [productLabelMap, chartProjectInfo]);

    const handleSelectUnit = useCallback((unitKey: string) => {
        setSelectedUnitKey(unitKey);
        setSelectedProductKeys([]);
    }, []);

    const handleToggleOverviewProduct = useCallback((unitKey: string, productKey: string) => {
        setHighlightedKey(null);
        if (selectedUnitKey !== unitKey) {
            setSelectedUnitKey(unitKey);
            setSelectedProductKeys([productKey]);
            return;
        }
        setSelectedProductKeys(prev => {
            if (prev.includes(productKey)) {
                return prev.filter(k => k !== productKey);
            }
            return [...prev, productKey];
        });
    }, [selectedUnitKey]);

    const handleToggleProduct = useCallback((productKey: string) => {
        setHighlightedKey(null);
        setSelectedProductKeys(prev => {
            if (prev.includes(productKey)) {
                return prev.filter(k => k !== productKey);
            }
            return [...prev, productKey];
        });
    }, []);

    const handleTogglePin = useCallback((child: string) => {
        setHighlightedKey(null);
        setPinnedChildren(prev => {
            if (prev.includes(child)) {
                return prev.filter(c => c !== child);
            }
            if (prev.length >= 5) return prev;
            return [...prev, child];
        });
    }, []);

    const exportAll = useCallback(() => {
        let workspaceName = project.fetch().specification.title;
        if (workspaceName === "") workspaceName = "My workspace";
        const slug = workspaceName.toLowerCase().replace(/\s+/g, "-");
        exportUsage(
            [{period: normalizedPeriod, overview: overviewGroups, detail}],
            [
                {key: "period", value: "Period", defaultChecked: true},
                {key: "overview", value: "Overview", defaultChecked: true},
                {key: "detail", value: "Active view detail", defaultChecked: true},
            ],
            workspaceName,
            {
                formats: ["json"],
                fileName: `usage-report-${slug}`,
            },
        );
    }, [project, normalizedPeriod, overviewGroups, detail]);

    if (project.fetch().status.personalProviderProjectFor != null) {
        return <MainContainer
            main={<>
                <Heading.h2>Unavailable for this project</Heading.h2>
                <p>
                    This project belongs to a provider which does not support the accounting and project management
                    features of UCloud. Try again with a different project.
                </p>
            </>}
        />;
    }

    const canConsumeResources = project.fetch().specification.canConsumeResources !== false;
    const hideLocal = !canConsumeResources && (detail?.kpis.localUsageAtEnd ?? 0) === 0 && (detail?.kpis.periodLocalUsage ?? 0) === 0;

    const hasMultipleProducts = (detail?.productsCovered?.length ?? 0) > 1;
    const subProjectCount = detail?.subProjectSummary?.totalCount ?? 0;
    const isLargeGrantAllocator = subProjectCount > 20;
    const isCapacityGroup = (activeGroup?.accountingModel ?? detail?.accountingModel) === "CAPACITY";
    const effectiveIncludeRetired = !isCapacityGroup && includeRetired;

    const effectiveSeriesMode: ConsumptionSeriesMode = useMemo(() => {
        if (seriesModeOverride !== null) {
            if (seriesModeOverride === "product" && !hasMultipleProducts) {
                return subProjectCount > 0 && !isLargeGrantAllocator ? "child" : "total";
            }
            if (seriesModeOverride === "child" && subProjectCount === 0) {
                return hasMultipleProducts ? "product" : "total";
            }
            return seriesModeOverride;
        }
        if (pinnedChildren.length > 0) return "total";
        if (isLargeGrantAllocator) return hasMultipleProducts ? "product" : "total";
        if (subProjectCount > 0) return "child";
        if (hasMultipleProducts) return "product";
        return "total";
    }, [seriesModeOverride, hasMultipleProducts, subProjectCount, isLargeGrantAllocator, pinnedChildren.length]);

    const chartWidth = Math.max(360, maxContentWidth - 20);
    const twoColCharts = chartWidth >= 920;
    const rateChartWidth = twoColCharts ? Math.floor((chartWidth - 24) / 2) : chartWidth;
    const burnupChartWidth = twoColCharts ? Math.floor((chartWidth - 24) / 2) : chartWidth;
    const rateChartHeight = twoColCharts ? 250 : Math.max(240, Math.min(310, Math.round(chartWidth * 0.27)));
    const burnupChartHeight = twoColCharts ? 250 : Math.max(240, Math.min(310, Math.round(chartWidth * 0.27)));
    const capacityChartHeight = Math.max(250, Math.min(320, Math.round(chartWidth * 0.27)));

    const renderFocusContent = () => {
        if (!detail || !unit || !detail.hasData) return null;

        const isCapacity = detail.accountingModel === "CAPACITY";
        const kpis = detail.kpis;
        const fmt = (v: number) => usageFormatValue(unit, v);
        const fmtNum = (v: number) => usageFormatNumber(unit, v);

        const alignedStart = getStartOfDay(new Date(normalizedPeriod.start)).getTime();
        const alignedEnd = getStartOfDay(new Date(normalizedPeriod.end)).getTime();
        const periodDays = Math.max(1, Math.floor((alignedEnd - alignedStart) / (1000 * 60 * 60 * 24)));
        const burnRatePerDay = kpis.periodUsage / periodDays;

        const remainingActive = Math.max(0, kpis.activeQuotaAtEnd - kpis.activeUsageAtEnd);
        const activeBalance = Math.max(0, Math.min(remainingActive, kpis.maxUsableAtEnd));
        const daysToDepletion = burnRatePerDay > 0 ? Math.floor(activeBalance / burnRatePerDay) : null;
        const daysToExpiry = usageDaysUntil(kpis.nextMeaningfulExpiration, normalizedPeriod.end);
        const dropRiskDays = usageDropRiskDays(kpis.nextQuotaDrop ?? undefined, detail.accountingModel, normalizedPeriod.end);

        const activeUtilPct = kpis.activeQuotaAtEnd > 0
            ? (kpis.activeUsageAtEnd / kpis.activeQuotaAtEnd) * 100
            : (isCapacity && kpis.activeUsageAtEnd > 0 ? 100 : 0);
        const totalUtilPct = kpis.quotaAtEnd > 0 ? (kpis.totalUsageAtEnd / kpis.quotaAtEnd) * 100 : 0;
        const expiredQuota = Math.max(0, kpis.quotaAtEnd - kpis.activeQuotaAtEnd);
        const expiredUnused = Math.max(0, expiredQuota - kpis.retiredUsageAtEnd);
        const expiredUnusedPct = expiredQuota > 0 ? (expiredUnused / expiredQuota) * 100 : 0;
        const isLifetimeConsumption = !isCapacity && effectiveIncludeRetired;
        const overCommitActive = kpis.activeQuotaAtEnd > 0 ? kpis.activeAllocatedAtEnd / kpis.activeQuotaAtEnd : 0;
        const overCommitTotal = kpis.quotaAtEnd > 0 ? kpis.totalAllocatedAtEnd / kpis.quotaAtEnd : 0;
        const capacityExcess = isCapacity ? Math.max(0, kpis.activeUsageAtEnd - kpis.activeQuotaAtEnd) : 0;

        const projectedSpendBeforeExpiry = (daysToExpiry !== null && burnRatePerDay > 0)
            ? burnRatePerDay * daysToExpiry
            : 0;
        const projectedUnusedAtExpiry = (daysToExpiry !== null && !isCapacity)
            ? Math.max(0, activeBalance - projectedSpendBeforeExpiry)
            : null;

        const showBreakdownSwitcher = hasMultipleProducts || subProjectCount > 0;
        const hasSubAllocators = detail.subProjectSummary.subAllocatorCount > 0;

        const spentTooltip = !hideLocal && subProjectCount > 0
            ? `Local: ${fmt(kpis.periodLocalUsage)}, Sub-projects: ${fmt(kpis.periodChildrenUsage)}`
            : undefined;
        const capacityUsageTooltip = [
            `Average: ${fmt(kpis.avgUsage)}`,
            ...(!hideLocal && subProjectCount > 0
                ? [`Local: ${fmt(kpis.localUsageAtEnd)}`, `Sub-projects: ${fmt(Math.max(0, kpis.totalUsageAtEnd - kpis.localUsageAtEnd))}`]
                : []),
        ].join(" — ");
        const quotaExpiryTooltip = kpis.nextMeaningfulExpiration
            ? `Next meaningful expiry: ${usageFormatShortDate(kpis.nextMeaningfulExpiration)}`
            : undefined;
        const balanceTooltip = kpis.upstreamCapped
            ? `Limited by parent project (own balance: ${fmt(remainingActive)})`
            : undefined;
        const runwaySub = daysToDepletion !== null && daysToExpiry !== null && daysToDepletion < daysToExpiry
            ? "Depletes before expiry"
            : projectedUnusedAtExpiry !== null && projectedUnusedAtExpiry > 0
                ? `Est. unused ${fmt(projectedUnusedAtExpiry)}`
                : daysToDepletion !== null
                    ? "At current burn rate"
                    : "No consumption in period";
        const grantedTooltip = isLifetimeConsumption
            ? `Active granted: ${fmt(kpis.activeAllocatedAtEnd)}, Total granted: ${fmt(kpis.totalAllocatedAtEnd)}`
            : `Active granted: ${fmt(kpis.activeAllocatedAtEnd)}`;
        const grantedPct = isLifetimeConsumption
            ? (kpis.quotaAtEnd > 0
                ? Math.min(100, (kpis.totalAllocatedAtEnd / kpis.quotaAtEnd) * 100)
                : (kpis.totalAllocatedAtEnd > 0 ? 100 : 0))
            : (kpis.activeQuotaAtEnd > 0
                ? Math.min(100, (kpis.activeAllocatedAtEnd / kpis.activeQuotaAtEnd) * 100)
                : (kpis.activeAllocatedAtEnd > 0 ? 100 : 0));

        const displayUtilPct = isLifetimeConsumption ? totalUtilPct : activeUtilPct;
        const usageBarColor = displayUtilPct >= 100
            ? "var(--errorMain)"
            : displayUtilPct >= 90
                ? "var(--warningMain)"
                : "var(--chart-blue-main)";

        const inferenceSection = detail.inferenceBreakdown ? (
            <UsageInferenceBreakdownSection
                breakdown={detail.inferenceBreakdown}
                bucketDurationMs={detail.bucketDurationMs}
                unit={unit}
                twoColCharts={twoColCharts}
                rateChartWidth={rateChartWidth}
                burnupChartWidth={burnupChartWidth}
                rateChartHeight={rateChartHeight}
                burnupChartHeight={burnupChartHeight}
                pinnedChildren={pinnedChildren}
                onClearPinned={() => setPinnedChildren([])}
            />
        ) : null;

        return <Flex flexDirection="column" gap="16px">
            {kpis.upstreamCapped ? (
                <Warning mb="0px">
                    <b>Limited by parent project:</b> Only <b>{fmt(kpis.maxUsableAtEnd)}</b> of the {fmt(remainingActive)} remaining balance can currently be used, because a parent project does not have enough quota left.
                </Warning>
            ) : null}

            {isCapacity && capacityExcess > 0 ? (
                <Warning mb="0px">
                    <b>Over active quota:</b> Current usage ({fmt(kpis.activeUsageAtEnd)}) exceeds active quota ({fmt(kpis.activeQuotaAtEnd)}) by{" "}
                    <b>{fmt(capacityExcess)}</b>.
                </Warning>
            ) : null}

            {kpis.nextQuotaDrop && dropRiskDays !== null ? (
                <Warning mb="0px">
                    {isCapacity ? <>
                        <b>Upcoming quota drop:</b> On <b>{usageFormatShortDate(kpis.nextQuotaDrop.timestamp)}</b> (in{" "}
                        <b>{formatNumber(dropRiskDays)} days</b>), active quota drops from{" "}
                        {fmt(kpis.nextQuotaDrop.quotaBefore)} to <b>{fmt(kpis.nextQuotaDrop.quotaAfter)}</b>, which is below current usage of{" "}
                        <b>{fmt(kpis.activeUsageAtEnd)}</b>.
                    </> : <>
                        <b>Upcoming quota drop:</b> On <b>{usageFormatShortDate(kpis.nextQuotaDrop.timestamp)}</b> (in{" "}
                        <b>{formatNumber(dropRiskDays)} days</b>), active quota of{" "}
                        <b>{fmt(kpis.nextQuotaDrop.quotaBefore)}</b> expires with no renewal in place.
                    </>}
                </Warning>
            ) : null}

            <div className="usage-kpi-strip">
                {isCapacity ? <>
                    <TooltipV2 tooltip={capacityUsageTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Current usage</span>
                                <span style={{color: capacityExcess > 0 ? "var(--errorMain)" : undefined}}>
                                    {capacityExcess > 0 ? "Over quota" : `${activeUtilPct.toFixed(1)}%`}
                                </span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary" style={{color: capacityExcess > 0 ? "var(--errorMain)" : undefined}}>
                                    {fmtNum(kpis.activeUsageAtEnd)}
                                </span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-bar">
                                <div
                                    className="usage-kpi-fill"
                                    style={{
                                        width: `${Math.min(100, activeUtilPct)}%`,
                                        background: capacityExcess > 0 ? "var(--errorMain)" : usageBarColor,
                                    }}
                                />
                            </div>
                            <div className="usage-kpi-detail">Peak {fmt(kpis.peakUsage)}</div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={balanceTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Active quota</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.activeQuotaAtEnd)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail" style={{color: capacityExcess > 0 ? "var(--errorMain)" : (kpis.upstreamCapped ? "var(--warningMain)" : undefined)}}>
                                {capacityExcess > 0
                                    ? `Exceeds by ${fmt(capacityExcess)}`
                                    : `Balance ${fmt(activeBalance)}`}
                            </div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={kpis.nextQuotaDrop ? usageFormatShortDate(kpis.nextQuotaDrop.timestamp) : undefined}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Next quota drop</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">
                                    {kpis.nextQuotaDrop ? `${formatNumber(usageDaysUntil(kpis.nextQuotaDrop.timestamp, normalizedPeriod.end) ?? 0)} days` : "None"}
                                </span>
                            </div>
                            <div className="usage-kpi-detail">
                                {kpis.nextQuotaDrop ? `Drops to ${fmt(kpis.nextQuotaDrop.quotaAfter)}` : "—"}
                            </div>
                        </div>
                    </TooltipV2>
                </> : isLifetimeConsumption ? <>
                    <TooltipV2 tooltip={spentTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Spent in period</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.periodUsage)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail">Avg {fmt(burnRatePerDay)}/day</div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={`Active: ${fmt(kpis.activeUsageAtEnd)}, Retired: ${fmt(kpis.retiredUsageAtEnd)}`}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Lifetime usage</span>
                                <span>{totalUtilPct.toFixed(1)}%</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.totalUsageAtEnd)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-bar">
                                <div
                                    className="usage-kpi-fill"
                                    style={{
                                        width: `${Math.min(100, totalUtilPct)}%`,
                                        background: usageBarColor,
                                    }}
                                />
                            </div>
                            <div className="usage-kpi-detail">
                                {kpis.retiredUsageAtEnd > 0
                                    ? `Active ${fmt(kpis.activeUsageAtEnd)}, retired ${fmt(kpis.retiredUsageAtEnd)}`
                                    : "All usage active"}
                            </div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={`Active quota: ${fmt(kpis.activeQuotaAtEnd)}, Expired quota: ${fmt(expiredQuota)}, Balance: ${fmt(activeBalance)}`}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Lifetime quota</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.quotaAtEnd)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail">
                                Active {fmt(kpis.activeQuotaAtEnd)}
                            </div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={`Expired quota: ${fmt(expiredQuota)}, Retired usage: ${fmt(kpis.retiredUsageAtEnd)}`}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Expired unused</span>
                                {expiredQuota > 0 ? <span>{expiredUnusedPct.toFixed(1)}% unspent</span> : null}
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(expiredUnused)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail">
                                Retired usage {fmt(kpis.retiredUsageAtEnd)}
                            </div>
                        </div>
                    </TooltipV2>
                </> : <>
                    <TooltipV2 tooltip={spentTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Spent in period</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.periodUsage)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail">Avg {fmt(burnRatePerDay)}/day</div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={balanceTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Active usage</span>
                                <span>{activeUtilPct.toFixed(1)}%</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.activeUsageAtEnd)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-bar">
                                <div
                                    className="usage-kpi-fill"
                                    style={{
                                        width: `${Math.min(100, activeUtilPct)}%`,
                                        background: usageBarColor,
                                    }}
                                />
                            </div>
                            <div className="usage-kpi-detail" style={{color: kpis.upstreamCapped ? "var(--warningMain)" : undefined}}>
                                Balance {fmt(activeBalance)}
                            </div>
                        </div>
                    </TooltipV2>

                    <TooltipV2 tooltip={quotaExpiryTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>Active quota</span>
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">{fmtNum(kpis.activeQuotaAtEnd)}</span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-detail" style={{color: kpis.upstreamCapped ? "var(--warningMain)" : undefined}}>
                                {kpis.upstreamCapped
                                    ? `Usable balance ${fmt(kpis.maxUsableAtEnd)}`
                                    : daysToExpiry !== null
                                        ? `Next expiry ${formatNumber(daysToExpiry)}d`
                                        : "No upcoming expiry"}
                            </div>
                        </div>
                    </TooltipV2>

                    <div className="usage-kpi-item">
                        <div className="usage-kpi-title">
                            <span>Est. runway</span>
                        </div>
                        <div className="usage-kpi-value-row">
                            <span className="usage-kpi-primary">
                                {daysToDepletion === null ? "—" : `${formatNumber(daysToDepletion)} days`}
                            </span>
                        </div>
                        <div className="usage-kpi-detail">{runwaySub}</div>
                    </div>
                </>}

                {subProjectCount > 0 ? (
                    <TooltipV2 tooltip={grantedTooltip}>
                        <div className="usage-kpi-item">
                            <div className="usage-kpi-title">
                                <span>{isLifetimeConsumption ? "Total granted to children" : "Granted to children"}</span>
                                {isLifetimeConsumption
                                    ? (kpis.quotaAtEnd > 0 ? <span>{overCommitTotal.toFixed(1)}x of quota</span> : null)
                                    : (kpis.activeQuotaAtEnd > 0 ? <span>{overCommitActive.toFixed(1)}x of quota</span> : null)}
                            </div>
                            <div className="usage-kpi-value-row">
                                <span className="usage-kpi-primary">
                                    {fmtNum(isLifetimeConsumption ? kpis.totalAllocatedAtEnd : kpis.activeAllocatedAtEnd)}
                                </span>
                                <span className="usage-kpi-unit">{unit.name}</span>
                            </div>
                            <div className="usage-kpi-bar">
                                <div
                                    className="usage-kpi-fill"
                                    style={{
                                        width: `${grantedPct}%`,
                                        background: "var(--chart-blue-main)",
                                    }}
                                />
                            </div>
                            <div className="usage-kpi-detail">
                                {formatNumber(subProjectCount)} sub-project{subProjectCount === 1 ? "" : "s"}
                            </div>
                        </div>
                    </TooltipV2>
                ) : null}

                {hasSubAllocators ? (
                    <div className="usage-kpi-item">
                        <div className="usage-kpi-title">
                            <span>Re-allocated to sub-children</span>
                        </div>
                        <div className="usage-kpi-value-row">
                            <span className="usage-kpi-primary">{fmtNum(kpis.downstreamActiveAllocated)}</span>
                            <span className="usage-kpi-unit">{unit.name}</span>
                        </div>
                        <div className="usage-kpi-detail">
                            By {formatNumber(detail.subProjectSummary.subAllocatorCount)} sub-allocator{detail.subProjectSummary.subAllocatorCount === 1 ? "" : "s"}
                        </div>
                    </div>
                ) : null}
            </div>

            {isCapacity ? (
                <section className="usage-section">
                    <div className="usage-section-head">
                        <h3>Usage over time</h3>
                        <Flex alignItems="center" gap="8px" flexWrap="wrap">
                            {pinnedChildren.length > 0 ? (
                                <Button color="ghost" onClick={() => setPinnedChildren([])}>
                                    Clear pinned ({pinnedChildren.length})
                                </Button>
                            ) : null}
                            {showBreakdownSwitcher ? (
                                <div className="usage-mode-row">
                                    <button
                                        type="button"
                                        className="usage-mode-button"
                                        aria-pressed={effectiveSeriesMode === "total"}
                                        onClick={() => setSeriesModeOverride("total")}
                                    >
                                        {pinnedChildren.length > 0 ? "Pinned" : "Total"}
                                    </button>
                                    {hasMultipleProducts ? (
                                        <button
                                            type="button"
                                            className="usage-mode-button"
                                            aria-pressed={effectiveSeriesMode === "product"}
                                            onClick={() => setSeriesModeOverride("product")}
                                        >
                                            By product
                                        </button>
                                    ) : null}
                                    {subProjectCount > 0 ? (
                                        <button
                                            type="button"
                                            className="usage-mode-button"
                                            aria-pressed={effectiveSeriesMode === "child"}
                                            onClick={() => setSeriesModeOverride("child")}
                                        >
                                            {isLargeGrantAllocator ? "Top sub-projects" : "By sub-project"}
                                        </button>
                                    ) : null}
                                </div>
                            ) : null}
                        </Flex>
                    </div>
                    <UsageCapacityOccupancyChart
                        detail={detail}
                        unit={unit}
                        width={chartWidth}
                        height={capacityChartHeight}
                        seriesMode={effectiveSeriesMode}
                        pinnedChildren={pinnedChildren}
                        highlightedKey={highlightedKey}
                        onHighlightKey={setHighlightedKey}
                        labelForKey={labelForKey}
                    />
                </section>
            ) : (
                <section className="usage-section">
                    {(detail.timeline?.length ?? 0) > 0 ? (
                        <div className="usage-chart-col" style={{marginBottom: 8}}>
                            <div className="usage-section-head">
                                <h3>Daily activity</h3>
                            </div>
                            <UsageCalendarHeatmap
                                detail={detail}
                                unit={unit}
                                width={chartWidth}
                            />
                        </div>
                    ) : null}
                    <div
                        className="usage-chart-grid"
                        style={{gridTemplateColumns: twoColCharts ? "repeat(2, minmax(0, 1fr))" : "minmax(0, 1fr)"}}
                    >
                        <div className="usage-chart-col">
                            <div className="usage-section-head">
                                <h3>Consumption rate</h3>
                                <Flex alignItems="center" gap="8px" flexWrap="wrap">
                                    {pinnedChildren.length > 0 ? (
                                        <Button color="ghost" onClick={() => setPinnedChildren([])}>
                                            Clear pinned ({pinnedChildren.length})
                                        </Button>
                                    ) : null}
                                    {showBreakdownSwitcher ? (
                                        <div className="usage-mode-row">
                                            <button
                                                type="button"
                                                className="usage-mode-button"
                                                aria-pressed={effectiveSeriesMode === "total"}
                                                onClick={() => setSeriesModeOverride("total")}
                                            >
                                                {pinnedChildren.length > 0 ? "Pinned" : "Total"}
                                            </button>
                                            {hasMultipleProducts ? (
                                                <button
                                                    type="button"
                                                    className="usage-mode-button"
                                                    aria-pressed={effectiveSeriesMode === "product"}
                                                    onClick={() => setSeriesModeOverride("product")}
                                                >
                                                    By product
                                                </button>
                                            ) : null}
                                            {subProjectCount > 0 ? (
                                                <button
                                                    type="button"
                                                    className="usage-mode-button"
                                                    aria-pressed={effectiveSeriesMode === "child"}
                                                    onClick={() => setSeriesModeOverride("child")}
                                                >
                                                    {isLargeGrantAllocator ? "Top sub-projects" : "By sub-project"}
                                                </button>
                                            ) : null}
                                        </div>
                                    ) : null}
                                    <TooltipV2 tooltip="Expand chart">
                                        <button
                                            type="button"
                                            className={UcxTableIconButtonClass}
                                            aria-label="Expand consumption rate chart"
                                            onClick={() => openUsageChartDialog("Consumption rate", (w, h) => (
                                                <UsageConsumptionRateChart
                                                    detail={detail}
                                                    unit={unit}
                                                    width={w}
                                                    height={h}
                                                    seriesMode={effectiveSeriesMode}
                                                    pinnedChildren={pinnedChildren}
                                                    highlightedKey={null}
                                                    onHighlightKey={() => void 0}
                                                    labelForKey={labelForKey}
                                                />
                                            ))}
                                        >
                                            <Icon name="heroArrowsPointingOut" size={15} />
                                        </button>
                                    </TooltipV2>
                                </Flex>
                            </div>
                            <UsageConsumptionRateChart
                                detail={detail}
                                unit={unit}
                                width={rateChartWidth}
                                height={rateChartHeight}
                                seriesMode={effectiveSeriesMode}
                                pinnedChildren={pinnedChildren}
                                highlightedKey={highlightedKey}
                                onHighlightKey={setHighlightedKey}
                                labelForKey={labelForKey}
                            />
                        </div>

                        <div className="usage-chart-col">
                            <div className="usage-section-head">
                                <h3>{effectiveIncludeRetired ? "Lifetime cumulative usage" : "Cumulative usage"}</h3>
                                <TooltipV2 tooltip="Expand chart">
                                    <button
                                        type="button"
                                        className={UcxTableIconButtonClass}
                                        aria-label="Expand cumulative usage chart"
                                        onClick={() => openUsageChartDialog(
                                            effectiveIncludeRetired ? "Lifetime cumulative usage" : "Cumulative usage",
                                            (w, h) => (
                                                <UsageConsumptionBurnupChart
                                                    detail={detail}
                                                    unit={unit}
                                                    width={w}
                                                    height={h}
                                                    includeRetired={effectiveIncludeRetired}
                                                />
                                            ),
                                        )}
                                    >
                                        <Icon name="heroArrowsPointingOut" size={15} />
                                    </button>
                                </TooltipV2>
                            </div>
                            <UsageConsumptionBurnupChart
                                detail={detail}
                                unit={unit}
                                width={burnupChartWidth}
                                height={burnupChartHeight}
                                includeRetired={effectiveIncludeRetired}
                            />
                        </div>
                    </div>
                </section>
            )}

            {subProjectCount > 0 ? (
                <section className="usage-section">
                    <UsageSubProjectTable
                        rows={detail.children ?? []}
                        utilizationBuckets={detail.subProjectSummary.utilizationBuckets ?? []}
                        accountingModel={detail.accountingModel}
                        unit={unit}
                        includeRetired={effectiveIncludeRetired}
                        hideLocal={hideLocal}
                        periodEnd={normalizedPeriod.end}
                        chartWidth={chartWidth}
                        bucketFilter={bucketFilter}
                        onSelectBucket={setBucketFilter}
                        pinnedChildren={pinnedChildren}
                        onTogglePin={handleTogglePin}
                        onClearFilters={() => {
                            setBucketFilter(null);
                        }}
                        exportTitle={(detail.productsCovered?.length ?? 0) === 1
                            ? productCategoryDisplayName(detail.productsCovered[0])
                            : detail.title}
                        workspaceTitle={project.fetch().specification.title}
                        beforeSubProjects={inferenceSection}
                    />
                </section>
            ) : inferenceSection}
        </Flex>;
    };

    return <MainContainer
        main={<div className={PageStyle}>
            <Flex flexDirection="column" gap="20px">
                <Flex alignItems="center" gap="12px" flexWrap="wrap">
                    <h3 className="title" style={{margin: 0}}>Usage report</h3>
                    <Box flexGrow={1} />
                    <ProjectSwitcher />
                </Flex>

                <div className="usage-controls">
                    <PeriodFilter
                        value={dateRange}
                        onChange={setDateRange}
                    />

                    <Box flexGrow={1} />

                    <Button height={35} onClick={exportAll} disabled={overviewGroups.length === 0}>
                        <Icon name="heroArrowDownTray" mr={8} />
                        Export all
                    </Button>
                </div>

                {(normalizedPeriod.start < new Date("2026-01-28").getTime() ||
                    normalizedPeriod.end < new Date("2026-01-28").getTime()) ? (
                    <Warning mb="0px">
                        Data prior to 28/01/2026 is incomplete due to the historic data being produced by an older
                        version of UCloud.
                    </Warning>
                ) : null}

                {overviewLoading ? (
                    <HexSpin />
                ) : overviewError ? (
                    <div>{overviewError}</div>
                ) : overviewGroups.length === 0 ? (
                    <div>No usage or allocation history available for this project.</div>
                ) : !overviewHasData ? (
                    <div>No usage data for this period.</div>
                ) : <>
                    <UsagePortfolioOverview
                        unitGroups={overviewGroups}
                        selectedUnitKey={selectedUnitKey}
                        selectedProductKeys={selectedProductKeys}
                        includeRetired={effectiveIncludeRetired}
                        periodEnd={normalizedPeriod.end}
                        onSelectUnit={handleSelectUnit}
                        onToggleProduct={handleToggleOverviewProduct}
                    />

                    <div className="usage-nav-bar">
                        <div className="usage-tabs" role="tablist" aria-label="Accounting unit groups">
                            {overviewGroups.map(group => {
                                const active = group.key === selectedUnitKey;
                                return <button
                                    key={group.key}
                                    type="button"
                                    role="tab"
                                    aria-selected={active}
                                    className="usage-tab"
                                    data-active={active ? "true" : undefined}
                                    onClick={() => handleSelectUnit(group.key)}
                                >
                                    <Icon name={productTypeToIcon(group.productType)} size={15} />
                                    <span>{group.title}</span>
                                </button>;
                            })}
                        </div>

                        <Flex ml="auto" mb="6px" alignItems="center" gap="16px" flexWrap="wrap">
                            <TooltipV2
                                tooltip={isCapacityGroup
                                    ? "Lifetime usage only applies to consumption-based resources. Capacity resources always show current usage."
                                    : undefined}
                            >
                                <div
                                    className="usage-toggle"
                                    role="group"
                                    aria-label="Usage scope"
                                    data-disabled={isCapacityGroup ? "true" : undefined}
                                >
                                    <button
                                        type="button"
                                        className="usage-toggle-label"
                                        data-active={!effectiveIncludeRetired ? "true" : undefined}
                                        disabled={isCapacityGroup}
                                        onClick={() => {
                                            if (!isCapacityGroup) setIncludeRetired(false);
                                        }}
                                    >
                                        Active usage
                                    </button>
                                    <Toggle
                                        height={18}
                                        activeColor="primaryMain"
                                        checked={effectiveIncludeRetired}
                                        disabled={isCapacityGroup}
                                        onChange={prev => {
                                            if (!isCapacityGroup) setIncludeRetired(!prev);
                                        }}
                                        ariaLabel="Lifetime usage"
                                    />
                                    <button
                                        type="button"
                                        className="usage-toggle-label"
                                        data-active={effectiveIncludeRetired ? "true" : undefined}
                                        disabled={isCapacityGroup}
                                        onClick={() => {
                                            if (!isCapacityGroup) setIncludeRetired(true);
                                        }}
                                    >
                                        Lifetime usage
                                    </button>
                                </div>
                            </TooltipV2>

                            {activeGroup && activeGroup.products.length > 1 ? (
                                <ClickableDropdown
                                    trigger={<span className="usage-product-trigger">
                                        <Icon name="heroCpuChip" size={14} />
                                        Products
                                        {selectedProductKeys.length > 0 ? ` (${formatNumber(selectedProductKeys.length)})` : ""}
                                        <Icon name="heroChevronDown" size={12} />
                                    </span>}
                                    triggerAriaLabel="Filter by product"
                                    width={300}
                                    focusable
                                    keepOpenOnClick
                                    colorOnHover={false}
                                    hoverColor="rowHover"
                                    paddingControlledByContent
                                >
                                    <div className={ProductMenuStyle}>
                                        {activeGroup.products.map(p => {
                                            const pKey = usageProductKey(p.category);
                                            const selected = selectedProductKeys.includes(pKey);
                                            return <div
                                                key={pKey}
                                                className="prod-option"
                                                role="menuitemcheckbox"
                                                aria-checked={selected}
                                                onClick={() => handleToggleProduct(pKey)}
                                            >
                                                <ProviderLogo providerId={p.category.provider} size={16} />
                                                <div className="prod-option-label">
                                                    <span>{productCategoryDisplayName(p.category)}</span>
                                                    <span className="prod-option-sub">
                                                        {getShortProviderTitle(p.category.provider)}
                                                    </span>
                                                </div>
                                                <Toggle
                                                    height={18}
                                                    activeColor="primaryMain"
                                                    checked={selected}
                                                    onChange={() => handleToggleProduct(pKey)}
                                                    ariaLabel={productCategoryDisplayName(p.category)}
                                                />
                                            </div>;
                                        })}
                                    </div>
                                </ClickableDropdown>
                            ) : null}
                        </Flex>
                    </div>

                    {detailLoading && !detail ? (
                        <HexSpin />
                    ) : detailError ? (
                        <div>{detailError}</div>
                    ) : detail && !detail.hasData ? (
                        <div>No usage data for this period.</div>
                    ) : renderFocusContent()}
                </>}
            </Flex>
        </div>}
    />;
};

export default UsagePage;
