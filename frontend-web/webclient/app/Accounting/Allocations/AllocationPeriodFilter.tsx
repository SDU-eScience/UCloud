import * as React from "react";
import {useLayoutEffect, useRef, useState} from "react";
import {Button, Icon} from "@/ui-components";
import {SubmitShortcut} from "@/Applications/KeyboardNavigation";
import {IconButton} from "@/ui-components/IconButton";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {injectStyle} from "@/Unstyled";
import type {AllocationDateRange} from "@/Accounting/Allocations/AllocationGrid";
import {isLikelyMac} from "@/UtilityFunctions";
import {format} from "date-fns";

const AllocationPeriodFilterClass = injectStyle("allocation-period-filter", k => `
    ${k} .period-trigger { display: flex; align-items: center; gap: 6px; height: 35px; padding: 0 14px; border: 1px solid var(--borderColor); border-radius: 8px; background: transparent; color: var(--textPrimary); font-size: 14px; user-select: none; }
    ${k} .period-trigger:hover { background: var(--rowHover); }
    ${k} .period-trigger > svg { flex-shrink: 0; }
`);

const AllocationPeriodPanelClass = injectStyle("allocation-period-panel", k => `
    ${k} { box-sizing: border-box; padding: 12px; color: var(--textPrimary); cursor: default; max-height: min(478px, calc(100vh - 34px)); overflow-y: auto; }
    ${k} h4 { margin: 0; font-size: 14px; font-weight: 600; }
    ${k} .period-presets { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; margin: 8px 0; }
    ${k} .period-presets > button { min-width: 0; padding: 4px 8px; white-space: nowrap; justify-content: space-between; }
    ${k} .period-bounds { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 16px 0 12px; }
    ${k} .period-bound { display: flex; flex-direction: column; align-items: flex-start; gap: 2px; padding: 0 0 6px; border: 0; border-bottom: 1px solid transparent; background: transparent; color: var(--textSecondary); cursor: pointer; font-size: 13px; line-height: 16px; text-align: left; }
    ${k} .period-bound small { color: var(--textSecondary); font-size: 11px; }
    ${k} .period-bound[aria-pressed="true"] { color: var(--textPrimary); border-bottom-color: var(--primaryMain); }
    ${k} .period-year { display: flex; align-items: center; justify-content: space-between; margin: 16px 0 8px; padding-top: 10px; border-top: 1px solid var(--borderColor); font-size: 14px; }
    ${k} .period-months { display: grid; grid-template-columns: repeat(3, 1fr); gap: 4px; }
    ${k} .period-month { height: 28px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: var(--textPrimary); cursor: pointer; font-size: 12px; }
    ${k} .period-month[data-in-range="true"] { background: var(--gray-10); color: var(--textPrimary); }
    html.dark ${k} .period-month[data-in-range="true"]:not([aria-pressed="true"]) { background: var(--gray-90); }
    ${k} .period-month:hover { background: var(--rowHover); color: var(--textPrimary); }
    ${k} .period-month[aria-pressed="true"] { background: var(--primaryMain); color: var(--primaryContrast); }
    ${k} .period-footer { display: flex; justify-content: flex-end; gap: 8px; margin: 12px -12px -12px; padding: 8px 12px; background: var(--dialogToolbar); }
    ${k} button:focus-visible { outline: 2px solid var(--textPrimary); outline-offset: 2px; }
`);

function calendarYearRange(year: number): AllocationDateRange {
    return {start: new Date(year, 0, 1), end: new Date(year, 11, 31)};
}

export function allocationCurrentCalendarYear(now = Date.now()): AllocationDateRange {
    return calendarYearRange(new Date(now).getFullYear());
}

