// Compact invocation editor card
// =====================================================================================================================
// The Invocation card sits after the Parameters card in the visual editor view. It contains a
// compact Monaco editor bound to the A2 `invocation` string with bash+Jinja syntax highlighting
// (the `bash-jinja` language), context-aware completion and hover (parameters, ucloud, functions,
// filters, tests), and client-side lint markers for syntax and reference errors.
//
// Invocation validation errors appear as inline markers in this editor (and in the page error
// summary for YAML/app-level errors). The compact editor inherits the user's editor settings
// (font size, vim, word wrap) from the same localStorage store as the file editor.
//
// The card is read-only when the source text is invalid, matching the visual read-only rule:
// while YAML is invalid, the visual editor (including the invocation field) must not accept
// changes because there is a single source of truth and it is the YAML text.

import * as React from "react";
import {useEffect, useLayoutEffect, useRef} from "react";
import {editor} from "monaco-editor";
import {classConcat, injectStyle} from "@/Unstyled";
import {Text} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {
    creatorEditorOptions,
    ensureBashJinjaLanguage,
    ensureUcloudDarkTheme,
} from "@/Applications/Creator/MonacoShared";
import {
    registerInvocationProviders,
    setInvocationModelParameters,
} from "@/Applications/Creator/InvocationProviders";
import {invocationLint} from "@/Applications/Creator/InvocationLinter";
import type {InvocationParameters} from "@/Applications/Creator/InvocationScope";
import {InvocationHelp} from "@/Applications/Creator/InvocationHelp";
import {useMonaco} from "@/Editor/Editor";

import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

// Fixed editor height in card mode. Tall enough for a typical invocation template without resizing.
const DEFAULT_HEIGHT = 500;
const INVOCATION_CARD_HEIGHT = DEFAULT_HEIGHT + 120;

export type InvocationTab = "invocation" | "preview" | "help";

export interface InvocationEditorProps {
    // The current invocation text from the draft.
    invocation: string;
    // True when the source text is invalid. The editor becomes read-only.
    readOnly: boolean;
    // The light/dark theme name.
    themeName?: string;
    // Called with the new invocation text on every content change.
    onChange: (text: string) => void;
    // The application parameters in declaration order. Drives completion, hover, and lint scope.
    parameters: InvocationParameters;
    // When true, the editor fills all available space while retaining the card styling.
    maximized: boolean;
    // Toggles maximized state.
    onToggleMaximized: () => void;
    // The active tab in the invocation card.
    activeTab: InvocationTab;
    // Called when the active tab changes.
    onTabChange: (tab: InvocationTab) => void;
    // The generated invocation preview or its empty state.
    preview: React.ReactNode;
}

