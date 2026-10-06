import * as React from "react";
import {useMemo, useState} from "react";
import type {JSONSchema} from "monaco-yaml";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import {injectStyle} from "@/Unstyled";
import ExternalLink from "@/ui-components/ExternalLink";
import Input from "@/ui-components/Input";
import {fixEditorMarkdown} from "./MarkdownFixers";

type SchemaNode = JSONSchema | boolean;
type SchemaField = {name: string; schema: SchemaNode; required: boolean};

function resolveSchema(root: JSONSchema, node: SchemaNode, seen = new Set<string>()): SchemaNode {
    if (typeof node === "boolean" || !node.$ref || seen.has(node.$ref)) return node;
    const reference = node.$ref;
    let target: unknown = root;
    if (reference !== "#") {
        if (!reference.startsWith("#/")) return node;
        let pointer: string;
        try {
            pointer = decodeURIComponent(reference.slice(2));
        } catch {
            return node;
        }
        for (const segment of pointer.split("/")) {
            if (!target || typeof target !== "object") return node;
            target = (target as Record<string, unknown>)[segment.replace(/~1/g, "/").replace(/~0/g, "~")];
        }
    }
    if (typeof target !== "boolean" && (!target || typeof target !== "object" || Array.isArray(target))) return node;
    const resolved = resolveSchema(root, target as SchemaNode, new Set([...seen, reference]));
    if (typeof resolved === "boolean") return resolved;
    const {$ref: _, ...siblings} = node;
    return {...resolved, ...siblings};
}

function schemaReferenceResolve(root: JSONSchema, node: SchemaNode, ancestors = new Set<SchemaNode>()): SchemaNode {
    if (ancestors.has(node)) return node;
    const resolved = resolveSchema(root, node);
    if (typeof resolved === "boolean" || !resolved.allOf?.length) return resolved;
    const next = new Set([...ancestors, node]);
    const parts = resolved.allOf.map(part => schemaReferenceResolve(root, part, next));
    const objects = parts.filter((part): part is JSONSchema => typeof part !== "boolean");
    if (objects.length !== parts.length) return resolved;
    const multipleObjects = objects.length > 1;
    const canFlatten = objects.every(part => {
        if (part.allOf || part.$ref) return false;
        if (multipleObjects && (part.oneOf || part.anyOf)) return false;
        return !multipleObjects || schemaType(part) === "object";
    });
    if (!canFlatten) return resolved;
    const types = new Set([resolved, ...objects].map(part => schemaType(part)).filter(type => type !== "any"));
    if (types.size > 1) return resolved;
    const {allOf: _, ...outer} = resolved;
    let merged: JSONSchema = {};
    for (const part of [...objects, outer]) {
        const properties = {...merged.properties};
        for (const [name, schema] of Object.entries(part.properties ?? {})) {
            const previous = properties[name];
            properties[name] = previous === undefined ? schema : {allOf: [previous, schema]};
        }
        const patternProperties = {...merged.patternProperties};
        for (const [pattern, schema] of Object.entries(part.patternProperties ?? {})) {
            const previous = patternProperties[pattern];
            patternProperties[pattern] = previous === undefined ? schema : {allOf: [previous, schema]};
        }
        const required = Array.from(new Set([...(merged.required ?? []), ...(part.required ?? [])]));
        merged = {...merged, ...part};
        if (Object.keys(properties).length > 0) merged.properties = properties;
        if (Object.keys(patternProperties).length > 0) merged.patternProperties = patternProperties;
        if (required.length > 0) merged.required = required;
    }
    if (multipleObjects) merged.type = "object";
    return merged;
}

