import * as React from "react";
import {Button, Icon, Input} from "@/ui-components";
import type {IconName} from "@/ui-components/Icon";
import {Toggle} from "@/ui-components/Toggle";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {classConcat, injectStyle} from "@/Unstyled";
import {UcxTableControlsClass} from "@/UCX/UcxBrowser";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";

export type AllocationFilterState = "all" | "active" | "expiring" | "upcoming" | "exhausted" | "expired";

export interface AllocationFilterOption {
    key: string;
    label: string;
    icon?: React.ReactNode;
}

const AllocationFilterOptionsStyle = injectStyle("allocation-filter-options", k => `
    ${k} { padding: 8px; max-height: 320px; overflow-y: auto; }
    ${k} .allocation-filter-option { display: flex; align-items: center; gap: 12px; padding: 9px 8px; cursor: pointer; font-size: 13px; border-radius: 4px; }
    ${k} .allocation-filter-option:hover { background: var(--rowHover); }
    ${k} .allocation-filter-label { flex: 1; min-width: 0; }
    ${k} .allocation-filter-icon { display: flex; align-items: center; justify-content: center; flex: 0 0 18px; }
    ${k} .allocation-filter-option > [role="switch"] { flex-shrink: 0; }
`);

function AllocationFilterSelect(props: {
    label: string;
    icon: IconName;
    options: AllocationFilterOption[];
    selected: string[];
    onChange: (selected: string[]) => void;
}): React.ReactNode {
    return <ClickableDropdown
        trigger={<span className="allocation-filter-trigger"><Icon name={props.icon} size={15} />{props.label}{props.selected.length > 0 ? ` (${props.selected.length})` : ""}<Icon name="heroChevronDown" size={12} /></span>}
        triggerAriaLabel={`Filter by ${props.label.toLowerCase()}`}
        width={300}
        focusable
        keepOpenOnClick
        colorOnHover={false}
        paddingControlledByContent
    >
        <div className={AllocationFilterOptionsStyle}>
            {props.options.map(option => {
                const toggle = () => {
                    const next = props.selected.includes(option.key)
                        ? props.selected.filter(key => key !== option.key)
                        : [...props.selected, option.key];
                    props.onChange(next);
                };
                return <div key={option.key} className="allocation-filter-option" onClick={toggle}>
                    {option.icon ? <span className="allocation-filter-icon">{option.icon}</span> : null}
                    <span className="allocation-filter-label">{option.label}</span>
                    <Toggle
                        height={18}
                        activeColor="primaryMain"
                        checked={props.selected.includes(option.key)}
                        onChange={toggle}
                        ariaLabel={option.label}
                    />
                </div>;
            })}
        </div>
    </ClickableDropdown>;
}

const AllocationFiltersStyle = injectStyle("allocation-filters", k => `
    ${k} { flex: 0 0 auto; margin-bottom: 12px; }
    ${k} .allocation-toolbar, ${k} .allocation-filter-row, ${k} .allocation-state-row { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    ${k} .allocation-toolbar { gap: 12px; margin-bottom: 12px; }
    ${k} .allocation-actions { margin-left: auto; }
    ${k} .allocation-filter-row { padding: 10px 0; border-top: 1px solid var(--borderColor); border-bottom: 1px solid var(--borderColor); }
    ${k} .allocation-state-row { gap: 2px; font-size: 12px; color: var(--textSecondary); }
    ${k} .allocation-search { flex: 1 1 280px; max-width: 380px; }
    ${k} .allocation-search .table-filter { width: 100%; }
    ${k} .allocation-filter-trigger { display: flex; align-items: center; gap: 7px; padding: 6px 8px; border: 1px solid transparent; border-radius: 8px; font-size: 12px; color: var(--textPrimary); }
    ${k} .allocation-filter-trigger:hover { background: var(--rowHover); }
    ${k} .allocation-result-count { margin-left: auto; font-size: 12px; color: var(--textSecondary); }
    ${k} .allocation-state-button { border: 0; border-radius: 8px; padding: 6px 8px; background: transparent; color: var(--textSecondary); cursor: pointer; font-size: 12px; }
    ${k} .allocation-state-button:hover { background: var(--rowHover); }
    ${k} .allocation-state-button[aria-pressed="true"] { background: color-mix(in srgb, var(--textPrimary) 8%, transparent); color: var(--textPrimary); }
    ${k} .allocation-state-button b { margin-left: 5px; font-weight: 400; opacity: 0.75; }
    ${k} .allocation-personal { display: inline-flex; align-items: center; gap: 8px; padding: 0 8px; color: var(--textPrimary); font-size: 12px; }
`);

