// Draft operations
// =====================================================================================================================
// Pure functions that update the CreatorDraft for each editor action. Each function takes the
// current draft and returns a new draft with the change applied. The caller marks the draft dirty.
//
// These operations own the stable-id bookkeeping. Renaming updates the parameterIds key but keeps
// the same value. Deletion removes the id. Reorder changes only parametersOrder.
//
// On rename, exact static references in the invocation are rewritten. On delete, references remain
// unchanged because there is no safe replacement value.

import {A2Yaml, A2Parameter, A2EnumOption} from "@/Applications/Creator/Draft";
import {CreatorDraft, CreatorCustomMeta, CreatorValidationError, CreatorValidationState, assignParameterId, creatorStableId, emptyValidationState} from "@/Applications/Creator/Draft";
import {
    invocationFindTags,
    tokenizeExpression,
    ExprToken,
} from "@/Applications/Creator/InvocationScope";
import type {AppCatalogCustomGroup} from "@/Applications/AppStoreApi";

export function draftUpdateBase(
    draft: CreatorDraft,
    parameterName: string,
    patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: {...param, ...patch},
        },
    };
    return clearValidation({...draft, application});
}

export function draftRenameParameter(
    draft: CreatorDraft,
    oldName: string,
    newName: string,
): CreatorDraft {
    const param = draft.application.parameters[oldName];
    if (!param || oldName === newName) return draft;

    if (!newName || !isValidParameterName(newName)) return draft;

    if (draft.application.parameters[newName] != null) return draft;

    const order = draft.application.parametersOrder.map(n => (n === oldName ? newName : n));
    const parameters: Record<string, A2Parameter> = {};
    for (const n of order) {
        if (n === newName) {
            parameters[n] = param;
        } else {
            parameters[n] = draft.application.parameters[n];
        }
    }

    const invocation = rewriteInvocationReferences(
        draft.application.invocation ?? "",
        oldName,
        newName,
    );

    const parameterIds: Record<string, string> = {};
    for (const n of order) {
        if (n === newName) {
            assignParameterId(parameterIds, newName, draft.parameterIds[oldName] ?? creatorStableId());
        } else {
            assignParameterId(parameterIds, n, draft.parameterIds[n] ?? creatorStableId());
        }
    }

    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder: order,
        invocation,
    };

    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection: {
            parameterId: draft.selection.parameterId,
            parameterName: newName,
        },
    });
}

export function draftDeleteParameter(
    draft: CreatorDraft,
    parameterName: string,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;

    const order = draft.application.parametersOrder.filter(n => n !== parameterName);
    const parameters: Record<string, A2Parameter> = {...draft.application.parameters};
    delete parameters[parameterName];

    const parameterIds: Record<string, string> = {};
    for (const n of order) {
        assignParameterId(parameterIds, n, draft.parameterIds[n] ?? creatorStableId());
    }

    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder: order,
    };

    const selection = draft.selection.parameterId === (draft.parameterIds[parameterName] ?? "")
        ? {parameterId: null, parameterName: null}
        : draft.selection;

    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection,
    });
}

export function draftReorderParameters(
    draft: CreatorDraft,
    newOrder: string[],
): CreatorDraft {
    const application: A2Yaml = {
        ...draft.application,
        parametersOrder: newOrder,
    };
    return clearValidation({...draft, application});
}

export function draftAddParameter(
    draft: CreatorDraft,
    type: A2WidgetType,
): CreatorDraft {
    const name = uniqueWidgetName(type, draft.application.parametersOrder);
    const param = createWidgetParameter(type);
    const parameters: Record<string, A2Parameter> = {
        ...draft.application.parameters,
        [name]: param,
    };
    const parametersOrder = [...draft.application.parametersOrder, name];
    const parameterIds = {...draft.parameterIds};
    assignParameterId(parameterIds, name, creatorStableId());
    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder,
    };
    const newId = parameterIds[name];
    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection: {parameterId: newId, parameterName: name},
    });
}

