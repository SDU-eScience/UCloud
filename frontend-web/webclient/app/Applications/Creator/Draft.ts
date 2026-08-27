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

import {A2Yaml} from "@/Applications/Creator/A2";
import {CreatorSourceParseError} from "@/Applications/Creator/SourceParser";
import type {
    AppCatalogCustomCategory,
    AppCatalogCustomGroup,
    AppEditorCustomEligibilityResponse,
    Application,
} from "@/Applications/AppStoreApi";
import type {JobSpecification} from "@/UCloud/JobsApi";

// Stable row identity
// -------------------------------------------------------------------------------------------------------------------
// Parameter names are YAML map keys and Jinja variable names. They change when the user renames a
// parameter. Selection and reorder state must survive name edits because they track the parameter
// itself, not its current name. The draft assigns a stable id to each parameter on load and on
// insertion. The panel and the content rows read and write the selection by stable id.

export function creatorStableId(): string {
    // A short unique marker. The id is internal and never serialized to YAML.
    return `pid-${creatorStableIdCounter++}`;
}

let creatorStableIdCounter = 1;

// Operation context
// -------------------------------------------------------------------------------------------------------------------

export type CreatorOperationKind = "newManaged" | "newCustom" | "newVersion" | "fork";

export interface CreatorOperationContext {
    operation: CreatorOperationKind;
    // The existing application name. Set for newVersion and fork.
    existingName?: string;
    // The existing application version. Set for newVersion.
    existingVersion?: string;
    // The provider to use for custom applications. Set for newCustom and fork.
    provider?: string;
    // Development-only template selection. Normal routes omit this and use backend source loading.
    developmentTemplate?: boolean;
}

export function creatorIsCustom(context: CreatorOperationContext): boolean {
    return context.operation === "newCustom" || context.operation === "fork";
}

export function creatorIsEditableName(context: CreatorOperationContext): boolean {
    return context.operation === "newManaged" || context.operation === "newCustom" || context.operation === "fork";
}

export function creatorIsEditableVersion(context: CreatorOperationContext): boolean {
    return context.operation !== "newVersion";
}

// Selection
// -------------------------------------------------------------------------------------------------------------------
// The selection points to a parameter by stable id, or null for the application metadata panel.
// The stable id does not change when the parameter is renamed. The draft stores the selection so
// that the panel state survives re-renders and rename operations without reading from the DOM.

export interface CreatorSelection {
    // The stable row id of the selected parameter, or null for the application metadata panel.
    parameterId: string | null;
    // The parameter name at the time of selection. Kept for convenience only; the stable id is the
    // source of truth. May be stale after a rename.
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
    // The parameter name this error relates to, or null for application metadata.
    parameterName: string | null;
    message: string;
    // Backend errors can identify a visual field and a source location.
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
    // The current editable A2 source shape. Visual controls read from this.
    application: A2Yaml;
    // The raw source text. The YAML view edits this directly; visual changes serialize the
    // complete model into canonical YAML and replace this field.
    sourceText: string;
    // True when the user has made any change since the draft was loaded or last saved.
    dirty: boolean;
    // The current selection in the content editor. Uses a stable id so selection survives
    // rename operations.
    selection: CreatorSelection;
    // The active view in the editor panel.
    view: CreatorView;
    // The last valid A2 model parsed from the source text. Visual controls show this when the
    // source text is invalid.
    lastValidApplication: A2Yaml;
    // True when the source text does not parse into a valid A2Yaml. While this is true, visual
    // controls are read-only and preview/save are disabled.
    sourceTextInvalid: boolean;
    // Validation state. Separate from sourceTextInvalid because a valid YAML document can still
    // contain invalid application fields.
    validation: CreatorValidationState;
    // The operation context that opened the editor.
    context: CreatorOperationContext;
    // Stable id per parameter name. The keys are parameter names, the values are ids that do not
    // change when the parameter is renamed. Renaming updates the key but keeps the same value.
    parameterIds: Record<string, string>;
    // Custom application metadata that is not part of the A2 YAML document. The backend stores
    // provider, category, group, flavor, and publication as separate request fields. Only custom
    // applications carry these values. Managed applications leave it null.
    customMeta: CreatorCustomMeta | null;
    // Parse errors from the last source-text parse. Empty when the source text is valid YAML.
    // These are distinct from `validation.errors` because parse errors come from the source text
    // and carry a line/column, while validation errors come from the structured model and do not
    // always have a source location.
    parseErrors: CreatorSourceParseError[];
    // False until the first visual edit normalizes the source text into canonical YAML. While
    // false, a visual change that would replace the user's formatting or comments shows a
    // confirmation. After the first confirmation, this is true and no further warnings show for
    // the same loaded draft. Resets to false when a new draft loads.
    sourceNormalized: boolean;
    // A parameter key the YAML editor should scroll to and highlight. Set by the Workflow row
    // "Open in YAML" action so the user lands on the relevant section. Cleared after the editor
    // applies the focus. Null when no focus is pending.
    yamlFocusKey: string | null;
    // Monotonically increasing revision for edits to the application or source text. Server
    // validation and preview results are accepted only for the revision they were requested for.
    revision: number;
}

// Custom application metadata carried outside the A2 YAML. Managed applications do not use this;
// the draft keeps customMeta null for them.
export interface CreatorCustomMeta {
    // The service provider id for the container.
    provider: string;
    // The custom category id. The editor loads available choices from the backend.
    category: string;
    // The custom group id. The editor loads available choices from the backend.
    group: string;
    // The flavor name.
    flavor: string;
    // Whether the application is published to the project. Always false in a personal workspace.
    publishedToProject: boolean;
    // True when the editor can offer publication. False in a personal workspace.
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
        parameterIds[name] = creatorStableId();
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
    // Load the A2 source for the given operation. Returns the source text, the last valid parsed
    // model, and the custom-only metadata (null for managed applications).
    loadSource(context: CreatorOperationContext): Promise<{application: A2Yaml; sourceText: string; customMeta: CreatorCustomMeta | null}>;
    // Validate a draft without creating an application.
    validate(request: CreatorValidationRequest): Promise<CreatorValidationResponse>;
    // Render a temporary job invocation through the selected provider.
    renderInvocation(request: CreatorRenderRequest): Promise<CreatorRenderResponse>;
    // Load custom placement choices and provider/publication eligibility.
    loadCustomEligibility(): Promise<AppEditorCustomEligibilityResponse>;
    loadCustomPlacement(): Promise<{groups: AppCatalogCustomGroup[]; categories: AppCatalogCustomCategory[]}>;
    // Create a new application version from the complete source.
    save(application: A2Yaml, sourceText: string, context: CreatorOperationContext, customMeta: CreatorCustomMeta | null): Promise<void>;
}

// The template service is defined in Templates.ts.
