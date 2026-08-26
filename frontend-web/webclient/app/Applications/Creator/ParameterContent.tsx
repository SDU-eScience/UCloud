// Parameter content editor card
// =====================================================================================================================
// This card renders one full-width row per ordered draft parameter. Each row reuses the existing
// job-creation visual controls (Widget/FieldGroup/FieldRow) for visual fidelity, but the editor
// does not collect job values. The controls are wrapped in a display-only layer. Selection and
// reordering use stable row identity so they survive rename operations.
//
// The card supports:
//
// - render: A2 parameters → runtime display props → WidgetFieldRow
// - selection: click or keyboard; the selected row has a clear outline and drag handle
// - reorder: pointer drag on the handle, or Alt+ArrowUp/ArrowDown on a selected row
// - workflow: YAML-only rows that can be reordered or deleted but not visually edited
//
// Editor state lives in the draft. The card calls draft operations on each interaction. It never
// reads from the DOM.

import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {Box, Flex, Icon, Text} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {Application} from "@/Applications/AppStoreApi";
import {FieldGroup, Widget} from "@/Applications/Jobs/Widgets/index";
import {A2Yaml, A2Parameter} from "@/Applications/Creator/A2";
import {CreatorDraft} from "@/Applications/Creator/Draft";
import {a2ToRuntimeParameter} from "@/Applications/Creator/ParameterConversion";

// Build a minimal fake Application that the widget controls can read. The widgets need
// application.metadata for some types (e.g. workflow). We provide a minimal shape.
function fakeApplication(a2: A2Yaml): Application {
    const features = a2.features;
    return {
        metadata: {
            name: a2.name,
            version: a2.version,
            authors: [],
            title: a2.title ?? "",
            description: a2.description ?? "",
            public: false,
        },
        invocation: {
            tool: {name: a2.name, version: a2.version, tool: undefined},
            invocation: [],
            parameters: [],
            outputFileGlobs: [],
            applicationType: "BATCH",
            allowMultiNode: features?.multiNode ?? false,
            allowPublicIp: features?.ipAddresses ?? false,
            allowPublicLink: features?.links ?? false,
            allowAdditionalPeers: features?.jobLinking ?? false,
            allowAdditionalMounts: features?.folders ?? false,
            ssh: a2.ssh ? {mode: a2.ssh.mode.toUpperCase() as any} : undefined,
            fileExtensions: [],
            licenseServers: [],
        },
    };
}

// Props for the content card.
export interface ParameterContentProps {
    draft: CreatorDraft;
    onSelectParameter: (parameterId: string | null) => void;
    onReorder: (newOrder: string[]) => void;
    // Called when the user clicks "Open in YAML" on a Workflow row. Switches the view to YAML and
    // asks the YAML editor to scroll to the parameter key.
    onOpenWorkflowYaml: (parameterName: string) => void;
}

