// Scope computation for invocation templates
// =====================================================================================================================
// This module answers one question for the completion provider and the linter: which names are in
// scope at a given position in a Jinja template, and what is the type of each name.
//
// It is a small hand-written parser over the invocation subset of Jinja, not a full Jinja parser.
// It recognizes tags, loops, sets, macros, and attribute chains, plus the static catalog
// (ucloud tree, functions) and the application parameters. It tracks these constructs:
//
// - `{% for x in seq %}` / `{% for k, v in seq %}`: `x` (or `k`/`v`) and `loop` are in scope until
//   the matching endfor.
// - `{% set name = expr %}`: `name` is in scope from the set onward, unless the set sits inside a
//   for/macro/with/filter/autoescape block. gonja runs those bodies in a sub-context, so the
//   binding dies at the end tag. if-bodies run in the enclosing context, so a set inside an if
//   persists after endif. `{% set ns.attr = v %}` mutates a namespace but does not create a new
//   binding.
// - `{% macro name(a, b) %}`: `name` is a function in scope from the macro declaration onward,
//   unless the macro is declared inside a for/macro/with/filter/autoescape block, in which case
//   it dies with that block. Inside the macro body, `a`/`b` plus later sets/loops are in scope.
// - `{% with x = v %}`: `x` is in scope until the matching endwith.
// - `{% filter f %}`, `{% autoescape %}`: no new bindings.
//
// Type inference tracks: parameter types from the application, literals, function return kinds
// (namespace(), cycler(), range(), dict()), and member access on the ucloud tree and loop members.
// Where a type cannot be inferred, the kind is "unknown" and member completion offers nothing.
//
// Raw blocks (`{% raw %}...{% endraw %}`) contain no Jinja semantics. Their inner tags are
// excluded from the tag list by invocationFindTags.
//
// Everything here is a pure function of (template text, parameters). Monaco is not imported; this
// module is unit-testable without a DOM.

import {
    invocationFunctions,
    invocationMembers,
    loopMembers,
    ucloudObject,
    type InvocationUcloudMember,
    type InvocationValueKind,
} from "@/Applications/Creator/InvocationCatalog";
import type {A2Parameter} from "@/Applications/Creator/A2";

// A single name in scope at some position.
export interface InvocationScopeEntry {
    name: string;
    kind: InvocationValueKind;
    description: string;
    // True when the name refers to an application parameter (used for completion sorting).
    isParameter?: boolean;
    // Signature for callables.
    signature?: string;
}

// A member offered after `.`.
export interface InvocationMemberEntry {
    name: string;
    kind: InvocationValueKind;
    description: string;
    signature?: string;
    isMethod?: boolean;
}

// Parameters passed to the scope functions: name/parameter pairs in declaration order.
export type InvocationParameters = {name: string; param: A2Parameter}[];

// The runtime value kind of an application parameter, from the K8s renderer's parameter mapping
// (containers/invocation.go and controller/job_parameters.go). Job and Workflow parameters have
// no direct value in scope (Workflow maps to nil, Job is absent) and are excluded.
export function invocationParameterKind(param: A2Parameter): InvocationValueKind | null {
    switch (param.type) {
        case "Integer":
        case "FloatingPoint":
            return "number";
        case "Boolean":
            return "boolean";
        case "Text":
        case "TextArea":
        case "Enumeration":
        case "File":
        case "Directory":
        case "License":
        case "PublicIP":
            return "string";
        default:
            return null;
    }
}

// A parsed template tag with offsets into the whole template text.
export interface InvocationTag {
    // "variable" ({{ }}), "statement" ({% %}), or "comment" ({# #}).
    type: "variable" | "statement" | "comment";
    // Offset of the opening `{`.
    start: number;
    // Offset just past the closing delimiter.
    end: number;
    // The raw inner text between the delimiters, including whitespace-control markers. Kept raw
    // so offsets computed against it stay aligned with the template text; consumers skip
    // leading whitespace themselves.
    inner: string;
}

