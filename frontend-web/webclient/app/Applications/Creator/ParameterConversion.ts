// A2-to-runtime parameter conversion
// =====================================================================================================================
// The content editor renders the existing job-creation controls for visual fidelity. These controls
// take the normalized runtime `ApplicationParameter` type from AppStoreApi, not the editable A2 source
// type. This module converts an A2 parameter into a runtime parameter for display only. The editor
// never collects job values from these controls; it owns the draft state directly.
//
// The conversion is one-directional for display. Editor changes go through the draft update path,
// not back through these objects.

import {A2Parameter} from "@/Applications/Creator/A2";
import {
    ApplicationParameter,
    ApplicationParameterNS,
} from "@/Applications/AppStoreApi";

// A2 type string → runtime ApplicationParameter type string.
const TYPE_MAP: Record<string, string> = {
    "Text": "text",
    "TextArea": "textarea",
    "Boolean": "boolean",
    "Integer": "integer",
    "FloatingPoint": "floating_point",
    "Enumeration": "enumeration",
    "File": "input_file",
    "Directory": "input_directory",
    "License": "license_server",
    "Job": "peer",
    "PublicIP": "network_ip",
    "Workflow": "workflow",
};

export function a2ToRuntimeType(type: string): string {
    return TYPE_MAP[type] ?? type;
}

// Convert a single A2 parameter to a runtime ApplicationParameter for display. The runtime type is
// a discriminated union on the `type` string. Optional fields are filled with defaults the widgets
// expect.
export function a2ToRuntimeParameter(name: string, param: A2Parameter): ApplicationParameter {
    const base = {
        name,
        title: param.title,
        description: param.description,
        optional: param.optional,
    };

    switch (param.type) {
        case "Text":
            return {
                ...base,
                type: "text",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.Text;
        case "TextArea":
            return {
                ...base,
                type: "textarea",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.TextArea;
        case "Boolean":
            return {
                ...base,
                type: "boolean",
                trueValue: "true",
                falseValue: "false",
                defaultValue: param.defaultValue ?? undefined,
            } as ApplicationParameterNS.Bool;
        case "Integer":
            return {
                ...base,
                type: "integer",
                defaultValue: param.defaultValue ?? undefined,
                min: param.min ?? undefined,
                max: param.max ?? undefined,
                step: param.step ?? undefined,
            } as ApplicationParameterNS.Integer;
        case "FloatingPoint":
            return {
                ...base,
                type: "floating_point",
                defaultValue: param.defaultValue ?? undefined,
                min: param.min ?? undefined,
                max: param.max ?? undefined,
                step: param.step ?? undefined,
            } as ApplicationParameterNS.FloatingPoint;
        case "Enumeration":
            return {
                ...base,
                type: "enumeration",
                defaultValue: param.defaultValue ?? undefined,
                // A2 options have {title, value}; runtime options have {name, value}.
                options: param.options.map(o => ({name: o.title, value: o.value})),
            } as ApplicationParameterNS.Enumeration;
        case "File":
            return {
                ...base,
                type: "input_file",
                defaultValue: undefined,
            } as ApplicationParameterNS.InputFile;
        case "Directory":
            return {
                ...base,
                type: "input_directory",
                defaultValue: undefined,
            } as ApplicationParameterNS.InputDirectory;
        case "License":
            return {
                ...base,
                type: "license_server",
                tagged: [],
                defaultValue: undefined,
            } as ApplicationParameterNS.LicenseServer;
        case "Job":
            return {
                ...base,
                type: "peer",
                suggestedApplication: undefined,
                defaultValue: undefined,
            } as ApplicationParameterNS.Peer;
        case "PublicIP":
            return {
                ...base,
                type: "network_ip",
                defaultValue: undefined,
            } as ApplicationParameterNS.NetworkIP;
        case "Workflow":
            return {
                ...base,
                type: "workflow",
                defaultValue: undefined,
            } as ApplicationParameterNS.Workflow;
    }
}