export function AllocationPeriodFilter(props: {
    value: AllocationDateRange;
    onChange: (value: AllocationDateRange) => void;
    navigationField?: boolean;
    now?: number;
}): React.ReactNode {
    const currentDate = new Date(props.now ?? Date.now());
    const [open, setOpen] = useState(false);
    const [start, setStart] = useState(props.value.start);
    const [end, setEnd] = useState<Date | null>(props.value.end);
    const [year, setYear] = useState(props.value.start.getFullYear());
    const [step, setStep] = useState<"start" | "end">("start");
    const [hoveredMonth, setHoveredMonth] = useState<number | null>(null);
    const wrapperRef = useRef<HTMLDivElement>(null);
    const panelRef = useRef<HTMLDivElement>(null);
    const pendingFocusMonthRef = useRef<number | null>(null);

    const setPanelNode = React.useCallback((node: HTMLDivElement | null) => {
        panelRef.current = node;
        if (!node) return;
        requestAnimationFrame(() => {
            node.querySelector<HTMLButtonElement>(".period-month[aria-pressed='true']")?.focus();
        });
    }, []);

    useLayoutEffect(() => {
        const month = pendingFocusMonthRef.current;
        if (month === null) return;
        pendingFocusMonthRef.current = null;
        const frame = requestAnimationFrame(() => {
            panelRef.current?.querySelectorAll<HTMLButtonElement>(".period-month")[month]?.focus();
        });
        return () => cancelAnimationFrame(frame);
    }, [year]);

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

    const focusApply = () => {
        requestAnimationFrame(() => panelRef.current?.querySelector<HTMLButtonElement>(".period-apply")?.focus());
    };
    const choosePreset = (range: AllocationDateRange) => {
        selectPreset(range);
        focusApply();
    };
    const nextTwelveMonths = (): AllocationDateRange => {
        const now = currentDate;
        return {start: new Date(now.getFullYear(), now.getMonth(), 1), end: new Date(now.getFullYear(), now.getMonth() + 12, 0)};
    };
    const onPanelKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
        if (event.altKey && primaryPressed && !event.shiftKey) {
            let range: AllocationDateRange | null = null;
            if (event.code === "KeyC") range = calendarYearRange(currentDate.getFullYear());
            if (event.code === "KeyL") range = calendarYearRange(currentDate.getFullYear() - 1);
            if (event.code === "KeyN") range = calendarYearRange(currentDate.getFullYear() + 1);
            if (event.code === "KeyR") range = nextTwelveMonths();
            if (range) {
                event.preventDefault();
                event.stopPropagation();
                choosePreset(range);
                return;
            }
            if (event.key === "Enter") {
                event.preventDefault();
                event.stopPropagation();
                if (!end) return;
                props.onChange({start, end});
                closePicker();
                return;
            }
        }
        if (event.altKey || event.ctrlKey || event.metaKey) return;
        if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            closePicker();
            return;
        }
        const movementKeys = ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "End"];
        if (!movementKeys.includes(event.key)) return;
        const buttons = Array.from(panelRef.current?.querySelectorAll<HTMLButtonElement>(".period-month") ?? []);
        const currentIndex = buttons.indexOf(document.activeElement as HTMLButtonElement);
        if (currentIndex === -1) return;
        let targetIndex = currentIndex;
        if (event.key === "ArrowLeft") targetIndex--;
        if (event.key === "ArrowRight") targetIndex++;
        if (event.key === "ArrowUp") targetIndex -= 3;
        if (event.key === "ArrowDown") targetIndex += 3;
        if (event.key === "Home") targetIndex = 0;
        if (event.key === "End") targetIndex = 11;
        event.preventDefault();
        event.stopPropagation();
        if (targetIndex < 0 || targetIndex > 11) {
            pendingFocusMonthRef.current = ((targetIndex % 12) + 12) % 12;
            setYear(year + (targetIndex < 0 ? -1 : 1));
        } else {
            buttons[targetIndex]?.focus();
        }
    };

    return <div
        ref={wrapperRef}
        className={AllocationPeriodFilterClass}
    >
        <ClickableDropdown
            data-navigation-field={props.navigationField ? true : undefined}
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
            width={380}
            height={480}
            keepOpenOnClick
            colorOnHover={false}
            paddingControlledByContent
            noYPadding
        >
            <div ref={setPanelNode} className={AllocationPeriodPanelClass} role="dialog" aria-label="Choose allocation period" onKeyDown={onPanelKeyDown}>
                <h4>Allocation period</h4>
                <div className="period-presets">
                    <Button color="ghost" height={25} onClick={() => choosePreset(calendarYearRange(currentDate.getFullYear()))}>Current year<SubmitShortcut shortcut="C" compact /></Button>
                    <Button color="ghost" height={25} onClick={() => {
                        choosePreset(calendarYearRange(currentDate.getFullYear() - 1));
                    }}>Last year<SubmitShortcut shortcut="L" compact /></Button>
                    <Button color="ghost" height={25} onClick={() => {
                        choosePreset(calendarYearRange(currentDate.getFullYear() + 1));
                    }}>Next year<SubmitShortcut shortcut="N" compact /></Button>
                    <Button color="ghost" height={25} onClick={() => {
                        choosePreset(nextTwelveMonths());
                    }}>Next 12 months<SubmitShortcut shortcut="R" compact /></Button>
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
                    <IconButton tooltip="Previous year" icon="heroChevronLeft" onClick={() => setYear(year - 1)} />
                    <span>{year}</span>
                    <IconButton tooltip="Next year" icon="heroChevronRight" onClick={() => setYear(year + 1)} />
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
                                focusApply();
                            }}
                        >{format(date, "MMM")}</button>;
                    })}
                </div>
                <div className="period-footer">
                    <Button color="errorMain" onClick={closePicker}>Cancel</Button>
                    <Button className="period-apply" color="successMain" disabled={!end} onClick={() => {
                        if (!end) return;
                        props.onChange({start, end});
                        closePicker();
                    }}>Apply period<SubmitShortcut compact /></Button>
                </div>
            </div>
        </ClickableDropdown>
    </div>;
}
