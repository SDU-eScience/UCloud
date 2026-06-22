import {useD3} from "@/Utilities/d3";
import {scaleBand, scaleLinear, scaleOrdinal, scaleTime} from "d3-scale";
import {line} from "d3-shape";
import {pointer, select} from "d3-selection";
import {timeFormat} from "d3-time-format";
import {axisBottom, axisLeft} from "d3-axis";
import {UsageReport, UsageReportAbsoluteDataPoint} from "@/Accounting/UsageCore2";
import React, {useId, useMemo, useState} from "react";
import {colorNames} from "@/Accounting/Diagrams/index";
import {axisRight, Selection} from "d3";
import {balanceToStringFromUnit, FrontendAccountingUnit} from "@/Accounting";
import {HTMLTooltipEx} from "@/ui-components/Tooltip";

export interface UsageOverTimeTableRow {
    child: string;
    color: string;
    usage: number;
}

export interface UsageOverTimeChart {
    chartRef: React.RefObject<SVGSVGElement | null>
    rows: UsageOverTimeTableRow[];
}

export function useUsageOverTimeChart(
    openReport: UsageReport | null | undefined,
    chartWidth: number,
    chartHeight: number,
    unit: FrontendAccountingUnit | null | undefined,
    labelFormatter: (child: string | null) => string,
): UsageOverTimeChart {
    const clipId = useId().replace(/:/g, "_");
    const unitNormalizationFactor = unit?.balanceFactor ?? 1;
    const unitName = unit?.name ?? "";

    const tableRows = useMemo(() => {

        if (!openReport) return [];

        const domainSet: Record<string, number> = {};

        for (const point of openReport.usageOverTime.childrenAbsolute) {
            const key = point.child ?? "";

            domainSet[key] = point.usage ?? 0;
        }

        const domain = Object.keys(domainSet).sort((a, b) =>
            domainSet[b] - domainSet[a]
        );

        const color = scaleOrdinal<string>()
            .domain(domain)
            .range(colorNames)
            .unknown("#ccc");


        return domain.map((child) => ({
            usage: domainSet[child],
            child: child,
            color: color(child),
        }));

    }, [openReport, chartWidth, chartHeight, labelFormatter]);


    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const childData = r.usageOverTime.childrenAbsolute;
        if (childData.length === 0)
            return;

        const children = Array.from(
            new Set(childData.map(d => d.child ?? "Unknown"))
        );

        const groupedData = new Map<string, typeof childData>();

        for (const point of childData) {
            const child = point.child ?? "Unknown";

            if (!groupedData.has(child)) {
                groupedData.set(child, []);
            }

            groupedData.get(child)!.push(point);
        }

        // Dimensions and margin
        // -------------------------------------------------------------------------------------------------------------
        const margin = {
            top: 16,
            bottom: 70,
            left: 40,
            right: 40,
        };

        const innerW = chartWidth - margin.left - margin.right;
        const innerH = chartHeight - margin.top - margin.bottom;

        // Data processing
        // -------------------------------------------------------------------------------------------------------------
        let minUsage = Number.MAX_SAFE_INTEGER;
        let maxUsage = Number.MIN_SAFE_INTEGER;
        const usageArray: number[] = [];

        const timestamps = Array.from(
            new Set(childData.map(d => d.timestamp))
        ).sort((a, b) => a - b);

        for (const d of childData) {
            if (d.usage < minUsage) minUsage = d.usage;
            if (d.usage > maxUsage) maxUsage = d.usage;
            usageArray.push(d.usage);
        }

        usageArray.sort();

        const xSlot = scaleTime()
            .domain([
                new Date(timestamps[0]),
                new Date(timestamps[timestamps.length - 1]),
            ])
            .range([0, innerW]);

        function xSlotInverse(pixel: number): number | null {
            return xSlot.invert(pixel).getTime();
        }

        // Series and Y-axis
        // -------------------------------------------------------------------------------------------------------------

        for (const point of childData) {
            minUsage = Math.min(minUsage, point.usage);
            maxUsage = Math.max(maxUsage, point.usage);
        }

        minUsage *= unitNormalizationFactor;
        maxUsage *= unitNormalizationFactor;

        const usageYScale = scaleLinear()
            .domain([minUsage, maxUsage])
            .nice()
            .range([innerH, 0]);

        const usageLineGenerator = line<typeof childData[number]>()
            .defined(d => Number.isFinite(d.usage))
            .x(d => xSlot(new Date(d.timestamp)))
            .y(d => usageYScale(d.usage * unitNormalizationFactor));

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------
        const color = scaleOrdinal<string>()
            .domain(children)
            .range(colorNames)
            .unknown("#ccc");

        const balanceToString = (normalizedBalance: number) => {
            return balanceToStringFromUnit(null, unitName, normalizedBalance, {referenceBalance: 1000, removeUnitIfPossible: true});
        };

        // SVG frame
        // -------------------------------------------------------------------------------------------------------------
        const svg = select(node);
        svg.selectAll("*").remove();

        svg
            .append("defs")
            .append("clipPath")
            .attr("id", clipId)
            .append("rect")
            .attr("width", innerW)
            .attr("height", innerH);

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        const overlay = g
            .append("rect")
            .attr("pointer-events", "all")
            .attr("fill", "transparent")
            .attr("width", innerW)
            .attr("height", innerH) as unknown as Selection<SVGRectElement, unknown, null, undefined>;

        const tsFormatter = timeFormat("%b %d %H:%M");

        const gXAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${innerH + margin.top})`)
            .call(
                axisBottom(xSlot)
                    .ticks(Math.max(2, Math.floor(innerW / 100)))
                    .tickFormat(d => tsFormatter(d as Date))
            );

        gXAxis.selectAll(".tick > text")
            .attr("style", "transform: translate(-20px, 20px) rotate(-45deg)")

        const grid = g.append("g")
            .attr("clip-path", `url(#${clipId})`)
            .attr("pointer-events", "none")
            .attr("shape-rendering", "crispEdges")
            .attr("stroke", "currentColor")
            .attr("stroke-opacity", 0.12);

        const usageTicks = usageYScale.ticks(5);

        const usageYAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${margin.top})`)
            .call(axisLeft(usageYScale).tickValues(usageTicks).ticks(5, "s"));

        usageYAxis.select(".tick:last-of-type text")
            .clone()
            .attr("x", 3)
            .attr("text-anchor", "start")
            .attr("font-weight", "bold")
            .text(`↑ Usage (${unitName})`);

        // SVG lines
        // -------------------------------------------------------------------------------------------------------------
        const clipped = g.append("g")
            .attr("clip-path", `url(#${clipId})`);

        for (const child of children) {
            const series = groupedData.get(child)!;

            clipped.append("path")
                .datum(series)
                .attr("d", usageLineGenerator)
                .style("fill", "none")
                .style("stroke", color(child))
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round");
        }

        // Tooltips
        // -------------------------------------------------------------------------------------------------------------
        const tooltip = document.createElement("div");
        const tooltipListeners = HTMLTooltipEx(tooltip, {tooltipContentWidth: 400});

        let prevTimeslot = -1;

        const timestampMap = new Map<number, typeof childData>();

        for (const point of childData) {
            const existing = timestampMap.get(point.timestamp);

            if (existing) {
                existing.push(point);
            } else {
                timestampMap.set(point.timestamp, [point]);
            }
        }

        overlay.on("mousemove", (ev: MouseEvent) => {
            const [px] = pointer(ev, overlay.node() as Element);

            const hoveredTime = xSlotInverse(px);

            let timeSlot: number | null = null;

            if (hoveredTime !== null) {
                timeSlot = timestamps.reduce((prev, curr) =>
                    Math.abs(curr - hoveredTime) < Math.abs(prev - hoveredTime)
                        ? curr
                        : prev
                );
            }

            if (timeSlot !== prevTimeslot) {
                prevTimeslot = timeSlot ?? 0;

                const points = timestampMap.get(timeSlot ?? 0) ?? [];

                tooltip.innerHTML = "";

                const title = document.createElement("b");
                title.append(tsFormatter(new Date(timeSlot ?? 0)));
                tooltip.append(title);
                tooltip.append(document.createElement("br"));

                for (const point of points) {
                    const container = document.createElement("div");
                    container.style.display = "flex";
                    container.style.gap = "8px";
                    container.style.alignItems = "center";

                    const square = document.createElement("div");
                    square.style.width = "14px";
                    square.style.height = "14px";
                    square.style.background = color(point.child ?? "Unknown");
                    container.append(square);

                    const label = document.createElement("b");
                    label.append(`${labelFormatter(point.child)}:`);
                    container.append(label);

                    const value = document.createElement("div");
                    value.append(
                        balanceToString(point.usage * unitNormalizationFactor)
                    );
                    container.append(value);

                    tooltip.append(container);
                }
            }
            tooltipListeners.moveListener(ev);
        });

        overlay.on("mouseleave", tooltipListeners.leaveListener);
    }, [openReport, chartWidth, chartHeight, labelFormatter]);

    // noinspection UnnecessaryLocalVariableJS
    const result: UsageOverTimeChart = useMemo(() => {
        return {
            chartRef: chart,
            rows: tableRows,
        }
    }, [chart, tableRows]);

    return result;
}
