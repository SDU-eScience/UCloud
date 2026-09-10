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
// - reorder: pointer drag anywhere on the row, or Alt+ArrowUp/ArrowDown on a selected row
// - workflow: YAML-only rows that can be reordered or deleted but not visually edited
//
// Editor state lives in the draft. The card calls draft operations on each interaction. It never
// reads from the DOM.
//
// This file also contains:
// - The A2-to-runtime parameter conversion used to render the display widgets.
// - The feature cards (storage and connectivity) shown when runtime features are enabled. They
//   reuse the job-creation widget controls for visual fidelity and highlight the corresponding
//   metadata toggle when clicked.

import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {Flex, Icon, Text} from "@/ui-components";
import {Application, ApplicationParameter, ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {FieldGroup, FieldRow, Widget} from "@/Applications/Jobs/Widgets/index";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {A2Yaml, A2Parameter, CreatorDraft} from "@/Applications/Creator/Draft";
import {CreatorHighlightTarget} from "@/Applications/Creator/CreatorKeyboard";

const A2_RUNTIME_TYPE_MAP: Record<string, string> = {
    "Text": "text",
    "TextArea": "textarea",
    "Boolean": "boolean",
    "Integer": "integer",
    "FloatingPoint": "floating_point",
    "Enumeration": "enumeration",
    "File": "input_file",
    "Directory": "input_directory",
    "License": "license_server",
    "Job": "peer",
    "PublicIP": "network_ip",
    "Workflow": "workflow",
};

export function a2ToRuntimeType(type: string): string {
    return A2_RUNTIME_TYPE_MAP[type] ?? type;
}

export function a2ToRuntimeParameter(name: string, param: A2Parameter): ApplicationParameter {
    const base = {
        name,
        title: param.title,
        description: param.description,
        optional: param.optional,
    };

    switch (param.type) {
        case "Text":
            return {
                ...base,
                type: "text",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.Text;
        case "TextArea":
            return {
                ...base,
                type: "textarea",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.TextArea;
        case "Boolean":
            return {
                ...base,
                type: "boolean",
                trueValue: "true",
                falseValue: "false",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.Bool;
        case "Integer":
            return {
                ...base,
                type: "integer",
                defaultValue: param.defaultValue ?? undefined,
                min: param.min ?? undefined,
                max: param.max ?? undefined,
                step: param.step ?? undefined,
            } as ApplicationParameterNS.Integer;
        case "FloatingPoint":
            return {
                ...base,
                type: "floating_point",
                defaultValue: param.defaultValue ?? undefined,
                min: param.min ?? undefined,
                max: param.max ?? undefined,
                step: param.step ?? undefined,
            } as ApplicationParameterNS.FloatingPoint;
        case "Enumeration":
            return {
                ...base,
                type: "enumeration",
                defaultValue: param.defaultValue ?? undefined,
                options: (param.options ?? []).map(o => ({name: o.title, value: o.value})),
            } as ApplicationParameterNS.Enumeration;
        case "File":
            return {
                ...base,
                type: "input_file",
                defaultValue: undefined,
            } as ApplicationParameterNS.InputFile;
        case "Directory":
            return {
                ...base,
                type: "input_directory",
                defaultValue: undefined,
            } as ApplicationParameterNS.InputDirectory;
        case "License":
            return {
                ...base,
                type: "license_server",
                tagged: [],
                defaultValue: undefined,
            } as ApplicationParameterNS.LicenseServer;
        case "Job":
            return {
                ...base,
                type: "peer",
                suggestedApplication: undefined,
                defaultValue: undefined,
            } as ApplicationParameterNS.Peer;
        case "PublicIP":
            return {
                ...base,
                type: "network_ip",
                defaultValue: undefined,
            } as ApplicationParameterNS.NetworkIP;
        case "Workflow":
            return {
                ...base,
                type: "workflow",
                defaultValue: undefined,
            } as ApplicationParameterNS.Workflow;
        default:
            return {
                ...base,
                type: "text",
                defaultValue: undefined,
            } as ApplicationParameterNS.Text;
    }
}

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
            ssh: sshRuntimeMode(a2),
            fileExtensions: [],
            licenseServers: [],
        },
    };
}

export interface ParameterContentProps {
    draft: CreatorDraft;
    onSelectParameter: (parameterId: string | null) => void;
    onReorder: (newOrder: string[]) => void;
    onOpenWorkflowYaml: (parameterName: string) => void;
}

export const ParameterContent = React.memo(ParameterContentBase, (prev, next) =>
    prev.draft.application.parameters === next.draft.application.parameters &&
    prev.draft.application.parametersOrder === next.draft.application.parametersOrder &&
    prev.draft.application === next.draft.application &&
    prev.draft.parameterIds === next.draft.parameterIds &&
    prev.draft.selection === next.draft.selection &&
    prev.onSelectParameter === next.onSelectParameter &&
    prev.onReorder === next.onReorder &&
    prev.onOpenWorkflowYaml === next.onOpenWorkflowYaml
);

function ParameterContentBase(props: ParameterContentProps): React.ReactNode {
    const {draft} = props;
    const {application} = draft;

    const [dragFromIndex, setDragFromIndex] = useState<number | null>(null);
    const [dragToIndex, setDragToIndex] = useState<number | null>(null);

    const onReorder = useCallback((fromIndex: number, toIndex: number) => {
        const order = [...application.parametersOrder];
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
                    const id = draft.parameterIds[name] ?? `pid-x-${index}-${name}`;
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
                            onSelectParameter={props.onSelectParameter}
                            onReorder={onReorder}
                            onDeselect={() => props.onSelectParameter(null)}
                            previousId={index === 0 ? null : draft.parameterIds[application.parametersOrder[index - 1]] ?? null}
                            nextId={index === application.parametersOrder.length - 1 ? null : draft.parameterIds[application.parametersOrder[index + 1]] ?? null}
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
    onSelectParameter: (parameterId: string) => void;
    onReorder: (fromIndex: number, toIndex: number) => void;
    onDeselect: () => void;
    previousId: string | null;
    nextId: string | null;
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
        if (Math.abs(lastOffsetRef.current) < 10) return;
        if (toIndex === fromIndex) return;
        propsRef.current.onReorder(fromIndex, toIndex);
    }, []);

    const onHandlePointerDown = useCallback((e: React.PointerEvent) => {
        if (e.button !== 0) return;
        e.preventDefault();
        e.stopPropagation();
        rowRef.current?.focus();
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

    useEffect(() => {
        return () => {
            window.removeEventListener("pointermove", onPointerMove);
            window.removeEventListener("pointerup", onPointerUp);
            window.removeEventListener("pointercancel", onPointerUp);
        };
    }, []);

    const onKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.metaKey || e.ctrlKey) return;
        if (e.altKey && (e.key === "ArrowUp" || e.key === "ArrowDown")) {
            e.preventDefault();
            if (!selected) return;
            const direction = e.key === "ArrowUp" ? -1 : 1;
            const targetIndex = index + direction;
            if (targetIndex < 0 || targetIndex >= count) return;
            props.onReorder(index, targetIndex);
            window.requestAnimationFrame(() => rowRef.current?.focus());
            return;
        }
        if (e.altKey) return;
        if (e.key === "ArrowUp" || e.key === "ArrowDown") {
            if (e.target !== e.currentTarget) return;
            e.preventDefault();
            e.stopPropagation();
            const targetId = e.key === "ArrowUp" ? props.previousId : props.nextId;
            if (targetId == null) return;
            props.onSelectParameter(targetId);
            window.requestAnimationFrame(() => {
                const next = rowRef.current?.parentElement?.querySelector<HTMLElement>(`[data-row-id="${CSS.escape(targetId)}"]`);
                next?.focus();
                next?.scrollIntoView({block: "nearest"});
            });
            return;
        }
        if (e.key === "Enter" || e.key === " ") {
            if (e.target !== e.currentTarget) return;
            e.preventDefault();
            props.onSelect();
            return;
        }
        if (e.key === "Escape") {
            if (e.target !== e.currentTarget) return;
            if (!selected) return;
            e.preventDefault();
            props.onDeselect();
        }
    }, [selected, index, count, props.onReorder, props.onSelect, props.onSelectParameter, props.onDeselect, props.previousId, props.nextId]);

    const isDraggingThis = dragFromIndex != null && dragFromIndex === index;
    const isDropTarget = dragFromIndex != null && dragToIndex != null && dragToIndex === index && dragToIndex !== dragFromIndex;

    const runtimeParam = a2ToRuntimeParameter(name, param);
    const app = fakeApplication(draft.application);
    const bodyRef = useRef<HTMLDivElement>(null);
    const [handleOffset, setHandleOffset] = useState(0);

    useLayoutEffect(() => {
        const body = bodyRef.current;
        if (!body) return;
        const fieldRow = body.querySelector<HTMLElement>("[data-field-row]");
        if (!fieldRow) return;
        const desc = fieldRow.firstElementChild as HTMLElement | null;
        if (!desc) return;
        const rect = desc.getBoundingClientRect();
        const bodyRect = body.getBoundingClientRect();
        setHandleOffset(rect.top - bodyRect.top + rect.height / 2);
    }, [param.title, param.description]);

    if (param.type === "Workflow") {
        return (
            <div
                ref={rowRef}
                className={ParameterRowWrapperClass}
                data-selected={selected}
                data-dragging={isDraggingThis || undefined}
                data-drop-target={isDropTarget || undefined}
                data-row-id={props.id}
                onClick={props.onSelect}
                onPointerDown={onHandlePointerDown}
                onKeyDown={onKeyDown}
                tabIndex={0}
                style={isDraggingThis ? {transform: `translateY(${dragOffset}px)`} : undefined}
            >
                <DragHandle onPointerDown={onHandlePointerDown} visible={selected} centerOffset={handleOffset} />
                <div ref={bodyRef} className={ParameterRowBodyClass}>
                    <FieldRow
                        title={param.title || name}
                        description={param.description}
                        control={
                            <button
                                type="button"
                                className={WorkflowYamlButtonClass}
                                onPointerDown={e => e.stopPropagation()}
                                onClick={e => {
                                    e.stopPropagation();
                                    props.onOpenWorkflowYaml(name);
                                }}
                            >
                                Workflow (YAML-only)
                            </button>
                        }
                        required={!param.optional}
                        parameterType="workflow"
                    />
                </div>
            </div>
        );
    }

    return (
        <div
            ref={rowRef}
            className={ParameterRowWrapperClass}
            data-selected={selected}
            data-dragging={isDraggingThis || undefined}
            data-drop-target={isDropTarget || undefined}
            data-row-id={props.id}
            onClick={props.onSelect}
            onPointerDown={onHandlePointerDown}
            onKeyDown={onKeyDown}
            tabIndex={0}
            style={isDraggingThis ? {transform: `translateY(${dragOffset}px)`} : undefined}
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

// Feature cards
// -------------------------------------------------------------------------------------------------------------------
// When a runtime feature is enabled, the content area shows the corresponding cards that the user
// would see on the job creation page. These cards reuse the existing job-creation widget controls
// for visual fidelity, wrapped in a display-only layer with `pointer-events: none` to disable
// interactivity — the same pattern as the parameter rows.
//
// - Storage card: shown when `features.folders` is enabled. Uses the input_directory widget.
// - Connectivity card: shown when any of links, public IPs, job linking, or SSH is enabled. Uses
//   the corresponding widget controls (ingress, peer, network_ip, SSH). Each sub-section is
//   clickable and scrolls to the metadata toggle that controls it.

export interface FeatureCardsProps {
    draft: CreatorDraft;
    onHighlight: (target: CreatorHighlightTarget) => void;
}

export const FeatureCards = React.memo(FeatureCardsBase, (prev, next) =>
    prev.draft.application.features === next.draft.application.features &&
    prev.draft.application.ssh === next.draft.application.ssh &&
    prev.draft.application.software === next.draft.application.software &&
    prev.onHighlight === next.onHighlight
);

function FeatureCardsBase(props: FeatureCardsProps): React.ReactNode {
    const {application} = props.draft;
    const features = application.features ?? null;

    const showStorage = features?.folders === true;
    const showConnectivity = hasConnectivity(application);

    if (!showStorage && !showConnectivity) return null;

    return (
        <>
            {showStorage ? (
                <StorageCard application={application} onHighlight={() => props.onHighlight("feature-folders")} />
            ) : null}
            {showConnectivity ? (
                <ConnectivityCard application={application} onHighlight={props.onHighlight} />
            ) : null}
        </>
    );
}

function fakeApplicationForCards(a2: A2Yaml): Application {
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
            ssh: sshRuntimeMode(a2),
            fileExtensions: [],
            licenseServers: [],
        },
    };
}

