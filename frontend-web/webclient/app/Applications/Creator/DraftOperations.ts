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
import type {AppCatalogCustomGroup} from "@/Applications/AppStoreApi";

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

export function draftRenameParameter(
    draft: CreatorDraft,
    oldName: string,
    newName: string,
): CreatorDraft {
    const param = draft.application.parameters[oldName];
    if (!param || oldName === newName) return draft;

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

    const invocation = rewriteInvocationReferences(
        draft.application.invocation ?? "",
        oldName,
        newName,
    );

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

export function draftReorderParameters(
    draft: CreatorDraft,
    newOrder: string[],
): CreatorDraft {
    const application: A2Yaml = {
        ...draft.application,
        parametersOrder: newOrder,
    };
    return clearValidation({...draft, application});
}

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

export function draftUpdateEnumeration(
    draft: CreatorDraft,
    parameterName: string,
    patch: { options?: A2EnumOption[]; defaultValue?: string | null },
): CreatorDraft {
    const param = draft.application.parameters[parameterName];
    if (!param || param.type !== "Enumeration") return draft;
    const options = patch.options ?? param.options;
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

export function draftUpdateMetadata(
    draft: CreatorDraft,
    patch: Partial<Pick<A2Yaml, "title" | "description" | "license" | "documentation" | "invocation">>,
): CreatorDraft {
    const application: A2Yaml = {...draft.application, ...patch};
    if (application.title === "") application.title = null;
    if (application.description === "") application.description = null;
    if (application.license === "") application.license = null;
    if (application.documentation === "") application.documentation = null;
    return clearValidation({...draft, application});
}

export function draftUpdateSoftware(draft: CreatorDraft, software: A2Yaml["software"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, software};
    return clearValidation({...draft, application});
}

export function draftUpdateFeatures(draft: CreatorDraft, features: A2Yaml["features"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, features};
    return clearValidation({...draft, application});
}

export function draftUpdateWeb(draft: CreatorDraft, web: A2Yaml["web"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, web};
    return clearValidation({...draft, application});
}

export function draftUpdateVnc(draft: CreatorDraft, vnc: A2Yaml["vnc"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, vnc};
    return clearValidation({...draft, application});
}

export function draftUpdateSsh(draft: CreatorDraft, ssh: A2Yaml["ssh"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ssh};
    return clearValidation({...draft, application});
}

export function draftUpdateInference(draft: CreatorDraft, inference: A2Yaml["inference"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, inference};
    return clearValidation({...draft, application});
}

export function draftUpdateModules(draft: CreatorDraft, modules: A2Yaml["modules"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, modules};
    return clearValidation({...draft, application});
}

export function draftUpdateUcx(draft: CreatorDraft, ucx: A2Yaml["ucx"]): CreatorDraft {
    const application: A2Yaml = {...draft.application, ucx};
    return clearValidation({...draft, application});
}

export function draftUpdateExtensions(draft: CreatorDraft, extensions: string[]): CreatorDraft {
    const application: A2Yaml = {...draft.application, extensions};
    return clearValidation({...draft, application});
}

export function draftUpdateEnvironment(draft: CreatorDraft, environment: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, environment};
    return clearValidation({...draft, application});
}

export function draftUpdateSbatch(draft: CreatorDraft, sbatch: Record<string, string>): CreatorDraft {
    const application: A2Yaml = {...draft.application, sbatch};
    return clearValidation({...draft, application});
}

export function draftUpdateCustomMeta(
    draft: CreatorDraft,
    patch: Partial<CreatorCustomMeta>,
): CreatorDraft {
    if (!draft.customMeta) return draft;
    const customMeta = {...draft.customMeta, ...patch};
    let application = draft.application;
    if ((patch.group !== undefined || patch.flavor !== undefined) && !draft.nameManuallySet) {
        const group = draftCustomSelectedGroup(customMeta, draft.placementGroups, draft.placementCreatedGroup);
        const name = draftCustomDerivedName(customMeta, group);
        if (name !== "") application = {...application, name};
    }
    return {...draft, customMeta, application, validation: emptyValidationState()};
}

// Derived name for custom applications
// -------------------------------------------------------------------------------------------------------------------
// The application name is derived from the group title and the flavor name. Both parts are
// lower-kebab-cased. The default flavor adds no suffix; any other flavor appends "-$flavor".

const draftCustomDefaultFlavor = "default";

export function creatorKebabCase(value: string): string {
    return value
        .normalize("NFKD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/[^a-zA-Z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "")
        .toLowerCase();
}

export function draftCustomDerivedName(
    customMeta: {flavor: string},
    group: {title: string} | null,
): string {
    const groupPart = creatorKebabCase(group?.title ?? "");
    const flavor = customMeta.flavor.trim();
    const suffix = flavor === "" || flavor.toLowerCase() === draftCustomDefaultFlavor
        ? ""
        : `-${creatorKebabCase(flavor)}`;
    return `${groupPart}${suffix}`;
}

// Derived presentation for custom applications
// -------------------------------------------------------------------------------------------------------------------
// Custom applications do not present their own title, description, license, or documentation.
// The presentation is derived from the placement metadata instead: the title joins the group
// title and the flavor name, the description is the group description, and license and
// documentation are absent. The derivation is applied live so the YAML view always shows the
// values that will be saved. Absent fields use null, never an empty string: the backend rejects
// a present-but-empty value.
export function draftCustomDerivedPresentation(
    application: A2Yaml,
    customMeta: {flavor: string} | null,
    group: {title: string; description: string} | null,
): A2Yaml {
    const flavor = customMeta?.flavor ?? "";
    const titleFlavor = flavor.trim().toLowerCase() === draftCustomDefaultFlavor ? "" : flavor;
    const titleParts = [group?.title ?? "", titleFlavor].filter(part => part.trim() !== "");
    const description = group?.description ?? "";
    return {
        ...application,
        title: titleParts.length === 0 ? null : titleParts.join(" "),
        description: description === "" ? null : description,
        license: null,
        documentation: null,
    };
}

export function draftCustomSelectedGroup(
    meta: {group: string},
    groups: AppCatalogCustomGroup[],
    createdGroup?: {id: number; title: string; description: string} | null,
): {title: string; description: string} | null {
    if (meta.group === "") return null;
    const selected = groups.find(group => String(group.id) === meta.group)
        ?? (createdGroup != null && String(createdGroup.id) === meta.group
            ? {specification: {title: createdGroup.title, description: createdGroup.description}}
            : null);
    return selected?.specification ?? null;
}

function clearValidation(draft: CreatorDraft): CreatorDraft {
    return {
        ...draft,
        validation: emptyValidationState(),
    };
}

export function nameForId(draft: CreatorDraft, parameterId: string): string | null {
    for (const name of draft.application.parametersOrder) {
        if (draft.parameterIds[name] === parameterId) return name;
    }
    return null;
}
