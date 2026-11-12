import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {Button, Icon} from "@/ui-components";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {UcxTableIconButtonClass} from "@/UCX/UcxBrowser";
import {injectStyle} from "@/Unstyled";
import type {AllocationDateRange} from "@/Accounting/Allocations/AllocationGrid";
import {format} from "date-fns";

const AllocationPeriodFilterClass = injectStyle("allocation-period-filter", k => `
    ${k} .period-trigger { display: flex; align-items: center; gap: 8px; height: 35px; padding: 0 10px; border: 1px solid var(--borderColor); border-radius: 8px; color: var(--textPrimary); font-size: 13px; }
    ${k} .period-trigger > svg { color: var(--textSecondary); flex-shrink: 0; }
`);

const AllocationPeriodPanelClass = injectStyle("allocation-period-panel", k => `
    ${k} { box-sizing: border-box; padding: 20px 12px 12px; color: var(--textPrimary); cursor: default; max-height: min(478px, calc(100vh - 34px)); overflow-y: auto; }
    ${k} h4 { margin: 0; font-size: 14px; font-weight: 600; }
    ${k} .period-presets { display: flex; flex-wrap: wrap; gap: 6px; margin: 8px 0; }
    ${k} .period-presets > button { flex: 1; min-width: 0; padding: 4px 6px; white-space: nowrap; }
    ${k} .period-bounds { display: grid; grid-template-columns: 1fr 1fr; gap: 6px; }
    ${k} .period-bound { display: flex; align-items: center; gap: 6px; padding: 5px 8px; border: 1px solid var(--borderColor); border-radius: 8px; background: transparent; color: var(--textPrimary); cursor: pointer; font-size: 12px; line-height: 16px; }
    ${k} .period-bound small { color: var(--textSecondary); font-size: 11px; }
    ${k} .period-bound[aria-pressed="true"] { border-color: var(--textPrimary); background: color-mix(in srgb, var(--textPrimary) 4%, transparent); }
    ${k} .period-year { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 8px; padding-top: 10px; border-top: 1px solid var(--borderColor); font-size: 14px; }
    ${k} .period-months { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
    ${k} .period-month { height: 28px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--textPrimary); cursor: pointer; font-size: 12px; }
    ${k} .period-month[data-in-range="true"] { background: var(--rowActive); }
    ${k} .period-month:hover { background: var(--rowHover); }
    ${k} .period-month[aria-pressed="true"] { background: var(--textPrimary); color: var(--backgroundDefault); }
    ${k} .period-footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; padding-top: 10px; border-top: 1px solid var(--borderColor); }
    ${k} button:focus-visible { outline: 2px solid var(--textPrimary); outline-offset: 2px; }
`);

export function allocationCurrentCalendarYear(): AllocationDateRange {
    const year = new Date().getFullYear();
    return {start: new Date(year, 0, 1), end: new Date(year, 11, 31)};
}

