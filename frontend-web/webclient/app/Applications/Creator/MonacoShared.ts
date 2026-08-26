// Shared Monaco setup for the creator editors
// =====================================================================================================================
// The creator has two embedded Monaco editors: the full YAML source view and the compact Jinja
// invocation editor. Both need the same theme, the same Jinja2 language registration, and the
// same user editor settings (font size, word wrap, vim) as the file editor. The file editor
// (`Editor/Editor.tsx`) already defines the `ucloud-dark` theme and registers the `jinja2`
// language, but it does so inline on its own mount. The creator editors must not assume the file
// editor is mounted, and must not register the language twice.
//
// This module owns three small guarantees:
//
// - `ensureJinja2Language(monaco)` registers the jinja2 language and its Monarch tokenizer at most
//   once per Monaco instance. The tokenizer is the same one the file editor uses; we export it
//   from Editor.tsx so the visual experience is identical.
// - `ensureUcloudDarkTheme(monaco)` defines the theme at most once per Monaco instance.
// - `creatorEditorOptions()` reads the same localStorage settings the file editor uses so the
//   embedded editors inherit the user's font size, weight, word wrap, and vim preference.
//
// The file editor's localStorage key is `PreviewEditorSettings`. We read it directly so the
// creator does not import the file editor's private helpers.

// Tokens
// -------------------------------------------------------------------------------------------------------------------

// The Monarch tokenizer for Jinja2 is defined in Editor.tsx and exported as
// `jinja2monarchTokens`. We import it so the creator reuses the exact same tokenizer the file
// editor uses, instead of redefining it.

import {jinja2monarchTokens} from "@/Editor/Editor";

// Language and theme registration guards
// -------------------------------------------------------------------------------------------------------------------
// Monaco does not expose a "is this language/theme defined" call. We use module-level flags so
// each is defined at most once per page load. The flags are safe across HMR reloads because the
// module is re-evaluated.

let jinja2Registered = false;
let themeDefined = false;

// Register the jinja2 language with Monaco at most once. Safe to call from every editor mount.
export function ensureJinja2Language(monaco: any): void {
    if (jinja2Registered) return;
    jinja2Registered = true;
    // `register` is idempotent in Monaco if the id already exists, but we skip the call to avoid
    // redefining the tokenizer and triggering a change event.
    if (monaco.languages.getLanguages().some((l: any) => l.id === "jinja2")) return;
    monaco.languages.register({id: "jinja2"});
    monaco.languages.setMonarchTokensProvider("jinja2", jinja2monarchTokens);
}

// Define the ucloud-dark theme at most once. The theme matches the file editor's definition.
export function ensureUcloudDarkTheme(monaco: any): void {
    if (themeDefined) return;
    themeDefined = true;
    monaco.editor.defineTheme("ucloud-dark", {
        base: "vs-dark",
        inherit: true,
        rules: [],
        colors: {
            "editor.background": "#21262D",
        },
    });
}

// User editor settings
// -------------------------------------------------------------------------------------------------------------------
// The embedded editors inherit the file editor's stored preferences. We read the same
// localStorage key so changing the font size in the file editor changes it here too. Returns the
// settings applicable to the editor.create options.

interface CreatorStoredSettings {
    fontSize?: number;
    fontWeight?: string;
    wordWrap?: string;
    vim?: boolean;
}

const CREATOR_EDITOR_SETTINGS_KEY = "PreviewEditorSettings";

function readStoredSettings(): CreatorStoredSettings {
    try {
        return JSON.parse(localStorage.getItem(CREATOR_EDITOR_SETTINGS_KEY) ?? "{}");
    } catch {
        return {};
    }
}

const EDITOR_TOP_PADDING = 15;

export function creatorEditorOptions(): {
    fontFamily: string;
    fontSize: number;
    fontWeight?: string;
    wordWrap: "off" | "on" | "wordWrapColumn" | "bounded";
    minimap: {enabled: boolean};
    renderLineHighlight: "none" | "all" | "gutter" | "line";
    scrollBeyondLastLine: boolean;
    automaticLayout: boolean;
    padding: {top: number; bottom: number};
} {
    const s = readStoredSettings();
    return {
        fontFamily: "Jetbrains Mono",
        fontSize: s.fontSize ?? 14,
        fontWeight: s.fontWeight,
        wordWrap: (s.wordWrap as "off" | "on" | "wordWrapColumn" | "bounded") ?? "off",
        minimap: {enabled: false},
        renderLineHighlight: "none",
        scrollBeyondLastLine: false,
        automaticLayout: true,
        padding: {top: EDITOR_TOP_PADDING, bottom: EDITOR_TOP_PADDING},
    };
}

// The creator editors use `useMonaco` from Editor.tsx directly; this module only owns the
// shared registration guards and editor settings, so it does not import `getMonaco`.
//
// Vim mode is intentionally deferred. Enabling it requires a vim command bar element and the
// monaco-vim initVimMode wiring, which is invasive for an embedded compact editor. The font
// size, font weight, and word wrap settings are inherited; the vim toggle is a later phase.
