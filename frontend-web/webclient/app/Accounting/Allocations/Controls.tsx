import * as React from "react";
import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {Button, Icon, Input} from "@/ui-components";
import type {IconName} from "@/ui-components/Icon";
import {IconButton} from "@/ui-components/IconButton";
import {Toggle} from "@/ui-components/Toggle";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {classConcat, injectStyle} from "@/Unstyled";
import {UcxTableControlsClass} from "@/UCX/UcxBrowser";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {formatNumber} from "@/Utilities/NumberFormatting";
import {
    SubmitShortcut,
    ShortcutHint,
    closeOpenDropdown,
    isModalOpen,
} from "@/Applications/KeyboardNavigation";
import {isLikelyMac} from "@/UtilityFunctions";
import type {DateRange, FilterState} from "./Model";
import {calendarYearRange} from "./Model";

interface FilterOption {
    key: string;
    label: string;
    icon?: React.ReactNode;
    advanced?: boolean;
}

const FilterOptionsStyle = injectStyle("allocation-filter-options", k => `
    ${k} {
        padding: 8px;
        max-height: 320px;
        overflow-y: auto;
    }
    ${k} .allocation-filter-option {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 9px 8px;
        cursor: pointer;
        font-size: 13px;
        border-radius: 4px;
    }
    ${k} .allocation-filter-option:hover {
        background: var(--rowHover);
    }
    ${k} .allocation-filter-label {
        flex: 1;
        min-width: 0;
    }
    ${k} .allocation-filter-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        flex: 0 0 18px;
    }
    ${k} .allocation-filter-option > [role="switch"] {
        flex-shrink: 0;
    }
    ${k} .allocation-filter-section {
        margin: 4px 0;
        padding: 8px 8px 0;
        border-top: 1px solid var(--borderColor);
        color: var(--textSecondary);
        font-size: 12px;
    }
`);

function FilterSelect(props: {
    label: string;
    icon: IconName;
    options: FilterOption[];
    selected: string[];
    onChange: (selected: string[]) => void;
    visibility?: boolean;
}): React.ReactNode {
    const toggle = (key: string) => {
        const next = props.selected.includes(key)
            ? props.selected.filter(selectedKey => selectedKey !== key)
            : [...props.selected, key];
        props.onChange(next);
    };
    return <ClickableDropdown
        trigger={<span className="allocation-trigger">
            <Icon name={props.icon} size={15} />
            {props.label}
            {!props.visibility && props.selected.length > 0 ? ` (${formatNumber(props.selected.length)})` : ""}
            <Icon name="heroChevronDown" size={12} />
        </span>}
        triggerAriaLabel={props.visibility ? "Choose visible columns" : `Filter by ${props.label.toLowerCase()}`}
        width={300}
        focusable
        isolateKeyboard
        keepOpenOnClick
        colorOnHover={false}
        hoverColor="rowHover"
        paddingControlledByContent
        data-allocation-filter-control=""
        arrowkeyNavigationKey="data-allocation-filter-option"
        onSelect={element => {
            const key = element?.getAttribute("data-allocation-filter-option");
            if (key !== null && key !== undefined) toggle(key);
        }}
    >
        <div className={FilterOptionsStyle}>
            {props.options.map((option, index) => {
                const selected = props.selected.includes(option.key);
                return <React.Fragment key={option.key}>
                    {option.advanced && !props.options[index - 1]?.advanced ?
                        <div className="allocation-filter-section">Advanced</div> :
                        null}
                    <div
                        className="allocation-filter-option"
                        data-allocation-filter-option={option.key}
                        role="menuitemcheckbox"
                        aria-checked={selected}
                        onClick={() => toggle(option.key)}
                    >
                        {option.icon ? <span className="allocation-filter-icon">{option.icon}</span> : null}
                        <span className="allocation-filter-label">{option.label}</span>
                        <Toggle
                            height={18}
                            activeColor="primaryMain"
                            checked={selected}
                            onChange={() => toggle(option.key)}
                            ariaLabel={option.label}
                        />
                    </div>
                </React.Fragment>;
            })}
        </div>
    </ClickableDropdown>;
}

