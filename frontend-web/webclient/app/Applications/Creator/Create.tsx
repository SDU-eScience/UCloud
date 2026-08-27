// Creator editor page
// =====================================================================================================================
// This page implements the editor shell for the custom application creator. It owns one local
// draft, renders a full-viewport two-island layout, and connects the dirty-state and navigation
// protection. All backend operations go through the CreatorService boundary; development-only
// templates remain available for the explicit template showcase routes.
//
// The shell fills the entire content viewport like the file editor (Editor/Editor.tsx). It splits
// the space between a main island (content + header) and a properties island (right sidebar).
// The properties panel is resizable by dragging a handle on its left edge. The default width is
// 450 pixels.
//
// The main island header contains the application title, the view-switch icon buttons, and the
// save button. The properties island contains application metadata and parameter settings, styled
// similarly to Figma's properties panel.

import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {useLocation, useNavigate, useBeforeUnload} from "react-router-dom";
import {useSelector} from "react-redux";
import {Box, Button, Flex, Grid, Text} from "@/ui-components";
import CodeSnippet from "@/ui-components/CodeSnippet";
import Warning from "@/ui-components/Warning";
import {IconButton} from "@/ui-components/IconButton";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {addStandardDialog} from "@/UtilityComponents";
import {inDevEnvironment} from "@/UtilityFunctions";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {Client} from "@/Authentication/HttpClientInstance";
import {useProjectId} from "@/Project/Api";
import {
    CreatorDraft,
    CreatorOperationContext,
    CreatorView,
    CreatorCustomMeta,
    CreatorValidationError,
    creatorInitialDraft,
    creatorStableId,
    creatorIsCustom,
    emptyValidationState, CreatorValidationRequest, CreatorValidationResponse,
} from "@/Applications/Creator/Draft";
import {
    creatorInternalName,
    creatorLogicalName,
    creatorService,
    creatorSourceForEditor,
} from "@/Applications/Creator/CreatorService";
import * as AppStore from "@/Applications/AppStoreApi";
import {
    applicationToSourceText,
    parseSourceText,
} from "@/Applications/Creator/SourceParser";
import {EditorHeader} from "@/Applications/Creator/EditorHeader";
import {ParameterContent} from "@/Applications/Creator/ParameterContent";
import {ParameterPanel} from "@/Applications/Creator/ParameterPanel";
import {MetadataPanel} from "@/Applications/Creator/MetadataPanel";
import {FeatureCards} from "@/Applications/Creator/FeatureCards";
import {YamlEditor} from "@/Applications/Creator/YamlEditor";
import {InvocationEditor, InvocationTab} from "@/Applications/Creator/InvocationEditor";
import {ErrorSummary} from "@/Applications/Creator/ErrorSummary";
import {CreatorHighlightTarget, creatorHighlightTarget} from "@/Applications/Creator/Highlight";
import {
    draftSelectParameter,
    draftUpdateBase,
    draftRenameParameter,
    draftDeleteParameter,
    draftReorderParameters,
    draftUpdateDefaultValue,
    draftUpdateNumericField,
    draftUpdateEnumeration,
    draftAddParameter,
    draftUpdateMetadata,
    draftUpdateSoftware,
    draftUpdateFeatures,
    draftUpdateWeb,
    draftUpdateVnc,
    draftUpdateSsh,
    draftUpdateInference,
    draftUpdateModules,
    draftUpdateUcx,
    draftUpdateExtensions,
    draftUpdateEnvironment,
    draftUpdateSbatch,
    draftUpdateCustomMeta,
} from "@/Applications/Creator/DraftOperations";
import {A2Parameter, A2EnumOption, A2Yaml, A2Software} from "@/Applications/Creator/A2";
import {A2WidgetType} from "@/Applications/Creator/WidgetDefaults";
import {validateApplicationLocal} from "@/Applications/Creator/ParameterValidation";
import {Application, ApplicationParameter} from "@/Applications/AppStoreApi";
import {ProductV2Compute} from "@/Accounting";
import {compute} from "@/UCloud";
import {JobSpecification} from "@/UCloud/JobsApi";
import JobCreate from "@/Applications/Jobs/Create";
import AppRoutes from "@/Routes";
import {UcxSpinner} from "@/UCX/UcxView";

// Shell layout
// -------------------------------------------------------------------------------------------------------------------
// The shell root fills the viewport. It is a flex row with an 8-pixel gap and padding, matching the
// file editor's outer container. The main island takes all remaining space. The properties panel
// has a fixed pixel width controlled by the resizer.

const CreatorShellClass = injectStyle("creator-shell", k => `
    ${k} {
        display: flex;
        width: 100%;
        max-width: 100%;
        height: 100%;
        min-height: 0;
        box-sizing: border-box;
        padding: 8px;
        gap: 8px;
        overflow: hidden;
        background: var(--backgroundCard);
    }

    @media (max-width: 900px) {
        ${k} {
            flex-direction: column;
            overflow-y: auto;
        }
    }
`);

const CreatorMainIslandClass = injectStyle("creator-main-island", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        flex: 1 1 auto;
        width: 0;
        height: 100%;
        min-height: 0;
        min-width: 0;
        overflow: hidden;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
    }

    @media (max-width: 900px) {
        ${k} {
            width: 100%;
            flex: none;
            height: auto;
            min-height: 0;
        }
    }
`);

const CreatorMainHeaderClass = injectStyle("creator-main-header", k => `
    ${k} {
        display: flex;
        align-items: center;
        gap: 8px;
        height: 48px;
        flex-shrink: 0;
        padding: 0 16px;
        border-bottom: 1px solid var(--borderColor);
    }
`);



const CreatorMainBodyClass = injectStyle("creator-main-body", k => `
    ${k} {
        flex: 1 1 auto;
        min-height: 0;
        overflow-y: auto;
        padding: 24px;
    }

    @media (max-width: 900px) {
        ${k} {
            overflow-y: visible;
        }
    }
`);

// Properties island (right sidebar)
// -------------------------------------------------------------------------------------------------------------------
// The properties panel is a bordered island to the right of the main content. It uses a CSS
// variable --panel-width so the resizer can update it without re-rendering React. The resizer handle
// sits on the left edge of the panel, following the same pointer-event pattern as the file editor's
// FileTree.

const DEFAULT_PANEL_WIDTH = 450;
const MIN_PANEL_WIDTH = 280;
const MAX_PANEL_WIDTH = 720;

const CreatorPanelIslandClass = injectStyle("creator-panel-island", k => `
    ${k} {
        flex: 0 0 var(--panel-width, ${DEFAULT_PANEL_WIDTH}px);
        width: var(--panel-width, ${DEFAULT_PANEL_WIDTH}px);
        max-width: var(--panel-width, ${DEFAULT_PANEL_WIDTH}px);
        height: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
        position: relative;
        overflow: hidden;
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        background: var(--backgroundDefault);
    }

    @media (max-width: 900px) {
        ${k} {
            flex: none;
            width: 100%;
            max-width: 100%;
            height: auto;
        }
    }

    ${k} .panel-resizer {
        width: 8px;
        height: 100%;
        position: absolute;
        top: 0;
        left: -4px;
        background: transparent;
        cursor: col-resize;
        touch-action: none;
        z-index: 2;
    }

    @media (max-width: 900px) {
        ${k} .panel-resizer {
            display: none;
        }
    }
`);

const CreatorPanelBodyClass = injectStyle("creator-panel-body", k => `
    ${k} {
        flex: 1 1 auto;
        min-height: 0;
        display: flex;
        flex-direction: column;
        padding: 0;
    }
`);

const CreatorPanelScrollClass = injectStyle("creator-panel-scroll", k => `
    ${k} {
        flex: 1 1 auto;
        min-height: 0;
        overflow-y: auto;
    }
