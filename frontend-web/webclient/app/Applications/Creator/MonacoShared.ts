// Shared Monaco setup for the creator editors
// =====================================================================================================================
// The creator has two embedded Monaco editors: the full YAML source view and the compact Jinja
// invocation editor. Both need the same theme, the same Jinja2 language registration, and the
// same user editor settings (font size, word wrap, vim) as the file editor. The file editor
// (`Editor/Editor.tsx`) already defines the `ucloud-dark` theme and registers the `jinja2`
// language, but it does so inline on its own mount. The creator editors must not assume the file
// editor is mounted, and must not register the language twice.
//
// This module owns these guarantees:
//
// - `ensureJinja2Language(monaco)` registers the jinja2 language and its Monarch tokenizer at most
//   once per Monaco instance. The tokenizer is the same one the file editor uses; we export it
//   from Editor.tsx so the visual experience is identical.
// - `ensureBashJinjaLanguage(monaco)` registers the bash-jinja language at most once per Monaco
//   instance.
// - `ensureUcloudDarkTheme(monaco)` defines the theme, re-applying it on each call. The file
//   editor defines the same theme name with empty rules, so re-applying keeps these token colors
//   alive after a file editor mount.
// - `creatorEditorOptions()` reads the same localStorage settings the file editor uses so the
//   embedded editors inherit the user's font size, weight, word wrap, and vim preference.
//
// The file editor's localStorage key is `PreviewEditorSettings`. We read it directly so the
// creator does not import the file editor's private helpers.

// Tokens
// -------------------------------------------------------------------------------------------------------------------

import {
    bashJinjaLanguageConfiguration,
    bashJinjaLanguageId,
    bashJinjaMonarchTokens,
} from "@/Applications/Creator/InvocationLanguage";
import {jinja2monarchTokens} from "@/Editor/Editor";

// Language and theme registration guards
// -------------------------------------------------------------------------------------------------------------------
// Monaco does not expose a "is this language/theme defined" call. We use module-level flags so
// each is defined at most once per page load. The flags are safe across HMR reloads because the
// module is re-evaluated.

let jinja2Registered = false;
let bashJinjaRegistered = false;

export function ensureJinja2Language(monaco: any): void {
    if (jinja2Registered) return;
    jinja2Registered = true;
    if (monaco.languages.getLanguages().some((l: any) => l.id === "jinja2")) return;
    monaco.languages.register({id: "jinja2"});
    monaco.languages.setMonarchTokensProvider("jinja2", jinja2monarchTokens);
}

export function ensureBashJinjaLanguage(monaco: any): void {
    if (bashJinjaRegistered) return;
    bashJinjaRegistered = true;
    if (monaco.languages.getLanguages().some((l: any) => l.id === bashJinjaLanguageId)) return;
    monaco.languages.register({id: bashJinjaLanguageId});
    monaco.languages.setMonarchTokensProvider(bashJinjaLanguageId, bashJinjaMonarchTokens as any);
    monaco.languages.setLanguageConfiguration(bashJinjaLanguageId, bashJinjaLanguageConfiguration);
}

export function ensureUcloudDarkTheme(monaco: any): void {
    monaco.editor.defineTheme("ucloud-dark", {
        base: "vs-dark",
        inherit: true,
        rules: [
            {token: "delimiter.bracket.jinja2", foreground: "F97316"},
            {token: "keyword.jinja2", foreground: "F97316"},
            {token: "comment.jinja2", foreground: "6A9955", fontStyle: "italic"},
            {token: "variable.name.jinja2", foreground: "9CDCFE"},
            {token: "string.jinja2", foreground: "CE9178"},
            {token: "number.jinja2", foreground: "B5CEA8"},
            {token: "number.float.jinja2", foreground: "B5CEA8"},
            {token: "operator.jinja2", foreground: "D4D4D4"},
        ],
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