function sshRuntimeMode(a2: A2Yaml): {mode: "DISABLED" | "OPTIONAL" | "MANDATORY"} | undefined {
    const mode = sshModeOf(a2);
    return mode == null ? undefined : {mode};
}

function sshModeOf(a2: A2Yaml): "DISABLED" | "OPTIONAL" | "MANDATORY" | null {
    const raw = a2.ssh?.mode;
    const normalized = typeof raw === "string" ? raw.toLowerCase() : null;
    if (normalized === "mandatory") return "MANDATORY";
    if (normalized === "optional") return "OPTIONAL";
    if (normalized === "disabled") return "DISABLED";
    return null;
}

function StorageCard(props: {application: A2Yaml; onHighlight: () => void}): React.ReactNode {
    const app = fakeApplicationForCards(props.application);
    const param: ApplicationParameter = {
        type: "input_directory",
        description: "Add directories to your job. Available in /work.",
        title: "",
        optional: true,
        name: "resourceFolder0",
    };

    return (
        <div className={FeatureCardIslandClass} id="creator-card-storage">
            <FeatureCardHeading>Storage</FeatureCardHeading>
            <FeatureSection onClick={props.onHighlight}>
                <div className={FeatureSectionBodyClass}>
                    <FieldGroup>
                        <Widget
                            application={app}
                            parameter={param}
                            errors={{}}
                            setErrors={() => {}}
                            injectWorkflowParameters={() => {}}
                            compact
                            selected={false}
                            displayTitle="Folder #1"
                            onValueChange={() => {}}
                        />
                    </FieldGroup>
                </div>
            </FeatureSection>
        </div>
    );
}

