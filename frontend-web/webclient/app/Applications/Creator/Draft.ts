// Creator draft model and operation context
// =====================================================================================================================
// The creator editor owns one local draft. The draft holds the A2 application data, the source
// text it was built from, dirty state, the current selection, validation state, and the last
// valid parsed model. The service boundary is shared by development templates and backend operations.
//
// These are the core concepts:
//
// - OperationContext: describes which operation opened the editor. It controls field
//   visibility (managed vs custom) and save rules without changing access policy.
// - Draft: the full editor state owned by the Create component. Visual changes write to the
//   draft; the YAML view reads from it.
// - CreatorService: the boundary between the editor and backend or template operations.

import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";
import type {
    AppCatalogCustomCategory,
    AppCatalogCustomGroup,
    AppEditorCustomEligibilityResponse,
    Application,
    ApplicationGroupLogo,
} from "@/Applications/AppStoreApi";
import type {JobSpecification} from "@/UCloud/JobsApi";

// Canonical A2 application source model
// -------------------------------------------------------------------------------------------------------------------
// This model mirrors the A2 application YAML format defined by the backend in
// provider-integration/shared/pkg/orchestrators/app_yaml.go. The editor works with this source shape
// instead of the normalized runtime Application type because the normalized type loses source
// details (parameter declaration order, the software discriminator, optional fields that become
// defaults when absent) and is not a safe editor model.
//
// The backend parses a document that starts with `application: v2` followed by the A2Yaml body.
// The version header is added by the service layer, not by the editor model, so it is not part of
// A2Yaml here.

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

// Stable row identity
// -------------------------------------------------------------------------------------------------------------------
// Parameter names are YAML map keys and Jinja variable names. They change when the user renames a
// parameter. Selection and reorder state must survive name edits because they track the parameter
// itself, not its current name. The draft assigns a stable id to each parameter on load and on
// insertion. The panel and the content rows read and write the selection by stable id.
//
// Names come from unvalidated YAML. Some names cannot key a plain object safely: "__proto__" and
// the properties of Object.prototype. assignParameterId refuses those names. The id is then
// missing, the renderer falls back to a synthetic id, and validation reports the name as an error.

export function creatorStableId(): string {
    return `pid-${creatorStableIdCounter++}`;
}

let creatorStableIdCounter = 1;

export function assignParameterId(parameterIds: Record<string, string>, name: string, id: string): void {
    if (name === "__proto__" || Object.prototype.hasOwnProperty.call(Object.prototype, name)) return;
    parameterIds[name] = id;
}

// Operation context
// -------------------------------------------------------------------------------------------------------------------

export type CreatorOperationKind = "newManaged" | "newCustom" | "newVersion" | "fork";
export type CreatorApplicationKind = "managed" | "custom";

export interface CreatorOperationContext {
    operation: CreatorOperationKind;
    applicationKind: CreatorApplicationKind;
    workspace: string;
    existingName?: string;
    existingVersion?: string;
    provider?: string;
    sourceApplicationKind?: CreatorApplicationKind;
    sourceProvider?: string;
    initialCategory?: string;
    returnTo?: string;
    developmentTemplate?: boolean;
}

export function creatorIsCustom(context: CreatorOperationContext): boolean {
    return context.applicationKind === "custom";
}

export function creatorIsEditableName(context: CreatorOperationContext): boolean {
    return context.operation === "newManaged" || context.operation === "newCustom" || context.operation === "fork";
}

export function creatorIsEditableVersion(context: CreatorOperationContext): boolean {
    return true;
}

// Selection
// -------------------------------------------------------------------------------------------------------------------
// The selection points to a parameter by stable id, or null for the application metadata panel.
// The stable id does not change when the parameter is renamed. The draft stores the selection so
// that the panel state survives re-renders and rename operations without reading from the DOM.

export interface CreatorSelection {
    parameterId: string | null;
    parameterName: string | null;
}

// Validation
// -------------------------------------------------------------------------------------------------------------------
// Validation runs only when the user requests a preview or save. The service returns backend
// errors separately so stale responses cannot overwrite a newer draft revision.

