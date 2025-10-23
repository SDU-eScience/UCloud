import {useD3} from "@/Utilities/d3";
import {scaleBand, scaleLinear, scaleOrdinal} from "d3-scale";
import {index, max, min, union} from "d3-array";
import {stack} from "d3-shape";
import {select} from "d3-selection";
import {timeFormat} from "d3-time-format";
import {axisBottom, axisLeft} from "d3-axis";
import {UsageReport} from "@/Accounting/UsageCore2";
import React, {useMemo, useState} from "react";
import {ChartLabel} from "@/Accounting/Diagrams/index";

export interface DeltaOverTimeChart {
    chartRef: React.RefObject<SVGSVGElement | null>
    labels: ChartLabel[];
}

export function useDeltaOverTimeChart(
    openReport: UsageReport | null | undefined,
    chartWidth: number,
    chartHeight: number
): DeltaOverTimeChart {
    const [childrenLabels, setChildrenLabels] = useState<ChartLabel[]>([]);

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const data = r.usageOverTime.delta.filter(it => it.timestamp > 0); // TODO backend shouldn't return this
        if (data.length === 0) return;

        // Dimensions and margin
        // -------------------------------------------------------------------------------------------------------------
        const margin = {
            top: 16,
            bottom: 70,
            left: 40,
            right: 16,
        };

        const innerW = chartWidth - margin.left - margin.right;
        const innerH = chartHeight - margin.top - margin.bottom;

        // X-axis
        // -------------------------------------------------------------------------------------------------------------
        const timestamps: number[] = [];
        let prevTimestamp = 0;
        for (const d of data) {
            if (d.timestamp !== prevTimestamp) {
                timestamps.push(d.timestamp);
                prevTimestamp = d.timestamp;
            }
        }

        const xSlot = scaleBand<number>()
            .domain(timestamps)
            .range([0, innerW])
            .paddingInner(0.15);

        // Series
        // -------------------------------------------------------------------------------------------------------------
        const byTimestampKey = index(data, d => d.timestamp, d => d.child);

        const seriesGenerator = stack<number>()
            .keys(union(data.map(it => it.child ?? "")))
            .value((ts, key) => {
                return byTimestampKey.get(ts)?.get(key)?.change ?? 0;
            });

        const series = seriesGenerator(timestamps);

        // Y-axis
        // -------------------------------------------------------------------------------------------------------------
        const yScaleMin = min(series, datum => {
            return min(datum, d => d[0])
        }) ?? 0;

        const yScaleMax = max(series, datum => {
            return max(datum, d => d[1])
        }) ?? 100;

        const yScale = scaleLinear().domain([yScaleMin, yScaleMax]).nice().range([innerH, margin.top]);

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------
        const color = scaleOrdinal<string>()
            .domain(series.map(d => d.key ?? ""))
            .range(["#1f77b4", "#ff7f0e", "#2ca02c", "#d62728", "#9467bd", "#8c564b", "#e377c2", "#7f7f7f",
                "#bcbd22", "#17becf"])
            .unknown("#ccc");

        setChildrenLabels(series.map(d => {
            return {child: d.key, color: color(d.key ?? "")};
        }));

        // SVG
        // -------------------------------------------------------------------------------------------------------------
        const svg = select(node);
        svg.selectAll("*").remove();

        svg.attr("style", "max-width: 100%; height: auto;")

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        g.selectAll()
            .data(series)
            .join("g")
            .attr("fill", d => color(d.key))
            .selectAll("rect")
            .data(D => D.map(d => d))
            .join("rect")
            .attr("x", d => {
                return xSlot(d.data) ?? 0;
            })
            .attr("y", d => yScale(d[1]))
            .attr("height", d => yScale(d[0]) - yScale(d[1]))
            .attr("width", xSlot.bandwidth());

        const tsFormatter = timeFormat("%b %d %H:%M");

        const gXAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${innerH + margin.top})`)
            .call(
                axisBottom(xSlot)
                    .tickFormat(d => tsFormatter(new Date(d)))
            );

        gXAxis.selectAll(".tick > text")
            .attr("style", "transform: translate(-20px, 20px) rotate(-45deg)")

        const gYAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${margin.top})`)
            .call(axisLeft(yScale).ticks(null, "s"));

        for (const gAxis of [gXAxis, gYAxis]) {
            gAxis.selectAll("path, line")
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round")
                .style("stroke", "var(--borderColor)");
        }
    }, [openReport, chartWidth, chartHeight])

    // noinspection UnnecessaryLocalVariableJS
    const result: DeltaOverTimeChart = useMemo(() => {
        return {
            chartRef: chart,
            labels: childrenLabels,
        }
    }, [chart, childrenLabels]);

    return result;
}