function ConnectivityCard(props: {
    application: A2Yaml;
    onHighlight: (target: CreatorHighlightTarget) => void;
}): React.ReactNode {
    const {application: app2} = props;
    const features = app2.features;
    const sshEnabled = sshModeOf(app2) != null && sshModeOf(app2) !== "DISABLED";
    const app = fakeApplicationForCards(app2);

    return (
        <div className={FeatureCardIslandClass} id="creator-card-connectivity">
            <FeatureCardHeading>Connectivity</FeatureCardHeading>
            <Flex flexDirection="column" gap="0">
                {sshEnabled ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-ssh")}>
                        <div className={FeatureSectionBodyClass}>
                            <SshWidget
                                application={app}
                                embedded
                                fieldRow
                                onSshStatusChanged={() => {}}
                                onSshKeysValid={() => {}}
                                initialEnabledStatus={false}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.links === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-links")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "ingress", description: "Public links make your job accessible through a web browser.", title: "", optional: true, name: "ingress0"}}
                                displayTitle="Public link #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.jobLinking === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-jobLinking")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "private_network", description: "Connect this job to a network of other jobs.", title: "", optional: true, name: "resourcePrivateNetwork0"}}
                                displayTitle="Private network #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
                {features?.ipAddresses === true ? (
                    <FeatureSection onClick={() => props.onHighlight("feature-ipAddresses")}>
                        <div className={FeatureSectionBodyClass}>
                            <CompactResourceDisplay
                                param={{type: "network_ip", description: "Make your job reachable from the Internet.", title: "", optional: true, name: "network0"}}
                                displayTitle="Public IP #1"
                                app={app}
                            />
                        </div>
                    </FeatureSection>
                ) : null}
            </Flex>
        </div>
    );
}

