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
import {FrontendAccountingUnit} from "@/Accounting/index";
import {formatAmount} from "@/Accounting/Allocations/Model";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";
import {
    UsageInferenceBreakdown,
    UsageInferenceModelSummary,
    UsageInferenceTimelineBucket,
    usageFormatValue,
} from "./Api";
import {
    ChartShellStyle,
    buildSeriesColorMap,
    createUsageChartTooltip,
    formatAxisTick,
    formatBucketTick,
    leftMarginForMax,
    openUsageChartDialog,
    styleAxes,
} from "./Charts";

type InferenceMetric = "cost" | "tokens";
type InferenceGroupBy = "model" | "type";

const InferenceStyle = injectStyle("usage-v2-inference", k => `
    ${k} .inf-title-wrap {
        display: inline-flex;
        align-items: center;
        gap: 6px;
    }

    ${k} .inf-info-trigger {
        display: inline-flex;
        align-items: center;
        color: var(--textSecondary);
        cursor: help;
    }

    ${k} .inf-controls {
        display: inline-flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 8px 12px;
    }

    ${k} .inf-divider {
        width: 1px;
        height: 16px;
        background: var(--borderColor);
    }

    ${k} .inf-subhead {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
    }

    ${k} .inf-subhead h4 {
        margin: 0;
        font-size: 13.5px;
        font-weight: 600;
        color: var(--textPrimary);
    }

    ${k} .inf-table-head {
        display: flex;
        align-items: baseline;
        justify-content: space-between;
        gap: 12px;
        margin-top: 4px;
    }

    ${k} .inf-table-head h4 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
    }

    ${k} .inf-table-count {
        font-size: 12px;
        color: var(--textSecondary);
    }

    ${k} .inf-table-box {
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    ${k} .inf-table-box .streamed-table-frame {
        min-height: 0;
    }

    ${k} .inf-model-cell {
        display: flex;
        align-items: center;
        gap: 8px;
        min-width: 0;
    }

    ${k} .inf-model-dot {
        width: 8px;
        height: 8px;
        border-radius: 2px;
        flex-shrink: 0;
    }

    ${k} .inf-model-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
        line-height: 1.25;
    }

    ${k} .inf-model-title {
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .inf-model-sub {
        font-size: 11px;
        color: var(--textSecondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .inf-num {
        text-align: right;
        font-variant-numeric: tabular-nums;
        display: block;
    }

    ${k} .inf-muted {
        color: var(--textSecondary);
    }

    ${k} .inf-empty {
        padding: 12px 0;
        font-size: 13px;
        color: var(--textSecondary);
    }
`);

const TYPE_KEYS_COST = ["input", "cachedInput", "output", "image", "audio", "video"] as const;
const TYPE_KEYS_TOKENS = ["input", "cachedInput", "output"] as const;
const MAX_NAMED_MODELS = 8;

const TYPE_LABELS: Record<string, string> = {
    input: "Uncached input",
    cachedInput: "Cached input",
    output: "Output",
    image: "Images",
    audio: "Audio",
    video: "Video",
};

const TYPE_COLORS: Record<string, string> = {
    input: "var(--chart-blue-main)",
    cachedInput: "var(--chart-green-main)",
    output: "var(--chart-purple-main)",
    image: "var(--chart-orange-main)",
    audio: "var(--chart-yellow-main)",
    video: "var(--chart-red-main)",
};

function formatSiNumber(value: number): string {
    const abs = Math.abs(value);
    const sign = value < 0 ? "-" : "";
    const suffixes: [number, string][] = [
        [1e12, "T"],
        [1e9, "B"],
        [1e6, "M"],
        [1e3, "K"],
    ];
    for (const [threshold, suffix] of suffixes) {
        if (abs >= threshold) {
            const scaled = abs / threshold;
            const formatted = scaled >= 100
                ? `${Math.round(scaled)}`
                : scaled >= 10
                    ? scaled.toFixed(1).replace(/\.0$/, "")
                    : scaled.toFixed(2).replace(/\.?0+$/, "");
            return `${sign}${formatted}${suffix}`;
        }
    }
    return formatAmount(value);
}

function formatInferenceAxisTick(metric: InferenceMetric, value: number): string {
    if (metric === "tokens") {
        return formatSiNumber(value);
    }
    return formatAxisTick(value);
}

function leftMarginForInferenceMax(metric: InferenceMetric, yMax: number): number {
    if (metric === "tokens") {
        const sample = formatSiNumber(Math.ceil(yMax));
        return Math.max(52, sample.length * 7 + 18);
    }
    return leftMarginForMax(yMax);
}

