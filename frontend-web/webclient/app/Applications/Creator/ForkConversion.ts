import * as YAML from "yaml";
import {A2Parameter, A2Yaml} from "@/Applications/Creator/A2";
import {CreatorCustomMeta} from "@/Applications/Creator/Draft";
import {applicationToSourceText, parseSourceText} from "@/Applications/Creator/SourceParser";

export interface CreatorForkConversion {
    application: A2Yaml;
    sourceText: string;
    customMeta: CreatorCustomMeta;
}

const topLevelFields = new Set([
    "application", "name", "version", "software", "title", "description", "license", "documentation",
    "features", "modules", "parameters", "sbatch", "invocation", "ucx", "environment", "web", "vnc", "ssh",
    "inference", "extensions",
]);
const parameterBaseFields = ["type", "title", "description", "optional"];

export function creatorConvertForkSource(source: string, suggestedName: string, suggestedVersion: string): CreatorForkConversion {
    creatorRejectUnknownForkFields(source);
    const parsed = parseSourceText(source);
    if (!parsed.ok) throw new Error("The application source could not be converted.");

    const original = parsed.application;
    const application: A2Yaml = {
        ...original,
        name: suggestedName,
        version: suggestedVersion,
        software: original.software.type === "Container"
            ? {...original.software}
            : {type: "Container", image: ""},
        documentation: null,
        modules: null,
        ucx: null,
        extensions: [],
    };
    return {
        application,
        sourceText: applicationToSourceText(application),
        customMeta: {
            provider: "",
            category: "",
            group: "",
            flavor: "",
            publishedToProject: false,
            canPublish: false,
        },
    };
}

function creatorRejectUnknownForkFields(source: string): void {
    const document = YAML.parseDocument(source);
    if (document.errors.length > 0) throw new Error("The application source could not be converted.");
    const root = creatorMapping(document.toJS(), "source");
    creatorAssertKnownFields(root, topLevelFields, "source");

    const software = creatorMapping(root.software, "software");
    const softwareType = software.type;
    const softwareFields = softwareType === "Native"
        ? new Set(["type", "load"])
        : new Set(["type", "image"]);
    if (softwareType !== "Native" && softwareType !== "Container" && softwareType !== "VirtualMachine" && softwareType !== "UCX") {
        throw new Error(`Fork cannot convert unknown software type '${String(softwareType)}'.`);
    }
    creatorAssertKnownFields(software, softwareFields, "software");
    if (softwareType === "Native" && Array.isArray(software.load)) {
        software.load.forEach((entry, index) => creatorAssertKnownFields(
            creatorMapping(entry, `software.load[${index}]`),
            new Set(["name", "version"]),
            `software.load[${index}]`,
        ));
    }

    creatorAssertOptionalMapping(root.features, ["multiNode", "links", "ipAddresses", "folders", "jobLinking", "jobAuditLog"], "features");
    creatorAssertOptionalMapping(root.modules, ["mountPath", "optional"], "modules");
    creatorAssertOptionalMapping(root.web, ["enabled", "port"], "web");
    creatorAssertOptionalMapping(root.vnc, ["enabled", "port", "password"], "vnc");
    creatorAssertOptionalMapping(root.ssh, ["mode"], "ssh");
    creatorAssertOptionalMapping(root.inference, ["mode"], "inference");
    if (root.ucx != null) {
        const ucx = creatorMapping(root.ucx, "ucx");
        creatorAssertKnownFields(ucx, new Set(["executable"]), "ucx");
        if (ucx.executable != null) {
            creatorAssertKnownFields(
                creatorMapping(ucx.executable, "ucx.executable"),
                new Set(["manifestUrl", "publicKey", "binaryName"]),
                "ucx.executable",
            );
        }
    }

    if (root.parameters != null) {
        const parameters = creatorMapping(root.parameters, "parameters");
        for (const [name, parameter] of Object.entries(parameters)) {
            creatorValidateParameter(creatorMapping(parameter, `parameters.${name}`), `parameters.${name}`);
        }
    }
}

function creatorValidateParameter(parameter: Record<string, unknown>, path: string): void {
    const type = parameter.type as A2Parameter["type"];
    let fields = parameterBaseFields;
    switch (type) {
        case "File":
        case "Directory":
        case "License":
        case "Job":
        case "PublicIP":
            break;
        case "Integer":
        case "FloatingPoint":
            fields = [...fields, "defaultValue", "min", "max", "step"];
            break;
        case "Boolean":
        case "Text":
        case "TextArea":
            fields = [...fields, "defaultValue"];
            break;
        case "Enumeration":
            fields = [...fields, "defaultValue", "options"];
            if (Array.isArray(parameter.options)) {
                parameter.options.forEach((option, index) => creatorAssertKnownFields(
                    creatorMapping(option, `${path}.options[${index}]`),
                    new Set(["title", "value"]),
                    `${path}.options[${index}]`,
                ));
            }
            break;
        case "Workflow":
            fields = [...fields, "init", "job", "readme", "parameters"];
            if (parameter.parameters != null) {
                const nested = creatorMapping(parameter.parameters, `${path}.parameters`);
                for (const [name, value] of Object.entries(nested)) {
                    creatorValidateParameter(creatorMapping(value, `${path}.parameters.${name}`), `${path}.parameters.${name}`);
                }
            }
            break;
        default:
            throw new Error(`Fork cannot convert unknown parameter type '${String(type)}' at ${path}.`);
    }
    creatorAssertKnownFields(parameter, new Set(fields), path);
}

function creatorAssertOptionalMapping(value: unknown, fields: string[], path: string): void {
    if (value == null) return;
    creatorAssertKnownFields(creatorMapping(value, path), new Set(fields), path);
}

function creatorAssertKnownFields(value: Record<string, unknown>, fields: Set<string>, path: string): void {
    const unknown = Object.keys(value).find(key => !fields.has(key));
    if (unknown) throw new Error(`Fork cannot convert unknown source field '${path}.${unknown}'.`);
}

function creatorMapping(value: unknown, path: string): Record<string, unknown> {
    if (value == null || typeof value !== "object" || Array.isArray(value)) {
        throw new Error(`Fork cannot convert invalid source section '${path}'.`);
    }
    return value as Record<string, unknown>;
}