export function draftUpdateDefaultValue(
    draft: CreatorDraft,
    parameterName: string,
    defaultValue: string | number | boolean | null,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    if (!("defaultValue" in param)) return draft;
    const updated = {...param, defaultValue} as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

export function draftUpdateNumericField(
    draft: CreatorDraft,
    parameterName: string,
    patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    if (param.type !== "Integer" && param.type !== "FloatingPoint") return draft;
    const updated: A2Parameter = {
        ...param,
        ...patch,
    } as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

export function draftUpdateEnumeration(
    draft: CreatorDraft,
    parameterName: string,
    patch: { options?: A2EnumOption[]; defaultValue?: string | null },
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param || param.type !== "Enumeration") return draft;
    const options = patch.options ?? param.options;
    const defaultValue = "defaultValue" in patch ? patch.defaultValue ?? null : param.defaultValue;
    const updated: A2Parameter = {
        ...param,
        options,
        defaultValue,
    } as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

export function draftSelectParameter(draft: CreatorDraft, parameterId: string | null): CreatorDraft {
    if (parameterId == null) {
        return {...draft, selection: {parameterId: null, parameterName: null}};
    }
    const name = nameForId(draft, parameterId);
    return {
        ...draft,
        selection: {
            parameterId: name ? parameterId : null,
            parameterName: name,
        },
    };
}

// Metadata operations
// -------------------------------------------------------------------------------------------------------------------
// These functions update metadata fields on the A2Yaml or the customMeta. They follow the same
// pattern as the parameter operations: take the draft and clear validation after the change.

export function draftUpdateMetadata(
    draft: CreatorDraft,
    patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>,
): CreatorDraft {
    const application: A2Yaml = {...draft.application, ...patch};
    if (application.title === "") application.title = null;
    if (application.description === "") application.description = null;
    if (application.license === "") application.license = null;
    if (application.documentation === "") application.documentation = null;
    return clearValidation({...draft, application});
}

export function draftUpdateSoftware(draft: CreatorDraft, software: A2Yaml["software"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, software};
    return clearValidation({...draft, application});
}

export function draftUpdateFeatures(draft: CreatorDraft, features: A2Yaml["features"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, features};
    return clearValidation({...draft, application});
}

export function draftUpdateWeb(draft: CreatorDraft, web: A2Yaml["web"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, web};
    return clearValidation({...draft, application});
}

export function draftUpdateVnc(draft: CreatorDraft, vnc: A2Yaml["vnc"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, vnc};
    return clearValidation({...draft, application});
}

export function draftUpdateSsh(draft: CreatorDraft, ssh: A2Yaml["ssh"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ssh};
    return clearValidation({...draft, application});
}

export function draftUpdateInference(draft: CreatorDraft, inference: A2Yaml["inference"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, inference};
    return clearValidation({...draft, application});
}

export function draftUpdateModules(draft: CreatorDraft, modules: A2Yaml["modules"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, modules};
    return clearValidation({...draft, application});
}

export function draftUpdateUcx(draft: CreatorDraft, ucx: A2Yaml["ucx"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ucx};
    return clearValidation({...draft, application});
}

export function draftUpdateExtensions(draft: CreatorDraft, extensions: string[]): CreatorDraft {
    const application: A2Yaml = {...draft.application, extensions};
    return clearValidation({...draft, application});
}

export function draftUpdateEnvironment(draft: CreatorDraft, environment: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, environment};
    return clearValidation({...draft, application});
}

export function draftUpdateSbatch(draft: CreatorDraft, sbatch: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, sbatch};
    return clearValidation({...draft, application});
}

export function draftUpdateCustomMeta(
    draft: CreatorDraft,
    patch: Partial<CreatorCustomMeta>,
): CreatorDraft {
    if (!draft.customMeta) return draft;
    const customMeta = {...draft.customMeta, ...patch};
    let application = draft.application;
    if ((patch.group !== undefined || patch.flavor !== undefined) && !draft.nameManuallySet) {
        const group = draftCustomSelectedGroup(customMeta, draft.placementGroups, draft.placementCreatedGroup);
        const name = draftCustomDerivedName(customMeta, group);
        if (name !== "") application = {...application, name};
    }
    return {...draft, customMeta, application, validation: emptyValidationState()};
}

// Derived name for custom applications
// -------------------------------------------------------------------------------------------------------------------
// The application name is derived from the group title and the flavor name. Both parts are
// lower-kebab-cased. The default flavor adds no suffix; any other flavor appends "-$flavor".

const draftCustomDefaultFlavor = "default";

