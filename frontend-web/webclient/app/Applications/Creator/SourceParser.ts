// Source text parse and serialization
// =====================================================================================================================
// The YAML document and the visual editor are two views of one local application model. This
// module owns the two directions:
//
// - parse: canonical YAML text → A2Yaml with explicit parameter declaration order and error
//   locations. The parser does not throw. It returns a result object so the caller can keep the
//   last valid model and show the exact invalid source text at the same time.
// - serialize: A2Yaml → canonical YAML text. The canonical form is stable: the same model always
//   produces the same text, with keys in a fixed order and null optional fields omitted. This is
//   what the visual editor writes back when the user makes a visual change.
//
// Semantic validation (duplicate names, numeric ranges, enumeration defaults, unresolved
// references) is separate from parsing and lives in ParameterValidation.ts. A document that
// parses successfully can still be invalid for save. Keeping the two apart means a parse error
// never hides a semantic error and the two can be reported in different places.
//
// The parser only needs YAML syntax and the top-level A2 shape. It preserves parameter declaration
// order by reading the keys of the parsed `parameters` map in insertion order. The `yaml` library
// preserves insertion order for plain objects, which matches the way the backend captures order
// from the raw YAML node.
//
// Parse errors carry a 1-based line and column from the `yaml` library so the YAML editor can
// place a Monaco marker and the page error summary can offer a click-to-line action.

import * as YAML from "yaml";
import {A2Yaml, A2Parameter} from "@/Applications/Creator/A2";

// Parse result
// -------------------------------------------------------------------------------------------------------------------
// The result is one of two states. `ok` means the text parsed into a usable A2Yaml. `error` means
// the text is not valid YAML or does not have the expected top-level shape. The caller must retain
// the exact source text on error and keep showing the last valid model.

export type CreatorSourceParseResult =
    | {ok: true; application: A2Yaml}
    | {ok: false; errors: CreatorSourceParseError[]};

export interface CreatorSourceParseError {
    line: number;
    column: number;
    message: string;
}

export function parseSourceText(text: string): CreatorSourceParseResult {
    const doc = YAML.parseDocument(text);

    if (doc.errors.length > 0) {
        const errors: CreatorSourceParseError[] = doc.errors.map(e => {
            const start = e.linePos?.[0];
            return {
                line: start?.line ?? 0,
                column: start?.col ?? 0,
                message: e.message.split("\n")[0],
            };
        });
        return {ok: false, errors};
    }

    const node = doc.toJS();
    if (node == null || typeof node !== "object" || Array.isArray(node)) {
        return {
            ok: false,
            errors: [{line: 0, column: 0, message: "Expected a YAML mapping at the top level."}],
        };
    }

    const body = {...node as Record<string, unknown>};
    delete body.application;

    const rawParameters = body.parameters;
    let parameters: Record<string, A2Parameter> = {};
    let parametersOrder: string[] = [];
    if (rawParameters != null && typeof rawParameters === "object" && !Array.isArray(rawParameters)) {
        const map = rawParameters as Record<string, unknown>;
        parametersOrder = Object.keys(map);
        parameters = map as Record<string, A2Parameter>;
    }

    const application: A2Yaml = {
        name: asString(body.name) ?? "",
        version: asString(body.version) ?? "",
        software: asSoftware(body.software),
        title: asOptString(body.title),
        description: asOptString(body.description),
        license: asOptString(body.license),
        documentation: asOptString(body.documentation),
        features: body.features == null ? null : body.features as A2Yaml["features"],
        modules: body.modules == null ? null : body.modules as A2Yaml["modules"],
        parameters,
        parametersOrder,
        sbatch: asStringMap(body.sbatch),
        invocation: asString(body.invocation) ?? "",
        ucx: body.ucx == null ? null : body.ucx as A2Yaml["ucx"],
        environment: asStringMap(body.environment),
        web: body.web == null ? null : body.web as A2Yaml["web"],
        vnc: body.vnc == null ? null : body.vnc as A2Yaml["vnc"],
        ssh: body.ssh == null ? null : body.ssh as A2Yaml["ssh"],
        inference: body.inference == null ? null : body.inference as A2Yaml["inference"],
        extensions: asStringArray(body.extensions),
    };

    return {ok: true, application};
}

export function applicationToSourceText(application: A2Yaml): string {
    const body = yamlBodyFromApplication(application);
    const bodyText = YAML.stringify(body, {nullStr: ""});
    return `---\napplication: v2\n\n${bodyText}`;
}

function yamlBodyFromApplication(application: A2Yaml): Record<string, unknown> {
    const orderedParameters: Record<string, unknown> = {};
    for (const name of application.parametersOrder) {
        const param = application.parameters[name];
        if (param) orderedParameters[name] = stripNulls(param);
    }

    const body: Record<string, unknown> = {
        name: application.name,
        version: application.version,
        software: stripNulls(application.software),
    };

    setIfPresent(body, "title", application.title);
    setIfPresent(body, "description", application.description);
    setIfPresent(body, "license", application.license);
    setIfPresent(body, "documentation", application.documentation);
    setIfPresent(body, "features", application.features);
    setIfPresent(body, "modules", application.modules);
    if (Object.keys(orderedParameters).length > 0) {
        body["parameters"] = orderedParameters;
    }
    if (Object.keys(application.sbatch).length > 0) {
        body["sbatch"] = application.sbatch;
    }
    body["invocation"] = application.invocation;
    setIfPresent(body, "ucx", application.ucx);
    if (Object.keys(application.environment).length > 0) {
        body["environment"] = application.environment;
    }
    setIfPresent(body, "web", application.web);
    setIfPresent(body, "vnc", application.vnc);
    setIfPresent(body, "ssh", application.ssh);
    setIfPresent(body, "inference", application.inference);
    if (application.extensions.length > 0) {
        body["extensions"] = application.extensions;
    }

    return body;
}

function setIfPresent(target: Record<string, unknown>, key: string, value: unknown): void {
    if (value === null || value === undefined) return;
    target[key] = stripNulls(value);
}

function stripNulls(value: unknown): unknown {
    if (value === null || value === undefined) return undefined;
    if (Array.isArray(value)) {
        return value.map(stripNulls).filter(v => v !== undefined);
    }
    if (typeof value === "object") {
        const result: Record<string, unknown> = {};
        for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
            const stripped = stripNulls(v);
            if (stripped === undefined) continue;
            result[k] = stripped;
        }
        return result;
    }
    return value;
}

function asString(value: unknown): string | undefined {
    if (typeof value === "string") return value;
    if (value == null) return undefined;
    return String(value);
}

function asOptString(value: unknown): string | null {
    if (value == null) return null;
    return typeof value === "string" ? value : String(value);
}

function asStringMap(value: unknown): Record<string, string> {
    if (value == null || typeof value !== "object" || Array.isArray(value)) return {};
    const result: Record<string, string> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
        result[k] = v == null ? "" : String(v);
    }
    return result;
}

function asStringArray(value: unknown): string[] {
    if (!Array.isArray(value)) return [];
    return value.map(v => (v == null ? "" : String(v)));
}

function asSoftware(value: unknown): A2Yaml["software"] {
    if (value == null || typeof value !== "object" || Array.isArray(value)) {
        return {type: "Container", image: ""};
    }
    return value as A2Yaml["software"];
}
