import type {editor} from "monaco-editor";
import type {JSONSchema, MonacoYaml, SchemasSettings} from "monaco-yaml";
import {getMonaco} from "./Monaco";
import YamlWorker from "./Yaml.worker?worker";

export interface EditorSchemaRegistration {
    schemaId: string;
    revision: string;
    schema: JSONSchema;
}

export interface EditorSchemaRegistry {
    id: number;
    generation: number;
    schemas: Map<string, EditorSchemaRegistration>;
    bindings: Map<editor.ITextModel, string>;
    listeners: Set<() => void>;
}

const registryState: {
    nextId: number;
    registries: Set<EditorSchemaRegistry>;
} = import.meta.hot?.data.editorSchemaRegistryState ?? {
    nextId: 1,
    registries: new Set(),
};
const registries = registryState.registries;
if (import.meta.hot) import.meta.hot.data.editorSchemaRegistryState = registryState;
let languageService: Promise<{monaco: Awaited<ReturnType<typeof getMonaco>>; yaml: MonacoYaml}> | undefined;
let updates = Promise.resolve();
let disposed = false;
let yamlWorker: {
    settings: Record<string, unknown>;
    inspect: (model: editor.ITextModel) => Promise<unknown>;
} | undefined;

export function createEditorSchemaRegistry(): EditorSchemaRegistry {
    return {
        id: registryState.nextId++,
        generation: 0,
        schemas: new Map(),
        bindings: new Map(),
        listeners: new Set(),
    };
}

function editorSchemaLanguageService() {
    if (!languageService) {
        languageService = Promise.all([getMonaco(), import("monaco-yaml")]).then(([monaco, {configureMonacoYaml}]) => ({
            monaco,
            yaml: configureMonacoYaml({
                ...monaco,
                editor: {
                    ...monaco.editor,
                    createWebWorker: <T>(options: {
                        createData?: unknown;
                        host?: Record<string, Function>;
                        keepIdleModels?: boolean;
                    }) => {
                        const worker = new YamlWorker();
                        worker.postMessage("ignore");
                        worker.postMessage(options.createData);
                        const client = monaco.editor.createWebWorker<T>({
                            worker,
                            host: options.host,
                            keepIdleModels: options.keepIdleModels,
                        });
                        yamlWorker = {
                            settings: options.createData as Record<string, unknown>,
                            inspect: async model => {
                                const proxy = await client.withSyncedResources([model.uri]) as {
                                    getCodeLens: (uri: string) => Promise<unknown>;
                                    doValidation: (uri: string) => Promise<unknown>;
                                };
                                return {
                                    resolvedSchemas: await proxy.getCodeLens(model.uri.toString()),
                                    diagnostics: await proxy.doValidation(model.uri.toString()),
                                };
                            },
                        };
                        return client;
                    },
                },
            }, {
                enableSchemaRequest: false,
                format: {enable: false},
                hoverSchemaSource: false,
            }),
        })).then(service => {
            if (import.meta.env.DEV && !disposed) {
                Object.assign(globalThis, {ucloudEditorSchemaDiagnostics: getEditorSchemaDiagnostics});
            }
            return service;
        }).catch(error => {
            languageService = undefined;
            throw error;
        });
    }
    return languageService;
}

function editorSchemaRefresh(): Promise<void> {
    if (disposed) return Promise.resolve();
    const hasSchemas = Array.from(registries).some(registry => registry.schemas.size > 0);
    if (!languageService && !hasSchemas) return Promise.resolve();
    updates = updates.catch(() => {}).then(async () => {
        if (disposed) return;
        const {monaco, yaml} = await editorSchemaLanguageService();
        if (disposed) return;
        const schemas: SchemasSettings[] = [];
        for (const registry of registries) {
            for (const registration of registry.schemas.values()) {
                const fileMatch: string[] = [];
                for (const [model, schemaId] of registry.bindings) {
                    if (schemaId === registration.schemaId && !model.isDisposed()) fileMatch.push(model.uri.toString());
                }
                schemas.push({
                    uri: monaco.Uri.from({
                        scheme: "ucloud-schema",
                        authority: `registry-${registry.id}`,
                        path: `/${encodeURIComponent(registration.schemaId)}/${encodeURIComponent(registration.revision)}.json`,
                    }).toString(),
                    fileMatch,
                    schema: registration.schema,
                });
            }
        }
        await yaml.update({...yaml.getOptions(), schemas});
        if (disposed) return;
        const jsonDefaults = monaco.json.jsonDefaults;
        const options = jsonDefaults.diagnosticsOptions;
        jsonDefaults.setDiagnosticsOptions({
            ...options,
            schemas: [
                ...(options.schemas ?? []).filter(schema => !schema.uri.startsWith("ucloud-schema://")),
                ...schemas,
            ],
        });
    });
    return updates;
}

function editorSchemaNotify(registry: EditorSchemaRegistry) {
    registryState.nextId = Math.max(registryState.nextId, registry.id + 1);
    if (registry.schemas.size === 0 && registry.bindings.size === 0) registries.delete(registry);
    else registries.add(registry);
    for (const listener of registry.listeners) listener();
}