// Find the closed Jinja tags in the template. Unclosed tags are skipped (the linter reports
// them). A `{` that is not a Jinja delimiter is plain bash. Statement tags that open a raw block
// mark the raw span so tags inside it are excluded.
export function invocationFindTags(text: string): InvocationTag[] {
    const tags: InvocationTag[] = [];
    let i = 0;
    const n = text.length;
    while (i < n) {
        const open = text.indexOf("{", i);
        if (open < 0 || open + 1 >= n) break;
        const d = text[open + 1];
        if (d !== "{" && d !== "%" && d !== "#") {
            i = open + 1;
            continue;
        }
        let closeTok: string;
        let type: InvocationTag["type"];
        if (d === "{") {
            closeTok = "}}";
            type = "variable";
        } else if (d === "%") {
            closeTok = "%}";
            type = "statement";
        } else {
            closeTok = "#}";
            type = "comment";
        }
        const close = findTagClose(text, open + 2, closeTok, type);
        if (close < 0) {
            // Unclosed tag: skip past the opening delimiter and continue scanning.
            i = open + 2;
            continue;
        }
        const inner = text.slice(open + 2, close);
        const tag: InvocationTag = {type, start: open, end: close + 2, inner};

        // A raw block hides its content from the tag scanner: skip to the matching endraw. The
        // leading whitespace-control marker (`{%- raw %}`) is part of the check, matching
        // statementWord above.
        if (type === "statement" && statementWord(inner) === "raw") {
            let endrawStart = -1;
            let endrawEnd = -1;
            let scan = close + 2;
            while (scan < n) {
                const candidate = text.indexOf("{%", scan);
                if (candidate < 0) break;
                const candidateClose = text.indexOf("%}", candidate + 2);
                if (candidateClose < 0) break;
                if (statementWord(text.slice(candidate + 2, candidateClose)) === "endraw") {
                    endrawStart = candidate;
                    endrawEnd = candidateClose + 2;
                    break;
                }
                scan = candidateClose + 2;
            }
            tags.push(tag);
            if (endrawStart >= 0) {
                tags.push({type: "statement", start: endrawStart, end: endrawEnd, inner: "endraw"});
            }
            // Without a matching endraw the whole rest is raw content.
            i = endrawEnd >= 0 ? endrawEnd : n;
            continue;
        }
        tags.push(tag);
        i = close + 2;
    }
    return tags;
}

// Find the closing delimiter of a tag, skipping string literals so a `}}` or `%}` inside a
// quoted string does not close the tag early. Comment tags do not skip strings: gonja's comment
// lexer treats an apostrophe as plain comment text, so `{# don't #}` is a valid closed comment.
export function findTagClose(text: string, from: number, closeTok: string, type: InvocationTag["type"]): number {
    let i = from;
    const n = text.length;
    while (i < n) {
        const c = text[i];
        if (type !== "comment" && (c === '"' || c === "'")) {
            let j = i + 1;
            while (j < n && text[j] !== c) {
                if (text[j] === "\\") j++;
                j++;
            }
            i = j + 1;
            continue;
        }
        if (text.startsWith(closeTok, i)) return i;
        i++;
    }
    return -1;
}

// The leading word of a statement tag's inner text, e.g. "for" or "endfor". Empty when the tag does
// not start with a word. Skips leading whitespace and whitespace-control markers.
function statementWord(inner: string): string {
    const m = /^\s*[+-]?\s*([a-zA-Z_][a-zA-Z0-9_]*)/.exec(inner);
    return m ? m[1] : "";
}

// Names bound by a `for` tag: "for x in" or "for k, v in".
function parseForTargets(inner: string): string[] {
    const body = skipTagLead(inner).replace(/^for\s+/, "");
    const inIdx = findInKeyword(body);
    if (inIdx < 0) return [];
    return body
        .slice(0, inIdx)
        .split(",")
        .map(t => t.trim())
        .filter(t => /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(t));
}

// Offset of the ` in ` keyword that separates loop targets from the iterated expression. Skips
// brackets so `for x in dict(a=1)` finds the right `in`.
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

// The target of a set tag: "set name = ..." or "set ns.attr = ...". Returns the root name and
// whether the target was an attribute/item of an existing value.
function parseSetTarget(inner: string): {name: string; isAttribute: boolean} | null {
    const body = skipTagLead(inner);
    // An attribute/item target: "set ns.attr =" or "set ns[key] =".
    const attr = /^set\s+([a-zA-Z_][a-zA-Z0-9_]*)(\s*[.[])/.exec(body);
    if (attr) return {name: attr[1], isAttribute: true};
    const plain = /^set\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*=/.exec(body);
    if (!plain) return null;
    return {name: plain[1], isAttribute: false};
}

// A macro declaration: name and parameter names.
function parseMacroDecl(inner: string): {name: string; params: string[]} | null {
    const m = /^macro\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(([^)]*)\)/.exec(skipTagLead(inner));
    if (!m) return null;
    const params = m[2]
        .split(",")
        .map(p => /^[a-zA-Z_][a-zA-Z0-9_]*/.exec(p.trim())?.[0] ?? "")
        .filter(Boolean);
    return {name: m[1], params};
}