export function creatorKebabCase(value: string): string {
    return value
        .normalize("NFKD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-zA-Z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .toLowerCase();
}

export function draftCustomDerivedName(
    customMeta: {flavor: string},
    group: {title: string} | null,
): string {
    const groupPart = creatorKebabCase(group?.title ?? "");
    const flavor = customMeta.flavor.trim();
    const suffix = flavor === "" || flavor.toLowerCase() === draftCustomDefaultFlavor
        ? ""
        : `-${creatorKebabCase(flavor)}`;
    return `${groupPart}${suffix}`;
}

// Derived presentation for custom applications
// -------------------------------------------------------------------------------------------------------------------
// Custom applications do not present their own title, description, license, or documentation.
// The presentation is derived from the placement metadata instead: the title joins the group
// title and the flavor name, the description is the group description, and license and
// documentation are absent. The derivation is applied live so the YAML view always shows the
// values that will be saved. Absent fields use null, never an empty string: the backend rejects
// a present-but-empty value.
export function draftCustomDerivedPresentation(
    application: A2Yaml,
    customMeta: {flavor: string} | null,
    group: {title: string; description: string} | null,
): A2Yaml {
    const flavor = customMeta?.flavor ?? "";
    const titleFlavor = flavor.trim().toLowerCase() === draftCustomDefaultFlavor ? "" : flavor;
    const titleParts = [group?.title ?? "", titleFlavor].filter(part => part.trim() !== "");
    const description = group?.description ?? "";
    return {
        ...application,
        title: titleParts.length === 0 ? null : titleParts.join(" "),
        description: description === "" ? null : description,
        license: null,
        documentation: null,
    };
}

export function draftCustomSelectedGroup(
    meta: {group: string},
    groups: AppCatalogCustomGroup[],
    createdGroup?: {id: number; title: string; description: string} | null,
): {title: string; description: string} | null {
    if (meta.group === "") return null;
    const selected = groups.find(group => String(group.id) === meta.group)
        ?? (createdGroup != null && String(createdGroup.id) === meta.group
            ? {specification: {title: createdGroup.title, description: createdGroup.description}}
            : null);
    return selected?.specification ?? null;
}

function clearValidation(draft: CreatorDraft): CreatorDraft {
    return {
        ...draft,
        validation: emptyValidationState(),
    };
}

export function nameForId(draft: CreatorDraft, parameterId: string): string | null {
    for (const name of draft.application.parametersOrder) {
        if (draft.parameterIds[name] === parameterId) return name;
    }
    return null;
}

// Widget drawer parameter defaults
// -------------------------------------------------------------------------------------------------------------------
// The widget drawer appends one parameter per click. Each new parameter needs a valid default
// with a unique name. The name starts with a short type-based identifier and appends a number when
// the base name is already in use: "text", "text2", "text3".
//
// Enumerations start with no options. The new row is selected so the user can enter the required
// options immediately. We do not add placeholder enum options that could be mistaken for user data.
//
// Workflow is a valid A2 type but does not appear in the first widget drawer. It is excluded from
// the drawer definitions.

export type WidgetDrawerGroup = "basic" | "resources";

export interface WidgetDrawerItem {
    type: A2WidgetType;
    label: string;
    description: string;
    group: WidgetDrawerGroup;
}

export type A2WidgetType =
    | "Text"
    | "TextArea"
    | "Boolean"
    | "Integer"
    | "FloatingPoint"
    | "Enumeration"
    | "File"
    | "Directory"
    | "License"
    | "PublicIP";

export const WIDGET_DRAWER_ITEMS: WidgetDrawerItem[] = [
    {type: "Text", label: "Text", description: "A short single-line text value.", group: "basic"},
    {type: "TextArea", label: "Text area", description: "A multi-line text value.", group: "basic"},
    {type: "Boolean", label: "Boolean", description: "A true or false toggle.", group: "basic"},
    {type: "Integer", label: "Integer", description: "A whole number with optional range.", group: "basic"},
    {type: "FloatingPoint", label: "Floating point", description: "A decimal number with optional range.", group: "basic"},
    {type: "Enumeration", label: "Enumeration", description: "A fixed list of named options.", group: "basic"},
    {type: "File", label: "File", description: "An input file from UCloud storage.", group: "resources"},
    {type: "Directory", label: "Directory", description: "An input directory from UCloud storage.", group: "resources"},
    {type: "License", label: "License server", description: "A license server the job can use.", group: "resources"},
    {type: "PublicIP", label: "Public IP", description: "A public IP address for the job.", group: "resources"},
];

export const WIDGET_BASE_NAMES: Record<A2WidgetType, string> = {
    Text: "text",
    TextArea: "textArea",
    Boolean: "boolean",
    Integer: "integer",
    FloatingPoint: "floatingPoint",
    Enumeration: "enumeration",
    File: "file",
    Directory: "directory",
    License: "license",
    PublicIP: "publicIp",
};

export const WIDGET_DEFAULT_TITLES: Record<A2WidgetType, string> = {
    Text: "Text",
    TextArea: "Text area",
    Boolean: "Boolean",
    Integer: "Integer",
    FloatingPoint: "Floating point",
    Enumeration: "Enumeration",
    File: "File",
    Directory: "Directory",
    License: "License",
    PublicIP: "Public IP",
};

export function uniqueWidgetName(type: A2WidgetType, existingNames: string[]): string {
    const base = WIDGET_BASE_NAMES[type];
    const taken = new Set(existingNames);
    if (!taken.has(base)) return base;
    let n = 2;
    while (taken.has(`${base}${n}`)) n++;
    return `${base}${n}`;
}

export function createWidgetParameter(type: A2WidgetType): A2Parameter {
    const title = WIDGET_DEFAULT_TITLES[type];
    const common = {title, description: "", optional: true};
    switch (type) {
        case "Text":
            return {...common, type: "Text", defaultValue: null};
        case "TextArea":
            return {...common, type: "TextArea", defaultValue: null};
        case "Boolean":
            return {...common, type: "Boolean", defaultValue: false};
        case "Integer":
            return {...common, type: "Integer", defaultValue: 0, min: null, max: null, step: null};
        case "FloatingPoint":
            return {...common, type: "FloatingPoint", defaultValue: 0, min: null, max: null, step: null};
        case "Enumeration":
            return {...common, type: "Enumeration", options: [] as A2EnumOption[], defaultValue: null};
        case "File":
            return {...common, type: "File"};
        case "Directory":
            return {...common, type: "Directory"};
        case "License":
            return {...common, type: "License"};
        case "PublicIP":
            return {...common, type: "PublicIP"};
    }
}

// Invocation reference tracking
// -------------------------------------------------------------------------------------------------------------------
// On rename, the editor updates references to the old parameter name in the Jinja invocation
// template. On delete, references remain unchanged because there is no safe replacement value.
//
// The rewrite covers the `invocation` field only. It is token-based and uses the same tag scanner
// and expression tokenizer as the completion provider and the linter (InvocationScope), so it
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

// Finds the start offset of the end tag that closes the block opened by `openTag`. Nesting
// matters: for `{% for p in seq %}...{% for x in s2 %}...{% endfor %}...{% endfor %}` the outer
// `for` closes at the second `endfor`, not the first. The scan keeps a depth counter of same-key
// blocks; `if` blocks are transparent because they do not open a scope for bindings.
function blockEnd(tags: ReturnType<typeof invocationFindTags>, openTag: {start: number}, endWord: string): number {
    const opener = endWord.replace(/^end/, "");
    let depth = 1;
    for (const tag of tags) {
        if (tag.type !== "statement") continue;
        if (tag.start <= openTag.start) continue;
        const word = statementWord(tag.inner);
        if (word === opener) {
            depth++;
            continue;
        }
        if (word === endWord) {
            depth--;
            if (depth === 0) return tag.start;
        }
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

// Local parameter validation
// -------------------------------------------------------------------------------------------------------------------
// The editor runs this local validation when preview or save is requested. It does not call
// backend validation. The validation covers: empty names, duplicate names, invalid names,
// numeric ranges, enumeration option duplicates, and enumeration defaults.
//
// The validation returns a list of errors. Each error has a parameter name (or null for a
// global error) and a message. The editor shows field errors in the parameter panel and summary
// errors at the top of the page.

export function validateApplicationLocal(application: A2Yaml): CreatorValidationState {
    const errors: CreatorValidationError[] = [];

    const seenNames = new Set<string>();
    const names = application.parametersOrder;

    for (const name of names) {
        const param = application.parameters[name];
        if (!param) continue;

        if (!name || name.trim() === "") {
            errors.push({parameterName: name, message: "Parameter name must not be empty."});
            continue;
        }

        const nameError = parameterNameIssue(name);
        if (nameError != null) {
            errors.push({parameterName: name, message: nameError});
        }

        if (seenNames.has(name)) {
            errors.push({parameterName: name, message: `Duplicate parameter name: "${name}".`});
        }
        seenNames.add(name);

        switch (param.type) {
            case "Integer":
            case "FloatingPoint":
                validateNumeric(param, name, errors);
                break;
            case "Enumeration":
                validateEnumeration(param, name, errors);
                break;
        }
    }

    return {errors};
}

const parameterNamePattern = /^[a-zA-Z_][a-zA-Z0-9_]*$/;

// Reports why a parameter name cannot be used. Shared by the local validator (documents that
// arrive through the YAML view) and the rename field (immediate feedback on commit).
export function parameterRenameIssue(name: string): string | null {
    if (!name) {
        return "Parameter name must not be empty.";
    }
    if (!parameterNamePattern.test(name)) {
        return "Parameter name must be a Jinja identifier: letters, digits, and underscores, starting with a letter.";
    }
    if (invocationReservedWords.has(name)) {
        return `"${name}" is a reserved word and cannot be used as a parameter name.`;
    }
    if (name === "__proto__" || Object.prototype.hasOwnProperty.call(Object.prototype, name)) {
        return `"${name}" conflicts with a built-in property name and cannot be used as a parameter name.`;
    }
    return null;
}

function parameterNameIssue(name: string): string | null {
    return parameterRenameIssue(name);
}

// Used by draftRenameParameter to reject names the stable-id bookkeeping and the invocation
// rewriter cannot handle before the change reaches the model. The same rule is reported as a
// validation error by parameterNameIssue for documents that arrive through the YAML view.
function isValidParameterName(name: string): boolean {
    return parameterNamePattern.test(name) && !invocationReservedWords.has(name)
        && name !== "__proto__" && !Object.prototype.hasOwnProperty.call(Object.prototype, name);
}

const invocationReservedWords = new Set([
    "and", "or", "not", "in", "is", "if", "else", "true", "false", "True", "False", "none", "None",
    "recursive", "loop",
]);

function validateNumeric(
    param: { type: string; min?: number | null; max?: number | null; step?: number | null; defaultValue?: number | null },
    name: string,
    errors: CreatorValidationError[],
): void {
    // YAML allows .inf, -.inf and .nan, which parse to Infinity/-Infinity/NaN. Treat them as
    // missing rather than letting them bypass the range comparisons below.
    const finiteOr = (value: number | null | undefined): number | null =>
        value == null || !Number.isFinite(value) ? null : value;
    const min = finiteOr(param.min);
    const max = finiteOr(param.max);
    const step = finiteOr(param.step);
    if (param.min != null && !Number.isFinite(param.min)) {
        errors.push({parameterName: name, message: "Minimum must be a finite number."});
    }
    if (param.max != null && !Number.isFinite(param.max)) {
        errors.push({parameterName: name, message: "Maximum must be a finite number."});
    }
    if (param.step != null && !Number.isFinite(param.step)) {
        errors.push({parameterName: name, message: "Step must be a finite number."});
    }
    if (min != null && max != null && min > max) {
        errors.push({parameterName: name, message: "Minimum must not be greater than maximum."});
    }
    if (step != null && step <= 0) {
        errors.push({parameterName: name, message: "Step must be greater than zero."});
    }
    const def = finiteOr(param.defaultValue);
    if (param.defaultValue != null && !Number.isFinite(param.defaultValue)) {
        errors.push({parameterName: name, message: "Default value must be a finite number."});
    }
    if (def != null) {
        if (min != null && def < min) {
            errors.push({parameterName: name, message: "Default value is below the minimum."});
        }
        if (max != null && def > max) {
            errors.push({parameterName: name, message: "Default value is above the maximum."});
        }
    }
}

function validateEnumeration(
    param: { type: "Enumeration"; options: { title: string; value: string }[]; defaultValue?: string | null },
    name: string,
    errors: CreatorValidationError[],
): void {
    if (param.options.length === 0) {
        errors.push({parameterName: name, message: "Enumeration must have at least one option."});
        return;
    }
    const seenValues = new Set<string>();
    for (const opt of param.options) {
        if (seenValues.has(opt.value)) {
            errors.push({parameterName: name, message: `Duplicate enumeration value: "${opt.value}".`});
        }
        seenValues.add(opt.value);
    }
    const def = param.defaultValue;
    if (def != null && def !== "" && !seenValues.has(def)) {
        errors.push({
            parameterName: name,
            message: "Default value is not present in the option list.",
        });
    }
}
