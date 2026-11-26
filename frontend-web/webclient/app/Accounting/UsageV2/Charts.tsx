import * as React from "react";
import {useEffect, useId, useMemo, useRef, useState} from "react";
import type ReactModal from "react-modal";
import {scaleBand, scaleLinear, scaleTime} from "d3-scale";
import {area, curveMonotoneX, curveStepAfter, line, stack} from "d3-shape";
import {axisBottom, axisLeft} from "d3-axis";
import {pointer, select} from "d3-selection";
import {useD3} from "@/Utilities/d3";
import {IconButton} from "@/ui-components/IconButton";
import {dialogStore} from "@/Dialog/DialogStore";
import {doNothing} from "@/UtilityFunctions";
import {colorNames} from "@/Accounting/Diagrams";
import {
    AccountingModel,
    FrontendAccountingUnit,
} from "@/Accounting/index";
import {formatAmount} from "@/Accounting/Allocations/Model";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {injectStyle} from "@/Unstyled";
import {
    UsageChildRow,
    UsageDetailResponse,
    UsageHistogramBucket,
    UsageLifecycleEvent,
    UsageSparklinePoint,
    UsageTimelineBucket,
    usageChildScopeMetrics,
    usageDropRiskDays,
    usageFormatShortDate,
    usageFormatPercent,
    usageFormatValue,
} from "./Api";

export const ChartShellStyle = injectStyle("usage-v2-charts", k => `
    ${k} .usage-legend {
        display: flex;
        flex-wrap: wrap;
        gap: 8px 16px;
        margin-top: 8px;
        padding-left: 52px;
        font-size: 12px;
        color: var(--textSecondary);
    }

    ${k} .usage-legend-item {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        cursor: pointer;
        user-select: none;
        padding: 2px 6px;
        border-radius: 4px;
        transition: background 120ms ease, opacity 120ms ease;
    }

    ${k} .usage-legend-item:hover {
        background: var(--rowHover);
        color: var(--textPrimary);
    }

    ${k} .usage-legend-item[data-dimmed="true"] {
        opacity: 0.4;
    }

    ${k} .usage-legend-swatch {
        width: 10px;
        height: 10px;
        border-radius: 2px;
        flex-shrink: 0;
    }

    ${k} .usage-legend-line {
        width: 16px;
        height: 0;
        border-top: 2px solid currentColor;
        flex-shrink: 0;
    }

    ${k} .usage-legend-line[data-dashed="true"] {
        border-top-style: dashed;
    }

    ${k} .usage-empty-chart {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 180px;
        color: var(--textSecondary);
        font-size: 13px;
    }
`);

