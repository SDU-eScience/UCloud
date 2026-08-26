// Canonical A2 application source model
// =====================================================================================================================
// This module mirrors the A2 application YAML format defined by the backend in
// provider-integration/shared/pkg/orchestrators/app_yaml.go. The editor works with this source shape
// instead of the normalized runtime Application type because the normalized type loses source
// details (parameter declaration order, the software discriminator, optional fields that become
// defaults when absent) and is not a safe editor model.
//
// The backend parses a document that starts with `application: v2` followed by the A2Yaml body.
// The version header is added by the service layer, not by the editor model, so it is not part of
// A2Yaml here.

// Software
// -------------------------------------------------------------------------------------------------------------------

export type A2Software =
    | A2NativeSoftware
    | A2ContainerSoftware
    | A2VirtualMachineSoftware
    | A2UcxSoftware;

export interface A2NativeSoftware {
    type: "Native";
    load: A2ApplicationToLoad[];
}

export interface A2ApplicationToLoad {
    name: string;
    version: string;
}

export interface A2ContainerSoftware {
    type: "Container";
    image: string;
}

export interface A2VirtualMachineSoftware {
    type: "VirtualMachine";
    image: string;
}

export interface A2UcxSoftware {
    type: "UCX";
    image: string;
}

// Parameters
// -------------------------------------------------------------------------------------------------------------------
// Parameters use a discriminated union on the `type` string. The type strings match the backend
// A2 format exactly (upper-camel, e.g. "Integer", "FloatingPoint", "TextArea").

export interface A2ParamBase {
    title: string;
    description: string;
    optional: boolean;
}

export type A2Parameter =
    | (A2ParamBase & { type: "File" })
    | (A2ParamBase & { type: "Directory" })
    | (A2ParamBase & { type: "License" })
    | (A2ParamBase & { type: "Job" })
    | (A2ParamBase & { type: "PublicIP" })
    | (A2ParamBase & { type: "Integer"; defaultValue?: number | null; min?: number | null; max?: number | null; step?: number | null })
    | (A2ParamBase & { type: "FloatingPoint"; defaultValue?: number | null; min?: number | null; max?: number | null; step?: number | null })
    | (A2ParamBase & { type: "Boolean"; defaultValue?: boolean | null })
    | (A2ParamBase & { type: "Text"; defaultValue?: string | null })
    | (A2ParamBase & { type: "TextArea"; defaultValue?: string | null })
    | (A2ParamBase & { type: "Enumeration"; defaultValue?: string | null; options: A2EnumOption[] })
    | (A2ParamBase & {
        type: "Workflow";
        init?: string | null;
        job?: string | null;
        readme?: string | null;
        parameters: Record<string, A2Parameter>;
    });

export interface A2EnumOption {
    title: string;
    value: string;
}

// Optional fields use `null` to represent an absent key in the source YAML.
// The backend uses util.Option[T] with the same semantics.

export interface A2Features {
    multiNode: boolean;
    links?: boolean | null;
    ipAddresses?: boolean | null;
    folders?: boolean | null;
    jobLinking?: boolean | null;
    jobAuditLog?: boolean | null;
}

export interface A2Web {
    enabled: boolean;
    port?: number | null;
}

export interface A2Vnc {
    enabled: boolean;
    port?: number | null;
    password?: string | null;
}

export type A2SshMode = "Mandatory" | "Optional" | "Disabled";

export interface A2Ssh {
    mode: A2SshMode;
}

export type A2InferenceMode = "None" | "Optional" | "Mandatory";

export interface A2Inference {
    mode: A2InferenceMode;
}

export interface A2Module {
    mountPath: string;
    optional: string[];
}

export interface UcxExecutableDescription {
    manifestUrl: string;
    publicKey: string;
    binaryName: string;
}

export interface UcxDescription {
    executable?: UcxExecutableDescription | null;
}

// A2Yaml
// -------------------------------------------------------------------------------------------------------------------
// Parameters are stored as a map plus a separate parametersOrder array. Go maps do not preserve
// insertion order, so the backend captures declaration order from the raw YAML node in
// UnmarshalYAML. The editor model does the same: parametersOrder is the source of truth for
// parameter order, never JavaScript object iteration or DOM order.

export interface A2Yaml {
    name: string;
    version: string;
    software: A2Software;
    title?: string | null;
    description?: string | null;
    license?: string | null;
    documentation?: string | null;
    features?: A2Features | null;
    modules?: A2Module | null;
    parameters: Record<string, A2Parameter>;
    parametersOrder: string[];
    sbatch: Record<string, string>;
    invocation: string;
    ucx?: UcxDescription | null;
    environment: Record<string, string>;
    web?: A2Web | null;
    vnc?: A2Vnc | null;
    ssh?: A2Ssh | null;
    inference?: A2Inference | null;
    extensions: string[];
}
