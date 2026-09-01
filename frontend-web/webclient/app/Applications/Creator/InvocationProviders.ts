// Monaco providers (completion, hover) for the invocation editor
// =====================================================================================================================
// Registers completion and hover providers for the `bash-jinja` language. The providers read the
// draft parameters from a per-model store so parameter edits update completions without
// re-registering anything.
//
// Context-sensitive completion:
//
// - Inside `{{ }}` or `{% %}` at expression start: parameters first, then set/macro names,
//   `ucloud`, and functions.
// - After `|`: filters. After `is`/`is not`: tests.
// - After `.`: members of the root name (ucloud tree, loop members, kind methods).
// - At the start of a `{% %}` tag (after the keyword of the statement): statement keywords. This
//   applies while typing the first word inside `{% `.
//
// Everything runs client-side. The scope module does the parsing; this module adapts it to
// Monaco's provider APIs.

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

// Per-model parameter store. The InvocationEditor keeps the entry fresh on prop change.
const modelParameters = new WeakMap<Monaco.editor.ITextModel, InvocationParameters>();

export function setInvocationModelParameters(
    model: Monaco.editor.ITextModel,
    parameters: InvocationParameters,
): void {
    modelParameters.set(model, parameters);
}

// Registration guard: providers are registered once per Monaco instance.
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

// Completion context at a position: what kind of completion applies and the partial word.
type CompletionKind = "expression" | "filter" | "test" | "member" | "statementKeyword" | "none";

interface CompletionContext {
    kind: CompletionKind;
    // Partial word being typed.
    word: string;
    // Replace range for the completion.
    range: Monaco.IRange;
    // Member path for kind "member": the root path before the dot.
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

// Compute the completion context from the text before the cursor.
function completionContextAt(model: Monaco.editor.ITextModel, position: Monaco.Position): CompletionContext {
    const text = model.getValue();
    const offset = model.getOffsetAt(position);
    const tags = invocationFindTags(text);

    // The tag containing the cursor. `offset <= tag.end` keeps completion active right after the
    // closing delimiter characters are typed (Monaco fires on the new text).
    let enclosing: {tag: (typeof tags)[number]; innerOffset: number} | null = null;
    for (const tag of tags) {
        if (tag.start < offset && offset <= tag.end) {
            // Inner offset relative to the inner text (skip the 2-char opening delimiter).
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

    // Partial word at the cursor.
    const wordMatch = /[a-zA-Z_][a-zA-Z0-9_]*$/.exec(before);
    const word = wordMatch ? wordMatch[0] : "";
    const range: Monaco.IRange = {
        startLineNumber: position.lineNumber,
        startColumn: position.column - word.length,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
    };

    // Statement keyword position: only whitespace before the cursor inside a {% %} tag.
    if (enclosing.tag.type === "statement" && before.trim() === "") {
        return {kind: "statementKeyword", word, range, memberPath: []};
    }

    // Member position: the tokens before the cursor end with a dot.
    const last = tokens[tokens.length - 1];
    if (last?.type === "operator" && last.text === ".") {
        const path = pathBeforeDot(tokens);
        if (path.length > 0) return {kind: "member", word, range, memberPath: path};
    }

    // Filter position: last significant token is `|`.
    if (last?.type === "operator" && last.text === "|") {
        return {kind: "filter", word, range, memberPath: []};
    }

    // Test position: tokens end with `is` or `is not` (optionally plus a partial word).
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

// True when the tokens before the cursor end with `is` or `is not` (skipping a partial word).
function isTestPosition(tokens: {type: string; text: string}[]): boolean {
    let i = tokens.length - 1;
    const last = tokens[i];
    if (last && last.type === "identifier") i--;
    if (i >= 0 && tokens[i]?.type === "identifier" && tokens[i].text === "not") i--;
    return i >= 0 && tokens[i]?.type === "identifier" && tokens[i].text === "is";
}

// The dotted path ending just before the trailing dot: tokens like [ident] . [ident] . → path.
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

    // A scope entry (variable/function/macro name).
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

    // A filter or test name at this position.
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