function FeatureSection(props: {onClick: () => void; children: React.ReactNode}): React.ReactNode {
    const onClick = useCallback(() => props.onClick(), [props.onClick]);
    return (
        <div
            className={FeatureSectionWrapperClass}
            onClick={onClick}
            role="button"
            tabIndex={0}
            onKeyDown={e => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); onClick(); } }}
        >
            {props.children}
        </div>
    );
}

function CompactResourceDisplay(props: {
    param: ApplicationParameter;
    displayTitle: string;
    app: Application;
}): React.ReactNode {
    return (
        <FieldGroup>
            <Widget
                application={props.app}
                parameter={props.param}
                errors={{}}
                setErrors={() => {}}
                injectWorkflowParameters={() => {}}
                compact
                selected={false}
                displayTitle={props.displayTitle}
                onValueChange={() => {}}
            />
        </FieldGroup>
    );
}

function FeatureCardHeading(props: React.PropsWithChildren<{action?: React.ReactNode}>): React.ReactNode {
    return (
        <Flex alignItems="center" gap="8px" mb="16px">
            <Text fontWeight="normal" fontSize="16px">{props.children}</Text>
            {!props.action ? null : <Flex ml="auto">{props.action}</Flex>}
        </Flex>
    );
}

function hasConnectivity(app: A2Yaml): boolean {
    const features = app.features;
    const sshMode = sshModeOf(app) ?? "DISABLED";
    return sshMode !== "DISABLED" ||
        features?.links === true ||
        features?.ipAddresses === true ||
        features?.jobLinking === true;
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const ParameterListClass = injectStyle("creator-parameter-list", k => `
    ${k} {
        margin-top: 8px;
        user-select: none;
        -webkit-user-select: none;
    }
`);

const ParameterRowWrapperClass = injectStyle("creator-parameter-row", k => `
    ${k} {
        display: grid;
        grid-template-columns: auto 1fr;
        column-gap: 0;
        align-items: start;
        border: 2px solid transparent;
        border-radius: 6px;
        padding: 2px;
        cursor: grab;
        touch-action: none;
        user-select: none;
        -webkit-user-select: none;
        transition: border-color 0.15s ease, opacity 0.15s ease;
    }

    ${k}[data-selected="true"] {
        border-color: var(--primaryMain);
    }

    ${k}[data-drop-target] {
        opacity: 0.4;
        border-color: var(--primaryMain);
        border-style: dashed;
    }

    ${k}[data-dragging] {
        position: relative;
        z-index: 10;
        opacity: 1;
        background: var(--backgroundCard);
    }

    ${k}:focus {
        outline: none;
    }

    ${k}:focus:not([data-selected="true"]) {
        border-color: var(--borderColorHover, var(--textSecondary));
    }

    ${k}:active {
        cursor: grabbing;
    }
`);

const ParameterRowBodyClass = injectStyleSimple("creator-parameter-row-body", `
    min-width: 0;
    overflow: hidden;
    pointer-events: none;
`);

const WorkflowYamlButtonClass = injectStyle("creator-workflow-yaml-button", k => `
    ${k} {
        pointer-events: auto;
        border: 0;
        padding: 0;
        color: var(--textSecondary);
        background: transparent;
        cursor: pointer;
        font: inherit;
        text-align: left;
        user-select: none;
        -webkit-user-select: none;
    }

    ${k}:hover {
        color: var(--textPrimary);
        text-decoration: underline;
    }
`);

const DragHandleClass = injectStyle("creator-drag-handle", k => `
    ${k} {
        width: 0;
        min-width: 0;
        margin-right: 0;
        overflow: hidden;
        display: flex;
        align-items: flex-start;
        justify-content: center;
        cursor: grab;
        opacity: 0;
        transition: width 0.15s ease, margin-right 0.15s ease, opacity 0.15s ease;
        touch-action: none;
        user-select: none;
    }

    ${k}[data-visible="true"] {
        width: 24px;
        margin-right: 12px;
        opacity: 1;
    }

    ${k}:hover {
        opacity: 1;
    }

    ${k}:active {
        cursor: grabbing;
    }
`);

const FeatureCardIslandClass = injectStyle("creator-feature-card", k => `
    ${k} {
        max-width: 944px;
        background: var(--backgroundCard);
        box-shadow: var(--defaultShadow);
        border: var(--defaultCardBorder);
        border-radius: 10px;
        padding: 20px;
        box-sizing: border-box;
    }
    @media (max-width: 600px) {
        ${k} {
            padding: 16px;
        }
    }
`);

const FeatureSectionWrapperClass = injectStyle("creator-feature-section", k => `
    ${k} {
        cursor: pointer;
        border-radius: 6px;
        transition: background-color 0.15s ease;
    }

    ${k} + ${k} {
        margin-top: 8px;
    }

    ${k}:hover {
        background: var(--rowHover, var(--backgroundCardHover));
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

const FeatureSectionBodyClass = injectStyle("creator-feature-section-body", k => `
    ${k} {
        pointer-events: none;
        min-width: 0;
    }
`);
