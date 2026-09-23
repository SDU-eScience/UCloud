// Client-side lint for invocation templates
// =====================================================================================================================
// Produces marker data for the invocation model. Runs debounced on content change. The checks
// are grouped by how certain the failure is:
//
// 1. Lexical: unclosed variable/statement/comment tags and unterminated strings inside tags.
// 2. Structure: block tags (for/if/macro/with/filter/autoescape/raw) must be closed by their end
//    tag, else/elif must sit inside an if-block, end tags must match an open block and template-
//    loading tags (include/import/from/extends/block) are rejected.
// 3. References: unknown identifiers, unknown filters after `|`, unknown tests
//    after `is`, unknown members after `.` on known roots (ucloud/loop), calling a non-function.

import {
    invocationFilters,
    invocationTests,
    statementEndPairs,
    unsupportedStatementTags,
} from "@/Applications/Creator/InvocationCatalog";
import {
    findTagClose,
    invocationFindTags,
    invocationMemberExists,
    invocationScopeAt,
    tokenizeExpression,
    type InvocationParameters,
    type InvocationTag,
    type ExprToken,
    type InvocationScopeEntry,
} from "@/Applications/Creator/InvocationScope";

export interface InvocationLintMarker {
    message: string;
    start: number;
    end: number;
    severity: "error" | "warning";
}

export function invocationLint(text: string, parameters: InvocationParameters): InvocationLintMarker[] {
    const markers: InvocationLintMarker[] = [];
    lintLexical(text, markers);
    const tags = invocationFindTags(text);
    lintStructure(tags, markers);
    lintReferences(text, tags, parameters, markers);
    return markers;
}

// Lexical checks
// -------------------------------------------------------------------------------------------------------------------

const openDelims: {open: string; close: string; label: string; type: "variable" | "statement" | "comment"}[] = [
    {open: "{{", close: "}}", label: "variable tag", type: "variable"},
    {open: "{%", close: "%}", label: "statement tag", type: "statement"},
    {open: "{#", close: "#}", label: "comment tag", type: "comment"},
];

function lintLexical(text: string, markers: InvocationLintMarker[]): void {
    const n = text.length;
    let i = 0;
    while (i < n) {
        const open = text.indexOf("{", i);
        if (open < 0 || open + 1 >= n) break;
        const d = text[open + 1];
        const spec = openDelims.find(s => s.open === "{" + d);
        if (!spec) {
            i = open + 1;
            continue;
        }
        const close = findTagClose(text, open + 2, spec.close, spec.type);
        if (close < 0) {
            markers.push({
                message: `Unclosed ${spec.label}. Add ${spec.close} to close it.`,
                start: open,
                end: Math.min(open + 2, n),
                severity: "error",
            });
            break;
        }
        if (spec.type !== "comment") {
            const inner = text.slice(open + 2, close);
            const str = unterminatedString(inner);
            if (str >= 0) {
                markers.push({
                    message: "Unterminated string.",
                    start: open + 2 + str,
                    end: close,
                    severity: "error",
                });
            }
        }
        i = close + 2;
    }
}

function unterminatedString(s: string): number {
    let i = 0;
    while (i < s.length) {
        const c = s[i];
        if (c === '"' || c === "'") {
            let j = i + 1;
            while (j < s.length && s[j] !== c) {
                if (s[j] === "\\") j++;
                j++;
            }
            if (j >= s.length) return i;
            i = j + 1;
            continue;
        }
        i++;
    }
    return -1;
}

// Structure checks
// -------------------------------------------------------------------------------------------------------------------

const endTagKeywords: Record<string, string> = {
    "endfor": "for",
    "endif": "if",
    "endmacro": "macro",
    "endwith": "with",
    "endfilter": "filter",
    "endautoescape": "autoescape",
    "endraw": "raw",
};

