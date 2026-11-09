// Parameter editor panel
// =====================================================================================================================
// When a parameter is selected, the properties island shows this panel. It contains the fields
// shared by all parameter types and the type-specific settings. All edits write to the draft
// through the provided callbacks; the panel never reads from the DOM.
//
// The shared fields are: parameter name, title, description, optional state, and delete.
//
// Type-specific settings:
// - Text and TextArea: default value
// - Boolean: default state
// - Integer and FloatingPoint: default, minimum, maximum, step
// - Enumeration: ordered option list (title + value) and default option
// - File, Directory, License, Job, PublicIP: no type-specific settings in this milestone
// - Workflow: no visual property fields (YAML-only)
//
// The panel has a "Back to application" action at the top and a delete action at the bottom.
//
// This file also contains the panel building blocks shared with the metadata panel:
// PanelSection, PanelRow, ToggleRow, and InfoDot. Each section has a bold header (no background,
// no uppercase) and stacked rows underneath. The sections are separated by a top border and extra
// vertical spacing. The last section has no bottom border.

import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {Input, Label, Select, Text} from "@/ui-components";
import Icon from "@/ui-components/Icon";
import {IconButton} from "@/ui-components/IconButton";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {Toggle} from "@/ui-components/Toggle";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {A2Parameter, A2EnumOption, CreatorDraft, CreatorValidationError} from "@/Applications/Creator/Draft";
import {nameForId, parameterRenameIssue} from "@/Applications/Creator/DraftOperations";
import {CreatorShortcutHint} from "@/Applications/Creator/CreatorKeyboard";
import {focusFirstNavigationTarget, FORM_NAVIGATION_SELECTOR} from "@/Applications/KeyboardNavigation";

export function PanelSection(props: {
    title: string;
    children: React.ReactNode;
    collapsedByDefault?: boolean;
    id?: string;
    shortcut?: string;
}): React.ReactNode {
    const [collapsed, setCollapsed] = useState(props.collapsedByDefault === true);
    const toggle = () => setCollapsed(c => !c);
    const onHeaderKeyDown = (event: React.KeyboardEvent<HTMLSpanElement>) => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        setCollapsed(false);
        const section = event.currentTarget.closest<HTMLElement>("[data-panel-section]");
        if (!section) return;
        window.requestAnimationFrame(() => {
            focusFirstNavigationTarget(section, FORM_NAVIGATION_SELECTOR);
        });
    };
    return (
        <div
            className={PanelSectionClass}
            data-collapsed={collapsed}
            data-panel-section
            id={props.id}
        >
            <div className="panel-section-header">
                <span
                    className="panel-section-title"
                    onClick={toggle}
                    onKeyDown={onHeaderKeyDown}
                    role="button"
                    aria-expanded={!collapsed}
                    tabIndex={collapsed ? 0 : -1}
                    data-navigation-field={collapsed ? true : undefined}
                    data-panel-section-toggle
                >
                    {props.title}
                </span>
                {props.shortcut ? <CreatorShortcutHint shortcut={props.shortcut} /> : null}
                <IconButton
                    icon={collapsed ? "heroChevronRight" : "heroChevronDown"}
                    tooltip={collapsed ? "Expand section" : "Collapse section"}
                    onClick={toggle}
                    compact
                />
            </div>
            {collapsed ? null : <div className="panel-section-body">{props.children}</div>}
        </div>
    );
}

export function PanelRow(props: {label: string; value: string}): React.ReactNode {
    return (
        <div className="panel-row">
            <span className="panel-row-label">{props.label}</span>
            <span className="panel-row-value">{props.value || "—"}</span>
        </div>
    );
}

export function ToggleRow(props: {label: string; checked: boolean; onChange: () => void; disabled?: boolean; id?: string}): React.ReactNode {
    return (
        <div className={ToggleRowClass} id={props.id}>
            <Toggle checked={props.checked} onChange={props.onChange} height={20} disabled={props.disabled} />
            <Text fontSize={13} className="toggle-row-label" onClick={props.disabled ? undefined : props.onChange}>{props.label}</Text>
        </div>
    );
}