export function ParameterContent(props: ParameterContentProps): React.ReactNode {
    const {draft} = props;
    const {application} = draft;

    // Drag state lives here so every row can react to the current drop target. dragFromIndex is
    // the row being dragged; dragToIndex is the position it will land on. When not dragging, both
    // are null.
    const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
    const [dragToIndex, setDragToIndex] = useState<number | null>(null);

    const onReorder = useCallback((fromIndex: number, toIndex: number) => {
        const order = [...application.parametersOrder];
        // Swap the two elements instead of removing and inserting.
        const tmp = order[fromIndex];
        order[fromIndex] = order[toIndex];
        order[toIndex] = tmp;
        props.onReorder(order);
    }, [application.parametersOrder, props.onReorder]);

    if (application.parametersOrder.length === 0) {
        return (
            <Text color="textSecondary" mt="8px">
                No parameters yet. Add one from the widget drawer in the properties panel.
            </Text>
        );
    }

    return (
        <div className={ParameterListClass}>
            <FieldGroup>
                {application.parametersOrder.map((name, index) => {
                    const param = application.parameters[name];
                    if (!param) return null;
                    const id = draft.parameterIds[name] ?? `pid-${index}`;
                    const selected = draft.selection.parameterId === id;
                    return (
                        <ParameterRow
                            key={id}
                            name={name}
                            param={param}
                            id={id}
                            index={index}
                            count={application.parametersOrder.length}
                            selected={selected}
                            draft={draft}
                            onSelect={() => props.onSelectParameter(id)}
                            onReorder={onReorder}
                            onOpenWorkflowYaml={props.onOpenWorkflowYaml}
                            dragFromIndex={dragFromIndex}
                            dragToIndex={dragToIndex}
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
            </FieldGroup>
        </div>
    );
}

// Single parameter row
// -------------------------------------------------------------------------------------------------------------------

interface ParameterRowProps {
    name: string;
    param: A2Parameter;
    id: string;
    index: number;
    count: number;
    selected: boolean;
    draft: CreatorDraft;
    onSelect: () => void;
    onReorder: (fromIndex: number, toIndex: number) => void;
    onOpenWorkflowYaml: (parameterName: string) => void;
    dragFromIndex: number | null;
    dragToIndex: number | null;
    onDragStart: (fromIndex: number) => void;
    onDragMove: (toIndex: number) => void;
    onDragEnd: () => void;
}

function ParameterRow(props: ParameterRowProps): React.ReactNode {
    const {name, param, draft, selected, index, count,
           dragFromIndex, dragToIndex} = props;

    // Pointer drag state for reordering. All event handlers read from refs and have stable
    // identity (empty deps). This is critical: the parent passes inline callbacks that change
    // identity on every render, which would otherwise cause the useEffect cleanup to remove
    // the window listeners mid-drag.
    const rowRef = useRef<HTMLDivElement>(null);
    const isDragging = useRef(false);
    const dragStartY = useRef(0);
    const dragStartIndex = useRef(0);
    const dragToIndexRef = useRef(0);
    const lastOffsetRef = useRef(0);
    const [dragOffset, setDragOffset] = useState(0);

    // Store latest props so the stable window listeners always read current values.
    const propsRef = useRef(props);
    propsRef.current = props;

    const onPointerMove = useCallback((e: PointerEvent) => {
        if (!isDragging.current || !rowRef.current) return;
        const offset = e.clientY - dragStartY.current;
        lastOffsetRef.current = offset;
        setDragOffset(offset);

        const rowHeight = rowRef.current.offsetHeight ?? 64;
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

        const fromIndex = dragStartIndex.current;
        const toIndex = dragToIndexRef.current;
        setDragOffset(0);
        propsRef.current.onDragEnd();
        if (Math.abs(lastOffsetRef.current) < 10) return; // not enough movement
        if (toIndex === fromIndex) return;
        propsRef.current.onReorder(fromIndex, toIndex);
    }, []);

    const onHandlePointerDown = useCallback((e: React.PointerEvent) => {
        if (e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        const idx = propsRef.current.index;
        isDragging.current = true;
        dragStartY.current = e.clientY;
        dragStartIndex.current = idx;
        dragToIndexRef.current = idx;
        propsRef.current.onSelect();
        propsRef.current.onDragStart(idx);
        window.addEventListener("pointermove", onPointerMove);
        window.addEventListener("pointerup", onPointerUp);
        window.addEventListener("pointercancel", onPointerUp);
    }, []);

    // Remove listeners only on unmount. Because the callbacks above have stable identity,
    // the cleanup correctly removes the same functions that were added to the window.
    useEffect(() => {
        return () => {
            window.removeEventListener("pointermove", onPointerMove);
            window.removeEventListener("pointerup", onPointerUp);
            window.removeEventListener("pointercancel", onPointerUp);
        };
    }, []);

    // Keyboard reorder: Alt+ArrowUp / Alt+ArrowDown on the selected row.
    const onKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (!selected) return;
        const isReorderKey = e.altKey && (e.key === "ArrowUp" || e.key === "ArrowDown");
        if (!isReorderKey) return;
        e.preventDefault();
        const direction = e.key === "ArrowUp" ? -1 : 1;
        const targetIndex = index + direction;
        if (targetIndex < 0 || targetIndex >= count) return;
        props.onReorder(index, targetIndex);
    }, [selected, index, count, props.onReorder]);

    // Drag visual state. The dragged row lifts and follows the pointer. The row it will swap
    // into dims to show where it will land.
    const isDraggingThis = dragFromIndex != null && dragFromIndex === index;
    const isDropTarget = dragFromIndex != null && dragToIndex != null && dragToIndex === index && dragToIndex !== dragFromIndex;

    // Workflow rows are YAML-only. They can be reordered and deleted (via the parameter panel),
    // but their content is edited in YAML. The row gives a direct action to open the relevant YAML
    // section so the user lands on the parameter key.
    if (param.type === "Workflow") {
        return (
            <div
                ref={rowRef}
                className={WorkflowRowClass}
                data-selected={selected}
                data-drop-target={isDropTarget || undefined}
                data-row-id={props.id}
                onClick={props.onSelect}
                onKeyDown={onKeyDown}
                tabIndex={0}
                style={isDraggingThis ? {transform: `translateY(${dragOffset}px)`, zIndex: 10, opacity: 0.8} : undefined}
            >
                <DragHandle onPointerDown={onHandlePointerDown} visible={selected} centerOffset={28} />
                <div className={WorkflowRowBodyClass}>
                    <Flex alignItems="center" gap="8px">
                        <Icon name="heroCodeBracket" size={16} color="textSecondary" />
                        <Text fontWeight={600}>{param.title || name}</Text>
                        <Text fontSize={11} color="textSecondary">Workflow (YAML-only)</Text>
                        <Box
                            ml="auto"
                            onClick={e => e.stopPropagation()}
                        >
                            <IconButton
                                icon="heroCodeBracket"
                                tooltip="Open in YAML"
                                compact
                                onClick={() => props.onOpenWorkflowYaml(name)}
                            />
                        </Box>
                    </Flex>
                    <Text fontSize={12} color="textSecondary" mt="4px">
                        Edit the workflow content in the YAML view.
                    </Text>
                </div>
            </div>
        );
    }

    // Standard parameter: render with the Widget control for visual fidelity.
    const runtimeParam = a2ToRuntimeParameter(name, param);
    const app = fakeApplication(draft.application);
    const bodyRef = useRef<HTMLDivElement>(null);
    const [handleOffset, setHandleOffset] = useState(0);

    useLayoutEffect(() => {
        const body = bodyRef.current;
        if (!body) return;
        // The Widget renders a FieldRow (data-field-row). Its first child is the
        // description column (title + optional markdown description). We center the
        // drag handle on that block, not on the entire row.
        const fieldRow = body.querySelector<HTMLElement>("[data-field-row]");
        if (!fieldRow) return;
        const desc = fieldRow.firstElementChild as HTMLElement | null;
        if (!desc) return;
        const rect = desc.getBoundingClientRect();
        const bodyRect = body.getBoundingClientRect();
        setHandleOffset(rect.top - bodyRect.top + rect.height / 2);
    }, [param.title, param.description]);

    return (
        <div
            ref={rowRef}
            className={ParameterRowWrapperClass}
            data-selected={selected}
            data-drop-target={isDropTarget || undefined}
            data-row-id={props.id}
            onClick={props.onSelect}
            onKeyDown={onKeyDown}
            tabIndex={0}
            style={isDraggingThis ? {transform: `translateY(${dragOffset}px)`, zIndex: 10, opacity: 0.8} : undefined}
        >
            <DragHandle onPointerDown={onHandlePointerDown} visible={selected} centerOffset={handleOffset} />
            <div ref={bodyRef} className={ParameterRowBodyClass}>
                <Widget
                    application={app}
                    parameter={runtimeParam}
                    errors={{}}
                    setErrors={() => {}}
                    injectWorkflowParameters={() => {}}
                    fieldGroup
                    selected={true}
                    onValueChange={() => {}}
                    displayTitle={param.title || name}
                />
            </div>
        </div>
    );
}

// Drag handle. Only visible when the row is selected. centerOffset positions the icon
// at the vertical center of the title+description block (in pixels from the row top).
function DragHandle(props: {
    visible: boolean;
    onPointerDown: (e: React.PointerEvent) => void;
    centerOffset?: number;
}): React.ReactNode {
    return (
        <div
            className={DragHandleClass}
            data-visible={props.visible}
            onPointerDown={props.onPointerDown}
            title="Drag to reorder"
            style={props.centerOffset != null ? {paddingTop: Math.max(0, props.centerOffset - 7)} : undefined}
        >
            <Icon name="heroBars3" size={14} color="textSecondary" />
        </div>
    );
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const ParameterListClass = injectStyle("creator-parameter-list", k => `
    ${k} {
        margin-top: 8px;
    }
`);

const ParameterRowWrapperClass = injectStyle("creator-parameter-row", k => `
    ${k} {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 12px;
        align-items: start;
        border: 2px solid transparent;
        border-radius: 6px;
        padding: 2px;
        cursor: pointer;
        transition: border-color 0.15s ease, opacity 0.15s ease;
    }

    ${k}[data-selected="true"] {
        border-color: var(--primaryMain);
    }

    ${k}[data-drop-target] {
        opacity: 0.4;
    }

    ${k}:focus {
        outline: none;
    }

    ${k}:focus:not([data-selected="true"]) {
        border-color: var(--borderColorHover, var(--textSecondary));
    }
`);

const ParameterRowBodyClass = injectStyleSimple("creator-parameter-row-body", `
    min-width: 0;
    overflow: hidden;
    pointer-events: none;
`);

const WorkflowRowClass = injectStyle("creator-workflow-row", k => `
    ${k} {
        display: grid;
        grid-template-columns: auto 1fr;
        gap: 12px;
        border: 2px solid transparent;
        border-radius: 6px;
        padding: 8px 12px;
        cursor: pointer;
        transition: border-color 0.15s ease, opacity 0.15s ease;
    }

    ${k}[data-selected="true"] {
        border-color: var(--primaryMain);
    }

    ${k}[data-drop-target] {
        opacity: 0.4;
    }

    ${k}:focus {
        outline: none;
    }

    ${k}:focus:not([data-selected="true"]) {
        border-color: var(--borderColorHover, var(--textSecondary));
    }
`);

const WorkflowRowBodyClass = injectStyleSimple("creator-workflow-row-body", `
    min-width: 0;
`);

const DragHandleClass = injectStyle("creator-drag-handle", k => `
    ${k} {
        width: 0;
        min-width: 0;
        overflow: hidden;
        display: flex;
        align-items: flex-start;
        justify-content: center;
        cursor: grab;
        opacity: 0;
        transition: width 0.15s ease, opacity 0.15s ease;
        touch-action: none;
        user-select: none;
    }

    ${k}[data-visible="true"] {
        width: 24px;
        opacity: 1;
    }

    ${k}:hover {
        opacity: 1;
    }

    ${k}:active {
        cursor: grabbing;
    }
`);
