// Monaco integration for the invocation editor
// =====================================================================================================================
// Everything the invocation editor needs from Monaco in one module:
//
// - The `bash-jinja` language definition (Monarch tokenizer + language configuration). The
//   tokenizer embeds a copy of Monaco's shell tokenizer (monaco-editor 0.56.0,
//   esm/vs/languages/definitions/shell) and prepends Jinja tag rules. Gonja (the engine that
//   renders invocations) uses `{{ }}`, `{% %}`, `{# #}` with optional whitespace control (`{{-`,
//   `{%-`, `-}}`, `-%}`, `-#}`, `{%+`). The shell rules are an embedded copy because Monaco does
//   not export them for composition. Keep in sync with monaco-editor when upgrading. The Jinja
//   rules on top take precedence over the shell rules; a `{` that is not part of a Jinja
//   delimiter still tokenizes as bash. The creator owns this language (`bash-jinja`); the file
//   editor keeps its separate `jinja2` language untouched.
// - Theme and `jinja2` language registration guards shared with the YAML editor.
// - The completion and hover providers for `bash-jinja`, reading the draft parameters from a
//   per-model store so parameter edits update completions without re-registering anything.
// - The stored user editor settings (font size, word wrap) shared with the file editor.
//
// Registration guards: Monaco does not expose a "is this language/provider defined" call. We use
// module-level flags so each is defined at most once per page load. The flags are safe across
// HMR reloads because the module is re-evaluated.

import type * as Monaco from "monaco-editor";
import {
    invocationFilters,
    invocationStatementTags,
    invocationTests,
    type InvocationSymbolDoc,
} from "@/Applications/Creator/InvocationCatalog";
import {
    invocationFindTags,
    invocationResolveMembers,
    invocationScopeAt,
    tokenizeExpression,
    type InvocationParameters,
    type InvocationScopeEntry,
} from "@/Applications/Creator/InvocationScope";
import {jinja2monarchTokens} from "@/Editor/Editor";

// Language definition
// -------------------------------------------------------------------------------------------------------------------

export const bashJinjaLanguageId = "bash-jinja";

const shellKeywords = [
    "if", "then", "do", "else", "elif", "while", "until", "for", "in", "esac", "fi", "fin", "fil",
    "done", "exit", "set", "unset", "export", "function",
];

const shellBuiltins = [
    "ab", "awk", "bash", "beep", "cat", "cc", "cd", "chown", "chmod", "chroot", "clear", "cp",
    "curl", "cut", "diff", "echo", "find", "gawk", "gcc", "get", "git", "grep", "hg", "kill",
    "killall", "ln", "ls", "make", "mkdir", "openssl", "mv", "nc", "node", "npm", "ping", "ps",
    "restart", "rm", "rmdir", "sed", "service", "sh", "shopt", "shred", "source", "sort", "sleep",
    "ssh", "start", "stop", "su", "sudo", "svn", "tee", "telnet", "top", "touch", "vi", "vim",
    "wall", "wc", "wget", "who", "write", "yes", "zsh",
];

const jinjaStatementKeywords = [
    "for", "endfor", "if", "elif", "else", "endif", "set", "macro", "endmacro", "filter",
    "endfilter", "autoescape", "endautoescape", "with", "endwith", "raw", "endraw",
];

const jinjaExpressionKeywords = [
    "true", "True", "false", "False", "none", "None", "and", "or", "not", "in", "is", "recursive",
];