`);

// Template kind selection
// -------------------------------------------------------------------------------------------------------------------

export type CreatorTemplateKind = "blankCustom" | "blankManaged" | "fullTemplate";

function contextFromKind(kind: CreatorTemplateKind): CreatorOperationContext {
    switch (kind) {
        case "blankCustom":
            return {operation: "newCustom", applicationKind: "custom", workspace: "personal", provider: "aalborg", developmentTemplate: true};
        case "blankManaged":
            return {operation: "newManaged", applicationKind: "managed", workspace: "personal", developmentTemplate: true};
        case "fullTemplate":
            return {
                operation: "newVersion",
                applicationKind: "managed",
                workspace: "personal",
                existingName: "example-app",
                existingVersion: "1.0",
                developmentTemplate: true,
            };
    }
}

function contextFromLocation(search: string, kind: CreatorTemplateKind): {context: CreatorOperationContext; error: string | null} {
    const operation = getQueryParam(search, "operation");
    if (!operation && inDevEnvironment() && getQueryParam(search, "kind")) {
        return {context: contextFromKind(kind), error: null};
    }

    const applicationKind = getQueryParam(search, "applicationKind");
    const workspace = getQueryParam(search, "workspace");
    const fallback = contextFromKind("blankManaged");
    if (operation !== "newManaged" && operation !== "newCustom" && operation !== "newVersion") {
        return {context: fallback, error: "The creator operation is missing or invalid."};
    }
    if (applicationKind !== "managed" && applicationKind !== "custom") {
        return {context: fallback, error: "The application kind is missing or invalid."};
    }
    if (!workspace) {
        return {context: fallback, error: "The target workspace is missing."};
    }
    if ((operation === "newManaged" && applicationKind !== "managed") || (operation === "newCustom" && applicationKind !== "custom")) {
        return {context: fallback, error: "The creator operation does not match the application kind."};
    }

    const context: CreatorOperationContext = {
        operation,
        applicationKind,
        workspace,
        existingName: getQueryParam(search, "name") ?? undefined,
        existingVersion: getQueryParam(search, "version") ?? undefined,
        provider: getQueryParam(search, "provider") ?? undefined,
        initialCategory: getQueryParam(search, "category") ?? undefined,
        returnTo: getQueryParam(search, "returnTo") ?? undefined,
    };
    if (operation === "newCustom" && !context.initialCategory) {
        return {context, error: "A custom application category is required."};
    }
    if (operation === "newVersion" && (!context.existingName || !context.existingVersion)) {
        return {context, error: "The source application name and version are required."};
    }
    if (operation === "newVersion" && applicationKind === "custom" && !context.provider) {
        return {context, error: "The source service provider is required."};
    }
    return {context, error: null};
}

function kindFromQuery(search: string): CreatorTemplateKind {
    const kind = getQueryParam(search, "kind");
    switch (kind) {
        case "blankCustom":
        case "blankManaged":
        case "fullTemplate":
            return kind;
        default:
            return "blankCustom";
    }
}

// Create component
// -------------------------------------------------------------------------------------------------------------------

export const Create: React.FunctionComponent = () => {
    const location = useLocation();
    const projectId = useProjectId();
    const kind = kindFromQuery(location.search);
    const parsedContext = contextFromLocation(location.search, kind);
    const context = parsedContext.context;
    const activeWorkspace = projectId ?? "personal";
    const contextError = parsedContext.error ?? (
        !context.developmentTemplate && context.workspace !== activeWorkspace
            ? "The active workspace no longer matches this application draft. Return to the source page and open it again."
            : null
    );
    const navigate = useNavigate();

    const [draft, setDraft] = useState<CreatorDraft | null>(null);
    const [loading, setLoading] = useState(true);
    const [loadError, setLoadError] = useState<string | null>(null);
    const [serverValidation, setServerValidation] = useState<CreatorValidationResponse>({errors: []});
    const [serverValidationRevision, setServerValidationRevision] = useState<number | null>(null);
    const [serverValidating, setServerValidating] = useState(false);
    const [previewApplication, setPreviewApplication] = useState<Application | null>(null);
    const [previewScript, setPreviewScript] = useState<string | null>(null);
    const [previewErrors, setPreviewErrors] = useState<CreatorValidationError[]>([]);
    const [previewRateLimit, setPreviewRateLimit] = useState<{remaining: number; retryAt?: number | string} | null>(null);
    const [previewRendering, setPreviewRendering] = useState(false);
    const [previewQueued, setPreviewQueued] = useState(false);
    const [previewParameters, setPreviewParameters] = useState<ApplicationParameter[]>([]);
    const [previewMachines, setPreviewMachines] = useState<ProductV2Compute[]>([]);
    const [previewDataReady, setPreviewDataReady] = useState(false);
    const [invocationTab, setInvocationTab] = useState<InvocationTab>("invocation");
    const [saveLoading, setSaveLoading] = useState(false);
    const [customEligibility, setCustomEligibility] = useState<AppStore.AppEditorCustomEligibilityResponse | null>(null);
    const [customGroups, setCustomGroups] = useState<AppStore.AppCatalogCustomGroup[]>([]);
    const [customCategories, setCustomCategories] = useState<AppStore.AppCatalogCustomCategory[]>([]);
    const validationRequestId = useRef(0);
    const draftRevisionRef = useRef(0);
    const draftRef = useRef<CreatorDraft | null>(null);
    const lastPreviewJobRef = useRef<JobSpecification | null>(null);

    // Resizable panel state. The width drives a CSS variable so the DOM updates without
    // re-rendering the component tree.
    const panelRef = useRef<HTMLDivElement>(null);
    const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
    const isResizing = useRef(false);

    usePage("Application editor", SidebarTabId.APPLICATIONS);

    // Prevent body scroll so the shell owns the full viewport, matching the file editor.
    useEffect(() => {
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = "";
        };
    }, []);

    // Load blank defaults or canonical source through the service boundary. The draft starts
    // clean; a fork or new-version operation is never reconstructed from normalized metadata.
    useEffect(() => {
        let cancelled = false;
        if (contextError) {
            setLoading(false);
            setLoadError(contextError);
            return;
        }
        if (context.applicationKind === "managed" && !Client.userIsAdmin) {
            setLoading(false);
            setDraft(null);
            setLoadError("Only UCloud administrators can create managed applications.");
            return;
        }
        const currentDraft = draftRef.current;
        if (currentDraft?.dirty && creatorContextKey(currentDraft.context) === creatorContextKey(context)) {
            setLoading(false);
            setLoadError(null);
            return;
        }
        setLoading(true);
        setLoadError(null);
        const customContext = creatorIsCustom(context);
        Promise.all([
            creatorService.loadSource(context),
            customContext ? creatorService.loadCustomPlacement() : Promise.resolve({groups: [], categories: []}),
            customContext ? creatorService.loadCustomEligibility() : Promise.resolve(null),
        ]).then(([source, placement, eligibility]) => {
            if (cancelled) return;
            if (customContext && placement.groups.length === 0) {
                throw new Error("Create a custom application group in this workspace before creating an application.");
            }
            const categoryId = source.customMeta?.category ?? context.initialCategory;
            const categoryIsEditable = categoryId != null && placement.categories.some(category => String(category.id) === categoryId);
            if (customContext && !categoryIsEditable) {
                throw new Error("You no longer have edit permission on the selected category.");
            }
            let customMeta = source.customMeta;
            if (customMeta != null) {
                const firstEligibleProvider = eligibility?.providers.find(provider => provider.eligible)?.provider;
                const firstProvider = firstEligibleProvider ?? eligibility?.providers[0]?.provider ?? "";
                customMeta = {
                    ...customMeta,
                    provider: context.operation === "newCustom" ? customMeta.provider || firstProvider : customMeta.provider,
                    group: context.operation === "newCustom" ? customMeta.group || String(placement.groups[0]?.id ?? "") : customMeta.group,
                    category: categoryId ?? customMeta.category,
                    canPublish: eligibility?.canPublish ?? false,
                    publishedToProject: eligibility?.canPublish === true && customMeta.publishedToProject,
                };
            }
            setDraft(creatorInitialDraft(source.application, source.sourceText, context, customMeta));
            setCustomGroups(placement.groups);
            setCustomCategories(placement.categories);
            setCustomEligibility(eligibility);
            setServerValidation({errors: []});
            setServerValidationRevision(null);
            setPreviewApplication(null);
            setPreviewScript(null);
            setPreviewErrors([]);
            setPreviewQueued(false);
            setPreviewParameters([]);
            setPreviewMachines([]);
            setPreviewDataReady(false);
            lastPreviewJobRef.current = null;
            setInvocationTab("invocation");
            setLoading(false);
        }).catch(error => {
            if (cancelled) return;
            setLoading(false);
            setDraft(null);
            setLoadError(creatorLoadError(error));
        });
        return () => {
            cancelled = true;
        };
    }, [context.operation, context.applicationKind, context.workspace, context.existingName, context.existingVersion, context.provider, context.initialCategory, context.developmentTemplate, contextError]);

    useEffect(() => {
        if (draft?.view !== "editor" || invocationTab !== "preview" || previewScript == null) return;
        window.requestAnimationFrame(() => {
            document.getElementById("creator-card-invocation")?.scrollIntoView({block: "nearest"});
        });
    }, [draft?.view, invocationTab, previewScript]);

    // Theme name for the embedded Monaco editors. Matches the file editor's selector.
    const themeName = useSelector((red: ReduxObject) => red.sidebar.theme);
    draftRef.current = draft;
    if (draft) draftRevisionRef.current = draft.revision;

    // YAML source parse cycle
    // -----------------------------------------------------------------------------------------------------------------
    // The source text and the structured model are kept in sync by parsing the source text and
    // replacing the model only when parsing succeeds. On failure, the source text is retained
    // exactly and the model keeps showing the last valid state.
    //
    // The parse runs:
    //   - on a short stable-edit debounce after source text changes (YamlEditor calls onParseTick)
    //   - immediately when the YAML editor loses focus (YamlEditor calls onBlur)
    //   - immediately before preview or save (the action handlers below call runParse)
    //
    // parseSourceText never throws; it returns a result object. The draft stores parseErrors with
    // line/column so the YAML editor can place Monaco markers and the error summary can offer a
    // click-to-line action.

    const runParse = useCallback(() => {
        setDraft(current => {
            if (!current) return current;
            const text = creatorIsCustom(current.context)
                ? creatorSourceForEditor(current.sourceText)
                : current.sourceText;
            const result = parseSourceText(text);
            if (result.ok) {
                // The parsed application replaces the structured model. parameterIds are
                // regenerated for the new parameter set so selection survives across a
                // re-parse only by coincidence; visual selection is expected to reset when the
                // user edits the source by hand, because stable ids are an internal editor
                // concept that does not exist in YAML.
                const parameterIds: Record<string, string> = {};
                for (const name of result.application.parametersOrder) {
                    parameterIds[name] = current.parameterIds[name] ?? creatorStableId();
                }
                // Do not mark dirty here: a parse refreshes the model, it is not an edit. The
                // text-change handler already marked the draft dirty when the user typed.
                const application = creatorIsCustom(current.context)
                    ? {...result.application, name: creatorLogicalName(result.application.name)}
                    : result.application;
                return {
                    ...current,
                    application,
                    lastValidApplication: application,
                    sourceTextInvalid: false,
                    parseErrors: [],
                    parameterIds,
                    sourceText: text,
                    validation: emptyValidationState(),
                };
            }
            // On failure we keep the source text and do not touch `dirty` either: the text change
            // that produced the invalid source already marked it dirty.
            return {
                ...current,
                sourceTextInvalid: true,
                    parseErrors: result.errors,
                    validation: emptyValidationState(),
                };
        });
    }, []);

    // YamlEditor reports each content change. We store the text and mark the draft dirty. The
    // delayed parse runs in the editor's own debounce effect (onParseTick). Action-triggered
    // validation errors are cleared because they no longer describe the current source.
    const onSourceTextChange = useCallback((text: string) => {
        setDraft(current => {
            if (!current) return current;
            const sourceText = creatorIsCustom(current.context) ? creatorSourceForEditor(text) : text;
            if (current.sourceText === sourceText) return current;
            return {
                ...current,
                sourceText,
                dirty: true,
                revision: current.revision + 1,
                validation: emptyValidationState(),
            };
        });
    }, []);

    // The YAML editor calls onParseTick when its debounce fires. We run the parse.
    const onSourceParseTick = useCallback(() => {
        runParse();
    }, [runParse]);

    // The YAML editor calls onBlur when it loses focus. We parse immediately.
    const onSourceBlur = useCallback(() => {
        runParse();
    }, [runParse]);

    const validateDraft = useCallback(async (current: CreatorDraft): Promise<CreatorValidationResponse | null> => {
        const parsed = parseSourceText(current.sourceText);
        if (!parsed.ok) {
            setDraft(previous => {
                if (!previous || previous.sourceText !== current.sourceText) return previous;
                return {
                    ...previous,
                    sourceTextInvalid: true,
                    parseErrors: parsed.errors,
                    validation: emptyValidationState(),
                };
            });
            return null;
        }

        const application = creatorIsCustom(current.context)
            ? {...parsed.application, name: creatorLogicalName(parsed.application.name)}
            : parsed.application;
        const localValidation = validateApplicationLocal(application);
        setDraft(previous => {
            if (!previous || previous.sourceText !== current.sourceText) return previous;
            return {
                ...previous,
                application,
                lastValidApplication: application,
                sourceTextInvalid: false,
                parseErrors: [],
                validation: localValidation,
            };
        });

        const revision = current.revision;
        const requestId = validationRequestId.current + 1;
        validationRequestId.current = requestId;
        setServerValidationRevision(revision);
        setServerValidation({errors: []});
        setServerValidating(true);
        try {
            const response = await creatorService.validate(creatorValidationRequest(current));
            if (draftRevisionRef.current !== revision || validationRequestId.current !== requestId) {
                if (validationRequestId.current === requestId) setServerValidating(false);
                return null;
            }
            const normalizedResponse = {
                ...response,
                errors: response.errors.map(creatorMapValidationError),
            };
            setServerValidation(normalizedResponse);
            setServerValidationRevision(revision);
            setServerValidating(false);
            return {
                ...normalizedResponse,
                errors: [...localValidation.errors, ...normalizedResponse.errors],
            };
        } catch (error) {
            if (draftRevisionRef.current !== revision || validationRequestId.current !== requestId) {
                if (validationRequestId.current === requestId) setServerValidating(false);
                return null;
            }
            const requestError = creatorRequestError(error);
            setServerValidation({errors: [requestError]});
            setServerValidationRevision(revision);
            setServerValidating(false);
            return {errors: [requestError]};
        }
    }, []);

    // Workflow rows set `yamlFocusKey` to ask the YAML editor to jump to a parameter key. We
    // also switch to the YAML view so the editor is visible. The editor clears the key after it
    // applies the focus.
    const onOpenWorkflowYaml = useCallback((parameterName: string) => {
        setDraft(current => {
            if (!current) return current;
            return {
                ...current,
                view: "yaml",
                yamlFocusKey: parameterName,
            };
        });
    }, []);

    // The YAML editor calls this after it scrolled to the focus key.
    const onYamlFocusApplied = useCallback(() => {
        setDraft(current => {
            if (!current) return current;
            return {...current, yamlFocusKey: null};
        });
    }, []);

    // Error summary actions. A parse error switches to the YAML view; the YamlEditor reacts to a
    // focus line prop and scrolls to it.
    const [focusLine, setFocusLine] = useState<number | null>(null);
    const [focusColumn, setFocusColumn] = useState<number>(0);

    const onJumpToSourceLine = useCallback((line: number, column: number) => {
        setFocusLine(line);
        setFocusColumn(column);
        setDraft(current => {
            if (!current) return current;
            return {...current, view: "yaml"};
        });
    }, []);

    const onYamlLineFocusApplied = useCallback(() => {
        setFocusLine(null);
        setFocusColumn(0);
    }, []);

    const onFocusErrorParameter = useCallback((error: CreatorValidationError) => {
        if (error.location) {
            setFocusLine(error.location.line);
            setFocusColumn(error.location.column);
            setDraft(current => current ? {...current, view: "yaml"} : current);
            return;
        }
        setDraft(current => {
            if (!current) return current;
            if (!error.parameterName) {
                // Global error: ensure we are in the editor view and no parameter selected.
                return {
                    ...current,
                    view: "editor",
                    selection: {parameterId: null, parameterName: null},
                };
            }
            // Select the named parameter so the panel shows the relevant field.
            const id = current.parameterIds[error.parameterName] ?? null;
            return {
                ...current,
                view: "editor",
                selection: {parameterId: id, parameterName: error.parameterName},
            };
        });
        if (error.path) {
            window.setTimeout(() => {
                const field = document.querySelector<HTMLElement>(`[data-creator-field="${error.path}"]`);
                field?.scrollIntoView({block: "center"});
                field?.focus();
            }, 0);
        }
    }, []);


    // Dirty-state helpers. Each visual change writes the A2 model and marks the draft dirty.
    //
    // First-visual-change normalization: while the draft was loaded from a source the user edited
    // by hand, the in-memory `sourceText` can differ from the canonical serialization of the model.
    // The first visual change replaces the source text with the canonical form, which can drop
    // comments and custom formatting. We warn the user once before that happens. After the first
    // confirmation, `sourceNormalized` is true and no further warnings show for the same loaded
    // draft.
    //
    // The guard is in `updateApplication` because every visual change goes through it. It cannot
    // be in the draft operations themselves because those are pure and synchronous. The dialog
    // is asynchronous, so we queue the updater in a ref and apply it on confirm.
    const pendingUpdaterRef = useRef<((draft: CreatorDraft) => CreatorDraft) | null>(null);
    const normalizationDialogOpenRef = useRef(false);

    const updateApplication = useCallback((updater: (draft: CreatorDraft) => CreatorDraft) => {
        setDraft(current => {
            if (!current) return current;
            // Guard: block visual edits while the source text is invalid. The visual editor is
            // read-only in that state; the only valid edit path is the YAML text.
            if (current.sourceTextInvalid) {
                return current;
            }
            const applyVisualChange = (draft: CreatorDraft): CreatorDraft => {
                const updated = updater(draft);
                return {
                    ...updated,
                    sourceText: applicationToSourceText(updated.application),
                    sourceNormalized: true,
                    dirty: true,
                    revision: draft.revision + 1,
                };
            };

            // Visual changes always serialize the complete model. This keeps validation and save
            // requests on the same source as the controls, while YAML edits retain their exact text.
            if (current.sourceNormalized) {
                return applyVisualChange(current);
            }
            // The source has not been normalized yet. If the source text matches the canonical
            // serialization of the current model, there is nothing to warn about: normalizing
            // would not change the text. Apply directly and mark normalized so we never check
            // again for this draft.
            if (current.sourceText === applicationToSourceText(current.application)) {
                return applyVisualChange(current);
            }
            // The source differs. We must ask before the first visual change replaces it with
            // canonical YAML. Queue the updater and show the dialog once.
            if (normalizationDialogOpenRef.current) {
                return current;
            }
            pendingUpdaterRef.current = updater;
            normalizationDialogOpenRef.current = true;
            addStandardDialog({
                title: "Visual editing will normalize the source",
                message: (
                    <Text fontSize={14}>
                        Visual changes replace the YAML source with the canonical form. This can
                        remove comments and custom formatting. Continue?
                    </Text>
                ),
                confirmText: "Continue",
                cancelText: "Cancel",
                confirmButtonColor: "successMain",
                cancelButtonColor: "errorMain",
                onConfirm: () => {
                    normalizationDialogOpenRef.current = false;
                    const pending = pendingUpdaterRef.current;
                    pendingUpdaterRef.current = null;
                    if (!pending) return;
                    setDraft(d => {
                        if (!d) return d;
                        const updated = pending(d);
                        return {
                            ...updated,
                            sourceText: applicationToSourceText(updated.application),
                            dirty: true,
                            sourceNormalized: true,
                            revision: d.revision + 1,
                        };
                    });
                },
                onCancel: () => {
                    normalizationDialogOpenRef.current = false;
                    pendingUpdaterRef.current = null;
                },
            });
            return current;
        });
    }, []);

    // Selection changes update the draft without marking it dirty because they are UI state,
    // not model changes.
    const updateSelection = useCallback((updater: (draft: CreatorDraft) => CreatorDraft) => {
        setDraft(current => {
            if (!current) return current;
            return updater(current);
        });
    }, []);

    const onNameChange = useCallback((name: string) => {
        updateApplication(d => ({
            ...d,
            application: {
                ...d.application,
                name: creatorIsCustom(d.context) ? creatorLogicalName(name) : name,
                title: d.application.title || (creatorIsCustom(d.context) ? creatorLogicalName(name) : name),
            },
        }));
    }, [updateApplication]);

    const onVersionChange = useCallback((version: string) => {
        updateApplication(d => ({
            ...d,
            application: {...d.application, version},
        }));
    }, [updateApplication]);

    // Selection. The content card and the panel both use the stable id for selection. Selection
    // is UI state and does not mark the draft dirty.
    const onSelectParameter = useCallback((parameterId: string | null) => {
        updateSelection(d => draftSelectParameter(d, parameterId));
    }, [updateSelection]);

    const onMainIslandPointerDown = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
        if (draft?.view !== "editor" || draft.selection.parameterId == null) return;
        const target = event.target;
        if (target instanceof Element && target.closest("[data-row-id], #creator-error-summary")) return;
        onSelectParameter(null);
    }, [draft?.selection.parameterId, draft?.view, onSelectParameter]);

    // Feature highlight. When the user clicks a feature card or sub-section in the content area,
    // deselect any selected parameter so the metadata panel becomes visible, then scroll to and
    // animate the corresponding toggle. The deselect must happen first because the metadata panel
    // is hidden when a parameter is selected, so the toggle element is not in the DOM.
    //
    // The timeout gives React time to re-render and remove the `hidden` attribute from the metadata
    // panel before we try to scroll. requestAnimationFrame is not enough because the state update
    // is async and React may not have committed the DOM change by the next frame.
    const onFeatureHighlight = useCallback((target: CreatorHighlightTarget) => {
        updateSelection(d => draftSelectParameter(d, null));
        setTimeout(() => creatorHighlightTarget(target), 50);
    }, [updateSelection]);

    // Reorder. The content card reports the new order; the draft stores it.
    const onReorder = useCallback((newOrder: string[]) => {
        updateApplication(d => draftReorderParameters(d, newOrder));
    }, [updateApplication]);

    // Parameter editing callbacks.
    const onRenameParameter = useCallback((oldName: string, newName: string) => {
        updateApplication(d => draftRenameParameter(d, oldName, newName));
    }, [updateApplication]);

    const onUpdateBase = useCallback((
        name: string,
        patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>,
    ) => {
        updateApplication(d => draftUpdateBase(d, name, patch));
    }, [updateApplication]);

    const onDeleteParameter = useCallback((name: string) => {
        updateApplication(d => draftDeleteParameter(d, name));
    }, [updateApplication]);

    const onUpdateDefaultValue = useCallback((name: string, value: string | number | boolean | null) => {
        updateApplication(d => draftUpdateDefaultValue(d, name, value));
    }, [updateApplication]);

    const onUpdateNumeric = useCallback((
        name: string,
        patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>,
    ) => {
        updateApplication(d => draftUpdateNumericField(d, name, patch));
    }, [updateApplication]);

    const onUpdateEnumeration = useCallback((
        name: string,
        patch: { options?: A2EnumOption[]; defaultValue?: string | null },
    ) => {
        updateApplication(d => draftUpdateEnumeration(d, name, patch));
    }, [updateApplication]);

    // Widget drawer: append a new parameter and select it.
    const onAddParameter = useCallback((type: A2WidgetType) => {
        updateApplication(d => draftAddParameter(d, type));
    }, [updateApplication]);

    // Metadata callbacks. Each writes the A2 model (or customMeta) and marks the draft dirty.
    const onUpdateMetadata = useCallback((patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>) => {
        updateApplication(d => draftUpdateMetadata(d, patch));
    }, [updateApplication]);

    // Invocation editor changes. The compact Monaco editor fires on each keystroke. Validation is
    // deferred until the user requests a preview or save.
    const onUpdateInvocation = useCallback((invocation: string) => {
        updateApplication(d => draftUpdateMetadata(d, {invocation}));
    }, [updateApplication]);

    const onUpdateSoftware = useCallback((software: A2Software) => {
        updateApplication(d => draftUpdateSoftware(d, software));
    }, [updateApplication]);

    const onUpdateFeatures = useCallback((features: A2Yaml["features"]) => {
        updateApplication(d => draftUpdateFeatures(d, features));
    }, [updateApplication]);

    const onUpdateWeb = useCallback((web: A2Yaml["web"]) => {
        updateApplication(d => draftUpdateWeb(d, web));
    }, [updateApplication]);

    const onUpdateVnc = useCallback((vnc: A2Yaml["vnc"]) => {
        updateApplication(d => draftUpdateVnc(d, vnc));
    }, [updateApplication]);

    const onUpdateSsh = useCallback((ssh: A2Yaml["ssh"]) => {
        updateApplication(d => draftUpdateSsh(d, ssh));
    }, [updateApplication]);

    const onUpdateInference = useCallback((inference: A2Yaml["inference"]) => {
        updateApplication(d => draftUpdateInference(d, inference));
    }, [updateApplication]);

    const onUpdateModules = useCallback((modules: A2Yaml["modules"]) => {
        updateApplication(d => draftUpdateModules(d, modules));
    }, [updateApplication]);

    const onUpdateUcx = useCallback((ucx: A2Yaml["ucx"]) => {
        updateApplication(d => draftUpdateUcx(d, ucx));
    }, [updateApplication]);

    const onUpdateExtensions = useCallback((extensions: string[]) => {
        updateApplication(d => draftUpdateExtensions(d, extensions));
    }, [updateApplication]);

    const onUpdateEnvironment = useCallback((environment: Record<string, string>) => {
        updateApplication(d => draftUpdateEnvironment(d, environment));
    }, [updateApplication]);

    const onUpdateSbatch = useCallback((sbatch: Record<string, string>) => {
        updateApplication(d => draftUpdateSbatch(d, sbatch));
    }, [updateApplication]);

    const onUpdateCustomMeta = useCallback((patch: Partial<CreatorCustomMeta>) => {
        updateApplication(d => draftUpdateCustomMeta(d, patch));
    }, [updateApplication]);

    // View switching. Switching to the YAML view keeps the current source text if the draft was
    // already normalized; otherwise it serializes the current model so the user sees canonical
    // YAML. Switching away from the YAML view parses the source immediately so the model is
    // current before the visual editor is shown.
    const onViewChange = useCallback((view: CreatorView) => {
        setDraft(current => {
            if (!current) return current;
            if (view === "yaml") {
                return {
                    ...current,
                    view,
                    sourceText: current.sourceNormalized
                        ? current.sourceText
                        : applicationToSourceText(current.application),
                };
            }
            return {...current, view};
        });
        // Leaving the YAML view: parse the text so the model matches what the user typed.
        // We run after the state set so the parse reads the latest source text. Use a microtask
        // to let React flush the state update first.
        if (draft?.view === "yaml") {
            Promise.resolve().then(runParse);
        }
    }, [draft?.view, runParse]);

    // Toggle between editor and YAML. When in the editor, switch to YAML. When in YAML or
    // preview, switch to the editor. On the way back to the editor from YAML, parse first.
    const onToggleYaml = useCallback(() => {
        const goingToEditor = draft?.view === "yaml";
        setDraft(current => {
            if (!current) return current;
            if (current.view === "yaml") {
                return {...current, view: "editor"};
            }
            return {
                ...current,
                view: "yaml",
                sourceText: current.sourceNormalized
                    ? current.sourceText
                    : applicationToSourceText(current.application),
            };
        });
        if (goingToEditor) {
            Promise.resolve().then(runParse);
        }
    }, [draft?.view, runParse]);

    // Toggle between the editor view and the invocation-only view. The invocation view shows only
    // the invocation editor filling the content area, with the sidebar hidden.
    const onToggleInvocation = useCallback(() => {
        setDraft(current => {
            if (!current) return current;
            if (current.view === "invocation") {
                return {...current, view: "editor"};
            }
            return {...current, view: "invocation"};
        });
    }, []);

    const onPreview = useCallback(async () => {
        if (!draft || previewRendering) return;
        if (draft.view === "preview") {
            setDraft(previous => previous ? {...previous, view: "editor"} : previous);
            return;
        }
        if (draft.sourceTextInvalid) return;
        const current = draft;
        const response = await validateDraft(current);
        if (!response || draftRevisionRef.current !== current.revision) return;
        if (response.errors.length > 0 || !response.application) {
            setDraft(previous => previous ? {...previous, view: "editor"} : previous);
            return;
        }
        setPreviewApplication(response.application);
        setPreviewScript(null);
        setPreviewErrors([]);
        setPreviewRateLimit(null);
        setPreviewDataReady(false);
        setPreviewQueued(false);
        setDraft(previous => previous ? {...previous, view: "preview"} : previous);
    }, [draft, previewRendering, validateDraft]);

    const onOpenPreviewPanel = useCallback(() => {
        setDraft(previous => previous ? {...previous, view: "preview"} : previous);
    }, []);

    const onSave = useCallback(async () => {
        if (!draft || saveLoading || draft.sourceTextInvalid) return;
        const current = draft;
        const response = await validateDraft(current);
        if (!response || draftRevisionRef.current !== current.revision) return;
        if (response.errors.length > 0) {
            setDraft(previous => previous ? {...previous, view: "editor"} : previous);
            return;
        }

        setSaveLoading(true);
        try {
            await creatorService.save(
                current.application,
                current.sourceText,
                current.context,
                current.customMeta,
            );
            if (draftRevisionRef.current !== current.revision) return;
            setDraft(previous => previous ? {...previous, dirty: false} : previous);
            const savedName = creatorIsCustom(current.context)
                ? creatorInternalName(current.application.name)
                : current.application.name;
            navigate(AppRoutes.jobs.create(savedName, current.application.version));
        } catch (error) {
            const saveError = creatorRequestError(error, "SAVE_FAILED");
            setServerValidation({errors: [saveError]});
            setServerValidationRevision(current.revision);
        } finally {
            setSaveLoading(false);
        }
    }, [draft, saveLoading, validateDraft, navigate]);

    const renderPreview = useCallback(async (job: JobSpecification, current: CreatorDraft) => {
        if (!previewApplication || previewRendering) return;
        if (draftRevisionRef.current !== current.revision) return;
        lastPreviewJobRef.current = job;
        setPreviewRendering(true);
        setPreviewErrors([]);
        try {
            const response = await creatorService.renderInvocation({
                validation: creatorValidationRequest(current),
                job,
            });
            if (draftRevisionRef.current !== current.revision) return;
            setPreviewRateLimit(response.rateLimit);
            setPreviewErrors(response.errors);
            const script = response.errors.length === 0 ? response.script ?? null : null;
            setPreviewScript(script);
            if (script !== null) {
                setInvocationTab("preview");
                setDraft(previous => previous ? {...previous, view: "editor"} : previous);
            }
        } catch (error) {
            if (draftRevisionRef.current !== current.revision) return;
            setPreviewErrors([creatorRequestError(error, "PROVIDER_RENDER_FAILED")]);
        } finally {
            setPreviewRendering(false);
        }
    }, [previewApplication, previewRendering]);

    const onPreviewScript = useCallback(async (job: JobSpecification) => {
        if (!draft) return;
        await renderPreview(job, draft);
    }, [draft, renderPreview]);

    const onRerunPreview = useCallback(async () => {
        if (!draft || previewRendering || previewQueued) return;
        setPreviewQueued(true);

        if (!previewApplication) {
            const response = await validateDraft(draft);
            if (!response || draftRevisionRef.current !== draft.revision || response.errors.length > 0 || !response.application) {
                setPreviewQueued(false);
                return;
            }
            setPreviewApplication(response.application);
        }
    }, [draft, previewApplication, previewQueued, previewRendering, validateDraft]);

    useEffect(() => {
        if (!previewQueued || !draft || !previewApplication || !previewDataReady || previewMachines.length === 0) return;
        setPreviewQueued(false);
        const previousJob = lastPreviewJobRef.current;
        const smallestMachine = creatorSmallestPreviewMachine(previewMachines);
        if (!smallestMachine) return;
        const baseJob = previousJob ?? creatorPreviewBaseJob(previewApplication, smallestMachine);
        const job = creatorPreviewJobWithDefaults(baseJob, previewParameters, previewMachines);
        const requiredResourceErrors = creatorPreviewRequiredResourceErrors(job, previewParameters);
        if (requiredResourceErrors.length > 0) {
            setPreviewScript(null);
            setPreviewErrors(requiredResourceErrors);
            return;
        }
        void renderPreview(job, draft);
    }, [draft, previewApplication, previewDataReady, previewMachines, previewParameters, previewQueued, renderPreview]);

    // Resizer. Follows the same pointer-event pattern as the file editor FileTree. The handle
    // is on the left edge of the panel (the left = panel's left edge from screen). Dragging right
    // widens the panel, dragging left narrows it.
    const onResizeStart = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
        if (e.button !== 0) return;
        e.preventDefault();
        isResizing.current = true;
        window.addEventListener("pointermove", onResizeMove);
        window.addEventListener("pointerup", onResizeStop);
        window.addEventListener("pointercancel", onResizeStop);
        window.addEventListener("blur", onResizeStop);
    }, []);

    const onResizeMove = useCallback((e: PointerEvent) => {
        if (!isResizing.current || !panelRef.current) return;
        const panelRight = panelRef.current.getBoundingClientRect().right;
        const newWidth = panelRight - e.clientX;
        const clamped = Math.min(Math.max(newWidth, MIN_PANEL_WIDTH), MAX_PANEL_WIDTH);
        setPanelWidth(clamped);
    }, []);

    const onResizeStop = useCallback(() => {
        if (!isResizing.current) return;
        isResizing.current = false;
        window.removeEventListener("pointermove", onResizeMove);
        window.removeEventListener("pointerup", onResizeStop);
        window.removeEventListener("pointercancel", onResizeStop);
        window.removeEventListener("blur", onResizeStop);
    }, [onResizeMove]);

    useEffect(() => onResizeStop, [onResizeStop]);

    // Navigation protection. Warn before tab close or refresh when the draft is dirty.
    const isDirty = draft?.dirty ?? false;
    useBeforeUnload((e: BeforeUnloadEvent): BeforeUnloadEvent => {
        if (isDirty) {
            e.preventDefault();
            e.returnValue = "truthy value";
            return e;
        }
        return e;
    }, {capture: true});

    if (loading) {
        return (
            <div className={CreatorShellClass}>
                <div className={CreatorMainIslandClass}>
                    <div className={CreatorMainBodyClass}>
                        <LoadingIcon size={36} />
                    </div>
                </div>
            </div>
        );
    }

    if (loadError || contextError || draft === null) {
        const returnTo = context.returnTo?.startsWith("/") ? context.returnTo : AppRoutes.apps.landing();
        return (
            <div className={CreatorShellClass}>
                <div className={CreatorMainIslandClass}>
                    <Flex height="100%" alignItems="center" justifyContent="center" flexDirection="column" gap="16px" p="32px">
                        <Text fontSize={20} fontWeight={600}>Application creator unavailable</Text>
                        <Text color="textSecondary" textAlign="center">{loadError ?? contextError ?? "The application draft could not be loaded."}</Text>
                        <Button onClick={() => navigate(returnTo)}>Back</Button>
                    </Flex>
                </div>
            </div>
        );
    }

    const currentServerErrors = serverValidationRevision === draft.revision
        ? serverValidation.errors
        : [];
    const visibleDraft: CreatorDraft = currentServerErrors.length === 0
        ? draft
        : {
            ...draft,
            validation: {
                errors: [...draft.validation.errors, ...currentServerErrors],
            },
        };

    // Parse errors disable both actions. Semantic validation is requested only by preview or save.
    const saveDisabled = draft.sourceTextInvalid || saveLoading;
    const previewDisabled = draft.sourceTextInvalid;
    const saveTooltip = draft.sourceTextInvalid
        ? "Fix the YAML source before saving"
        : saveLoading ? "Saving application" : "Save application version";
    const previewTooltip = draft.view === "preview"
        ? "Back to editor"
        : previewDisabled
        ? "Fix the YAML source before previewing"
        : previewRendering ? "Rendering preview" : "Preview job creation";

    return (
        <div className={CreatorShellClass}>
            <div className={CreatorMainIslandClass} onPointerDown={onMainIslandPointerDown}>
                <div className={CreatorMainHeaderClass}>
                    <EditorHeader draft={draft} />
                    <Box flexGrow={1} />
                    <Flex alignItems="center" gap="4px">
                        <IconButton
                            icon="heroCodeBracket"
                            tooltip={draft.view === "yaml" ? "Back to editor" : "View YAML"}
                            onClick={onToggleYaml}
                            color={draft.view === "yaml" ? "primaryMain" : "textSecondary"}
                        />
                        <IconButton
                            icon="heroEye"
                            tooltip={previewTooltip}
                            onClick={() => { if (!previewDisabled) void onPreview(); }}
                            color={draft.view === "preview" ? "primaryMain" : "textSecondary"}
                        />
                        <TooltipV2 tooltip={saveTooltip}>
                            <Button
                                type="button"
                                color="successMain"
                                disabled={saveDisabled}
                                onClick={() => void onSave()}
                            >
                                Save
                            </Button>
                        </TooltipV2>
                    </Flex>
                </div>
                <div className={CreatorMainBodyClass} style={(draft.view === "yaml" || draft.view === "invocation") ? {display: "flex", flexDirection: "column", overflowY: "hidden"} as React.CSSProperties : undefined}>
                    {!creatorIsCustom(context) || customEligibility?.providers.some(provider => provider.eligible) ? null : (
                        <Box m="12px" mb="0" p="12px" borderRadius="6px" background="color-mix(in srgb, var(--warningMain) 12%, transparent)">
                            <Text color="warningMain">No provider in this workspace currently meets the custom container and allocation requirements. You can edit the draft, but preview and save will remain unavailable until a provider is eligible.</Text>
                        </Box>
                    )}
                    <CreatorMainContent
                        draft={visibleDraft}
                        readOnly={draft.sourceTextInvalid}
                        themeName={themeName}
                        focusLine={focusLine}
                        focusColumn={focusColumn}
                        onSelectParameter={onSelectParameter}
                        onReorder={onReorder}
                        onFeatureHighlight={onFeatureHighlight}
                        onOpenWorkflowYaml={onOpenWorkflowYaml}
                        onSourceTextChange={onSourceTextChange}
                        onSourceBlur={onSourceBlur}
                        onSourceParseTick={onSourceParseTick}
                        onYamlFocusApplied={onYamlFocusApplied}
                        onYamlLineFocusApplied={onYamlLineFocusApplied}
                        onInvocationChange={onUpdateInvocation}
                        onJumpToSourceLine={onJumpToSourceLine}
                        onFocusErrorParameter={onFocusErrorParameter}
                        onToggleInvocation={onToggleInvocation}
                        invocationTab={invocationTab}
                        onInvocationTabChange={setInvocationTab}
                        previewApplication={previewApplication}
                        previewScript={previewScript}
                        previewErrors={previewErrors}
                        previewRateLimit={previewRateLimit}
                        previewRendering={previewRendering}
                        previewQueued={previewQueued}
                        validating={serverValidating && serverValidationRevision === draft.revision}
                        onPreviewScript={onPreviewScript}
                        onRerunPreview={onRerunPreview}
                        onOpenPreviewPanel={onOpenPreviewPanel}
                        onPreviewParametersChange={setPreviewParameters}
                        onPreviewMachinesChange={setPreviewMachines}
                        onPreviewDataReady={setPreviewDataReady}
                    />
                </div>
            </div>
            {draft.view !== "yaml" && draft.view !== "invocation" && draft.view !== "preview" ? (
                <div
                    ref={panelRef}
                    className={CreatorPanelIslandClass}
                    style={{"--panel-width": `${panelWidth}px`} as React.CSSProperties}
                >
                    <div className="panel-resizer" onPointerDown={onResizeStart} />
                    <div className={CreatorPanelBodyClass}>
                        <CreatorPanel
                            draft={visibleDraft}
                            readOnly={draft.sourceTextInvalid}
                            onNameChange={onNameChange}
                            onVersionChange={onVersionChange}
                            onSelectParameter={onSelectParameter}
                            onRenameParameter={onRenameParameter}
                            onUpdateBase={onUpdateBase}
                            onDeleteParameter={onDeleteParameter}
                            onUpdateDefaultValue={onUpdateDefaultValue}
                            onUpdateNumeric={onUpdateNumeric}
                            onUpdateEnumeration={onUpdateEnumeration}
                            onUpdateMetadata={onUpdateMetadata}
                            onUpdateSoftware={onUpdateSoftware}
                            onUpdateFeatures={onUpdateFeatures}
                            onUpdateWeb={onUpdateWeb}
                            onUpdateVnc={onUpdateVnc}
                            onUpdateSsh={onUpdateSsh}
                            onUpdateInference={onUpdateInference}
                            onUpdateModules={onUpdateModules}
                            onUpdateUcx={onUpdateUcx}
                            onUpdateExtensions={onUpdateExtensions}
                            onUpdateEnvironment={onUpdateEnvironment}
                            onUpdateSbatch={onUpdateSbatch}
                            onUpdateCustomMeta={onUpdateCustomMeta}
                            onAddParameter={onAddParameter}
                            customEligibility={customEligibility}
                            customGroups={customGroups}
                            customCategories={customCategories}
                        />
                    </div>
                </div>
            ) : null}
        </div>
    );
};

// Main content area
// -------------------------------------------------------------------------------------------------------------------
// The content area holds the error summary, the Parameters card, the Invocation card, and (in
// the YAML view) the full source editor. Preview embeds the job form and provider rendering flow.

function CreatorMainContent(props: {
    draft: CreatorDraft;
    readOnly: boolean;
    themeName: string | undefined;
    focusLine: number | null;
    focusColumn: number;
    onSelectParameter: (parameterId: string | null) => void;
    onReorder: (newOrder: string[]) => void;
    onFeatureHighlight: (target: CreatorHighlightTarget) => void;
    onOpenWorkflowYaml: (parameterName: string) => void;
    onSourceTextChange: (text: string) => void;
    onSourceBlur: () => void;
    onSourceParseTick: () => void;
    onYamlFocusApplied: () => void;
    onYamlLineFocusApplied: () => void;
    onInvocationChange: (text: string) => void;
    onJumpToSourceLine: (line: number, column: number) => void;
    onFocusErrorParameter: (error: CreatorValidationError) => void;
    onToggleInvocation: () => void;
    invocationTab: InvocationTab;
    onInvocationTabChange: (tab: InvocationTab) => void;
    previewApplication: Application | null;
    previewScript: string | null;
    previewErrors: CreatorValidationError[];
    previewRateLimit: {remaining: number; retryAt?: number | string} | null;
    previewRendering: boolean;
    previewQueued: boolean;
    validating: boolean;
    onPreviewScript: (job: JobSpecification) => Promise<void>;
    onRerunPreview: () => Promise<void>;
    onOpenPreviewPanel: () => void;
    onPreviewParametersChange: (parameters: ApplicationParameter[]) => void;
    onPreviewMachinesChange: (machines: ProductV2Compute[]) => void;
    onPreviewDataReady: (ready: boolean) => void;
}): React.ReactNode {
    const {draft} = props;

    const invocationPreview = <PreviewScriptViewer
        script={props.previewScript}
        errors={props.previewErrors}
        onRerun={props.onRerunPreview}
        onOpenPreviewPanel={props.onOpenPreviewPanel}
        rerunning={props.previewRendering || props.previewQueued}
    />;

    const renderInvocationEditor = (maximized: boolean) => (
        <InvocationEditor
            invocation={draft.application.invocation}
            readOnly={props.readOnly}
            themeName={props.themeName}
            onChange={props.onInvocationChange}
            maximized={maximized}
            onToggleMaximized={props.onToggleInvocation}
            activeTab={props.invocationTab}
            onTabChange={props.onInvocationTabChange}
            preview={invocationPreview}
        />
    );

    // Keep all view surfaces mounted. In particular, the job form owns temporary widget state;
    // hiding it instead of unmounting it preserves preview values when the user returns to edit.
    const previewSurface = (
        <div className={CreatorPreviewClass} hidden={draft.view !== "preview"} style={draft.view !== "preview" ? {display: "none"} : undefined}>
            {props.previewApplication == null ? (
                <Text color="textSecondary">The application must pass validation before it can be previewed.</Text>
            ) : (
                <>
                    <div hidden={props.previewScript != null} className={PreviewJobFormClass}>
                        <JobCreate
                            previewApplication={props.previewApplication}
                            previewMode
                            previewRendering={props.previewRendering}
                            onPreviewScript={props.onPreviewScript}
                            onPreviewParametersChange={props.onPreviewParametersChange}
                            onPreviewMachinesChange={props.onPreviewMachinesChange}
                            onPreviewDataReady={props.onPreviewDataReady}
                        />
                    </div>
                    {props.previewScript != null ? (
                        <PreviewScriptViewer
                            script={props.previewScript}
                            errors={props.previewErrors}
                            onRerun={props.onRerunPreview}
                            onOpenPreviewPanel={props.onOpenPreviewPanel}
                            rerunning={props.previewRendering || props.previewQueued}
                        />
                    ) : null}
                </>
            )}
        </div>
    );

    return (
        <>
            <ErrorSummary
                draft={draft}
                onJumpToSourceLine={props.onJumpToSourceLine}
                onFocusParameter={props.onFocusErrorParameter}
                validating={props.validating}
                extraErrors={props.previewErrors}
                rateLimit={props.previewRateLimit}
            />
            {draft.view === "yaml" ? <div className={CreatorMainContentYamlClass}>
                <YamlEditor
                    sourceText={draft.sourceText}
                    sourceTextInvalid={draft.sourceTextInvalid}
                    parseErrors={draft.parseErrors}
                    yamlFocusKey={draft.yamlFocusKey}
                    focusLine={props.focusLine}
                    focusColumn={props.focusColumn}
                    readOnly={false}
                    themeName={props.themeName}
                    onChange={props.onSourceTextChange}
                    onBlur={props.onSourceBlur}
                    onParseTick={props.onSourceParseTick}
                    onFocusApplied={props.onYamlFocusApplied}
                    onLineFocusApplied={props.onYamlLineFocusApplied}
                />
            </div> : null}
            {draft.view === "invocation" ? <div className={CreatorMainContentYamlClass}>
                {renderInvocationEditor(true)}
            </div> : null}
            {previewSurface}
            {draft.view === "editor" ? <div>
                <Grid gridTemplateColumns="1fr" gap="24px" width="100%">
                    <FeatureCards draft={draft} onHighlight={props.onFeatureHighlight} />
                    <div className={CreatorCardIslandClass} id="creator-card-parameters">
                        <CreatorCardHeading>Parameters</CreatorCardHeading>
                        <ParameterContent
                            draft={draft}
                            onSelectParameter={props.onSelectParameter}
                            onReorder={props.onReorder}
                            onOpenWorkflowYaml={props.onOpenWorkflowYaml}
                        />
                    </div>
                    {renderInvocationEditor(false)}
                </Grid>
            </div> : null}
        </>
    );
}

// Column flex container for the YAML view. The YamlEditor sits without a card and fills all
// remaining vertical space so the source view uses the whole main content island. The container
// keeps the body padding from the island edges but stretches vertically.
const CreatorMainContentYamlClass = injectStyle("creator-main-content-yaml", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        min-height: 0;
        flex: 1 1 auto;
        gap: 16px;
    }
`);

