import * as React from "react";
import {useCallback, useLayoutEffect, useMemo, useState} from "react";
import {scaleLinear, scaleTime} from "d3-scale";
import {area, curveMonotoneX, line, stack} from "d3-shape";
import {axisBottom, axisLeft} from "d3-axis";
import {pointer, select} from "d3-selection";
import {useD3} from "@/Utilities/d3";
import {Button, Flex, Icon} from "@/ui-components";
import {TooltipV2} from "@/ui-components/Tooltip";
import {UcxStreamedTable, UcxTableIconButtonClass, UcxTableStore} from "@/UCX/UcxBrowser";
import type {UcxTableCellRenderContext} from "@/UCX/UcxBrowser";
import {TableColumnSortType} from "@/UCX/protocol";
import type {TableColumn, TableRow} from "@/UCX/protocol";
import {FrontendAccountingUnit, productDisplayName} from "@/Accounting/index";
import {formatAmount} from "@/Accounting/Allocations/Model";
import {getShortProviderTitle} from "@/Providers/ProviderTitle";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";
import {
    UsageJobApplicationSummary,
    UsageJobBreakdown,
    UsageJobOutcomeCounts,
    UsageJobProductSummary,
    UsageJobTimelineBucket,
    usageFormatPercent,
} from "./Api";
import {
    ChartShellStyle,
    appendUsageSeriesTooltip,
    buildSeriesColorMap,
    buildUsageSeriesTotals,
    createUsageChartTooltip,
    formatBucketTick,
    formatCompactAxisNumber,
    leftMarginForCompactMax,
    UsageChartExpandButton,
    styleAxes,
} from "./Charts";

type JobMetric = "compute" | "jobs" | "queue";
type JobGroupBy = "application" | "product" | "outcome";
type JobTableTab = "applications" | "products";

function countJobOutcomes(outcomes: UsageJobOutcomeCounts): number {
    return outcomes.completed + outcomes.failed + outcomes.expired + outcomes.active;
}

const JobBreakdownStyle = injectStyle("usage-v2-jobs", k => `
    ${k} .job-title-wrap {
        display: inline-flex;
        align-items: center;
        gap: 6px;
    }

    ${k} .job-info-trigger {
        display: inline-flex;
        align-items: center;
        color: var(--textSecondary);
        cursor: help;
    }

    ${k} .job-controls {
        display: inline-flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px 12px;
    }

    ${k} .job-divider {
        width: 1px;
        height: 16px;
        background: var(--borderColor);
    }

    ${k} .job-subhead {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    ${k} .job-subhead h4 {
        margin: 0;
        font-size: 13.5px;
        font-weight: 600;
        color: var(--textPrimary);
    }

    ${k} .job-table-head {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-top: 4px;
        flex-wrap: wrap;
    }

    ${k} .job-table-box {
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    ${k} .job-table-box .streamed-table-frame {
        min-height: 0;
    }

    ${k} .job-name-cell {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    ${k} .job-dot {
        width: 8px;
        height: 8px;
        border-radius: 2px;
        flex-shrink: 0;
    }

    ${k} .job-name-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
        line-height: 1.25;
    }

    ${k} .job-name-title {
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .job-name-sub {
        font-size: 11px;
        color: var(--textSecondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .job-num {
        text-align: right;
        font-variant-numeric: tabular-nums;
        display: block;
    }

    ${k} .job-muted {
        color: var(--textSecondary);
    }

    ${k} .job-ellipsis {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        display: block;
    }

    ${k} .job-empty {
        padding: 12px 0;
        font-size: 13px;
        color: var(--textSecondary);
    }
`);

const MAX_NAMED_SERIES = 8;

const OUTCOME_KEYS = ["completed", "failed", "expired", "active"] as const;

const OUTCOME_LABELS: Record<string, string> = {
    completed: "Completed",
    failed: "Failed",
    expired: "Expired",
    active: "Active",
};

const OUTCOME_COLORS: Record<string, string> = {
    completed: "var(--chart-green-main)",
    failed: "var(--chart-red-main)",
    expired: "var(--chart-orange-main)",
    active: "var(--chart-blue-main)",
};

function formatWaitDuration(seconds: number): string {
    if (!Number.isFinite(seconds) || seconds <= 0) return "0s";
    if (seconds < 60) {
        return `${formatNumber(seconds, {precision: 0})}s`;
    }
    if (seconds < 3600) {
        const mins = seconds / 60;
        return `${formatNumber(mins, {precision: mins >= 10 ? 0 : 1, removeTrailingZeros: true})}m`;
    }
    if (seconds < 86400) {
        const hrs = seconds / 3600;
        return `${formatNumber(hrs, {precision: hrs >= 10 ? 0 : 1, removeTrailingZeros: true})}h`;
    }
    const days = seconds / 86400;
    return `${formatNumber(days, {precision: 1, removeTrailingZeros: true})}d`;
}

function formatWaitDurationLong(seconds: number): string {
    if (!Number.isFinite(seconds) || seconds <= 0) return "0s";
    if (seconds < 60) return `${formatNumber(seconds, {precision: 0})}s`;
    const totalMins = Math.floor(seconds / 60);
    const remSecs = Math.round(seconds % 60);
    if (totalMins < 60) {
        return remSecs > 0 ? `${formatNumber(totalMins)}m ${formatNumber(remSecs)}s` : `${formatNumber(totalMins)}m`;
    }
    const totalHours = Math.floor(totalMins / 60);
    const remMins = totalMins % 60;
    if (totalHours < 24) {
        return remMins > 0 ? `${formatNumber(totalHours)}h ${formatNumber(remMins)}m` : `${formatNumber(totalHours)}h`;
    }
    const days = Math.floor(totalHours / 24);
    const remHours = totalHours % 24;
    return remHours > 0 ? `${formatNumber(days)}d ${formatNumber(remHours)}h` : `${formatNumber(days)}d`;
}

function formatJobMetricValue(metric: "compute" | "jobs", unit: FrontendAccountingUnit, value: number): string {
    if (metric === "compute") {
        return `${formatAmount(value)} ${unit.name}`;
    }
    const rounded = Math.round(value);
    return `${formatNumber(rounded)} job${rounded === 1 ? "" : "s"}`;
}

function formatProductSpecs(p: UsageJobProductSummary): string {
    const parts: string[] = [];
    if (p.gpu > 0) parts.push(`${formatNumber(p.gpu)} GPU`);
    if (p.cpu > 0) parts.push(`${formatNumber(p.cpu)} CPU`);
    if (p.memoryInGigs > 0) parts.push(`${formatNumber(p.memoryInGigs)} GB`);
    return parts.join(", ");
}

interface JobSeriesData {
    seriesKeys: string[];
    colorMap: Map<string, string>;
    bucketMaps: Map<string, number>[];
    cumulativeMaps: Map<string, number>[];
    bucketTotals: number[];
    cumulativeTotals: number[];
}

