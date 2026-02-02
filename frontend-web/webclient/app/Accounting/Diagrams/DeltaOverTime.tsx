import {useD3} from "@/Utilities/d3";
import {scaleBand, scaleLinear, scaleOrdinal} from "d3-scale";
import {index, max, min, union} from "d3-array";
import {Series, stack} from "d3-shape";
import {select} from "d3-selection";
import {timeFormat} from "d3-time-format";
import {axisBottom, axisLeft} from "d3-axis";
import {UsageReport} from "@/Accounting/UsageCore2";
import React, {useMemo, useState} from "react";
import {ChartLabel, colorNames} from "@/Accounting/Diagrams/index";
import {HTMLTooltipEx} from "@/ui-components/Tooltip";
import {balanceToStringFromUnit, FrontendAccountingUnit} from "@/Accounting";
import truncate, {truncateText} from "@/ui-components/Truncate";
import {groupBy} from "@/Utilities/CollectionUtilities";

export interface DeltaOverTimeChart {
    chartRef: React.RefObject<SVGSVGElement | null>
    labels: ChartLabel[];
}

export function useDeltaOverTimeChart(
    openReport: UsageReport | null | undefined,
    chartWidth: number,
    chartHeight: number,
    unit: FrontendAccountingUnit | null,
    childToLabel: (child: string | null) => string,
): DeltaOverTimeChart {
    const [childrenLabels, setChildrenLabels] = useState<ChartLabel[]>([]);

    const unitNormalizationFactor = unit?.balanceFactor ?? 1;
    const unitName = unit?.name ?? "";

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        let data = r.usageOverTime.delta;
        if (data.length === 0) return;

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
        const tsFormatter = timeFormat("%b %d %H:%M");

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

        const summed: Array<{ timestamp: number; child: string | null; change: number }> = [];

        const byTs = groupBy(openReport!.usageOverTime.delta, d => d.timestamp.toString());
        for (const [tsStr, entriesAtTs] of Object.entries(byTs)) {
            const ts = Number(tsStr);
            const byChild = groupBy(entriesAtTs, e => e.child ?? "");
            for (const [childKey, childEntries] of Object.entries(byChild)) {
                const change = childEntries.reduce((acc, e) => acc + e.change, 0);
                summed.push({ timestamp: ts, child: childKey === "" ? null : childKey, change });
            }
        }

        data = summed;

        const byTimestampKey = index(data, d => d.timestamp, d => d.child ?? "");

        const seriesGenerator = stack<number>()
            .keys(union(data.map(it => it.child ?? "")))
            .value((ts, key) => {
                return byTimestampKey.get(ts)?.get(key)?.change ?? 0;
            });

        const series = seriesGenerator(timestamps);

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------
        const color = scaleOrdinal<string>()
            .domain(domain)
            .range(colorNames)
            .unknown("#ccc");

        setChildrenLabels(domain.map(d => {
            return {child: d, color: color(d ?? "")};
        }));

        // Tooltips
        // -------------------------------------------------------------------------------------------------------------
        const tooltips: Record<number, ReturnType<typeof HTMLTooltipEx>> = {};
        for (const ts of timestamps) {
            const usageBucket = byTimestampKey.get(ts);
            if (!usageBucket) continue;

            const tooltip = document.createElement("div");
            {
                const bold = document.createElement("b");
                bold.append(tsFormatter(new Date(ts)));

                tooltip.append(bold);
                tooltip.append(document.createElement("br"));
            }

            for (const child of domain) {
                const container = document.createElement("div");
                container.style.display = "flex";
                container.style.gap = "8px";
                container.style.alignItems = "center";

                {
                    const colorSquare = document.createElement("div");
                    colorSquare.style.width = "14px";
                    colorSquare.style.height = "14px";
                    colorSquare.style.background = color(child);

                    container.append(colorSquare);
                }

                {
                    const name = document.createElement("div");
                    name.append(truncateText(childToLabel(child), 30));
                    name.style.flexGrow = "1";
                    container.append(name);
                }

                {
                    const node = document.createElement("div");
                    const change = usageBucket.get(child)?.change ?? 0;
                    node.append(balanceToStringFromUnit(null, unitName, change * unitNormalizationFactor));

                    container.append(node);
                }

                tooltip.append(container);
            }

            tooltips[ts] = HTMLTooltipEx(tooltip, { tooltipContentWidth: 400 });
        }

        // Y-axis
        // -------------------------------------------------------------------------------------------------------------
        const yScaleMin = (min(series, datum => {
            return min(datum, d => d[0])
        }) ?? 0) * unitNormalizationFactor;

        const yScaleMax = (max(series, datum => {
            return max(datum, d => d[1])
        }) ?? 100) * unitNormalizationFactor;

        const yScale = scaleLinear().domain([yScaleMin, yScaleMax]).nice().range([innerH, margin.top]);

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
            .attr("y", d => yScale(d[1] * unitNormalizationFactor))
            .attr("height", d => yScale(d[0] * unitNormalizationFactor) - yScale(d[1] * unitNormalizationFactor))
            .attr("width", xSlot.bandwidth())
            .each((datum, index, elements) => {
                const rect = elements[index] as SVGRectElement;
                const ts = datum.data;
                const tooltip = tooltips[ts];

                if (tooltip) {
                    rect.onmousemove = tooltip.moveListener;
                    rect.onmouseleave = tooltip.leaveListener;
                }
            })
        ;

        // X-axis tick throttling
        // -------------------------------------------------------------------------------------------------------------
        const maxTickLabels = Math.max(2, Math.floor(innerW / 90)); // ~1 label per 45px
        const step = Math.max(1, Math.ceil(timestamps.length / maxTickLabels));

        const tickTimestamps: number[] = timestamps.filter((_, i) => i % step === 0);

        // Always include last tick so the axis shows the most recent timestamp
        if (tickTimestamps[tickTimestamps.length - 1] !== timestamps[timestamps.length - 1]) {
            tickTimestamps.push(timestamps[timestamps.length - 1]);
        }

        const gXAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${innerH + margin.top})`)
            .call(
                axisBottom(xSlot)
                    .tickValues(tickTimestamps)
                    .tickFormat(d => tsFormatter(new Date(d)))
            );

        gXAxis.selectAll(".tick > text")
            .attr("style", "transform: translate(-20px, 20px) rotate(-45deg)");

        const gYAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${margin.top})`)
            .call(axisLeft(yScale).ticks(null, "s"));

        gYAxis.select(".tick:last-of-type text")
            .clone()
            .attr("x", 3)
            .attr("text-anchor", "start")
            .attr("font-weight", "bold")
            .text(`↑ Usage (${unitName})`);

        for (const gAxis of [gXAxis, gYAxis]) {
            gAxis.selectAll("path, line")
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round")
                .style("stroke", "var(--borderColor)");
        }
    }, [openReport, chartWidth, chartHeight, childToLabel])

    // noinspection UnnecessaryLocalVariableJS
    const result: DeltaOverTimeChart = useMemo(() => {
        return {
            chartRef: chart,
            labels: childrenLabels,
        }
    }, [chart, childrenLabels]);

    return result;
}