const HistogramStyle = injectStyle("usage-v2-histogram", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 8px;
    }

    ${k} .hist-bars {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
        align-items: flex-end;
    }

    ${k} .hist-bucket {
        width: 75px;
        max-width: 75px;
        flex: 0 0 auto;
        display: flex;
        flex-direction: column;
        align-items: stretch;
        gap: 4px;
        padding: 6px 4px;
        border: 0;
        border-bottom: 2px solid transparent;
        border-radius: 6px 6px 0 0;
        background: transparent;
        cursor: pointer;
        text-align: left;
        color: var(--textPrimary);
        transition: background 120ms ease, border-color 120ms ease;
    }

    ${k} .hist-bucket:hover {
        background: var(--rowHover);
    }

    ${k} .hist-bucket[data-active="true"] {
        background: color-mix(in srgb, var(--textPrimary) 8%, transparent);
        border-bottom-color: var(--chart-blue-main);
    }

    ${k} .hist-bar-track {
        height: 48px;
        display: flex;
        align-items: flex-end;
    }

    ${k} .hist-bar-fill {
        width: 100%;
        border-radius: 2px 2px 0 0;
        min-height: 2px;
        transition: height 150ms ease;
    }

    ${k} .hist-bucket-count {
        font-size: 15px;
        font-weight: 700;
        font-variant-numeric: tabular-nums;
    }

    ${k} .hist-bucket-label {
        font-size: 11px;
        color: var(--textSecondary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
`);

const ChartTooltipStyle = injectStyle("usage-v2-chart-tooltip", k => `
    ${k} {
        position: fixed;
        top: 0;
        left: 0;
        z-index: 90000;
        box-sizing: border-box;
        min-width: 160px;
        max-width: min(340px, calc(100vw - 24px));
        padding: 8px 11px;
        border-radius: 6px;
        background: var(--backgroundCard);
        color: var(--textPrimary);
        border: 1px solid var(--borderColor);
        box-shadow: 0 6px 20px rgba(0, 0, 0, 0.28);
        font-size: 12px;
        line-height: 1.45;
        text-align: left;
        pointer-events: none;
        opacity: 0;
        visibility: hidden;
        transition: opacity 90ms ease;
    }

    ${k}[data-visible="true"] {
        opacity: 1;
        visibility: visible;
    }
`);

let sharedChartTooltipHost: HTMLDivElement | null = null;

function getUsageChartTooltipHost(): HTMLDivElement {
    if (sharedChartTooltipHost && document.body.contains(sharedChartTooltipHost)) {
        return sharedChartTooltipHost;
    }
    const el = document.createElement("div");
    el.className = ChartTooltipStyle;
    document.body.appendChild(el);
    sharedChartTooltipHost = el;
    return el;
}

function hideUsageChartTooltip() {
    if (sharedChartTooltipHost) {
        sharedChartTooltipHost.removeAttribute("data-visible");
    }
}

function appendUsageTooltipText(row: HTMLElement, label: string, value: string, emphasized = true): HTMLSpanElement {
    const name = document.createElement("span");
    const amount = document.createElement("span");
    name.textContent = label;
    amount.textContent = value;
    amount.style.fontVariantNumeric = "tabular-nums";
    if (emphasized) {
        name.style.color = "var(--textSecondary)";
        amount.style.fontWeight = "600";
    }
    row.append(name, amount);
    return name;
}

export function appendUsageSeriesTooltip(content: HTMLElement, props: {
    heading: string;
    totalLabel: string;
    total: number;
    keys: readonly string[];
    values: ReadonlyMap<string, number> | undefined;
    colors: ReadonlyMap<string, string>;
    labelForKey: (key: string) => string;
    formatValue: (value: number) => string;
}) {
    const heading = document.createElement("div");
    heading.style.cssText = "font-weight:700;margin-bottom:4px";
    heading.textContent = props.heading;
    const total = document.createElement("div");
    total.style.cssText = "display:flex;justify-content:space-between;gap:12px;margin-bottom:6px;font-weight:600";
    appendUsageTooltipText(total, props.totalLabel, props.formatValue(props.total), false);
    content.replaceChildren(heading, total);

    const items = props.keys
        .map(key => ({key, value: props.values?.get(key) ?? 0}))
        .filter(item => item.value > 0)
        .sort((a, b) => b.value - a.value)
        .slice(0, 8);
    for (const item of items) {
        const row = document.createElement("div");
        row.style.cssText = "display:flex;align-items:center;gap:6px;font-size:12px";
        const dot = document.createElement("span");
        dot.style.cssText = "width:8px;height:8px;border-radius:2px;flex-shrink:0";
        dot.style.background = props.colors.get(item.key) ?? "#888";
        row.append(dot);
        const label = appendUsageTooltipText(row, props.labelForKey(item.key), props.formatValue(item.value), false);
        label.style.cssText = "flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap";
        content.append(row);
    }
}

export function createUsageChartTooltip(content: HTMLElement): {
    moveListener: (ev: MouseEvent) => void;
    leaveListener: () => void;
} {
    const moveListener = (ev: MouseEvent) => {
        const host = getUsageChartTooltipHost();
        if (host.firstChild !== content) {
            host.replaceChildren(content);
        }
        host.setAttribute("data-visible", "true");
        const rect = host.getBoundingClientRect();
        const pad = 12;
        const offset = 14;
        let x = ev.clientX + offset;
        let y = ev.clientY - rect.height / 2;
        if (x + rect.width > window.innerWidth - pad) {
            x = ev.clientX - rect.width - offset;
        }
        if (x < pad) {
            x = pad;
        }
        if (y + rect.height > window.innerHeight - pad) {
            y = window.innerHeight - rect.height - pad;
        }
        if (y < pad) {
            y = pad;
        }
        host.style.transform = `translate3d(${Math.round(x)}px, ${Math.round(y)}px, 0)`;
    };

    const leaveListener = () => {
        if (!sharedChartTooltipHost) return;
        if (sharedChartTooltipHost.firstChild === content) {
            sharedChartTooltipHost.removeAttribute("data-visible");
        }
    };

    return {moveListener, leaveListener};
}

export function formatAxisTick(value: number): string {
    return formatAmount(value);
}

export function leftMarginForMax(yMax: number): number {
    const sample = formatAmount(Math.ceil(yMax));
    return Math.max(56, sample.length * 7 + 18);
}

export function formatCompactAxisNumber(value: number): string {
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
            const precision = scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2;
            const formatted = formatNumber(scaled, {precision, removeTrailingZeros: true});
            return `${sign}${formatted}${suffix}`;
        }
    }
    return formatAmount(value);
}

export function leftMarginForCompactMax(yMax: number): number {
    const sample = formatCompactAxisNumber(Math.ceil(yMax));
    return Math.max(52, sample.length * 7 + 18);
}

export function buildUsageSeriesTotals(seriesKeys: readonly string[], bucketMaps: readonly ReadonlyMap<string, number>[]) {
    const running = new Map<string, number>();
    const cumulativeMaps: Map<string, number>[] = [];
    const bucketTotals: number[] = [];
    const cumulativeTotals: number[] = [];
    let runningTotal = 0;

    for (const bucket of bucketMaps) {
        let bucketTotal = 0;
        for (const key of seriesKeys) {
            const value = bucket.get(key) ?? 0;
            bucketTotal += value;
            running.set(key, (running.get(key) ?? 0) + value);
        }
        runningTotal += bucketTotal;
        bucketTotals.push(bucketTotal);
        cumulativeTotals.push(runningTotal);
        cumulativeMaps.push(new Map(running));
    }
    return {cumulativeMaps, bucketTotals, cumulativeTotals};
}

export function formatBucketTick(bucketDurationMs: number): (date: Date) => string {
    const dayOptions: Intl.DateTimeFormatOptions = {month: "short", day: "numeric"};
    if (bucketDurationMs < 24 * 60 * 60 * 1000) {
        return new Intl.DateTimeFormat(undefined, {...dayOptions, hour: "numeric", minute: "2-digit"}).format;
    }
    if (bucketDurationMs <= 7 * 24 * 60 * 60 * 1000) {
        return new Intl.DateTimeFormat(undefined, dayOptions).format;
    }
    return new Intl.DateTimeFormat(undefined, {month: "short", year: "numeric"}).format;
}

export function buildSeriesColorMap(keys: string[]): Map<string, string> {
    const map = new Map<string, string>();
    const hasLocal = keys.includes("");
    let idx = hasLocal ? 1 : 0;
    for (const key of keys) {
        if (key === "Other") {
            map.set(key, "var(--textSecondary)");
        } else if (key === "") {
            map.set(key, "var(--chart-blue-main)");
        } else {
            map.set(key, colorNames[idx % colorNames.length]);
            idx++;
        }
    }
    return map;
}

export function UsageSparkline(props: {
    points: UsageSparklinePoint[];
    accountingModel: AccountingModel;
    width?: number;
    height?: number;
}): React.ReactNode {
    const width = props.width ?? 120;
    const height = props.height ?? 28;
    const points = props.points;
    const baselineY = height - 0.5;
    const svgStyle: React.CSSProperties = {
        display: "block",
        width: "100%",
        height: `${height}px`,
    };

    if (points.length === 0) {
        return <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" style={svgStyle}>
            <line
                x1={0}
                x2={width}
                y1={baselineY}
                y2={baselineY}
                stroke="var(--borderColor)"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
                shapeRendering="crispEdges"
            />
        </svg>;
    }

    const values = points.map(p => Math.max(0, p.value));
    const quotas = points.map(p => Math.max(0, p.quota));
    const maxVal = Math.max(
        1,
        ...values,
        ...(props.accountingModel === "CAPACITY" ? quotas : []),
    );

    if (props.accountingModel === "CONSUMPTION") {
        const count = points.length;
        const barW = Math.max(2, Math.min(8, (width - Math.max(0, count - 1) * 1.5) / count));
        const step = count > 1 ? (width - barW) / (count - 1) : 0;
        const plotH = Math.max(2, baselineY - 3);
        return <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" style={svgStyle}>
            <line
                x1={0}
                x2={width}
                y1={baselineY}
                y2={baselineY}
                stroke="var(--borderColor)"
                strokeWidth={1}
                vectorEffect="non-scaling-stroke"
                shapeRendering="crispEdges"
            />
            {points.map((pt, i) => {
                const h = pt.value > 0 ? Math.max(2, (Math.max(0, pt.value) / maxVal) * plotH) : 0;
                const x = i * step;
                if (h <= 0) return null;
                return <rect
                    key={pt.timestamp}
                    x={x}
                    y={baselineY - h}
                    width={barW}
                    height={h}
                    fill="var(--chart-blue-main)"
                    opacity={0.85}
                />;
            })}
        </svg>;
    }

    const stepX = points.length > 1 ? width / (points.length - 1) : width;
    const plotH = Math.max(2, baselineY - 4);
    const usageCoords = points.map((pt, i) => {
        const x = i * stepX;
        const y = baselineY - (Math.max(0, pt.value) / maxVal) * plotH;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    const areaPath = `M 0,${baselineY} L ${usageCoords.join(" L ")} L ${width},${baselineY} Z`;
    const linePath = `M ${usageCoords.join(" L ")}`;
    const quotaCoords = points.map((pt, i) => {
        const x = i * stepX;
        const y = baselineY - (Math.max(0, pt.quota) / maxVal) * plotH;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    });
    const quotaPath = `M ${quotaCoords.join(" L ")}`;
    const hasExcess = points.some(pt => pt.value > pt.quota && pt.value > 0);

    return <svg viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none" style={svgStyle}>
        <path d={areaPath} fill={hasExcess ? "var(--chart-red-main)" : "var(--chart-blue-main)"} opacity={0.24} />
        <path
            d={linePath}
            fill="none"
            stroke={hasExcess ? "var(--chart-red-main)" : "var(--chart-blue-main)"}
            strokeWidth={1.5}
            vectorEffect="non-scaling-stroke"
        />
        <path
            d={quotaPath}
            fill="none"
            stroke="var(--textSecondary)"
            strokeWidth={1}
            strokeDasharray="3,2"
            vectorEffect="non-scaling-stroke"
        />
        <line
            x1={0}
            x2={width}
            y1={baselineY}
            y2={baselineY}
            stroke="var(--borderColor)"
            strokeWidth={1}
            vectorEffect="non-scaling-stroke"
            shapeRendering="crispEdges"
        />
    </svg>;
}

export type ConsumptionSeriesMode = "total" | "product" | "child";

export function UsageConsumptionRateChart(props: {
    detail: UsageDetailResponse;
    unit: FrontendAccountingUnit;
    width: number;
    height: number;
    seriesMode: ConsumptionSeriesMode;
    pinnedChildren: string[];
    highlightedKey: string | null;
    onHighlightKey: (key: string | null) => void;
    labelForKey: (key: string, mode: ConsumptionSeriesMode) => string;
}): React.ReactNode {
    const {
        detail,
        unit,
        width,
        height,
        seriesMode,
        pinnedChildren,
        highlightedKey,
        onHighlightKey,
        labelForKey,
    } = props;
    const timeline = detail.timeline;
    const pinnedSet = useMemo(() => new Set(pinnedChildren), [pinnedChildren]);

    const {seriesKeys, colorMap, bucketMaps} = useMemo(() => {
        const totals = new Map<string, number>();
        const maps: Map<string, number>[] = timeline.map(bucket => {
            const m = new Map<string, number>();
            if (seriesMode === "total") {
                if (pinnedSet.size === 0) {
                    const v = Math.max(0, bucket.usageChange);
                    m.set("__total__", v);
                    totals.set("__total__", (totals.get("__total__") ?? 0) + v);
                } else {
                    let pinnedSum = 0;
                    for (const item of bucket.byChild) {
                        if (pinnedSet.has(item.key)) {
                            const v = Math.max(0, item.change);
                            m.set(item.key, (m.get(item.key) ?? 0) + v);
                            totals.set(item.key, (totals.get(item.key) ?? 0) + v);
                            pinnedSum += v;
                        }
                    }
                    const rest = Math.max(0, bucket.usageChange - pinnedSum);
                    if (rest > 0) {
                        m.set("Other", rest);
                        totals.set("Other", (totals.get("Other") ?? 0) + rest);
                    }
                }
            } else if (seriesMode === "product") {
                for (const item of bucket.byProduct) {
                    const v = Math.max(0, item.change);
                    if (v > 0) {
                        m.set(item.key, (m.get(item.key) ?? 0) + v);
                        totals.set(item.key, (totals.get(item.key) ?? 0) + v);
                    }
                }
            } else {
                for (const item of bucket.byChild) {
                    const v = Math.max(0, item.change);
                    if (v > 0) {
                        m.set(item.key, (m.get(item.key) ?? 0) + v);
                        totals.set(item.key, (totals.get(item.key) ?? 0) + v);
                    }
                }
            }
            return m;
        });

        const ordered = [...totals.entries()]
            .filter(([, total]) => total > 0)
            .sort((a, b) => {
                if (a[0] === "Other") return 1;
                if (b[0] === "Other") return -1;
                if (a[0] === "") return -1;
                if (b[0] === "") return 1;
                return b[1] - a[1];
            })
            .map(([k]) => k);

        const colors = buildSeriesColorMap(ordered);
        if (ordered.includes("__total__")) {
            colors.set("__total__", "var(--chart-blue-main)");
        }
        return {seriesKeys: ordered, colorMap: colors, bucketMaps: maps};
    }, [timeline, seriesMode, pinnedSet]);

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || width <= 80) return;

        const keysToStack = seriesKeys.length > 0 ? seriesKeys : ["__total__"];
        const indices = timeline.map((_, i) => i);
        const stacked = stack<number>()
            .keys(keysToStack)
            .value((idx, key) => (bucketMaps[idx]?.get(key) ?? 0) * unit.balanceFactor)(indices);

        let yMax = 0;
        for (const layer of stacked) {
            for (const pt of layer) {
                if (pt[1] > yMax) yMax = pt[1];
            }
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 20, bottom: 40, left: leftMarginForMax(yMax * 1.1)};
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

        const tickFormat = formatBucketTick(detail.bucketDurationMs);

        g.selectAll("g.layer")
            .data(stacked)
            .join("g")
            .attr("class", "layer")
            .attr("fill", d => colorMap.get(d.key) ?? "var(--chart-blue-main)")
            .attr("opacity", d => {
                if (highlightedKey === null || d.key === "__total__") return 0.92;
                return d.key === highlightedKey ? 1 : 0.25;
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

        drawLifecycleMarkers(g, detail.events, xScale, innerH, minTs, maxTs);

        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatAxisTick(Number(d)));

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

        let lastHoverKey = "";
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

            const hoverEvents = collectHoverLifecycleEvents(
                detail.events,
                bucket.timestamp,
                bucket.endTimestamp,
                idx === timeline.length - 1,
                xScale,
                mx,
                minTs,
                maxTs,
            );
            const hoverKey = `${idx}:${hoverEvents.map(e => `${e.timestamp}-${e.type}`).join(",")}`;

            if (hoverKey !== lastHoverKey) {
                lastHoverKey = hoverKey;
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
                appendUsageTooltipText(totalRow, "Period consumption", usageFormatValue(unit, bucket.usageChange), false);
                tooltipEl.append(totalRow);

                if (seriesKeys.length > 0 && !(seriesKeys.length === 1 && seriesKeys[0] === "__total__")) {
                    const bMap = bucketMaps[idx];
                    const items = seriesKeys
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
                        dot.style.background = colorMap.get(item.key) ?? "#888";
                        dot.style.flexShrink = "0";
                        const label = document.createElement("span");
                        label.style.flex = "1";
                        label.style.overflow = "hidden";
                        label.style.textOverflow = "ellipsis";
                        label.style.whiteSpace = "nowrap";
                        label.textContent = labelForKey(item.key, seriesMode);
                        const val = document.createElement("span");
                        val.style.fontVariantNumeric = "tabular-nums";
                        val.textContent = usageFormatValue(unit, item.val);
                        row.append(dot, label, val);
                        tooltipEl.append(row);
                    }
                }

                if (bucket.flowShift !== 0) {
                    const note = document.createElement("div");
                    note.style.marginTop = "6px";
                    note.style.paddingTop = "4px";
                    note.style.borderTop = "1px solid var(--borderColor)";
                    note.style.fontSize = "11px";
                    note.style.color = "var(--textSecondary)";
                    const sign = bucket.flowShift > 0 ? "+" : "";
                    note.textContent = `Rebalanced between funding sources: ${sign}${usageFormatValue(unit, bucket.flowShift)}`;
                    tooltipEl.append(note);
                }

                appendLifecycleEventsToTooltip(tooltipEl, hoverEvents, unit);
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            hoverLine.attr("opacity", 0);
            lastHoverKey = "";
            tooltip.leaveListener();
        });
    }, [timeline, width, height, unit, seriesKeys, bucketMaps, colorMap, highlightedKey, detail.events, detail.bucketDurationMs, seriesMode, labelForKey]);

    const showLegend = seriesKeys.length > 0 && seriesKeys.length <= 5 && !(seriesKeys.length === 1 && seriesKeys[0] === "__total__");

    return <div className={ChartShellStyle}>
        {timeline.length === 0 ?
            <div className="usage-empty-chart">No consumption recorded in this period.</div> :
            <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />}
        {showLegend ? <div className="usage-legend">
            {seriesKeys.map(key => {
                const isDimmed = highlightedKey !== null && highlightedKey !== key;
                return <div
                    key={key || "__local__"}
                    className="usage-legend-item"
                    data-dimmed={isDimmed ? "true" : undefined}
                    onMouseEnter={() => onHighlightKey(key)}
                    onMouseLeave={() => onHighlightKey(null)}
                    onClick={() => onHighlightKey(highlightedKey === key ? null : key)}
                >
                    <span className="usage-legend-swatch" style={{background: colorMap.get(key) ?? "#888"}} />
                    <span>{labelForKey(key, seriesMode)}</span>
                </div>;
            })}
        </div> : null}
    </div>;
}

export function UsageConsumptionBurnupChart(props: {
    detail: UsageDetailResponse;
    unit: FrontendAccountingUnit;
    width: number;
    height: number;
    includeRetired: boolean;
}): React.ReactNode {
    const {detail, unit, width, height, includeRetired} = props;
    const timeline = detail.timeline;
    const clipId = useId().replace(/:/g, "_");
    const [highlightedLayer, setHighlightedLayer] = useState<string | null>(null);

    const forecast = useMemo(() => {
        if (timeline.length === 0) return null;
        const dayMs = 24 * 60 * 60 * 1000;
        const reportEnd = new Date(detail.validUntil);
        if (reportEnd.toDateString() !== new Date().toDateString()) return null;
        if (detail.kpis.periodUsage <= 0) return null;

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const spanMs = Math.max(dayMs, maxTs - minTs);
        const remainingActive = Math.max(0, detail.kpis.activeQuotaAtEnd - detail.kpis.activeUsageAtEnd);
        const activeBalance = Math.max(0, Math.min(remainingActive, detail.kpis.maxUsableAtEnd));
        if (activeBalance <= 0) return null;

        const burnRatePerMs = detail.kpis.periodUsage / spanMs;
        if (burnRatePerMs <= 0) return null;

        const maxExtensionMs = Math.max(30 * dayMs, Math.round(spanMs * 0.6));
        const depletionTs = maxTs + activeBalance / burnRatePerMs;
        const expiryTs = detail.kpis.nextMeaningfulExpiration ?? null;

        let forecastEndTs: number;
        if (expiryTs !== null && expiryTs > maxTs && expiryTs <= maxTs + maxExtensionMs) {
            forecastEndTs = expiryTs;
        } else if (depletionTs <= maxTs + maxExtensionMs) {
            forecastEndTs = Math.min(
                maxTs + maxExtensionMs,
                maxTs + Math.max(14 * dayMs, Math.round((depletionTs - maxTs) * 1.15)),
            );
        } else {
            forecastEndTs = maxTs + maxExtensionMs;
        }

        const lastPt = timeline[timeline.length - 1];
        const baseRetired = includeRetired ? lastPt.retiredUsage : 0;
        const startTotal = baseRetired + lastPt.activeUsage;
        const ceilingTotal = startTotal + activeBalance;
        const hitsCeiling = depletionTs <= forecastEndTs;
        const lineEndTs = hitsCeiling ? depletionTs : forecastEndTs;
        const endTotal = hitsCeiling
            ? ceilingTotal
            : Math.min(ceilingTotal, startTotal + burnRatePerMs * (lineEndTs - maxTs));

        return {
            maxTs,
            forecastEndTs,
            lineEndTs,
            expiryTs: expiryTs !== null && expiryTs > maxTs && expiryTs <= forecastEndTs ? expiryTs : null,
            burnRatePerMs,
            baseRetired,
            startActive: lastPt.activeUsage,
            startTotal,
            endTotal,
            ceilingTotal,
            activeBalance,
            activeQuota: lastPt.activeQuota,
            quota: lastPt.quota,
            hitsCeiling,
        };
    }, [timeline, detail.validUntil, detail.kpis, includeRetired]);

    const hasUpstreamCap = timeline.some(d => d.activeQuota > 0 && (d.activeUsage + d.maxUsable) < d.activeQuota);
    const effectiveLayer = highlightedLayer !== null
        && (
            highlightedLayer === "active"
            || highlightedLayer === "quota"
            || (highlightedLayer === "retired" && includeRetired)
            || (highlightedLayer === "forecast" && forecast !== null)
            || (highlightedLayer === "cap" && hasUpstreamCap)
        )
        ? highlightedLayer
        : null;

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || width <= 80) return;

        let yMax = 0;
        for (const pt of timeline) {
            const retired = includeRetired ? pt.retiredUsage : 0;
            const usageTop = (retired + pt.activeUsage) * unit.balanceFactor;
            const quotaTop = (includeRetired ? pt.quota : pt.activeQuota) * unit.balanceFactor;
            const effLimit = (retired + pt.activeUsage + pt.maxUsable) * unit.balanceFactor;
            yMax = Math.max(yMax, usageTop, quotaTop);
            if (detail.kpis.upstreamCapped) {
                yMax = Math.max(yMax, effLimit);
            }
        }
        if (forecast) {
            yMax = Math.max(yMax, forecast.endTotal * unit.balanceFactor);
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 24, bottom: 40, left: leftMarginForMax(yMax * 1.12)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const domainEnd = forecast ? forecast.forecastEndTs : maxTs;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(domainEnd)])
            .range([0, innerW]);

        const yScale = scaleLinear().domain([0, yMax * 1.12]).nice().range([innerH, 0]);

        svg.append("defs")
            .append("clipPath")
            .attr("id", clipId)
            .append("rect")
            .attr("width", innerW)
            .attr("height", innerH);

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

        const clipped = g.append("g").attr("clip-path", `url(#${clipId})`);

        const layerOpacity = (layer: string): number => {
            if (effectiveLayer === null || effectiveLayer === layer) return 1;
            return 0.18;
        };

        if (forecast) {
            const nowX = xScale(new Date(forecast.maxTs));
            clipped.append("rect")
                .attr("x", nowX)
                .attr("y", 0)
                .attr("width", Math.max(0, innerW - nowX))
                .attr("height", innerH)
                .attr("fill", "var(--textSecondary)")
                .attr("opacity", 0.045 * layerOpacity("forecast"));

            clipped.append("line")
                .attr("x1", nowX)
                .attr("x2", nowX)
                .attr("y1", 0)
                .attr("y2", innerH)
                .attr("stroke", "var(--borderColor)")
                .attr("stroke-width", 1)
                .attr("stroke-dasharray", "2,3")
                .attr("opacity", layerOpacity("forecast"));
        }

        if (includeRetired) {
            const retiredArea = area<UsageTimelineBucket>()
                .x(d => xScale(new Date(d.endTimestamp)))
                .y0(yScale(0))
                .y1(d => yScale(d.retiredUsage * unit.balanceFactor))
                .curve(curveStepAfter);

            clipped.append("path")
                .datum(timeline)
                .attr("d", retiredArea)
                .attr("fill", "var(--textSecondary)")
                .attr("opacity", 0.22 * layerOpacity("retired"));

            const retiredLine = line<UsageTimelineBucket>()
                .x(d => xScale(new Date(d.endTimestamp)))
                .y(d => yScale(d.retiredUsage * unit.balanceFactor))
                .curve(curveStepAfter);

            clipped.append("path")
                .datum(timeline)
                .attr("d", retiredLine)
                .attr("fill", "none")
                .attr("stroke", "var(--textSecondary)")
                .attr("stroke-width", 1.25)
                .attr("stroke-dasharray", "3,3")
                .attr("opacity", layerOpacity("retired"));
        }

        const activeArea = area<UsageTimelineBucket>()
            .x(d => xScale(new Date(d.endTimestamp)))
            .y0(yScale(0))
            .y1(d => yScale(((includeRetired ? d.retiredUsage : 0) + d.activeUsage) * unit.balanceFactor))
            .curve(curveMonotoneX);

        if (includeRetired) {
            activeArea.y0(d => yScale(d.retiredUsage * unit.balanceFactor));
        }

        clipped.append("path")
            .datum(timeline)
            .attr("d", activeArea)
            .attr("fill", "var(--chart-blue-main)")
            .attr("opacity", 0.24 * layerOpacity("active"));

        const activeLine = line<UsageTimelineBucket>()
            .x(d => xScale(new Date(d.endTimestamp)))
            .y(d => yScale(((includeRetired ? d.retiredUsage : 0) + d.activeUsage) * unit.balanceFactor))
            .curve(curveMonotoneX);

        clipped.append("path")
            .datum(timeline)
            .attr("d", activeLine)
            .attr("fill", "none")
            .attr("stroke", "var(--chart-blue-main)")
            .attr("stroke-width", 2.2)
            .attr("opacity", layerOpacity("active"));

        const quotaLine = line<UsageTimelineBucket>()
            .x(d => xScale(new Date(d.endTimestamp)))
            .y(d => yScale((includeRetired ? d.quota : d.activeQuota) * unit.balanceFactor))
            .curve(curveStepAfter);

        clipped.append("path")
            .datum(timeline)
            .attr("d", quotaLine)
            .attr("fill", "none")
            .attr("stroke", "var(--textPrimary)")
            .attr("stroke-width", 1.8)
            .attr("opacity", 0.8 * layerOpacity("quota"));

        if (hasUpstreamCap) {
            const capLine = line<UsageTimelineBucket>()
                .x(d => xScale(new Date(d.endTimestamp)))
                .y(d => {
                    const base = includeRetired ? d.retiredUsage : 0;
                    const ceiling = includeRetired ? d.quota : d.activeQuota;
                    const eff = Math.min(ceiling, base + d.activeUsage + d.maxUsable);
                    return yScale(eff * unit.balanceFactor);
                })
                .curve(curveStepAfter);

            clipped.append("path")
                .datum(timeline)
                .attr("d", capLine)
                .attr("fill", "none")
                .attr("stroke", "var(--warningMain)")
                .attr("stroke-width", 2)
                .attr("stroke-dasharray", "6,4")
                .attr("opacity", layerOpacity("cap"));
        }

        if (forecast) {
            const x0 = xScale(new Date(forecast.maxTs));
            const xEnd = xScale(new Date(forecast.forecastEndTs));
            const xProjEnd = xScale(new Date(forecast.lineEndTs));
            const quotaY = yScale((includeRetired ? forecast.quota : forecast.activeQuota) * unit.balanceFactor);

            clipped.append("line")
                .attr("x1", x0)
                .attr("x2", xEnd)
                .attr("y1", quotaY)
                .attr("y2", quotaY)
                .attr("stroke", "var(--textPrimary)")
                .attr("stroke-width", 1.75)
                .attr("stroke-dasharray", "4,4")
                .attr("opacity", 0.65 * layerOpacity("quota"));

            if (hasUpstreamCap) {
                const effY = yScale(forecast.ceilingTotal * unit.balanceFactor);
                clipped.append("line")
                    .attr("x1", x0)
                    .attr("x2", xEnd)
                    .attr("y1", effY)
                    .attr("y2", effY)
                    .attr("stroke", "var(--warningMain)")
                    .attr("stroke-width", 1.75)
                    .attr("stroke-dasharray", "4,4")
                    .attr("opacity", 0.75 * layerOpacity("cap"));
            }

            if (forecast.expiryTs !== null) {
                const expX = xScale(new Date(forecast.expiryTs));
                clipped.append("line")
                    .attr("x1", expX)
                    .attr("x2", expX)
                    .attr("y1", 0)
                    .attr("y2", innerH)
                    .attr("stroke", "var(--warningMain)")
                    .attr("stroke-width", 1.25)
                    .attr("stroke-dasharray", "3,3")
                    .attr("opacity", 0.8 * layerOpacity("forecast"));
            }

            const y0 = yScale(forecast.startTotal * unit.balanceFactor);
            const y1 = yScale(forecast.endTotal * unit.balanceFactor);
            clipped.append("line")
                .attr("x1", x0)
                .attr("x2", xProjEnd)
                .attr("y1", y0)
                .attr("y2", y1)
                .attr("stroke", "var(--chart-blue-main)")
                .attr("stroke-width", 2)
                .attr("stroke-dasharray", "4,4")
                .attr("opacity", layerOpacity("forecast"));

            clipped.append("circle")
                .attr("cx", xProjEnd)
                .attr("cy", y1)
                .attr("r", 3.5)
                .attr("fill", forecast.hitsCeiling ? "var(--warningMain)" : "var(--chart-blue-main)")
                .attr("opacity", layerOpacity("forecast"));
        }

        drawLifecycleMarkers(g, detail.events, xScale, innerH, minTs, maxTs);

        const tickFormat = formatBucketTick(detail.bucketDurationMs);
        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatAxisTick(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, unit.name);

        const guide = g.append("line")
            .attr("y1", 0)
            .attr("y2", innerH)
            .attr("stroke", "var(--textSecondary)")
            .attr("stroke-dasharray", "3,3")
            .attr("opacity", 0)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastKey = "";
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();

            if (forecast && hoverMs > forecast.maxTs) {
                const clampedMs = Math.min(forecast.forecastEndTs, Math.max(forecast.maxTs, hoverMs));
                const gx = xScale(new Date(clampedMs));
                guide.attr("x1", gx).attr("x2", gx).attr("opacity", 0.7);
                const dayKey = `proj:${Math.floor(clampedMs / (12 * 60 * 60 * 1000))}`;
                if (dayKey !== lastKey) {
                    lastKey = dayKey;
                    const added = forecast.burnRatePerMs * (clampedMs - forecast.maxTs);
                    const projActive = Math.min(forecast.startActive + forecast.activeBalance, forecast.startActive + added);
                    const projBalance = Math.max(0, forecast.startActive + forecast.activeBalance - projActive);
                    const projPct = forecast.activeQuota > 0 ? (projActive / forecast.activeQuota) * 100 : 0;
                    tooltipEl.innerHTML = "";
                    const head = document.createElement("div");
                    head.style.fontWeight = "700";
                    head.style.marginBottom = "4px";
                    head.textContent = `Projected — ${usageFormatShortDate(clampedMs)}`;
                    tooltipEl.append(head);

                    const rows: [string, string][] = [
                        ["Projected usage", `${usageFormatValue(unit, projActive)} (${usageFormatPercent(projPct)})`],
                        ["Active quota", usageFormatValue(unit, forecast.activeQuota)],
                        ["Projected balance", usageFormatValue(unit, projBalance)],
                    ];
                    for (const [label, value] of rows) {
                        const r = document.createElement("div");
                        r.style.display = "flex";
                        r.style.justifyContent = "space-between";
                        r.style.gap = "12px";
                        r.style.fontSize = "12px";
                         appendUsageTooltipText(r, label, value);
                        tooltipEl.append(r);
                    }
                }
                tooltip.moveListener(ev);
                return;
            }

            let idx = 0;
            let bestDist = Infinity;
            for (let i = 0; i < timeline.length; i++) {
                const dist = Math.abs(timeline[i].endTimestamp - hoverMs);
                if (dist < bestDist) {
                    bestDist = dist;
                    idx = i;
                }
            }
            const pt = timeline[idx];
            guide.attr("x1", xScale(new Date(pt.endTimestamp))).attr("x2", xScale(new Date(pt.endTimestamp))).attr("opacity", 0.7);

            const hoverEvents = collectHoverLifecycleEvents(
                detail.events,
                pt.timestamp,
                pt.endTimestamp,
                idx === timeline.length - 1,
                xScale,
                mx,
                minTs,
                maxTs,
            );
            const histKey = `hist:${idx}:${hoverEvents.map(e => `${e.timestamp}-${e.type}`).join(",")}`;
            if (histKey !== lastKey) {
                lastKey = histKey;
                const activeUtil = pt.activeQuota > 0 ? (pt.activeUsage / pt.activeQuota) * 100 : 0;
                tooltipEl.innerHTML = "";
                const head = document.createElement("div");
                head.style.fontWeight = "700";
                head.style.marginBottom = "4px";
                head.textContent = tickFormat(new Date(pt.endTimestamp));
                tooltipEl.append(head);

                const rows: [string, string][] = [
                    ["Active usage", `${usageFormatValue(unit, pt.activeUsage)} (${usageFormatPercent(activeUtil)})`],
                    ["Active quota", usageFormatValue(unit, pt.activeQuota)],
                ];
                if (includeRetired) {
                    rows.push(["Retired baseline", usageFormatValue(unit, pt.retiredUsage)]);
                    rows.push(["Total contributing quota", usageFormatValue(unit, pt.quota)]);
                }
                if (pt.activeQuota > 0 && (pt.activeUsage + pt.maxUsable) < pt.activeQuota) {
                    rows.push(["Effective usable ceiling", usageFormatValue(unit, pt.activeUsage + pt.maxUsable)]);
                }

                for (const [label, value] of rows) {
                    const r = document.createElement("div");
                    r.style.display = "flex";
                    r.style.justifyContent = "space-between";
                    r.style.gap = "12px";
                    r.style.fontSize = "12px";
                     appendUsageTooltipText(r, label, value);
                    tooltipEl.append(r);
                }

                appendLifecycleEventsToTooltip(tooltipEl, hoverEvents, unit);
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            guide.attr("opacity", 0);
            lastKey = "";
            tooltip.leaveListener();
        });
    }, [timeline, width, height, unit, includeRetired, clipId, detail.events, detail.bucketDurationMs, detail.kpis.upstreamCapped, forecast, effectiveLayer, hasUpstreamCap]);

    const legendProps = (key: string) => ({
        "data-dimmed": effectiveLayer !== null && effectiveLayer !== key ? "true" : undefined,
        onMouseEnter: () => setHighlightedLayer(key),
        onMouseLeave: () => setHighlightedLayer(null),
        onClick: () => setHighlightedLayer(prev => prev === key ? null : key),
    });

    return <div className={ChartShellStyle}>
        {timeline.length === 0 ?
            <div className="usage-empty-chart">No quota or usage history in this period.</div> :
            <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />}
        <div className="usage-legend">
            <div className="usage-legend-item" {...legendProps("active")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-blue-main)"}} />
                <span>Active usage</span>
            </div>
            {includeRetired ? <div className="usage-legend-item" {...legendProps("retired")}>
                <span className="usage-legend-swatch" style={{background: "var(--textSecondary)", opacity: 0.45}} />
                <span>Retired baseline</span>
            </div> : null}
            <div className="usage-legend-item" {...legendProps("quota")}>
                <span className="usage-legend-line" style={{color: "var(--textPrimary)"}} />
                <span>{includeRetired ? "Total contributing quota" : "Active quota"}</span>
            </div>
            {forecast ? <div className="usage-legend-item" {...legendProps("forecast")}>
                <span className="usage-legend-line" data-dashed="true" style={{color: "var(--chart-blue-main)"}} />
                <span>Projected pace</span>
            </div> : null}
            {hasUpstreamCap ? <div className="usage-legend-item" {...legendProps("cap")}>
                <span className="usage-legend-line" data-dashed="true" style={{color: "var(--warningMain)"}} />
                <span>Effective ceiling</span>
            </div> : null}
        </div>
    </div>;
}

