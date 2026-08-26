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
import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {editor} from "monaco-editor";
import {injectStyle} from "@/Unstyled";
import {Text} from "@/ui-components";
import {
    creatorEditorOptions,
    ensureJinja2Language,
    ensureUcloudDarkTheme,
} from "@/Applications/Creator/MonacoShared";
import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";
import {useMonaco} from "@/Editor/Editor";

import IStandaloneCodeEditor = editor.IStandaloneCodeEditor;
import IMarkerData = editor.IMarkerData;

// Parse debounce interval. A short stable edit period prevents parsing on every keystroke while
// keeping the visual state responsive. The root design calls this a "short stable edit interval".
const PARSE_DEBOUNCE_MS = 600;

export interface YamlEditorProps {
    // The current source text. The editor model is initialized from this and updated when the
    // parent replaces the text (for example after a visual change serializes canonical YAML).
    sourceText: string;
    // True when the last parse failed. The editor is editable either way; this controls the
    // banner shown above it and is used to color the error list.
    sourceTextInvalid: boolean;
    // Parse errors from the last parse, with line/column. Placed as Monaco markers.
    parseErrors: CreatorSourceParseError[];
    // A parameter key the editor should scroll to and highlight. The Workflow rows set this when
    // the user clicks "Open in YAML". Cleared by the editor after the focus is applied.
    yamlFocusKey: string | null;
    // A line number the editor should scroll to and reveal. The error summary sets this when
    // the user clicks a parse error. Cleared by the editor after the focus is applied.
    focusLine: number | null;
    // The column for the focusLine jump. 1-based.
    focusColumn: number;
    // True when the editor should be read-only. Set by the parent when the visual editor owns the
    // edits (for example, the source is invalid and the user is in the editor view). The YAML
    // editor is the only editable surface in the YAML view, so this is generally false.
    readOnly?: boolean;
    // The light/dark theme name. The editor uses the ucloud-dark theme when this is not "light".
    themeName?: string;
    // Called on every content change with the new text. The parent stores this in the draft and
    // schedules the delayed parse. The editor does not parse itself.
    onChange: (text: string) => void;
    // Called when the user leaves the editor (blur). The parent runs the parse immediately.
    onBlur: () => void;
    // Called when the stable-edit debounce fires. The parent runs the delayed parse.
    onParseTick: () => void;
    // Called after the editor applies a `yamlFocusKey` jump so the parent can clear it.
    onFocusApplied: () => void;
    // Called after the editor applies a `focusLine` jump so the parent can clear it.
    onLineFocusApplied: () => void;
}