export function InfoDot(props: {tooltip: React.ReactNode}): React.ReactNode {
    return (
        <TooltipV2
            tooltip={<div className={InfoTooltipContentClass}>{props.tooltip}</div>}
            contentWidth={280}
            triggerStyle={{display: "inline-flex", verticalAlign: "middle"}}
        >
            <span className={InfoDotClass} role="img" aria-label="More information">
                <Icon name="heroInformationCircle" size={14} color="textSecondary" />
            </span>
        </TooltipV2>
    );
}

export const PanelSectionClass = injectStyle("creator-panel-section-shared", k => `
    ${k} {
        padding: 16px 12px;
    }

    ${k} + ${k} {
        border-top: 1px solid var(--borderColor);
    }

    ${k} > .panel-section-header {
        display: flex;
        align-items: center;
        gap: 4px;
        margin-bottom: 12px;
        font-size: 13px;
        font-weight: 700;
        color: var(--textPrimary);
        flex-shrink: 0;
    }

    ${k}[data-collapsed="true"] > .panel-section-header {
        margin-bottom: 0;
    }

    ${k} > .panel-section-header > .panel-section-title {
        flex: 1 1 auto;
        cursor: pointer;
        user-select: none;
    }

    ${k} > .panel-section-header > .panel-section-title:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 2px;
        border-radius: 4px;
    }

    ${k} > .panel-section-body {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }

    ${k} .panel-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        min-height: 32px;
    }

    ${k} .panel-row > .panel-row-label {
        flex-shrink: 0;
        color: var(--textSecondary);
        font-size: 13px;
    }

    ${k} .panel-row > .panel-row-value {
        text-align: right;
        font-size: 13px;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .panel-field {
        display: flex;
        flex-direction: column;
        gap: 4px;
    }

    ${k} .panel-field > .panel-field-label {
        font-size: 13px;
        font-weight: 700;
        color: var(--textPrimary);
    }
`);

const ToggleRowClass = injectStyle("creator-toggle-row", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 8px;
        min-height: 32px;
        cursor: pointer;
        user-select: none;
        border-radius: 6px;
        padding: 2px 4px;
        margin: -2px -4px;
        transition: box-shadow 0.2s ease;
    }

    ${k} > .toggle-row-label {
        cursor: pointer;
    }
`);

const InfoDotClass = injectStyle("creator-info-dot", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        cursor: help;
        vertical-align: middle;
        margin-left: 4px;
    }
`);

const InfoTooltipContentClass = injectStyle("creator-info-tooltip-content", k => `
    ${k}, ${k} * {
        text-align: left !important;
    }

    ${k} ul {
        margin: 6px 0;
        padding-left: 0;
        list-style-position: inside;
    }

    ${k} ul li {
        margin: 0;
        padding-left: 16px;
    }
`);

injectStyle("creator-highlight-global", () => `
    .creator-highlight-active {
        --creatorHighlightColor: var(--primaryMain);
        animation: creator-pulse-glow 2s ease-out 1;
        border-radius: 6px;
    }

    html.dark .creator-highlight-active {
        --creatorHighlightColor: var(--blue-50);
    }

    @keyframes creator-pulse-glow {
        0%   { box-shadow: 0 0 0 0   color-mix(in srgb, var(--creatorHighlightColor) 0%,   transparent); }
        15%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--creatorHighlightColor) 35%,  transparent); }
        30%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--creatorHighlightColor) 35%,  transparent); }
        45%  { box-shadow: 0 0 0 2px color-mix(in srgb, var(--creatorHighlightColor) 18%,  transparent); }
        60%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--creatorHighlightColor) 35%,  transparent); }
        75%  { box-shadow: 0 0 0 4px color-mix(in srgb, var(--creatorHighlightColor) 35%,  transparent); }
        100% { box-shadow: 0 0 0 0   color-mix(in srgb, var(--creatorHighlightColor) 0%,   transparent); }
    }
`);

export interface ParameterPanelProps {
    draft: CreatorDraft;
    readOnly?: boolean;
    onBack: () => void;
    onRename: (oldName: string, newName: string) => void;
    onUpdateBase: (name: string, patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>) => void;
    onDelete: (name: string) => void;
    onUpdateDefaultValue: (name: string, value: string | number | boolean | null) => void;
    onUpdateNumeric: (name: string, patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>) => void;
    onUpdateEnumeration: (name: string, patch: { options?: A2EnumOption[]; defaultValue?: string | null }) => void;
}