export function UsageCapacityOccupancyChart(props: {
    detail: UsageDetailResponse;
    unit: FrontendAccountingUnit;
    width: number;
    height: number;
    seriesMode: ConsumptionSeriesMode;
    pinnedChildren: string[];
    highlightedKey: string | null;
    onHighlightKey: (key: string | null) => void;
    labelForKey: (key: string, mode: ConsumptionSeriesMode) => string;
}): React.ReactNode {
    const {
        detail,
        unit,
        width,
        height,
        seriesMode,
        pinnedChildren,
        highlightedKey,
        onHighlightKey,
        labelForKey,
    } = props;
    const timeline = detail.timeline;
    const clipId = useId().replace(/:/g, "_");
    const pinnedSet = useMemo(() => new Set(pinnedChildren), [pinnedChildren]);

    const {seriesKeys, colorMap, bucketMaps} = useMemo(() => {
        const totals = new Map<string, number>();
        const maps: Map<string, number>[] = timeline.map(bucket => {
            const m = new Map<string, number>();
            if (seriesMode === "total") {
                if (pinnedSet.size === 0) {
                    const v = Math.max(0, bucket.activeUsage);
                    m.set("__total__", v);
                    totals.set("__total__", Math.max(totals.get("__total__") ?? 0, v));
                } else {
                    let pinnedSum = 0;
                    for (const item of bucket.byChild) {
                        if (pinnedSet.has(item.key)) {
                            const v = Math.max(0, item.usage);
                            m.set(item.key, (m.get(item.key) ?? 0) + v);
                            totals.set(item.key, Math.max(totals.get(item.key) ?? 0, v));
                            pinnedSum += v;
                        }
                    }
                    const rest = Math.max(0, bucket.activeUsage - pinnedSum);
                    if (rest > 0) {
                        m.set("Other", rest);
                        totals.set("Other", Math.max(totals.get("Other") ?? 0, rest));
                    }
                }
            } else if (seriesMode === "product") {
                for (const item of bucket.byProduct) {
                    const v = Math.max(0, item.usage);
                    if (v > 0) {
                        m.set(item.key, (m.get(item.key) ?? 0) + v);
                        totals.set(item.key, Math.max(totals.get(item.key) ?? 0, v));
                    }
                }
            } else {
                for (const item of bucket.byChild) {
                    const v = Math.max(0, item.usage);
                    if (v > 0) {
                        m.set(item.key, (m.get(item.key) ?? 0) + v);
                        totals.set(item.key, Math.max(totals.get(item.key) ?? 0, v));
                    }
                }
            }
            return m;
        });

        const ordered = [...totals.entries()]
            .filter(([, maxV]) => maxV > 0)
            .sort((a, b) => {
                if (a[0] === "Other") return 1;
                if (b[0] === "Other") return -1;
                if (a[0] === "") return -1;
                if (b[0] === "") return 1;
                return b[1] - a[1];
            })
            .map(([k]) => k);

        const colors = buildSeriesColorMap(ordered);
        if (ordered.includes("__total__")) {
            colors.set("__total__", "var(--chart-blue-main)");
        }
        return {seriesKeys: ordered, colorMap: colors, bucketMaps: maps};
    }, [timeline, seriesMode, pinnedSet]);

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (timeline.length === 0 || width <= 80) return;

        let yMax = 0;
        for (const pt of timeline) {
            yMax = Math.max(yMax, pt.activeUsage * unit.balanceFactor, pt.peakUsage * unit.balanceFactor, pt.activeQuota * unit.balanceFactor);
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 18, right: 24, bottom: 40, left: leftMarginForMax(yMax * 1.12)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const minTs = timeline[0].timestamp;
        const maxTs = timeline[timeline.length - 1].endTimestamp;
        const xScale = scaleTime()
            .domain([new Date(minTs), new Date(maxTs)])
            .range([0, innerW]);

        const keysToStack = seriesKeys.length > 0 ? seriesKeys : ["__total__"];
        const indices = timeline.map((_, i) => i);
        const stacked = stack<number>()
            .keys(keysToStack)
            .value((idx, key) => (bucketMaps[idx]?.get(key) ?? 0) * unit.balanceFactor)(indices);

        const yScale = scaleLinear().domain([0, yMax * 1.12]).nice().range([innerH, 0]);

        svg.append("defs")
            .append("clipPath")
            .attr("id", clipId)
            .append("rect")
            .attr("width", innerW)
            .attr("height", innerH);

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

        const clipped = g.append("g").attr("clip-path", `url(#${clipId})`);

        const isFixedHighlight = highlightedKey === "__ceiling__" || highlightedKey === "__excess__" || highlightedKey === "__cap__";
        const layerOpacity = (layer: string): number => {
            if (highlightedKey === null || highlightedKey === layer) return 1;
            return 0.18;
        };

        const areaGen = area<{idx: number; y0: number; y1: number}>()
            .x(d => xScale(new Date(timeline[d.idx].endTimestamp)))
            .y0(d => yScale(d.y0))
            .y1(d => yScale(d.y1))
            .curve(curveStepAfter);

        const isMultiSeries = !(keysToStack.length === 1 && keysToStack[0] === "__total__");

        clipped.selectAll("path.occ-layer")
            .data(stacked)
            .join("path")
            .attr("class", "occ-layer")
            .attr("fill", d => colorMap.get(d.key) ?? "var(--chart-blue-main)")
            .attr("opacity", d => {
                if (isFixedHighlight) return isMultiSeries ? 0.16 : 0.08;
                if (!isMultiSeries) return 0.28;
                if (highlightedKey === null) return 0.78;
                return d.key === highlightedKey ? 0.95 : 0.2;
            })
            .attr("d", layer => areaGen(layer.map(pt => ({idx: pt.data, y0: pt[0], y1: pt[1]}))));

        const excessArea = area<UsageTimelineBucket>()
            .x(d => xScale(new Date(d.endTimestamp)))
            .y0(d => yScale(Math.min(d.activeUsage, d.activeQuota) * unit.balanceFactor))
            .y1(d => yScale(Math.max(d.activeUsage, d.activeQuota) * unit.balanceFactor))
            .defined(d => d.activeUsage > d.activeQuota)
            .curve(curveStepAfter);

        clipped.append("path")
            .datum(timeline)
            .attr("d", excessArea)
            .attr("fill", "var(--chart-red-main)")
            .attr("opacity", 0.4 * layerOpacity("__excess__"));

        if (!isMultiSeries) {
            const totalOccLine = line<UsageTimelineBucket>()
                .x(d => xScale(new Date(d.endTimestamp)))
                .y(d => yScale(d.activeUsage * unit.balanceFactor))
                .curve(curveStepAfter);

            clipped.append("path")
                .datum(timeline)
                .attr("d", totalOccLine)
                .attr("fill", "none")
                .attr("stroke", "var(--chart-blue-main)")
                .attr("stroke-width", 2)
                .attr("opacity", isFixedHighlight ? 0.25 : 1);
        }

        const quotaLine = line<UsageTimelineBucket>()
            .x(d => xScale(new Date(d.endTimestamp)))
            .y(d => yScale(d.activeQuota * unit.balanceFactor))
            .curve(curveStepAfter);

        clipped.append("path")
            .datum(timeline)
            .attr("d", quotaLine)
            .attr("fill", "none")
            .attr("stroke", "var(--textPrimary)")
            .attr("stroke-width", 1.8)
            .attr("opacity", 0.8 * layerOpacity("__ceiling__"));

        const hasUpstreamCap = timeline.some(d => d.activeQuota > 0 && (d.activeUsage + d.maxUsable) < d.activeQuota);
        if (hasUpstreamCap) {
            const capLine = line<UsageTimelineBucket>()
                .x(d => xScale(new Date(d.endTimestamp)))
                .y(d => yScale(Math.min(d.activeQuota, d.activeUsage + d.maxUsable) * unit.balanceFactor))
                .curve(curveStepAfter);

            clipped.append("path")
                .datum(timeline)
                .attr("d", capLine)
                .attr("fill", "none")
                .attr("stroke", "var(--warningMain)")
                .attr("stroke-width", 2)
                .attr("stroke-dasharray", "6,4")
                .attr("opacity", layerOpacity("__cap__"));
        }

        drawLifecycleMarkers(g, detail.events, xScale, innerH, minTs, maxTs);

        const tickFormat = formatBucketTick(detail.bucketDurationMs);
        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 95)))
            .tickFormat(d => tickFormat(d as Date));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatAxisTick(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, unit.name);

        const guide = g.append("line")
            .attr("y1", 0)
            .attr("y2", innerH)
            .attr("stroke", "var(--textSecondary)")
            .attr("stroke-dasharray", "3,3")
            .attr("opacity", 0)
            .attr("pointer-events", "none");

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const overlay = g.append("rect")
            .attr("width", innerW)
            .attr("height", innerH)
            .attr("fill", "transparent")
            .attr("pointer-events", "all");

        let lastHoverKey = "";
        overlay.on("mousemove", (ev: MouseEvent) => {
            const [mx] = pointer(ev, overlay.node() as Element);
            const hoverMs = xScale.invert(mx).getTime();
            let idx = 0;
            let bestDist = Infinity;
            for (let i = 0; i < timeline.length; i++) {
                const dist = Math.abs(timeline[i].endTimestamp - hoverMs);
                if (dist < bestDist) {
                    bestDist = dist;
                    idx = i;
                }
            }
            const pt = timeline[idx];
            guide.attr("x1", xScale(new Date(pt.endTimestamp))).attr("x2", xScale(new Date(pt.endTimestamp))).attr("opacity", 0.7);

            const hoverEvents = collectHoverLifecycleEvents(
                detail.events,
                pt.timestamp,
                pt.endTimestamp,
                idx === timeline.length - 1,
                xScale,
                mx,
                minTs,
                maxTs,
            );
            const hoverKey = `${idx}:${hoverEvents.map(e => `${e.timestamp}-${e.type}`).join(",")}`;

            if (hoverKey !== lastHoverKey) {
                lastHoverKey = hoverKey;
                tooltipEl.innerHTML = "";
                const head = document.createElement("div");
                head.style.fontWeight = "700";
                head.style.marginBottom = "4px";
                head.textContent = tickFormat(new Date(pt.endTimestamp));
                tooltipEl.append(head);

                const excess = Math.max(0, pt.activeUsage - pt.activeQuota);
                const rows: [string, string][] = [
                    ["Usage", usageFormatValue(unit, pt.activeUsage)],
                    ["Active quota", usageFormatValue(unit, pt.activeQuota)],
                ];
                if (pt.peakUsage > pt.activeUsage) {
                    rows.push(["Peak in bucket", usageFormatValue(unit, pt.peakUsage)]);
                }
                if (excess > 0) {
                    rows.push(["Over-quota excess", usageFormatValue(unit, excess)]);
                }

                for (const [label, value] of rows) {
                    const r = document.createElement("div");
                    r.style.display = "flex";
                    r.style.justifyContent = "space-between";
                    r.style.gap = "12px";
                    r.style.fontSize = "12px";
                     appendUsageTooltipText(r, label, value);
                    tooltipEl.append(r);
                }

                if (seriesKeys.length > 0 && !(seriesKeys.length === 1 && seriesKeys[0] === "__total__")) {
                    const divider = document.createElement("div");
                    divider.style.borderTop = "1px solid var(--borderColor)";
                    divider.style.margin = "6px 0";
                    tooltipEl.append(divider);

                    const bMap = bucketMaps[idx];
                    const items = seriesKeys
                        .map(k => ({key: k, val: bMap?.get(k) ?? 0}))
                        .filter(it => it.val > 0)
                        .sort((a, b) => b.val - a.val)
                        .slice(0, 6);
                    for (const item of items) {
                        const r = document.createElement("div");
                        r.style.display = "flex";
                        r.style.alignItems = "center";
                        r.style.gap = "6px";
                        r.style.fontSize = "12px";
                        const dot = document.createElement("span");
                        dot.style.cssText = "width:8px;height:8px;border-radius:2px;flex-shrink:0";
                        dot.style.backgroundColor = colorMap.get(item.key) ?? "#888";
                        r.append(dot);
                        const label = appendUsageTooltipText(r, labelForKey(item.key, seriesMode), usageFormatValue(unit, item.val), false);
                        label.style.cssText = "flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap";
                        tooltipEl.append(r);
                    }
                }

                appendLifecycleEventsToTooltip(tooltipEl, hoverEvents, unit);
            }
            tooltip.moveListener(ev);
        });

        overlay.on("mouseleave", () => {
            guide.attr("opacity", 0);
            lastHoverKey = "";
            tooltip.leaveListener();
        });
    }, [timeline, width, height, unit, seriesKeys, bucketMaps, colorMap, highlightedKey, clipId, detail.events, detail.bucketDurationMs, seriesMode, labelForKey]);

    const hasExcess = timeline.some(d => d.activeUsage > d.activeQuota);
    const hasUpstreamCap = timeline.some(d => d.activeQuota > 0 && (d.activeUsage + d.maxUsable) < d.activeQuota);
    const showSeriesLegend = seriesKeys.length > 0 && seriesKeys.length <= 5 && !(seriesKeys.length === 1 && seriesKeys[0] === "__total__");

    const legendProps = (key: string) => ({
        "data-dimmed": highlightedKey !== null && highlightedKey !== key ? "true" : undefined,
        onMouseEnter: () => onHighlightKey(key),
        onMouseLeave: () => onHighlightKey(null),
        onClick: () => onHighlightKey(highlightedKey === key ? null : key),
    });

    return <div className={ChartShellStyle}>
        {timeline.length === 0 ?
            <div className="usage-empty-chart">No capacity data recorded in this period.</div> :
            <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />}
        <div className="usage-legend">
            <div className="usage-legend-item" {...legendProps("__ceiling__")}>
                <span className="usage-legend-line" style={{color: "var(--textPrimary)"}} />
                <span>Active quota ceiling</span>
            </div>
            {hasExcess ? <div className="usage-legend-item" {...legendProps("__excess__")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-red-main)", opacity: 0.7}} />
                <span>Over-quota excess</span>
            </div> : null}
            {hasUpstreamCap ? <div className="usage-legend-item" {...legendProps("__cap__")}>
                <span className="usage-legend-line" data-dashed="true" style={{color: "var(--warningMain)"}} />
                <span>Effective limit</span>
            </div> : null}
            {showSeriesLegend ? seriesKeys.map(key => <div
                key={key || "__local__"}
                className="usage-legend-item"
                {...legendProps(key)}
            >
                <span className="usage-legend-swatch" style={{background: colorMap.get(key) ?? "#888"}} />
                <span>{labelForKey(key, seriesMode)}</span>
            </div>) : null}
        </div>
    </div>;
}

