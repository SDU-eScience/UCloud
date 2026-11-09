import {isScalar, parseDocument} from "yaml";
import type {EditorSchemaRegistry} from "./SchemaRegistry";

export type YamlFieldPath = Array<string | number>;

export interface YamlFieldLink {
    source: YamlFieldPath;
    targets: YamlFieldPath[];
}

type YamlFieldScalar = string | number | boolean | null;

interface YamlFieldLinkRule {
    source: YamlFieldPath;
    targets: YamlFieldPath[];
    initialized: boolean;
    value?: YamlFieldScalar;
    fields: Map<string, {path: YamlFieldPath; value: YamlFieldScalar}>;
}

export interface YamlFieldLinkState {
    source: string;
    configuration: string;
    links: YamlFieldLinkRule[];
}

const sessionLinks = new WeakMap<EditorSchemaRegistry, {generation: number; documents: Map<string, YamlFieldLinkState>}>();

export function yamlFieldLinks(registry: EditorSchemaRegistry | undefined, documentId: string, source: string, links: YamlFieldLink[]): YamlFieldLinkState {
    let session = registry ? sessionLinks.get(registry) : undefined;
    if (registry && (!session || session.generation !== registry.generation)) {
        session = {generation: registry.generation, documents: new Map()};
        sessionLinks.set(registry, session);
    }
    const configuration = JSON.stringify(links);
    const retained = session?.documents.get(documentId);
    if (retained?.source === source && retained.configuration === configuration) return retained;
    const state: YamlFieldLinkState = {
        source,
        configuration,
        links: links.map(link => ({...link, initialized: false, fields: new Map()})),
    };
    session?.documents.set(documentId, state);
    return state;
}

function yamlFieldLinkScalar(value: unknown): value is YamlFieldScalar {
    return value === null || typeof value === "string" || typeof value === "boolean" || (typeof value === "number" && Number.isFinite(value));
}

export function yamlFieldLinkEdits(state: YamlFieldLinkState, source: string): Array<{start: number; end: number; text: string}> {
    state.source = source;
    const document = parseDocument(source);
    if (document.errors.length > 0) return [];
    const edits: Array<{start: number; end: number; text: string}> = [];
    for (const link of state.links) {
        const sourceNode = document.getIn(link.source, true);
        if (!isScalar(sourceNode) || !yamlFieldLinkScalar(sourceNode.value)) continue;
        const sourceValue = sourceNode.value;
        if (!link.initialized) {
            for (const path of link.targets) {
                const node = document.getIn(path, true);
                if (!isScalar(node) || !yamlFieldLinkScalar(node.value)) continue;
                const value = node.value;
                if (value === "" || value === null || value === sourceValue) {
                    link.fields.set(JSON.stringify(path), {path, value});
                }
            }
            link.initialized = true;
            link.value = sourceValue;
        }
        for (const [key, field] of link.fields) {
            const node = document.getIn(field.path, true);
            if (!isScalar(node) || !node.range) {
                link.fields.delete(key);
                continue;
            }
            const value = node.value;
            if (value !== field.value && !(sourceValue !== link.value && value === sourceValue)) {
                link.fields.delete(key);
                continue;
            }
            if (value !== sourceValue) edits.push({start: node.range[0], end: node.range[1], text: JSON.stringify(sourceValue)});
            field.value = sourceValue;
        }
        link.value = sourceValue;
    }
    return edits;
}