const FiltersStyle = injectStyle("allocation-filters", k => `
    ${k} {
        flex: 0 0 auto;
        margin-bottom: 12px;
    }
    ${k} .allocation-toolbar, ${k} .allocation-filter-row, ${k} .allocation-state-row {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 8px;
    }
    ${k} .allocation-toolbar {
        gap: 12px;
        margin-bottom: 12px;
    }
    ${k} .allocation-actions {
        margin-left: auto;
    }
    ${k} .allocation-filter-row {
        padding: 10px 0;
        border-top: 1px solid var(--borderColor);
        border-bottom: 1px solid var(--borderColor);
    }
    ${k} .allocation-state-row {
        gap: 2px;
        font-size: 12px;
        color: var(--textSecondary);
    }
    ${k} .allocation-search {
        flex: 1 1 280px;
        max-width: 380px;
    }
    ${k} .allocation-search .table-filter {
        width: 100%;
    }
    ${k} .allocation-result-count {
        margin-left: auto;
        font-size: 12px;
        color: var(--textSecondary);
    }
    ${k} .allocation-state-button {
        border: 0;
        border-radius: 8px;
        padding: 6px 8px;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font-size: 12px;
    }
    ${k} .allocation-state-button:hover {
        background: var(--rowHover);
    }
    ${k} .allocation-state-button[aria-pressed="true"] {
        background: color-mix(in srgb, var(--textPrimary) 8%, transparent);
        color: var(--textPrimary);
    }
    ${k} .allocation-state-button b {
        margin-left: 5px;
        font-weight: 400;
        opacity: 0.75;
    }
    ${k} .allocation-personal {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 0 8px;
        color: var(--textPrimary);
        font-size: 12px;
    }
`);

export function Filters(props: {
    query: string;
    onQueryChange: (query: string) => void;
    onSearchExit: () => void;
    periodControl?: React.ReactNode;
    actions?: React.ReactNode;
    products: FilterOption[];
    providers: FilterOption[];
    sources?: FilterOption[];
    columns: FilterOption[];
    selectedProducts: string[];
    selectedProviders: string[];
    selectedSources?: string[];
    visibleColumns: string[];
    onProductsChange: (selected: string[]) => void;
    onProvidersChange: (selected: string[]) => void;
    onSourcesChange?: (selected: string[]) => void;
    onColumnsChange: (selected: string[]) => void;
    state: FilterState;
    onStateChange: (state: FilterState) => void;
    counts: Record<FilterState, number>;
    workspaceCount: number;
    groupLabel?: string;
    personalWorkspaces: boolean;
    showPersonalWorkspaces?: boolean;
    onPersonalWorkspacesChange: () => void;
    searchPlaceholder?: string;
    onClear: () => void;
}): React.ReactNode {
    const states: [FilterState, string][] = [
        ["all", "All"], ["active", "Active"], ["expiring", "Expiring"],
        ["upcoming", "Upcoming allocations"], ["exhausted", "Exhausted"], ["expired", "Expired allocations"],
    ];
    const groupLabel = props.groupLabel ?? "workspace";
    const groupCount = `${formatNumber(props.workspaceCount)} ${groupLabel}${props.workspaceCount === 1 ? "" : "s"}`;
    const hasFilters = props.query.trim() !== ""
        || props.selectedProducts.length > 0
        || props.selectedProviders.length > 0
        || (props.selectedSources?.length ?? 0) > 0
        || props.state !== "all"
        || (props.showPersonalWorkspaces !== false && props.personalWorkspaces);
    const onFilterRowKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            focusTable();
            return;
        }
        if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
        if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
        if ((event.target as HTMLElement).closest("[data-tag='dropdown'][aria-expanded='true']")) return;
        const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
            "[data-allocation-filter-control], button:not(:disabled), [role='switch']"
        )).filter(element => element.offsetParent !== null && !element.closest("[data-visible='false']"));
        const current = (event.target as HTMLElement).closest<HTMLElement>(
            "[data-allocation-filter-control], button, [role='switch']"
        );
        if (!current) return;
        const index = controls.indexOf(current);
        const next = controls[index + (event.key === "ArrowRight" ? 1 : -1)];
        if (!next) return;
        event.preventDefault();
        event.stopPropagation();
        next.focus({preventScroll: true});
    };
    return <div className={classConcat(FiltersStyle, DropdownTriggerStyle)}>
        <div className="allocation-toolbar">
            <Search
                className="allocation-search"
                height={35}
                query={props.query}
                onQueryChange={props.onQueryChange}
                placeholder={props.searchPlaceholder ?? "Search workspace, PI or product…"}
                ariaLabel="Search allocations"
                ariaKeyshortcuts="/ Control+K Meta+K"
                dataAttributes={{"data-allocation-shortcut": "search"}}
                onKeyDown={event => {
                    if (event.altKey && (event.metaKey || event.ctrlKey)) return;
                    event.stopPropagation();
                    if (event.key === "Escape" || event.key === "Enter") {
                        event.preventDefault();
                        props.onSearchExit();
                    }
                }}
            />
            {props.periodControl}
            <div className="allocation-actions">{props.actions}</div>
        </div>
        <div className="allocation-filter-row" data-allocation-shortcut="filters"
            aria-keyshortcuts="Control+Alt+G Meta+Alt+G" onKeyDown={onFilterRowKeyDown}>
            <FilterSelect
                label="Products"
                icon="heroCpuChip"
                options={props.products}
                selected={props.selectedProducts}
                onChange={props.onProductsChange}
            />
            <FilterSelect
                label="Provider"
                icon="heroCloud"
                options={props.providers}
                selected={props.selectedProviders}
                onChange={props.onProvidersChange}
            />
            {props.sources && props.onSourcesChange ? <FilterSelect
                label="Sources"
                icon="heroBuildingOffice2"
                options={props.sources}
                selected={props.selectedSources ?? []}
                onChange={props.onSourcesChange}
            /> : null}
            <FilterSelect
                label="Columns"
                icon="heroViewColumns"
                options={props.columns}
                selected={props.visibleColumns}
                onChange={props.onColumnsChange}
                visibility
            />
            <div className="allocation-state-row" aria-label="Allocation state filters">
                {states.map(([state, label]) => <button
                    key={state}
                    className="allocation-state-button"
                    aria-pressed={props.state === state}
                    onClick={() => props.onStateChange(state)}
                >{label}<b>{formatNumber(props.counts[state])}</b></button>)}
            </div>
            {props.showPersonalWorkspaces !== false ? <div className="allocation-personal">
                <span>Personal workspaces</span>
                <Toggle
                    height={18}
                    activeColor="primaryMain"
                    checked={props.personalWorkspaces}
                    onChange={props.onPersonalWorkspacesChange}
                    ariaLabel="Personal workspaces"
                />
            </div> : null}
            {hasFilters ?
                <Button color="ghost" onClick={props.onClear}>
                    <Icon name="heroXMark" size={13} />
                    Clear filters
                </Button> :
                null}
            <span className="allocation-result-count">
                {groupCount}
                <ShortcutHint shortcut="G" hideUntilReveal />
            </span>
        </div>
    </div>;
}