function collectHoverLifecycleEvents(
    events: UsageLifecycleEvent[],
    bucketStart: number,
    bucketEnd: number,
    isLastBucket: boolean,
    xScale: any,
    mx: number,
    minTs: number,
    maxTs: number,
): UsageLifecycleEvent[] {
    const matched: UsageLifecycleEvent[] = [];
    for (const ev of events) {
        if (ev.timestamp < minTs || ev.timestamp > maxTs) continue;
        const inBucket = ev.timestamp >= bucketStart && (isLastBucket ? ev.timestamp <= bucketEnd : ev.timestamp < bucketEnd);
        const nearCursor = Math.abs(xScale(new Date(ev.timestamp)) - mx) <= 10;
        if (inBucket || nearCursor) {
            matched.push(ev);
        }
    }
    return matched;
}

function appendLifecycleEventsToTooltip(
    tooltipEl: HTMLDivElement,
    events: UsageLifecycleEvent[],
    unit: FrontendAccountingUnit,
) {
    if (events.length === 0) return;
    const section = document.createElement("div");
    section.style.marginTop = "6px";
    section.style.paddingTop = "5px";
    section.style.borderTop = "1px solid var(--borderColor)";
    section.style.display = "flex";
    section.style.flexDirection = "column";
    section.style.gap = "3px";

    for (const ev of events) {
        const row = document.createElement("div");
        row.style.display = "flex";
        row.style.alignItems = "center";
        row.style.justifyContent = "space-between";
        row.style.gap = "10px";
        row.style.fontSize = "11.5px";

        const isStart = ev.type === "start";
        const color = isStart ? "var(--chart-green-main)" : "var(--warningMain)";
        const label = isStart ? "Grant started" : ev.type === "retire" ? "Retired" : "Expired";
        const sign = ev.quotaDelta >= 0 ? "+" : "-";

        const left = document.createElement("span");
        left.style.display = "inline-flex";
        left.style.alignItems = "center";
        left.style.gap = "6px";
        left.style.color = color;

        const dot = document.createElement("span");
        dot.style.width = "6px";
        dot.style.height = "6px";
        dot.style.borderRadius = "50%";
        dot.style.background = color;
        dot.style.flexShrink = "0";

        const text = document.createElement("span");
        text.textContent = `${label} ${usageFormatShortDate(ev.timestamp)}`;
        left.append(dot, text);

        const val = document.createElement("span");
        val.style.fontVariantNumeric = "tabular-nums";
        val.style.fontWeight = "600";
        val.textContent = `${sign}${usageFormatValue(unit, Math.abs(ev.quotaDelta))}`;

        row.append(left, val);
        section.append(row);
    }

    tooltipEl.append(section);
}