const CreatorCardIslandClass = injectStyle("creator-card-island", k => `
    ${k} {
        max-width: 944px;
        background: var(--backgroundCard);
        box-shadow: var(--defaultShadow);
        border: var(--defaultCardBorder);
        border-radius: 10px;
        padding: 20px;
        box-sizing: border-box;
    }
    @media (max-width: 600px) {
        ${k} {
            padding: 16px;
        }
    }
`);

function PreviewScriptViewer(props: {
    script: string | null;
    errors: CreatorValidationError[];
    onRerun: () => Promise<void>;
    onOpenPreviewPanel: () => void;
    rerunning: boolean;
}): React.ReactNode {
    const requiresPreviewValues = props.errors.some(error => error.code === "PREVIEW_RESOURCE_REQUIRED");
    const errorKey = props.errors.map(error => `${error.code ?? ""}:${error.message}`).join("\u0000");
    const [dismissedErrorKey, setDismissedErrorKey] = useState<string | null>(null);

    useEffect(() => {
        setDismissedErrorKey(null);
    }, [errorKey]);

    const showErrors = props.errors.length > 0 && dismissedErrorKey !== errorKey;
    return (
        <div className={PreviewScriptClass}>
            <div className={PreviewScriptCodeClass}>
                {showErrors ? (
                    <Warning
                        warning="Preview could not be rendered."
                        clearWarning={() => setDismissedErrorKey(errorKey)}
                    >
                        <ul className={PreviewErrorListClass}>
                            {props.errors.map((error, index) => <li key={index}>{error.message}</li>)}
                        </ul>
                        {requiresPreviewValues ? (
                            <button type="button" className={PreviewErrorLinkClass} onClick={props.onOpenPreviewPanel}>
                                Open preview panel
                            </button>
                        ) : null}
                    </Warning>
                ) : props.script == null ? (
                    <Text color="textSecondary">
                        Generate a preview to see the rendered invocation here.
                    </Text>
                ) : <CodeSnippet lang="bash">{props.script}</CodeSnippet>}
            </div>
            <Button
                type="button"
                color="successMain"
                disabled={props.rerunning}
                onClick={() => void props.onRerun()}
            >
                {props.rerunning ? <UcxSpinner size={16} color="white" margin="0 8px 0 0" /> : null}
                {props.script == null ? "Preview script" : "Run again"}
            </Button>
        </div>
    );
}