function lintStructure(tags: InvocationTag[], markers: InvocationLintMarker[]): void {
    const stack: {keyword: string; endWord: string; start: number; end: number}[] = [];
    let inRaw = false;
    for (const tag of tags) {
        if (tag.type !== "statement") continue;
        const word = statementWordOf(tag.inner);
        if (!word) continue;

        if (inRaw && word !== "endraw") continue;
        if (word === "raw") {
            stack.push({keyword: "raw", endWord: "endraw", start: tag.start, end: tag.end});
            inRaw = true;
            continue;
        }

        if (word in statementEndPairs) {
            stack.push({keyword: word, endWord: statementEndPairs[word], start: tag.start, end: tag.end});
        } else if (word in endTagKeywords) {
            const matchIdx = [...stack].reverse().findIndex(b => b.endWord === word);
            if (matchIdx < 0) {
                markers.push({
                    message: `{% ${word} %} without a matching {% ${endTagKeywords[word]} %}.`,
                    start: tag.start,
                    end: tag.end,
                    severity: "error",
                });
            } else {
                for (let i = stack.length - matchIdx; i < stack.length; i++) {
                    markers.push({
                        message: `Missing {% ${stack[i].endWord} %} for this {% ${stack[i].keyword} %}.`,
                        start: stack[i].start,
                        end: stack[i].end,
                        severity: "error",
                    });
                }
                stack.length = stack.length - 1 - matchIdx;
            }
        } else if (word === "else" || word === "elif") {
            if (!stack.some(b => b.keyword === "if" || b.keyword === "for")) {
                markers.push({
                    message: `{% ${word} %} outside of an {% if %} block.`,
                    start: tag.start,
                    end: tag.end,
                    severity: "error",
                });
            }
            if (word === "elif" && skipLead(tag.inner) === "elif") {
                markers.push({
                    message: "elif requires a condition.",
                    start: tag.start,
                    end: tag.end,
                    severity: "error",
                });
            }
        } else if (unsupportedStatementTags.includes(word)) {
            markers.push({
                message: `{% ${word} %} is not available in application invocations.`,
                start: tag.start,
                end: tag.end,
                severity: "error",
            });
        }
    }
    for (const block of stack) {
        markers.push({
            message: `Missing {% ${block.endWord} %} for this {% ${block.keyword} %}.`,
            start: block.start,
            end: block.end,
            severity: "error",
        });
    }
}

function statementWordOf(inner: string): string {
    return /^\s*[+-]?\s*([a-zA-Z_][a-zA-Z0-9_]*)/.exec(inner)?.[1] ?? "";
}

function skipLead(inner: string): string {
    return inner.replace(/^\s*[+-]?\s*/, "");
}

// Reference checks
// -------------------------------------------------------------------------------------------------------------------

function lintReferences(
    text: string,
    tags: InvocationTag[],
    parameters: InvocationParameters,
    markers: InvocationLintMarker[],
): void {
    for (const tag of tags) {
        if (tag.type === "comment") continue;

        const scope = invocationScopeAt(text, tag.start, parameters);
        const inner = tag.inner;

        let exprInner = inner;
        if (tag.type === "statement") {
            const keyword = statementWordOf(inner);
            const keywordEnd = inner.indexOf(keyword) + keyword.length;
            if (keyword === "for") {
                const inIdx = findInKeywordOf(inner);
                exprInner = inIdx >= 0 ? inner.slice(inIdx) : "";
            } else if (keyword === "set" || keyword === "with") {
                const eq = inner.indexOf("=");
                exprInner = eq >= 0 ? inner.slice(eq + 1) : "";
            } else if (keyword === "macro") {
                const closeParen = inner.indexOf(")");
                exprInner = closeParen >= 0 ? inner.slice(closeParen + 1) : "";
            } else if (keyword === "if" || keyword === "elif") {
                exprInner = inner.slice(keywordEnd);
            } else if (endTagKeywords[keyword] !== undefined || keyword === "else" || unsupportedStatementTags.includes(keyword)) {
                continue;
            } else {
                exprInner = inner.slice(keywordEnd);
            }
        }

        const tokens = tokenizeExpression(exprInner);
        const base = tag.start + 2 + (inner.length - exprInner.length);
        checkNames(scope, tokens, base, markers);
        checkFiltersAndTests(tokens, base, markers);
    }
}