function drawLifecycleMarkers(
    g: any,
    events: UsageLifecycleEvent[],
    xScale: any,
    innerH: number,
    minTs: number,
    maxTs: number,
) {
    const visible = events.filter(ev => ev.timestamp >= minTs && ev.timestamp <= maxTs);
    if (visible.length === 0) return;

    const markers = g.append("g").attr("pointer-events", "none");
    for (const ev of visible) {
        const x = xScale(new Date(ev.timestamp));
        const isStart = ev.type === "start";
        const color = isStart ? "var(--chart-green-main)" : "var(--warningMain)";
        markers.append("line")
            .attr("x1", x)
            .attr("x2", x)
            .attr("y1", 0)
            .attr("y2", innerH)
            .attr("stroke", color)
            .attr("stroke-width", 1.25)
            .attr("stroke-dasharray", "3,3")
            .attr("opacity", 0.8);

        markers.append("circle")
            .attr("cx", x)
            .attr("cy", 5)
            .attr("r", 3.5)
            .attr("fill", color)
            .attr("stroke", "var(--backgroundDefault)")
            .attr("stroke-width", 1);
    }
}

export function styleAxes(gX: any, gY: any, unitName: string) {
    for (const axis of [gX, gY]) {
        axis.selectAll("path, line")
            .style("stroke", "var(--borderColor)")
            .style("stroke-width", 1);
        axis.selectAll("text")
            .style("fill", "var(--textSecondary)")
            .style("font-size", "11px");
    }

    gY.select(".tick:last-of-type text")
        .clone()
        .attr("x", 6)
        .attr("y", -10)
        .attr("text-anchor", "start")
        .style("font-weight", "600")
        .style("fill", "var(--textPrimary)")
        .text(unitName);
}

export function UsageUtilizationHistogram(props: {
    buckets: UsageHistogramBucket[];
    selectedBucket: string | null;
    onSelectBucket: (key: string | null) => void;
}): React.ReactNode {
    const maxCount = Math.max(1, ...props.buckets.map(b => b.count));

    const bucketColor = (key: string): string => {
        switch (key) {
            case "idle":
                return "var(--textSecondary)";
            case "low":
                return "var(--chart-blue-muted)";
            case "medium":
                return "var(--chart-blue-main)";
            case "high":
                return "var(--chart-green-main)";
            case "near_full":
                return "var(--warningMain)";
            case "over":
                return "var(--chart-red-main)";
            default:
                return "var(--chart-blue-main)";
        }
    };

    return <div className={HistogramStyle}>
        <div className="hist-bars">
            {props.buckets.map(b => {
                const heightPct = b.count > 0 ? Math.max(8, (b.count / maxCount) * 100) : 0;
                const active = props.selectedBucket === b.key;
                const displayLabel = b.key === "over" ? "Exhausted" : b.label;
                return <button
                    key={b.key}
                    type="button"
                    className="hist-bucket"
                    title={b.key === "over" ? "Exhausted or over active quota" : b.label}
                    data-active={active ? "true" : undefined}
                    onClick={() => props.onSelectBucket(active ? null : b.key)}
                >
                    <div className="hist-bar-track">
                        <div
                            className="hist-bar-fill"
                            style={{
                                height: `${heightPct}%`,
                                background: bucketColor(b.key),
                                opacity: b.count > 0 ? 0.85 : 0.15,
                            }}
                        />
                    </div>
                    <div className="hist-bucket-count">{formatNumber(b.count)}</div>
                    <div className="hist-bucket-label">{displayLabel}</div>
                </button>;
            })}
        </div>
    </div>;
}

export function usageExpiryMonthKey(timestamp: number): string {
    const d = new Date(timestamp);
    const month = String(d.getMonth() + 1).padStart(2, "0");
    return `${d.getFullYear()}-${month}`;
}

export function usageFormatMonthKey(monthKey: string): string {
    if (monthKey === "later") return "Later";
    const [yearStr, monthStr] = monthKey.split("-");
    const year = Number(yearStr);
    const month = Number(monthStr);
    if (!Number.isFinite(year) || !Number.isFinite(month)) return monthKey;
    return new Date(year, month - 1, 1).toLocaleDateString(undefined, {
        month: "short",
        year: "numeric",
    });
}

export function usageFormatMonthKeyShort(monthKey: string, includeYear: boolean): string {
    if (monthKey === "later") return "Later";
    const [yearStr, monthStr] = monthKey.split("-");
    const year = Number(yearStr);
    const month = Number(monthStr);
    if (!Number.isFinite(year) || !Number.isFinite(month)) return monthKey;
    return new Date(year, month - 1, 1).toLocaleDateString(undefined, {
        month: "short",
        year: includeYear ? "2-digit" : undefined,
    });
}

