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

import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {Button, Input, Label, Select, Text} from "@/ui-components";
import Icon from "@/ui-components/Icon";
import {IconButton} from "@/ui-components/IconButton";
import {injectStyle} from "@/Unstyled";
import {addStandardDialog} from "@/UtilityComponents";
import {A2Parameter, A2EnumOption} from "@/Applications/Creator/A2";
import {CreatorDraft, CreatorValidationError} from "@/Applications/Creator/Draft";
import {nameForId} from "@/Applications/Creator/DraftOperations";
import {PanelSection, PanelSectionClass, ToggleRow} from "@/Applications/Creator/ParameterPanelShared";

export interface ParameterPanelProps {
    draft: CreatorDraft;
    // True when the YAML source is invalid. The panel becomes read-only by disabling pointer
    // events so visual edits cannot conflict with the invalid source.
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
    // The Back button stays enabled in read-only mode so the user can still leave the parameter.
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
                    <Button
                        type="button"
                        color="errorMain"
                        onClick={() => {
                            addStandardDialog({
                                title: "Delete parameter",
                                message: `Delete "${name}"? References in the invocation will remain and must be resolved manually.`,
                                confirmText: "Delete",
                                cancelText: "Cancel",
                                cancelButtonColor: "errorMain",
                                confirmButtonColor: "errorMain",
                                onConfirm: () => props.onDelete(name),
                            });
                        }}
                    >
                        <Icon name="heroTrash" mr={6} size={16} />
                        Delete parameter
                    </Button>
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

// Parameter header. A back-arrow icon button sits to the left of the title. The parameter
// settings render below the header without any collapsible behavior.
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

    // Keep the local name input in sync if the parameter name changes externally (e.g. undo).
    React.useEffect(() => { setNameValue(name); }, [name]);

    // Name errors are the validation messages that describe the parameter name itself. The panel
    // shows these under the name field. All other errors (numeric, enumeration) are shown under
    // their respective type-specific controls.
    const nameError = errors.find(e =>
        e.message.startsWith("Parameter name") || e.message.startsWith("Duplicate parameter name"),
    );

    return (
        <PanelSection title="Common">
            <Label className="panel-field">
                <span className="panel-field-label">Parameter name</span>
                <Input
                    className={PanelInputClass}
                    value={nameValue}
                    onChange={e => setNameValue(e.target.value)}
                    onBlur={() => {
                        if (nameValue.trim() !== name) {
                            props.onRename(name, nameValue.trim());
                        }
                    }}
                    error={nameError != null}
                />
                {nameError ? <Text fontSize={12} color="errorMain" mt="4px">{nameError.message}</Text> : null}
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
                <PanelSection title="Default value">
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
                <PanelSection title="Default value">
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
        if (s.trim() === "") return null;
        const n = param.type === "Integer" ? parseInt(s, 10) : parseFloat(s);
        return isNaN(n) ? null : n;
    };

    return (
        <PanelSection title="Numeric">
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

    // Drag state lives here so every row can react to the current drop target.
    const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
    const [dragToIndex, setDragToIndex] = useState<number | null>(null);
    const nextRowKey = useRef(0);
    const rowKeys = useRef<string[]>([]);

    while (rowKeys.current.length < param.options.length + 1) {
        rowKeys.current.push(`enum-option-${nextRowKey.current++}`);
    }
    rowKeys.current.length = param.options.length + 1;

    const updateOption = (index: number, patch: Partial<A2EnumOption>) => {
        const options = param.options.map((o, i) => i === index ? {...o, ...patch} : o);
        props.onUpdateEnumeration(name, {options});
    };

    const removeOption = (index: number) => {
        const options = param.options.filter((_, i) => i !== index);
        const removedValue = param.options[index]?.value;
        const defaultValue = param.defaultValue === removedValue ? null : param.defaultValue;
        rowKeys.current.splice(index, 1);
        props.onUpdateEnumeration(name, {options, defaultValue});
    };

    const commitPlaceholder = (field: "title" | "value", text: string) => {
        const newOpt: A2EnumOption = {title: "", value: "", [field]: text};
        rowKeys.current.push(`enum-option-${nextRowKey.current++}`);
        props.onUpdateEnumeration(name, {options: [...param.options, newOpt]});
    };

    const swapOptions = (from: number, to: number) => {
        const options = [...param.options];
        const tmp = options[from];
        options[from] = options[to];
        options[to] = tmp;
        const tmpKey = rowKeys.current[from];
        rowKeys.current[from] = rowKeys.current[to];
        rowKeys.current[to] = tmpKey;
        props.onUpdateEnumeration(name, {options});
    };

    return (
        <PanelSection title="Enumeration">
            <Label className="panel-field">
                <span className="panel-field-label">Default value</span>
                <Select
                    value={param.defaultValue ?? ""}
                    onChange={e => props.onUpdateEnumeration(name, {defaultValue: e.target.value || null})}
                >
                    <option value="">— none —</option>
                    {param.options.map(o => (
                        <option key={o.value} value={o.value}>{o.title}</option>
                    ))}
                </Select>
            </Label>

            <Text fontWeight={600} fontSize={13}>Options</Text>

            <div data-enum-options>
            {[...param.options, null].map((opt, index) => {
                if (opt == null) {
                    return (
                        <EnumOptionRow
                            key={rowKeys.current[index]}
                            placeholder
                            index={index}
                            count={param.options.length}
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

                // The last option in the array may be a "pending" option that was just
                // committed from the placeholder. It uses the same component so the DOM
                // input keeps focus.
                return (
                    <EnumOptionRow
                        key={rowKeys.current[index]}
                        index={index}
                        count={param.options.length}
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

// A committed option row with title, value, drag handle, and delete button.
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

    // Stable props ref so window listeners read current values.
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

    useEffect(() => {
        return () => {
            window.removeEventListener("pointermove", onPointerMove);
            window.removeEventListener("pointerup", onPointerUp);
            window.removeEventListener("pointercancel", onPointerUp);
        };
    }, []);

    // Drag visual state. The dragged row lifts and follows the pointer. The row it will
    // swap with dims to show where it will land.
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
            />
            <Input
                className={PanelInputClass}
                placeholder="Value"
                value={props.option.value}
                onChange={e => props.onChange({value: e.target.value})}
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
