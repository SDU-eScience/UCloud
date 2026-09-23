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
    return `pid-${creatorStableIdCounter++}`;
}

let creatorStableIdCounter = 1;

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
    placementCreatedGroup: {id: number; title: string; description: string} | null;
    nameManuallySet: boolean;
    parseErrors: CreatorSourceParseError[];
    sourceNormalized: boolean;
    yamlFocusKey: string | null;
    revision: number;
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