export function UsageSubProjectScatterChart(props: {
    rows: UsageChildRow[];
    accountingModel: AccountingModel;
    includeRetired?: boolean;
    isHistorical?: boolean;
    unit: FrontendAccountingUnit;
    periodEnd: number;
    width: number;
    height: number;
    pinnedChildren: string[];
    onTogglePin: (child: string) => void;
    labelForChild: (row: UsageChildRow) => {title: string; pi: string};
}): React.ReactNode {
    const {
        rows,
        accountingModel,
        includeRetired = false,
        isHistorical = false,
        unit,
        periodEnd,
        width,
        height,
        pinnedChildren,
        onTogglePin,
        labelForChild,
    } = props;
    const isCapacity = accountingModel === "CAPACITY";
    const useLifetime = !isCapacity && includeRetired;
    const pinnedSet = useMemo(() => new Set(pinnedChildren), [pinnedChildren]);
    const [highlightedLegend, setHighlightedLegend] = useState<string | null>(null);

    const points = useMemo(() => {
        return rows.filter(r => {
            if (r.isLocal) return false;
            const m = usageChildScopeMetrics(r, accountingModel, includeRetired);
            return m.quotaKnown && (m.quota > 0 || m.usage > 0);
        });
    }, [rows, accountingModel, includeRetired]);

    const legendKeyFor = (row: UsageChildRow): string => {
        const m = usageChildScopeMetrics(row, accountingModel, includeRetired);
        const hasDropRisk = !isHistorical && usageDropRiskDays(row.nextQuotaDrop, accountingModel, periodEnd) !== null;
        if (m.utilizationBucket === "over" || hasDropRisk) return "risk";
        if (m.utilizationBucket === "near_full") return "warning";
        if (m.utilizationBucket === "idle") return "idle";
        return "active";
    };

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (points.length === 0 || width <= 80) return;

        let maxQuota = 0;
        let maxUtil = 100;
        for (const r of points) {
            const m = usageChildScopeMetrics(r, accountingModel, includeRetired);
            const q = m.quota * unit.balanceFactor;
            if (q > maxQuota) maxQuota = q;
            const u = m.quota === 0 && m.usage > 0 ? 120 : m.utilizationPercent;
            if (u > maxUtil) maxUtil = u;
        }
        if (maxQuota <= 0) maxQuota = 1;
        const yCap = Math.min(150, Math.max(100, Math.ceil(maxUtil / 10) * 10));

        const margin = {top: 18, right: 20, bottom: 42, left: 60};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const xScale = scaleLinear().domain([0, maxQuota * 1.08]).nice().range([0, innerW]);
        const yScale = scaleLinear().domain([0, yCap]).nice().range([innerH, 0]);

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

        g.append("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", yScale(100))
            .attr("y2", yScale(100))
            .attr("stroke", "var(--chart-red-main)")
            .attr("stroke-width", 1)
            .attr("stroke-dasharray", "4,4")
            .attr("opacity", 0.6);

        const dotColor = (r: UsageChildRow): string => {
            const key = legendKeyFor(r);
            if (key === "risk") return "var(--chart-red-main)";
            if (key === "warning") return "var(--warningMain)";
            if (key === "idle") return "var(--textSecondary)";
            return "var(--chart-blue-main)";
        };

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        g.selectAll("circle.scatter-dot")
            .data(points)
            .join("circle")
            .attr("class", "scatter-dot")
            .attr("cx", d => {
                const m = usageChildScopeMetrics(d, accountingModel, includeRetired);
                return xScale(m.quota * unit.balanceFactor);
            })
            .attr("cy", d => {
                const m = usageChildScopeMetrics(d, accountingModel, includeRetired);
                const rawUtil = m.quota === 0 && m.usage > 0 ? yCap : m.utilizationPercent;
                return yScale(Math.min(yCap, Math.max(0, rawUtil)));
            })
            .attr("r", d => pinnedSet.has(d.child) ? 6 : 4.5)
            .attr("fill", d => dotColor(d))
            .attr("fill-opacity", d => {
                const isDimmed = highlightedLegend !== null && legendKeyFor(d) !== highlightedLegend;
                if (isDimmed) return 0.08;
                return pinnedSet.has(d.child) ? 1 : 0.78;
            })
            .attr("stroke", d => pinnedSet.has(d.child) ? "var(--textPrimary)" : "var(--backgroundDefault)")
            .attr("stroke-width", d => pinnedSet.has(d.child) ? 2 : 0.75)
            .style("cursor", "pointer")
            .on("mouseenter", function (ev: MouseEvent, d: UsageChildRow) {
                const isDimmed = highlightedLegend !== null && legendKeyFor(d) !== highlightedLegend;
                select(this).attr("r", 7).attr("fill-opacity", isDimmed ? 0.2 : 1);
                const info = labelForChild(d);
                const m = usageChildScopeMetrics(d, accountingModel, includeRetired);
                tooltipEl.innerHTML = "";
                const head = document.createElement("div");
                head.style.fontWeight = "700";
                head.style.marginBottom = "2px";
                head.textContent = info.title;
                tooltipEl.append(head);

                if (info.pi) {
                    const piEl = document.createElement("div");
                    piEl.style.fontSize = "11px";
                    piEl.style.color = "var(--textSecondary)";
                    piEl.style.marginBottom = "4px";
                    piEl.textContent = `PI ${info.pi}`;
                    tooltipEl.append(piEl);
                }

                const utilText = m.quota === 0 && m.usage > 0 ? "Over" : usageFormatPercent(m.utilizationPercent);
                const items: [string, string][] = [
                    [useLifetime ? "Lifetime usage" : "Active usage", usageFormatValue(unit, m.usage)],
                    [useLifetime ? "Lifetime quota" : "Active quota", usageFormatValue(unit, m.quota)],
                    ["Utilization", utilText],
                ];
                if (useLifetime && d.retiredUsage > 0) {
                    items.push(["Retired usage", usageFormatValue(unit, d.retiredUsage)]);
                }
                const dropRiskDays = isHistorical ? null : usageDropRiskDays(d.nextQuotaDrop, accountingModel, periodEnd);
                if (d.nextQuotaDrop && dropRiskDays !== null) {
                    if (isCapacity) {
                        items.push(["Over quota in", `${formatNumber(dropRiskDays)}d (drops to ${usageFormatValue(unit, d.nextQuotaDrop.quotaAfter)} on ${usageFormatShortDate(d.nextQuotaDrop.timestamp)})`]);
                    } else {
                        items.push(["No quota in", `${formatNumber(dropRiskDays)}d (expires ${usageFormatShortDate(d.nextQuotaDrop.timestamp)} with no renewal)`]);
                    }
                }
                for (const [label, value] of items) {
                    const r = document.createElement("div");
                    r.style.display = "flex";
                    r.style.justifyContent = "space-between";
                    r.style.gap = "12px";
                    r.style.fontSize = "12px";
                     appendUsageTooltipText(r, label, value);
                    tooltipEl.append(r);
                }
                tooltip.moveListener(ev);
            })
            .on("mousemove", (ev: MouseEvent) => {
                tooltip.moveListener(ev);
            })
            .on("mouseleave", function (_ev: MouseEvent, d: UsageChildRow) {
                const isDimmed = highlightedLegend !== null && legendKeyFor(d) !== highlightedLegend;
                select(this)
                    .attr("r", pinnedSet.has(d.child) ? 6 : 4.5)
                    .attr("fill-opacity", isDimmed ? 0.08 : (pinnedSet.has(d.child) ? 1 : 0.78));
                tooltip.leaveListener();
            })
            .on("click", (_ev: MouseEvent, d: UsageChildRow) => {
                tooltip.leaveListener();
                onTogglePin(d.child);
            });

        const xAxis = axisBottom(xScale)
            .ticks(Math.max(3, Math.floor(innerW / 100)))
            .tickFormat(d => formatAxisTick(Number(d)));
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => `${formatNumber(Number(d))}%`);

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);

        for (const axis of [gX, gY]) {
            axis.selectAll("path, line")
                .style("stroke", "var(--borderColor)")
                .style("stroke-width", 1);
            axis.selectAll("text")
                .style("fill", "var(--textSecondary)")
                .style("font-size", "11px");
        }

        gX.append("text")
            .attr("x", innerW)
            .attr("y", 34)
            .attr("text-anchor", "end")
            .style("fill", "var(--textSecondary)")
            .style("font-size", "11px")
            .text(`${useLifetime ? "Lifetime quota" : "Active quota"} (${unit.name})`);

        g.append("text")
            .attr("transform", `translate(${-margin.left + 12},${innerH / 2}) rotate(-90)`)
            .attr("text-anchor", "middle")
            .style("fill", "var(--textSecondary)")
            .style("font-size", "11px")
            .text("Utilization (% of quota)");
    }, [points, width, height, unit, isCapacity, useLifetime, accountingModel, includeRetired, isHistorical, periodEnd, pinnedSet, labelForChild, onTogglePin, highlightedLegend]);

    const legendProps = (key: string) => ({
        "data-dimmed": highlightedLegend !== null && highlightedLegend !== key ? "true" : undefined,
        onMouseEnter: () => setHighlightedLegend(key),
        onMouseLeave: () => setHighlightedLegend(null),
        onClick: () => setHighlightedLegend(prev => prev === key ? null : key),
    });

    return <div className={ChartShellStyle}>
        {points.length === 0 ?
            <div className="usage-empty-chart">{useLifetime ? "No sub-project quotas to plot." : "No active sub-project quotas to plot."}</div> :
            <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />}
        <div className="usage-legend" style={{paddingLeft: 60}}>
            <div className="usage-legend-item" {...legendProps("active")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-blue-main)", borderRadius: "50%"}} />
                <span>Active</span>
            </div>
            <div className="usage-legend-item" {...legendProps("warning")}>
                <span className="usage-legend-swatch" style={{background: "var(--warningMain)", borderRadius: "50%"}} />
                <span>90–100%</span>
            </div>
            <div className="usage-legend-item" {...legendProps("risk")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-red-main)", borderRadius: "50%"}} />
                <span>{isHistorical
                    ? (isCapacity ? "Over quota" : "Exhausted")
                    : (isCapacity ? "Over / expiry risk" : "Exhausted / expiry risk")}</span>
            </div>
            <div className="usage-legend-item" {...legendProps("idle")}>
                <span className="usage-legend-swatch" style={{background: "var(--textSecondary)", borderRadius: "50%"}} />
                <span>Idle</span>
            </div>
        </div>
    </div>;
}

export interface UsageExpiryMonthBucket {
    key: string;
    label: string;
    count: number;
    atRiskCount: number;
    atRiskQuota: number;
    safeQuota: number;
    totalBalance: number;
}

export function buildUsageExpiryMonthBuckets(
    rows: UsageChildRow[],
    accountingModel: AccountingModel,
    periodEnd: number,
): {buckets: UsageExpiryMonthBucket[]; rowMonthKeys: Map<string, Set<string>>} {
    const grouped = new Map<string, UsageExpiryMonthBucket>();
    const rawRowMonths = new Map<string, Set<string>>();

    const ensureBucket = (mKey: string): UsageExpiryMonthBucket => {
        let bucket = grouped.get(mKey);
        if (!bucket) {
            bucket = {
                key: mKey,
                label: usageFormatMonthKey(mKey),
                count: 0,
                atRiskCount: 0,
                atRiskQuota: 0,
                safeQuota: 0,
                totalBalance: 0,
            };
            grouped.set(mKey, bucket);
        }
        return bucket;
    };

    const addRowMonth = (child: string, mKey: string) => {
        let set = rawRowMonths.get(child);
        if (!set) {
            set = new Set<string>();
            rawRowMonths.set(child, set);
        }
        set.add(mKey);
    };

    for (const row of rows) {
        if (row.isLocal || row.activeQuota <= 0) continue;
        const isAtRisk = usageDropRiskDays(row.nextQuotaDrop, accountingModel, periodEnd) !== null;
        const dropRiskMonth = isAtRisk && row.nextQuotaDrop
            ? usageExpiryMonthKey(row.nextQuotaDrop.timestamp)
            : null;
        const unspentBalance = Math.max(0, row.activeQuota - row.activeUsage);

        const expirations = (row.upcomingExpirations ?? []).filter(exp => exp.timestamp > periodEnd && exp.quota > 0);
        if (expirations.length > 0) {
            const perMonth = new Map<string, number>();
            for (const exp of expirations) {
                const mKey = usageExpiryMonthKey(exp.timestamp);
                perMonth.set(mKey, (perMonth.get(mKey) ?? 0) + exp.quota);
            }
            for (const [mKey, expiringQuota] of perMonth) {
                addRowMonth(row.child, mKey);
                const bucket = ensureBucket(mKey);
                bucket.count++;
                if (dropRiskMonth === mKey) {
                    bucket.atRiskCount++;
                    bucket.atRiskQuota += expiringQuota;
                } else {
                    bucket.safeQuota += expiringQuota;
                }
                bucket.totalBalance += Math.min(expiringQuota, unspentBalance);
            }
            continue;
        }

        const target = row.nextQuotaDrop?.timestamp ?? row.nextExpiration;
        if (target == null || target <= periodEnd) continue;
        const mKey = usageExpiryMonthKey(target);
        addRowMonth(row.child, mKey);
        const bucket = ensureBucket(mKey);
        bucket.count++;
        if (isAtRisk) {
            bucket.atRiskCount++;
            bucket.atRiskQuota += row.activeQuota;
        } else {
            bucket.safeQuota += row.activeQuota;
        }
        bucket.totalBalance += unspentBalance;
    }

    if (grouped.size === 0) {
        return {buckets: [], rowMonthKeys: rawRowMonths};
    }

    const firstKey = usageExpiryMonthKey(periodEnd);
    let lastKey = firstKey;
    for (const key of grouped.keys()) {
        if (key > lastKey) lastKey = key;
    }

    const monthKeys: string[] = [];
    const firstDate = new Date(periodEnd);
    for (let i = 0; i < 12; i++) {
        const key = usageExpiryMonthKey(new Date(firstDate.getFullYear(), firstDate.getMonth() + i, 1).getTime());
        monthKeys.push(key);
        if (key >= lastKey) break;
    }
    if (monthKeys[monthKeys.length - 1] !== lastKey) {
        monthKeys.pop();
    }

    const buckets = monthKeys.map(key => ({
        key,
        label: usageFormatMonthKey(key),
        count: 0,
        atRiskCount: 0,
        atRiskQuota: 0,
        safeQuota: 0,
        totalBalance: 0,
    }));
    const byKey = new Map(buckets.map(b => [b.key, b]));

    let later: UsageExpiryMonthBucket | null = null;
    for (const source of grouped.values()) {
        const target = byKey.get(source.key);
        if (target) {
            target.count += source.count;
            target.atRiskCount += source.atRiskCount;
            target.atRiskQuota += source.atRiskQuota;
            target.safeQuota += source.safeQuota;
            target.totalBalance += source.totalBalance;
            continue;
        }
        if (!later) {
            later = {
                key: "later",
                label: "Later",
                count: 0,
                atRiskCount: 0,
                atRiskQuota: 0,
                safeQuota: 0,
                totalBalance: 0,
            };
            buckets.push(later);
        }
        later.count += source.count;
        later.atRiskCount += source.atRiskCount;
        later.atRiskQuota += source.atRiskQuota;
        later.safeQuota += source.safeQuota;
        later.totalBalance += source.totalBalance;
    }

    const coveredKeys = new Set(monthKeys);
    const rowMonthKeys = new Map<string, Set<string>>();
    for (const [child, mKeys] of rawRowMonths) {
        const mapped = new Set<string>();
        for (const mKey of mKeys) {
            mapped.add(coveredKeys.has(mKey) ? mKey : "later");
        }
        rowMonthKeys.set(child, mapped);
    }
    return {buckets, rowMonthKeys};
}