export function ParameterPanel(props: ParameterPanelProps): React.ReactNode {
    const {draft} = props;
    const selectedId = draft.selection.parameterId;
    if (selectedId == null) return null;
    const name = nameForId(draft, selectedId);
    if (name == null) return null;
    const param = draft.application.parameters[name];
    if (!param) return null;

    const errors = draft.validation.errors.filter(
        (e: CreatorValidationError) => e.parameterName === name,
    );

    const readOnly = props.readOnly === true;
    return (
        <div className={readOnly ? ParameterReadOnlyClass : undefined}>
            <ParameterHeaderSection title={`Parameter: ${name}`} onBack={props.onBack}>
                <CommonSettings
                    name={name}
                    param={param}
                    errors={errors}
                    onRename={props.onRename}
                    onUpdateBase={props.onUpdateBase}
                    onDelete={props.onDelete}
                />

                <TypeSpecificSettings
                    name={name}
                    param={param}
                    errors={errors}
                    onUpdateDefaultValue={props.onUpdateDefaultValue}
                    onUpdateNumeric={props.onUpdateNumeric}
                    onUpdateEnumeration={props.onUpdateEnumeration}
                />

                {param.type === "Workflow" ? (
                    <PanelSection title="Workflow">
                        <Text fontSize={12} color="textSecondary">
                            This is a YAML-only parameter. Edit its content in the YAML view.
                        </Text>
                    </PanelSection>
                ) : null}

                <PanelSection title="Danger zone">
                    <ConfirmationButton
                        actionText="Delete parameter"
                        color="errorMain"
                        icon="heroTrash"
                        onAction={async () => props.onDelete(name)}
                    />
                </PanelSection>
            </ParameterHeaderSection>
        </div>
    );
}

const ParameterReadOnlyClass = injectStyle("creator-parameter-readonly", k => `
    ${k} {
        pointer-events: none;
        opacity: 0.85;
    }
`);

function ParameterHeaderSection(props: {
    title: string;
    onBack: () => void;
    children: React.ReactNode;
}): React.ReactNode {
    return (
        <div className={PanelSectionClass}>
            <div className="panel-section-header" style={{marginBottom: 0}}>
                <IconButton
                    icon="heroArrowLeft"
                    tooltip="Back to application"
                    onClick={props.onBack}
                    compact
                />
                <span className="panel-section-title">{props.title}</span>
            </div>
            <div className="panel-section-body" style={{marginTop: 16}}>
                {props.children}
            </div>
        </div>
    );
}

// Common settings: name, title, description, optional
// -------------------------------------------------------------------------------------------------------------------

function renameIssue(newName: string): string | null {
    return parameterRenameIssue(newName);
}