export function InvocationEditor(props: InvocationEditorProps): React.ReactNode {
    const monaco = useMonaco(true);
    const containerRef = useRef<HTMLDivElement>(null);
    const editorRef = useRef<IStandaloneCodeEditor | null>(null);
    const modelRef = useRef<editor.ITextModel | null>(null);
    const lastEmittedRef = useRef<string>(props.invocation);
    const propsRef = useRef(props);
    propsRef.current = props;

    useLayoutEffect(() => {
        const m = monaco;
        const node = containerRef.current;
        if (!m || !node) return;
        monacoInstanceRef.current = m;

        ensureUcloudDarkTheme(m);
        ensureBashJinjaLanguage(m);
        registerInvocationProviders(m);

        node.innerHTML = "";
        const model = m.editor.createModel(props.invocation, "bash-jinja");
        model.setEOL(0 /* EndOfLineSequence.LF */);
        modelRef.current = model;
        setInvocationModelParameters(model, props.parameters);
        lintModel(model, props.parameters);

        const ed: IStandaloneCodeEditor = m.editor.create(node, {
            model,
            language: "bash-jinja",
            readOnly: props.readOnly,
            theme: props.themeName === "light" ? "light" : "ucloud-dark",
            ...creatorEditorOptions(),
            wordWrap: "on",
        });
        editorRef.current = ed;

        model.onDidChangeContent(() => {
            const value = model.getValue();
            if (value === lastEmittedRef.current) return;
            lastEmittedRef.current = value;
            propsRef.current.onChange(value);
            scheduleLint(model, propsRef.current.parameters);
        });

        return () => {
            const timer = lintTimers.get(model);
            if (timer != null) {
                window.clearTimeout(timer);
                lintTimers.delete(model);
            }
            ed.dispose();
            model.dispose();
            editorRef.current = null;
            modelRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [monaco]);

    // External text replacement (when the invocation changes from outside, e.g. a rename rewrote
    // references, or a parse replaced the model). We avoid clobbering the cursor when the model
    // already matches.
    useLayoutEffect(() => {
        const model = modelRef.current;
        if (!model) return;
        const current = model.getValue();
        if (current === props.invocation) {
            lastEmittedRef.current = props.invocation;
            return;
        }
        model.setValue(props.invocation);
        lastEmittedRef.current = props.invocation;
    }, [props.invocation]);

    useEffect(() => {
        const ed = editorRef.current;
        if (!ed) return;
        ed.updateOptions({readOnly: props.readOnly});
    }, [props.readOnly]);

    useEffect(() => {
        const m = monaco;
        if (!m) return;
        ensureUcloudDarkTheme(m);
        m.editor.setTheme(props.themeName === "light" ? "light" : "ucloud-dark");
    }, [monaco, props.themeName]);

    // Re-layout the editor when maximized toggles. Monaco's automaticLayout catches up, but an
    // explicit call avoids a one-frame delay during the size transition.
    useEffect(() => {
        const ed = editorRef.current;
        if (!ed) return;
        ed.layout();
    }, [props.maximized, props.activeTab]);

    // Refresh completion scope and lint markers when the parameters change (rename, add,
    // delete, type change in the parameter panel).
    useEffect(() => {
        const model = modelRef.current;
        if (!model) return;
        setInvocationModelParameters(model, props.parameters);
        lintModel(model, props.parameters);
    }, [props.parameters]);

    return (
        <TabbedCard
            id="creator-card-invocation"
            className={classConcat(InvocationCardClass, props.maximized ? InvocationMaximizedClass : undefined)}
            style={props.maximized ? {flex: "1 1 auto", minHeight: 0} : {height: `${INVOCATION_CARD_HEIGHT}px`}}
            activeIndex={props.activeTab === "preview" ? 1 : props.activeTab === "help" ? 2 : 0}
            onTabChange={idx => props.onTabChange(idx === 1 ? "preview" : idx === 2 ? "help" : "invocation")}
            rightControls={
                <IconButton
                    icon={props.maximized ? "heroArrowsPointingIn" : "heroArrowsPointingOut"}
                    tooltip={props.maximized ? "Minimize" : "Maximize"}
                    onClick={props.onToggleMaximized}
                />
            }
        >
            <TabbedCardTab name="Invocation" icon="heroCodeBracket">
                <div className={InvocationTabClass}>
                    {props.readOnly ? (
                        <Text fontSize={12} color="textSecondary" mb="8px">
                            Read-only while YAML source is invalid.
                        </Text>
                    ) : null}
                    <div
                        className={InvocationEditorHostClass}
                        ref={containerRef}
                        style={{flex: "1 1 auto", minHeight: 0}}
                    />
                </div>
            </TabbedCardTab>
            <TabbedCardTab name="Preview" icon="heroEye">
                {props.preview}
            </TabbedCardTab>
            <TabbedCardTab name="Help" icon="heroQuestionMarkCircle">
                <InvocationHelp />
            </TabbedCardTab>
        </TabbedCard>
    );
}

// Linting
// -------------------------------------------------------------------------------------------------------------------

// Marker owner key for invocation lint markers.
const LINT_OWNER = "creator-invocation-lint";

// The Monaco instance, set on editor mount. Module-level because the lint helpers are plain
// functions called from effects and timeouts.
const monacoInstanceRef: {current: typeof import("monaco-editor") | null} = {current: null};

// Debounce timers per model. 50ms coalesces bursts from a single keystroke (Monaco can fire
// multiple content changes per key) while keeping the marker feedback near-instant.
const lintTimers = new WeakMap<editor.ITextModel, number>();

function lintModel(model: editor.ITextModel, parameters: InvocationParameters): void {
    const m = monacoInstanceRef.current;
    if (!m) return;
    const markers = invocationLint(model.getValue(), parameters);
    m.editor.setModelMarkers(
        model,
        LINT_OWNER,
        markers.map(marker => ({
            message: marker.message,
            severity: marker.severity === "error" ? m.MarkerSeverity.Error : m.MarkerSeverity.Warning,
            startLineNumber: model.getPositionAt(marker.start).lineNumber,
            startColumn: model.getPositionAt(marker.start).column,
            endLineNumber: model.getPositionAt(marker.end).lineNumber,
            endColumn: model.getPositionAt(marker.end).column,
        })),
    );
}

function scheduleLint(model: editor.ITextModel, parameters: InvocationParameters): void {
    const existing = lintTimers.get(model);
    if (existing) window.clearTimeout(existing);
    const timer = window.setTimeout(() => {
        lintTimers.delete(model);
        lintModel(model, parameters);
    }, 50);
    lintTimers.set(model, timer);
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

// The invocation editor host matches the YAML editor host: a bordered rounded container.
const InvocationEditorHostClass = injectStyle("creator-invocation-editor-host", k => `
    ${k} {
        width: 100%;
        border: 1px solid var(--borderColor);
        border-radius: 6px;
        overflow: hidden;
    }
`);

// The invocation card keeps the same card treatment in both tabs. Maximized mode removes the compact
// card width limit and lets the editor fill the creator content island.
const InvocationCardClass = injectStyle("creator-invocation-card", k => `
    ${k} {
        max-width: 944px;
        display: flex;
        flex-direction: column;
        min-height: 0;
    }

    ${k} > div {
        display: flex;
        flex-direction: column;
        min-height: 0;
        flex: 1 1 auto;
    }

    ${k} > div > [data-tab-name] {
        min-height: 0;
        flex: 1 1 auto;
    }
`);

const InvocationMaximizedClass = injectStyle("creator-invocation-maximized", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        min-height: 0;
        flex: 1 1 auto;
        height: 100%;
        width: 100%;
        max-width: none;
    }
`);

const InvocationTabClass = injectStyle("creator-invocation-tab", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        min-height: 0;
        height: 100%;
    }
`);