export function UsageExpirationScheduleChart(props: {
    buckets: UsageExpiryMonthBucket[];
    accountingModel: AccountingModel;
    unit: FrontendAccountingUnit;
    width: number;
    height: number;
    selectedMonth: string | null;
    onSelectMonth: (monthKey: string | null) => void;
}): React.ReactNode {
    const {
        buckets,
        accountingModel,
        unit,
        width,
        height,
        selectedMonth,
        onSelectMonth,
    } = props;
    const isCapacity = accountingModel === "CAPACITY";
    const [highlightedLegend, setHighlightedLegend] = useState<string | null>(null);

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (buckets.length === 0 || width <= 80) return;

        let yMax = 0;
        for (const b of buckets) {
            const total = (b.atRiskQuota + b.safeQuota) * unit.balanceFactor;
            if (total > yMax) yMax = total;
        }
        if (yMax <= 0) yMax = 1;

        const margin = {top: 22, right: 16, bottom: 42, left: leftMarginForMax(yMax * 1.15)};
        const innerW = Math.max(40, width - margin.left - margin.right);
        const innerH = Math.max(80, height - margin.top - margin.bottom);

        const xScale = scaleBand<string>()
            .domain(buckets.map(b => b.key))
            .range([0, innerW])
            .padding(0.28);

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

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        const barWidth = Math.min(48, xScale.bandwidth());
        const barOffset = (xScale.bandwidth() - barWidth) / 2;

        const groups = g.selectAll("g.exp-bar")
            .data(buckets)
            .join("g")
            .attr("class", "exp-bar")
            .style("cursor", d => d.count === 0 ? "default" : "pointer")
            .attr("opacity", d => selectedMonth === null || selectedMonth === d.key ? 1 : 0.32)
            .on("mouseenter", (ev: MouseEvent, d: UsageExpiryMonthBucket) => {
                tooltipEl.innerHTML = "";
                const head = document.createElement("div");
                head.style.fontWeight = "700";
                head.style.marginBottom = "4px";
                head.textContent = d.label;
                tooltipEl.append(head);

                const rows: [string, string][] = [
                    ["Expiring sub-projects", formatNumber(d.count)],
                    ["Expiring active quota", usageFormatValue(unit, d.atRiskQuota + d.safeQuota)],
                ];
                if (d.atRiskCount > 0) {
                    const riskLabel = isCapacity ? "Over quota after expiry" : "No quota after expiry";
                    rows.push([riskLabel, `${formatNumber(d.atRiskCount)} (${usageFormatValue(unit, d.atRiskQuota)})`]);
                }
                if (!isCapacity) {
                    rows.push(["Current unspent balance", usageFormatValue(unit, d.totalBalance)]);
                }
                for (const [label, value] of rows) {
                    const r = document.createElement("div");
                    r.style.display = "flex";
                    r.style.justifyContent = "space-between";
                    r.style.gap = "12px";
                    r.style.fontSize = "12px";
                     appendUsageTooltipText(r, label, value);
                    tooltipEl.append(r);
                }
                tooltip.moveListener(ev);
            })
            .on("mousemove", (ev: MouseEvent) => {
                tooltip.moveListener(ev);
            })
            .on("mouseleave", () => {
                tooltip.leaveListener();
            })
            .on("click", (_ev: MouseEvent, d: UsageExpiryMonthBucket) => {
                tooltip.leaveListener();
                if (d.count === 0) return;
                onSelectMonth(selectedMonth === d.key ? null : d.key);
            });

        groups.each(function (d) {
            const cell = select(this);
            const x = (xScale(d.key) ?? 0) + barOffset;
            const riskNorm = d.atRiskQuota * unit.balanceFactor;
            const totalNorm = (d.atRiskQuota + d.safeQuota) * unit.balanceFactor;

            if (riskNorm > 0) {
                cell.append("rect")
                    .attr("x", x)
                    .attr("width", barWidth)
                    .attr("y", yScale(riskNorm))
                    .attr("height", Math.max(2, yScale(0) - yScale(riskNorm)))
                    .attr("fill", "var(--chart-red-main)")
                    .attr("opacity", highlightedLegend === null || highlightedLegend === "risk" ? 1 : 0.15)
                    .attr("rx", 1.5);
            }
            if (d.safeQuota > 0) {
                cell.append("rect")
                    .attr("x", x)
                    .attr("width", barWidth)
                    .attr("y", yScale(totalNorm))
                    .attr("height", Math.max(2, yScale(riskNorm) - yScale(totalNorm)))
                    .attr("fill", "var(--chart-blue-main)")
                    .attr("opacity", (highlightedLegend === null || highlightedLegend === "safe" ? 1 : 0.15) * 0.88)
                    .attr("rx", 1.5);
            }
            cell.append("text")
                .attr("x", x + barWidth / 2)
                .attr("y", Math.max(10, yScale(totalNorm) - 5))
                .attr("text-anchor", "middle")
                .style("fill", "var(--textSecondary)")
                .style("font-size", "10.5px")
                .style("font-variant-numeric", "tabular-nums")
                .text(formatNumber(d.count));
        });

        const useShortLabels = buckets.length > 6;
        const maxTicks = Math.max(2, Math.floor(innerW / (useShortLabels ? 56 : 88)));
        const tickStep = Math.max(1, Math.ceil(buckets.length / maxTicks));
        const tickBuckets = buckets.filter((_, i) => i % tickStep === 0 || i === buckets.length - 1);
        const xAxis = axisBottom(xScale)
            .tickValues(tickBuckets.map(b => b.key))
            .tickFormat(key => {
                const bucket = buckets.find(b => b.key === key);
                if (!bucket) return key;
                if (!useShortLabels || bucket.key === "later") return bucket.label;
                const index = tickBuckets.indexOf(bucket);
                const previous = index > 0 ? tickBuckets[index - 1] : null;
                const includeYear = index === 0 || (previous !== null && previous.key.slice(0, 4) !== bucket.key.slice(0, 4));
                return usageFormatMonthKeyShort(bucket.key, includeYear);
            });
        const yAxis = axisLeft(yScale)
            .ticks(5)
            .tickFormat(d => formatAxisTick(Number(d)));

        const gX = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top + innerH})`)
            .call(xAxis);
        const gY = svg.append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`)
            .call(yAxis);
        styleAxes(gX, gY, unit.name);
    }, [buckets, width, height, unit, isCapacity, selectedMonth, onSelectMonth, highlightedLegend]);

    const hasAtRisk = buckets.some(b => b.atRiskCount > 0);

    const legendProps = (key: string) => ({
        "data-dimmed": highlightedLegend !== null && highlightedLegend !== key ? "true" : undefined,
        onMouseEnter: () => setHighlightedLegend(key),
        onMouseLeave: () => setHighlightedLegend(null),
        onClick: () => setHighlightedLegend(prev => prev === key ? null : key),
    });

    return <div className={ChartShellStyle}>
        {buckets.length === 0 ?
            <div className="usage-empty-chart">No upcoming sub-project expirations.</div> :
            <svg ref={chartRef} width={width} height={height} style={{display: "block", maxWidth: "100%"}} />}
        {buckets.length > 0 ? <div className="usage-legend">
            <div className="usage-legend-item" {...legendProps("safe")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-blue-main)"}} />
                <span>Expiring active quota</span>
            </div>
            {hasAtRisk ? <div className="usage-legend-item" {...legendProps("risk")}>
                <span className="usage-legend-swatch" style={{background: "var(--chart-red-main)"}} />
                <span>{isCapacity ? "Over quota after expiry" : "No quota after expiry"}</span>
            </div> : null}
        </div> : null}
    </div>;
}

const CalendarStyle = injectStyle("usage-v2-calendar", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 6px;
    }

    ${k} .cal-scroll {
        overflow-x: auto;
    }

    ${k} .cal-legend {
        display: flex;
        align-items: center;
        gap: 6px;
        padding-left: 32px;
        font-size: 11.5px;
        color: var(--textSecondary);
    }

    ${k} .cal-legend-swatches {
        display: inline-flex;
        align-items: center;
        gap: 3px;
    }

    ${k} .cal-legend-cell {
        width: 11px;
        height: 11px;
        border-radius: 2px;
        cursor: pointer;
        border: 0;
        padding: 0;
        transition: transform 100ms ease, opacity 120ms ease;
    }

    ${k} .cal-legend-cell:hover {
        transform: scale(1.15);
    }

    ${k} .cal-legend-cell[data-dimmed="true"] {
        opacity: 0.25;
    }