function buildJobSeriesData(
    timeline: UsageJobTimelineBucket[],
    metric: "compute" | "jobs",
    groupBy: JobGroupBy,
    selectedApps: ReadonlySet<string>,
    selectedProducts: ReadonlySet<string>,
): JobSeriesData {
    const hasAppFilter = selectedApps.size > 0;
    const hasProdFilter = selectedProducts.size > 0;
    const effectiveGroupBy: JobGroupBy = (metric === "compute" && groupBy === "outcome") ? "application" : groupBy;

    const rawTotals = new Map<string, number>();
    if (effectiveGroupBy === "application" || effectiveGroupBy === "product") {
        for (const bucket of timeline) {
            for (const sl of bucket.bySlice) {
                if (hasAppFilter && !selectedApps.has(sl.application)) continue;
                if (hasProdFilter && !selectedProducts.has(sl.productKey)) continue;
                const key = effectiveGroupBy === "application" ? sl.application : sl.productKey;
                const val = Math.max(0, metric === "compute" ? sl.estimatedHours : countJobOutcomes(sl.outcomes));
                if (val > 0) {
                    rawTotals.set(key, (rawTotals.get(key) ?? 0) + val);
                }
            }
        }
    }

    const namedSet = new Set<string>();
    if (effectiveGroupBy === "application" || effectiveGroupBy === "product") {
        const sortedRaw = [...rawTotals.entries()].sort((a, b) => b[1] - a[1]);
        const hasSpecificFilter = (effectiveGroupBy === "application" && hasAppFilter)
            || (effectiveGroupBy === "product" && hasProdFilter);
        const limit = hasSpecificFilter ? sortedRaw.length : MAX_NAMED_SERIES;
        for (let i = 0; i < Math.min(limit, sortedRaw.length); i++) {
            namedSet.add(sortedRaw[i][0]);
        }
    }

    const totals = new Map<string, number>();
    const bucketMaps: Map<string, number>[] = timeline.map(bucket => {
        const m = new Map<string, number>();
        if (effectiveGroupBy === "outcome") {
            let completed = 0;
            let failed = 0;
            let expired = 0;
            let active = 0;
            for (const sl of bucket.bySlice) {
                if (hasAppFilter && !selectedApps.has(sl.application)) continue;
                if (hasProdFilter && !selectedProducts.has(sl.productKey)) continue;
                completed += Math.max(0, sl.outcomes.completed);
                failed += Math.max(0, sl.outcomes.failed);
                expired += Math.max(0, sl.outcomes.expired);
                active += Math.max(0, sl.outcomes.active);
            }
            const pairs: [string, number][] = [
                ["completed", completed],
                ["failed", failed],
                ["expired", expired],
                ["active", active],
            ];
            for (const [k, v] of pairs) {
                if (v > 0) {
                    m.set(k, v);
                    totals.set(k, (totals.get(k) ?? 0) + v);
                }
            }
        } else {
            for (const sl of bucket.bySlice) {
                if (hasAppFilter && !selectedApps.has(sl.application)) continue;
                if (hasProdFilter && !selectedProducts.has(sl.productKey)) continue;
                const rawKey = effectiveGroupBy === "application" ? sl.application : sl.productKey;
                const val = Math.max(0, metric === "compute" ? sl.estimatedHours : countJobOutcomes(sl.outcomes));
                if (val > 0) {
                    const outKey = namedSet.has(rawKey) ? rawKey : "Other";
                    m.set(outKey, (m.get(outKey) ?? 0) + val);
                    totals.set(outKey, (totals.get(outKey) ?? 0) + val);
                }
            }
        }
        return m;
    });

    let seriesKeys: string[];
    let colorMap: Map<string, string>;
    if (effectiveGroupBy === "outcome") {
        seriesKeys = OUTCOME_KEYS.filter(k => (totals.get(k) ?? 0) > 0);
        colorMap = new Map<string, string>();
        for (const k of seriesKeys) {
            colorMap.set(k, OUTCOME_COLORS[k] ?? "var(--chart-blue-main)");
        }
    } else {
        seriesKeys = [...totals.entries()]
            .filter(([, total]) => total > 0)
            .sort((a, b) => {
                if (a[0] === "Other") return 1;
                if (b[0] === "Other") return -1;
                return b[1] - a[1];
            })
            .map(([k]) => k);
        colorMap = buildSeriesColorMap(seriesKeys);
    }

    return {
        seriesKeys,
        colorMap,
        bucketMaps,
        ...buildUsageSeriesTotals(seriesKeys, bucketMaps),
    };
}

