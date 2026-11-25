import * as React from "react";
import {useCallback, useLayoutEffect, useMemo, useState} from "react";
import {Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {productCategoryDisplayName, productTypeToIcon} from "@/Accounting/index";
import {Amount} from "@/Accounting/Allocations/Table";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableActivationEvent, UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {injectStyle} from "@/Unstyled";
import {
    USAGE_DROP_CRITICAL_DAYS,
    UsageProductSummary,
    UsageUnitGroup,
    usageDaysUntil,
    usageDropRiskDays,
    usageExplainUnit,
    usageFormatShortDate,
    usageFormatValue,
    usageProductKey,
} from "./Api";
import {UsageSparkline} from "./Charts";

type OverviewRowModel =
    | {
        kind: "group";
        key: string;
        group: UsageUnitGroup;
    }
    | {
        kind: "product";
        key: string;
        group: UsageUnitGroup;
        product: UsageProductSummary;
        productKey: string;
        sharePercent: number;
    };

function productShareInGroup(
    group: UsageUnitGroup,
    product: UsageProductSummary,
    includeRetired: boolean,
): number {
    if (group.accountingModel === "CAPACITY") {
        if (group.activeUsage > 0) return (product.activeUsage / group.activeUsage) * 100;
        if (group.activeQuota > 0) return (product.activeQuota / group.activeQuota) * 100;
        return 0;
    }
    if (includeRetired) {
        if (group.totalUsage > 0) return (product.totalUsage / group.totalUsage) * 100;
        if (group.quota > 0) return (product.quota / group.quota) * 100;
        return 0;
    }
    if (group.periodUsage > 0) return (product.periodUsage / group.periodUsage) * 100;
    if (group.activeUsage > 0) return (product.activeUsage / group.activeUsage) * 100;
    return 0;
}

const OverviewStyle = injectStyle("usage-v2-overview", k => `
    ${k} {
        padding-bottom: 14px;
    }

    ${k} .ov-heading {
        display: flex;
        align-items: center;
        gap: 8px;
        width: 100%;
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        text-align: left;
    }

    ${k} .ov-heading h3 {
        margin: 0;
        font-size: 15px;
    }

    ${k} .ov-chevron {
        transition: transform 120ms ease;
    }

    ${k} .ov-heading[aria-expanded="false"] .ov-chevron {
        transform: rotate(-90deg);
    }

    ${k} .ov-table {
        display: flex;
        flex-direction: column;
        margin-top: 10px;
        min-height: 0;
    }

    ${k} .ov-table .streamed-table-frame {
        min-height: 0;
    }

    ${k} .ov-name {
        display: flex;
        align-items: center;
        gap: 7px;
        min-width: 0;
        width: 100%;
    }

    ${k} .ov-label {
        flex: 1;
        min-width: 0;
    }

    ${k} .ov-indent {
        width: 18px;
        flex-shrink: 0;
    }

    ${k} .ov-expand {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 18px;
        height: 20px;
        padding: 0;
        border: 0;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        flex-shrink: 0;
    }

    ${k} .ov-expand:hover {
        color: var(--textPrimary);
    }

    ${k} .ov-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 500;
    }

    ${k} .ov-group-title {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 600;
    }

    ${k} .ov-sub {
        font-size: 11px;
        color: var(--textSecondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .ov-num {
        display: block;
        text-align: right;
        font-variant-numeric: tabular-nums;
    }

    ${k} .ov-trend-cell {
        display: flex;
        align-items: center;
        width: 100%;
        height: 28px;
    }

    ${k} .ov-warn {
        display: inline-flex;
        align-items: center;
        justify-content: flex-end;
        gap: 4px;
        width: 100%;
        font-variant-numeric: tabular-nums;
    }
`);

const overviewWidths: Record<string, number> = {
    "ov.name": 270,
    "ov.trend": 116,
    "ov.period": 134,
    "ov.activeUsage": 128,
    "ov.activeQuota": 128,
    "ov.utilization": 88,
    "ov.share": 104,
    "ov.expiry": 146,
};

export function UsagePortfolioOverview(props: {
    unitGroups: UsageUnitGroup[];
    selectedUnitKey: string | null;
    selectedProductKeys: string[];
    includeRetired: boolean;
    periodEnd: number;
    onSelectUnit: (unitKey: string) => void;
    onToggleProduct: (unitKey: string, productKey: string) => void;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const [collapsed, setCollapsed] = useState(false);
    const [collapsedUnits, setCollapsedUnits] = useState<Record<string, boolean>>({});

    const columns = useMemo((): TableColumn[] => {
        return [
            {
                key: "ov.name",
                label: "Product",
                jsonPath: "name",
                sortType: TableColumnSortType.Text,
                alignment: "left",
            },
            {
                key: "ov.trend",
                label: "Trend",
                jsonPath: "trend",
                sortType: TableColumnSortType.Text,
                alignment: "left",
            },
            {
                key: "ov.period",
                label: "Period peak / usage",
                jsonPath: "period",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "ov.activeUsage",
                label: "Usage",
                jsonPath: "activeUsage",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "ov.activeQuota",
                label: "Quota",
                jsonPath: "activeQuota",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "ov.utilization",
                label: "Utilization",
                jsonPath: "utilization",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "ov.share",
                label: "Share in group",
                jsonPath: "share",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "ov.expiry",
                label: "Next expiry",
                jsonPath: "expiry",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
        ];
    }, []);

    const models = useMemo((): OverviewRowModel[] => {
        const result: OverviewRowModel[] = [];
        for (const group of props.unitGroups) {
            result.push({
                kind: "group",
                key: `group:${group.key}`,
                group,
            });
            if (!collapsedUnits[group.key]) {
                for (const product of group.products) {
                    const productKey = usageProductKey(product.category);
                    result.push({
                        kind: "product",
                        key: `product:${group.key}:${productKey}`,
                        group,
                        product,
                        productKey,
                        sharePercent: productShareInGroup(group, product, props.includeRetired),
                    });
                }
            }
        }
        return result;
    }, [props.unitGroups, collapsedUnits, props.includeRetired]);

    const modelByKey = useMemo(() => new Map(models.map(m => [m.key, m])), [models]);

    const tableRows = useMemo((): TableRow[] => models.map(m => {
        const g = m.group;
        const isCapacity = g.accountingModel === "CAPACITY";
        const useLifetime = !isCapacity && props.includeRetired;
        const source = m.kind === "group" ? g : m.product;
        const title = m.kind === "group" ? g.title : productCategoryDisplayName(m.product.category);
        const periodMetric = isCapacity ? source.peakUsage : source.periodUsage;
        const usageMetric = useLifetime ? source.totalUsage : source.activeUsage;
        const quotaMetric = useLifetime ? source.quota : source.activeQuota;
        const pct = quotaMetric > 0
            ? (usageMetric / quotaMetric) * 100
            : (usageMetric > 0 ? 100 : 0);
        const expiry = source.nextQuotaDrop?.timestamp ?? source.nextMeaningfulExpiration ?? Number.MAX_SAFE_INTEGER;

        const cells: string[] = [
            title,
            "",
            `${periodMetric}`,
            `${usageMetric}`,
            `${quotaMetric}`,
            `${pct.toFixed(1)}`,
            m.kind === "product" ? `${m.sharePercent.toFixed(1)}` : "",
            `${expiry}`,
        ];
        return {
            key: m.key,
            group: "",
            cells,
        };
    }), [models, props.includeRetired]);

    useLayoutEffect(() => {
        store.apply({
            tableId: "usage-v2-overview",
            revision: 0,
            snapshot: true,
            columns,
            upserts: tableRows,
            removed: [],
        });
    }, [store, columns, tableRows]);

    useLayoutEffect(() => {
        if (!props.selectedUnitKey) return;
        const key = props.selectedProductKeys.length === 1
            ? `product:${props.selectedUnitKey}:${props.selectedProductKeys[0]}`
            : `group:${props.selectedUnitKey}`;
        if (modelByKey.has(key)) {
            store.setSelected("usage-v2-overview", key);
        } else {
            store.setSelected("usage-v2-overview", `group:${props.selectedUnitKey}`);
        }
    }, [store, props.selectedUnitKey, props.selectedProductKeys, modelByKey]);

    const onRowActivated = useCallback((event: UcxTableActivationEvent) => {
        const model = modelByKey.get(event.rowKey);
        if (!model) return;
        if (model.kind === "group") {
            setCollapsedUnits(prev => ({...prev, [model.group.key]: !prev[model.group.key]}));
        }
    }, [modelByKey]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const model = modelByKey.get(context.row.key);
        if (!model) return context.cell;

        const group = model.group;
        const unit = usageExplainUnit(group.unitAndFrequency, group.productType);
        const isCapacity = group.accountingModel === "CAPACITY";
        const source = model.kind === "group" ? group : model.product;

        if (context.column.key === "ov.name") {
            if (model.kind === "group") {
                const expanded = !collapsedUnits[group.key];
                const isActiveGroup = props.selectedUnitKey === group.key && props.selectedProductKeys.length === 0;
                const label = isActiveGroup
                    ? `Showing ${group.title}`
                    : (props.selectedUnitKey === group.key && props.selectedProductKeys.length > 0
                        ? `Show all products in ${group.title}`
                        : `Show ${group.title}`);
                return <div className="ov-name">
                    <button
                        type="button"
                        className="ov-expand"
                        aria-label={expanded ? "Collapse products" : "Expand products"}
                        onClick={ev => {
                            ev.stopPropagation();
                            setCollapsedUnits(prev => ({...prev, [group.key]: !prev[group.key]}));
                        }}
                    >
                        <Icon name={expanded ? "heroChevronDown" : "heroChevronRight"} size={14} />
                    </button>
                    <Icon name={productTypeToIcon(group.productType)} size={15} />
                    <div className="ov-label">
                        <div className="ov-group-title">{group.title}</div>
                        <div className="ov-sub">
                            {group.products.length} product{group.products.length === 1 ? "" : "s"}
                        </div>
                    </div>
                    <TooltipV2 tooltip={label}>
                        <button
                            type="button"
                            className={UcxTableIconButtonClass}
                            aria-pressed={isActiveGroup}
                            aria-label={label}
                            disabled={isActiveGroup}
                            onClick={ev => {
                                ev.stopPropagation();
                                store.setSelected("usage-v2-overview", context.row.key);
                                props.onSelectUnit(group.key);
                            }}
                            onKeyDown={ev => {
                                if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                            }}
                        >
                            <Icon name={isActiveGroup ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                        </button>
                    </TooltipV2>
                </div>;
            }
            const isSelectedProduct = props.selectedUnitKey === group.key
                && props.selectedProductKeys.includes(model.productKey);
            const productLabel = isSelectedProduct
                ? `Remove ${productCategoryDisplayName(model.product.category)} from product filter`
                : `Show ${productCategoryDisplayName(model.product.category)}`;
            return <div className="ov-name">
                <span className="ov-indent" />
                <ProviderLogo providerId={model.product.category.provider} size={15} />
                <div className="ov-label">
                    <div className="ov-title">{productCategoryDisplayName(model.product.category)}</div>
                    <div className="ov-sub">{getShortProviderTitle(model.product.category.provider)}</div>
                </div>
                <TooltipV2 tooltip={productLabel}>
                    <button
                        type="button"
                        className={UcxTableIconButtonClass}
                        aria-pressed={isSelectedProduct}
                        aria-label={productLabel}
                        onClick={ev => {
                            ev.stopPropagation();
                            store.setSelected("usage-v2-overview", context.row.key);
                            props.onToggleProduct(group.key, model.productKey);
                        }}
                        onKeyDown={ev => {
                            if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                        }}
                    >
                        <Icon name={isSelectedProduct ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                    </button>
                </TooltipV2>
            </div>;
        }

        if (context.column.key === "ov.trend") {
            return <div className="ov-trend-cell">
                <UsageSparkline points={source.sparkline} accountingModel={group.accountingModel} width={120} height={28} />
            </div>;
        }

        if (context.column.key === "ov.period") {
            if (isCapacity) {
                return <Amount
                    value={source.peakUsage * unit.balanceFactor}
                    unit={unit.name}
                    title="Peak capacity in use during the selected period."
                />;
            }
            return <Amount
                value={source.periodUsage * unit.balanceFactor}
                unit={unit.name}
                title="Usage recorded during the selected period."
            />;
        }

        if (context.column.key === "ov.activeUsage") {
            const useLifetime = !isCapacity && props.includeRetired;
            if (useLifetime) {
                const tooltip = `Active: ${usageFormatValue(unit, source.activeUsage)}, Retired: ${usageFormatValue(unit, source.retiredUsage)}.`;
                return <Amount
                    value={source.totalUsage * unit.balanceFactor}
                    unit={unit.name}
                    title={tooltip}
                />;
            }
            const isExcess = isCapacity && source.activeUsage > source.activeQuota && source.activeUsage > 0;
            return <Amount
                value={source.activeUsage * unit.balanceFactor}
                unit={unit.name}
                negative={isExcess}
                title={isCapacity ? "Current capacity in use." : "Usage charged against active quota."}
            />;
        }

        if (context.column.key === "ov.activeQuota") {
            const useLifetime = !isCapacity && props.includeRetired;
            if (useLifetime) {
                const expiredQuota = Math.max(0, source.quota - source.activeQuota);
                const expiredUnused = Math.max(0, expiredQuota - source.retiredUsage);
                const tooltip = `Active: ${usageFormatValue(unit, source.activeQuota)}, Expired: ${usageFormatValue(unit, expiredQuota)} (unspent: ${usageFormatValue(unit, expiredUnused)}).`;
                return <Amount
                    value={source.quota * unit.balanceFactor}
                    unit={unit.name}
                    title={tooltip}
                />;
            }
            return <Amount
                value={source.activeQuota * unit.balanceFactor}
                unit={unit.name}
                title="Quota from allocations that are active now."
            />;
        }

        if (context.column.key === "ov.utilization") {
            const useLifetime = !isCapacity && props.includeRetired;
            const usageMetric = useLifetime ? source.totalUsage : source.activeUsage;
            const quotaMetric = useLifetime ? source.quota : source.activeQuota;
            const isExcess = isCapacity && source.activeUsage > source.activeQuota && source.activeUsage > 0;
            const pct = quotaMetric > 0
                ? (usageMetric / quotaMetric) * 100
                : (usageMetric > 0 ? 100 : 0);
            const color = isExcess
                ? "var(--errorMain)"
                : pct >= 90
                    ? "var(--warningMain)"
                    : undefined;
            return <span className="ov-num" style={{color, fontWeight: isExcess ? 600 : undefined}}>
                {isExcess && source.activeQuota === 0 ? "Over" : `${pct.toFixed(1)}%`}
            </span>;
        }

        if (context.column.key === "ov.share") {
            if (model.kind === "group") return null;
            const useLifetime = !isCapacity && props.includeRetired;
            const tooltip = isCapacity
                ? `Share of active usage within ${group.title}.`
                : useLifetime
                    ? `Share of lifetime usage within ${group.title}.`
                    : `Share of period usage within ${group.title}.`;
            return <TooltipV2 tooltip={tooltip}>
                <span className="ov-num">{model.sharePercent.toFixed(1)}%</span>
            </TooltipV2>;
        }

        if (context.column.key === "ov.expiry") {
            const drop = source.nextQuotaDrop;
            const riskDays = usageDropRiskDays(drop, group.accountingModel, props.periodEnd);
            if (drop && riskDays !== null) {
                const isCritical = riskDays < USAGE_DROP_CRITICAL_DAYS;
                const label = isCapacity ? `Over quota in ${riskDays}d` : `No quota in ${riskDays}d`;
                const tooltip = isCapacity
                    ? `On ${usageFormatShortDate(drop.timestamp)}, active quota drops from ${usageFormatValue(unit, drop.quotaBefore)} to ${usageFormatValue(unit, drop.quotaAfter)}, below current usage of ${usageFormatValue(unit, source.activeUsage)}.`
                    : `On ${usageFormatShortDate(drop.timestamp)}, active quota of ${usageFormatValue(unit, drop.quotaBefore)} expires with no renewal in place.`;
                return <TooltipV2 tooltip={tooltip}>
                    <span
                        className="ov-warn"
                        style={{color: isCritical ? "var(--errorMain)" : "var(--warningMain)"}}
                    >
                        <Icon name="heroExclamationTriangle" size={14} color={isCritical ? "errorMain" : "warningMain"} />
                        <span>{label}</span>
                    </span>
                </TooltipV2>;
            }
            const target = drop?.timestamp ?? source.nextMeaningfulExpiration;
            if (target == null) return <span className="ov-num" style={{color: "var(--textSecondary)"}}>—</span>;
            const days = usageDaysUntil(target, props.periodEnd);
            return <TooltipV2 tooltip={usageFormatShortDate(target)}>
                <span className="ov-num">{days}d</span>
            </TooltipV2>;
        }

        return context.cell;
    }, [
        modelByKey,
        collapsedUnits,
        props.selectedUnitKey,
        props.selectedProductKeys,
        props.onSelectUnit,
        props.onToggleProduct,
        props.periodEnd,
        props.includeRetired,
        store,
    ]);

    const tableHeight = Math.min(7 * 46 + 42, Math.max(1, models.length) * 46 + 42);

    return <section className={OverviewStyle} aria-label="Resource overview">
        <button
            type="button"
            className="ov-heading"
            aria-expanded={!collapsed}
            onClick={() => setCollapsed(prev => !prev)}
        >
            <Icon name="heroChevronDown" size={14} className="ov-chevron" />
            <h3>Resource overview</h3>
        </button>
        {collapsed ? null : <div className="ov-table" style={{height: tableHeight}}>
            <UcxStreamedTable
                tableId="usage-v2-overview"
                store={store}
                preserveRowOrder
                showControls={false}
                density="compact"
                rowHeight={46}
                columnWidths={overviewWidths}
                renderCell={renderCell}
                onRowActivated={onRowActivated}
                emptyMessage="No usage data available."
            />
        </div>}
    </section>;
}