`);

function utcDayKey(ts: number): string {
    const d = new Date(ts);
    const y = d.getUTCFullYear();
    const m = String(d.getUTCMonth() + 1).padStart(2, "0");
    const day = String(d.getUTCDate()).padStart(2, "0");
    return `${y}-${m}-${day}`;
}

export function UsageCalendarHeatmap(props: {
    detail: UsageDetailResponse;
    unit: FrontendAccountingUnit;
    width: number;
}): React.ReactNode {
    const {detail, unit, width} = props;
    const [highlightedLevel, setHighlightedLevel] = useState<number | null>(null);

    const labelLeft = 32;
    const topPad = 18;
    const gap = 3;
    const availW = Math.max(160, width - labelLeft - 8);

    const model = useMemo(() => {
        const dayMs = 24 * 60 * 60 * 1000;
        const endDate = new Date(detail.validUntil);
        const endUtc = Date.UTC(endDate.getUTCFullYear(), endDate.getUTCMonth(), endDate.getUTCDate());
        const rawStartDate = new Date(detail.validFrom);
        const rawStartUtc = Date.UTC(rawStartDate.getUTCFullYear(), rawStartDate.getUTCMonth(), rawStartDate.getUTCDate());
        const maxSpanDays = 365;
        const clamped = (endUtc - rawStartUtc) / dayMs > maxSpanDays;
        const startUtc = clamped ? endUtc - maxSpanDays * dayMs : rawStartUtc;

        const now = new Date();
        const todayUtc = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate());
        const endsInCurrentMonth =
            endDate.getUTCFullYear() === now.getUTCFullYear()
            && endDate.getUTCMonth() === now.getUTCMonth();

        const valueByDay = new Map<string, number>();
        if (detail.dailyActivity && detail.dailyActivity.length > 0) {
            for (const pt of detail.dailyActivity) {
                if (pt.usageChange > 0) {
                    const key = utcDayKey(pt.timestamp);
                    valueByDay.set(key, (valueByDay.get(key) ?? 0) + pt.usageChange);
                }
            }
        } else if (detail.bucketDurationMs <= dayMs) {
            for (const pt of detail.timeline) {
                if (pt.usageChange > 0) {
                    const key = utcDayKey(pt.timestamp);
                    valueByDay.set(key, (valueByDay.get(key) ?? 0) + pt.usageChange);
                }
            }
        }

        const eventsByDay = new Map<string, UsageLifecycleEvent[]>();
        for (const ev of detail.events) {
            const key = utcDayKey(ev.timestamp);
            const list = eventsByDay.get(key) ?? [];
            list.push(ev);
            eventsByDay.set(key, list);
        }

        const startDow = (new Date(startUtc).getUTCDay() + 6) % 7;
        const selectedStartMonday = startUtc - startDow * dayMs;
        const endDow = (new Date(endUtc).getUTCDay() + 6) % 7;
        const selectedEndSunday = endUtc + (6 - endDow) * dayMs;
        const selectedWeeks = Math.max(1, Math.round((selectedEndSunday - selectedStartMonday) / (7 * dayMs)) + 1);

        const preferredCell = 14;
        const fittedCell = Math.max(10, Math.min(preferredCell, Math.floor((availW - (selectedWeeks - 1) * gap) / selectedWeeks)));
        const step = fittedCell + gap;
        const fullRowWeeks = Math.max(selectedWeeks, Math.floor((availW + gap) / step));
        const extraWeeks = fullRowWeeks - selectedWeeks;

        const futureWeeks = endsInCurrentMonth ? 0 : Math.floor(extraWeeks / 2);
        const pastWeeks = extraWeeks - futureWeeks;

        const gridStartUtc = selectedStartMonday - pastWeeks * 7 * dayMs;
        const numWeeks = fullRowWeeks;

        const inPeriodNonZero: number[] = [];
        for (let ts = startUtc; ts <= endUtc; ts += dayMs) {
            const v = valueByDay.get(utcDayKey(ts)) ?? 0;
            if (v > 0) inPeriodNonZero.push(v);
        }
        const visibleNonZero: number[] = [];
        if (inPeriodNonZero.length === 0) {
            for (let i = 0; i < numWeeks * 7; i++) {
                const ts = gridStartUtc + i * dayMs;
                const v = valueByDay.get(utcDayKey(ts)) ?? 0;
                if (v > 0) visibleNonZero.push(v);
            }
        }
        const nonZero = inPeriodNonZero.length > 0 ? inPeriodNonZero : visibleNonZero;
        nonZero.sort((a, b) => a - b);

        const quantile = (p: number): number => {
            if (nonZero.length === 0) return 0;
            const idx = Math.min(nonZero.length - 1, Math.floor(p * (nonZero.length - 1)));
            return nonZero[idx];
        };
        const q1 = quantile(0.25);
        const q2 = quantile(0.5);
        const q3 = quantile(0.75);

        const levelFor = (v: number): number => {
            if (v <= 0) return 0;
            if (v <= q1) return 1;
            if (v <= q2) return 2;
            if (v <= q3) return 3;
            return 4;
        };

        type CalCell = {
            ts: number;
            key: string;
            week: number;
            dow: number;
            value: number;
            level: number;
            inPeriod: boolean;
            events: UsageLifecycleEvent[];
        };

        const cells: CalCell[] = [];
        const monthMarkers: {week: number; label: string; inPeriod: boolean}[] = [];
        let lastMonth = -1;
        let lastMarkerWeek = -10;

        for (let week = 0; week < numWeeks; week++) {
            for (let dow = 0; dow < 7; dow++) {
                const ts = gridStartUtc + (week * 7 + dow) * dayMs;
                const inPeriod = ts >= startUtc && ts <= endUtc;
                const key = utcDayKey(ts);
                const val = valueByDay.get(key) ?? 0;
                const d = new Date(ts);
                const m = d.getUTCMonth();
                if (m !== lastMonth) {
                    lastMonth = m;
                    if (week - lastMarkerWeek >= 2) {
                        const monthEndTs = Date.UTC(d.getUTCFullYear(), m + 1, 0);
                        const monthStartTs = Date.UTC(d.getUTCFullYear(), m, 1);
                        const monthOverlaps = monthEndTs >= startUtc && monthStartTs <= endUtc;
                        monthMarkers.push({
                            week,
                            label: d.toLocaleDateString(undefined, {month: "short", timeZone: "UTC"}),
                            inPeriod: monthOverlaps,
                        });
                        lastMarkerWeek = week;
                    }
                }
                cells.push({
                    ts,
                    key,
                    week,
                    dow,
                    value: val,
                    level: levelFor(val),
                    inPeriod,
                    events: inPeriod ? (eventsByDay.get(key) ?? []) : [],
                });
            }
        }

        return {
            cells,
            monthMarkers,
            numWeeks,
            cellSize: fittedCell,
            step,
            clamped,
            todayUtc,
        };
    }, [detail.validFrom, detail.validUntil, detail.dailyActivity, detail.timeline, detail.bucketDurationMs, detail.events, availW]);

    const levelOpacity = [0, 0.25, 0.48, 0.74, 1];
    const cellSize = model.cellSize;
    const step = model.step;
    const svgWidth = labelLeft + model.numWeeks * step + 2;
    const svgHeight = topPad + 7 * step + 4;

    const chartRef = useD3(node => {
        const svg = select(node);
        svg.selectAll("*").remove();
        if (model.cells.length === 0) return;

        const g = svg.append("g");

        const dayLabels: [number, string][] = [
            [0, "Mon"],
            [2, "Wed"],
            [4, "Fri"],
            [6, "Sun"],
        ];
        for (const [dow, text] of dayLabels) {
            g.append("text")
                .attr("x", labelLeft - 6)
                .attr("y", topPad + dow * step + cellSize * 0.78)
                .attr("text-anchor", "end")
                .style("fill", "var(--textSecondary)")
                .style("font-size", "10.5px")
                .text(text);
        }

        for (const marker of model.monthMarkers) {
            g.append("text")
                .attr("x", labelLeft + marker.week * step)
                .attr("y", 11)
                .attr("text-anchor", "start")
                .style("fill", "var(--textSecondary)")
                .style("font-size", "10.5px")
                .style("opacity", marker.inPeriod ? 1 : 0.45)
                .text(marker.label);
        }

        const tooltipEl = document.createElement("div");
        const tooltip = createUsageChartTooltip(tooltipEl);

        g.selectAll("rect.cal-day")
            .data(model.cells)
            .join("rect")
            .attr("class", "cal-day")
            .attr("x", d => labelLeft + d.week * step)
            .attr("y", d => topPad + d.dow * step)
            .attr("width", cellSize)
            .attr("height", cellSize)
            .attr("rx", 2.5)
            .attr("fill", d => d.level === 0 ? "var(--rowHover)" : "var(--chart-blue-main)")
            .attr("fill-opacity", d => {
                const isDimmed = highlightedLevel !== null && d.level !== highlightedLevel;
                if (!d.inPeriod) {
                    if (d.level === 0) return isDimmed ? 0.14 : 0.32;
                    return isDimmed ? 0.06 : levelOpacity[d.level] * 0.36;
                }
                if (d.level === 0) return isDimmed ? 0.25 : 1;
                return isDimmed ? 0.1 : levelOpacity[d.level];
            })
            .attr("stroke", "var(--borderColor)")
            .attr("stroke-opacity", d => d.inPeriod ? 0.45 : 0.22)
            .attr("stroke-width", 0.75)
            .on("mouseenter", function (ev: MouseEvent, d) {
                select(this).attr("stroke", "var(--textPrimary)").attr("stroke-opacity", d.inPeriod ? 0.9 : 0.5).attr("stroke-width", 1.5);
                tooltipEl.innerHTML = "";
                const head = document.createElement("div");
                head.style.fontWeight = "700";
                head.style.marginBottom = "3px";
                head.textContent = new Date(d.ts).toLocaleDateString(undefined, {
                    weekday: "short",
                    month: "short",
                    day: "numeric",
                    year: "numeric",
                    timeZone: "UTC",
                });
                tooltipEl.append(head);

                const isFuture = d.ts > model.todayUtc;
                if (!isFuture || d.value > 0) {
                    const row = document.createElement("div");
                    row.style.display = "flex";
                    row.style.justifyContent = "space-between";
                    row.style.gap = "12px";
                    row.style.fontSize = "12px";
                    appendUsageTooltipText(row, "Consumption", usageFormatValue(unit, d.value));
                    tooltipEl.append(row);
                }

                if (!d.inPeriod) {
                    const note = document.createElement("div");
                    note.style.marginTop = (!isFuture || d.value > 0) ? "4px" : "0";
                    note.style.fontSize = "11.5px";
                    note.style.color = "var(--textSecondary)";
                    note.textContent = "Outside selected period";
                    tooltipEl.append(note);
                }

                for (const lifeEv of d.events) {
                    const evRow = document.createElement("div");
                    evRow.style.marginTop = "4px";
                    evRow.style.fontSize = "11.5px";
                    evRow.style.color = lifeEv.type === "start" ? "var(--chart-green-main)" : "var(--warningMain)";
                    const label = lifeEv.type === "start" ? "Grant started" : lifeEv.type === "retire" ? "Retired" : "Expired";
                    const sign = lifeEv.quotaDelta >= 0 ? "+" : "-";
                    evRow.textContent = `${label} (${sign}${usageFormatValue(unit, Math.abs(lifeEv.quotaDelta))})`;
                    tooltipEl.append(evRow);
                }
                tooltip.moveListener(ev);
            })
            .on("mousemove", (ev: MouseEvent) => {
                tooltip.moveListener(ev);
            })
            .on("mouseleave", function (_ev: MouseEvent, d) {
                select(this)
                    .attr("stroke", "var(--borderColor)")
                    .attr("stroke-opacity", d.inPeriod ? 0.45 : 0.22)
                    .attr("stroke-width", 0.75);
                tooltip.leaveListener();
            });

        const eventCells = model.cells.filter(c => c.events.length > 0);
        if (eventCells.length > 0) {
            g.selectAll("circle.cal-event-dot")
                .data(eventCells)
                .join("circle")
                .attr("class", "cal-event-dot")
                .attr("cx", d => labelLeft + d.week * step + cellSize / 2)
                .attr("cy", d => topPad + d.dow * step + cellSize / 2)
                .attr("r", Math.max(2, cellSize * 0.22))
                .attr("fill", d => d.events.some(e => e.type === "start") ? "var(--chart-green-main)" : "var(--warningMain)")
                .attr("stroke", "var(--backgroundDefault)")
                .attr("stroke-width", 1.5)
                .attr("pointer-events", "none");
        }
    }, [model, unit, cellSize, step, highlightedLevel]);

    const levelLabels = ["No usage", "Low", "Moderate", "High", "Peak"];

    return <div className={CalendarStyle}>
        <div className="cal-scroll">
            <svg ref={chartRef} width={svgWidth} height={svgHeight} style={{display: "block"}} />
        </div>
        <div className="cal-legend">
            <span>Less</span>
            <span className="cal-legend-swatches">
                {[0, 1, 2, 3, 4].map(level => (
                    <button
                        key={level}
                        type="button"
                        className="cal-legend-cell"
                        title={levelLabels[level]}
                        aria-label={levelLabels[level]}
                        data-dimmed={highlightedLevel !== null && highlightedLevel !== level ? "true" : undefined}
                        style={{
                            background: level === 0 ? "var(--rowHover)" : "var(--chart-blue-main)",
                            opacity: level === 0 ? 1 : levelOpacity[level],
                            boxShadow: level === 0 ? "inset 0 0 0 1px var(--borderColor)" : undefined,
                        }}
                        onMouseEnter={() => setHighlightedLevel(level)}
                        onMouseLeave={() => setHighlightedLevel(null)}
                        onClick={() => setHighlightedLevel(prev => prev === level ? null : level)}
                    />
                ))}
            </span>
            <span>More</span>
            {model.clamped ? <span style={{marginLeft: 12}}>Showing last 12 months</span> : null}
        </div>
    </div>;
}

const ChartDialogStyle = injectStyle("usage-v2-chart-dialog", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
        gap: 12px;
    }

    ${k} .cd-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        flex-shrink: 0;
    }

    ${k} .cd-header h3 {
        margin: 0;
        font-size: 16px;
    }

    ${k} .cd-body {
        flex: 1 1 0;
        min-height: 0;
        min-width: 0;
        overflow: hidden;
    }
`);

const chartModalStyle: ReactModal.Styles = {
    content: {
        borderRadius: "6px",
        width: "min(1200px, calc(100vw - 48px))",
        height: "min(720px, calc(100vh - 64px))",
        maxHeight: "calc(100vh - 64px)",
        position: "fixed",
        top: "50%",
        left: "50%",
        transform: "translate(-50%, -50%)",
        outline: "none",
        padding: "20px 24px",
        overflow: "hidden",
        boxSizing: "border-box",
        backgroundColor: "var(--backgroundCard)",
        color: "var(--textPrimary)",
        border: "1px solid var(--borderColor)",
    },
    overlay: {
        backgroundColor: "var(--modalShadow)",
        zIndex: 102,
    },
};

function UsageChartDialogBody(props: {
    title: string;
    renderContent: (width: number, height: number) => React.ReactNode;
}): React.ReactNode {
    const bodyRef = useRef<HTMLDivElement>(null);
    const [size, setSize] = useState<{width: number; height: number}>({width: 900, height: 500});

    useEffect(() => {
        const el = bodyRef.current;
        if (!el) return;
        const update = () => {
            const rect = el.getBoundingClientRect();
            const w = Math.max(320, Math.floor(rect.width));
            const h = Math.max(240, Math.floor(rect.height - 44));
            setSize(prev => (prev.width === w && prev.height === h ? prev : {width: w, height: h}));
        };
        update();
        const observer = new ResizeObserver(update);
        observer.observe(el);
        return () => observer.disconnect();
    }, []);

    useEffect(() => {
        const onKey = (ev: KeyboardEvent) => {
            if (ev.key === "Escape") {
                dialogStore.success();
            }
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, []);

    return <div className={ChartDialogStyle}>
        <div className="cd-header">
            <h3>{props.title}</h3>
            <IconButton tooltip="Close chart dialog" icon="heroXMark" onClick={() => dialogStore.success()} />
        </div>
        <div ref={bodyRef} className="cd-body">
            {props.renderContent(size.width, size.height)}
        </div>
    </div>;
}

export function UsageChartExpandButton({title, renderContent}: {
    title: string;
    renderContent: (width: number, height: number) => React.ReactNode;
}): React.ReactNode {
    return <IconButton
        tooltip={`Expand ${title.toLowerCase()} chart`}
        icon="heroArrowsPointingOut"
        onClick={() => {
            hideUsageChartTooltip();
            dialogStore.addDialog(
                <UsageChartDialogBody title={title} renderContent={renderContent} />,
                doNothing,
                true,
                chartModalStyle,
            );
        }}
    />;
}
