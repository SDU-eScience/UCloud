// Invocation reference tracking
// =====================================================================================================================
// On rename, the editor updates references to the old parameter name in the Jinja invocation
// template. On delete, references remain unchanged because there is no safe replacement value.
//
// The rewrite covers the `invocation` field only. It is token-based and uses the same tag scanner
// and expression tokenizer as the completion provider and the linter (InvocationScope.ts), so it
// agrees with the linter about which identifiers are reads of a parameter. Handled shapes:
//
// - `{{ name }}`, `{{- name -}}`, `{{ name | filter }}`, `{{ name.attr }}`, `{{ f(name) }}`,
//   `{% if name %}`: rewritten. Only the identifier span changes; whitespace is preserved.
// - `{% for x in name %}`, `{% set x = name %}`, `{% with x = name %}`: rewritten. The iterable
//   and the right-hand sides are evaluated in the enclosing scope, so they read the parameter.
// - `a.name`: not rewritten (a member, not the parameter).
// - `f(name=1)` keyword arguments, filter names after `|`, test names after `is`: not rewritten.
// - `{% for name in seq %}`, `{% set name = ... %}`, `{% with name = ... %}`, macro
//   declarations and their parameter lists: not rewritten. These define local bindings that
//   shadow the parameter; reads inside those scopes resolve to the local binding, not the
//   parameter, and are left alone. A top-level macro name shadows the parameter from the
//   declaration to the end of the template; a set inside a for/macro/with/filter/autoescape
//   block dies with that block, matching the scope module's model.
// - `{% raw %}` bodies and `{# comments #}`: not rewritten (no Jinja semantics).
// - Bash text outside tags: not rewritten.
//
// Replacements are applied back-to-front on the original text so offsets stay valid.

import {
    invocationFindTags,
    tokenizeExpression,
    ExprToken,
} from "@/Applications/Creator/InvocationScope";

interface LocalBinding {
    name: string;
    start: number;
    end: number;
}

export function rewriteInvocationReferences(invocation: string, oldName: string, newName: string): string {
    if (!oldName || !newName || oldName === newName) return invocation;

    const tags = invocationFindTags(invocation);

    const bindings: LocalBinding[] = [];

    for (const tag of tags) {
        if (tag.type !== "statement") continue;
        const word = statementWord(tag.inner);
        switch (word) {
            case "for": {
                const inIdx = findInKeyword(tag.inner);
                if (inIdx < 0) break;
                const bindStart = tag.start + 2 + inIdx;
                for (const target of forTargets(tag.inner)) {
                    bindings.push({name: target, start: bindStart, end: blockEnd(tags, tag, "endfor")});
                }
                break;
            }
            case "set": {
                const target = setTarget(tag.inner);
                if (target && !target.isAttribute) {
                    bindings.push({name: target.name, start: tag.end, end: enclosingBlockEnd(tags, tag)});
                }
                break;
            }
            case "with": {
                for (const name of withNames(tag.inner)) {
                    bindings.push({name, start: tag.end, end: blockEnd(tags, tag, "endwith")});
                }
                break;
            }
            case "macro": {
                const decl = macroDecl(tag.inner);
                if (decl) {
                    bindings.push({name: decl.name, start: tag.start, end: enclosingBlockEnd(tags, tag)});
                    for (const p of decl.params) {
                        bindings.push({name: p, start: tag.start, end: blockEnd(tags, tag, "endmacro")});
                    }
                }
                break;
            }
            default:
                break;
        }
    }

    const spans: {start: number; end: number}[] = [];

    for (const tag of tags) {
        if (tag.type === "comment") continue;
        const innerBase = tag.start + 2;
        const tokens = tokenizeExpression(tag.inner);
        const word = tag.type === "statement" ? statementWord(tag.inner) : "";

        for (let i = 0; i < tokens.length; i++) {
            const t = tokens[i];
            if (t.type !== "identifier") continue;
            if (isKeywordWord(t.text)) continue;
            if (isDefinitionPosition(tokens, i, word)) continue;

            if (t.text !== oldName) continue;

            const absStart = innerBase + t.start;
            const absEnd = innerBase + t.end;

            if (bindings.some(b => b.name === oldName && b.start <= absStart && absEnd <= b.end)) continue;

            spans.push({start: absStart, end: absEnd});
        }
    }

    if (spans.length === 0) return invocation;

    let result = invocation;
    for (let i = spans.length - 1; i >= 0; i--) {
        result = result.slice(0, spans[i].start) + newName + result.slice(spans[i].end);
    }
    return result;
}

function isDefinitionPosition(tokens: ExprToken[], i: number, word: string): boolean {
    const t = tokens[i];
    const prev = tokens[i - 1];

    if (prev?.type === "operator" && prev.text === ".") return true;
    if (prev?.type === "operator" && prev.text === "|") return true;
    if (isTestNamePosition(tokens, i)) return true;
    if (isKeywordArgument(tokens, i)) return true;

    if (word === "for") {
        const inIdx = findInKeyword(tokensToText(tokens));
        if (inIdx >= 0 && t.start < inIdx) return true;
    } else if (word === "set") {
        const setIdx = tokens.findIndex(tok => tok.type === "identifier" && tok.text === "set");
        if (setIdx >= 0) {
            const target = tokens[setIdx + 1];
            if (target && target.type === "identifier" && target.start === t.start) return true;
        }
    } else if (word === "with") {
        const next = tokens[i + 1];
        if (next?.type === "operator" && next.text === "=") return true;
    } else if (word === "macro") {
        const closeParen = tokensToText(tokens).indexOf(")");
        if (closeParen >= 0 && t.start < closeParen) return true;
    }
    return false;
}