export const bashJinjaMonarchTokens = {
    defaultToken: "",
    ignoreCase: false,
    tokenPostfix: "",
    startingWithDash: /-+\w+/,
    identifiersWithDashes: /[a-zA-Z]\w+(?:@startingWithDash)+/,
    symbols: /[=><!~?&|+\-*\/\^;\.,]+/,

    keywords: shellKeywords,
    builtins: shellBuiltins,
    jinjaStatementKeywords,
    jinjaExpressionKeywords,

    tokenizer: {
        root: [
            [/\{\{/, "delimiter.bracket.jinja2", "@jinjaVariable"],
            [/\{%[+-]?/, "delimiter.bracket.jinja2", "@jinjaStatement"],
            [/\{#[+-]?/, "comment.jinja2", "@jinjaComment"],

            [/@identifiersWithDashes/, ""],
            [/(\s)((?:@startingWithDash)+)/, ["white", "attribute.name"]],
            [
                /[a-zA-Z]\w*/,
                {
                    cases: {
                        "@keywords": "keyword",
                        "@builtins": "type.identifier",
                        "@default": "",
                    },
                },
            ],
            {include: "@whitespace"},
            {include: "@strings"},
            {include: "@parameters"},
            {include: "@heredoc"},
            [/[{}\[\]()]/, "@brackets"],
            [/@symbols/, "delimiter"],
            {include: "@numbers"},
            [/[,;]/, "delimiter"],
        ],

        whitespace: [
            [/\s+/, "white"],
            [/(^#!.*$)/, "metatag"],
            [/(^#.*$)/, "comment"],
        ],

        numbers: [
            [/\d*\.\d+([eE][-+]?\d+)?/, "number.float"],
            [/0[xX][0-9a-fA-F_]*[0-9a-fA-F]/, "number.hex"],
            [/\d+/, "number"],
        ],

        strings: [
            [/'/, "string", "@stringBody"],
            [/"/, "string", "@dblStringBody"],
        ],

        stringBody: [
            [/'/, "string", "@popall"],
            [/./, "string"],
        ],

        dblStringBody: [
            [/"/, "string", "@popall"],
            [/./, "string"],
        ],

        heredoc: [
            [
                /(<<[-<]?)(\s*)(['"`]?)([\w\-]+)(['"`]?)/,
                [
                    "constants",
                    "white",
                    "string.heredoc.delimiter",
                    "string.heredoc",
                    "string.heredoc.delimiter",
                ],
            ],
        ],

        parameters: [
            [/\$\d+/, "variable.predefined"],
            [/\$\w+/, "variable"],
            [/\$[*@#?\-$!0_]/, "variable"],
            [/\$'/, "variable", "@parameterBodyQuote"],
            [/\$"/, "variable", "@parameterBodyDoubleQuote"],
            [/\$\(/, "variable", "@parameterBodyParen"],
            [/\$\{/, "variable", "@parameterBodyCurlyBrace"],
        ],

        parameterBodyQuote: [
            [/[^#:%*@\-!_']+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/'/, "variable", "@pop"],
        ],

        parameterBodyDoubleQuote: [
            [/[^#:%*@\-!_"]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/["]/, "variable", "@pop"],
        ],

        parameterBodyParen: [
            [/[^#:%*@\-!_)]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/[)]/, "variable", "@pop"],
        ],

        parameterBodyCurlyBrace: [
            [/[^#:%*@\-!_}]+/, "variable"],
            [/[#:%*@\-!_]/, "delimiter"],
            [/[}]/, "variable", "@pop"],
        ],

        jinjaVariable: [
            [/[-+]?}}/, "delimiter.bracket.jinja2", "@popall"],
            {include: "@jinjaExpression"},
        ],

        jinjaStatement: [
            [/[-+]?%\}/, "delimiter.bracket.jinja2", "@popall"],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, {
                cases: {
                    "@jinjaStatementKeywords": "keyword.jinja2",
                    "@default": {token: "variable.name.jinja2", next: "@jinjaExpressionTail"},
                },
            }],
            {include: "@jinjaExpression"},
        ],

        jinjaComment: [
            [/[-+]?#\}/, "comment.jinja2", "@popall"],
            [/[\s\S]/, "comment.jinja2"],
        ],

        jinjaExpression: [
            [/"([^"\\]|\\.)*"/, "string.jinja2"],
            [/'([^'\\]|\\.)*'/, "string.jinja2"],
            [/\d+\.\d+/, "number.float.jinja2"],
            [/\d+/, "number.jinja2"],
            [/[a-zA-Z_][a-zA-Z0-9_]*/, {
                cases: {
                    "@jinjaExpressionKeywords": "keyword.jinja2",
                    "@default": "variable.name.jinja2",
                },
            }],
            [/~|==|!=|<=|>=|\||\+|-|\*|\/|%|!|<|>|=|\(|\)|\[|\]|,|\./, "operator.jinja2"],
            [/["']/, "string.jinja2"],
            [/\s+/, "white"],
        ],

        jinjaExpressionTail: [
            [/[-+]?%\}/, "delimiter.bracket.jinja2", "@popall"],
            {include: "@jinjaExpression"},
        ],
    },
};

export const bashJinjaLanguageConfiguration = {
    comments: {
        lineComment: "#",
    },
    brackets: [
        ["{", "}"],
        ["[", "]"],
        ["(", ")"],
    ],
    autoClosingPairs: [
        {open: "{", close: "}"},
        {open: "[", close: "]"},
        {open: "(", close: ")"},
        {open: "\"", close: "\""},
        {open: "'", close: "'"},
        {open: "`", close: "`"},
    ],
    surroundingPairs: [
        {open: "{", close: "}"},
        {open: "[", close: "]"},
        {open: "(", close: ")"},
        {open: "\"", close: "\""},
        {open: "'", close: "'"},
        {open: "`", close: "`"},
    ],
};

// Language and theme registration guards
// -------------------------------------------------------------------------------------------------------------------
// The file editor (`Editor/Editor.tsx`) also defines the `ucloud-dark` theme and registers the
// `jinja2` language, but it does so inline on its own mount. The creator editors must not assume
// the file editor is mounted, and must not register the language twice.

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

// Completion and hover providers
// -------------------------------------------------------------------------------------------------------------------
// Context-sensitive completion:
//
// - Inside `{{ }}` or `{% %}` at expression start: parameters first, then set/macro names,
//   `ucloud`, and functions.
// - After `|`: filters. After `is`/`is not`: tests.
// - After `.`: members of the root name (ucloud tree, loop members, kind methods).
// - At the start of a `{% %}` tag (after the keyword of the statement): statement keywords. This
//   applies while typing the first word inside `{% `.

const modelParameters = new WeakMap<Monaco.editor.ITextModel, InvocationParameters>();

export function setInvocationModelParameters(
    model: Monaco.editor.ITextModel,
    parameters: InvocationParameters,
): void {
    modelParameters.set(model, parameters);
}

let providersRegistered = false;

export function registerInvocationProviders(monaco: typeof Monaco): void {
    if (providersRegistered) return;
    providersRegistered = true;

    monaco.languages.registerCompletionItemProvider("bash-jinja", {
        triggerCharacters: [".", "|", " ", "("],
        provideCompletionItems(model, position) {
            const parameters = modelParameters.get(model) ?? [];
            return completionItems(monaco, model, position, parameters);
        },
    });

    monaco.languages.registerHoverProvider("bash-jinja", {
        provideHover(model, position) {
            const parameters = modelParameters.get(model) ?? [];
            return hoverInfo(model, position, parameters);
        },
    });
}

type CompletionKind = "expression" | "filter" | "test" | "member" | "statementKeyword" | "none";

interface CompletionContext {
    kind: CompletionKind;
    word: string;
    range: Monaco.IRange;
    memberPath: string[];
}

function completionItems(
    monaco: typeof Monaco,
    model: Monaco.editor.ITextModel,
    position: Monaco.Position,
    parameters: InvocationParameters,
): Monaco.languages.CompletionList {
    const context = completionContextAt(model, position);
    const items: Monaco.languages.CompletionItem[] = [];

    if (context.kind === "statementKeyword") {
        for (const tag of invocationStatementTags) {
            items.push(symbolItem(monaco, tag, context.range, monaco.languages.CompletionItemKind.Keyword, "2"));
        }
    } else if (context.kind === "filter") {
        for (const filter of invocationFilters) {
            items.push(symbolItem(monaco, filter, context.range, monaco.languages.CompletionItemKind.Function, "1"));
        }
    } else if (context.kind === "test") {
        for (const test of invocationTests) {
            items.push(symbolItem(monaco, test, context.range, monaco.languages.CompletionItemKind.Function, "1"));
        }
    } else if (context.kind === "member") {
        const scope = invocationScopeAt(model.getValue(), model.getOffsetAt(position), parameters);
        const members = invocationResolveMembers(scope, context.memberPath);
        if (members) {
            for (const member of members) {
                items.push({
                    label: member.name,
                    kind: member.isMethod
                        ? monaco.languages.CompletionItemKind.Method
                        : monaco.languages.CompletionItemKind.Field,
                    insertText: member.name,
                    detail: member.signature ?? member.kind,
                    documentation: member.description,
                    range: context.range,
                    sortText: (member.isMethod ? "3" : "1") + member.name,
                });
            }
        }
    } else if (context.kind === "expression") {
        const scope = invocationScopeAt(model.getValue(), model.getOffsetAt(position), parameters);
        for (const entry of scope) {
            const callable = entry.kind === "function" || entry.kind === "macro";
            items.push({
                label: entry.name,
                kind: callable
                    ? monaco.languages.CompletionItemKind.Function
                    : monaco.languages.CompletionItemKind.Variable,
                insertText: entry.name,
                detail: entry.signature ?? entry.kind,
                documentation: entry.description,
                range: context.range,
                sortText: (entry.isParameter ? "0" : callable ? "2" : "1") + entry.name,
            });
        }
    }

    return {suggestions: items};
}

function symbolItem(
    monaco: typeof Monaco,
    doc: InvocationSymbolDoc,
    range: Monaco.IRange,
    kind: Monaco.languages.CompletionItemKind,
    sortPrefix: string,
): Monaco.languages.CompletionItem {
    return {
        label: doc.name,
        kind,
        insertText: doc.name,
        detail: doc.signature,
        documentation: doc.description,
        range,
        sortText: sortPrefix + doc.name,
    };
}

function completionContextAt(model: Monaco.editor.ITextModel, position: Monaco.Position): CompletionContext {
    const text = model.getValue();
    const offset = model.getOffsetAt(position);
    const tags = invocationFindTags(text);

    let enclosing: {tag: (typeof tags)[number]; innerOffset: number} | null = null;
    for (const tag of tags) {
        if (tag.start < offset && offset <= tag.end) {
            enclosing = {tag, innerOffset: offset - tag.start - 2};
            break;
        }
    }
    if (!enclosing || enclosing.tag.type === "comment") {
        return noneContext(position);
    }

    const inner = enclosing.tag.inner;
    const innerOffset = Math.max(0, Math.min(enclosing.innerOffset, inner.length));
    const before = inner.slice(0, innerOffset);
    const tokens = tokenizeExpression(before);

    const wordMatch = /[a-zA-Z_][a-zA-Z0-9_]*$/.exec(before);
    const word = wordMatch ? wordMatch[0] : "";
    const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        startColumn: position.column - word.length,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
    };

    if (enclosing.tag.type === "statement" && before.trim() === "") {
        return {kind: "statementKeyword", word, range, memberPath: []};
    }

    const last = tokens[tokens.length - 1];
    if (last?.type === "operator" && last.text === ".") {
        const path = pathBeforeDot(tokens);
        if (path.length > 0) return {kind: "member", word, range, memberPath: path};
    }

    if (last?.type === "operator" && last.text === "|") {
        return {kind: "filter", word, range, memberPath: []};
    }

    if (isTestPosition(tokens)) {
        return {kind: "test", word, range, memberPath: []};
    }

    return {kind: "expression", word, range, memberPath: []};
}

function noneContext(position: Monaco.Position): CompletionContext {
    return {
        kind: "none",
        word: "",
        range: {
            startLineNumber: position.lineNumber,
            startColumn: position.column,
            endLineNumber: position.lineNumber,
            endColumn: position.column,
        },
        memberPath: [],
    };
}

function isTestPosition(tokens: {type: string; text: string}[]): boolean {
    let i = tokens.length - 1;
    const last = tokens[i];
    if (last && last.type === "identifier") i--;
    if (tokens[i]?.text === "is" || (last && last.type === "identifier" && last.text === "is")) return true;
    if (i >= 0 && tokens[i]?.type === "identifier" && tokens[i].text === "not") i--;
    return i >= 0 && tokens[i]?.type === "identifier" && tokens[i].text === "is";
}

function pathBeforeDot(tokens: {type: string; text: string}[]): string[] {
    const path: string[] = [];
    let i = tokens.length - 1;
    if (i < 0 || tokens[i].type !== "operator" || tokens[i].text !== ".") return path;
    i--;
    while (i >= 0 && tokens[i].type === "identifier") {
        path.unshift(tokens[i].text);
        const next = tokens[i - 1];
        if (next && next.type === "operator" && next.text === ".") {
            i -= 2;
            continue;
        }
        break;
    }
    return path;
}

// Hover
// -------------------------------------------------------------------------------------------------------------------

function hoverInfo(
    model: Monaco.editor.ITextModel,
    position: Monaco.Position,
    parameters: InvocationParameters,
): Monaco.languages.Hover | null {
    const word = model.getWordAtPosition(position);
    if (!word) return null;
    const text = model.getValue();
    const offset = model.getOffsetAt(position);
    const tags = invocationFindTags(text);
    const tag = tags.find(t => t.start <= offset && offset < t.end && t.type !== "comment");
    if (!tag) return null;

    const scope = invocationScopeAt(text, offset, parameters);
    const entry = scope.find(e => e.name === word.word);
    if (entry) {
        return {
            range: {
                startLineNumber: position.lineNumber,
                startColumn: word.startColumn,
                endLineNumber: position.lineNumber,
                endColumn: word.endColumn,
            },
            contents: [{value: hoverMarkdown(entry)}],
        };
    }

    const innerOffset = offset - tag.start - 2;
    const tokens = tokenizeExpression(tag.inner);
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i];
        if (t.type !== "identifier" || t.text !== word.word) continue;
        if (innerOffset < t.start || innerOffset > t.end) continue;
        const prev = tokens[i - 1];
        let doc: InvocationSymbolDoc | undefined;
        if (prev?.type === "operator" && prev.text === "|") {
            doc = invocationFilters.find(f => f.name === word.word);
        } else if (isTestNamePosition(tokens, i)) {
            doc = invocationTests.find(t => t.name === word.word);
        }
        if (doc) {
            return {
                range: {
                    startLineNumber: position.lineNumber,
                    startColumn: word.startColumn,
                    endLineNumber: position.lineNumber,
                    endColumn: word.endColumn,
                },
                contents: [{value: `**${doc.name}** — ${doc.signature}\n\n${doc.description}`}],
            };
        }
        break;
    }
    return null;
}

function isTestNamePosition(tokens: {type: string; text: string}[], i: number): boolean {
    const prev = tokens[i - 1];
    if (!prev) return false;
    if (prev.type === "identifier" && prev.text === "is") return true;
    if (prev.type === "identifier" && prev.text === "not") {
        const before = tokens[i - 2];
        return before?.type === "identifier" && before.text === "is";
    }
    return false;
}

function hoverMarkdown(entry: InvocationScopeEntry): string {
    const header = entry.signature ? `**${entry.name}** — ${entry.signature}` : `**${entry.name}**`;
    const lines = [header];
    if (entry.description) lines.push("", entry.description);
    return lines.join("\n");
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
}

const CREATOR_EDITOR_SETTINGS_KEY = "PreviewEditorSettings";

function readStoredSettings(): CreatorStoredSettings {
    try {
        const parsed = JSON.parse(localStorage.getItem(CREATOR_EDITOR_SETTINGS_KEY) ?? "{}");
        if (parsed == null || typeof parsed !== "object" || Array.isArray(parsed)) return {};
        const stored = parsed as Record<string, unknown>;
        const result: CreatorStoredSettings = {};
        if (typeof stored.fontSize === "number" && Number.isFinite(stored.fontSize)) result.fontSize = stored.fontSize;
        if (typeof stored.fontWeight === "string") result.fontWeight = stored.fontWeight;
        if (typeof stored.wordWrap === "string") result.wordWrap = stored.wordWrap;
        return result;
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
