// Compact invocation editor card
// =====================================================================================================================
// The Invocation card sits after the Parameters card in the visual editor view. It contains a
// compact Monaco editor bound to the A2 `invocation` string with Jinja syntax highlighting.
//
// The first version enables the existing Jinja syntax highlighting. It does not include variable
// auto-completion or inline documentation; those are a later phase.
//
// Invocation validation errors appear in the page error summary rather than as inline markers in
// this editor. The compact editor inherits the user's editor settings (font size, vim, word wrap)
// from the same localStorage store as the file editor.
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
    ensureJinja2Language,
    ensureUcloudDarkTheme,
} from "@/Applications/Creator/MonacoShared";
import {useMonaco} from "@/Editor/Editor";

import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;

// Fixed editor height in card mode. Tall enough for a typical invocation template without resizing.
const DEFAULT_HEIGHT = 500;
const INVOCATION_CARD_HEIGHT = DEFAULT_HEIGHT + 120;

export type InvocationTab = "invocation" | "preview";

export interface InvocationEditorProps {
    // The current invocation text from the draft.
    invocation: string;
    // True when the source text is invalid. The editor becomes read-only.
    readOnly: boolean;
    // The light/dark theme name.
    themeName?: string;
    // Called with the new invocation text on every content change.
    onChange: (text: string) => void;
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

        ensureUcloudDarkTheme(m);
        ensureJinja2Language(m);

        node.innerHTML = "";
        const model = m.editor.createModel(props.invocation, "jinja2");
        model.setEOL(0 /* EndOfLineSequence.LF */);
        modelRef.current = model;

        const ed: IStandaloneCodeEditor = m.editor.create(node, {
            model,
            language: "jinja2",
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
        });

        return () => {
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

    return (
        <TabbedCard
            id="creator-card-invocation"
            className={classConcat(InvocationCardClass, props.maximized ? InvocationMaximizedClass : undefined)}
            style={props.maximized ? {flex: "1 1 auto", minHeight: 0} : {height: `${INVOCATION_CARD_HEIGHT}px`}}
            activeIndex={props.activeTab === "preview" ? 1 : 0}
            onTabChange={idx => props.onTabChange(idx === 1 ? "preview" : "invocation")}
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
        </TabbedCard>
    );
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

// The invocation card keeps the same card treatment in both tabs. Maximized mode only changes the
// card's flex behavior so the editor can fill the creator content island.
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
