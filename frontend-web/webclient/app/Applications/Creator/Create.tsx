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
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
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
import {inDevEnvironment, createKeyboardShortcut} from "@/UtilityFunctions";
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
    CreatorShortcutGuide,
    CreatorShortcutControl,
    CreatorShortcutHintsProvider,
    CreatorSectionKey,
    useCreatorShortcuts,
    creatorFocusSection,
} from "@/Applications/Creator/CreatorKeyboard";
import {
    FIELD_NAVIGATION_SELECTOR,
    FORM_NAVIGATION_SELECTOR,
    focusFirstNavigationTarget,
    KeyboardNavigation,
} from "@/Applications/KeyboardNavigation";
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
    draftCustomDerivedPresentation,
    draftCustomDerivedName,
    draftCustomSelectedGroup,
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
import {customApplicationsEnabled} from "@/Applications/AppStoreApi";

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
    const sourceApplicationKind = getQueryParam(search, "sourceApplicationKind");
    const workspace = getQueryParam(search, "workspace");
    const fallback = contextFromKind("blankManaged");
    if (operation !== "newManaged" && operation !== "newCustom" && operation !== "newVersion" && operation !== "fork") {
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
    if (operation === "fork" && applicationKind !== "custom") {
        return {context: fallback, error: "A fork must create a custom application."};
    }

    const context: CreatorOperationContext = {
        operation,
        applicationKind,
        workspace,
        existingName: getQueryParam(search, "name") ?? undefined,
        existingVersion: getQueryParam(search, "version") ?? undefined,
        provider: getQueryParam(search, "provider") ?? undefined,
        sourceApplicationKind: sourceApplicationKind === "managed" || sourceApplicationKind === "custom"
            ? sourceApplicationKind
            : undefined,
        sourceProvider: getQueryParam(search, "sourceProvider") ?? undefined,
        initialCategory: getQueryParam(search, "category") ?? undefined,
        returnTo: getQueryParam(search, "returnTo") ?? undefined,
    };
    if (operation === "newCustom" && !context.initialCategory) {
        return {context, error: "A custom application category is required."};
    }
    if ((operation === "newVersion" || operation === "fork") && (!context.existingName || !context.existingVersion)) {
        return {context, error: "The source application name and version are required."};
    }
    if (operation === "newVersion" && applicationKind === "custom" && !context.provider) {
        return {context, error: "The source service provider is required."};
    }
    if (operation === "fork" && context.sourceApplicationKind !== "managed" && context.sourceApplicationKind !== "custom") {
        return {context, error: "The source application kind is missing or invalid."};
    }
    if (operation === "fork" && context.sourceApplicationKind === "custom" && !context.sourceProvider) {
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
    const [inlineCreatedGroup, setInlineCreatedGroup] = useState<{id: number; title: string; description: string} | null>(null);
    const validationRequestId = useRef(0);
    const draftRevisionRef = useRef(0);
    const draftRef = useRef<CreatorDraft | null>(null);
    const lastPreviewJobRef = useRef<JobSpecification | null>(null);

    const panelRef = useRef<HTMLDivElement>(null);
    const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
    const isResizing = useRef(false);

    usePage("Application editor", SidebarTabId.APPLICATIONS);

    useEffect(() => {
        document.body.style.overflow = "hidden";
        return () => {
            document.body.style.overflow = "";
        };
    }, []);

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
        if (creatorIsCustom(context) && !customApplicationsEnabled()) {
            setLoading(false);
            setDraft(null);
            setLoadError("Custom application creation is not available to you.");
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
        ]).then(([source, placement, rawEligibility]) => {
            if (cancelled) return;
            const eligibility = rawEligibility == null ? null : {
                ...rawEligibility,
                providers: rawEligibility.providers.filter(provider => provider.provider !== ""),
            };
            const categoryId = source.customMeta?.category || context.initialCategory;
            const categoryIsEditable = categoryId != null && placement.categories.some(category => String(category.id) === categoryId);
            if (customContext && context.operation !== "fork" && !categoryIsEditable) {
                throw new Error("You no longer have edit permission on the selected category.");
            }
            let customMeta = source.customMeta;
            if (customMeta != null) {
                const firstEligibleProvider = eligibility?.providers.find(provider => provider.eligible)?.provider;
                const firstProvider = firstEligibleProvider ?? eligibility?.providers[0]?.provider ?? "";
                const autoSelectProvider = context.operation === "newCustom" || eligibility?.providers.length === 1;
                customMeta = {
                    ...customMeta,
                    provider: customMeta.provider || (autoSelectProvider ? firstProvider : ""),
                    group: context.operation === "newCustom" ? customMeta.group || String(placement.groups[0]?.id ?? "") : customMeta.group,
                    category: categoryId ?? customMeta.category,
                    canPublish: eligibility?.canPublish ?? false,
                    publishedToProject: eligibility?.canPublish === true && customMeta.publishedToProject,
                };
            }
            const installDraft = () => {
                if (cancelled) return;
                let application = source.application;
                let sourceText = source.sourceText;
                if (customMeta != null) {
                    const group = draftCustomSelectedGroup(customMeta, placement.groups);
                    application = draftCustomDerivedPresentation(application, customMeta, group);
                    const derivedName = draftCustomDerivedName(customMeta, group);
                    if (derivedName !== "" && application.name === "") {
                        application = {...application, name: derivedName};
                        sourceText = applicationToSourceText(application);
                    }
                }
                const initialDraft = creatorInitialDraft(application, sourceText, context, customMeta);
                setDraft({
                    ...initialDraft,
                    placementGroups: placement.groups,
                    placementCreatedGroup: null,
                    ...(context.operation === "fork" ? {dirty: true, sourceNormalized: true} : {}),
                });
                setCustomGroups(placement.groups);
                setCustomCategories(placement.categories);
                setCustomEligibility(eligibility);
                setInlineCreatedGroup(null);
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
            };
            installDraft();
        }).catch(error => {
            if (cancelled) return;
            setLoading(false);
            setDraft(null);
            setLoadError(creatorLoadError(error));
        });
        return () => {
            cancelled = true;
        };
    }, [context.operation, context.applicationKind, context.workspace, context.existingName, context.existingVersion, context.provider, context.sourceApplicationKind, context.sourceProvider, context.initialCategory, context.developmentTemplate, contextError, navigate]);

    const refreshPlacement = useCallback(async () => {
        if (!creatorIsCustom(context)) return;
        try {
            const placement = await creatorService.loadCustomPlacement();
            setCustomGroups(placement.groups);
            setCustomCategories(placement.categories);
        } catch {
        }
    }, [context.operation, context.applicationKind]);

    useEffect(() => {
        void refreshPlacement();
    }, [projectId, refreshPlacement]);

    const onInlineCreatedGroup = useCallback((group: {id: number; title: string; description: string} | null) => {
        setInlineCreatedGroup(group);
    }, []);

    useEffect(() => {
        if (draft?.view !== "editor" || invocationTab !== "preview" || previewScript == null) return;
        window.requestAnimationFrame(() => {
            document.getElementById("creator-card-invocation")?.scrollIntoView({block: "nearest"});
        });
    }, [draft?.view, invocationTab, previewScript]);

    const themeName = useSelector((red: ReduxObject) => red.sidebar.theme);
    draftRef.current = draft;
    if (draft) draftRevisionRef.current = draft.revision;

    const runParse = useCallback(() => {
        setDraft(current => {
            if (!current) return current;
            const text = creatorIsCustom(current.context)
                ? creatorSourceForEditor(current.sourceText)
                : current.sourceText;
            const result = parseSourceText(text);
            if (result.ok) {
                const parameterIds: Record<string, string> = {};
                for (const name of result.application.parametersOrder) {
                    parameterIds[name] = current.parameterIds[name] ?? creatorStableId();
                }
                let application = result.application;
                let nameManuallySet = current.nameManuallySet;
                if (creatorIsCustom(current.context) && current.customMeta != null) {
                    const group = draftCustomSelectedGroup(
                        current.customMeta,
                        current.placementGroups,
                        current.placementCreatedGroup,
                    );
                    application = draftCustomDerivedPresentation(application, current.customMeta, group);
                    const derivedName = draftCustomDerivedName(current.customMeta, group);
                    if (application.name === derivedName) {
                        nameManuallySet = false;
                    } else if (application.name !== "" && application.name !== current.application.name) {
                        nameManuallySet = true;
                    } else if (!nameManuallySet && application.name === "" && derivedName !== "") {
                        application = {...application, name: derivedName};
                    }
                }
                application = creatorIsCustom(current.context)
                    ? {...application, name: creatorLogicalName(application.name)}
                    : application;
                return {
                    ...current,
                    application,
                    lastValidApplication: application,
                    nameManuallySet,
                    sourceTextInvalid: false,
                    parseErrors: [],
                    parameterIds,
                    sourceText: text,
                    validation: emptyValidationState(),
                };
            }
            return {
                ...current,
                sourceTextInvalid: true,
                    parseErrors: result.errors,
                    validation: emptyValidationState(),
                };
        });
    }, []);

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

    const onSourceParseTick = useCallback(() => {
        runParse();
    }, [runParse]);

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

    const onYamlFocusApplied = useCallback(() => {
        setDraft(current => {
            if (!current) return current;
            return {...current, yamlFocusKey: null};
        });
    }, []);

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
                return {
                    ...current,
                    view: "editor",
                    selection: {parameterId: null, parameterName: null},
                };
            }
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

    const pendingUpdaterRef = useRef<((draft: CreatorDraft) => CreatorDraft) | null>(null);
    const normalizationDialogOpenRef = useRef(false);

    const updateApplication = useCallback((updater: (draft: CreatorDraft) => CreatorDraft) => {
        setDraft(current => {
            if (!current) return current;
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

            if (current.sourceNormalized) {
                return applyVisualChange(current);
            }
            if (current.sourceText === applicationToSourceText(current.application)) {
                return applyVisualChange(current);
            }
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

    const updateSelection = useCallback((updater: (draft: CreatorDraft) => CreatorDraft) => {
        setDraft(current => {
            if (!current) return current;
            return updater(current);
        });
    }, []);

    const onNameChange = useCallback((name: string) => {
        updateApplication(d => ({
            ...d,
            nameManuallySet: true,
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

    const onSelectParameter = useCallback((parameterId: string | null) => {
        updateSelection(d => draftSelectParameter(d, parameterId));
    }, [updateSelection]);

    const onMainIslandPointerDown = useCallback((event: React.PointerEvent<HTMLDivElement>) => {
        if (draft?.view !== "editor" || draft.selection.parameterId == null) return;
        const target = event.target;
        if (target instanceof Element && target.closest("[data-row-id], #creator-error-summary")) return;
        onSelectParameter(null);
    }, [draft?.selection.parameterId, draft?.view, onSelectParameter]);

    useEffect(() => {
        if (draft?.view !== "editor") return;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key !== "Escape") return;
            if (document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            const target = event.target instanceof HTMLElement ? event.target : null;
            if (target?.closest("[data-row-id]")) return;
            if (target?.isContentEditable || target instanceof HTMLInputElement ||
                target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
                if (target.getAttribute("role") === "switch") return;
                (target as HTMLElement).blur();
                return;
            }
            const current = draftRef.current;
            if (current?.selection.parameterId == null) return;
            event.preventDefault();
            updateSelection(d => draftSelectParameter(d, null));
        };
        document.addEventListener("keydown", onKeyDown);
        return () => document.removeEventListener("keydown", onKeyDown);
    }, [draft?.view, updateSelection]);

    useEffect(() => {
        if (draft?.view !== "editor" && draft?.view !== "preview") return;
        const view = draft.view;
        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
            if (event.metaKey || event.ctrlKey || event.altKey) return;
            if (document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            const active = document.activeElement;
            if (active?.closest("[data-row-id]")) return;
            if (active?.closest(FORM_NAVIGATION_SELECTOR)) return;
            if (active instanceof HTMLTextAreaElement || active instanceof HTMLSelectElement) return;
            if (active instanceof HTMLInputElement && !active.readOnly) return;
            if (active instanceof HTMLElement && active.isContentEditable) return;
            if (view === "editor") {
                const rows = Array.from(document.querySelectorAll<HTMLElement>("[data-row-id]"))
                    .filter(element => element.offsetParent !== null);
                const row = rows.find(element => element.getAttribute("data-selected") === "true") ?? rows[0];
                if (!row) return;
                event.preventDefault();
                const parameterId = row.getAttribute("data-row-id");
                if (parameterId != null) {
                    updateSelection(d => draftSelectParameter(d, parameterId));
                }
                row.focus();
                row.scrollIntoView({block: "nearest"});
            } else {
                const field = focusFirstNavigationTarget(document.body, FIELD_NAVIGATION_SELECTOR);
                if (!field) return;
                event.preventDefault();
                field.scrollIntoView({block: "nearest"});
            }
        };
        document.addEventListener("keydown", onKeyDown, true);
        return () => document.removeEventListener("keydown", onKeyDown, true);
    }, [draft?.view, updateSelection]);

    const onFeatureHighlight = useCallback((target: CreatorHighlightTarget) => {
        updateSelection(d => draftSelectParameter(d, null));
        setTimeout(() => creatorHighlightTarget(target), 50);
    }, [updateSelection]);

    const onReorder = useCallback((newOrder: string[]) => {
        updateApplication(d => draftReorderParameters(d, newOrder));
    }, [updateApplication]);

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

    const focusAddedRowRef = useRef(false);

    const onAddParameter = useCallback((type: A2WidgetType) => {
        focusAddedRowRef.current = true;
        updateApplication(d => draftAddParameter(d, type));
    }, [updateApplication]);

    useEffect(() => {
        if (!focusAddedRowRef.current) return;
        focusAddedRowRef.current = false;
        const parameterId = draft?.selection.parameterId;
        if (parameterId == null) return;
        window.requestAnimationFrame(() => {
            const row = document.querySelector<HTMLElement>(`[data-row-id="${parameterId}"]`);
            row?.scrollIntoView({block: "nearest"});
            row?.focus();
        });
    }, [draft?.selection.parameterId]);

    const onUpdateMetadata = useCallback((patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>) => {
        updateApplication(d => draftUpdateMetadata(d, patch));
    }, [updateApplication]);

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
        updateApplication(d => {
            const updated = draftUpdateCustomMeta(d, patch);
            if (updated === d || updated.customMeta == null) return updated;
            if (patch.group !== undefined || patch.flavor !== undefined) {
                const group = draftCustomSelectedGroup(
                    updated.customMeta,
                    customGroups,
                    inlineCreatedGroup,
                );
                return {
                    ...updated,
                    application: draftCustomDerivedPresentation(updated.application, updated.customMeta, group),
                };
            }
            return updated;
        });
    }, [updateApplication, customGroups, inlineCreatedGroup]);

    useEffect(() => {
        setDraft(current => {
            if (!current || current.placementGroups === customGroups) return current;
            return {...current, placementGroups: customGroups};
        });
    }, [customGroups]);

    useEffect(() => {
        setDraft(current => {
            if (!current || current.placementCreatedGroup === inlineCreatedGroup) return current;
            return {...current, placementCreatedGroup: inlineCreatedGroup};
        });
    }, [inlineCreatedGroup]);

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
        if (draft?.view === "yaml") {
            Promise.resolve().then(runParse);
        }
    }, [draft?.view, runParse]);

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
        const parsed = parseSourceText(current.sourceText);
        if (!parsed.ok) return;
        const savedApplication = creatorIsCustom(current.context)
            ? {...parsed.application, name: creatorLogicalName(parsed.application.name)}
            : parsed.application;

        setSaveLoading(true);
        try {
            await creatorService.save(
                savedApplication,
                current.sourceText,
                current.context,
                current.customMeta,
            );
            if (draftRevisionRef.current !== current.revision) return;
            setDraft(previous => previous ? {...previous, dirty: false} : previous);
            const savedName = creatorIsCustom(current.context)
                ? creatorInternalName(savedApplication.name)
                : savedApplication.name;
            navigate(AppRoutes.jobs.create(savedName, savedApplication.version));
        } catch (error) {
            const saveError = creatorRequestError(error, "SAVE_FAILED");
            setServerValidation({errors: [saveError]});
            setServerValidationRevision(current.revision);
        } finally {
            setSaveLoading(false);
        }
    }, [draft, saveLoading, validateDraft, navigate]);

    const onReturnToEditor = useCallback(() => {
        setDraft(current => {
            if (!current || current.view === "editor") return current;
            return {...current, view: "editor"};
        });
        if (draft?.view === "yaml") {
            Promise.resolve().then(runParse);
        }
    }, [draft?.view, runParse]);

    const onFocusSection = useCallback((key: CreatorSectionKey) => {
        updateSelection(d => draftSelectParameter(d, null));
        window.requestAnimationFrame(() => creatorFocusSection(key));
    }, [updateSelection]);

    const shortcutsEnabled = draft != null && !loading && loadError == null && contextError == null;
    const hintsVisible = useCreatorShortcuts(
        draft?.view ?? null,
        shortcutsEnabled,
        {
            onToggleYaml,
            onToggleInvocation,
            onTogglePreview: () => void onPreview(),
            onReturnToEditor,
            onSave: () => void onSave(),
            onFocusSection,
        },
    );

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
        const current = draftRef.current;
        if (!current) return;
        await renderPreview(job, current);
    }, [renderPreview]);

    const onRerunPreview = useCallback(async () => {
        const current = draftRef.current;
        if (!current || previewRendering || previewQueued) return;
        setPreviewQueued(true);

        if (!previewApplication) {
            const response = await validateDraft(current);
            if (!response || draftRevisionRef.current !== current.revision || response.errors.length > 0 || !response.application) {
                setPreviewQueued(false);
                return;
            }
            setPreviewApplication(response.application);
        }
    }, [previewApplication, previewQueued, previewRendering, validateDraft]);

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

    const saveDisabled = draft.sourceTextInvalid || saveLoading;
    const previewDisabled = draft.sourceTextInvalid;
    const saveTooltip = draft.sourceTextInvalid
        ? "Fix the YAML source before saving"
        : saveLoading ? "Saving application" : `Save application version (${createKeyboardShortcut("S", ["ctrl"])})`;
    const previewTooltip = draft.view === "preview"
        ? "Back to editor"
        : previewDisabled
        ? "Fix the YAML source before previewing"
        : previewRendering ? "Rendering preview" : `Preview job creation (${createKeyboardShortcut("P", ["ctrl", "alt"])})`;
    const yamlTooltip = draft.view === "yaml"
        ? `Back to editor (${createKeyboardShortcut("E", ["ctrl", "alt"])})`
        : `View YAML (${createKeyboardShortcut("Y", ["ctrl", "alt"])})`;

    return (
        <CreatorShortcutHintsProvider visible={hintsVisible}>
        <div className={CreatorShellClass}>
            <div className={CreatorMainIslandClass} onPointerDown={onMainIslandPointerDown}>
                <div className={CreatorMainHeaderClass}>
                    <EditorHeader draft={draft} />
                    <Box flexGrow={1} />
                    <Flex alignItems="center" gap="4px">
                        <CreatorShortcutControl shortcut="Y">
                            <IconButton
                                icon="heroCodeBracket"
                                tooltip={yamlTooltip}
                                onClick={onToggleYaml}
                                color={draft.view === "yaml" ? "primaryMain" : "textSecondary"}
                            />
                        </CreatorShortcutControl>
                        <CreatorShortcutControl shortcut="P">
                            <IconButton
                                icon="heroEye"
                                tooltip={previewTooltip}
                                onClick={() => { if (!previewDisabled) void onPreview(); }}
                                color={draft.view === "preview" ? "primaryMain" : "textSecondary"}
                            />
                        </CreatorShortcutControl>
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
                    {!creatorIsCustom(context) || context.operation === "fork" || customEligibility?.providers.some(provider => provider.eligible) ? null : (
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
                            refreshPlacement={refreshPlacement}
                            onInlineCreatedGroup={onInlineCreatedGroup}
                        />
                    </div>
                    <CreatorShortcutGuide />
                </div>
            ) : null}
        </div>
        </CreatorShortcutHintsProvider>
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

    const invocationParameters = React.useMemo(
        () => draft.application.parametersOrder.flatMap(name => {
            const param = draft.application.parameters[name];
            return param ? [{name, param}] : [];
        }),
        [draft.application.parametersOrder, draft.application.parameters],
    );

    const invocationPreview = useMemo(() => (
        <PreviewScriptViewer
            script={props.previewScript}
            errors={props.previewErrors}
            onRerun={props.onRerunPreview}
            onOpenPreviewPanel={props.onOpenPreviewPanel}
            rerunning={props.previewRendering || props.previewQueued}
        />
    ), [
        props.previewScript,
        props.previewErrors,
        props.onRerunPreview,
        props.onOpenPreviewPanel,
        props.previewRendering,
        props.previewQueued,
    ]);

    const renderInvocationEditor = (maximized: boolean) => (
        <InvocationEditor
            invocation={draft.application.invocation}
            parameters={invocationParameters}
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

    const previewSurface = useMemo(() => (
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
    ), [
        draft.view,
        props.previewApplication,
        props.previewScript,
        props.previewErrors,
        props.previewRendering,
        props.previewQueued,
        props.onPreviewScript,
        props.onRerunPreview,
        props.onOpenPreviewPanel,
        props.onPreviewParametersChange,
        props.onPreviewMachinesChange,
        props.onPreviewDataReady,
    ]);

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
    refreshPlacement: () => Promise<void>;
    onInlineCreatedGroup: (group: {id: number; title: string; description: string} | null) => void;
}): React.ReactNode {
    const {draft} = props;
    const {selection} = draft;
    const scrollRef = useRef<HTMLDivElement>(null);
    const metadataScroll = useRef(0);

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

    React.useLayoutEffect(() => {
        if (!showingMetadata) return;
        const el = scrollRef.current;
        if (!el) return;
        el.scrollTop = metadataScroll.current;
    }, [showingMetadata]);

    return (
        <div ref={scrollRef} className={CreatorPanelScrollClass} onScroll={onPanelScroll}>
            <KeyboardNavigation
                navigationSelector={FORM_NAVIGATION_SELECTOR}
                horizontalSelector={FORM_NAVIGATION_SELECTOR}
            >
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
                        refreshPlacement={props.refreshPlacement}
                        onInlineCreatedGroup={props.onInlineCreatedGroup}
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
            </KeyboardNavigation>
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
        context.sourceApplicationKind ?? "",
        context.sourceProvider ?? "",
        context.initialCategory ?? "",
    ].join("\n");
}

export default Create;