export interface CreatorValidationState {
    errors: CreatorValidationError[];
}

export interface CreatorValidationError {
    parameterName: string | null;
    message: string;
    code?: string;
    path?: string;
    location?: {line: number; column: number};
}

export function emptyValidationState(): CreatorValidationState {
    return {errors: []};
}

// Draft
// -------------------------------------------------------------------------------------------------------------------
// The draft keeps the last valid structured model separate from the source text. Later
// milestones need this split to keep invalid YAML text while the visual editor displays the last
// valid state.

export interface CreatorDraft {
    application: A2Yaml;
    sourceText: string;
    dirty: boolean;
    selection: CreatorSelection;
    view: CreatorView;
    lastValidApplication: A2Yaml;
    sourceTextInvalid: boolean;
    validation: CreatorValidationState;
    context: CreatorOperationContext;
    parameterIds: Record<string, string>;
    customMeta: CreatorCustomMeta | null;
    placementGroups: AppCatalogCustomGroup[];
    placementCreatedGroup: CreatorCreatedGroup | null;
    nameManuallySet: boolean;
    parseErrors: CreatorSourceParseError[];
    sourceNormalized: boolean;
    yamlFocusKey: string | null;
    revision: number;
}

export interface CreatorCreatedGroup {
    id: number;
    title: string;
    description: string;
    logo: ApplicationGroupLogo;
}

export interface CreatorCustomMeta {
    provider: string;
    category: string;
    group: string;
    flavor: string;
    publishedToProject: boolean;
    canPublish: boolean;
}

export interface CreatorValidationRequest {
    kind: "MANAGED" | "CUSTOM";
    source: string;
    custom?: CreatorCustomMeta;
}

export interface CreatorValidationResponse {
    application?: Application;
    errors: CreatorValidationError[];
}

export interface CreatorRenderRequest {
    validation: CreatorValidationRequest;
    job: JobSpecification;
}

export interface CreatorRenderResponse {
    script?: string;
    errors: CreatorValidationError[];
    rateLimit: {
        limit: number;
        remaining: number;
        retryAt?: number | string;
    };
}

export type CreatorView = "editor" | "yaml" | "invocation" | "preview";

export function creatorInitialDraft(
    application: A2Yaml,
    sourceText: string,
    context: CreatorOperationContext,
    customMeta: CreatorCustomMeta | null,
): CreatorDraft {
    const parameterIds: Record<string, string> = {};
    for (const name of application.parametersOrder) {
        assignParameterId(parameterIds, name, creatorStableId());
    }
    return {
        application,
        sourceText,
        dirty: false,
        selection: {parameterId: null, parameterName: null},
        view: "editor",
        lastValidApplication: application,
        sourceTextInvalid: false,
        validation: emptyValidationState(),
        context,
        parameterIds,
        customMeta,
        placementGroups: [],
        placementCreatedGroup: null,
        nameManuallySet: false,
        parseErrors: [],
        sourceNormalized: false,
        yamlFocusKey: null,
        revision: 0,
    };
}

// Creator service boundary
// -------------------------------------------------------------------------------------------------------------------
// The editor calls these operations through a small interface so backend policy stays out of the UI.

export interface CreatorService {
    loadSource(context: CreatorOperationContext): Promise<{
        application: A2Yaml;
        sourceText: string;
        customMeta: CreatorCustomMeta | null;
    }>;
    validate(request: CreatorValidationRequest): Promise<CreatorValidationResponse>;
    renderInvocation(request: CreatorRenderRequest): Promise<CreatorRenderResponse>;
    loadCustomEligibility(): Promise<AppEditorCustomEligibilityResponse>;
    loadCustomPlacement(): Promise<{groups: AppCatalogCustomGroup[]; categories: AppCatalogCustomCategory[]}>;
    save(application: A2Yaml, sourceText: string, context: CreatorOperationContext, customMeta: CreatorCustomMeta | null): Promise<void>;
}