export function AllocationFilters(props: {
    query: string;
    onQueryChange: (query: string) => void;
    searchRef?: React.RefObject<HTMLInputElement | null>;
    onSearchExit: () => void;
    periodControl?: React.ReactNode;
    actions?: React.ReactNode;
    products: AllocationFilterOption[];
    providers: AllocationFilterOption[];
    selectedProducts: string[];
    selectedProviders: string[];
    onProductsChange: (selected: string[]) => void;
    onProvidersChange: (selected: string[]) => void;
    state: AllocationFilterState;
    onStateChange: (state: AllocationFilterState) => void;
    counts: Record<AllocationFilterState, number>;
    workspaceCount: number;
    personalWorkspaces: boolean;
    onPersonalWorkspacesChange: () => void;
    onClear: () => void;
}): React.ReactNode {
    const states: [AllocationFilterState, string][] = [
        ["all", "All"], ["active", "Active"], ["expiring", "Expiring"],
        ["upcoming", "Upcoming"], ["exhausted", "Exhausted"], ["expired", "Expired"],
    ];
    return <div className={AllocationFiltersStyle}>
        <div className="allocation-toolbar">
            <div className={classConcat("allocation-search", UcxTableControlsClass)}>
                <div className="table-filter">
                    <Input
                        height={35}
                        inputRef={props.searchRef}
                        value={props.query}
                        placeholder="Search workspace, PI or product…"
                        aria-label="Search allocations"
                        aria-keyshortcuts="/"
                        onChange={event => props.onQueryChange(event.currentTarget.value)}
                        onKeyDown={event => {
                            event.stopPropagation();
                            if (event.key === "Escape" || event.key === "Enter") {
                                event.preventDefault();
                                props.onSearchExit();
                            }
                        }}
                    />
                    {props.query === "" ? <span className="table-filter-shortcut"><div className={ShortcutClass}>/</div></span> : null}
                </div>
            </div>
            {props.periodControl}
            <div className="allocation-actions">{props.actions}</div>
        </div>
        <div className="allocation-filter-row">
            <AllocationFilterSelect label="Products" icon="heroCpuChip" options={props.products} selected={props.selectedProducts} onChange={props.onProductsChange} />
            <AllocationFilterSelect label="Provider" icon="heroCloud" options={props.providers} selected={props.selectedProviders} onChange={props.onProvidersChange} />
            <div className="allocation-state-row" aria-label="Allocation state filters">
                {states.map(([state, label]) => <button
                    key={state}
                    className="allocation-state-button"
                    aria-pressed={props.state === state}
                    onClick={() => props.onStateChange(state)}
                >{label}<b>{props.counts[state]}</b></button>)}
            </div>
            <div className="allocation-personal">
                <span>Personal workspaces</span>
                <Toggle height={18} activeColor="primaryMain" checked={props.personalWorkspaces} onChange={props.onPersonalWorkspacesChange} ariaLabel="Personal workspaces" />
            </div>
            <Button color="ghost" onClick={props.onClear}><Icon name="heroXMark" size={13} />Clear filters</Button>
            <span className="allocation-result-count">{props.workspaceCount} workspaces</span>
        </div>
    </div>;
}
