// YAML source editor card
// =====================================================================================================================
// The YAML view is an editable Monaco editor bound to the canonical source text. It is one side
// of the two-way synchronization with the visual editor.
//
// Synchronization rules (from the root design):
//
// - Do not parse on every keystroke. The editor debounces a parse on a short stable interval. It
//   also parses immediately when the editor loses focus or when preview/save is requested (the
//   parent calls runParse).
// - When parsing fails, the exact source text is retained. The visual editor keeps showing the
//   last valid model. The parent marks the draft `sourceTextInvalid` and disables visual editing,
//   preview, and save.
// - When parsing succeeds, the parent replaces the structured model with the parsed result and
//   clears parse errors.
//
// The editor receives the source text and parse errors as props and reports changes back. The
// parse cycle itself runs in the parent (Create.tsx) so the parent owns the draft state and the
// visual read-only decision. This keeps a single owner for the parse result and the draft.
//
// Error markers are placed on the Monaco model so they appear in the editor gutter and squiggle
// under the offending text. The same errors also appear in the page error summary (ErrorSummary).
//
// The Workflow rows can ask the editor to jump to a parameter key. The parent sets the draft
// `yamlFocusKey`; this editor reacts to it by searching the model for the key and scrolling to it.

import * as React from "react";
import {useEffect, useLayoutEffect, useRef} from "react";
import {editor} from "monaco-editor";
import {injectStyle} from "@/Unstyled";
import {
    creatorEditorOptions,
    ensureJinja2Language,
    ensureUcloudDarkTheme,
} from "@/Applications/Creator/MonacoShared";
import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";
import {creatorRegisterCodeEditorFocus} from "@/Applications/Creator/CreatorKeyboard";
import {useMonaco} from "@/Editor/Editor";

import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import IMarkerData = editor.IMarkerData;

const PARSE_DEBOUNCE_MS = 600;

export interface YamlEditorProps {
    sourceText: string;
    sourceTextInvalid: boolean;
    parseErrors: CreatorSourceParseError[];
    yamlFocusKey: string | null;
    focusLine: number | null;
    focusColumn: number;
    readOnly?: boolean;
    themeName?: string;
    onChange: (text: string) => void;
    onBlur: () => void;
    onParseTick: () => void;
    onFocusApplied: () => void;
    onLineFocusApplied: () => void;
}

export function YamlEditor(props: YamlEditorProps): React.ReactNode {
    const monaco = useMonaco(true);
    const containerRef = useRef<HTMLDivElement>(null);
    const editorRef = useRef<IStandaloneCodeEditor | null>(null);
    const modelRef = useRef<editor.ITextModel | null>(null);
    const lastEmittedRef = useRef<string>(props.sourceText);
    const propsRef = useRef(props);
    propsRef.current = props;

    useEffect(() => creatorRegisterCodeEditorFocus(() => {
        editorRef.current?.focus();
    }), []);

    useLayoutEffect(() => {
        const m = monaco;
        const node = containerRef.current;
        if (!m || !node) return;

        ensureUcloudDarkTheme(m);
        ensureJinja2Language(m);

        node.innerHTML = "";
        const model = m.editor.createModel(props.sourceText, "yaml");
        model.setEOL(0);
        modelRef.current = model;

        const ed: IStandaloneCodeEditor = m.editor.create(node, {
            model,
            language: "yaml",
            readOnly: props.readOnly ?? false,
            theme: props.themeName === "light" ? "light" : "ucloud-dark",
            ...creatorEditorOptions(),
        });
        editorRef.current = ed;
        ed.focus();

        model.onDidChangeContent(() => {
            const value = model.getValue();
            if (value === lastEmittedRef.current) return;
            lastEmittedRef.current = value;
            propsRef.current.onChange(value);
        });

        ed.onDidBlurEditorText(() => {
            propsRef.current.onBlur();
        });

        return () => {
            ed.dispose();
            model.dispose();
            editorRef.current = null;
            modelRef.current = null;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [monaco]);

    useLayoutEffect(() => {
        const model = modelRef.current;
        if (!model) return;
        const current = model.getValue();
        if (current === props.sourceText) {
            lastEmittedRef.current = props.sourceText;
            return;
        }
        model.setValue(props.sourceText);
        lastEmittedRef.current = props.sourceText;
    }, [props.sourceText]);

    useEffect(() => {
        const ed = editorRef.current;
        if (!ed) return;
        ed.updateOptions({readOnly: props.readOnly ?? false});
    }, [props.readOnly]);

    useEffect(() => {
        const m = monaco;
        if (!m) return;
        ensureUcloudDarkTheme(m);
        m.editor.setTheme(props.themeName === "light" ? "light" : "ucloud-dark");
    }, [monaco, props.themeName]);

    useEffect(() => {
        const m = monaco;
        const model = modelRef.current;
        if (!m || !model) return;
        const markers: IMarkerData[] = (props.parseErrors ?? []).map(e => ({
            startLineNumber: Math.max(1, e.line),
            startColumn: Math.max(1, e.column),
            endLineNumber: Math.max(1, e.line),
            endColumn: Math.max(1, e.column + 1),
            message: e.message,
            severity: 8,
            source: "creator-yaml",
        }));
        m.editor.setModelMarkers(model, "creator-yaml", markers);

    }, [monaco, props.parseErrors, props.sourceTextInvalid]);

    useEffect(() => {
        const handle = window.setTimeout(() => {
            propsRef.current.onParseTick();
        }, PARSE_DEBOUNCE_MS);
        return () => window.clearTimeout(handle);
    }, [props.sourceText]);

    useEffect(() => {
        const ed = editorRef.current;
        const model = modelRef.current;
        if (!ed || !model || !props.yamlFocusKey) return;

        const key = props.yamlFocusKey;
        const matches = model.findMatches(
            `\\b${escapeRegExp(key)}:`,
            false, // searchOnlyEditableRange
            true,  // isRegex
            false, // matchCase
            null, // wordSeparators
            true,  // captureMatches
        );
        if (matches.length > 0) {
            const target = matches[0].range;
            ed.revealRangeInCenter(target);
            ed.setPosition({lineNumber: target.startLineNumber, column: target.startColumn});
            ed.focus();
        }
        propsRef.current.onFocusApplied();
    }, [props.yamlFocusKey]);

    useEffect(() => {
        const ed = editorRef.current;
        if (!ed || props.focusLine == null || props.focusLine <= 0) return;
        const line = props.focusLine;
        const column = Math.max(1, props.focusColumn || 1);
        ed.revealLineInCenter(line);
        ed.setPosition({lineNumber: line, column});
        const model = modelRef.current;
        if (model) {
            const endColumn = model.getLineContent(line).length + 1;
            ed.setSelection({
                startLineNumber: line,
                startColumn: column,
                endLineNumber: line,
                endColumn: Math.max(column + 1, endColumn),
            });
        }
        ed.focus();
        propsRef.current.onLineFocusApplied();
    }, [props.focusLine, props.focusColumn]);

    return <div className={YamlEditorHostClass} ref={containerRef} />;
}

function escapeRegExp(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

const YamlEditorHostClass = injectStyle("creator-yaml-editor-host", k => `
    ${k} {
        flex: 1 1 auto;
        min-height: 0;
        width: 100%;
        border-radius: 6px;
        overflow: hidden;
        border: 1px solid var(--borderColor);
    }
`);