const DropdownTriggerStyle = injectStyle("allocation-dropdown-trigger", k => `
    ${k} .allocation-trigger {
        display: flex;
        align-items: center;
        gap: 6px;
        height: 35px;
        padding: 0 14px;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: transparent;
        color: var(--textPrimary);
        font-size: 14px;
        user-select: none;
    }
    ${k} .allocation-trigger:hover {
        background: var(--rowHover);
    }
`);

export function Search(props: {
    query: string;
    onQueryChange: (query: string) => void;
    placeholder: string;
    ariaLabel: string;
    inputRef?: React.RefObject<HTMLInputElement | null>;
    className?: string;
    height?: number;
    maxLength?: number;
    ariaKeyshortcuts?: string;
    dataAttributes?: Record<string, string>;
    onKeyDown?: React.KeyboardEventHandler<HTMLInputElement>;
}): React.ReactNode {
    return <div className={classConcat(UcxTableControlsClass, props.className)}>
        <div className="table-filter">
            <Input
                height={props.height}
                inputRef={props.inputRef}
                value={props.query}
                placeholder={props.placeholder}
                aria-label={props.ariaLabel}
                aria-keyshortcuts={props.ariaKeyshortcuts}
                maxLength={props.maxLength}
                onChange={event => props.onQueryChange(event.currentTarget.value)}
                onKeyDown={props.onKeyDown}
                {...props.dataAttributes}
            />
            {props.query === "" ?
                <span className="table-filter-shortcut">
                    <div className={ShortcutClass}>/</div>
                </span> :
                null}
        </div>
    </div>;
}

const PeriodFilterClass = injectStyle("allocation-period-filter", k => `
    ${k} .period-trigger > svg {
        flex-shrink: 0;
    }
`);