export function AllocationPeriodFilter(props: {
    value: AllocationDateRange;
    onChange: (value: AllocationDateRange) => void;
    title?: string;
}): React.ReactNode {
    const [open, setOpen] = useState(false);
    const [start, setStart] = useState(props.value.start);
    const [end, setEnd] = useState<Date | null>(props.value.end);
    const [year, setYear] = useState(props.value.start.getFullYear());
    const [step, setStep] = useState<"start" | "end">("start");
    const [hoveredMonth, setHoveredMonth] = useState<number | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!open) return;
        const frame = requestAnimationFrame(() => {
            panelRef.current?.querySelector<HTMLButtonElement>(".period-month[aria-pressed='true']")?.focus();
        });
        return () => cancelAnimationFrame(frame);
    }, [open]);

    const closePicker = () => {
        setOpen(false);
        wrapperRef.current?.querySelector<HTMLElement>("[data-tag='dropdown']")?.focus();
    };
    const selectPreset = (range: AllocationDateRange) => {
        setStart(range.start);
        setEnd(range.end);
        setYear(range.start.getFullYear());
        setStep("start");
        setHoveredMonth(null);
    };
    const startMonth = start.getFullYear() * 12 + start.getMonth();
    const endMonth = end ? end.getFullYear() * 12 + end.getMonth() : null;
    const previewEnd = endMonth ?? (step === "end" ? hoveredMonth : null);
    const rangeStart = previewEnd === null ? startMonth : Math.min(startMonth, previewEnd);
    const rangeEnd = previewEnd === null ? startMonth : Math.max(startMonth, previewEnd);

    return <div
        ref={wrapperRef}
        className={AllocationPeriodFilterClass}
        title={props.title ?? "Filter allocations by period"}
    >
        <ClickableDropdown
            open={open}
            onClose={() => setOpen(false)}
            onOpeningTriggerClick={() => {
                selectPreset(props.value);
                setOpen(true);
            }}
            trigger={<span className="period-trigger">
                <Icon name="heroCalendar" size={16} />
                <span>{format(props.value.start, "MMM yyyy")} – {format(props.value.end, "MMM yyyy")}</span>
                <Icon name="heroChevronDown" size={12} />
            </span>}
            triggerAriaLabel="Choose allocation period"
            focusable
            rightAligned
            width={360}
            height={480}
            keepOpenOnClick
            colorOnHover={false}
            paddingControlledByContent
            noYPadding
        >
            <div ref={panelRef} className={AllocationPeriodPanelClass} role="dialog" aria-label="Choose allocation period">
                <h4>Allocation period</h4>
                <div className="period-presets">
                    <Button color="ghost" height={25} onClick={() => selectPreset(allocationCurrentCalendarYear())}>Current year</Button>
                    <Button color="ghost" height={25} onClick={() => {
                        const previous = new Date().getFullYear() - 1;
                        selectPreset({start: new Date(previous, 0, 1), end: new Date(previous, 11, 31)});
                    }}>Last year</Button>
                    <Button color="ghost" height={25} onClick={() => {
                        const now = new Date();
                        selectPreset({start: new Date(now.getFullYear(), now.getMonth(), 1), end: new Date(now.getFullYear(), now.getMonth() + 12, 0)});
                    }}>Next 12 months</Button>
                </div>
                <div className="period-bounds">
                    <button type="button" className="period-bound" aria-pressed={step === "start"} onClick={() => {
                        setStep("start");
                        setYear(start.getFullYear());
                    }}><small>From</small><span>{format(start, "MMM yyyy")}</span></button>
                    <button type="button" className="period-bound" aria-pressed={step === "end"} onClick={() => {
                        setStep("end");
                        setYear((end ?? start).getFullYear());
                    }}><small>Through</small><span>{end ? format(end, "MMM yyyy") : "Choose a month"}</span></button>
                </div>
                <div className="period-year">
                    <button type="button" className={UcxTableIconButtonClass} aria-label="Previous year" onClick={() => setYear(year - 1)}><Icon name="heroChevronLeft" size={16} /></button>
                    <span>{year}</span>
                    <button type="button" className={UcxTableIconButtonClass} aria-label="Next year" onClick={() => setYear(year + 1)}><Icon name="heroChevronRight" size={16} /></button>
                </div>
                <div className="period-months" onMouseLeave={() => setHoveredMonth(null)}>
                    {Array.from({length: 12}, (_, month) => {
                        const index = year * 12 + month;
                        const selected = index === startMonth || index === endMonth;
                        const inRange = previewEnd !== null && index >= rangeStart && index <= rangeEnd;
                        const date = new Date(year, month, 1);
                        return <button
                            key={month}
                            type="button"
                            className="period-month"
                            aria-label={format(date, "MMMM yyyy")}
                            aria-pressed={selected}
                            data-in-range={inRange}
                            onMouseEnter={() => {
                                if (step === "end" && end === null) setHoveredMonth(index);
                            }}
                            onFocus={() => {
                                if (step === "end" && end === null) setHoveredMonth(index);
                            }}
                            onClick={() => {
                                setHoveredMonth(null);
                                if (step === "start") {
                                    setStart(date);
                                    setEnd(null);
                                    setStep("end");
                                    return;
                                }
                                if (index < startMonth) {
                                    setEnd(new Date(start.getFullYear(), start.getMonth() + 1, 0));
                                    setStart(date);
                                } else {
                                    setEnd(new Date(year, month + 1, 0));
                                }
                                setStep("start");
                            }}
                        >{format(date, "MMM")}</button>;
                    })}
                </div>
                <div className="period-footer">
                    <Button color="ghost" onClick={closePicker}>Cancel</Button>
                    <Button disabled={!end} onClick={() => {
                        if (!end) return;
                        props.onChange({start, end});
                        closePicker();
                    }}>Apply period</Button>
                </div>
            </div>
        </ClickableDropdown>
    </div>;
}