function editorSchemaValidate(schema: unknown): void {
    if (typeof schema === "boolean") return;
    if (!schema || typeof schema !== "object" || Array.isArray(schema)) throw new Error("Expected a JSON Schema object");
    const node = schema as Record<string, unknown>;
    for (const key of ["$ref", "$dynamicRef", "$recursiveRef"]) {
        const reference = node[key];
        if (reference !== undefined && (typeof reference !== "string" || !reference.startsWith("#"))) {
            throw new Error("Editor schemas must use document-local references");
        }
    }
    for (const key of ["properties", "patternProperties", "definitions", "$defs", "dependentSchemas"]) {
        const children = node[key];
        if (children && typeof children === "object") {
            for (const child of Object.values(children)) editorSchemaValidate(child);
        }
    }
    for (const key of ["allOf", "anyOf", "oneOf", "prefixItems", "schemaSequence"]) {
        const children = node[key];
        if (Array.isArray(children)) children.forEach(editorSchemaValidate);
    }
    for (const key of ["items", "additionalItems", "additionalProperties", "unevaluatedProperties", "unevaluatedItems", "contains", "not", "if", "then", "else", "propertyNames"]) {
        const child = node[key];
        if (Array.isArray(child)) child.forEach(editorSchemaValidate);
        else if (child !== undefined) editorSchemaValidate(child);
    }
    const dependencies = node.dependencies;
    if (dependencies && typeof dependencies === "object") {
        for (const child of Object.values(dependencies)) {
            if (!Array.isArray(child)) editorSchemaValidate(child);
        }
    }
}

export async function registerEditorSchemas(registry: EditorSchemaRegistry, registrations: EditorSchemaRegistration[]): Promise<void> {
    const generation = registry.generation;
    if (!Array.isArray(registrations)) throw new Error("Expected a schemas array");
    for (const registration of registrations) {
        if (!registration || typeof registration.schemaId !== "string" || registration.schemaId === "" || typeof registration.revision !== "string") {
            throw new Error("Expected a schemaId and revision for each editor schema");
        }
        if (!registration.schema || typeof registration.schema !== "object" || Array.isArray(registration.schema)) {
            throw new Error("Expected a JSON Schema object");
        }
        editorSchemaValidate(registration.schema);
    }
    await editorSchemaLanguageService();
    if (registry.generation !== generation) throw new Error("Editor schema session closed");
    for (const registration of registrations) registry.schemas.set(registration.schemaId, registration);
    editorSchemaNotify(registry);
    await editorSchemaRefresh();
}

export async function unregisterEditorSchemas(registry: EditorSchemaRegistry, schemaIds: string[]): Promise<void> {
    if (!Array.isArray(schemaIds) || schemaIds.some(id => typeof id !== "string")) throw new Error("Expected a schemaIds array");
    for (const id of schemaIds) registry.schemas.delete(id);
    editorSchemaNotify(registry);
    await editorSchemaRefresh();
}

export function clearEditorSchemas(registry: EditorSchemaRegistry) {
    registry.generation++;
    registry.schemas.clear();
    editorSchemaNotify(registry);
    void editorSchemaRefresh().catch(console.error);
}

export function bindEditorSchema(registry: EditorSchemaRegistry, schemaId: string, model: editor.ITextModel, onStatus: (available: boolean) => void): () => void {
    const listener = () => onStatus(registry.schemas.has(schemaId));
    registry.bindings.set(model, schemaId);
    registry.listeners.add(listener);
    editorSchemaNotify(registry);
    void editorSchemaRefresh().catch(console.error);
    return () => {
        registry.bindings.delete(model);
        registry.listeners.delete(listener);
        editorSchemaNotify(registry);
        void editorSchemaRefresh().catch(console.error);
    };
}

export async function getEditorSchemaDiagnostics() {
    if (!languageService) return {configured: false, moduleUrl: import.meta.url};
    const {monaco, yaml} = await languageService;
    const options = yaml.getOptions();
    const worker = yamlWorker;
    const models = monaco.editor.getModels().filter(model => model.getLanguageId() === "yaml");
    const schemaSummary = (schemas: SchemasSettings[] | undefined) => schemas?.map(schema => ({
        uri: schema.uri,
        fileMatch: schema.fileMatch,
        properties: Object.keys(schema.schema?.properties ?? {}),
    }));
    const documents = [];
    for (const model of models) {
        let result: unknown;
        try {
            result = await worker?.inspect(model);
        } catch (error) {
            result = {error: String(error)};
        }
        documents.push({
            uri: model.uri.toString(),
            bindings: Array.from(registries).flatMap(registry => {
                const schemaId = registry.bindings.get(model);
                return schemaId ? [{registryId: registry.id, schemaId, registered: registry.schemas.has(schemaId)}] : [];
            }),
            worker: result,
            markers: monaco.editor.getModelMarkers({resource: model.uri}),
        });
    }
    return {
        configured: true,
        moduleUrl: import.meta.url,
        options: {
            validate: options.validate,
            hover: options.hover,
            completion: options.completion,
            schemas: schemaSummary(options.schemas),
        },
        workerOptions: worker ? {
            validate: worker.settings.validate,
            hover: worker.settings.hover,
            completion: worker.settings.completion,
            schemas: schemaSummary(worker.settings.schemas as SchemasSettings[] | undefined),
        } : undefined,
        documents,
    };
}

if (import.meta.hot) {
    import.meta.hot.dispose(() => {
        disposed = true;
        const service = languageService;
        languageService = undefined;
        yamlWorker = undefined;
        void service?.then(({yaml}) => yaml.dispose()).catch(console.error);
    });
}