function creatorPreviewJobWithDefaults(
    job: JobSpecification,
    parameters: ApplicationParameter[],
    machines: ProductV2Compute[],
): JobSpecification {
    const values = {...job.parameters};
    for (const parameter of parameters) {
        if (parameter.optional || values[parameter.name] !== undefined) continue;
        const value = creatorPreviewDefaultValue(parameter);
        if (value !== undefined) values[parameter.name] = value;
    }

    const smallestMachine = creatorSmallestPreviewMachine(machines);

    return {
        ...job,
        product: smallestMachine == null ? job.product : creatorPreviewProductRef(smallestMachine),
        parameters: values,
    };
}

function creatorPreviewBaseJob(application: Application, machine: ProductV2Compute): JobSpecification {
    return {
        application: {
            name: application.metadata.name,
            version: application.metadata.version,
        },
        product: creatorPreviewProductRef(machine),
        name: "preview",
        replicas: 1,
        allowDuplicateJob: true,
        parameters: {},
        resources: [],
        timeAllocation: {hours: 1, minutes: 0, seconds: 0},
    };
}

function creatorSmallestPreviewMachine(machines: ProductV2Compute[]): ProductV2Compute | null {
    return machines.reduce<ProductV2Compute | null>((smallest, machine) => {
        if (smallest == null) return machine;
        return (machine.cpu ?? Number.POSITIVE_INFINITY) < (smallest.cpu ?? Number.POSITIVE_INFINITY)
            ? machine
            : smallest;
    }, null);
}

