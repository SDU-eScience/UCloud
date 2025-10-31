import * as React from "react";
import {UsageReport} from "@/Accounting/UsageCore2";
import {ChartLabel, colorNames} from "@/Accounting/Diagrams/index";
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
): BreakdownChart {
    const [tableRows, setTableRows] = useState<BreakdownChartRow[]>([]);

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const data = r.usageOverTime.delta;
        if (data.length === 0) return;

        // Data processing
        // -------------------------------------------------------------------------------------------------------------
        const domainSet: Record<string, number> = {};
        for (const point of data) {
            domainSet[point.child ?? ""] = (domainSet[point.child ?? ""] ?? 0) + point.change;
        }

        const domain = Object.keys(domainSet).sort((a, b) => {
            if (domainSet[a] > domainSet[b]) {
                return -1;
            } else if (domainSet[a] < domainSet[b]) {
                return 1;
            } else {
                return 0;
            }
        });
        const dataSet: [string, number][] = Object.entries(domainSet);

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------

        const color = scaleOrdinal<string>()
            .domain(domain)
            .range(colorNames)
            .unknown("#ccc");

        setTableRows(domain.map(d => {
            return {child: d, color: color(d), value: domainSet[d]};
        }));

        // Pie series
        // -------------------------------------------------------------------------------------------------------------
        const outerRadius = Math.min(chartWidth, chartHeight) / 2 - 1;
        const pieGenerator = pie<[string, number]>().sort(null).value(d => d[1]);
        const arcGenerator = arc<PieArcDatum<[string, number]>>().innerRadius(0).outerRadius(outerRadius);
        const labelRadius = outerRadius * 0.8;
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

        svg.append("g")
            .attr("text-anchor", "middle")
            .selectAll()
            .data(arcs)
            .join("text")
            .attr("transform", d => `translate(${arcLabelGenerator.centroid(d)})`)
            .call(text => text.filter(d => (d.endAngle - d.startAngle) > 0.10).append("tspan")
                .attr("x", 0)
                .attr("y", "0.7em")
                .text(d => valueFormatter(d.data[1])));
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