function isTestNamePosition(tokens: ExprToken[], i: number): boolean {
    const prev = tokens[i - 1];
    if (!prev) return false;
    if (prev.type === "identifier" && prev.text === "is") return true;
    if (prev.type === "identifier" && prev.text === "not") {
        const before = tokens[i - 2];
        return before?.type === "identifier" && before.text === "is";
    }
    return false;
}

function isKeywordArgument(tokens: ExprToken[], i: number): boolean {
    const next = tokens[i + 1];
    if (!next || next.type !== "operator" || next.text !== "=") return false;
    let depth = 0;
    for (let j = 0; j < i; j++) {
        const t = tokens[j];
        if (t.type === "operator" && t.text === "(") depth++;
        else if (t.type === "operator" && t.text === ")") depth--;
    }
    return depth > 0;
}

function tokensToText(tokens: ExprToken[]): string {
    let text = "";
    for (const t of tokens) {
        const gap = t.start - text.length;
        if (gap > 0) text += " ".repeat(gap);
        text += t.text;
    }
    return text;
}

function blockEnd(tags: ReturnType<typeof invocationFindTags>, openTag: {start: number}, endWord: string): number {
    for (const tag of tags) {
        if (tag.type !== "statement") continue;
        if (tag.start <= openTag.start) continue;
        if (statementWord(tag.inner) === endWord) return tag.start;
    }
    return Number.MAX_SAFE_INTEGER;
}

function enclosingBlockEnd(tags: ReturnType<typeof invocationFindTags>, tag: {start: number}): number {
    const openers: Record<string, string> = {
        "for": "endfor", "macro": "endmacro", "with": "endwith",
        "filter": "endfilter", "autoescape": "endautoescape",
    };
    const stack: string[] = [];
    for (const t of tags) {
        if (t.type !== "statement") continue;
        if (t.start >= tag.start) break;
        const word = statementWord(t.inner);
        if (openers[word]) {
            stack.push(openers[word]);
        } else if (stack.length > 0 && stack[stack.length - 1] === word) {
            stack.pop();
        }
    }
    if (stack.length === 0) return Number.MAX_SAFE_INTEGER;
    const endWord = stack[stack.length - 1];
    for (const t of tags) {
        if (t.type !== "statement") continue;
        if (t.start <= tag.start) continue;
        if (statementWord(t.inner) === endWord) return t.start;
    }
    return Number.MAX_SAFE_INTEGER;
}

function statementWord(inner: string): string {
    const m = /^\s*[+-]?\s*([a-zA-Z_][a-zA-Z0-9_]*)/.exec(inner);
    return m ? m[1] : "";
}

function skipTagLead(inner: string): string {
    return inner.replace(/^\s*[+-]?\s*/, "");
}

function forTargets(inner: string): string[] {
    const body = skipTagLead(inner).replace(/^for\s+/, "");
    const inIdx = findInKeyword(body);
    if (inIdx < 0) return [];
    return body
        .slice(0, inIdx)
        .split(",")
        .map(t => t.trim())
        .filter(t => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(t));
}

function findInKeyword(s: string): number {
    let depth = 0;
    for (let i = 0; i < s.length; i++) {
        const c = s[i];
        if (c === "(" || c === "[" || c === "{") depth++;
        else if (c === ")" || c === "]" || c === "}") depth--;
        else if (depth === 0 && s.startsWith(" in ", i)) return i;
    }
    return -1;
}

function setTarget(inner: string): {name: string; isAttribute: boolean} | null {
    const body = skipTagLead(inner);
    const attr = /^set\s+([a-zA-Z_][a-zA-Z0-9_]*)(\s*[.[])/.exec(body);
    if (attr) return {name: attr[1], isAttribute: true};
    const plain = /^set\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*=/.exec(body);
    if (!plain) return null;
    return {name: plain[1], isAttribute: false};
}

function macroDecl(inner: string): {name: string; params: string[]} | null {
    const m = /^macro\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)/.exec(skipTagLead(inner));
    if (!m) return null;
    const params = m[2]
        .split(",")
        .map(p => /^[a-zA-Z_][a-zA-Z0-9_]*/.exec(p.trim())?.[0] ?? "")
        .filter(Boolean);
    return {name: m[1], params};
}

function withNames(inner: string): string[] {
    const body = skipTagLead(inner).replace(/^with\s+/, "");
    const names: string[] = [];
    const re = /(?:^|,)\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*=/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(body)) !== null) names.push(m[1]);
    return names;
}

function isKeywordWord(word: string): boolean {
    return ["and", "or", "not", "in", "is", "if", "else", "true", "false", "True", "False", "none", "None", "recursive"].includes(word);
}