function UsageJobRateChart(props: {
    timeline: UsageJobTimelineBucket[];
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    metric: "compute" | "jobs";
    series: JobSeriesData;
    width: number;
    height: number;
    highlightedKey: string | null;
    labelForKey: (key: string) => string;
}): React.ReactNode {
    const {
        timeline,
        bucketDurationMs,
        unit,
        metric,
        series,
        width,
        height,
        highlightedKey,
        labelForKey,
    } = props;

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || width <= 80 || series.seriesKeys.length === 0) return;

        const indices = timeline.map((_, i) => i);
        const stacked = stack<number>()
            .keys(series.seriesKeys)
            .value((idx, key) => series.bucketMaps[idx]?.get(key) ?? 0)(indices);

        let yMax = 0;
        for (const layer of stacked) {
            for (const pt of layer) {
                if (pt[1] > yMax) yMax = pt[1];
            }
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForCompactMax(yMax * 1.1)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(maxTs)])
            .range([0, innerW]);

        const yScale = scaleLinear().domain([0, yMax * 1.1]).nice().range([innerH, 0]);
        const g = svg.append("g").attr("transform", `translate(${margin.left},${margin.top})`);

        g.append("g")
            .attr("stroke", "var(--borderColor)")
            .attr("stroke-opacity", 0.35)
            .selectAll("line")
            .data(yScale.ticks(5))
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", d => yScale(d))
            .attr("y2", d => yScale(d));

        const tickFormat = formatBucketTick(bucketDurationMs);

        g.selectAll("g.layer")
            .data(stacked)
            .join("g")
            .attr("class", "layer")
            .attr("fill", d => series.colorMap.get(d.key) ?? "var(--chart-blue-main)")
            .attr("opacity", d => {
                if (highlightedKey === null) return 0.92;
                return d.key === highlightedKey ? 1 : 0.22;
            })
            .selectAll("rect")
            .data(layer => layer.map(pt => ({key: layer.key, idx: pt.data, y0: pt[0], y1: pt[1]})))
            .join("rect")
            .attr("x", d => xScale(new Date(timeline[d.idx].timestamp)) + 1)
            .attr("width", d => {
                const x0 = xScale(new Date(timeline[d.idx].timestamp));
                const x1 = xScale(new Date(timeline[d.idx].endTimestamp));
                return Math.max(2, x1 - x0 - 2);
            })
            .attr("y", d => yScale(d.y1))
            .attr("height", d => Math.max(0, yScale(d.y0) - yScale(d.y1)))
            .attr("rx", 1.5);

        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatCompactAxisNumber(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, metric === "compute" ? unit.name : "Jobs");

        const hoverLine = g.append("rect")
            .attr("fill", "var(--textPrimary)")
            .attr("opacity", 0)
            .attr("y", 0)
            .attr("height", innerH)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastIdx = -1;
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();
            let idx = timeline.findIndex(b => hoverMs >= b.timestamp && hoverMs < b.endTimestamp);
            if (idx < 0) {
                idx = hoverMs < minTs ? 0 : timeline.length - 1;
            }
            const bucket = timeline[idx];
            const bx0 = xScale(new Date(bucket.timestamp));
            const bx1 = xScale(new Date(bucket.endTimestamp));
            hoverLine.attr("x", bx0).attr("width", Math.max(2, bx1 - bx0)).attr("opacity", 0.08);

            if (idx !== lastIdx) {
                lastIdx = idx;
                appendUsageSeriesTooltip(tooltipEl, {
                    heading: `${tickFormat(new Date(bucket.timestamp))} – ${tickFormat(new Date(bucket.endTimestamp))}`,
                    totalLabel: metric === "compute" ? "Est. compute" : "Submitted jobs",
                    total: series.bucketTotals[idx] ?? 0,
                    keys: series.seriesKeys,
                    values: series.bucketMaps[idx],
                    colors: series.colorMap,
                    labelForKey,
                    formatValue: value => formatJobMetricValue(metric, unit, value),
                });
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            hoverLine.attr("opacity", 0);
            lastIdx = -1;
            tooltip.leaveListener();
        });
    }, [timeline, bucketDurationMs, unit, metric, series, width, height, highlightedKey, labelForKey]);

    if (timeline.length === 0 || series.seriesKeys.length === 0) {
        return <div className={ChartShellStyle}>
            <div className="usage-empty-chart">No matching jobs in this selection.</div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

function UsageJobCumulativeChart(props: {
    timeline: UsageJobTimelineBucket[];
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    metric: "compute" | "jobs";
    series: JobSeriesData;
    width: number;
    height: number;
    highlightedKey: string | null;
    labelForKey: (key: string) => string;
}): React.ReactNode {
    const {
        timeline,
        bucketDurationMs,
        unit,
        metric,
        series,
        width,
        height,
        highlightedKey,
        labelForKey,
    } = props;

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || width <= 80 || series.seriesKeys.length === 0) return;

        const indices = timeline.map((_, i) => i);
        const stacked = stack<number>()
            .keys(series.seriesKeys)
            .value((idx, key) => series.cumulativeMaps[idx]?.get(key) ?? 0)(indices);

        let yMax = 0;
        for (const layer of stacked) {
            for (const pt of layer) {
                if (pt[1] > yMax) yMax = pt[1];
            }
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForCompactMax(yMax * 1.1)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(maxTs)])
            .range([0, innerW]);

        const yScale = scaleLinear().domain([0, yMax * 1.1]).nice().range([innerH, 0]);
        const g = svg.append("g").attr("transform", `translate(${margin.left},${margin.top})`);

        g.append("g")
            .attr("stroke", "var(--borderColor)")
            .attr("stroke-opacity", 0.35)
            .selectAll("line")
            .data(yScale.ticks(5))
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", d => yScale(d))
            .attr("y2", d => yScale(d));

        const tickFormat = formatBucketTick(bucketDurationMs);
        const xForIdx = (idx: number) => {
            if (timeline.length === 1) return innerW;
            return xScale(new Date(timeline[idx].endTimestamp));
        };

        const areaGen = area<[number, number] & {data: number}>()
            .x(d => xForIdx(d.data))
            .y0(d => yScale(d[0]))
            .y1(d => yScale(d[1]))
            .curve(curveMonotoneX);

        g.selectAll("path.job-area")
            .data(stacked)
            .join("path")
            .attr("class", "job-area")
            .attr("d", layer => {
                if (timeline.length === 1) {
                    const pt = layer[0];
                    const y0 = yScale(pt[0]);
                    const y1 = yScale(pt[1]);
                    return `M 0,${y0} L 0,${y1} L ${innerW},${y1} L ${innerW},${y0} Z`;
                }
                return areaGen(layer as any) ?? "";
            })
            .attr("fill", d => series.colorMap.get(d.key) ?? "var(--chart-blue-main)")
            .attr("opacity", d => {
                if (highlightedKey === null) return 0.78;
                return d.key === highlightedKey ? 0.92 : 0.18;
            });

        if (timeline.length > 1) {
            const totalLineGen = line<number>()
                .x((_, i) => xForIdx(i))
                .y(v => yScale(v))
                .curve(curveMonotoneX);
            g.append("path")
                .datum(series.cumulativeTotals)
                .attr("fill", "none")
                .attr("stroke", "var(--textPrimary)")
                .attr("stroke-opacity", 0.5)
                .attr("stroke-width", 1.5)
                .attr("d", totalLineGen);
        }

        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatCompactAxisNumber(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, metric === "compute" ? unit.name : "Jobs");

        const hoverRule = g.append("line")
            .attr("stroke", "var(--textPrimary)")
            .attr("stroke-opacity", 0)
            .attr("stroke-width", 1)
            .attr("y1", 0)
            .attr("y2", innerH)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastIdx = -1;
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();
            let idx = timeline.findIndex(b => hoverMs >= b.timestamp && hoverMs < b.endTimestamp);
            if (idx < 0) {
                idx = hoverMs < minTs ? 0 : timeline.length - 1;
            }
            const bucket = timeline[idx];
            const xPos = xForIdx(idx);
            hoverRule.attr("x1", xPos).attr("x2", xPos).attr("stroke-opacity", 0.35);

            if (idx !== lastIdx) {
                lastIdx = idx;
                appendUsageSeriesTooltip(tooltipEl, {
                    heading: `Through ${tickFormat(new Date(bucket.endTimestamp))}`,
                    totalLabel: metric === "compute" ? "Cumulative est. compute" : "Cumulative jobs",
                    total: series.cumulativeTotals[idx] ?? 0,
                    keys: series.seriesKeys,
                    values: series.cumulativeMaps[idx],
                    colors: series.colorMap,
                    labelForKey,
                    formatValue: value => formatJobMetricValue(metric, unit, value),
                });
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            hoverRule.attr("stroke-opacity", 0);
            lastIdx = -1;
            tooltip.leaveListener();
        });
    }, [timeline, bucketDurationMs, unit, metric, series, width, height, highlightedKey, labelForKey]);

    if (timeline.length === 0 || series.seriesKeys.length === 0) {
        return <div className={ChartShellStyle}>
            <div className="usage-empty-chart">No matching jobs in this selection.</div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

function UsageJobQueueWaitChart(props: {
    timeline: UsageJobTimelineBucket[];
    bucketDurationMs: number;
    width: number;
    height: number;
}): React.ReactNode {
    const {timeline, bucketDurationMs, width, height} = props;
    const sampledPoints = useMemo(
        () => timeline.map((b, idx) => ({bucket: b, idx})).filter(it => it.bucket.queueSamples > 0),
        [timeline],
    );

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (sampledPoints.length === 0 || width <= 80) return;

        let yMax = 0;
        for (const pt of sampledPoints) {
            yMax = Math.max(yMax, pt.bucket.p90QueueSeconds, pt.bucket.avgQueueSeconds, pt.bucket.p50QueueSeconds);
        }
        if (yMax <= 0) yMax = 60;

        const maxLabel = formatWaitDuration(yMax * 1.15);
        const margin = {top: 18, right: 20, bottom: 40, left: Math.max(54, maxLabel.length * 7 + 18)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(maxTs)])
            .range([0, innerW]);

        const yScale = scaleLinear().domain([0, yMax * 1.15]).nice().range([innerH, 0]);
        const g = svg.append("g").attr("transform", `translate(${margin.left},${margin.top})`);

        g.append("g")
            .attr("stroke", "var(--borderColor)")
            .attr("stroke-opacity", 0.35)
            .selectAll("line")
            .data(yScale.ticks(5))
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", d => yScale(d))
            .attr("y2", d => yScale(d));

        const xMid = (b: UsageJobTimelineBucket) => xScale(new Date((b.timestamp + b.endTimestamp) / 2));

        const p90Area = area<{bucket: UsageJobTimelineBucket; idx: number}>()
            .x(d => xMid(d.bucket))
            .y0(d => yScale(d.bucket.p50QueueSeconds))
            .y1(d => yScale(d.bucket.p90QueueSeconds))
            .curve(curveMonotoneX);

        if (sampledPoints.length > 1) {
            g.append("path")
                .datum(sampledPoints)
                .attr("fill", "var(--chart-orange-main)")
                .attr("opacity", 0.16)
                .attr("d", p90Area);
        }

        const p90Line = line<{bucket: UsageJobTimelineBucket; idx: number}>()
            .x(d => xMid(d.bucket))
            .y(d => yScale(d.bucket.p90QueueSeconds))
            .curve(curveMonotoneX);
        const p50Line = line<{bucket: UsageJobTimelineBucket; idx: number}>()
            .x(d => xMid(d.bucket))
            .y(d => yScale(d.bucket.p50QueueSeconds))
            .curve(curveMonotoneX);
        const avgLine = line<{bucket: UsageJobTimelineBucket; idx: number}>()
            .x(d => xMid(d.bucket))
            .y(d => yScale(d.bucket.avgQueueSeconds))
            .curve(curveMonotoneX);

        g.append("path")
            .datum(sampledPoints)
            .attr("fill", "none")
            .attr("stroke", "var(--chart-orange-main)")
            .attr("stroke-width", 2)
            .attr("d", p90Line);

        g.append("path")
            .datum(sampledPoints)
            .attr("fill", "none")
            .attr("stroke", "var(--chart-blue-main)")
            .attr("stroke-width", 2)
            .attr("d", p50Line);

        g.append("path")
            .datum(sampledPoints)
            .attr("fill", "none")
            .attr("stroke", "var(--textSecondary)")
            .attr("stroke-width", 1.5)
            .attr("stroke-dasharray", "4,3")
            .attr("d", avgLine);

        if (sampledPoints.length === 1) {
            const pt = sampledPoints[0];
            g.append("circle").attr("cx", xMid(pt.bucket)).attr("cy", yScale(pt.bucket.p90QueueSeconds)).attr("r", 3.5).attr("fill", "var(--chart-orange-main)");
            g.append("circle").attr("cx", xMid(pt.bucket)).attr("cy", yScale(pt.bucket.p50QueueSeconds)).attr("r", 3.5).attr("fill", "var(--chart-blue-main)");
        }

        const tickFormat = formatBucketTick(bucketDurationMs);
        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatWaitDuration(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, "Wait");

        const hoverRule = g.append("line")
            .attr("stroke", "var(--textPrimary)")
            .attr("stroke-opacity", 0)
            .attr("stroke-width", 1)
            .attr("y1", 0)
            .attr("y2", innerH)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastIdx = -1;
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();
            let nearest = sampledPoints[0];
            let bestDist = Infinity;
            for (const pt of sampledPoints) {
                const mid = (pt.bucket.timestamp + pt.bucket.endTimestamp) / 2;
                const dist = Math.abs(mid - hoverMs);
                if (dist < bestDist) {
                    bestDist = dist;
                    nearest = pt;
                }
            }
            const bucket = nearest.bucket;
            const xPos = xMid(bucket);
            hoverRule.attr("x1", xPos).attr("x2", xPos).attr("stroke-opacity", 0.35);

            if (nearest.idx !== lastIdx) {
                lastIdx = nearest.idx;
                tooltipEl.innerHTML = "";
                const header = document.createElement("div");
                header.style.fontWeight = "700";
                header.style.marginBottom = "4px";
                header.textContent = `${tickFormat(new Date(bucket.timestamp))} – ${tickFormat(new Date(bucket.endTimestamp))}`;
                tooltipEl.append(header);

                const rows: [string, string, string][] = [
                    ["p90 wait", formatWaitDurationLong(bucket.p90QueueSeconds), "var(--chart-orange-main)"],
                    ["Median (p50)", formatWaitDurationLong(bucket.p50QueueSeconds), "var(--chart-blue-main)"],
                    ["Average", formatWaitDurationLong(bucket.avgQueueSeconds), "var(--textSecondary)"],
                ];
                for (const [labelText, valText, color] of rows) {
                    const row = document.createElement("div");
                    row.style.display = "flex";
                    row.style.alignItems = "center";
                    row.style.gap = "6px";
                    row.style.fontSize = "12px";
                    const dot = document.createElement("span");
                    dot.style.width = "8px";
                    dot.style.height = "8px";
                    dot.style.borderRadius = "2px";
                    dot.style.background = color;
                    dot.style.flexShrink = "0";
                    const label = document.createElement("span");
                    label.style.flex = "1";
                    label.textContent = labelText;
                    const val = document.createElement("span");
                    val.style.fontVariantNumeric = "tabular-nums";
                    val.textContent = valText;
                    row.append(dot, label, val);
                    tooltipEl.append(row);
                }

                const samples = document.createElement("div");
                samples.style.marginTop = "4px";
                samples.style.fontSize = "11px";
                samples.style.color = "var(--textSecondary)";
                samples.textContent = `${formatNumber(bucket.queueSamples)} started job${bucket.queueSamples === 1 ? "" : "s"}`;
                tooltipEl.append(samples);
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            hoverRule.attr("stroke-opacity", 0);
            lastIdx = -1;
            tooltip.leaveListener();
        });
    }, [timeline, sampledPoints, bucketDurationMs, width, height]);

    if (sampledPoints.length === 0) {
        return <div className={ChartShellStyle}>
            <div className="usage-empty-chart">No queue wait samples recorded in this period.</div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

function UsageJobRequestedVsActualChart(props: {
    timeline: UsageJobTimelineBucket[];
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    selectedApps: ReadonlySet<string>;
    selectedProducts: ReadonlySet<string>;
    width: number;
    height: number;
}): React.ReactNode {
    const {timeline, bucketDurationMs, unit, selectedApps, selectedProducts, width, height} = props;

    const bucketPairs = useMemo(() => {
        const hasApp = selectedApps.size > 0;
        const hasProd = selectedProducts.size > 0;
        return timeline.map(b => {
            let requested = 0;
            let actual = 0;
            for (const sl of b.bySlice) {
                if (hasApp && !selectedApps.has(sl.application)) continue;
                if (hasProd && !selectedProducts.has(sl.productKey)) continue;
                requested += Math.max(0, sl.requestedHours);
                actual += Math.max(0, sl.estimatedHours);
            }
            return {requested, actual};
        });
    }, [timeline, selectedApps, selectedProducts]);

    const hasAny = bucketPairs.some(p => p.requested > 0 || p.actual > 0);

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || !hasAny || width <= 80) return;

        let yMax = 0;
        for (const p of bucketPairs) {
            yMax = Math.max(yMax, p.requested, p.actual);
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForCompactMax(yMax * 1.1)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(maxTs)])
            .range([0, innerW]);

        const yScale = scaleLinear().domain([0, yMax * 1.1]).nice().range([innerH, 0]);
        const g = svg.append("g").attr("transform", `translate(${margin.left},${margin.top})`);

        g.append("g")
            .attr("stroke", "var(--borderColor)")
            .attr("stroke-opacity", 0.35)
            .selectAll("line")
            .data(yScale.ticks(5))
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", d => yScale(d))
            .attr("y2", d => yScale(d));

        g.selectAll("rect.req-bar")
            .data(bucketPairs)
            .join("rect")
            .attr("class", "req-bar")
            .attr("x", (_, i) => xScale(new Date(timeline[i].timestamp)) + 1)
            .attr("width", (_, i) => {
                const x0 = xScale(new Date(timeline[i].timestamp));
                const x1 = xScale(new Date(timeline[i].endTimestamp));
                return Math.max(2, x1 - x0 - 2);
            })
            .attr("y", d => yScale(d.requested))
            .attr("height", d => Math.max(0, innerH - yScale(d.requested)))
            .attr("fill", "var(--textSecondary)")
            .attr("opacity", 0.25)
            .attr("rx", 1.5);

        g.selectAll("rect.act-bar")
            .data(bucketPairs)
            .join("rect")
            .attr("class", "act-bar")
            .attr("x", (_, i) => xScale(new Date(timeline[i].timestamp)) + 1)
            .attr("width", (_, i) => {
                const x0 = xScale(new Date(timeline[i].timestamp));
                const x1 = xScale(new Date(timeline[i].endTimestamp));
                return Math.max(2, x1 - x0 - 2);
            })
            .attr("y", d => yScale(d.actual))
            .attr("height", d => Math.max(0, innerH - yScale(d.actual)))
            .attr("fill", "var(--chart-blue-main)")
            .attr("opacity", 0.88)
            .attr("rx", 1.5);

        const tickFormat = formatBucketTick(bucketDurationMs);
        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatCompactAxisNumber(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, unit.name);

        const hoverLine = g.append("rect")
            .attr("fill", "var(--textPrimary)")
            .attr("opacity", 0)
            .attr("y", 0)
            .attr("height", innerH)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastIdx = -1;
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();
            let idx = timeline.findIndex(b => hoverMs >= b.timestamp && hoverMs < b.endTimestamp);
            if (idx < 0) {
                idx = hoverMs < minTs ? 0 : timeline.length - 1;
            }
            const bucket = timeline[idx];
            const bx0 = xScale(new Date(bucket.timestamp));
            const bx1 = xScale(new Date(bucket.endTimestamp));
            hoverLine.attr("x", bx0).attr("width", Math.max(2, bx1 - bx0)).attr("opacity", 0.08);

            if (idx !== lastIdx) {
                lastIdx = idx;
                const pair = bucketPairs[idx];
                const eff = pair.requested > 0 ? Math.min(100, (pair.actual / pair.requested) * 100) : 0;
                tooltipEl.innerHTML = "";
                const header = document.createElement("div");
                header.style.fontWeight = "700";
                header.style.marginBottom = "4px";
                header.textContent = `${tickFormat(new Date(bucket.timestamp))} – ${tickFormat(new Date(bucket.endTimestamp))}`;
                tooltipEl.append(header);

                const rows: [string, string, string][] = [
                    ["Est. actual", `${formatAmount(pair.actual)} ${unit.name}`, "var(--chart-blue-main)"],
                    ["Requested", `${formatAmount(pair.requested)} ${unit.name}`, "var(--textSecondary)"],
                ];
                for (const [labelText, valText, color] of rows) {
                    const row = document.createElement("div");
                    row.style.display = "flex";
                    row.style.alignItems = "center";
                    row.style.gap = "6px";
                    row.style.fontSize = "12px";
                    const dot = document.createElement("span");
                    dot.style.width = "8px";
                    dot.style.height = "8px";
                    dot.style.borderRadius = "2px";
                    dot.style.background = color;
                    dot.style.flexShrink = "0";
                    const label = document.createElement("span");
                    label.style.flex = "1";
                    label.textContent = labelText;
                    const val = document.createElement("span");
                    val.style.fontVariantNumeric = "tabular-nums";
                    val.textContent = valText;
                    row.append(dot, label, val);
                    tooltipEl.append(row);
                }

                if (pair.requested > 0) {
                    const effRow = document.createElement("div");
                    effRow.style.marginTop = "4px";
                    effRow.style.fontSize = "11px";
                    effRow.style.color = "var(--textSecondary)";
                    effRow.textContent = `Time efficiency: ${usageFormatPercent(eff, 0)}`;
                    tooltipEl.append(effRow);
                }
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            hoverLine.attr("opacity", 0);
            lastIdx = -1;
            tooltip.leaveListener();
        });
    }, [timeline, bucketPairs, hasAny, bucketDurationMs, unit, width, height]);

    if (!hasAny) {
        return <div className={ChartShellStyle}>
            <div className="usage-empty-chart">No matching jobs in this selection.</div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

export function UsageJobBreakdownSection(props: {
    breakdown: UsageJobBreakdown;
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    twoColCharts: boolean;
    chartWidth: number;
    chartHeight: number;
    pinnedChildren: string[];
    onClearPinned: () => void;
}): React.ReactNode {
    const {
        breakdown,
        bucketDurationMs,
        unit,
        twoColCharts,
        chartWidth,
        chartHeight,
        pinnedChildren,
        onClearPinned,
    } = props;

    const [metric, setMetric] = useState<JobMetric>("compute");
    const [groupBy, setGroupBy] = useState<JobGroupBy>("application");
    const [tableTab, setTableTab] = useState<JobTableTab>("applications");
    const [selectedApps, setSelectedApps] = useState<string[]>([]);
    const [selectedProducts, setSelectedProducts] = useState<string[]>([]);
    const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
    const [appStore] = useState(() => new UcxTableStore());
    const [prodStore] = useState(() => new UcxTableStore());

    const appByKey = useMemo(
        () => new Map<string, UsageJobApplicationSummary>(breakdown.applications.map(a => [a.application, a])),
        [breakdown.applications],
    );
    const prodByKey = useMemo(
        () => new Map<string, UsageJobProductSummary>(breakdown.products.map(p => [`${p.provider}/${p.product}`, p])),
        [breakdown.products],
    );
    const activeApps = useMemo(() => selectedApps.filter(a => appByKey.has(a)), [selectedApps, appByKey]);
    const activeProducts = useMemo(() => selectedProducts.filter(p => prodByKey.has(p)), [selectedProducts, prodByKey]);
    const selectedAppSet = useMemo(() => new Set(activeApps), [activeApps]);
    const selectedProdSet = useMemo(() => new Set(activeProducts), [activeProducts]);
    const totalFilterCount = activeApps.length + activeProducts.length;

    const toggleAppFilter = useCallback((appName: string) => {
        setHighlightedKey(null);
        setSelectedApps(prev => (prev.includes(appName) ? prev.filter(a => a !== appName) : [...prev, appName]));
    }, []);

    const toggleProductFilter = useCallback((prodName: string) => {
        setHighlightedKey(null);
        setSelectedProducts(prev => (prev.includes(prodName) ? prev.filter(p => p !== prodName) : [...prev, prodName]));
    }, []);

    const effectiveGroupBy: JobGroupBy = (metric === "compute" && groupBy === "outcome") ? "application" : groupBy;
    const seriesMetric: "compute" | "jobs" = metric === "jobs" ? "jobs" : "compute";

    const series = useMemo(
        () => buildJobSeriesData(breakdown.timeline, seriesMetric, effectiveGroupBy, selectedAppSet, selectedProdSet),
        [breakdown.timeline, seriesMetric, effectiveGroupBy, selectedAppSet, selectedProdSet],
    );

    const appColorMap = useMemo(() => {
        if (effectiveGroupBy === "application") return series.colorMap;
        return buildJobSeriesData(breakdown.timeline, seriesMetric, "application", selectedAppSet, selectedProdSet).colorMap;
    }, [breakdown.timeline, seriesMetric, effectiveGroupBy, selectedAppSet, selectedProdSet, series.colorMap]);

    const prodColorMap = useMemo(() => {
        if (effectiveGroupBy === "product") return series.colorMap;
        return buildJobSeriesData(breakdown.timeline, seriesMetric, "product", selectedAppSet, selectedProdSet).colorMap;
    }, [breakdown.timeline, seriesMetric, effectiveGroupBy, selectedAppSet, selectedProdSet, series.colorMap]);

    const showProviders = new Set(breakdown.products.map(product => product.provider)).size > 1;
    const formatProductName = useCallback((key: string): string => {
        const product = prodByKey.get(key);
        if (!product) return key;
        const name = productDisplayName({name: product.product, category: {provider: product.provider}});
        return showProviders ? `${name} (${getShortProviderTitle(product.provider)})` : name;
    }, [prodByKey, showProviders]);

    const labelForKey = useCallback((key: string): string => {
        if (effectiveGroupBy === "outcome") {
            return OUTCOME_LABELS[key] ?? key;
        }
        if (effectiveGroupBy === "product") {
            return formatProductName(key);
        }
        return key;
    }, [effectiveGroupBy, formatProductName]);

    const appColumns = useMemo((): TableColumn[] => [
        {key: "job.app.filter", label: "", jsonPath: "filter", sortType: TableColumnSortType.Number, alignment: "center"},
        {key: "job.app.name", label: "Application", jsonPath: "name", sortType: TableColumnSortType.Text, alignment: "left"},
        {key: "job.app.jobs", label: "Jobs", jsonPath: "jobs", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.app.hours", label: `Est. ${unit.name}`, jsonPath: "hours", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.app.share", label: "Share", jsonPath: "share", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.app.issues", label: "Failed / Expired", jsonPath: "issues", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.app.prods", label: "Top machine types", jsonPath: "prods", sortType: TableColumnSortType.Text, alignment: "left"},
    ], [unit.name]);

    const appWidths = useMemo((): Record<string, number> => ({
        "job.app.filter": 40,
        "job.app.name": 210,
        "job.app.jobs": 90,
        "job.app.hours": 145,
        "job.app.share": 80,
        "job.app.issues": 135,
        "job.app.prods": 240,
    }), []);

    const prodColumns = useMemo((): TableColumn[] => [
        {key: "job.prod.filter", label: "", jsonPath: "filter", sortType: TableColumnSortType.Number, alignment: "center"},
        {key: "job.prod.name", label: "Machine type", jsonPath: "name", sortType: TableColumnSortType.Text, alignment: "left"},
        {key: "job.prod.jobs", label: "Jobs", jsonPath: "jobs", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.prod.hours", label: `Est. ${unit.name}`, jsonPath: "hours", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.prod.share", label: "Share", jsonPath: "share", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.prod.req", label: "Requested", jsonPath: "req", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.prod.p50", label: "Median wait", jsonPath: "p50", sortType: TableColumnSortType.Number, alignment: "right"},
        {key: "job.prod.p90", label: "p90 wait", jsonPath: "p90", sortType: TableColumnSortType.Number, alignment: "right"},
    ], [unit.name]);

    const prodWidths = useMemo((): Record<string, number> => ({
        "job.prod.filter": 40,
        "job.prod.name": 240,
        "job.prod.jobs": 85,
        "job.prod.hours": 140,
        "job.prod.share": 80,
        "job.prod.req": 135,
        "job.prod.p50": 105,
        "job.prod.p90": 105,
    }), []);

    const totalEstHours = breakdown.applications.reduce((total, application) => total + application.estimatedHours, 0);

    const appRows = useMemo((): TableRow[] => breakdown.applications.map(a => {
        const share = totalEstHours > 0 ? (a.estimatedHours / totalEstHours) * 100 : 0;
        const issues = a.outcomes.failed + a.outcomes.expired;
        return {
            key: a.application,
            group: "",
            cells: [
                selectedAppSet.has(a.application) ? "1" : "0",
                a.application,
                `${countJobOutcomes(a.outcomes)}`,
                `${a.estimatedHours.toFixed(4)}`,
                `${share.toFixed(2)}`,
                `${issues}`,
                a.topProductKeys.map(formatProductName).join(", "),
            ],
        };
    }), [breakdown.applications, selectedAppSet, totalEstHours, formatProductName]);

    const prodRows = useMemo((): TableRow[] => breakdown.products.map(p => {
        const key = `${p.provider}/${p.product}`;
        const share = totalEstHours > 0 ? (p.estimatedHours / totalEstHours) * 100 : 0;
        return {
            key,
            group: "",
            cells: [
                selectedProdSet.has(key) ? "1" : "0",
                formatProductName(key),
                `${p.jobs}`,
                `${p.estimatedHours.toFixed(4)}`,
                `${share.toFixed(2)}`,
                `${p.requestedHours.toFixed(4)}`,
                `${p.queueSamples > 0 ? p.p50QueueSeconds.toFixed(1) : -1}`,
                `${p.queueSamples > 0 ? p.p90QueueSeconds.toFixed(1) : -1}`,
            ],
        };
    }), [breakdown.products, selectedProdSet, totalEstHours, formatProductName]);

    useLayoutEffect(() => {
        appStore.apply({
            tableId: "usage-v2-job-apps",
            revision: 0,
            snapshot: true,
            columns: appColumns,
            upserts: appRows,
            removed: [],
        });
    }, [appStore, appColumns, appRows]);

    useLayoutEffect(() => {
        prodStore.apply({
            tableId: "usage-v2-job-products",
            revision: 0,
            snapshot: true,
            columns: prodColumns,
            upserts: prodRows,
            removed: [],
        });
    }, [prodStore, prodColumns, prodRows]);

    const renderAppCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const a = appByKey.get(context.row.key);
        if (!a) return context.cell;

        if (context.column.key === "job.app.filter") {
            const isSelected = selectedAppSet.has(a.application);
            const label = isSelected ? "Remove application from job chart filter" : "Filter compute and job counts to this application";
            return <TooltipV2 tooltip={label}>
                <button
                    type="button"
                    className={UcxTableIconButtonClass}
                    aria-pressed={isSelected}
                    aria-label={label}
                    onClick={ev => {
                        ev.stopPropagation();
                        toggleAppFilter(a.application);
                    }}
                    onKeyDown={ev => {
                        if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                    }}
                >
                    <Icon name={isSelected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                </button>
            </TooltipV2>;
        }
        if (context.column.key === "job.app.name") {
            const dotColor = appColorMap.get(a.application) ?? "var(--textSecondary)";
            return <div className="job-name-cell">
                <span className="job-dot" style={{background: dotColor}} />
                <span className="job-name-title">{a.application}</span>
            </div>;
        }
        if (context.column.key === "job.app.jobs") {
            return <span className="job-num">{formatNumber(countJobOutcomes(a.outcomes))}</span>;
        }
        if (context.column.key === "job.app.hours") {
            return <span className="job-num">{formatAmount(a.estimatedHours)}</span>;
        }
        if (context.column.key === "job.app.share") {
            if (totalEstHours <= 0) return <span className="job-num job-muted">—</span>;
            return <span className="job-num">{usageFormatPercent((a.estimatedHours / totalEstHours) * 100)}</span>;
        }
        if (context.column.key === "job.app.issues") {
            const o = a.outcomes;
            const tip = `Completed: ${formatNumber(o.completed)}, Failed: ${formatNumber(o.failed)}, Expired: ${formatNumber(o.expired)}, Active: ${formatNumber(o.active)}`;
            if (o.failed === 0 && o.expired === 0) {
                return <TooltipV2 tooltip={tip}>
                    <span className="job-num job-muted">0</span>
                </TooltipV2>;
            }
            return <TooltipV2 tooltip={tip}>
                <span className="job-num">
                    {formatNumber(o.failed)} / {formatNumber(o.expired)}
                </span>
            </TooltipV2>;
        }
        if (context.column.key === "job.app.prods") {
            const text = a.topProductKeys.map(formatProductName).join(", ");
            if (text === "") return <span className="job-muted">—</span>;
            return <TooltipV2 tooltip={text}>
                <span className="job-ellipsis job-muted">{text}</span>
            </TooltipV2>;
        }
        return context.cell;
    }, [appByKey, selectedAppSet, toggleAppFilter, appColorMap, totalEstHours, unit.name, formatProductName]);

    const renderProdCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const p = prodByKey.get(context.row.key);
        if (!p) return context.cell;

        if (context.column.key === "job.prod.filter") {
            const isSelected = selectedProdSet.has(context.row.key);
            const label = isSelected ? "Remove machine type from job chart filter" : "Filter compute and job counts to this machine type";
            return <TooltipV2 tooltip={label}>
                <button
                    type="button"
                    className={UcxTableIconButtonClass}
                    aria-pressed={isSelected}
                    aria-label={label}
                    onClick={ev => {
                        ev.stopPropagation();
                        toggleProductFilter(context.row.key);
                    }}
                    onKeyDown={ev => {
                        if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                    }}
                >
                    <Icon name={isSelected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                </button>
            </TooltipV2>;
        }
        if (context.column.key === "job.prod.name") {
            const dotColor = prodColorMap.get(context.row.key) ?? "var(--textSecondary)";
            const specs = formatProductSpecs(p);
            const displayName = formatProductName(context.row.key);
            return <div className="job-name-cell">
                <span className="job-dot" style={{background: dotColor}} />
                <div className="job-name-text">
                    <span className="job-name-title">{displayName}</span>
                    {specs !== "" ? <span className="job-name-sub">{specs}</span> : null}
                </div>
            </div>;
        }
        if (context.column.key === "job.prod.jobs") {
            return <span className="job-num">{formatNumber(p.jobs)}</span>;
        }
        if (context.column.key === "job.prod.hours") {
            return <span className="job-num">{formatAmount(p.estimatedHours)}</span>;
        }
        if (context.column.key === "job.prod.share") {
            if (totalEstHours <= 0) return <span className="job-num job-muted">—</span>;
            return <span className="job-num">{usageFormatPercent((p.estimatedHours / totalEstHours) * 100)}</span>;
        }
        if (context.column.key === "job.prod.req") {
            return <span className="job-num">{formatAmount(p.requestedHours)}</span>;
        }
        if (context.column.key === "job.prod.p50") {
            if (p.queueSamples <= 0) return <span className="job-num job-muted">—</span>;
            return <TooltipV2 tooltip={`Average: ${formatWaitDurationLong(p.avgQueueSeconds)} (${formatNumber(p.queueSamples)} started jobs)`}>
                <span className="job-num">{formatWaitDuration(p.p50QueueSeconds)}</span>
            </TooltipV2>;
        }
        if (context.column.key === "job.prod.p90") {
            if (p.queueSamples <= 0) return <span className="job-num job-muted">—</span>;
            return <TooltipV2 tooltip={`Max wait: ${formatWaitDurationLong(p.maxQueueSeconds)}, Average: ${formatWaitDurationLong(p.avgQueueSeconds)}`}>
                <span className="job-num">{formatWaitDuration(p.p90QueueSeconds)}</span>
            </TooltipV2>;
        }
        return context.cell;
    }, [prodByKey, selectedProdSet, toggleProductFilter, prodColorMap, totalEstHours, formatProductName]);

    const noteParts: string[] = [];
    if (breakdown.coFundedWorkspaceCount > 0) {
        noteParts.push(
            `Job counts and estimated compute include proportional estimates for ${formatNumber(breakdown.coFundedWorkspaceCount)} co-funded sub-project${breakdown.coFundedWorkspaceCount === 1 ? "" : "s"} weighted by daily billed spend.`,
        );
    } else {
        noteParts.push("Job counts are based on recorded jobs submitted in this period.");
    }
    noteParts.push("Estimated compute counts runtime through the end of the report and groups it by job submission time. Job outcomes reflect state at the end of the report, and queue wait uses the first recorded start within the report.");
    if (pinnedChildren.length > 0) {
        noteParts.push(`Scoped to ${formatNumber(pinnedChildren.length)} pinned sub-project${pinnedChildren.length === 1 ? "" : "s"}.`);
    }

    const hasJobs = breakdown.applications.length > 0 || breakdown.products.length > 0;
    const rateTitle = metric === "compute"
        ? `Estimated ${unit.name} by submission time`
        : metric === "jobs"
            ? "Submitted jobs per period"
            : "Queue wait over time (all jobs in report)";
    const rightTitle = metric === "compute"
        ? `Cumulative estimated ${unit.name}`
        : metric === "jobs"
            ? "Cumulative submitted jobs"
            : `Requested vs. estimated ${unit.name}`;

    const appTableHeight = Math.min(8 * 44 + 42, Math.max(1, breakdown.applications.length) * 44 + 42);
    const prodTableHeight = Math.min(8 * 44 + 42, Math.max(1, breakdown.products.length) * 44 + 42);

    return <section className={`usage-section ${JobBreakdownStyle}`} aria-label="Job breakdown">
        <div className="usage-section-head">
            <div className="job-title-wrap">
                <h3>Job breakdown</h3>
                {breakdown.available && hasJobs ? (
                    <TooltipV2 tooltip={noteParts.join(" ")}>
                        <span className="job-info-trigger" aria-label="Job breakdown details">
                            <Icon name="heroInformationCircle" size={16} />
                        </span>
                    </TooltipV2>
                ) : null}
            </div>
            <div className="job-controls">
                {totalFilterCount > 0 ? (
                    <Button
                        color="ghost"
                        onClick={() => {
                            setSelectedApps([]);
                            setSelectedProducts([]);
                        }}
                    >
                        Clear filter ({formatNumber(totalFilterCount)})
                    </Button>
                ) : null}
                {pinnedChildren.length > 0 ? (
                    <Button color="ghost" onClick={onClearPinned}>
                        Clear pinned ({formatNumber(pinnedChildren.length)})
                    </Button>
                ) : null}
                {breakdown.available && hasJobs ? <>
                    <div className="usage-mode-row" role="group" aria-label="Job metric">
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={metric === "compute"}
                            onClick={() => {
                                setMetric("compute");
                                setHighlightedKey(null);
                            }}
                        >
                            Est. compute
                        </button>
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={metric === "jobs"}
                            onClick={() => {
                                setMetric("jobs");
                                setHighlightedKey(null);
                            }}
                        >
                            Jobs
                        </button>
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={metric === "queue"}
                            onClick={() => {
                                setMetric("queue");
                                setHighlightedKey(null);
                            }}
                        >
                            Queue wait
                        </button>
                    </div>
                    {metric !== "queue" ? <>
                        <span className="job-divider" aria-hidden="true" />
                        <div className="usage-mode-row" role="group" aria-label="Job grouping">
                            <button
                                type="button"
                                className="usage-mode-button"
                                aria-pressed={effectiveGroupBy === "application"}
                                onClick={() => {
                                    setGroupBy("application");
                                    setHighlightedKey(null);
                                }}
                            >
                                By application
                            </button>
                            <button
                                type="button"
                                className="usage-mode-button"
                                aria-pressed={effectiveGroupBy === "product"}
                                onClick={() => {
                                    setGroupBy("product");
                                    setHighlightedKey(null);
                                }}
                            >
                                By machine type
                            </button>
                            {metric === "jobs" ? (
                                <button
                                    type="button"
                                    className="usage-mode-button"
                                    aria-pressed={effectiveGroupBy === "outcome"}
                                    onClick={() => {
                                        setGroupBy("outcome");
                                        setHighlightedKey(null);
                                    }}
                                >
                                    By outcome
                                </button>
                            ) : null}
                        </div>
                    </> : null}
                </> : null}
            </div>
        </div>

        {!breakdown.available ? (
            <div className="job-empty">
                Job breakdown is currently unavailable.
            </div>
        ) : !hasJobs ? (
            <div className="job-empty">
                No jobs submitted in this period.
            </div>
        ) : <>
            <div
                className="usage-chart-grid"
                style={{gridTemplateColumns: twoColCharts ? "repeat(2, minmax(0, 1fr))" : "minmax(0, 1fr)"}}
            >
                <div className="usage-chart-col">
                    <div className="job-subhead">
                        <h4>{rateTitle}</h4>
                        <UsageChartExpandButton title={rateTitle} renderContent={(w, h) => metric === "queue" ? (
                            <UsageJobQueueWaitChart
                                timeline={breakdown.timeline}
                                bucketDurationMs={bucketDurationMs}
                                width={w}
                                height={h}
                            />
                        ) : (
                            <UsageJobRateChart
                                timeline={breakdown.timeline}
                                bucketDurationMs={bucketDurationMs}
                                unit={unit}
                                metric={seriesMetric}
                                series={series}
                                width={w}
                                height={h}
                                highlightedKey={null}
                                labelForKey={labelForKey}
                            />
                        )} />
                    </div>
                    {metric === "queue" ? (
                        <UsageJobQueueWaitChart
                            timeline={breakdown.timeline}
                            bucketDurationMs={bucketDurationMs}
                            width={chartWidth}
                            height={chartHeight}
                        />
                    ) : (
                        <UsageJobRateChart
                            timeline={breakdown.timeline}
                            bucketDurationMs={bucketDurationMs}
                            unit={unit}
                            metric={seriesMetric}
                            series={series}
                            width={chartWidth}
                            height={chartHeight}
                            highlightedKey={highlightedKey}
                            labelForKey={labelForKey}
                        />
                    )}
                </div>

                <div className="usage-chart-col">
                    <div className="job-subhead">
                        <h4>{rightTitle}</h4>
                        <UsageChartExpandButton title={rightTitle} renderContent={(w, h) => metric === "queue" ? (
                            <UsageJobRequestedVsActualChart
                                timeline={breakdown.timeline}
                                bucketDurationMs={bucketDurationMs}
                                unit={unit}
                                selectedApps={selectedAppSet}
                                selectedProducts={selectedProdSet}
                                width={w}
                                height={h}
                            />
                        ) : (
                            <UsageJobCumulativeChart
                                timeline={breakdown.timeline}
                                bucketDurationMs={bucketDurationMs}
                                unit={unit}
                                metric={seriesMetric}
                                series={series}
                                width={w}
                                height={h}
                                highlightedKey={null}
                                labelForKey={labelForKey}
                            />
                        )} />
                    </div>
                    {metric === "queue" ? (
                        <UsageJobRequestedVsActualChart
                            timeline={breakdown.timeline}
                            bucketDurationMs={bucketDurationMs}
                            unit={unit}
                            selectedApps={selectedAppSet}
                            selectedProducts={selectedProdSet}
                            width={chartWidth}
                            height={chartHeight}
                        />
                    ) : (
                        <UsageJobCumulativeChart
                            timeline={breakdown.timeline}
                            bucketDurationMs={bucketDurationMs}
                            unit={unit}
                            metric={seriesMetric}
                            series={series}
                            width={chartWidth}
                            height={chartHeight}
                            highlightedKey={highlightedKey}
                            labelForKey={labelForKey}
                        />
                    )}
                </div>
            </div>

            {metric === "queue" ? (
                <div className={ChartShellStyle}>
                    <div className="usage-legend" style={{paddingLeft: 0, marginTop: 0}}>
                        <div className="usage-legend-item" style={{cursor: "default"}}>
                            <span className="usage-legend-line" style={{color: "var(--chart-blue-main)"}} />
                            <span>Median (p50) wait</span>
                        </div>
                        <div className="usage-legend-item" style={{cursor: "default"}}>
                            <span className="usage-legend-line" style={{color: "var(--chart-orange-main)"}} />
                            <span>p90 wait</span>
                        </div>
                        <div className="usage-legend-item" style={{cursor: "default"}}>
                            <span className="usage-legend-line" data-dashed="true" style={{color: "var(--textSecondary)"}} />
                            <span>Average wait</span>
                        </div>
                        <div className="usage-legend-item" style={{cursor: "default"}}>
                            <span className="usage-legend-swatch" style={{background: "var(--textSecondary)", opacity: 0.35}} />
                            <span>Requested {unit.name}</span>
                        </div>
                        <div className="usage-legend-item" style={{cursor: "default"}}>
                            <span className="usage-legend-swatch" style={{background: "var(--chart-blue-main)"}} />
                            <span>Est. actual {unit.name}</span>
                        </div>
                    </div>
                </div>
            ) : series.seriesKeys.length > 0 ? (
                <div className={ChartShellStyle}>
                    <div className="usage-legend" style={{paddingLeft: 0, marginTop: 0}}>
                        {series.seriesKeys.map(key => {
                            const isDimmed = highlightedKey !== null && highlightedKey !== key;
                            return <div
                                key={key}
                                className="usage-legend-item"
                                data-dimmed={isDimmed ? "true" : undefined}
                                onMouseEnter={() => setHighlightedKey(key)}
                                onMouseLeave={() => setHighlightedKey(null)}
                                onClick={() => setHighlightedKey(highlightedKey === key ? null : key)}
                            >
                                <span className="usage-legend-swatch" style={{background: series.colorMap.get(key) ?? "#888"}} />
                                <span>{labelForKey(key)}</span>
                            </div>;
                        })}
                    </div>
                </div>
            ) : null}

            <Flex flexDirection="column" gap="8px" mt="8px">
                <div className="job-table-head">
                    <div className="usage-mode-row" role="tablist" aria-label="Job summary view">
                        <button
                            type="button"
                            role="tab"
                            className="usage-mode-button"
                            aria-selected={tableTab === "applications"}
                            aria-pressed={tableTab === "applications"}
                            onClick={() => setTableTab("applications")}
                        >
                            Applications ({formatNumber(breakdown.applications.length)})
                        </button>
                        <button
                            type="button"
                            role="tab"
                            className="usage-mode-button"
                            aria-selected={tableTab === "products"}
                            aria-pressed={tableTab === "products"}
                            onClick={() => setTableTab("products")}
                        >
                            Machine types ({formatNumber(breakdown.products.length)})
                        </button>
                    </div>
                </div>
                {tableTab === "applications" ? (
                    <div className="job-table-box" style={{height: appTableHeight}}>
                        <UcxStreamedTable
                            tableId="usage-v2-job-apps"
                            store={appStore}
                            showControls={false}
                            density="compact"
                            rowHeight={44}
                            columnWidths={appWidths}
                            renderCell={renderAppCell}
                            onRowActivated={() => void 0}
                            emptyMessage="No applications recorded."
                        />
                    </div>
                ) : (
                    <div className="job-table-box" style={{height: prodTableHeight}}>
                        <UcxStreamedTable
                            tableId="usage-v2-job-products"
                            store={prodStore}
                            showControls={false}
                            density="compact"
                            rowHeight={44}
                            columnWidths={prodWidths}
                            renderCell={renderProdCell}
                            onRowActivated={() => void 0}
                            emptyMessage="No machine types recorded."
                        />
                    </div>
                )}
            </Flex>
        </>}
    </section>;
}
