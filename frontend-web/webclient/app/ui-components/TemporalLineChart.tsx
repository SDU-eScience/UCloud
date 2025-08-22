import {DependencyList, useEffect, useId, useRef} from "react";
import {pointer, select} from "d3-selection";
import {Selection} from "d3";
import {bisector, extent} from "d3-array";
import {scaleLinear, scaleTime} from "d3-scale";
import {axisBottom, axisLeft} from "d3-axis";
import {timeFormat} from "d3-time-format";
import {line} from "d3-shape";
import * as React from "react";

function useD3(render: (elem: SVGSVGElement) => void, deps: DependencyList | undefined) {
    const ref = useRef<SVGSVGElement>(null);
    useEffect(() => {
        if (!ref.current) return;
        return render(ref.current);
    }, deps);
    return ref;
}

export interface LineSample {
    t: Date;
    v: number;
}

export interface Line {
    name: string;
    color: string;
    data: LineSample[];
}

function last<T>(array: T[]): T {
    return array[array.length - 1];
}

export interface TemporalLineChartProps {
    // Style
    width?: number;
    height?: number;
    spaceForLabel?: number;

    // Data
    lines: Line[];

    // Axis configuration
    liveDomainMs?: number;
    yDomain?: readonly [number, number];
    yTickFormatter: (value: number, isAxis: boolean) => string;
}