function schemaFields(node: SchemaNode): SchemaField[] {
    if (typeof node === "boolean") return [];
    const fields: SchemaField[] = Object.entries(node.properties ?? {}).map(([name, schema]) => ({
        name,
        schema,
        required: node.required?.includes(name) ?? false,
    }));
    for (const [pattern, schema] of Object.entries(node.patternProperties ?? {})) {
        fields.push({name: `Keys matching ${pattern}`, schema, required: false});
    }
    if (node.additionalProperties && typeof node.additionalProperties === "object") {
        fields.push({name: "Additional keys", schema: node.additionalProperties, required: false});
    }
    if (Array.isArray(node.items)) {
        node.items.forEach((schema, index) => fields.push({name: `[${index}]`, schema, required: false}));
    } else if (node.items !== undefined) {
        fields.push({name: "[]", schema: node.items, required: false});
    }
    for (const [keyword, label] of [["allOf", "All of"], ["oneOf", "One of"], ["anyOf", "Any of"]] as const) {
        node[keyword]?.forEach((schema, index) => fields.push({name: `${label}: option ${index + 1}`, schema, required: false}));
    }
    return fields;
}

function schemaType(node: SchemaNode): string {
    if (typeof node === "boolean") return node ? "any" : "not allowed";
    if (Array.isArray(node.type)) return node.type.join(" | ");
    if (node.type) return node.type;
    if (node.properties || node.additionalProperties) return "object";
    if (node.items) return "array";
    if (node.oneOf || node.anyOf) return "union";
    return node.$ref ? "reference" : "any";
}

function SchemaDescription({schema, required = false, markdownFixer}: {schema: SchemaNode; required?: boolean; markdownFixer?: string}) {
    if (typeof schema === "boolean") return null;
    const emptyObjectDefault = schema.default !== null && typeof schema.default === "object"
        && !Array.isArray(schema.default) && Object.keys(schema.default).length === 0;
    const showDefault = schema.default !== undefined && !(required && emptyObjectDefault);
    const description = fixEditorMarkdown(schema.markdownDescription ?? schema.description ?? "", markdownFixer);
    return <>
        {description ? <div className="schema-description">
            <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                skipHtml
                allowedElements={["h1", "h2", "h3", "h4", "h5", "h6", "br", "a", "p", "strong", "em", "del", "ul", "ol", "li", "pre", "code", "blockquote", "hr", "table", "thead", "tbody", "tr", "th", "td"]}
                components={{a: props => <ExternalLink href={props.href} title={props.title}>{props.children}</ExternalLink>}}
            >{description}</ReactMarkdown>
        </div> : null}
        {schema.enum ? <p>Allowed values: <code>{schema.enum.map(value => JSON.stringify(value)).join(", ")}</code></p> : null}
        {schema.const !== undefined ? <p>Value: <code>{JSON.stringify(schema.const)}</code></p> : null}
        {showDefault ? <p>Default: <code>{JSON.stringify(schema.default)}</code></p> : null}
        {schema.format ? <p>Format: <code>{schema.format}</code></p> : null}
        {schema.$ref ? <p>Reference: <code>{schema.$ref}</code></p> : null}
    </>;
}

function SchemaReferenceField({root, field, markdownFixer}: {root: JSONSchema; field: SchemaField; markdownFixer?: string}) {
    const [open, setOpen] = useState(false);
    const schema = useMemo(() => schemaReferenceResolve(root, field.schema), [root, field.schema]);
    const constant = typeof schema === "boolean" ? undefined : (
        schema.const !== undefined ? schema.const : (schema.enum?.length === 1 ? schema.enum[0] : undefined)
    );
    if (typeof constant === "string") {
        return <div className="schema-constant" title={typeof schema === "boolean" ? undefined : schema.description}>
            <code>{field.name}</code> <code className="schema-type">{JSON.stringify(constant)}</code>
            {field.required ? <span className="schema-required">Required</span> : null}
        </div>;
    }
    return <details open={open} onToggle={event => setOpen(event.currentTarget.open)}>
        <summary>
            <code>{field.name}</code> <span className="schema-type">{schemaType(schema)}</span>
            {field.required ? <span className="schema-required">Required</span> : null}
        </summary>
        {open ? <div className="schema-field-content">
            {typeof schema !== "boolean" && schema.title ? <strong>{schema.title}</strong> : null}
            <SchemaDescription schema={schema} required={field.required} markdownFixer={markdownFixer} />
            {schemaFields(schema).map(child => <SchemaReferenceField key={child.name} root={root} field={child} markdownFixer={markdownFixer} />)}
        </div> : null}
    </details>;
}

