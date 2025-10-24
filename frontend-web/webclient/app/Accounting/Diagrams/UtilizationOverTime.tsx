import {useD3} from "@/Utilities/d3";
import {scaleBand, scaleLinear, scaleOrdinal} from "d3-scale";
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

export interface UtilizationOverTimeTableRow {
    title: string;
    color: string;

    min: string;
    mean: string;
    max: string;
}

export interface UtilizationOverTimeChart {
    chartRef: React.RefObject<SVGSVGElement | null>
    rows: UtilizationOverTimeTableRow[];
}

export function useUtilizationOverTimeChart(
    openReport: UsageReport | null | undefined,
    chartWidth: number,
    chartHeight: number,
    unit: FrontendAccountingUnit | null | undefined,
): UtilizationOverTimeChart {
    const clipId = useId().replace(/:/g, "_");
    const unitNormalizationFactor = unit?.balanceFactor ?? 1;
    const unitName = unit?.name ?? "";
    const [rows, setRows] = useState<UtilizationOverTimeTableRow[]>([]);

    const chart = useD3(node => {
        // Data validation and initial setup
        // -------------------------------------------------------------------------------------------------------------
        const r = openReport;
        if (r == null) return;

        const data = r.usageOverTime.absolute.filter(it => it.timestamp > 0); // TODO backend shouldn't return this
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

        // Data processing
        // -------------------------------------------------------------------------------------------------------------
        let minUsage = Number.MAX_SAFE_INTEGER;
        let maxUsage = Number.MIN_SAFE_INTEGER;
        const usageArray: number[] = [];

        let minUtilization = 100;
        let maxUtilization = 0;
        const utilizationArray: number[] = [];

        const timestampToSlot: Record<number, number> = {};
        const timestamps: number[] = [];
        let prevTimestamp = 0;
        {
            let idx = 0;
            for (const d of data) {
                timestampToSlot[d.timestamp] = idx++;

                if (d.timestamp !== prevTimestamp) {
                    timestamps.push(d.timestamp);
                    prevTimestamp = d.timestamp;
                }

                if (d.usage < minUsage) minUsage = d.usage;
                if (d.usage > maxUsage) maxUsage = d.usage;
                usageArray.push(d.usage);

                if (d.utilizationPercent100 < minUtilization) minUtilization = d.utilizationPercent100;
                if (d.utilizationPercent100 > maxUtilization) maxUtilization = d.utilizationPercent100;
                utilizationArray.push(d.utilizationPercent100);
            }
        }

        usageArray.sort();
        utilizationArray.sort();

        const meanUsage = usageArray[Math.floor(usageArray.length / 2)] * unitNormalizationFactor;
        const meanUtilization = utilizationArray[Math.floor(utilizationArray.length / 2)];
        console.log({l: usageArray.length, middle: usageArray.length/2, meanUsage});

        minUsage *= unitNormalizationFactor;
        maxUsage *= unitNormalizationFactor;

        const xSlot = scaleBand<number>()
            .domain(timestamps)
            .range([0, innerW])
            .paddingInner(0.15);

        function xSlotInverse(pixel: number): number | null {
            const domain = xSlot.domain();
            const step = xSlot.step();
            const rangeStart = xSlot.range()[0];

            const index = Math.floor((pixel - rangeStart) / step);

            if (index < 0 || index >= domain.length) {
                return null;
            }

            return domain[index];
        }

        // Series and Y-axis
        // -------------------------------------------------------------------------------------------------------------

        const usageYScale = scaleLinear()
            .domain([minUsage, maxUsage])
            .nice()
            .range([innerH, 0]);

        const usageLineGenerator = line<UsageReportAbsoluteDataPoint>()
            .defined((d) => Number.isFinite(d.usage))
            .x((d) => xSlot(d.timestamp) ?? 0)
            .y((d) => usageYScale(d.usage * unitNormalizationFactor));

        const utilizationYScale = scaleLinear()
            .domain([0, 1])
            .nice()
            .range([innerH, 0]);

        const utilizationLineGenerator = line<UsageReportAbsoluteDataPoint>()
            .defined((d) => Number.isFinite(d.usage))
            .x((d) => xSlot(d.timestamp) ?? 0)
            .y((d) => utilizationYScale(d.utilizationPercent100 / 100));

        // Color scheme
        // -------------------------------------------------------------------------------------------------------------
        const color = scaleOrdinal<string>()
            .domain(["usage", "utilization"])
            .range(colorNames)
            .unknown("#ccc");

        const balanceToString = (normalizedBalance: number) => {
            return balanceToStringFromUnit(null, unitName, normalizedBalance, { referenceBalance: 1000, removeUnitIfPossible: true });
        };

        setRows([
            {
                title: "Usage",
                color: color("usage"),
                min: balanceToString(minUsage),
                max: balanceToString(maxUsage),
                mean: balanceToString(meanUsage),
            },
            {
                title: "Utilization",
                color: color("utilization"),
                min: minUtilization.toFixed(2) + "%",
                max: maxUtilization.toFixed(2) + "%",
                mean: meanUtilization.toFixed(2) + "%",
            }
        ]);

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
        const gXAxis = g
            .append("g")
            .attr("transform", `translate(0, ${innerH})`)
            .call(axisBottom(xSlot).tickFormat(d => tsFormatter(new Date(d))));

        gXAxis.selectAll(".tick > text")
            .attr("style", "transform: translate(-20px, 20px) rotate(-45deg)")

        const grid = g.append("g")
            .attr("clip-path", `url(#${clipId})`)
            .attr("pointer-events", "none")
            .attr("shape-rendering", "crispEdges")
            .attr("stroke", "currentColor")
            .attr("stroke-opacity", 0.12);

        const utilToUsage = scaleLinear()
            .domain(utilizationYScale.domain())
            .range(usageYScale.domain());

        const utilTicks = utilizationYScale.ticks(5);
        const usageTicks = utilTicks.map(utilToUsage);

        const usageYAxis = svg.append("g")
            .attr("transform", `translate(${margin.left}, ${margin.top})`)
            .call(axisLeft(usageYScale).tickValues(usageTicks).ticks(5, "s"));

        usageYAxis.select(".tick:last-of-type text")
            .clone()
            .attr("x", 3)
            .attr("text-anchor", "start")
            .attr("font-weight", "bold")
            .text(`↑ Usage (${unitName})`);

        const utilizationYAxis = svg.append("g")
            .attr("transform", `translate(${innerW + margin.left}, ${margin.top})`)
            .call(axisRight(utilizationYScale).tickValues(utilTicks).ticks(5, "%"));

        utilizationYAxis.select(".tick:last-of-type text")
            .clone()
            .attr("x", -110)
            .attr("text-anchor", "start")
            .attr("font-weight", "bold")
            .text("Utilization (Percent) ↑");

        grid.selectAll<SVGLineElement, number>("line")
            .data(utilTicks)
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", (d) => utilizationYScale(d))
            .attr("y2", (d) => utilizationYScale(d));

        for (const gAxis of [gXAxis, usageYAxis, utilizationYAxis]) {
            gAxis.selectAll("path, line")
                .style("stroke-width", 2)
                .style("stroke-linejoin", "round")
                .style("stroke", "var(--borderColor)");
        }

        // SVG lines
        // -------------------------------------------------------------------------------------------------------------
        const clipped = g.append("g").attr("clip-path", `url(#${clipId})`);

        clipped.selectAll(".line")
            .data([1])
            .enter().append("path")
            .attr("d", utilizationLineGenerator(data))
            .style("fill", "none")
            .style("stroke", color("utilization"))
            .style("stroke-width", 2)
            .style("stroke-linejoin", "round");

        clipped.selectAll(".line")
            .data([1])
            .enter().append("path")
            .attr("d", usageLineGenerator(data))
            .style("fill", "none")
            .style("stroke", color("usage"))
            .style("stroke-width", 2)
            .style("stroke-linejoin", "round");

        // Tooltips
        // -------------------------------------------------------------------------------------------------------------
        const tooltip = document.createElement("div");
        const tooltipListeners = HTMLTooltipEx(tooltip);

        let prevTimeslot = -1;

        overlay.on("mousemove", (ev: MouseEvent) => {
            const [px] = pointer(ev, overlay.node() as Element);

            const timeSlot = xSlotInverse(px);
            if (timeSlot !== prevTimeslot) {
                prevTimeslot = timeSlot ?? 0;
                const dataPoint = data[timestampToSlot[timeSlot ?? 0]];

                tooltip.innerHTML = "";
                {
                    const node = document.createElement("b");
                    node.append(tsFormatter(new Date(timeSlot ?? 0)))
                    tooltip.append(node);
                    tooltip.append(document.createElement("br"));
                }

                const keys: (keyof UsageReportAbsoluteDataPoint)[] = ["usage", "utilizationPercent100"];
                for (const key of keys) {
                    const value = dataPoint[key];

                    const container = document.createElement("div");
                    container.style.display = "flex";
                    container.style.gap = "8px";
                    container.style.alignItems = "center";

                    {
                        const colorSquare = document.createElement("div");
                        colorSquare.style.width = "14px";
                        colorSquare.style.height = "14px";
                        colorSquare.style.background = color(key == "utilizationPercent100" ? "utilization" : "usage");

                        container.append(colorSquare);
                    }

                    {
                        const node = document.createElement("b");
                        const title = key === "utilizationPercent100" ? "Utilization" : "Usage";
                        node.append(`${title}:`);

                        container.append(node);
                    }

                    {
                        const node = document.createElement("div");
                        if (key === "utilizationPercent100") {
                            node.append(value.toFixed(2) + "%");
                        } else {
                            node.append(balanceToString(value));
                        }

                        container.append(node);
                    }

                    tooltip.append(container);
                }
            }
            tooltipListeners.moveListener(ev);
        });

        overlay.on("mouseleave", tooltipListeners.leaveListener);
    }, [openReport, chartWidth, chartHeight])

    // noinspection UnnecessaryLocalVariableJS
    const result: UtilizationOverTimeChart = useMemo(() => {
        return {
            chartRef: chart,
            rows: rows,
        }
    }, [chart, rows]);

    return result;
}