// Names bound by a with tag: "with x = v, y = w".
function parseWithNames(inner: string): string[] {
    const body = skipTagLead(inner).replace(/^with\s+/, "");
    const names: string[] = [];
    const re = /(?:^|,)\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*=/g;
    let m: RegExpExecArray | null;
    while ((m = re.exec(body)) !== null) names.push(m[1]);
    return names;
}

// Skip the leading whitespace-control marker and whitespace of a tag's inner text.
function skipTagLead(inner: string): string {
    return inner.replace(/^\s*[+-]?\s*/, "");
}

// Value kind of a set tag's right-hand expression. Literals and a few known function calls.
function setExpressionKind(inner: string, parameters: InvocationParameters): InvocationValueKind {
    const eq = inner.indexOf("=");
    if (eq < 0) return "unknown";
    const expr = inner.slice(eq + 1).trim();
    if (/^(["']).*\1$/.test(expr)) return "string";
    if (/^-?\d+$/.test(expr)) return "number";
    if (/^-?\d+\.\d+$/.test(expr)) return "number";
    if (/^(true|false|True|False)$/.test(expr)) return "boolean";
    if (/^namespace\s*\(/.test(expr)) return "namespace";
    if (/^cycler\s*\(/.test(expr)) return "cycler";
    if (/^joiner\s*\(/.test(expr)) return "joiner";
    if (/^dict\s*\(/.test(expr)) return "dict";
    if (/^range\s*\(/.test(expr)) return "list";
    const ref = /^([a-zA-Z_][a-zA-Z0-9_]*)/.exec(expr);
    if (ref) {
        const p = parameters.find(p => p.name === ref[0]);
        if (p) return invocationParameterKind(p.param) ?? "unknown";
    }
    return "unknown";
}

// A lexical block opened by a statement tag and closed by its end tag.
interface ScopeBlock {
    keyword: string;
    // Names bound inside the block.
    bound: InvocationScopeEntry[];
    // Whether `loop` is in scope inside the block.
    hasLoop: boolean;
}

// The end tag word for each block-opening keyword.
const blockEndWord: Record<string, string> = {
    "for": "endfor",
    "if": "endif",
    "macro": "endmacro",
    "with": "endwith",
    "filter": "endfilter",
    "autoescape": "endautoescape",
    "raw": "endraw",
};

// The end tag words ("endfor", ...) that close a block.
const endTagWords: Set<string> = new Set(Object.values(blockEndWord));

// Compute the names in scope at `offset`.
export function invocationScopeAt(
    text: string,
    offset: number,
    parameters: InvocationParameters,
): InvocationScopeEntry[] {
    const tags = invocationFindTags(text);

    // Base scope: application parameters, the ucloud object, and global functions.
    const base: InvocationScopeEntry[] = [];
    for (const {name, param} of parameters) {
        const kind = invocationParameterKind(param);
        if (kind == null) continue;
        base.push({
            name,
            kind,
            description: param.title || param.description || "Application parameter.",
            isParameter: true,
        });
    }
    base.push({
        name: "ucloud",
        kind: "dict",
        description: "UCloud runtime information (job, machine, application).",
    });
    for (const fn of invocationFunctions) {
        base.push({name: fn.name, kind: "function", description: fn.description, signature: fn.signature});
    }

    // Walk statement tags before the offset. Blocks push/pop a stack; sets add bindings.
    // `entries` holds names bound at top level (parameters, ucloud, functions, macros, sets).
    // gonja runs for/macro/with/filter/autoescape bodies in a sub-context, so names set inside
    // those blocks live only in `block.bound` and die at the end tag. if-bodies render in the
    // enclosing context, so a set inside an if persists past the endif.
    const stack: ScopeBlock[] = [];
    const entries: InvocationScopeEntry[] = [...base];

    for (const tag of tags) {
        if (tag.type !== "statement") continue;
        if (tag.start > offset) break;
        const word = statementWord(tag.inner);
        if (!word) continue;

        if (word === "for") {
            const bound: InvocationScopeEntry[] = parseForTargets(tag.inner).map(t => ({
                name: t,
                kind: "unknown" as InvocationValueKind,
                description: "Loop variable.",
            }));
            stack.push({keyword: "for", bound, hasLoop: true});
        } else if (word === "macro") {
            const decl = parseMacroDecl(tag.inner);
            if (decl) {
                // The macro name is a function visible from the declaration onward. At top level
                // it outlives the macro body; nested inside another block it dies with it.
                const entry: InvocationScopeEntry = {
                    name: decl.name,
                    kind: "macro",
                    description: "Macro defined in this template.",
                    signature: `${decl.name}(${decl.params.join(", ")})`,
                };
                const enclosing = stack[stack.length - 1];
                if (enclosing) {
                    enclosing.bound.push(entry);
                } else {
                    entries.push(entry);
                }
                stack.push({
                    keyword: "macro",
                    bound: decl.params.map(p => ({
                        name: p,
                        kind: "unknown" as InvocationValueKind,
                        description: "Macro parameter.",
                    })),
                    hasLoop: false,
                });
            }
        } else if (word === "with") {
            stack.push({
                keyword: "with",
                bound: parseWithNames(tag.inner).map(n => ({
                    name: n,
                    kind: "unknown" as InvocationValueKind,
                    description: "With variable.",
                })),
                hasLoop: false,
            });
        } else if (word === "set") {
            const target = parseSetTarget(tag.inner);
            if (target && !target.isAttribute) {
                const entry: InvocationScopeEntry = {
                    name: target.name,
                    kind: setExpressionKind(tag.inner, parameters),
                    description: "Set in the template.",
                };
                const enclosing = stack[stack.length - 1];
                if (enclosing) {
                    enclosing.bound.push(entry);
                } else {
                    entries.push(entry);
                }
            }
        } else if (word === "filter" || word === "autoescape") {
            stack.push({keyword: word, bound: [], hasLoop: false});
        } else if (endTagWords.has(word)) {
            // Pop to the matching opener; stray end tags (matching nothing) are ignored here.
            // The linter reports those.
            for (let i = stack.length - 1; i >= 0; i--) {
                if (blockEndWord[stack[i].keyword] === word) {
                    stack.length = i;
                    break;
                }
            }
        }
        // if/elif/else do not bind and do not close the enclosing block.
    }

    // Names from blocks still open at the offset (they enclose it).
    for (const block of stack) {
        entries.push(...block.bound);
        if (block.hasLoop) {
            entries.push({name: "loop", kind: "unknown", description: "Loop information."});
        }
    }
    return entries;
}

// Members offered after `.` on a root name, or null when the members cannot be listed (unknown
// kind). An empty array means the kind is known but has no members.
export function invocationResolveMembers(
    entries: InvocationScopeEntry[],
    path: string[],
): InvocationMemberEntry[] | null {
    if (path.length === 0) return [];
    const root = entries.find(e => e.name === path[0]);
    if (!root) return null;

    // The ucloud tree has its own static member list.
    if (root.name === "ucloud") {
        if (path.length === 1) return ucloudObject.map(ucloudMemberToEntry);
        let level: InvocationUcloudMember[] | undefined = ucloudObject;
        for (let i = 1; i < path.length; i++) {
            const next = level?.find(c => c.name === path[i]);
            if (!next) return null;
            if (i === path.length - 1) {
                if (next.children) return next.children.map(ucloudMemberToEntry);
                return membersToEntries(next.kind);
            }
            level = next.children;
        }
        return null;
    }

    // The loop object inside a for-block.
    if (root.name === "loop") {
        if (path.length === 1) {
            return [
                ...loopMembers.attributes.map(a => ({
                    name: a,
                    kind: "number" as InvocationValueKind,
                    description: "Loop attribute.",
                })),
                ...loopMembers.methods.map(m => ({
                    name: m.name,
                    kind: "function" as InvocationValueKind,
                    description: m.description,
                    signature: m.signature,
                    isMethod: true,
                })),
            ];
        }
        return null;
    }

    // Members by value kind. Deeper paths (methods on methods) are not resolved.
    if (path.length > 1) return null;
    return membersToEntries(root.kind);
}

function membersToEntries(kind: InvocationValueKind): InvocationMemberEntry[] | null {
    const members = invocationMembers[kind];
    if (!members) return null;
    return [
        ...members.attributes.map(a => ({
            name: a,
            kind: "unknown" as InvocationValueKind,
            description: `Attribute of ${kind}.`,
        })),
        ...members.methods.map(m => ({
            name: m.name,
            kind: "function" as InvocationValueKind,
            description: m.description,
            signature: m.signature,
            isMethod: true,
        })),
    ];
}

function ucloudMemberToEntry(m: InvocationUcloudMember): InvocationMemberEntry {
    return {name: m.name, kind: m.kind, description: m.description};
}

// Whether a dotted path like ["ucloud", "machine", "name"] resolves to something known. Used by
// the linter for unknown-member errors. `childrenKnown` is false when we cannot say anything
// about the children of the last path segment (e.g. kind "unknown").
export function invocationMemberExists(
    entries: InvocationScopeEntry[],
    path: string[],
): {exists: boolean; childrenKnown: boolean} {
    if (path.length === 0) return {exists: false, childrenKnown: false};
    const root = entries.find(e => e.name === path[0]);
    if (!root) return {exists: false, childrenKnown: false};
    if (path.length === 1) return {exists: true, childrenKnown: true};

    // ucloud and loop have enumerable members.
    if (root.name === "ucloud") {
        let level: InvocationUcloudMember[] | undefined = ucloudObject;
        let current: InvocationUcloudMember | undefined;
        for (let i = 1; i < path.length; i++) {
            current = level?.find(c => c.name === path[i]);
            if (!current) return {exists: false, childrenKnown: false};
            level = current.children;
        }
        return {exists: true, childrenKnown: current?.children != null};
    }
    if (root.name === "loop") {
        if (path.length > 2) return {exists: false, childrenKnown: false};
        const known =
            loopMembers.attributes.includes(path[1]) ||
            loopMembers.methods.some(m => m.name === path[1]);
        return {exists: known, childrenKnown: known};
    }

    // Namespaces accept arbitrary attributes (that is their purpose), so any member is allowed.
    if (root.kind === "namespace") return {exists: true, childrenKnown: false};

    // Other kinds: check method/attribute membership for a single hop.
    if (path.length > 2) return {exists: false, childrenKnown: false};
    const members = invocationMembers[root.kind];
    if (!members) return {exists: true, childrenKnown: false};
    const known =
        members.attributes.includes(path[1]) ||
        members.methods.some(m => m.name === path[1]);
    return {exists: known, childrenKnown: known};
}

// Expression tokenizer
// -------------------------------------------------------------------------------------------------------------------
// A small tokenizer over tag inner text. It splits an expression into identifiers, operators,
// strings, and numbers with offsets relative to the whole template. The completion provider uses
// it to find the partial word at the cursor; the linter uses it to find identifier references.
// Strings are consumed whole so identifiers inside strings do not produce references.

export interface ExprToken {
    type: "identifier" | "operator" | "number" | "string" | "call" | "unknown";
    text: string;
    // Offset of the token's first character within the tag inner text.
    start: number;
    end: number;
}

export function tokenizeExpression(expr: string): ExprToken[] {
    const tokens: ExprToken[] = [];
    let i = 0;
    const n = expr.length;
    while (i < n) {
        const c = expr[i];
        if (/\s/.test(c)) {
            i++;
            continue;
        }
        if (/[a-zA-Z_]/.test(c)) {
            const m = /^[a-zA-Z_][a-zA-Z0-9_]*/.exec(expr.slice(i));
            const text = m ? m[0] : c;
            tokens.push({type: "identifier", text, start: i, end: i + text.length});
            i += text.length;
            continue;
        }
        if (/[0-9]/.test(c)) {
            const m = /^\d+(\.\d+)?/.exec(expr.slice(i));
            const text = m ? m[0] : c;
            tokens.push({type: "number", text, start: i, end: i + text.length});
            i += text.length;
            continue;
        }
        if (c === '"' || c === "'") {
            let j = i + 1;
            while (j < n && expr[j] !== c) {
                if (expr[j] === "\\") j++;
                j++;
            }
            const end = Math.min(j + 1, n);
            tokens.push({type: "string", text: expr.slice(i, end), start: i, end});
            i = end;
            continue;
        }
        if ("~=<>!+-*/%|,.[]()".includes(c)) {
            const two = expr.slice(i, i + 2);
            if (["==", "!=", "<=", ">=", "//", "**"].includes(two)) {
                tokens.push({type: "operator", text: two, start: i, end: i + 2});
                i += 2;
                continue;
            }
            tokens.push({type: "operator", text: c, start: i, end: i + 1});
            i++;
            continue;
        }
        tokens.push({type: "unknown", text: c, start: i, end: i + 1});
        i++;
    }
    return tokens;
}