function findInKeywordOf(inner: string): number {
    let depth = 0;
    for (let i = 0; i < inner.length; i++) {
        const c = inner[i];
        if (c === "(" || c === "[" || c === "{") depth++;
        else if (c === ")" || c === "]" || c === "}") depth--;
        else if (depth === 0 && inner.startsWith(" in ", i)) return i;
    }
    return -1;
}

function checkNames(
    scope: InvocationScopeEntry[],
    tokens: ExprToken[],
    base: number,
    identifiers: InvocationLintMarker[],
): void {
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i];
        if (t.type !== "identifier") continue;
        if (isKeywordWord(t.text)) continue;
        const prev = tokens[i - 1];

        if (prev?.type === "operator" && prev.text === "|") continue;
        if (isTestNamePosition(tokens, i)) continue;

        if (isKeywordArgument(tokens, i)) continue;

        if (prev?.type === "operator" && prev.text === ".") continue;

        const path: string[] = [t.text];
        let end = t.end;
        let j = i + 1;
        let called = false;
        while (j + 1 < tokens.length) {
            const dot = tokens[j];
            const name = tokens[j + 1];
            if (dot?.type === "operator" && dot.text === "." && name?.type === "identifier") {
                path.push(name.text);
                end = name.end;
                j += 2;
                continue;
            }
            break;
        }
        if (tokens[j]?.type === "operator" && tokens[j].text === "(") called = true;

        const root = scope.find(e => e.name === path[0]);
        if (!root) {
            identifiers.push({
                message: `Unknown variable "${path.join(".")}".`,
                start: base + t.start,
                end: base + end,
                severity: "error",
            });
            i = j - 1;
            continue;
        }

        if (path.length > 1 && !called) {
            const known = invocationMemberExists(scope, path);
            if (!known.exists) {
                identifiers.push({
                    message: `Unknown member "${path.join(".")}".`,
                    start: base + t.start,
                    end: base + end,
                    severity: "error",
                });
            }
        }

        const next = tokens[j];
        const isCall = next?.type === "operator" && next.text === "(" && end === tokens[j - 1].end;
        if (isCall && path.length === 1 && root.kind !== "function" && root.kind !== "macro") {
            identifiers.push({
                message: `"${path[0]}" is not callable.`,
                start: base + t.start,
                end: base + end,
                severity: "error",
            });
        }

        i = j - 1;
    }
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

function checkFiltersAndTests(
    tokens: ExprToken[],
    base: number,
    markers: InvocationLintMarker[],
): void {
    for (let i = 0; i < tokens.length; i++) {
        const t = tokens[i];
        if (t.type !== "identifier") continue;
        const prev = tokens[i - 1];
        if (prev?.type === "operator" && prev.text === "|") {
            if (!invocationFilters.some(f => f.name === t.text)) {
                markers.push({
                    message: `Unknown filter "${t.text}".`,
                    start: base + t.start,
                    end: base + t.end,
                severity: "error",
                });
            }
        }
        if (isTestNamePosition(tokens, i) && !isKeywordWord(t.text)) {
            if (!invocationTests.some(test => test.name === t.text)) {
                markers.push({
                    message: `Unknown test "${t.text}".`,
                    start: base + t.start,
                    end: base + t.end,
                    severity: "error",
                });
            }
        }
    }
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

function isKeywordWord(word: string): boolean {
    return ["and", "or", "not", "in", "is", "if", "else", "true", "false", "True", "False", "none", "None", "recursive"].includes(word);
}