function CommonSettings(props: {
    name: string;
    param: A2Parameter;
    errors: CreatorValidationError[];
    onRename: (oldName: string, newName: string) => void;
    onUpdateBase: (name: string, patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>) => void;
    onDelete: (name: string) => void;
}): React.ReactNode {
    const {name, param, errors} = props;
    const [nameValue, setNameValue] = useState(name);
    const [nameError, setNameError] = useState<string | null>(null);
    const nameInputFocused = useRef(false);

    React.useEffect(() => {
        const el = document.activeElement;
        if (nameInputFocused.current && el instanceof HTMLInputElement && el === nameInputRef.current) return;
        setNameValue(name);
        setNameError(null);
    }, [name]);

    React.useEffect(() => {
        return () => {
            commitNameRef.current();
        };
    }, []);

    const validationError = errors.find(e =>
        e.message.startsWith("Parameter name") || e.message.startsWith("Duplicate parameter name"),
    );

    const nameInputRef = useRef<HTMLInputElement | null>(null);
    const commitNameRef = useRef(() => {});
    commitNameRef.current = () => {
        const trimmed = nameValue.trim();
        if (trimmed === name) {
            if (trimmed !== nameValue) setNameValue(trimmed);
            setNameError(null);
            return;
        }
        const issue = renameIssue(trimmed);
        if (issue != null) {
            setNameError(issue);
            return;
        }
        props.onRename(name, trimmed);
        window.requestAnimationFrame(() => {
            setNameValue(current => current === trimmed && trimmed !== name ? name : current);
        });
    };

    return (
        <PanelSection title="Common" id="creator-section-common" shortcut="C">
            <Label className="panel-field">
                <span className="panel-field-label">Parameter name</span>
                <Input
                    className={PanelInputClass}
                    inputRef={nameInputRef}
                    value={nameValue}
                    onChange={e => {
                        setNameValue(e.target.value);
                        setNameError(null);
                    }}
                    onFocus={() => { nameInputFocused.current = true; }}
                    onBlur={() => {
                        nameInputFocused.current = false;
                        commitNameRef.current();
                    }}
                    onKeyDown={e => {
                        if (e.key === "Enter") {
                            (e.target as HTMLInputElement).blur();
                        }
                    }}
                    error={nameError != null || validationError != null}
                />
                {nameError ? <Text fontSize={12} color="errorMain" mt="4px">{nameError}</Text> : null}
                {nameError == null && validationError ? <Text fontSize={12} color="errorMain" mt="4px">{validationError.message}</Text> : null}
            </Label>

            <Label className="panel-field">
                <span className="panel-field-label">Title</span>
                <Input
                    className={PanelInputClass}
                    value={param.title}
                    onChange={e => props.onUpdateBase(name, {title: e.target.value})}
                />
            </Label>

            <Label className="panel-field">
                <span className="panel-field-label">Description</span>
                <Input
                    className={PanelInputClass}
                    value={param.description}
                    onChange={e => props.onUpdateBase(name, {description: e.target.value})}
                />
            </Label>

            <ToggleRow
                label="Mandatory"
                checked={!param.optional}
                onChange={() => props.onUpdateBase(name, {optional: !param.optional})}
            />
        </PanelSection>
    );
}

// Type-specific settings
// -------------------------------------------------------------------------------------------------------------------

function TypeSpecificSettings(props: {
    name: string;
    param: A2Parameter;
    errors: CreatorValidationError[];
    onUpdateDefaultValue: (name: string, value: string | number | boolean | null) => void;
    onUpdateNumeric: (name: string, patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>) => void;
    onUpdateEnumeration: (name: string, patch: { options?: A2EnumOption[]; defaultValue?: string | null }) => void;
}): React.ReactNode {
    const {name, param} = props;

    switch (param.type) {
        case "Text":
        case "TextArea":
            return (
                <PanelSection title="Default value" id="creator-section-type" shortcut="V">
                    <Label className="panel-field">
                        <span className="panel-field-label">Default</span>
                        <Input
                            className={PanelInputClass}
                            value={param.defaultValue ?? ""}
                            onChange={e => props.onUpdateDefaultValue(name, e.target.value || null)}
                        />
                    </Label>
                </PanelSection>
            );

        case "Boolean":
            return (
                <PanelSection title="Default value" id="creator-section-type" shortcut="V">
                    <ToggleRow
                        label={`Default: ${param.defaultValue === true ? "True" : "False"}`}
                        checked={param.defaultValue === true}
                        onChange={() => props.onUpdateDefaultValue(name, param.defaultValue !== true)}
                    />
                </PanelSection>
            );

        case "Integer":
        case "FloatingPoint":
            return <NumericSettings name={name} param={param} errors={props.errors} onUpdateNumeric={props.onUpdateNumeric} />;

        case "Enumeration":
            return <EnumerationSettings name={name} param={param} errors={props.errors} onUpdateEnumeration={props.onUpdateEnumeration} />;

        case "File":
        case "Directory":
        case "License":
        case "Job":
        case "PublicIP":
            return null;

        case "Workflow":
            return null;
    }
}

// Numeric settings: default, min, max, step
// -------------------------------------------------------------------------------------------------------------------

