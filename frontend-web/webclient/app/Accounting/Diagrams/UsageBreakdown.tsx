import * as React from "react";
import {UsageReport} from "@/Accounting/UsageCore2";
import {ChartLabel} from "@/Accounting/Diagrams/index";
import {useMemo, useState} from "react";
import {useD3} from "@/Utilities/d3";
import {scaleOrdinal} from "d3-scale";
import {select} from "d3-selection";
import {arc, pie, PieArcDatum} from "d3-shape";
import {schemeSpectral} from "d3";

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
    labelGenerator: (child: string | null) => string,
): BreakdownChart {
    const [tableRows, setTableRows] = useState<BreakdownChartRow[]>([]);

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const data = r.usageOverTime.delta.filter(it => it.timestamp > 0); // TODO backend shouldn't return this
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
        const colorStrength = [40, 80, 60];
        // const shades = ["pink", "purple", "blue", "green", "yellow", "orange", "red"];
        const shades = ["blue", "orange", "green", "red", "purple", "yellow", "pink"];
        const colorNames = colorStrength.flatMap(str => shades.map(shade => `var(--${shade}-${str})`));
        const color = scaleOrdinal<string>()
            .domain(domain)
            // .range(["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f",
            //     "#bcbd22", "#17becf"])
            .range(colorNames)
            .unknown("#ccc");


        setTableRows(domain.map(d => {
            return {child: d, color: color(d), value: domainSet[d][1]};
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
            .attr("style", "max-width: 100%; height: auto;")
            .attr("viewBox", [-chartWidth / 2, -chartHeight / 2, chartWidth, chartHeight])

        svg.append("g")
            .attr("stroke", "white")
            .selectAll()
            .data(arcs)
            .join("path")
            .attr("fill", d => color(d.data[0]))
            .attr("d", d => arcGenerator(d));

        svg.append("g")
            .attr("text-anchor", "middle")
            .selectAll()
            .data(arcs)
            .join("text")
            .attr("transform", d => `translate(${arcLabelGenerator.centroid(d)})`)
            .call(text => text.append("tspan")
                .attr("y", "-0.4em")
                .attr("font-weight", "bold")
                .text(d => labelGenerator(d.data[0])))
            .call(text => text.filter(d => (d.endAngle - d.startAngle) > 0.25).append("tspan")
                .attr("x", 0)
                .attr("y", "0.7em")
                .attr("fill-opacity", 0.7)
                .text(d => d.data[1]));
    }, [openReport, chartWidth, chartHeight, labelGenerator]);

    // noinspection UnnecessaryLocalVariableJS
    const result: BreakdownChart = useMemo(() => {
        return {
            chartRef: chart,
            table: tableRows,
        }
    }, [chart, tableRows]);

    return result;
}