function creatorPreviewProductRef(machine: ProductV2Compute): JobSpecification["product"] {
    return {
        provider: machine.category.provider,
        category: machine.category.name,
        id: machine.name,
    };
}

function creatorPreviewDefaultValue(parameter: ApplicationParameter): compute.AppParameterValue | undefined {
    switch (parameter.type) {
        case "text":
        case "textarea":
            return {type: "text", value: parameter.name};
        case "boolean":
            return {type: "boolean", value: true};
        case "integer":
            return {type: "integer", value: 1234};
        case "floating_point":
            return {type: "floating_point", value: 12.34};
        case "enumeration":
            return parameter.options.length === 0 ? undefined : {type: "text", value: parameter.options[0].value};
        default:
            return undefined;
    }
}

function creatorPreviewRequiredResourceErrors(
    job: JobSpecification,
    parameters: ApplicationParameter[],
): CreatorValidationError[] {
    const errors: CreatorValidationError[] = [];
    for (const parameter of parameters) {
        if (parameter.optional || !creatorPreviewRequiresResource(parameter)) continue;
        if (job.parameters[parameter.name] !== undefined) continue;
        errors.push({
            code: "PREVIEW_RESOURCE_REQUIRED",
            parameterName: parameter.name,
            message: `Set a value for "${parameter.title || parameter.name}" in the preview panel.`,
        });
    }
    return errors;
}

