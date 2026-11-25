import * as React from "react";
import {useCallback, useLayoutEffect, useMemo, useState} from "react";
import {Button, Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import Warning from "@/ui-components/Warning";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import type {ProjectInfo} from "@/Project/InfoCache";
import {projectInfoPi, projectInfoTitle, useProjectInfos} from "@/Project/InfoCache";
import {AccountingModel, FrontendAccountingUnit} from "@/Accounting/index";
import {exportUsage} from "@/Accounting/Usage";
import {dialogStore} from "@/Dialog/DialogStore";
import type {ExportHeader} from "@/Accounting/Usage";
import {Search} from "@/Accounting/Allocations/Controls";
import {Amount, WorkspaceLabel} from "@/Accounting/Allocations/Table";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {looksLikeUUID, shortUUID} from "@/UtilityFunctions";
import {injectStyle} from "@/Unstyled";
import {
    USAGE_DROP_CRITICAL_DAYS,
    UsageChildRow,
    UsageHistogramBucket,
    usageBuildChildHistogramBuckets,
    usageChildScopeMetrics,
    usageDaysUntil,
    usageDropRiskDays,
    usageFormatShortDate,
    usageFormatValue,
} from "./Api";
import {
    UsageExpirationScheduleChart,
    UsageSubProjectScatterChart,
    UsageUtilizationHistogram,
    buildUsageExpiryMonthBuckets,
    openUsageChartDialog,
} from "./Charts";

const TableWrapperStyle = injectStyle("usage-v2-subtable", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 10px;
    }

    ${k} .st-toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px;
    }

    ${k} .st-search {
        flex: 1 1 280px;
        max-width: 380px;
    }

    ${k} .st-search .table-filter {
        width: 100%;
    }

    ${k} .st-actions {
        margin-left: auto;
        display: flex;
        align-items: center;
        gap: 12px;
    }

    ${k} .st-result-count {
        font-size: 12px;
        color: var(--textSecondary);
    }

    ${k} .st-table-box {
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    ${k} .st-table-box .streamed-table-frame {
        min-height: 0;
    }

    ${k} .st-num {
        text-align: right;
        font-variant-numeric: tabular-nums;
        display: block;
    }

    ${k} .st-warn {
        display: inline-flex;
        align-items: center;
        justify-content: flex-end;
        gap: 4px;
        width: 100%;
        font-variant-numeric: tabular-nums;
    }

    ${k} .st-charts-grid {
        display: grid;
        gap: 24px;
        align-items: start;
        padding: 0 0 20px;
        margin-bottom: 10px;
    }

    ${k} .st-section-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    ${k} .st-section-head h3 {
        margin: 0;
        font-size: 15px;
    }

    ${k} .st-chart-col {
        display: flex;
        flex-direction: column;
        gap: 6px;
        min-width: 0;
    }

    ${k} .st-chart-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    ${k} .st-chart-head h4 {
        margin: 0;
        font-size: 13.5px;
        font-weight: 600;
        color: var(--textPrimary);
    }
`);

function formatChildDisplay(
    row: UsageChildRow,
    projectInfo: Record<string, ProjectInfo | null>,
): {title: string; pi: string} {
    if (row.isLocal) {
        return {title: "Local (this project)", pi: ""};
    }
    if (looksLikeUUID(row.child)) {
        const info = projectInfo[row.child];
        return {
            title: projectInfoTitle(info) ?? shortUUID(row.child),
            pi: projectInfoPi(info) ?? "",
        };
    }
    return {title: row.child, pi: ""};
}

export function UsageSubProjectTable(props: {
    rows: UsageChildRow[];
    utilizationBuckets: UsageHistogramBucket[];
    accountingModel: AccountingModel;
    unit: FrontendAccountingUnit;
    includeRetired: boolean;
    hideLocal: boolean;
    periodEnd: number;
    chartWidth: number;
    bucketFilter: string | null;
    onSelectBucket: (key: string | null) => void;
    pinnedChildren: string[];
    onTogglePin: (child: string) => void;
    onClearFilters: () => void;
    exportTitle: string;
    workspaceTitle: string;
    beforeSubProjects?: React.ReactNode;
}): React.ReactNode {
    const store = useMemo(() => new UcxTableStore(), []);
    const [query, setQuery] = useState("");
    const [onlyCapacityDropRisk, setOnlyCapacityDropRisk] = useState(false);
    const [expiryMonthFilter, setExpiryMonthFilter] = useState<string | null>(null);
    const isCapacity = props.accountingModel === "CAPACITY";
    const hasSubAllocators = useMemo(() => props.rows.some(r => r.isSubAllocator), [props.rows]);
    const pinnedSet = useMemo(() => new Set(props.pinnedChildren), [props.pinnedChildren]);

    const childProjectIds = useMemo(() => {
        const ids: string[] = [];
        for (const r of props.rows) {
            if (!r.isLocal && looksLikeUUID(r.child)) {
                ids.push(r.child);
            }
        }
        return ids;
    }, [props.rows]);

    const projectInfo = useProjectInfos(childProjectIds).data;

    const labelForChild = useCallback(
        (row: UsageChildRow) => formatChildDisplay(row, projectInfo),
        [projectInfo],
    );

    const {buckets: expiryBuckets, rowMonthKeys} = useMemo(
        () => buildUsageExpiryMonthBuckets(props.rows, props.accountingModel, props.periodEnd),
        [props.rows, props.accountingModel, props.periodEnd],
    );

    const activeExpiryMonth = expiryMonthFilter && expiryBuckets.some(b => b.key === expiryMonthFilter)
        ? expiryMonthFilter
        : null;

    const capacityDropRows = useMemo(() => {
        return props.rows.filter(
            row => !row.isLocal && usageDropRiskDays(row.nextQuotaDrop, props.accountingModel, props.periodEnd) !== null,
        );
    }, [props.accountingModel, props.periodEnd, props.rows]);

    const earliestCapacityDrop = useMemo(() => {
        let earliest: UsageChildRow | null = null;
        for (const row of capacityDropRows) {
            if (!row.nextQuotaDrop) continue;
            if (!earliest || row.nextQuotaDrop.timestamp < earliest.nextQuotaDrop!.timestamp) {
                earliest = row;
            }
        }
        return earliest;
    }, [capacityDropRows]);

    const scopedHistogramBuckets = useMemo(() => {
        if (isCapacity || !props.includeRetired) return props.utilizationBuckets;
        return usageBuildChildHistogramBuckets(props.rows, props.accountingModel, true);
    }, [isCapacity, props.includeRetired, props.utilizationBuckets, props.rows, props.accountingModel]);

    const filteredRows = useMemo(() => {
        const needle = query.trim().toLowerCase();
        const filterDropRisk = onlyCapacityDropRisk && capacityDropRows.length > 0;
        return props.rows.filter(row => {
            if (row.isLocal && props.hideLocal) return false;
            if (filterDropRisk) {
                if (row.isLocal || usageDropRiskDays(row.nextQuotaDrop, props.accountingModel, props.periodEnd) === null) return false;
            }
            if (activeExpiryMonth !== null) {
                if (row.isLocal || rowMonthKeys.get(row.child) !== activeExpiryMonth) return false;
            }
            if (!row.isLocal) {
                if (props.bucketFilter !== null) {
                    const metrics = usageChildScopeMetrics(row, props.accountingModel, props.includeRetired);
                    if (metrics.utilizationBucket !== props.bucketFilter) {
                        return false;
                    }
                }
            } else if (props.bucketFilter !== null) {
                return false;
            }
            if (needle === "") return true;
            const info = formatChildDisplay(row, projectInfo);
            return info.title.toLowerCase().includes(needle) ||
                info.pi.toLowerCase().includes(needle) ||
                row.child.toLowerCase().includes(needle);
        });
    }, [props.rows, props.hideLocal, props.accountingModel, props.periodEnd, props.includeRetired, onlyCapacityDropRisk, capacityDropRows.length, activeExpiryMonth, rowMonthKeys, props.bucketFilter, query, projectInfo]);

    const columns = useMemo((): TableColumn[] => {
        const useLifetime = !isCapacity && props.includeRetired;
        const cols: TableColumn[] = [
            {
                key: "child.pin",
                label: "",
                jsonPath: "pin",
                sortType: TableColumnSortType.Text,
                alignment: "center",
            },
            {
                key: "child.workspace",
                label: "Workspace",
                jsonPath: "workspace",
                sortType: TableColumnSortType.Text,
                alignment: "left",
            },
        ];
        if (!isCapacity) {
            cols.push({
                key: "child.period",
                label: "Period usage",
                jsonPath: "period",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            });
        }
        cols.push({
            key: "child.activeUsage",
            label: useLifetime ? "Lifetime usage" : "Active usage",
            jsonPath: "activeUsage",
            sortType: TableColumnSortType.Number,
            alignment: "right",
        });
        if (isCapacity) {
            cols.push({
                key: "child.peak",
                label: "Period peak",
                jsonPath: "peak",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            });
        }
        cols.push(
            {
                key: "child.activeQuota",
                label: useLifetime ? "Lifetime quota" : "Active quota",
                jsonPath: "activeQuota",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
            {
                key: "child.utilization",
                label: "Utilization",
                jsonPath: "utilization",
                sortType: TableColumnSortType.Number,
                alignment: "right",
            },
        );
        if (hasSubAllocators) {
            cols.push(
                {
                    key: "child.localUsage",
                    label: "Local usage",
                    jsonPath: "localUsage",
                    sortType: TableColumnSortType.Number,
                    alignment: "right",
                },
                {
                    key: "child.subUsage",
                    label: "Sub-children usage",
                    jsonPath: "subUsage",
                    sortType: TableColumnSortType.Number,
                    alignment: "right",
                },
            );
        }
        cols.push({
            key: "child.expiry",
            label: "Next expiry",
            jsonPath: "expiry",
            sortType: TableColumnSortType.Number,
            alignment: "right",
        });
        return cols;
    }, [isCapacity, props.includeRetired, hasSubAllocators]);

    const columnWidths = useMemo((): Record<string, number> => ({
        "child.pin": 40,
        "child.workspace": 250,
        "child.period": 124,
        "child.activeUsage": 128,
        "child.peak": 124,
        "child.activeQuota": 128,
        "child.utilization": 90,
        "child.localUsage": 124,
        "child.subUsage": 138,
        "child.expiry": 146,
    }), []);

    const rowByKey = useMemo(() => {
        const map = new Map<string, UsageChildRow>();
        for (const r of filteredRows) {
            map.set(r.isLocal ? "__local__" : r.child, r);
        }
        return map;
    }, [filteredRows]);

    const tableRows = useMemo((): TableRow[] => filteredRows.map(row => {
        const key = row.isLocal ? "__local__" : row.child;
        const info = formatChildDisplay(row, projectInfo);
        const metrics = usageChildScopeMetrics(row, props.accountingModel, props.includeRetired);
        const cells: string[] = [
            pinnedSet.has(row.child) ? "1" : "0",
            info.title,
        ];
        if (!isCapacity) {
            cells.push(`${row.periodUsage}`);
        }
        cells.push(`${metrics.usage}`);
        if (isCapacity) {
            cells.push(`${row.peakUsage}`);
        }
        cells.push(
            metrics.quotaKnown ? `${metrics.quota}` : "",
            metrics.quotaKnown ? `${metrics.utilizationPercent.toFixed(1)}` : "",
        );
        if (hasSubAllocators) {
            const localVal = row.isSubAllocator && row.subAllocator ? row.subAllocator.directUsage : row.activeUsage;
            const subVal = row.isSubAllocator && row.subAllocator ? row.subAllocator.downstreamUsage : -1;
            cells.push(
                `${localVal}`,
                subVal >= 0 ? `${subVal}` : "",
            );
        }
        const expiryTarget = row.nextQuotaDrop?.timestamp ?? row.nextExpiration ?? Number.MAX_SAFE_INTEGER;
        cells.push(`${expiryTarget}`);
        return {key, group: "", cells};
    }), [filteredRows, projectInfo, pinnedSet, isCapacity, props.accountingModel, props.includeRetired, hasSubAllocators]);

    useLayoutEffect(() => {
        store.apply({
            tableId: "usage-v2-children",
            revision: 0,
            snapshot: true,
            columns,
            upserts: tableRows,
            removed: [],
        });
    }, [store, columns, tableRows]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const row = rowByKey.get(context.row.key);
        if (!row) return context.cell;

        if (context.column.key === "child.pin") {
            const pinned = pinnedSet.has(row.child);
            const canPinMore = pinned || props.pinnedChildren.length < 5;
            return <TooltipV2 tooltip={pinned ? "Remove from chart" : (canPinMore ? "Show on chart" : "Maximum 5 pinned")}>
                <button
                    type="button"
                    className={UcxTableIconButtonClass}
                    disabled={!canPinMore}
                    aria-pressed={pinned}
                    aria-label={pinned ? "Remove from chart" : "Show on chart"}
                    onClick={ev => {
                        ev.stopPropagation();
                        props.onTogglePin(row.child);
                    }}
                    onKeyDown={ev => {
                        if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                    }}
                >
                    <Icon
                        name={pinned ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"}
                        size={16}
                    />
                </button>
            </TooltipV2>;
        }

        if (context.column.key === "child.workspace") {
            const info = formatChildDisplay(row, projectInfo);
            const meta = info.pi ? `PI ${info.pi}` : undefined;
            return <WorkspaceLabel title={info.title} meta={meta} metaTooltip={info.pi || undefined} />;
        }

        if (context.column.key === "child.period") {
            return <Amount
                value={row.periodUsage * props.unit.balanceFactor}
                unit={props.unit.name}
                title="Usage recorded during the selected period."
            />;
        }
        if (context.column.key === "child.activeUsage") {
            const useLifetime = !isCapacity && props.includeRetired;
            if (useLifetime) {
                const tooltip = `Active: ${usageFormatValue(props.unit, row.activeUsage)}, Retired: ${usageFormatValue(props.unit, row.retiredUsage)}.`;
                return <Amount
                    value={row.totalUsage * props.unit.balanceFactor}
                    unit={props.unit.name}
                    title={tooltip}
                />;
            }
            const isOver = isCapacity && row.utilizationBucket === "over";
            return <Amount
                value={row.activeUsage * props.unit.balanceFactor}
                unit={props.unit.name}
                negative={isOver}
                title={isCapacity ? "Current capacity in use." : "Usage charged against active quota."}
            />;
        }
        if (context.column.key === "child.peak") {
            return <Amount
                value={row.peakUsage * props.unit.balanceFactor}
                unit={props.unit.name}
                title="Peak capacity in use during the selected period."
            />;
        }
        if (context.column.key === "child.activeQuota") {
            if (row.isLocal) return <span className="st-num">—</span>;
            const metrics = usageChildScopeMetrics(row, props.accountingModel, props.includeRetired);
            if (!metrics.quotaKnown) {
                return <TooltipV2 tooltip="Quota was not recorded for this historical period.">
                    <span className="st-num" style={{color: "var(--textSecondary)"}}>—</span>
                </TooltipV2>;
            }
            const useLifetime = !isCapacity && props.includeRetired;
            if (useLifetime) {
                const expiredQuota = Math.max(0, metrics.quota - row.activeQuota);
                const expiredUnused = Math.max(0, expiredQuota - row.retiredUsage);
                const tooltip = `Active: ${usageFormatValue(props.unit, row.activeQuota)}, Expired: ${usageFormatValue(props.unit, expiredQuota)} (unspent: ${usageFormatValue(props.unit, expiredUnused)}).`;
                return <Amount
                    value={metrics.quota * props.unit.balanceFactor}
                    unit={props.unit.name}
                    title={tooltip}
                />;
            }
            return <Amount
                value={row.activeQuota * props.unit.balanceFactor}
                unit={props.unit.name}
                title="Quota from allocations that are active now."
            />;
        }
        if (context.column.key === "child.utilization") {
            if (row.isLocal) return <span className="st-num">—</span>;
            const metrics = usageChildScopeMetrics(row, props.accountingModel, props.includeRetired);
            if (!metrics.quotaKnown) {
                return <TooltipV2 tooltip="Quota was not recorded for this historical period.">
                    <span className="st-num" style={{color: "var(--textSecondary)"}}>—</span>
                </TooltipV2>;
            }
            const isOver = metrics.utilizationBucket === "over";
            return <span className="st-num" style={{color: isOver ? "var(--errorMain)" : undefined, fontWeight: isOver ? 600 : undefined}}>
                {metrics.quota === 0 && metrics.usage > 0 ? "Over" : `${metrics.utilizationPercent.toFixed(1)}%`}
            </span>;
        }
        if (context.column.key === "child.localUsage") {
            const localVal = row.isSubAllocator && row.subAllocator ? row.subAllocator.directUsage : row.activeUsage;
            return <Amount
                value={localVal * props.unit.balanceFactor}
                unit={props.unit.name}
                title="Active usage consumed directly within this workspace."
            />;
        }
        if (context.column.key === "child.subUsage") {
            if (!row.isSubAllocator || !row.subAllocator) {
                return null;
            }
            const sub = row.subAllocator;
            const tooltip = `Used by sub-children. Re-allocated: ${usageFormatValue(props.unit, sub.downstreamActiveAllocated)} (${sub.reallocatedPercent.toFixed(0)}% of active quota).`;
            return <Amount
                value={sub.downstreamUsage * props.unit.balanceFactor}
                unit={props.unit.name}
                title={tooltip}
            />;
        }
        if (context.column.key === "child.expiry") {
            const drop = row.nextQuotaDrop;
            const riskDays = usageDropRiskDays(drop, props.accountingModel, props.periodEnd);
            if (drop && riskDays !== null) {
                const isCritical = riskDays < USAGE_DROP_CRITICAL_DAYS;
                const label = isCapacity ? `Over quota in ${riskDays}d` : `No quota in ${riskDays}d`;
                const tooltip = isCapacity
                    ? `On ${usageFormatShortDate(drop.timestamp)}, active quota drops from ${usageFormatValue(props.unit, drop.quotaBefore)} to ${usageFormatValue(props.unit, drop.quotaAfter)}, below current usage of ${usageFormatValue(props.unit, row.activeUsage)}.`
                    : `On ${usageFormatShortDate(drop.timestamp)}, active quota of ${usageFormatValue(props.unit, drop.quotaBefore)} expires with no renewal in place.`;
                return <TooltipV2 tooltip={tooltip}>
                    <span
                        className="st-warn"
                        style={{color: isCritical ? "var(--errorMain)" : "var(--warningMain)"}}
                    >
                        <Icon name="heroExclamationTriangle" size={14} color={isCritical ? "errorMain" : "warningMain"} />
                        <span>{label}</span>
                    </span>
                </TooltipV2>;
            }
            const target = drop?.timestamp ?? row.nextExpiration;
            if (target == null) return <span className="st-num" style={{color: "var(--textSecondary)"}}>—</span>;
            const days = usageDaysUntil(target, props.periodEnd);
            return <TooltipV2 tooltip={usageFormatShortDate(target)}>
                <span className="st-num">{days}d</span>
            </TooltipV2>;
        }
        return context.cell;
    }, [rowByKey, pinnedSet, props.pinnedChildren.length, props.onTogglePin, projectInfo, props.unit, props.periodEnd, props.accountingModel, props.includeRetired, isCapacity]);

    const onExport = useCallback(() => {
        const data = filteredRows.map(row => {
            const info = formatChildDisplay(row, projectInfo);
            const localVal = row.isSubAllocator && row.subAllocator ? row.subAllocator.directUsage : row.activeUsage;
            return {
                workspace: info.title,
                pi: info.pi,
                id: row.isLocal ? "local" : row.child,
                periodUsage: row.periodUsage * props.unit.balanceFactor,
                activeUsage: row.activeUsage * props.unit.balanceFactor,
                lifetimeUsage: row.totalUsage * props.unit.balanceFactor,
                activeQuota: row.activeQuota * props.unit.balanceFactor,
                retiredUsage: row.retiredUsage * props.unit.balanceFactor,
                utilizationPercent: Number(row.utilizationPercent.toFixed(1)),
                localUsage: localVal * props.unit.balanceFactor,
                subChildrenUsage: (row.subAllocator?.downstreamUsage ?? 0) * props.unit.balanceFactor,
                reallocatedQuota: (row.subAllocator?.downstreamActiveAllocated ?? 0) * props.unit.balanceFactor,
            };
        });

        const headers: ExportHeader<(typeof data)[number]>[] = [
            {key: "workspace", value: "Workspace", defaultChecked: true},
            {key: "pi", value: "PI", defaultChecked: true},
            {key: "id", value: "ID", defaultChecked: true},
            {key: "periodUsage", value: isCapacity ? `Period peak (${props.unit.name})` : `Period usage (${props.unit.name})`, defaultChecked: true},
            {key: "activeUsage", value: `Active usage (${props.unit.name})`, defaultChecked: true},
            {key: "activeQuota", value: `Active quota (${props.unit.name})`, defaultChecked: true},
            {key: "retiredUsage", value: `Retired usage (${props.unit.name})`, defaultChecked: true},
            {key: "utilizationPercent", value: "Utilization (%)", defaultChecked: true},
            {key: "localUsage", value: `Local usage (${props.unit.name})`, defaultChecked: true},
            {key: "subChildrenUsage", value: `Sub-children usage (${props.unit.name})`, defaultChecked: true},
            {key: "reallocatedQuota", value: `Re-allocated quota (${props.unit.name})`, defaultChecked: true},
        ];
        if (props.includeRetired && !isCapacity) {
            headers.splice(5, 0, {key: "lifetimeUsage", value: `Lifetime usage (${props.unit.name})`, defaultChecked: true});
        }

        exportUsage(
            data,
            headers,
            props.workspaceTitle,
            {
                fileName: `usage-subprojects-${props.exportTitle.toLowerCase().replace(/\s+/g, "-")}`,
            },
        );
    }, [filteredRows, projectInfo, props.unit, isCapacity, props.includeRetired, props.workspaceTitle, props.exportTitle]);

    const hasActiveFilters = query.trim() !== ""
        || props.bucketFilter !== null
        || (onlyCapacityDropRisk && capacityDropRows.length > 0)
        || activeExpiryMonth !== null;
    const tableHeight = 480;

    const hasScatterPoints = useMemo(
        () => props.rows.some(r => {
            if (r.isLocal) return false;
            const m = usageChildScopeMetrics(r, props.accountingModel, props.includeRetired);
            return m.quotaKnown && (m.quota > 0 || m.usage > 0);
        }),
        [props.rows, props.accountingModel, props.includeRetired],
    );
    const hasExpiryChart = expiryBuckets.length > 0;
    const bothSubCharts = hasScatterPoints && hasExpiryChart;
    const twoColSubCharts = bothSubCharts && props.chartWidth >= 920;
    const scatterWidth = twoColSubCharts ? Math.floor((props.chartWidth - 24) / 2) : props.chartWidth;
    const expiryWidth = twoColSubCharts ? Math.floor((props.chartWidth - 24) / 2) : props.chartWidth;
    const scatterHeight = 225;
    const expiryHeight = 225;

    return <div className={TableWrapperStyle}>
        {hasScatterPoints || hasExpiryChart ? (
            <div
                className="st-charts-grid"
                style={{gridTemplateColumns: twoColSubCharts ? "repeat(2, minmax(0, 1fr))" : "minmax(0, 1fr)"}}
            >
                {hasScatterPoints ? (
                    <div className="st-chart-col">
                        <div className="st-chart-head">
                            <h4>Quota vs. utilization</h4>
                            <TooltipV2 tooltip="Expand chart">
                                <button
                                    type="button"
                                    className={UcxTableIconButtonClass}
                                    aria-label="Expand quota vs utilization chart"
                                    onClick={() => openUsageChartDialog("Quota vs. utilization", (w, h) => (
                                        <UsageSubProjectScatterChart
                                            rows={props.rows}
                                            accountingModel={props.accountingModel}
                                            includeRetired={props.includeRetired}
                                            unit={props.unit}
                                            periodEnd={props.periodEnd}
                                            width={w}
                                            height={h}
                                            pinnedChildren={props.pinnedChildren}
                                            onTogglePin={props.onTogglePin}
                                            labelForChild={labelForChild}
                                        />
                                    ))}
                                >
                                    <Icon name="heroArrowsPointingOut" size={15} />
                                </button>
                            </TooltipV2>
                        </div>
                        <UsageSubProjectScatterChart
                            rows={props.rows}
                            accountingModel={props.accountingModel}
                            includeRetired={props.includeRetired}
                            unit={props.unit}
                            periodEnd={props.periodEnd}
                            width={scatterWidth}
                            height={scatterHeight}
                            pinnedChildren={props.pinnedChildren}
                            onTogglePin={props.onTogglePin}
                            labelForChild={labelForChild}
                        />
                    </div>
                ) : null}

                {hasExpiryChart ? (
                    <div className="st-chart-col">
                        <div className="st-chart-head">
                            <h4>Upcoming expirations</h4>
                            <TooltipV2 tooltip="Expand chart">
                                <button
                                    type="button"
                                    className={UcxTableIconButtonClass}
                                    aria-label="Expand upcoming expirations chart"
                                    onClick={() => openUsageChartDialog("Upcoming expirations", (w, h) => (
                                        <UsageExpirationScheduleChart
                                            buckets={expiryBuckets}
                                            accountingModel={props.accountingModel}
                                            unit={props.unit}
                                            width={w}
                                            height={h}
                                            selectedMonth={activeExpiryMonth}
                                            onSelectMonth={monthKey => {
                                                setExpiryMonthFilter(monthKey);
                                                dialogStore.success();
                                            }}
                                        />
                                    ))}
                                >
                                    <Icon name="heroArrowsPointingOut" size={15} />
                                </button>
                            </TooltipV2>
                        </div>
                        <UsageExpirationScheduleChart
                            buckets={expiryBuckets}
                            accountingModel={props.accountingModel}
                            unit={props.unit}
                            width={expiryWidth}
                            height={expiryHeight}
                            selectedMonth={activeExpiryMonth}
                            onSelectMonth={setExpiryMonthFilter}
                        />
                    </div>
                ) : null}
            </div>
        ) : null}

        {props.beforeSubProjects}

        <div className="st-section-head">
            <h3>Sub-projects</h3>
        </div>

        <UsageUtilizationHistogram
            buckets={scopedHistogramBuckets}
            selectedBucket={props.bucketFilter}
            onSelectBucket={props.onSelectBucket}
        />

        {capacityDropRows.length > 0 && earliestCapacityDrop?.nextQuotaDrop ? (
            <Warning
                mb="0px"
                centered
                trailing={
                    <Button
                        onClick={() => setOnlyCapacityDropRisk(prev => !prev)}
                    >
                        {onlyCapacityDropRisk ? "Show all" : "Show affected"}
                    </Button>
                }
            >
                {isCapacity ? (
                    capacityDropRows.length === 1 ? <>
                        <b>Upcoming quota drop:</b>{" "}
                        <b>{formatChildDisplay(earliestCapacityDrop, projectInfo).title}</b> will be over quota and lose write access in{" "}
                        <b>{usageDaysUntil(earliestCapacityDrop.nextQuotaDrop.timestamp, props.periodEnd)} days</b>{" "}
                        ({usageFormatShortDate(earliestCapacityDrop.nextQuotaDrop.timestamp)}) unless renewed or reduced.
                    </> : <>
                        <b>Upcoming quota drop:</b>{" "}
                        <b>{formatNumber(capacityDropRows.length)} sub-projects</b> will be over quota and lose write access within 90 days
                        (earliest in <b>{usageDaysUntil(earliestCapacityDrop.nextQuotaDrop.timestamp, props.periodEnd)} days</b>,{" "}
                        {usageFormatShortDate(earliestCapacityDrop.nextQuotaDrop.timestamp)}).
                    </>
                ) : (
                    capacityDropRows.length === 1 ? <>
                        <b>Upcoming quota drop:</b>{" "}
                        <b>{formatChildDisplay(earliestCapacityDrop, projectInfo).title}</b> will run out of active quota in{" "}
                        <b>{usageDaysUntil(earliestCapacityDrop.nextQuotaDrop.timestamp, props.periodEnd)} days</b>{" "}
                        ({usageFormatShortDate(earliestCapacityDrop.nextQuotaDrop.timestamp)}) with no renewal in place.
                    </> : <>
                        <b>Upcoming quota drop:</b>{" "}
                        <b>{formatNumber(capacityDropRows.length)} sub-projects</b> will run out of active quota within 90 days with no renewal in place
                        (earliest in <b>{usageDaysUntil(earliestCapacityDrop.nextQuotaDrop.timestamp, props.periodEnd)} days</b>,{" "}
                        {usageFormatShortDate(earliestCapacityDrop.nextQuotaDrop.timestamp)}).
                    </>
                )}
            </Warning>
        ) : null}

        <div className="st-toolbar">
            <Search
                className="st-search"
                height={35}
                query={query}
                onQueryChange={setQuery}
                placeholder="Search workspace or PI…"
                ariaLabel="Search workspaces"
            />
            {hasActiveFilters ? (
                <Button
                    color="ghost"
                    onClick={() => {
                        setQuery("");
                        setOnlyCapacityDropRisk(false);
                        setExpiryMonthFilter(null);
                        props.onClearFilters();
                    }}
                >
                    <Icon name="heroXMark" size={13} />
                    Clear filter
                </Button>
            ) : null}
            <div className="st-actions">
                <span className="st-result-count">
                    {formatNumber(filteredRows.length)} workspace{filteredRows.length === 1 ? "" : "s"}
                </span>
                <Button height={35} onClick={onExport}>
                    <Icon name="heroArrowDownTray" mr={8} />
                    Export
                </Button>
            </div>
        </div>
        <div className="st-table-box" style={{height: tableHeight}}>
            <UcxStreamedTable
                tableId="usage-v2-children"
                store={store}
                preserveRowOrder
                showControls={false}
                density="compact"
                rowHeight={46}
                columnWidths={columnWidths}
                renderCell={renderCell}
                onRowActivated={() => void 0}
                emptyMessage="No matching workspaces in this period."
            />
        </div>
    </div>;
}