export function TemporalLineChart(
    {
        lines,
        width = 750,
        height = 150,
        liveDomainMs = 5 * 60 * 1000,
        yDomain = [0, 100],
        spaceForLabel = 115,
        yTickFormatter,
    }: TemporalLineChartProps
) {
    const margin = {
        top: 16,
        bottom: 28,
        left: 44,
        right: 16 + spaceForLabel,
    };

    const clipId = useId().replace(/:/g, "_");
    const innerW = width - margin.left - margin.right;
    const innerH = height - margin.top - margin.bottom;

    const ref = useD3((node: SVGSVGElement) => {
        // Panel and setup
        // -------------------------------------------------------------------------------------------------------------
        const svg = select(node);
        svg.selectAll("*").remove();

        svg.attr("width", width).attr("height", height);

        const g = svg
            .append("g")
            .attr("transform", `translate(${margin.left},${margin.top})`);

        svg
            .append("defs")
            .append("clipPath")
            .attr("id", clipId)
            .append("rect")
            .attr("width", innerW)
            .attr("height", innerH);

        const overlay = g
            .append("rect")
            .attr("pointer-events", "all")
            .attr("fill", "transparent")
            .attr("width", innerW)
            .attr("height", innerH) as unknown as Selection<SVGRectElement, unknown, null, undefined>;

        // X and Y axis configuration
        // -------------------------------------------------------------------------------------------------------------
        const now = lines[0].data.length ? lines[0].data[lines[0].data.length - 1].t : new Date();
        const xDomain: [Date, Date] =
            lines[0].data.length
                ? [new Date(now.getTime() - liveDomainMs), now]
                : (extent(lines[0].data, (d) => d.t) as [Date, Date]) ?? [now, now];

        const xScale = scaleTime().domain(xDomain).range([0, innerW]);
        const yScale = scaleLinear().domain(yDomain as [number, number]).nice().range([innerH, 0]);

        const gXAxis = g
            .append("g")
            .attr("transform", `translate(0,${innerH})`)
            .call(axisBottom(xScale).tickFormat(timeFormat("%H:%M")));

        gXAxis.selectAll("path, line")
            .style("stroke-width", 2)
            .style("stroke-linejoin", "round")
            .style("stroke", "var(--borderColor)");

        const gYAxis = g
            .append("g")
            .call(
                axisLeft(yScale)
                    .ticks(5)
                    .tickFormat((d) => yTickFormatter(d as number, true))
            );

        gYAxis.selectAll("path, line")
            .style("stroke-width", 2)
            .style("stroke-linejoin", "round")
            .style("stroke", "var(--borderColor)");

        const grid = g.append("g")
            .attr("clip-path", `url(#${clipId})`)
            .attr("pointer-events", "none")
            .attr("shape-rendering", "crispEdges")
            .attr("stroke", "currentColor")
            .attr("stroke-opacity", 0.12);

        const yTicks = yScale.ticks(5); // match axisLeft(yScale).ticks(5)
        grid.selectAll<SVGLineElement, number>("line")
            .data(yTicks)
            .join("line")
            .attr("x1", 0)
            .attr("x2", innerW)
            .attr("y1", (d) => yScale(d))
            .attr("y2", (d) => yScale(d));

        // Line plot
        // -------------------------------------------------------------------------------------------------------------
        const lineGenerator = line<LineSample>()
            .defined((d) => Number.isFinite(d.v))
            .x((d) => xScale(d.t))
            .y((d) => yScale(d.v))

        const clipped = g.append("g").attr("clip-path", `url(#${clipId})`);

        clipped.selectAll(".line")
            .data(lines)
            .enter().append("path")
            .attr("d", d => lineGenerator(d.data))
            .style("fill", "none")
            .style("stroke", d => d.color)
            .style("stroke-width", 2)
            .style("stroke-linejoin", "round");

        const valueLabel = g.selectAll(".label")
            .data(lines)
            .enter().append("g")
            .attr("class", "label")
            .attr("transform", d => {
                if (d.data.length === 0) return null;
                return `translate(${xScale(last(d.data).t)}, ${yScale(last(d.data).v)})`;
            });

        valueLabel.append("circle")
            .attr("r", 4)
            .style("stroke", "white")
            .style("fill", d => d.color);

        const labels = valueLabel.append("text")
            .text(d => d.data.length > 0 ? `${d.name}: ${yTickFormatter(last(d.data).v, false)}` : "")
            .attr("dy", 5)
            .attr("dx", 10)
            .style("font-family", "monospace")
            .style("fill", d => d.color);

        // Sort labels and deal with overlap
        // -------------------------------------------------------------------------------------------------------------
        {
            const padding = 2; // breathing room between labels (px)

            const textNodes = labels.nodes() as SVGTextElement[];
            const groupNodes = valueLabel.nodes() as SVGGElement[];

            type Item = {
                txt: SVGTextElement;
                g: SVGGElement;
                anchorY: number;   // group's global Y (line endpoint)
                boxY: number;      // text bbox.y in group-local coords
                height: number;    // text bbox.height
            };

            const items: Item[] = textNodes.map((txt, i) => {
                const g = groupNodes[i];
                const d = (select(g).datum() as Line);
                if (!d || !d.data.length) return null as unknown as Item;

                const lastPt = last(d.data);
                const anchorY = yScale(lastPt.v);
                const bbox = (txt as SVGTextElement).getBBox(); // local to group
                return { txt, g, anchorY, boxY: bbox.y, height: bbox.height };
            }).filter(Boolean);

            if (!items.length) return;

            // Sort by natural top (global), which keeps labels visually sorted
            items.sort((a, b) => (a.anchorY + a.boxY) - (b.anchorY + b.boxY));

            // Desired tops in GLOBAL coordinates
            const tops: number[] = items.map(it => it.anchorY + it.boxY);

            // Pass 1: top-down — keep spacing and top bound
            for (let i = 0; i < items.length; i++) {
                const h = items[i].height;
                if (i === 0) {
                    tops[i] = Math.max(0, tops[i]);
                } else {
                    const minTop = tops[i - 1] + items[i - 1].height + padding;
                    tops[i] = Math.max(tops[i], minTop);
                }
                // Also ensure it doesn't already exceed bottom
                tops[i] = Math.min(tops[i], innerH - h);
            }

            // Pass 2: bottom-up — enforce bottom bound and allow moving UP if needed
            for (let i = items.length - 1; i >= 0; i--) {
                const h = items[i].height;
                tops[i] = Math.min(tops[i], innerH - h);
                if (i < items.length - 1) {
                    const maxTop = tops[i + 1] - h - padding;
                    tops[i] = Math.min(tops[i], maxTop);
                    tops[i] = Math.max(tops[i], 0);
                }
            }

            // If the topmost is still above 0 (negative), shift the whole stack down
            if (tops[0] < 0) {
                const shift = -tops[0];
                for (let i = 0; i < tops.length; i++) tops[i] += shift;
            }

            // Apply shifts to each text's dy (convert from global back to group-local)
            items.forEach((it, i) => {
                const desiredTopLocal = tops[i] - it.anchorY;   // group-local top
                const dyNeeded = desiredTopLocal - it.boxY;     // how much to move text within the group
                const currentDy = +select(it.txt).attr("dy") || 0;
                select(it.txt).attr("dy", currentDy + dyNeeded);
            });
        }

        // Tooltip
        // -------------------------------------------------------------------------------------------------------------
        const tooltip = g.append("g").style("display", "none");
        let updateTooltip: (px: number) => void;

        {
            const cursorTooltipGap = 8;
            const ttPaddingX = 6;
            const ttPaddingY = 6;

            const fmtTime = timeFormat("%H:%M:%S");
            const bisectDate = bisector<LineSample, Date>((d) => d.t).center;

            const ttLine = tooltip
                .append("line")
                .attr("y1", 0)
                .attr("y2", innerH)
                .attr("stroke", "currentColor")
                .attr("stroke-opacity", 0.35)
            ;

            const marker = tooltip.append("g");

            const ttDot = marker.append("circle").attr("r", 3).attr("fill", "currentColor");
            const ttBg = marker
                .append("rect")
                .attr("rx", 4).attr("ry", 4)
                .attr("fill", "var(--backgroundDefault)")
                .attr("stroke", "var(--borderColor)")
            const ttText = marker
                .append("text")
                .attr("dominant-baseline", "middle");

            updateTooltip = (px) => {
                if (!lines.length) return;
                const data = lines[0].data;
                if (!data.length) return;

                // Find data point at cursor
                // -----------------------------------------------------------------------------------------------------
                const xValueAtCursor = (xScale.invert as (p: number) => Date)(px);
                const index = Math.min(Math.max(bisectDate(data, xValueAtCursor), 0), data.length - 1);
                const referencePoint = data[index];

                // Place marker at data point
                // -----------------------------------------------------------------------------------------------------
                const posX = xScale(referencePoint.t);
                const posY = yScale(referencePoint.v);

                tooltip.attr("transform", `translate(${posX},0)`);
                marker.attr("transform", `translate(0, ${posY})`)

                let label = `${fmtTime(referencePoint.t)}\n`;
                for (const line of lines) {
                    if (line.data.length > index) {
                        label += `${line.name}: ${yTickFormatter(line.data[index].v, false)}`;
                    }
                }
                ttText.text(label).attr("y", -ttPaddingY * 2);

                // Determine size and position of box
                // -----------------------------------------------------------------------------------------------------
                const bbox = (ttText.node() as SVGTextElement).getBBox();
                const boxW = bbox.width + ttPaddingX * 2;
                const boxH = bbox.height + ttPaddingY * 2;

                const wouldOverflowRight = posX + cursorTooltipGap + boxW > innerW;
                const side = wouldOverflowRight ? "left" : "right";

                const labelYOffset = -boxH;

                if (side === "right") {
                    ttText.attr("x", cursorTooltipGap + ttPaddingX).attr("text-anchor", "start");
                    ttBg
                        .attr("x", cursorTooltipGap)
                        .attr("y", labelYOffset)
                        .attr("width", boxW)
                        .attr("height", boxH);
                } else {
                    ttText.attr("x", -(cursorTooltipGap + ttPaddingX)).attr("text-anchor", "end");
                    ttBg
                        .attr("x", -(cursorTooltipGap + boxW))
                        .attr("y", labelYOffset)
                        .attr("width", boxW)
                        .attr("height", boxH);
                }
            }

            overlay
                .on("mouseenter", () => tooltip.style("display", null))
                .on("mouseleave", () => tooltip.style("display", "none"))
                .on("mousemove", (event) => {
                    const [px] = pointer(event as PointerEvent, overlay.node() as Element);
                    updateTooltip(px);
                });
        }

        return () => {
            svg.on(".zoom", null);
        };
    }, [lines, width, height, margin, liveDomainMs, yDomain]);

    return <svg ref={ref} style={{width, height}}/>;
}