function NumericSettings(props: {
    name: string;
    param: A2Parameter & { type: "Integer" | "FloatingPoint"; defaultValue?: number | null; min?: number | null; max?: number | null; step?: number | null };
    errors: CreatorValidationError[];
    onUpdateNumeric: (name: string, patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>) => void;
}): React.ReactNode {
    const {name, param, errors} = props;

    const parseNum = (s: string): number | null => {
        const trimmed = s.trim();
        if (trimmed === "") return null;
        const n = param.type === "Integer" ? Number.parseInt(trimmed, 10) : Number.parseFloat(trimmed);
        return Number.isFinite(n) ? n : null;
    };

    return (
        <PanelSection title="Numeric" id="creator-section-type" shortcut="V">
            <Label className="panel-field">
                <span className="panel-field-label">Default value</span>
                <Input
                    className={PanelInputClass}
                    type="number"
                    value={param.defaultValue ?? ""}
                    onChange={e => props.onUpdateNumeric(name, {defaultValue: parseNum(e.target.value)})}
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Minimum</span>
                <Input
                    className={PanelInputClass}
                    type="number"
                    value={param.min ?? ""}
                    onChange={e => props.onUpdateNumeric(name, {min: parseNum(e.target.value)})}
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Maximum</span>
                <Input
                    className={PanelInputClass}
                    type="number"
                    value={param.max ?? ""}
                    onChange={e => props.onUpdateNumeric(name, {max: parseNum(e.target.value)})}
                />
            </Label>
            <Label className="panel-field">
                <span className="panel-field-label">Step</span>
                <Input
                    className={PanelInputClass}
                    type="number"
                    value={param.step ?? ""}
                    onChange={e => props.onUpdateNumeric(name, {step: parseNum(e.target.value)})}
                />
            </Label>
            {errors.map((e, i) => (
                <Text key={i} fontSize={12} color="errorMain">{e.message}</Text>
            ))}
        </PanelSection>
    );
}

// Enumeration settings: ordered option list and default
// -------------------------------------------------------------------------------------------------------------------

function EnumerationSettings(props: {
    name: string;
    param: A2Parameter & { type: "Enumeration"; defaultValue?: string | null; options: A2EnumOption[] };
    errors: CreatorValidationError[];
    onUpdateEnumeration: (name: string, patch: { options?: A2EnumOption[]; defaultValue?: string | null }) => void;
}): React.ReactNode {
    const {name, param, errors} = props;

    const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
    const [dragToIndex, setDragToIndex] = useState<number | null>(null);
    const nextRowKey = useRef(0);
    const rowKeys = useRef<string[]>([]);
    const enumOptionsRef = useRef<HTMLDivElement | null>(null);

    const options = param.options ?? [];

    while (rowKeys.current.length < options.length + 1) {
        rowKeys.current.push(`enum-option-${nextRowKey.current++}`);
    }
    rowKeys.current.length = options.length + 1;

    const updateOption = (index: number, patch: Partial<A2EnumOption>) => {
        const nextOptions = options.map((o, i) => i === index ? {...o, ...patch} : o);
        props.onUpdateEnumeration(name, {options: nextOptions});
    };

    const removeOption = (index: number) => {
        const nextOptions = options.filter((_, i) => i !== index);
        const removedValue = options[index]?.value;
        const defaultValue = param.defaultValue === removedValue ? null : param.defaultValue;
        rowKeys.current.splice(index, 1);
        props.onUpdateEnumeration(name, {options: nextOptions, defaultValue});
        window.requestAnimationFrame(() => {
            const rows = enumOptionsRef.current?.querySelectorAll<HTMLElement>("[data-enum-option-row]");
            if (!rows || rows.length === 0) {
                enumOptionsRef.current?.querySelector<HTMLElement>("input")?.focus();
                return;
            }
            const nextRow = rows[Math.min(index, rows.length - 1)];
            nextRow.querySelector<HTMLElement>("input")?.focus();
        });
    };    const commitPlaceholder = (field: "title" | "value", text: string) => {
        const newOpt: A2EnumOption = {title: "", value: "", [field]: text};
        rowKeys.current.push(`enum-option-${nextRowKey.current++}`);
        props.onUpdateEnumeration(name, {options: [...options, newOpt]});
    };

    const swapOptions = (from: number, to: number) => {
        const nextOptions = [...options];
        const tmp = nextOptions[from];
        nextOptions[from] = nextOptions[to];
        nextOptions[to] = tmp;
        const tmpKey = rowKeys.current[from];
        rowKeys.current[from] = rowKeys.current[to];
        rowKeys.current[to] = tmpKey;
        props.onUpdateEnumeration(name, {options: nextOptions});
    };

    return (
        <PanelSection title="Enumeration" id="creator-section-type" shortcut="V">
            <Label className="panel-field">
                <span className="panel-field-label">Default value</span>
                <Select
                    value={param.defaultValue ?? ""}
                    onChange={e => props.onUpdateEnumeration(name, {defaultValue: e.target.value || null})}
                >
                    <option value="">— none —</option>
                    {options.map((o, i) => (
                        <option key={`${o.value}-${i}`} value={o.value}>{o.title}</option>
                    ))}
                </Select>
            </Label>

            <Text fontWeight={600} fontSize={13}>Options</Text>

            <div data-enum-options ref={enumOptionsRef}>
            {[...options, null].map((opt, index) => {
                if (opt == null) {
                    return (
                        <EnumOptionRow
                            key={rowKeys.current[index]}
                            placeholder
                            index={index}
                            count={options.length}
                            option={{title: "", value: ""}}
                            dragFromIndex={null}
                            dragToIndex={null}
                            onChange={() => {}}
                            onRemove={() => {}}
                            onReorder={() => {}}
                            onDragStart={() => {}}
                            onDragMove={() => {}}
                            onDragEnd={() => {}}
                            onCommit={commitPlaceholder}
                        />
                    );
                }

                return (
                    <EnumOptionRow
                        key={rowKeys.current[index]}
                        index={index}
                        count={options.length}
                        option={opt}
                        dragFromIndex={dragFromIndex}
                        dragToIndex={dragToIndex}
                        onChange={(patch) => updateOption(index, patch)}
                        onRemove={() => removeOption(index)}
                        onReorder={swapOptions}
                        onDragStart={(from) => {
                            setDragFromIndex(from);
                            setDragToIndex(from);
                        }}
                        onDragMove={(to) => setDragToIndex(to)}
                        onDragEnd={() => {
                            setDragFromIndex(null);
                            setDragToIndex(null);
                        }}
                    />
                );
            })}
            </div>

            {errors.map((e, i) => (
                <Text key={i} fontSize={12} color="errorMain">{e.message}</Text>
            ))}
        </PanelSection>
    );
}