const PeriodPanelClass = injectStyle("allocation-period-panel", k => `
    ${k} {
        box-sizing: border-box;
        padding: 12px;
        color: var(--textPrimary);
        cursor: default;
        max-height: min(478px, calc(100vh - 34px));
        overflow-y: auto;
    }
    ${k} h4 {
        margin: 0;
        font-size: 14px;
        font-weight: 600;
    }
    ${k} .period-presets {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 6px;
        margin: 8px 0;
    }
    ${k} .period-presets > button {
        min-width: 0;
        padding: 4px 8px;
        white-space: nowrap;
        justify-content: space-between;
    }
    ${k} .period-bounds {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
        margin: 16px 0 12px;
    }
    ${k} .period-bound {
        display: flex;
        flex-direction: column;
        align-items: flex-start;
        gap: 2px;
        padding: 0 0 6px;
        border: 0;
        border-bottom: 1px solid transparent;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        font-size: 13px;
        line-height: 16px;
        text-align: left;
    }
    ${k} .period-bound small {
        color: var(--textSecondary);
        font-size: 11px;
    }
    ${k} .period-bound[aria-pressed="true"] {
        color: var(--textPrimary);
        border-bottom-color: var(--primaryMain);
    }
    ${k} .period-year {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin: 16px 0 8px;
        padding-top: 10px;
        border-top: 1px solid var(--borderColor);
        font-size: 14px;
    }
    ${k} .period-months {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 4px;
    }
    ${k} .period-month {
        height: 28px;
        border: 1px solid transparent;
        border-radius: 8px;
        background: transparent;
        color: var(--textPrimary);
        cursor: pointer;
        font-size: 12px;
    }
    ${k} .period-month[data-in-range="true"] {
        background: var(--gray-10);
        color: var(--textPrimary);
    }
    html.dark ${k} .period-month[data-in-range="true"]:not([aria-pressed="true"]) {
        background: var(--gray-90);
    }
    ${k} .period-month:hover:not(:disabled) {
        background: var(--rowHover);
        color: var(--textPrimary);
    }
    ${k} .period-month:disabled {
        opacity: 0.35;
        cursor: not-allowed;
    }
    ${k} .period-month[aria-pressed="true"] {
        background: var(--primaryMain);
        color: var(--primaryContrast);
    }
    ${k} .period-footer {
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        margin: 12px -12px -12px;
        padding: 8px 12px;
        background: var(--dialogToolbar);
    }
    ${k} button:focus-visible {
        outline: 2px solid var(--textPrimary);
        outline-offset: 2px;
    }
`);

const formatPeriodMonth = new Intl.DateTimeFormat(undefined, {month: "short"}).format;
const formatPeriodMonthYear = new Intl.DateTimeFormat(undefined, {month: "short", year: "numeric"}).format;
const describePeriodMonthYear = new Intl.DateTimeFormat(undefined, {month: "long", year: "numeric"}).format;

export interface PeriodPreset {
    label: string;
    shortcut?: string;
    keyCode?: string;
    range: (now: Date) => DateRange;
}

const defaultAllocationPresets: PeriodPreset[] = [
    {
        label: "Current year",
        shortcut: "V",
        keyCode: "KeyV",
        range: now => calendarYearRange(now.getFullYear()),
    },
    {
        label: "Last year",
        shortcut: "X",
        keyCode: "KeyX",
        range: now => calendarYearRange(now.getFullYear() - 1),
    },
    {
        label: "Next year",
        shortcut: "Y",
        keyCode: "KeyY",
        range: now => calendarYearRange(now.getFullYear() + 1),
    },
    {
        label: "Next 12 months",
        shortcut: "Z",
        keyCode: "KeyZ",
        range: now => ({
            start: new Date(now.getFullYear(), now.getMonth(), 1),
            end: new Date(now.getFullYear(), now.getMonth() + 12, 0),
        }),
    },
];

