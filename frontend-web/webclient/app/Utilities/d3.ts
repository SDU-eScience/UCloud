import {DependencyList, useEffect, useRef} from "react";
import {timeFormat} from "d3-time-format";
import {timeDay, timeHour, timeMinute, timeMonth, timeSecond, timeWeek, timeYear} from "d3";

export function useD3(render: (elem: SVGSVGElement) => void, deps: DependencyList | undefined) {
    const ref = useRef<SVGSVGElement>(null);
    useEffect(() => {
        if (!ref.current) return;
        return render(ref.current);
    }, deps);
    return ref;
}

const formatMillisecond = timeFormat(".%L"),
    formatSecond = timeFormat(":%S"),
    formatMinute = timeFormat("%H:%M"),
    formatHour = timeFormat("%H:%M"),
    formatDay = timeFormat("%b %d"),
    formatWeek = timeFormat("%b %d"),
    formatMonth = timeFormat("%B"),
    formatYear = timeFormat("%Y");

export function multiFormat(date: Date) {
    return (timeSecond(date) < date ? formatMillisecond
        : timeMinute(date) < date ? formatSecond
            : timeHour(date)   < date ? formatMinute
                : timeDay(date)    < date ? formatHour
                    : timeMonth(date)  < date ? (timeWeek(date) < date ? formatDay : formatWeek)
                        : timeYear(date)   < date ? formatMonth
                            : formatYear)(date);
}