function formatDurationSeconds(totalSeconds: number): string {
    if (totalSeconds <= 0) return "—";
    if (totalSeconds < 60) {
        return `${formatAmount(totalSeconds)} s`;
    }
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = Math.round(totalSeconds % 60);
    if (minutes < 60) {
        return seconds > 0 ? `${formatNumber(minutes)}m ${formatNumber(seconds)}s` : `${formatNumber(minutes)}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remMin = minutes % 60;
    return remMin > 0 ? `${formatNumber(hours)}h ${formatNumber(remMin)}m` : `${formatNumber(hours)}h`;
}

function formatMetricValue(metric: InferenceMetric, unit: FrontendAccountingUnit, rawValue: number): string {
    if (metric === "cost") {
        return usageFormatValue(unit, rawValue);
    }
    return `${formatNumber(Math.round(rawValue))} tokens`;
}

function scaleMetricValue(metric: InferenceMetric, unit: FrontendAccountingUnit, rawValue: number): number {
    if (metric === "cost") {
        return rawValue * unit.balanceFactor;
    }
    return rawValue;
}

interface InferenceSeriesData {
    seriesKeys: string[];
    colorMap: Map<string, string>;
    bucketMaps: Map<string, number>[];
    cumulativeMaps: Map<string, number>[];
    bucketTotals: number[];
    cumulativeTotals: number[];
}

function buildInferenceSeriesData(
    timeline: UsageInferenceTimelineBucket[],
    metric: InferenceMetric,
    groupBy: InferenceGroupBy,
    selectedModels: ReadonlySet<string>,
): InferenceSeriesData {
    const hasFilter = selectedModels.size > 0;
    const rawModelTotals = new Map<string, number>();

    if (groupBy === "model") {
        for (const bucket of timeline) {
            for (const item of bucket.byModel) {
                if (hasFilter && !selectedModels.has(item.model)) continue;
                const val = Math.max(0, metric === "cost" ? item.estimatedCost.total : item.tokens.total);
                if (val > 0) {
                    rawModelTotals.set(item.model, (rawModelTotals.get(item.model) ?? 0) + val);
                }
            }
        }
    }

    const namedModels = new Set<string>();
    if (groupBy === "model") {
        const sortedRaw = [...rawModelTotals.entries()].sort((a, b) => b[1] - a[1]);
        const limit = hasFilter ? sortedRaw.length : MAX_NAMED_MODELS;
        for (let i = 0; i < Math.min(limit, sortedRaw.length); i++) {
            namedModels.add(sortedRaw[i][0]);
        }
    }

    const totals = new Map<string, number>();

    const bucketMaps: Map<string, number>[] = timeline.map(bucket => {
        const m = new Map<string, number>();
        if (groupBy === "type") {
            let input = 0;
            let cachedInput = 0;
            let output = 0;
            let image = 0;
            let audio = 0;
            let video = 0;

            if (hasFilter) {
                for (const item of bucket.byModel) {
                    if (!selectedModels.has(item.model)) continue;
                    if (metric === "cost") {
                        input += Math.max(0, item.estimatedCost.input);
                        cachedInput += Math.max(0, item.estimatedCost.cachedInput);
                        output += Math.max(0, item.estimatedCost.output);
                        image += Math.max(0, item.estimatedCost.image);
                        audio += Math.max(0, item.estimatedCost.audio);
                        video += Math.max(0, item.estimatedCost.video);
                    } else {
                        input += Math.max(0, item.tokens.input);
                        cachedInput += Math.max(0, item.tokens.cachedInput);
                        output += Math.max(0, item.tokens.output);
                    }
                }
            } else if (metric === "cost") {
                input = Math.max(0, bucket.estimatedCost.input);
                cachedInput = Math.max(0, bucket.estimatedCost.cachedInput);
                output = Math.max(0, bucket.estimatedCost.output);
                image = Math.max(0, bucket.estimatedCost.image);
                audio = Math.max(0, bucket.estimatedCost.audio);
                video = Math.max(0, bucket.estimatedCost.video);
            } else {
                input = Math.max(0, bucket.tokens.input);
                cachedInput = Math.max(0, bucket.tokens.cachedInput);
                output = Math.max(0, bucket.tokens.output);
            }

            const pairs: [string, number][] = metric === "cost"
                ? [
                    ["input", input],
                    ["cachedInput", cachedInput],
                    ["output", output],
                    ["image", image],
                    ["audio", audio],
                    ["video", video],
                ]
                : [
                    ["input", input],
                    ["cachedInput", cachedInput],
                    ["output", output],
                ];
            for (const [key, val] of pairs) {
                if (val > 0) {
                    m.set(key, val);
                    totals.set(key, (totals.get(key) ?? 0) + val);
                }
            }
        } else {
            for (const item of bucket.byModel) {
                if (hasFilter && !selectedModels.has(item.model)) continue;
                const val = Math.max(0, metric === "cost" ? item.estimatedCost.total : item.tokens.total);
                if (val > 0) {
                    const outKey = namedModels.has(item.model) ? item.model : "Other";
                    m.set(outKey, (m.get(outKey) ?? 0) + val);
                    totals.set(outKey, (totals.get(outKey) ?? 0) + val);
                }
            }
        }
        return m;
    });

    let seriesKeys: string[];
    let colorMap: Map<string, string>;
    if (groupBy === "type") {
        const order: readonly string[] = metric === "cost" ? TYPE_KEYS_COST : TYPE_KEYS_TOKENS;
        seriesKeys = order.filter(k => (totals.get(k) ?? 0) > 0);
        colorMap = new Map<string, string>();
        for (const k of seriesKeys) {
            colorMap.set(k, TYPE_COLORS[k] ?? "var(--chart-blue-main)");
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

    const running = new Map<string, number>();
    const cumulativeMaps: Map<string, number>[] = [];
    const bucketTotals: number[] = [];
    const cumulativeTotals: number[] = [];
    let runningTotal = 0;

    for (let i = 0; i < timeline.length; i++) {
        const bMap = bucketMaps[i];
        let bTotal = 0;
        for (const key of seriesKeys) {
            const v = bMap.get(key) ?? 0;
            bTotal += v;
            running.set(key, (running.get(key) ?? 0) + v);
        }
        runningTotal += bTotal;
        bucketTotals.push(bTotal);
        cumulativeTotals.push(runningTotal);
        cumulativeMaps.push(new Map(running));
    }

    return {
        seriesKeys,
        colorMap,
        bucketMaps,
        cumulativeMaps,
        bucketTotals,
        cumulativeTotals,
    };
}

function UsageInferenceRateChart(props: {
    timeline: UsageInferenceTimelineBucket[];
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    metric: InferenceMetric;
    series: InferenceSeriesData;
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
            .value((idx, key) => scaleMetricValue(metric, unit, series.bucketMaps[idx]?.get(key) ?? 0))(indices);

        let yMax = 0;
        for (const layer of stacked) {
            for (const pt of layer) {
                if (pt[1] > yMax) yMax = pt[1];
            }
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForInferenceMax(metric, yMax * 1.1)};
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
            .tickFormat(d => formatInferenceAxisTick(metric, Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, metric === "cost" ? unit.name : "Tokens");

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
                tooltipEl.innerHTML = "";
                const header = document.createElement("div");
                header.style.fontWeight = "700";
                header.style.marginBottom = "4px";
                header.textContent = `${tickFormat(new Date(bucket.timestamp))} – ${tickFormat(new Date(bucket.endTimestamp))}`;
                tooltipEl.append(header);

                const totalRow = document.createElement("div");
                totalRow.style.display = "flex";
                totalRow.style.justifyContent = "space-between";
                totalRow.style.gap = "12px";
                totalRow.style.marginBottom = "6px";
                totalRow.style.fontWeight = "600";
                const totalLabel = document.createElement("span");
                totalLabel.textContent = metric === "cost" ? "Estimated cost" : "Tokens";
                const totalVal = document.createElement("span");
                totalVal.style.fontVariantNumeric = "tabular-nums";
                totalVal.textContent = formatMetricValue(metric, unit, series.bucketTotals[idx] ?? 0);
                totalRow.append(totalLabel, totalVal);
                tooltipEl.append(totalRow);

                const bMap = series.bucketMaps[idx];
                const items = series.seriesKeys
                    .map(k => ({key: k, val: bMap?.get(k) ?? 0}))
                    .filter(it => it.val > 0)
                    .sort((a, b) => b.val - a.val)
                    .slice(0, 8);
                for (const item of items) {
                    const row = document.createElement("div");
                    row.style.display = "flex";
                    row.style.alignItems = "center";
                    row.style.gap = "6px";
                    row.style.fontSize = "12px";
                    const dot = document.createElement("span");
                    dot.style.width = "8px";
                    dot.style.height = "8px";
                    dot.style.borderRadius = "2px";
                    dot.style.background = series.colorMap.get(item.key) ?? "#888";
                    dot.style.flexShrink = "0";
                    const label = document.createElement("span");
                    label.style.flex = "1";
                    label.style.overflow = "hidden";
                    label.style.textOverflow = "ellipsis";
                    label.style.whiteSpace = "nowrap";
                    label.textContent = labelForKey(item.key);
                    const val = document.createElement("span");
                    val.style.fontVariantNumeric = "tabular-nums";
                    val.textContent = formatMetricValue(metric, unit, item.val);
                    row.append(dot, label, val);
                    tooltipEl.append(row);
                }
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
            <div className="usage-empty-chart">
                {metric === "cost" ? "No priced inference usage in this selection." : "No token usage recorded in this selection."}
            </div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

function UsageInferenceCumulativeChart(props: {
    timeline: UsageInferenceTimelineBucket[];
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    metric: InferenceMetric;
    series: InferenceSeriesData;
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
            .value((idx, key) => scaleMetricValue(metric, unit, series.cumulativeMaps[idx]?.get(key) ?? 0))(indices);

        let yMax = 0;
        for (const layer of stacked) {
            for (const pt of layer) {
                if (pt[1] > yMax) yMax = pt[1];
            }
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForInferenceMax(metric, yMax * 1.1)};
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
            const b = timeline[idx];
            return xScale(new Date(b.endTimestamp));
        };

        const areaGen = area<[number, number] & {data: number}>()
            .x(d => xForIdx(d.data))
            .y0(d => yScale(d[0]))
            .y1(d => yScale(d[1]))
            .curve(curveMonotoneX);

        g.selectAll("path.inf-area")
            .data(stacked)
            .join("path")
            .attr("class", "inf-area")
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

        const totalLineGen = line<number>()
            .x((_, i) => xForIdx(i))
            .y(v => yScale(scaleMetricValue(metric, unit, v)))
            .curve(curveMonotoneX);

        if (timeline.length > 1) {
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
            .tickFormat(d => formatInferenceAxisTick(metric, Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, metric === "cost" ? unit.name : "Tokens");

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
                tooltipEl.innerHTML = "";
                const header = document.createElement("div");
                header.style.fontWeight = "700";
                header.style.marginBottom = "4px";
                header.textContent = `Through ${tickFormat(new Date(bucket.endTimestamp))}`;
                tooltipEl.append(header);

                const totalRow = document.createElement("div");
                totalRow.style.display = "flex";
                totalRow.style.justifyContent = "space-between";
                totalRow.style.gap = "12px";
                totalRow.style.marginBottom = "6px";
                totalRow.style.fontWeight = "600";
                const totalLabel = document.createElement("span");
                totalLabel.textContent = metric === "cost" ? "Cumulative est. cost" : "Cumulative tokens";
                const totalVal = document.createElement("span");
                totalVal.style.fontVariantNumeric = "tabular-nums";
                totalVal.textContent = formatMetricValue(metric, unit, series.cumulativeTotals[idx] ?? 0);
                totalRow.append(totalLabel, totalVal);
                tooltipEl.append(totalRow);

                const cMap = series.cumulativeMaps[idx];
                const items = series.seriesKeys
                    .map(k => ({key: k, val: cMap?.get(k) ?? 0}))
                    .filter(it => it.val > 0)
                    .sort((a, b) => b.val - a.val)
                    .slice(0, 8);
                for (const item of items) {
                    const row = document.createElement("div");
                    row.style.display = "flex";
                    row.style.alignItems = "center";
                    row.style.gap = "6px";
                    row.style.fontSize = "12px";
                    const dot = document.createElement("span");
                    dot.style.width = "8px";
                    dot.style.height = "8px";
                    dot.style.borderRadius = "2px";
                    dot.style.background = series.colorMap.get(item.key) ?? "#888";
                    dot.style.flexShrink = "0";
                    const label = document.createElement("span");
                    label.style.flex = "1";
                    label.style.overflow = "hidden";
                    label.style.textOverflow = "ellipsis";
                    label.style.whiteSpace = "nowrap";
                    label.textContent = labelForKey(item.key);
                    const val = document.createElement("span");
                    val.style.fontVariantNumeric = "tabular-nums";
                    val.textContent = formatMetricValue(metric, unit, item.val);
                    row.append(dot, label, val);
                    tooltipEl.append(row);
                }
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
            <div className="usage-empty-chart">
                {metric === "cost" ? "No priced inference usage in this selection." : "No token usage recorded in this selection."}
            </div>
        </div>;
    }

    return <div className={ChartShellStyle}>
        <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />
    </div>;
}

export function UsageInferenceBreakdownSection(props: {
    breakdown: UsageInferenceBreakdown;
    bucketDurationMs: number;
    unit: FrontendAccountingUnit;
    twoColCharts: boolean;
    rateChartWidth: number;
    burnupChartWidth: number;
    rateChartHeight: number;
    burnupChartHeight: number;
    pinnedChildren: string[];
    onClearPinned: () => void;
}): React.ReactNode {
    const {
        breakdown,
        bucketDurationMs,
        unit,
        twoColCharts,
        rateChartWidth,
        burnupChartWidth,
        rateChartHeight,
        burnupChartHeight,
        pinnedChildren,
        onClearPinned,
    } = props;

    const [metric, setMetric] = useState<InferenceMetric>("cost");
    const [groupBy, setGroupBy] = useState<InferenceGroupBy>("model");
    const [selectedModels, setSelectedModels] = useState<string[]>([]);
    const [highlightedKey, setHighlightedKey] = useState<string | null>(null);
    const [store] = useState(() => new UcxTableStore());

    const modelByKey = useMemo(
        () => new Map<string, UsageInferenceModelSummary>(breakdown.models.map(m => [m.model, m])),
        [breakdown.models],
    );

    const activeSelectedModels = useMemo(
        () => selectedModels.filter(m => modelByKey.has(m)),
        [selectedModels, modelByKey],
    );

    const selectedModelSet = useMemo(
        () => new Set(activeSelectedModels),
        [activeSelectedModels],
    );

    const toggleModelFilter = useCallback((modelName: string) => {
        setHighlightedKey(null);
        setSelectedModels(prev => {
            if (prev.includes(modelName)) {
                return prev.filter(m => m !== modelName);
            }
            return [...prev, modelName];
        });
    }, []);

    const modelTitleMap = useMemo(() => {
        const m = new Map<string, string>();
        for (const item of breakdown.models) {
            m.set(item.model, item.title || item.model);
        }
        return m;
    }, [breakdown.models]);

    const labelForKey = useCallback((key: string): string => {
        if (groupBy === "type") {
            return TYPE_LABELS[key] ?? key;
        }
        if (key === "Other") return "Other";
        return modelTitleMap.get(key) ?? key;
    }, [groupBy, modelTitleMap]);

    const series = useMemo(
        () => buildInferenceSeriesData(breakdown.timeline, metric, groupBy, selectedModelSet),
        [breakdown.timeline, metric, groupBy, selectedModelSet],
    );

    const modelColorMap = useMemo(() => {
        if (groupBy === "model") return series.colorMap;
        const modelSeries = buildInferenceSeriesData(breakdown.timeline, metric, "model", selectedModelSet);
        return modelSeries.colorMap;
    }, [breakdown.timeline, metric, groupBy, selectedModelSet, series.colorMap]);

    const hasImages = breakdown.imageCount > 0 || breakdown.imagePixels > 0;
    const hasAudio = breakdown.audioMilliseconds > 0;
    const hasVideo = breakdown.videoSeconds > 0;

    const columns = useMemo((): TableColumn[] => {
        const cols: TableColumn[] = [
            {key: "inf.filter", label: "", jsonPath: "filter", sortType: TableColumnSortType.Number, alignment: "left"},
            {key: "inf.model", label: "Model", jsonPath: "model", sortType: TableColumnSortType.Text, alignment: "left"},
            {key: "inf.cost", label: "Est. cost", jsonPath: "cost", sortType: TableColumnSortType.Number, alignment: "right"},
            {key: "inf.share", label: "Share", jsonPath: "share", sortType: TableColumnSortType.Number, alignment: "right"},
            {key: "inf.input", label: "Uncached input", jsonPath: "input", sortType: TableColumnSortType.Number, alignment: "right"},
            {key: "inf.cached", label: "Cached input", jsonPath: "cached", sortType: TableColumnSortType.Number, alignment: "right"},
            {key: "inf.output", label: "Output", jsonPath: "output", sortType: TableColumnSortType.Number, alignment: "right"},
            {key: "inf.tokens", label: "Total tokens", jsonPath: "tokens", sortType: TableColumnSortType.Number, alignment: "right"},
        ];
        if (hasImages) {
            cols.push({key: "inf.images", label: "Images", jsonPath: "images", sortType: TableColumnSortType.Number, alignment: "right"});
        }
        if (hasAudio) {
            cols.push({key: "inf.audio", label: "Audio", jsonPath: "audio", sortType: TableColumnSortType.Number, alignment: "right"});
        }
        if (hasVideo) {
            cols.push({key: "inf.video", label: "Video", jsonPath: "video", sortType: TableColumnSortType.Number, alignment: "right"});
        }
        return cols;
    }, [hasImages, hasAudio, hasVideo]);

    const columnWidths = useMemo(() => {
        const widths: Record<string, number> = {
            "inf.filter": 44,
            "inf.model": 240,
            "inf.cost": 140,
            "inf.share": 85,
            "inf.input": 135,
            "inf.cached": 135,
            "inf.output": 135,
            "inf.tokens": 140,
        };
        if (hasImages) widths["inf.images"] = 100;
        if (hasAudio) widths["inf.audio"] = 100;
        if (hasVideo) widths["inf.video"] = 100;
        return widths;
    }, [hasImages, hasAudio, hasVideo]);

    const totalEstCost = breakdown.estimatedCost.total;

    const tableRows = useMemo((): TableRow[] => breakdown.models.map(m => {
        const share = totalEstCost > 0 ? (m.estimatedCost.total / totalEstCost) * 100 : 0;
        const isFiltered = selectedModelSet.has(m.model);
        const cells: string[] = [
            isFiltered ? "1" : "0",
            m.title || m.model,
            `${m.priced ? m.estimatedCost.total : -1}`,
            `${share.toFixed(2)}`,
            `${m.tokens.input}`,
            `${m.tokens.cachedInput}`,
            `${m.tokens.output}`,
            `${m.tokens.total}`,
        ];
        if (hasImages) cells.push(`${m.imageCount}`);
        if (hasAudio) cells.push(`${m.audioMilliseconds}`);
        if (hasVideo) cells.push(`${m.videoSeconds}`);
        return {
            key: m.model,
            group: "",
            cells,
        };
    }), [breakdown.models, selectedModelSet, totalEstCost, hasImages, hasAudio, hasVideo]);

    useLayoutEffect(() => {
        store.apply({
            tableId: "usage-v2-inference-models",
            revision: 0,
            snapshot: true,
            columns,
            upserts: tableRows,
            removed: [],
        });
    }, [store, columns, tableRows]);

    const renderCell = useCallback((context: UcxTableCellRenderContext): React.ReactNode => {
        const m = modelByKey.get(context.row.key);
        if (!m) return context.cell;

        if (context.column.key === "inf.filter") {
            const isSelected = selectedModelSet.has(m.model);
            const label = isSelected ? "Remove model from chart filter" : "Filter charts to this model";
            return <TooltipV2 tooltip={label}>
                <button
                    type="button"
                    className={UcxTableIconButtonClass}
                    aria-pressed={isSelected}
                    aria-label={label}
                    onClick={ev => {
                        ev.stopPropagation();
                        toggleModelFilter(m.model);
                    }}
                    onKeyDown={ev => {
                        if (ev.key === "Enter" || ev.key === " ") ev.stopPropagation();
                    }}
                >
                    <Icon name={isSelected ? "heroMagnifyingGlassMinus" : "heroMagnifyingGlassPlus"} size={16} />
                </button>
            </TooltipV2>;
        }
        if (context.column.key === "inf.model") {
            const dotColor = modelColorMap.get(m.model) ?? "var(--textSecondary)";
            const showRawName = m.title !== "" && m.title !== m.model;
            return <div className="inf-model-cell">
                <span className="inf-model-dot" style={{background: dotColor}} />
                <div className="inf-model-text">
                    <span className="inf-model-title">{m.title || m.model}</span>
                    {showRawName ? <span className="inf-model-sub">{m.model}</span> : null}
                </div>
            </div>;
        }
        if (context.column.key === "inf.cost") {
            if (!m.priced) {
                return <TooltipV2 tooltip="Model is no longer in the active catalog; current price unavailable.">
                    <span className="inf-num inf-muted">Unpriced</span>
                </TooltipV2>;
            }
            return <span className="inf-num">{usageFormatValue(unit, m.estimatedCost.total)}</span>;
        }
        if (context.column.key === "inf.share") {
            if (!m.priced || totalEstCost <= 0) {
                return <span className="inf-num inf-muted">—</span>;
            }
            const pct = (m.estimatedCost.total / totalEstCost) * 100;
            return <span className="inf-num">{pct.toFixed(1)}%</span>;
        }
        if (context.column.key === "inf.input") {
            return <span className="inf-num">{m.tokens.input > 0 ? formatNumber(m.tokens.input) : "—"}</span>;
        }
        if (context.column.key === "inf.cached") {
            return <span className="inf-num">{m.tokens.cachedInput > 0 ? formatNumber(m.tokens.cachedInput) : "—"}</span>;
        }
        if (context.column.key === "inf.output") {
            return <span className="inf-num">{m.tokens.output > 0 ? formatNumber(m.tokens.output) : "—"}</span>;
        }
        if (context.column.key === "inf.tokens") {
            return <span className="inf-num">{m.tokens.total > 0 ? formatNumber(m.tokens.total) : "—"}</span>;
        }
        if (context.column.key === "inf.images") {
            if (m.imageCount <= 0 && m.imagePixels <= 0) {
                return <span className="inf-num inf-muted">—</span>;
            }
            const mp = m.imagePixels / 1_000_000;
            return <TooltipV2 tooltip={`${formatAmount(mp)} megapixels`}>
                <span className="inf-num">{formatNumber(m.imageCount)}</span>
            </TooltipV2>;
        }
        if (context.column.key === "inf.audio") {
            if (m.audioMilliseconds <= 0) {
                return <span className="inf-num inf-muted">—</span>;
            }
            return <span className="inf-num">{formatDurationSeconds(m.audioMilliseconds / 1000)}</span>;
        }
        if (context.column.key === "inf.video") {
            if (m.videoSeconds <= 0) {
                return <span className="inf-num inf-muted">—</span>;
            }
            return <span className="inf-num">{formatDurationSeconds(m.videoSeconds)}</span>;
        }
        return context.cell;
    }, [modelByKey, selectedModelSet, toggleModelFilter, modelColorMap, unit, totalEstCost]);

    const noteParts: string[] = [];
    if (breakdown.coFundedWorkspaceCount > 0) {
        noteParts.push(
            `Token and media counts include proportional estimates for ${formatNumber(breakdown.coFundedWorkspaceCount)} co-funded sub-project${breakdown.coFundedWorkspaceCount === 1 ? "" : "s"} weighted by daily billed spend.`,
        );
    } else {
        noteParts.push("Token and media counts are exact from provider logs.");
    }
    if (breakdown.hasVideoEstimate) {
        noteParts.push("Costs are estimated using current model prices (video uses each model's default tier price); refer to the charts above for exact billed credits.");
    } else {
        noteParts.push("Costs are estimated using current model prices; refer to the charts above for exact billed credits.");
    }
    if (breakdown.hasUnpricedModels) {
        noteParts.push("Models removed from the catalog cannot be priced.");
    }
    if (pinnedChildren.length > 0) {
        noteParts.push(`Scoped to ${formatNumber(pinnedChildren.length)} pinned sub-project${pinnedChildren.length === 1 ? "" : "s"}.`);
    }

    const rateTitle = metric === "cost" ? "Estimated cost per period" : "Tokens per period";
    const cumulativeTitle = metric === "cost" ? "Cumulative estimated cost" : "Cumulative tokens";
    const hasModels = breakdown.models.length > 0;
    const tableHeight = Math.min(8 * 44 + 42, Math.max(1, breakdown.models.length) * 44 + 42);

    return <section className={`usage-section ${InferenceStyle}`} aria-label="Model and token breakdown">
        <div className="usage-section-head">
            <div className="inf-title-wrap">
                <h3>Model &amp; token breakdown</h3>
                {breakdown.available && hasModels ? (
                    <TooltipV2 tooltip={noteParts.join(" ")}>
                        <span className="inf-info-trigger" aria-label="Estimation details">
                            <Icon name="heroInformationCircle" size={16} />
                        </span>
                    </TooltipV2>
                ) : null}
            </div>
            <div className="inf-controls">
                {activeSelectedModels.length > 0 ? (
                    <Button color="ghost" onClick={() => setSelectedModels([])}>
                        Clear model filter ({activeSelectedModels.length})
                    </Button>
                ) : null}
                {pinnedChildren.length > 0 ? (
                    <Button color="ghost" onClick={onClearPinned}>
                        Clear pinned ({pinnedChildren.length})
                    </Button>
                ) : null}
                {breakdown.available && hasModels ? <>
                    <div className="usage-mode-row" role="group" aria-label="Inference metric">
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={metric === "cost"}
                            onClick={() => {
                                setMetric("cost");
                                setHighlightedKey(null);
                            }}
                        >
                            Estimated cost
                        </button>
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={metric === "tokens"}
                            onClick={() => {
                                setMetric("tokens");
                                setHighlightedKey(null);
                            }}
                        >
                            Tokens
                        </button>
                    </div>
                    <span className="inf-divider" aria-hidden="true" />
                    <div className="usage-mode-row" role="group" aria-label="Inference grouping">
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={groupBy === "model"}
                            onClick={() => {
                                setGroupBy("model");
                                setHighlightedKey(null);
                            }}
                        >
                            By model
                        </button>
                        <button
                            type="button"
                            className="usage-mode-button"
                            aria-pressed={groupBy === "type"}
                            onClick={() => {
                                setGroupBy("type");
                                setHighlightedKey(null);
                            }}
                        >
                            By type
                        </button>
                    </div>
                </> : null}
            </div>
        </div>

        {!breakdown.available ? (
            <div className="inf-empty">
                Model and token breakdown is currently unavailable from the inference provider.
            </div>
        ) : !hasModels ? (
            <div className="inf-empty">
                No model-level inference usage recorded in this period.
            </div>
        ) : <>
            <div
                className="usage-chart-grid"
                style={{gridTemplateColumns: twoColCharts ? "repeat(2, minmax(0, 1fr))" : "minmax(0, 1fr)"}}
            >
                <div className="usage-chart-col">
                    <div className="inf-subhead">
                        <h4>{rateTitle}</h4>
                        <TooltipV2 tooltip="Expand chart">
                            <button
                                type="button"
                                className={UcxTableIconButtonClass}
                                aria-label={`Expand ${rateTitle.toLowerCase()} chart`}
                                onClick={() => openUsageChartDialog(rateTitle, (w, h) => (
                                    <UsageInferenceRateChart
                                        timeline={breakdown.timeline}
                                        bucketDurationMs={bucketDurationMs}
                                        unit={unit}
                                        metric={metric}
                                        series={series}
                                        width={w}
                                        height={h}
                                        highlightedKey={null}
                                        labelForKey={labelForKey}
                                    />
                                ))}
                            >
                                <Icon name="heroArrowsPointingOut" size={15} />
                            </button>
                        </TooltipV2>
                    </div>
                    <UsageInferenceRateChart
                        timeline={breakdown.timeline}
                        bucketDurationMs={bucketDurationMs}
                        unit={unit}
                        metric={metric}
                        series={series}
                        width={rateChartWidth}
                        height={rateChartHeight}
                        highlightedKey={highlightedKey}
                        labelForKey={labelForKey}
                    />
                </div>

                <div className="usage-chart-col">
                    <div className="inf-subhead">
                        <h4>{cumulativeTitle}</h4>
                        <TooltipV2 tooltip="Expand chart">
                            <button
                                type="button"
                                className={UcxTableIconButtonClass}
                                aria-label={`Expand ${cumulativeTitle.toLowerCase()} chart`}
                                onClick={() => openUsageChartDialog(cumulativeTitle, (w, h) => (
                                    <UsageInferenceCumulativeChart
                                        timeline={breakdown.timeline}
                                        bucketDurationMs={bucketDurationMs}
                                        unit={unit}
                                        metric={metric}
                                        series={series}
                                        width={w}
                                        height={h}
                                        highlightedKey={null}
                                        labelForKey={labelForKey}
                                    />
                                ))}
                            >
                                <Icon name="heroArrowsPointingOut" size={15} />
                            </button>
                        </TooltipV2>
                    </div>
                    <UsageInferenceCumulativeChart
                        timeline={breakdown.timeline}
                        bucketDurationMs={bucketDurationMs}
                        unit={unit}
                        metric={metric}
                        series={series}
                        width={burnupChartWidth}
                        height={burnupChartHeight}
                        highlightedKey={highlightedKey}
                        labelForKey={labelForKey}
                    />
                </div>
            </div>

            {series.seriesKeys.length > 0 ? (
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
                <div className="inf-table-head">
                    <h4>Models</h4>
                    <span className="inf-table-count">
                        {formatNumber(breakdown.models.length)} model{breakdown.models.length === 1 ? "" : "s"}
                    </span>
                </div>
                <div className="inf-table-box" style={{height: tableHeight}}>
                    <UcxStreamedTable
                        tableId="usage-v2-inference-models"
                        store={store}
                        showControls={false}
                        density="compact"
                        rowHeight={44}
                        columnWidths={columnWidths}
                        renderCell={renderCell}
                        onRowActivated={() => void 0}
                        emptyMessage="No model usage recorded."
                    />
                </div>
            </Flex>
        </>}
    </section>;
}
