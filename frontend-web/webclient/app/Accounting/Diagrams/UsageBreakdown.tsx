import * as React from "react";
import {UsageReport} from "@/Accounting/UsageCore2";
import {colorNames, contrastColorNames} from "@/Accounting/Diagrams/index";
import {useMemo, useState} from "react";
import {useD3} from "@/Utilities/d3";
import {scaleOrdinal} from "d3-scale";
import {select} from "d3-selection";
import {arc, pie, PieArcDatum} from "d3-shape";
import {HTMLTooltipEx} from "@/ui-components/Tooltip";

export interface BreakdownChartRow {
    child: string;
    color: string;
    value: number;
}

export interface BreakdownChart {
    chartRef: React.RefObject<SVGSVGElement | null>
    table: BreakdownChartRow[];
}

export function useBreakdownChart(
    openReport: UsageReport | null | undefined,
    chartWidth: number,
    chartHeight: number,
    labelFormatter: (child: string | null) => string,
    valueFormatter: (value: number) => string,
    childColors: Map<string, string>,
): BreakdownChart {
    const tableRows = useMemo(() => {
        if (!openReport) return [];

        const latestByChild = new Map<
            string,
            {timestamp: number; usage: number}
        >();

        for (const point of openReport.usageOverTime.childrenAbsolute) {
            const child = point.child ?? "";
            const existing = latestByChild.get(child);

            if (!existing || point.timestamp > existing.timestamp) {
                latestByChild.set(child, {
                    timestamp: point.timestamp,
                    usage: point.usage,
                });
            }
        }

        return [...latestByChild.entries()]
            .sort(([, a], [, b]) => b.usage - a.usage)
            .map(([child, point]) => ({
                child,
                color: childColors.get(child) ?? "#ccc",
                value: point.usage,
            }));
    }, [openReport, childColors]);

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const data = r.usageOverTime.childrenAbsolute;
        if (data.length === 0) return;

        // Data processing
        // -------------------------------------------------------------------------------------------------------------
        const dataSet: [string, number][] = tableRows.map(row => [
            row.child,
            row.value,
        ]);

        const domain = tableRows.map(row => row.child);

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------

        const color = (child: string) =>
            childColors.get(child) ?? "#ccc";

        const contrastColor = scaleOrdinal<string>()
            .domain(domain)
            .range(contrastColorNames)
            .unknown("var(--textPrimary)");

        // Pie series
        // -------------------------------------------------------------------------------------------------------------
        const outerRadius = Math.min(chartWidth, chartHeight) / 2 - 1;
        const pieGenerator = pie<[string, number]>().sort(null).value(d => d[1]);
        const arcGenerator = arc<PieArcDatum<[string, number]>>().innerRadius(0).outerRadius(outerRadius);
        const labelRadius = outerRadius * 0.65;
        const arcLabelGenerator = arc<PieArcDatum<[string, number]>>().innerRadius(labelRadius).outerRadius(labelRadius);

        const arcs = pieGenerator(dataSet);

        // SVG
        // -------------------------------------------------------------------------------------------------------------
        const svg = select(node);
        svg.selectAll("*").remove();

        svg
            .attr("viewBox", [-chartWidth / 2, -chartHeight / 2, chartWidth, chartHeight])

        const pieSlice = svg.append("g")
            .attr("stroke", "white")
            .selectAll()
            .data(arcs)
            .join("path")
            .attr("fill", d => color(d.data[0]))
            .attr("d", d => arcGenerator(d));

        pieSlice.each((datum, index, elements) => {
            const element = elements[index];
            if (!element) return;

            const [title, usage] = datum.data;

            const tooltip = document.createElement("div");
            {
                const bold = document.createElement("b");
                bold.append(labelFormatter(title));
                tooltip.append(bold);
            }

            tooltip.append(document.createElement("br"));

            {
                const bold = document.createElement("b");
                bold.append("Usage: ");
                tooltip.append(bold);
            }
            tooltip.append(valueFormatter(usage));

            const tooltipEvents = HTMLTooltipEx(tooltip, {tooltipContentWidth: 300});
            element.onmousemove = tooltipEvents.moveListener;
            element.onmouseleave = tooltipEvents.leaveListener;
        })

        const labelGroup = svg.append("g")
            .attr("text-anchor", "middle");

        const labels = labelGroup
            .selectAll<SVGTextElement, PieArcDatum<[string, number]>>("text")
            .data(arcs)
            .join("text")
            .attr("fill", d => contrastColor(d.data[0]))
            .attr("transform", d => `translate(${arcLabelGenerator.centroid(d)})`)
            .append("tspan")
            .attr("x", 0)
            .attr("y", "0.7em")
            .text(d => valueFormatter(d.data[1]));

        labels.each(function (d) {
            const text = this;
            const textWidth = text.getComputedTextLength();

            const sliceAngle = d.endAngle - d.startAngle;
            const requiredAngle = textWidth / labelRadius;

            // Extra angular padding around the label.
            const padding = 0.10;

            if (sliceAngle < requiredAngle + padding) {
                select(text.parentElement).remove();
            }
        });

    }, [openReport, chartWidth, chartHeight, labelFormatter]);

    // noinspection UnnecessaryLocalVariableJS
    const result: BreakdownChart = useMemo(() => {
        return {
            chartRef: chart,
            table: tableRows,
        }
    }, [chart, tableRows]);
    return result;
}
