import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {LineSample, Line, TemporalLineChart} from "@/ui-components/TemporalLineChart";

function addSample(prev: LineSample[]): LineSample[] {
    function last<T>(array: T[]): T {
        return array[array.length - 1];
    }

    const spike = Math.random() < 0.0003 ? 30 + Math.random() * 10 : 0;
    const t = new Date();

    let v: number;
    if (prev.length > 0) v = last(prev).v;
    else v = 25 + Math.random() * 10;

    v = Math.max(0, Math.min(100, v + (Math.random() - 0.5) * 6 + spike));

    const next = [...prev, {t, v}];
    const cutoff = new Date(t.getTime() - 30 * 60 * 1000); // keep ≤ 30 min
    const i = next.findIndex((d) => d.t >= cutoff);
    return i <= 0 ? next : next.slice(i);
}

export const CpuChartDemo: React.FunctionComponent = () => {
    const [lines, setLines] = useState<Line[]>([
        {
            name: "Read",
            color: "var(--blue-fg)",
            data: [],
        },
        {
            name: "Write",
            color: "var(--green-fg)",
            data: [],
        }
    ]);
    const windowMs = 5 * 60 * 1000; // 5 minutes
    const intervalMs = 50;          // 1 Hz
    const running = useRef(false);

    useEffect(() => {
        running.current = true;
        const id = window.setInterval(() => {
            setLines((prev) => {
                const newLines: Line[] = [];
                let idx = 0;
                for (const line of prev) {
                    const newLine: Line = {...line, data: addSample(line.data)}
                    newLines.push(newLine);
                    idx++;
                }
                return newLines;
            });
        }, intervalMs);

        return () => {
            running.current = false;
            window.clearInterval(id);
        };
    }, []);

    const yTickFormatter = useCallback((value: number, isAxis: boolean): string => {
        if (isAxis) {
            return `${value}%`;
        } else {
            return `${value.toFixed(1)}%`;
        }
    }, []);

    return (
        <TemporalLineChart
            lines={lines}
            liveDomainMs={windowMs}
            yTickFormatter={yTickFormatter}
        />
    );
}