function creatorPreviewRequiresResource(parameter: ApplicationParameter): boolean {
    switch (parameter.type) {
        case "input_file":
        case "input_directory":
        case "peer":
        case "ingress":
        case "license_server":
        case "network_ip":
        case "private_network":
            return true;
        default:
            return false;
    }
}

const CreatorPreviewClass = injectStyle("creator-preview", k => `
    ${k} {
        min-width: 0;
    }
`);

const PreviewJobFormClass = injectStyle("creator-preview-job-form", k => `
    ${k} {
        min-width: 0;
    }
`);

const PreviewScriptClass = injectStyle("creator-preview-script", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 16px;
        max-width: 944px;
        min-width: 0;
        height: 100%;
        min-height: 0;
    }

    ${k} > button {
        flex-shrink: 0;
        align-self: flex-start;
    }
`);

const PreviewScriptCodeClass = injectStyle("creator-preview-script-code", k => `
    ${k} {
        flex: 1 1 auto;
        min-height: 0;
        overflow: auto;
    }

    ${k} > div:has(> pre) {
        height: 100%;
    }

    ${k} > div:has(> pre) > pre {
        height: 100%;
        box-sizing: border-box;
    }
`);

const PreviewErrorListClass = injectStyle("creator-preview-error-list", k => `
    ${k} {
        margin: 0 0 8px;
        padding-left: 20px;
    }