function EnumOptionRow(props: {
    index: number;
    count: number;
    option: A2EnumOption;
    dragFromIndex: number | null;
    dragToIndex: number | null;
    placeholder?: boolean;
    onChange: (patch: Partial<A2EnumOption>) => void;
    onRemove: () => void;
    onReorder: (from: number, to: number) => void;
    onDragStart: (from: number) => void;
    onDragMove: (to: number) => void;
    onDragEnd: () => void;
    onCommit?: (field: "title" | "value", text: string) => void;
}): React.ReactNode {
    const rowRef = useRef<HTMLDivElement>(null);
    const isDragging = useRef(false);
    const dragStartY = useRef(0);
    const dragStartIndex = useRef(0);
    const dragToIndexRef = useRef(0);
    const lastOffsetRef = useRef(0);
    const [dragOffset, setDragOffset] = useState(0);

    const propsRef = useRef(props);
    propsRef.current = props;

    const onPointerMove = useCallback((e: PointerEvent) => {
        if (!isDragging.current || !rowRef.current) return;
        const offset = e.clientY - dragStartY.current;
        lastOffsetRef.current = offset;
        setDragOffset(offset);
        const rowHeight = rowRef.current.offsetHeight ?? 40;
        const rowsToMove = Math.round(offset / rowHeight);
        const c = propsRef.current.count;
        let targetIndex = dragStartIndex.current + rowsToMove;
        targetIndex = Math.max(0, Math.min(targetIndex, c - 1));
        dragToIndexRef.current = targetIndex;
        propsRef.current.onDragMove(targetIndex);
    }, []);

    const onPointerUp = useCallback(() => {
        if (!isDragging.current) return;
        isDragging.current = false;
        window.removeEventListener("pointermove", onPointerMove);
        window.removeEventListener("pointerup", onPointerUp);
        window.removeEventListener("pointercancel", onPointerUp);
        setDragOffset(0);
        propsRef.current.onDragEnd();
        if (Math.abs(lastOffsetRef.current) < 10) return;
        const fromIndex = dragStartIndex.current;
        const toIndex = dragToIndexRef.current;
        if (toIndex === fromIndex) return;
        propsRef.current.onReorder(fromIndex, toIndex);
    }, []);

    const onHandlePointerDown = useCallback((e: React.PointerEvent) => {
        if (e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        isDragging.current = true;
        dragStartY.current = e.clientY;
        dragStartIndex.current = propsRef.current.index;
        dragToIndexRef.current = propsRef.current.index;
        propsRef.current.onDragStart(propsRef.current.index);
        window.addEventListener("pointermove", onPointerMove);
        window.addEventListener("pointerup", onPointerUp);
        window.addEventListener("pointercancel", onPointerUp);
    }, []);

    const onRowKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (!e.altKey || e.metaKey || e.ctrlKey) return;
        if (e.key !== "ArrowUp" && e.key !== "ArrowDown") return;
        const {index, count} = propsRef.current;
        const targetIndex = index + (e.key === "ArrowUp" ? -1 : 1);
        if (targetIndex < 0 || targetIndex >= count) return;
        e.preventDefault();
        e.stopPropagation();
        propsRef.current.onReorder(index, targetIndex);
    }, []);

    const onDeleteEmpty = (event: React.KeyboardEvent<HTMLInputElement>, fieldValue: string) => {
        if (event.key !== "Delete" || fieldValue !== "" || event.metaKey || event.ctrlKey || event.altKey) return;
        event.preventDefault();
        event.stopPropagation();
        props.onRemove();
    };

    useEffect(() => {
        return () => {
            window.removeEventListener("pointermove", onPointerMove);
            window.removeEventListener("pointerup", onPointerUp);
            window.removeEventListener("pointercancel", onPointerUp);
        };
    }, []);

    const isDraggingThis = !props.placeholder && props.dragFromIndex != null && props.dragFromIndex === props.index;
    const isDropTarget = !props.placeholder && props.dragFromIndex != null && props.dragToIndex != null
        && props.dragToIndex === props.index && props.dragToIndex !== props.dragFromIndex;

    if (props.placeholder) {
        return (
            <div ref={rowRef} className={EnumOptionRowClass}>
                <div className={EnumDragHandleClass} style={{opacity: 0.3}}>
                    <Icon name="heroBars3" size={14} color="textSecondary" />
                </div>
                <Input
                    className={PanelInputClass}
                    placeholder="New option title…"
                    onChange={e => props.onCommit?.("title", e.target.value)}
                />
                <Input
                    className={PanelInputClass}
                    placeholder="New option value…"
                    onChange={e => props.onCommit?.("value", e.target.value)}
                />
            </div>
        );
    }

    return (
        <div
            ref={rowRef}
            className={EnumOptionRowClass}
            data-enum-option-row
            onKeyDown={onRowKeyDown}
            data-drop-target={isDropTarget || undefined}
            style={isDraggingThis ? {transform: `translateY(${dragOffset}px)`, zIndex: 10, opacity: 0.8} : undefined}
        >
            <div
                className={EnumDragHandleClass}
                onPointerDown={onHandlePointerDown}
                title="Drag to reorder"
            >
                <Icon name="heroBars3" size={14} color="textSecondary" />
            </div>
            <Input
                className={PanelInputClass}
                placeholder="Title"
                value={props.option.title}
                onChange={e => props.onChange({title: e.target.value})}
                onKeyDown={event => onDeleteEmpty(event, props.option.title)}
            />
            <Input
                className={PanelInputClass}
                placeholder="Value"
                value={props.option.value}
                onChange={e => props.onChange({value: e.target.value})}
                onKeyDown={event => onDeleteEmpty(event, props.option.value)}
            />
            <IconButton
                icon="heroTrash"
                tooltip="Remove option"
                color="errorMain"
                onClick={props.onRemove}
                compact
            />
        </div>
    );
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const PanelInputClass = injectStyle("creator-panel-input-param", k => `
    ${k} {
        width: 100%;
    }
`);

const EnumOptionRowClass = injectStyle("creator-enum-option-row", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 6px;
        padding: 8px;
        border-radius: 4px;
        transition: opacity 0.15s ease;
    }

    ${k}[data-drop-target] {
        opacity: 0.4;
    }
`);

const EnumDragHandleClass = injectStyle("creator-enum-drag-handle", k => `
    ${k} {
        display: flex;
        align-items: center;
        justify-content: center;
        cursor: grab;
        touch-action: none;
        user-select: none;
        flex-shrink: 0;
    }

    ${k}:active {
        cursor: grabbing;
    }
`);