export function YamlEditor(props: YamlEditorProps): React.ReactNode {
    const monaco = useMonaco(true);
    const containerRef = useRef<HTMLDivElement>(null);
    const editorRef = useRef<IStandaloneCodeEditor | null>(null);
    const modelRef = useRef<editor.ITextModel | null>(null);
    // Tracks whether the incoming text prop change is an external replace (visual edit
    // serialized canonical YAML) or our own keystroke. We only call setValue when the text
    // changed from outside, to avoid clobbering the cursor on every keystroke.
    const lastEmittedRef = useRef<string>(props.sourceText);
    // Latest props in a ref so the Monaco content listeners installed at mount read the current
    // callbacks without re-subscribing. The parent (Create.tsx) uses useCallback for the handlers
    // so identity is stable across renders, but the ref makes the dependency explicit.
    const propsRef = useRef(props);
    propsRef.current = props;

    const [bannerText, setBannerText] = useState<string | null>(null);

    // Layout effect: create the editor and model once Monaco is available.
    useLayoutEffect(() => {
        const m = monaco;
        const node = containerRef.current;
        if (!m || !node) return;

        ensureUcloudDarkTheme(m);
        ensureJinja2Language(m);

        node.innerHTML = "";
        const model = m.editor.createModel(props.sourceText, "yaml");
        model.setEOL(0 /* EndOfLineSequence.LF */);
        modelRef.current = model;

        const ed: IStandaloneCodeEditor = m.editor.create(node, {
            model,
            language: "yaml",
            readOnly: props.readOnly ?? false,
            theme: props.themeName === "light" ? "light" : "ucloud-dark",
            ...creatorEditorOptions(),
        });
        editorRef.current = ed;

        // Report content changes to the parent and arm the debounce. We compare to the last
        // emitted value so a programmatic setValue does not trigger a parent update cycle. The
        // handlers are read from propsRef so the listener installed at mount keeps using the
        // latest callbacks without re-subscribing.
        model.onDidChangeContent(() => {
            const value = model.getValue();
            if (value === lastEmittedRef.current) return;
            lastEmittedRef.current = value;
            propsRef.current.onChange(value);
        });

        // Parse immediately on blur.
        ed.onDidBlurEditorText(() => {
            propsRef.current.onBlur();
        });

        return () => {
            ed.dispose();
            model.dispose();
            editorRef.current = null;
            modelRef.current = null;
        };
        // We intentionally do not depend on props.callbacks so the editor is not recreated on
        // every parent render. The callbacks are read from propsRef.
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [monaco]);

    // External text replacement: when the parent replaces sourceText from outside (visual edit
    // serialized canonical YAML, or a load), update the model without clobbering the cursor if
    // the value is already current.
    useLayoutEffect(() => {
        const model = modelRef.current;
        if (!model) return;
        const current = model.getValue();
        if (current === props.sourceText) {
            lastEmittedRef.current = props.sourceText;
            return;
        }
        // Replace the whole document. This resets undo history to a single step, which is what we
        // want: the user can undo the whole visual-change normalization as one step.
        model.setValue(props.sourceText);
        lastEmittedRef.current = props.sourceText;
    }, [props.sourceText]);

    // Update readOnly when the prop changes.
    useEffect(() => {
        const ed = editorRef.current;
        if (!ed) return;
        ed.updateOptions({readOnly: props.readOnly ?? false});
    }, [props.readOnly]);

    // Update theme when it changes.
    useEffect(() => {
        const m = monaco;
        if (!m) return;
        ensureUcloudDarkTheme(m);
        m.editor.setTheme(props.themeName === "light" ? "light" : "ucloud-dark");
    }, [monaco, props.themeName]);

    // Place parse errors as Monaco markers on the model and keep the banner in sync.
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
            severity: 8 /* MarkerSeverity.Error */,
            source: "creator-yaml",
        }));
        m.editor.setModelMarkers(model, "creator-yaml", markers);

        if (props.sourceTextInvalid) {
            setBannerText(props.parseErrors.length > 0
                ? `${props.parseErrors.length} parse error${props.parseErrors.length === 1 ? "" : "s"}. Fix the YAML to re-enable visual editing.`
                : "The source text is invalid. Fix the YAML to re-enable visual editing.");
        } else {
            setBannerText(null);
        }
    }, [monaco, props.parseErrors, props.sourceTextInvalid]);

    // Debounced parse. We start/restart a timer whenever the source text changes (the parent
    // updates props.sourceText synchronously on each Monaco content change). When the timer
    // fires, we tell the parent to run the delayed parse. The parent clears stale errors and
    // replaces them with the new parse result on the next tick.
    useEffect(() => {
        const handle = window.setTimeout(() => {
            propsRef.current.onParseTick();
        }, PARSE_DEBOUNCE_MS);
        return () => window.clearTimeout(handle);
    }, [props.sourceText]);

    // Apply a pending yamlFocusKey by searching the model for the key and revealing it.
    useEffect(() => {
        const ed = editorRef.current;
        const model = modelRef.current;
        if (!ed || !model || !props.yamlFocusKey) return;

        // Search for `key:` at the start of a line. We use a word-boundary regex via the model
        // matches API so we land on the parameter declaration, not a substring match elsewhere.
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

    // Apply a pending focusLine by scrolling the editor to that line and placing the cursor
    // there. The error summary uses this so the user can jump from an error to its source line.
    useEffect(() => {
        const ed = editorRef.current;
        if (!ed || props.focusLine == null || props.focusLine <= 0) return;
        const line = props.focusLine;
        const column = Math.max(1, props.focusColumn || 1);
        ed.revealLineInCenter(line);
        ed.setPosition({lineNumber: line, column});
        // Add a brief selection highlight so the user sees the target line.
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

    return (
        <>
            {bannerText ? (
                <div className={YamlBannerClass} role="status">
                    <Text fontSize={13} color="errorMain">{bannerText}</Text>
                </div>
            ) : null}
            <div className={YamlEditorHostClass} ref={containerRef} />
        </>
    );
}

function escapeRegExp(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

// Styling
// -------------------------------------------------------------------------------------------------------------------

// The YAML editor host fills all remaining vertical space in the main content island. It has no
// card wrapper, title, or border: the editor itself is the content.
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

const YamlBannerClass = injectStyle("creator-yaml-banner", k => `
    ${k} {
        flex-shrink: 0;
        padding: 8px 12px;
        margin-bottom: 0;
        background: color-mix(in srgb, var(--errorMain) 12%, transparent);
        border: 1px solid color-mix(in srgb, var(--errorMain) 35%, var(--borderColor));
        border-radius: 6px;
    }
`);
