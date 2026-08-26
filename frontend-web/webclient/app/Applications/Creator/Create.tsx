// Creator editor page
// =====================================================================================================================
// This page implements the editor shell for the custom application creator. It owns one local
// draft, renders a full-viewport two-island layout, and connects the dirty-state and navigation
// protection. Milestone 1 uses templates only. All service calls go through the CreatorService
// boundary so template implementations can satisfy them now and real operations can satisfy
// them in milestone 5.
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
import {useLocation, useBeforeUnload} from "react-router-dom";
import {useSelector} from "react-redux";
import {Box, Button, Flex, Grid, Text} from "@/ui-components";
import {IconButton} from "@/ui-components/IconButton";
import {TooltipV2} from "@/ui-components/Tooltip";
import {injectStyle} from "@/Unstyled";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {addStandardDialog} from "@/UtilityComponents";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {
    CreatorDraft,
    CreatorOperationContext,
    CreatorView,
    CreatorCustomMeta,
    creatorInitialDraft,
    creatorStableId,
} from "@/Applications/Creator/Draft";
import {templateService} from "@/Applications/Creator/Templates";
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
import {InvocationEditor} from "@/Applications/Creator/InvocationEditor";
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
            return {operation: "newCustom", provider: "aalborg"};
        case "blankManaged":
            return {operation: "newManaged"};
        case "fullTemplate":
            return {operation: "newVersion", existingName: "example-app", existingVersion: "1.0"};
    }
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
    const kind = kindFromQuery(location.search);

    const [draft, setDraft] = useState<CreatorDraft | null>(null);
    const [loading, setLoading] = useState(true);

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

    // Load the template when the kind changes. The template service returns the A2 model and
    // source text. The draft starts clean.
    useEffect(() => {
        let cancelled = false;
        setLoading(true);
        const context = contextFromKind(kind);
        templateService.loadSource(context).then(({application, sourceText, customMeta}) => {
            if (cancelled) return;
            setDraft(creatorInitialDraft(application, sourceText, context, customMeta));
            setLoading(false);
        });
        return () => {
            cancelled = true;
        };
    }, [kind]);

    // Theme name for the embedded Monaco editors. Matches the file editor's selector.
    const themeName = useSelector((red: ReduxObject) => red.sidebar.theme);

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
            const text = current.sourceText;
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
                return {
                    ...current,
                    application: result.application,
                    lastValidApplication: result.application,
                    sourceTextInvalid: false,
                    parseErrors: [],
                    parameterIds,
                };
            }
            // On failure we keep the source text and do not touch `dirty` either: the text change
            // that produced the invalid source already marked it dirty.
            return {
                ...current,
                sourceTextInvalid: true,
                parseErrors: result.errors,
            };
        });
    }, []);

    // YamlEditor reports each content change. We store the text and mark the draft dirty. The
    // delayed parse runs in the editor's own debounce effect (onParseTick). Parse errors are not
    // cleared here: they stay visible until the next parse runs and either clears them or
    // replaces them with new ones.
    const onSourceTextChange = useCallback((text: string) => {
        setDraft(current => {
            if (!current) return current;
            return {...current, sourceText: text, dirty: true};
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

    const onFocusErrorParameter = useCallback((parameterName: string | null) => {
        setDraft(current => {
            if (!current) return current;
            if (!parameterName) {
                // Global error: ensure we are in the editor view and no parameter selected.
                return {
                    ...current,
                    view: "editor",
                    selection: {parameterId: null, parameterName: null},
                };
            }
            // Select the named parameter so the panel shows the relevant field.
            const id = current.parameterIds[parameterName] ?? null;
            return {
                ...current,
                view: "editor",
                selection: {parameterId: id, parameterName},
            };
        });
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
            // If the source was already normalized for this loaded draft, apply the change
            // directly. The serialized canonical YAML will be written back when the user switches
            // to the YAML view.
            if (current.sourceNormalized) {
                return {...updater(current), dirty: true};
            }
            // The source has not been normalized yet. If the source text matches the canonical
            // serialization of the current model, there is nothing to warn about: normalizing
            // would not change the text. Apply directly and mark normalized so we never check
            // again for this draft.
            if (current.sourceText === applicationToSourceText(current.application)) {
                return {...updater(current), dirty: true, sourceNormalized: true};
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
                        return {...pending(d), dirty: true, sourceNormalized: true};
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
                name,
                title: d.application.title || name,
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

    // Invocation editor changes. The compact Monaco editor fires on each keystroke. We reuse
    // the metadata update path; local validation is cheap and keeps the reference errors fresh.
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

    // Save. Runs the parse so the model and source are current, then defers to milestone 5.
    const onSave = useCallback(() => {
        runParse();
        // Save is out of scope for milestone 4. The button is disabled until backend support
        // arrives in milestone 5.
    }, [runParse]);

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

    if (loading || draft === null) {
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

    // Save and preview are disabled when the source text is invalid. Save itself remains a
    // placeholder until backend support arrives in milestone 5; it is left disabled here because
    // the create-version operation does not exist yet.
    const saveDisabled = draft.sourceTextInvalid || true;
    const previewDisabled = draft.sourceTextInvalid;
    const saveTooltip = draft.sourceTextInvalid
        ? "Fix the YAML source before saving"
        : "Save is a placeholder in this milestone";
    const previewTooltip = previewDisabled
        ? "Fix the YAML source before previewing"
        : "Preview is a placeholder in this milestone";

    return (
        <div className={CreatorShellClass}>
            <div className={CreatorMainIslandClass}>
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
                            onClick={() => { if (!previewDisabled) onViewChange("preview"); }}
                            color={draft.view === "preview" ? "primaryMain" : "textSecondary"}
                        />
                        <TooltipV2 tooltip={saveTooltip}>
                            <Button
                                type="button"
                                color="successMain"
                                disabled={saveDisabled}
                                onClick={onSave}
                            >
                                Save
                            </Button>
                        </TooltipV2>
                    </Flex>
                </div>
                <div className={CreatorMainBodyClass} style={(draft.view === "yaml" || draft.view === "invocation") ? {display: "flex", flexDirection: "column", overflowY: "hidden"} as React.CSSProperties : undefined}>
                    <CreatorMainContent
                        draft={draft}
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
                    />
                </div>
            </div>
            {draft.view !== "yaml" && draft.view !== "invocation" ? (
                <div
                    ref={panelRef}
                    className={CreatorPanelIslandClass}
                    style={{"--panel-width": `${panelWidth}px`} as React.CSSProperties}
                >
                    <div className="panel-resizer" onPointerDown={onResizeStart} />
                    <div className={CreatorPanelBodyClass}>
                        <CreatorPanel
                            draft={draft}
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
// the YAML view) the full source editor. Preview remains a placeholder for milestone 5.

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
    onFocusErrorParameter: (parameterName: string | null) => void;
    onToggleInvocation: () => void;
}): React.ReactNode {
    const {draft} = props;

    // The error summary appears at the top in every view. Parse errors and validation errors are
    // merged. Clicking a parse error switches to the YAML view and asks the YamlEditor to scroll
    // to the line via the focusLine prop. Clicking a semantic error selects the named parameter.
    if (draft.view === "yaml") {
        return (
            <div className={CreatorMainContentYamlClass}>
                <ErrorSummary
                    draft={draft}
                    onJumpToSourceLine={props.onJumpToSourceLine}
                    onFocusParameter={props.onFocusErrorParameter}
                />
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
            </div>
        );
    }

    if (draft.view === "invocation") {
        return (
            <div className={CreatorMainContentYamlClass}>
                <ErrorSummary
                    draft={draft}
                    onJumpToSourceLine={props.onJumpToSourceLine}
                    onFocusParameter={props.onFocusErrorParameter}
                />
                <InvocationEditor
                    invocation={draft.application.invocation}
                    readOnly={props.readOnly}
                    themeName={props.themeName}
                    onChange={props.onInvocationChange}
                    maximized={true}
                    onToggleMaximized={props.onToggleInvocation}
                />
            </div>
        );
    }

    return (
        <Grid gridTemplateColumns="1fr" gap="24px" width="100%">
            <ErrorSummary
                draft={draft}
                onJumpToSourceLine={props.onJumpToSourceLine}
                onFocusParameter={props.onFocusErrorParameter}
            />
            {draft.view === "preview" ? <PreviewPlaceholder /> : null}
            {draft.view === "editor" ? (
                <>
                    {draft.sourceTextInvalid ? <InvalidSourceBanner /> : null}
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
                    <InvocationEditor
                        invocation={draft.application.invocation}
                        readOnly={props.readOnly}
                        themeName={props.themeName}
                        onChange={props.onInvocationChange}
                        maximized={false}
                        onToggleMaximized={props.onToggleInvocation}
                    />
                </>
            ) : null}
        </Grid>
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

// Banner shown above the visual editor when the YAML source is invalid. Explains that visual
// editing is read-only until the source is fixed, matching the root design.
function InvalidSourceBanner(): React.ReactNode {
    return (
        <div className={InvalidSourceBannerClass} role="status">
            <Text fontSize={13} color="errorMain">
                The YAML source is invalid. Visual editing is read-only. Switch to the YAML view to
                fix the source.
            </Text>
        </div>
    );
}

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

function PreviewPlaceholder(): React.ReactNode {
    return (
        <div className={CreatorCardIslandClass}>
            <CreatorCardHeading>Preview</CreatorCardHeading>
            <Text color="textSecondary">
                The full job preview is a placeholder in this milestone.
            </Text>
        </div>
    );
}

const InvalidSourceBannerClass = injectStyle("creator-invalid-source-banner", k => `
    ${k} {
        max-width: 944px;
        padding: 8px 12px;
        background: color-mix(in srgb, var(--errorMain) 12%, transparent);
        border: 1px solid color-mix(in srgb, var(--errorMain) 35%, var(--borderColor));
        border-radius: 6px;
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

export default Create;