export function PeriodFilter(props: {
    value: DateRange;
    onChange: (value: DateRange) => void;
    title?: string;
    ariaLabel?: string;
    presets?: PeriodPreset[];
    maxDate?: Date;
    navigationField?: boolean;
    presetButtonColor?: "secondaryMain";
    now?: number;
    shortcut?: string;
    onReturnFocus?: () => void;
}): React.ReactNode {
    const currentDate = new Date(props.now ?? Date.now());
    const title = props.title ?? "Allocation period";
    const ariaLabel = props.ariaLabel ?? "Choose allocation period";
    const presets = props.presets ?? defaultAllocationPresets;
    const maxMonth = props.maxDate ? props.maxDate.getFullYear() * 12 + props.maxDate.getMonth() : null;
    const maxYear = props.maxDate ? props.maxDate.getFullYear() : null;
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
        if (props.onReturnFocus) {
            props.onReturnFocus();
        } else {
            wrapperRef.current?.querySelector<HTMLElement>("[data-tag='dropdown']")?.focus();
        }
    };
    const selectPreset = (range: DateRange) => {
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
    const choosePreset = (range: DateRange) => {
        selectPreset(range);
        focusApply();
    };
    const onPanelKeyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
        const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;
        if (event.altKey && primaryPressed && !event.shiftKey) {
            const matchedPreset = presets.find(p => p.keyCode === event.code);
            if (matchedPreset) {
                event.preventDefault();
                event.stopPropagation();
                choosePreset(matchedPreset.range(currentDate));
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
            const nextYear = year + (targetIndex < 0 ? -1 : 1);
            const nextMonth = ((targetIndex % 12) + 12) % 12;
            if (maxMonth !== null && nextYear * 12 + nextMonth > maxMonth) return;
            pendingFocusMonthRef.current = nextMonth;
            setYear(nextYear);
        } else {
            if (maxMonth !== null && year * 12 + targetIndex > maxMonth) return;
            buttons[targetIndex]?.focus();
        }
    };

    return <div
        ref={wrapperRef}
        className={classConcat(PeriodFilterClass, DropdownTriggerStyle)}
        data-allocation-shortcut="period"
    >
        <ClickableDropdown
            data-navigation-field={props.navigationField ? true : undefined}
            open={open}
            onClose={() => setOpen(false)}
            onOpeningTriggerClick={() => {
                selectPreset(props.value);
                setOpen(true);
            }}
            trigger={<span className="allocation-trigger period-trigger">
                <Icon name="heroCalendar" size={16} />
                <span>{formatPeriodMonthYear(props.value.start)} – {formatPeriodMonthYear(props.value.end)}</span>
                <Icon name="heroChevronDown" size={12} />
                {props.shortcut ? <ShortcutHint shortcut={props.shortcut} hideUntilReveal /> : null}
            </span>}
            triggerAriaLabel={ariaLabel}
            triggerAriaKeyshortcuts={props.shortcut ? "Control+Alt+K Meta+Alt+K" : undefined}
            focusable
            rightAligned
            width={380}
            height={480}
            keepOpenOnClick
            colorOnHover={false}
            paddingControlledByContent
            noYPadding
        >
            <div
                ref={setPanelNode}
                className={PeriodPanelClass}
                role="dialog"
                aria-label={ariaLabel}
                onKeyDown={onPanelKeyDown}
            >
                <h4>{title}</h4>
                <div className="period-presets">
                    {presets.map(preset => (
                        <Button
                            key={preset.label}
                            color={props.presetButtonColor ?? "ghost"}
                            height={25}
                            onClick={() => choosePreset(preset.range(currentDate))}
                        >
                            {preset.label}{preset.shortcut ? <SubmitShortcut shortcut={preset.shortcut} compact /> : null}
                        </Button>
                    ))}
                </div>
                <div className="period-bounds">
                    <button type="button" className="period-bound" aria-pressed={step === "start"} onClick={() => {
                        setStep("start");
                        setYear(start.getFullYear());
                    }}><small>From</small><span>{formatPeriodMonthYear(start)}</span></button>
                    <button type="button" className="period-bound" aria-pressed={step === "end"} onClick={() => {
                        setStep("end");
                        setYear((end ?? start).getFullYear());
                    }}><small>Through</small><span>{end ? formatPeriodMonthYear(end) : "Choose a month"}</span></button>
                </div>
                <div className="period-year">
                    <IconButton tooltip="Previous year" icon="heroChevronLeft" onClick={() => setYear(year - 1)} />
                    <span>{year}</span>
                    <IconButton
                        tooltip="Next year"
                        icon="heroChevronRight"
                        disabled={maxYear !== null && year >= maxYear}
                        onClick={() => setYear(year + 1)}
                    />
                </div>
                <div className="period-months" onMouseLeave={() => setHoveredMonth(null)}>
                    {Array.from({length: 12}, (_, month) => {
                        const index = year * 12 + month;
                        const disabled = maxMonth !== null && index > maxMonth;
                        const selected = index === startMonth || index === endMonth;
                        const inRange = previewEnd !== null && index >= rangeStart && index <= rangeEnd;
                        const date = new Date(year, month, 1);
                        return <button
                            key={month}
                            type="button"
                            className="period-month"
                            disabled={disabled}
                            aria-label={describePeriodMonthYear(date)}
                            aria-pressed={selected}
                            data-in-range={inRange}
                            onMouseEnter={() => {
                                if (!disabled && step === "end" && end === null) setHoveredMonth(index);
                            }}
                            onFocus={() => {
                                if (!disabled && step === "end" && end === null) setHoveredMonth(index);
                            }}
                            onClick={() => {
                                if (disabled) return;
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
                        >{formatPeriodMonth(date)}</button>;
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

function shortcutElement(name: string): HTMLElement | null {
    return document.querySelector<HTMLElement>(`[data-allocation-shortcut="${name}"]`);
}

function focusFirstControl(root: HTMLElement | null): void {
    if (!root) return;
    const target = Array.from(root.querySelectorAll<HTMLElement>(
        "[data-allocation-filter-control], button:not(:disabled), [data-tag='dropdown'][tabindex='0'], [role='switch']"
    )).find(element => element.offsetParent !== null && !element.closest("[data-visible='false']") &&
        !element.matches(":disabled, [aria-disabled='true']"));
    target?.focus({preventScroll: true});
}

function focusTableRoot(selector: string): void {
    const table = document.querySelector<HTMLElement>(selector);
    table?.focus({preventScroll: true});
}

export function focusTable(): void {
    if (isModalOpen()) return;
    focusTableRoot("[data-ucx-table='allocation-grid']");
}

function focusOverview(): void {
    if (isModalOpen()) return;
    const toggle = document.querySelector<HTMLElement>("[data-allocation-overview-toggle]");
    const collapsed = toggle?.getAttribute("aria-expanded") === "false";
    if (toggle && collapsed) toggle.click();
    requestAnimationFrame(() => requestAnimationFrame(() => {
        const section = document.querySelector<HTMLElement>("[data-allocation-overview]");
        const table = section?.querySelector<HTMLElement>("[data-ucx-table]");
        (table ?? toggle)?.focus({preventScroll: true});
    }));
}

export function focusAction(name: "allocate" | "export"): void {
    if (isModalOpen()) return;
    shortcutElement(name)?.focus({preventScroll: true});
}

function triggerAction(name: "allocate" | "export"): void {
    if (isModalOpen()) return;
    shortcutElement(name)?.click();
}

function focusSearch(): void {
    const search = shortcutElement("search");
    if (!search) return;
    search.scrollIntoView({block: "nearest"});
    search.focus({preventScroll: true});
}

export function useShortcuts(): void {
    useEffect(() => {
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.defaultPrevented || event.repeat || isModalOpen()) return;
            const target = event.target as HTMLElement | null;
            const editable = target?.closest("input, textarea, select, [contenteditable='true']") != null;
            const primaryPressed = isLikelyMac ? event.metaKey : event.ctrlKey;

            if ((event.key === "/" && !event.metaKey && !event.ctrlKey && !event.altKey) ||
                (primaryPressed && !event.altKey && !event.shiftKey && event.key.toLowerCase() === "k")) {
                if (editable && event.key === "/") return;
                event.preventDefault();
                event.stopPropagation();
                focusSearch();
                return;
            }

            if (!event.altKey || !primaryPressed || event.shiftKey) return;
            if (event.code !== "KeyO" && event.code !== "KeyG" && event.code !== "KeyK" &&
                event.code !== "KeyS" && event.code !== "KeyA" && event.code !== "KeyE") return;

            event.preventDefault();
            event.stopPropagation();
            if (document.activeElement instanceof HTMLElement) closeOpenDropdown(document.activeElement);

            switch (event.code) {
                case "KeyO":
                    focusOverview();
                    return;
                case "KeyG":
                    focusFirstControl(shortcutElement("filters"));
                    return;
                case "KeyK":
                    focusFirstControl(shortcutElement("period"));
                    return;
                case "KeyS":
                    focusTable();
                    return;
                case "KeyA":
                    triggerAction("allocate");
                    return;
                case "KeyE":
                    triggerAction("export");
                    return;
            }
        };

        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, []);
}
