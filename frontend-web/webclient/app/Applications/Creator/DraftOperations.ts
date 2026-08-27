// Draft operations
// =====================================================================================================================
// Pure functions that update the CreatorDraft for each editor action. Each function takes the
// current draft and returns a new draft with the change applied. The caller marks the draft dirty.
//
// These operations own the stable-id bookkeeping. Renaming updates the parameterIds key but keeps
// the same value. Deletion removes the id. Reorder changes only parametersOrder.
//
// On rename, exact static references in the invocation are rewritten. On delete, references remain
// unchanged because there is no safe replacement value.

import {A2Yaml, A2Parameter, A2EnumOption} from "@/Applications/Creator/A2";
import {CreatorDraft, CreatorCustomMeta, creatorStableId, emptyValidationState} from "@/Applications/Creator/Draft";
import {rewriteInvocationReferences} from "@/Applications/Creator/ReferenceTracking";
import {createWidgetParameter, uniqueWidgetName, A2WidgetType} from "@/Applications/Creator/WidgetDefaults";

// Update a common (base) field on a parameter.
export function draftUpdateBase(
    draft: CreatorDraft,
    parameterName: string,
    patch: Partial<Pick<A2Parameter, "title" | "description" | "optional">>,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: {...param, ...patch},
        },
    };
    return clearValidation({...draft, application});
}

// Rename a parameter. Rewrites exact static invocation references from old to new. If the new
// name is empty, a duplicate, or identical to the old name, the rename does not proceed. The
// caller is responsible for showing validation errors; this function only applies valid renames.
export function draftRenameParameter(
    draft: CreatorDraft,
    oldName: string,
    newName: string,
): CreatorDraft {
    const param = draft.application.parameters[oldName];
    if (!param || oldName === newName) return draft;

    // Reject a duplicate name: the new name already belongs to a different parameter.
    if (newName && draft.application.parameters[newName] != null) return draft;

    const order = draft.application.parametersOrder.map(n => (n === oldName ? newName : n));
    const parameters: Record<string, A2Parameter> = {};
    for (const n of order) {
        if (n === newName) {
            parameters[n] = param;
        } else {
            parameters[n] = draft.application.parameters[n];
        }
    }

    // Rewrite invocation references.
    const invocation = rewriteInvocationReferences(
        draft.application.invocation ?? "",
        oldName,
        newName,
    );

    // Update stable id map.
    const parameterIds: Record<string, string> = {};
    for (const n of order) {
        if (n === newName) {
            parameterIds[newName] = draft.parameterIds[oldName] ?? creatorStableId();
        } else {
            parameterIds[n] = draft.parameterIds[n] ?? creatorStableId();
        }
    }

    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder: order,
        invocation,
    };

    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection: {
            parameterId: draft.selection.parameterId,
            parameterName: newName,
        },
    });
}

// Delete a parameter. References in the invocation remain unchanged. The validator reports them
// as errors.
export function draftDeleteParameter(
    draft: CreatorDraft,
    parameterName: string,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;

    const order = draft.application.parametersOrder.filter(n => n !== parameterName);
    const parameters: Record<string, A2Parameter> = {...draft.application.parameters};
    delete parameters[parameterName];

    const parameterIds: Record<string, string> = {};
    for (const n of order) {
        parameterIds[n] = draft.parameterIds[n] ?? creatorStableId();
    }

    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder: order,
    };

    // Clear selection if the deleted parameter was selected.
    const selection = draft.selection.parameterId === (draft.parameterIds[parameterName] ?? "")
        ? {parameterId: null, parameterName: null}
        : draft.selection;

    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection,
    });
}

// Reorder parameters. The new order is an array of parameter names in the desired display order.
export function draftReorderParameters(
    draft: CreatorDraft,
    newOrder: string[],
): CreatorDraft {
    const application: A2Yaml = {
        ...draft.application,
        parametersOrder: newOrder,
    };
    // Reordering is an edit, so clear any validation result from an earlier preview or save.
    return clearValidation({...draft, application});
}

// Append a new parameter of the given widget type. Generates a unique name, assigns a stable id,
// appends the row to the end of the declaration order, and selects the new row. Returns the
// updated draft with the new parameter selected.
export function draftAddParameter(
    draft: CreatorDraft,
    type: A2WidgetType,
): CreatorDraft {
    const name = uniqueWidgetName(type, draft.application.parametersOrder);
    const param = createWidgetParameter(type);
    const parameters: Record<string, A2Parameter> = {
        ...draft.application.parameters,
        [name]: param,
    };
    const parametersOrder = [...draft.application.parametersOrder, name];
    const parameterIds = {
        ...draft.parameterIds,
        [name]: creatorStableId(),
    };
    const application: A2Yaml = {
        ...draft.application,
        parameters,
        parametersOrder,
    };
    const newId = parameterIds[name];
    return clearValidation({
        ...draft,
        application,
        parameterIds,
        selection: {parameterId: newId, parameterName: name},
    });
}