function searchSchema(root: JSONSchema, query: string): SchemaField[] {
    const results: SchemaField[] = [];
    const visit = (field: SchemaField, path: string, ancestors: Set<SchemaNode>) => {
        const name = path ? `${path}.${field.name}` : field.name;
        if (name.toLowerCase().includes(query)) results.push({...field, name});
        if (ancestors.has(field.schema)) return;
        const schema = schemaReferenceResolve(root, field.schema);
        const next = new Set([...ancestors, field.schema]);
        for (const child of schemaFields(schema)) visit(child, name, next);
    };
    for (const field of schemaFields(schemaReferenceResolve(root, root))) visit(field, "", new Set([root]));
    return results;
}

export function SchemaReference({schema, markdownFixer}: {schema: JSONSchema; markdownFixer?: string}) {
    const [search, setSearch] = useState("");
    const root = useMemo(() => schemaReferenceResolve(schema, schema), [schema]);
    const query = search.trim().toLowerCase();
    const fields = useMemo(() => query ? searchSchema(schema, query) : schemaFields(root), [schema, root, query]);
    return <aside className={SchemaReferenceClass} aria-label="API reference">
        <header>
            <strong>API reference</strong>
            <label className="schema-search">
                <span className="schema-search-label">Search schema fields</span>
                <Input type="search" placeholder="Search fields…" value={search} onChange={event => setSearch(event.target.value)} />
            </label>
        </header>
        <div className="schema-reference-content">
            {typeof root !== "boolean" && root.title ? <h2>{root.title}</h2> : null}
            <SchemaDescription schema={root} markdownFixer={markdownFixer} />
            {fields.length === 0 ? <p>{query ? "No matching fields." : "This schema has no documented fields."}</p> : null}
            {fields.map(field => <SchemaReferenceField key={`${query}:${field.name}`} root={schema} field={field} markdownFixer={markdownFixer} />)}
        </div>
    </aside>;
}

const SchemaReferenceClass = injectStyle("editor-schema-reference", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-width: 0;
        font-size: 14px;
    }

    ${k} header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 24px;
        padding: 12px;
    }

    ${k} header > strong {
        flex-shrink: 0;
        white-space: nowrap;
    }

    ${k} .schema-search {
        flex: 1;
        min-width: 0;
    }

    ${k} .schema-search-label {
        position: absolute;
        width: 1px;
        height: 1px;
        overflow: hidden;
        clip-path: inset(50%);
        white-space: nowrap;
    }

    ${k} .schema-reference-content {
        flex: 1;
        min-height: 0;
        overflow: auto;
        padding: 0 12px 12px;
        overflow-wrap: anywhere;
    }

    ${k} h2 {
        font-size: 18px;
    }

    ${k} p {
        margin: 8px 0;
    }

    ${k} .schema-description {
        overflow-x: auto;
    }

    ${k} .schema-description pre {
        overflow-x: auto;
        padding: 8px;
        background: var(--backgroundDefault);
    }

    ${k} .schema-description table {
        border-collapse: collapse;
    }

    ${k} .schema-description th,
    ${k} .schema-description td {
        padding: 6px;
        border: 1px solid var(--borderColor);
    }

    ${k} details {
        border-top: 1px solid var(--borderColor);
    }

    ${k} .schema-constant {
        border-top: 1px solid var(--borderColor);
        padding: 12px 0;
    }

    ${k} summary {
        padding: 12px 0;
        cursor: pointer;
    }

    ${k} code {
        font-size: 12px;
    }

    ${k} .schema-type {
        color: var(--textSecondary);
        margin-left: 8px;
    }

    ${k} .schema-required {
        color: var(--textSecondary);
        font-size: 12px;
        margin-left: 8px;
    }

    ${k} .schema-field-content {
        padding: 0 0 12px 16px;
    }
`);