`);

const PreviewErrorLinkClass = injectStyle("creator-preview-error-link", k => `
    ${k} {
        border: 0;
        padding: 0;
        background: transparent;
        color: var(--linkColor);
        cursor: pointer;
        font: inherit;
    }

    ${k}:hover {
        color: var(--linkColorHover);
        text-decoration: underline;
    }
`);

// Properties panel
// -------------------------------------------------------------------------------------------------------------------
// The panel shows application metadata when no parameter is selected and the parameter editor
// when a parameter is selected. The styling follows Figma's properties inspector: sections with
// uppercase header bars and compact label/value rows.

function CreatorPanel(props: {
    draft: CreatorDraft;
    readOnly: boolean;
    onNameChange: (name: string) => void;
    onVersionChange: (version: string) => void;
    onSelectParameter: (parameterId: string | null) => void;
    onRenameParameter: (oldName: string, newName: string) => void;
    onUpdateBase: (name: string, patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>) => void;
    onDeleteParameter: (name: string) => void;
    onUpdateDefaultValue: (name: string, value: string | number | boolean | null) => void;
    onUpdateNumeric: (name: string, patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>) => void;
    onUpdateEnumeration: (name: string, patch: { options?: A2EnumOption[]; defaultValue?: string | null }) => void;
    onUpdateMetadata: (patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>) => void;
    onUpdateSoftware: (software: A2Software) => void;
    onUpdateFeatures: (features: A2Yaml["features"]) => void;
    onUpdateWeb: (web: A2Yaml["web"]) => void;
    onUpdateVnc: (vnc: A2Yaml["vnc"]) => void;
    onUpdateSsh: (ssh: A2Yaml["ssh"]) => void;
    onUpdateInference: (inference: A2Yaml["inference"]) => void;
    onUpdateModules: (modules: A2Yaml["modules"]) => void;
    onUpdateUcx: (ucx: A2Yaml["ucx"]) => void;
    onUpdateExtensions: (extensions: string[]) => void;
    onUpdateEnvironment: (environment: Record<string, string>) => void;
    onUpdateSbatch: (sbatch: Record<string, string>) => void;
    onUpdateCustomMeta: (patch: Partial<CreatorCustomMeta>) => void;
    onAddParameter: (type: A2WidgetType) => void;
    customEligibility: AppStore.AppEditorCustomEligibilityResponse | null;
    customGroups: AppStore.AppCatalogCustomGroup[];
    customCategories: AppStore.AppCatalogCustomCategory[];
}): React.ReactNode {
    const {draft} = props;
    const {selection} = draft;
    const scrollRef = useRef<HTMLDivElement>(null);
    const metadataScroll = useRef(0);

    // The metadata scroll position is saved continuously while the metadata panel is visible.
    // Saving in the layout effect would be too late: by the time React commits the DOM change
    // (hiding metadata, showing the parameter panel), the scroll container has already reset
    // scrollTop to 0. The onScroll handler captures the real position before the switch.
    const showingMetadata = selection.parameterId == null;
    const showingMetadataRef = useRef(showingMetadata);
    showingMetadataRef.current = showingMetadata;

    const onPanelScroll = useCallback(() => {
        const el = scrollRef.current;
        if (!el) return;
        if (showingMetadataRef.current) {
            metadataScroll.current = el.scrollTop;
        }
    }, []);

    // Restore the saved scroll position when returning to the metadata panel. The metadata
    // content is visible in the DOM by this point, so setting scrollTop works.
    React.useLayoutEffect(() => {
        if (!showingMetadata) return;
        const el = scrollRef.current;
        if (!el) return;
        el.scrollTop = metadataScroll.current;
    }, [showingMetadata]);

    return (
        <div ref={scrollRef} className={CreatorPanelScrollClass} onScroll={onPanelScroll}>
            <div hidden={!showingMetadata}>
                <MetadataPanel
                    draft={draft}
                    readOnly={props.readOnly}
                    onNameChange={props.onNameChange}
                    onVersionChange={props.onVersionChange}
                    onUpdateMetadata={props.onUpdateMetadata}
                    onUpdateSoftware={props.onUpdateSoftware}
                    onUpdateFeatures={props.onUpdateFeatures}
                    onUpdateWeb={props.onUpdateWeb}
                    onUpdateVnc={props.onUpdateVnc}
                    onUpdateSsh={props.onUpdateSsh}
                    onUpdateInference={props.onUpdateInference}
                    onUpdateModules={props.onUpdateModules}
                    onUpdateUcx={props.onUpdateUcx}
                    onUpdateExtensions={props.onUpdateExtensions}
                    onUpdateEnvironment={props.onUpdateEnvironment}
                    onUpdateSbatch={props.onUpdateSbatch}
                    onUpdateCustomMeta={props.onUpdateCustomMeta}
                    onAddParameter={props.onAddParameter}
                    customEligibility={props.customEligibility}
                    customGroups={props.customGroups}
                    customCategories={props.customCategories}
                />
            </div>
            {!showingMetadata ? (
                <ParameterPanel
                    draft={draft}
                    readOnly={props.readOnly}
                    onBack={() => props.onSelectParameter(null)}
                    onRename={props.onRenameParameter}
                    onUpdateBase={props.onUpdateBase}
                    onDelete={props.onDeleteParameter}
                    onUpdateDefaultValue={props.onUpdateDefaultValue}
                    onUpdateNumeric={props.onUpdateNumeric}
                    onUpdateEnumeration={props.onUpdateEnumeration}
                />
            ) : null}
        </div>
    );
}

// Shared headings
// -------------------------------------------------------------------------------------------------------------------

function CreatorCardHeading(props: React.PropsWithChildren<{action?: React.ReactNode}>): React.ReactNode {
    return (
        <Flex alignItems="center" gap="8px" mb="16px">
            <Text fontWeight="normal" fontSize="16px">{props.children}</Text>
            {!props.action ? null : <Box ml="auto">{props.action}</Box>}
        </Flex>
    );
}

function creatorValidationRequest(draft: CreatorDraft): CreatorValidationRequest {
    return {
        kind: creatorIsCustom(draft.context) ? "CUSTOM" : "MANAGED",
        source: draft.sourceText,
        custom: draft.customMeta ?? undefined,
    };
}

function creatorMapValidationError(error: CreatorValidationError): CreatorValidationError {
    const path = error.path ?? "";
    const parameterMatch = path.match(/^parameters\.([^.[\]]+)/);
    return {
        ...error,
        parameterName: error.parameterName ?? parameterMatch?.[1] ?? null,
        message: error.message.replace(/\bcustom-/g, ""),
    };
}

function creatorRequestError(error: unknown, code = "REQUEST_FAILED"): CreatorValidationError {
    const value = error as {response?: {why?: string}; message?: string} | null;
    const message = value?.response?.why ?? value?.message ?? "The server request failed.";
    return {
        code,
        parameterName: null,
        message: message.replace(/\bcustom-/g, ""),
    };
}

function creatorLoadError(error: unknown): string {
    return creatorRequestError(error).message;
}

function creatorContextKey(context: CreatorOperationContext): string {
    return [
        context.operation,
        context.applicationKind,
        context.workspace,
        context.existingName ?? "",
        context.existingVersion ?? "",
        context.provider ?? "",
        context.initialCategory ?? "",
    ].join("\n");
}

export default Create;