// Update the default value for a Text, TextArea, or Boolean parameter.
export function draftUpdateDefaultValue(
    draft: CreatorDraft,
    parameterName: string,
    defaultValue: string | number | boolean | null,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    if (!("defaultValue" in param)) return draft;
    const updated = {...param, defaultValue} as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

// Update numeric fields (min, max, step, defaultValue) on an Integer or FloatingPoint parameter.
export function draftUpdateNumericField(
    draft: CreatorDraft,
    parameterName: string,
    patch: Partial<{ min: number | null; max: number | null; step: number | null; defaultValue: number | null }>,
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param) return draft;
    if (param.type !== "Integer" && param.type !== "FloatingPoint") return draft;
    const updated: A2Parameter = {
        ...param,
        ...patch,
    } as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

// Update an enumeration parameter. Replaces the full option list and/or default value.
export function draftUpdateEnumeration(
    draft: CreatorDraft,
    parameterName: string,
    patch: { options?: A2EnumOption[]; defaultValue?: string | null },
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param || param.type !== "Enumeration") return draft;
    const options = patch.options ?? param.options;
    // Use "in" so that an explicit null clears the default. A null coalesce would keep the old value.
    const defaultValue = "defaultValue" in patch ? patch.defaultValue ?? null : param.defaultValue;
    const updated: A2Parameter = {
        ...param,
        options,
        defaultValue,
    } as A2Parameter;
    const application: A2Yaml = {
        ...draft.application,
        parameters: {
            ...draft.application.parameters,
            [parameterName]: updated,
        },
    };
    return clearValidation({...draft, application});
}

// Set the selection to a parameter by stable id. Returns the draft with the selection updated. If
// the id does not match any current parameter, the selection is cleared.
export function draftSelectParameter(draft: CreatorDraft, parameterId: string | null): CreatorDraft {
    if (parameterId == null) {
        return {...draft, selection: {parameterId: null, parameterName: null}};
    }
    const name = nameForId(draft, parameterId);
    return {
        ...draft,
        selection: {
            parameterId: name ? parameterId : null,
            parameterName: name,
        },
    };
}

// Metadata operations
// -------------------------------------------------------------------------------------------------------------------
// These functions update metadata fields on the A2Yaml or the customMeta. They follow the same
// pattern as the parameter operations: take the draft and clear validation after the change.

// Update simple scalar metadata fields on the A2Yaml.
export function draftUpdateMetadata(
    draft: CreatorDraft,
    patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>,
): CreatorDraft {
    const application: A2Yaml = {...draft.application, ...patch};
    return clearValidation({...draft, application});
}

// Update the software configuration. Managed applications can change the kind; custom
// applications always use Container.
export function draftUpdateSoftware(draft: CreatorDraft, software: A2Yaml["software"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, software};
    return clearValidation({...draft, application});
}

// Update the features block. Missing fields default to false.
export function draftUpdateFeatures(draft: CreatorDraft, features: A2Yaml["features"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, features};
    return clearValidation({...draft, application});
}

// Update the web block.
export function draftUpdateWeb(draft: CreatorDraft, web: A2Yaml["web"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, web};
    return clearValidation({...draft, application});
}

// Update the vnc block.
export function draftUpdateVnc(draft: CreatorDraft, vnc: A2Yaml["vnc"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, vnc};
    return clearValidation({...draft, application});
}

// Update the ssh block.
export function draftUpdateSsh(draft: CreatorDraft, ssh: A2Yaml["ssh"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ssh};
    return clearValidation({...draft, application});
}

// Update the inference block.
export function draftUpdateInference(draft: CreatorDraft, inference: A2Yaml["inference"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, inference};
    return clearValidation({...draft, application});
}

// Update the modules block (managed only).
export function draftUpdateModules(draft: CreatorDraft, modules: A2Yaml["modules"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, modules};
    return clearValidation({...draft, application});
}

// Update the ucx block (managed only).
export function draftUpdateUcx(draft: CreatorDraft, ucx: A2Yaml["ucx"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ucx};
    return clearValidation({...draft, application});
}

// Update the extensions list (managed only).
export function draftUpdateExtensions(draft: CreatorDraft, extensions: string[]): CreatorDraft {
    const application: A2Yaml = {...draft.application, extensions};
    return clearValidation({...draft, application});
}

// Replace the full environment map (ordered key-value pairs).
export function draftUpdateEnvironment(draft: CreatorDraft, environment: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, environment};
    return clearValidation({...draft, application});
}

// Replace the full sbatch map (ordered key-value pairs).
export function draftUpdateSbatch(draft: CreatorDraft, sbatch: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, sbatch};
    return clearValidation({...draft, application});
}

// Update the custom-only metadata (provider, category, group, flavor, publication). Only used
// for custom applications.
export function draftUpdateCustomMeta(
    draft: CreatorDraft,
    patch: Partial<CreatorCustomMeta>,
): CreatorDraft {
    if (!draft.customMeta) return draft;
    const customMeta = {...draft.customMeta, ...patch};
    return {...draft, customMeta, validation: emptyValidationState()};
}

// Editing clears the result of the last action-triggered validation. The next preview or save
// validates the current draft again.
function clearValidation(draft: CreatorDraft): CreatorDraft {
    return {
        ...draft,
        validation: emptyValidationState(),
    };
}

// Helper: find the parameter name for a stable id. Returns null if not found.
export function nameForId(draft: CreatorDraft, parameterId: string): string | null {
    for (const name of draft.application.parametersOrder) {
        if (draft.parameterIds[name] === parameterId) return name;
    }
    return null;
}
