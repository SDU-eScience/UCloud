// Backend service boundary for the application creator
// =====================================================================================================================
// The editor keeps source editing local. This module is the only place where it chooses an RPC
// operation, converts custom placement metadata, or adds the internal custom application prefix.

import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {
    CreatorCustomMeta,
    CreatorOperationContext,
    CreatorRenderRequest,
    CreatorRenderResponse,
    CreatorService,
    CreatorValidationError,
    CreatorValidationRequest,
    CreatorValidationResponse,
    creatorIsCustom,
} from "@/Applications/Creator/Draft";
import {applicationToSourceText, parseSourceText} from "@/Applications/Creator/SourceParser";
import {templateApplicationForContext, templateCustomMetaForContext} from "@/Applications/Creator/Templates";
import {A2Yaml} from "@/Applications/Creator/A2";
import {fetchAll} from "@/Utilities/PageUtilities";

export const creatorService: CreatorService = {
    async loadSource(context) {
        const createsBlankDraft = context.operation === "newManaged" || context.operation === "newCustom";
        if (context.developmentTemplate || createsBlankDraft) {
            const application = templateApplicationForContext(context);
            return {
                application,
                sourceText: sourceTextForApplication(application),
                customMeta: templateCustomMetaForContext(context),
            };
        }

        if (!context.existingName || !context.existingVersion) {
            throw new Error("The source application name and version are required.");
        }
        if (creatorIsCustom(context) && !context.provider) {
            throw new Error("The source service provider is required.");
        }

        const response = await callAPI(AppStore.retrieveEditorSource({
            kind: creatorIsCustom(context) ? "CUSTOM" : "MANAGED",
            name: context.existingName,
            version: context.existingVersion,
            serviceProvider: context.provider,
            intent: context.operation === "fork" ? "FORK" : "EDIT",
        }));
        const source = creatorIsCustom(context) ? creatorSourceForEditor(response.source) : response.source;
        const parsed = parseSourceText(source);
        if (!parsed.ok) {
            throw new Error("The application source could not be loaded.");
        }
        return {
            application: parsed.application,
            sourceText: source,
            customMeta: response.custom ? customMetaFromResponse(response.custom) : null,
        };
    },

    async validate(request): Promise<CreatorValidationResponse> {
        const response = await callAPI(AppStore.validateEditor({
            kind: request.kind,
            source: request.kind === "CUSTOM" ? creatorSourceForEditor(request.source) : request.source,
            custom: request.custom ? customMetaForRequest(request.custom) : undefined,
        }));
        return {
            application: response.application,
            errors: (response.errors ?? []).map(creatorValidationError),
        };
    },

    async renderInvocation(request: CreatorRenderRequest): Promise<CreatorRenderResponse> {
        const response = await callAPI(AppStore.renderEditorInvocation({
            validation: {
                kind: request.validation.kind,
                source: request.validation.kind === "CUSTOM"
                    ? creatorSourceForEditor(request.validation.source)
                    : request.validation.source,
                custom: request.validation.custom ? customMetaForRequest(request.validation.custom) : undefined,
            },
            job: request.job,
        }));
        return {
            script: response.script,
            errors: (response.errors ?? []).map(creatorValidationError),
            rateLimit: response.rateLimit ?? {limit: 0, remaining: 0},
        };
    },

    async loadCustomEligibility() {
        return await callAPI(AppStore.retrieveEditorEligibility());
    },

    async loadCustomPlacement() {
        const [groups, categories] = await Promise.all([
            fetchAll(next => callAPI(AppStore.browseCustomGroups({itemsPerPage: 250, next}))),
            fetchAll(next => callAPI(AppStore.browseCustomCategories({itemsPerPage: 250, next}))),
        ]);
        return {
            groups,
            categories: categories.filter(category => category.permissions.myself.includes("EDIT")),
        };
    },

    async save(application, sourceText, context, customMeta) {
        if (creatorIsCustom(context)) {
            if (!customMeta) throw new Error("Custom application placement metadata is missing.");
            await callAPI(AppStore.createCustomApplication({
                ...application,
                name: internalCustomName(application.name),
                serviceProvider: customMeta.provider,
                publishedToProject: customMeta.publishedToProject,
                flavorName: customMeta.flavor,
                groupId: numericId(customMeta.group),
                categoryId: numericId(customMeta.category),
            }));
            return;
        }

        const response = await AppStore.createFromSource(sourceText);
        if (response.error) throw new Error(response.error);
    },
};

function customMetaForRequest(meta: CreatorCustomMeta): AppStore.AppEditorCustomMetadata {
    return {
        serviceProvider: meta.provider,
        publishedToProject: meta.publishedToProject,
        flavorName: meta.flavor,
        groupId: numericId(meta.group),
        categoryId: numericId(meta.category),
    };
}

function customMetaFromResponse(meta: AppStore.AppEditorCustomMetadata): CreatorCustomMeta {
    return {
        provider: meta.serviceProvider,
        category: String(meta.categoryId),
        group: String(meta.groupId),
        flavor: meta.flavorName,
        publishedToProject: meta.publishedToProject,
        canPublish: false,
    };
}

function numericId(value: string): number {
    const parsed = Number.parseInt(value, 10);
    return Number.isFinite(parsed) ? parsed : 0;
}

function creatorValidationError(error: AppStore.AppEditorValidationError): CreatorValidationError {
    return {
        code: error.code,
        path: error.path,
        location: error.location,
        parameterName: null,
        message: error.message.replace(/\bcustom-/g, ""),
    };
}

function internalCustomName(name: string): string {
    return name.startsWith("custom-") ? name : `custom-${name}`;
}

export function creatorInternalName(name: string): string {
    return internalCustomName(name);
}

export function creatorLogicalName(name: string): string {
    return name.replace(/^custom-/, "");
}

export function creatorSourceForEditor(source: string): string {
    const withoutQuotedPrefix = source.replace(
        /^(\s*name:\s*)(["'])custom-([^"'\r\n]*)\2(\s*(?:#.*)?)$/m,
        (_match: string, prefix: string, quote: string, name: string, suffix: string) => `${prefix}${quote}${name}${quote}${suffix}`,
    );
    return withoutQuotedPrefix.replace(
        /^(\s*name:\s*)custom-([^\s#\r\n]*)(\s*(?:#.*)?)$/m,
        (_match: string, prefix: string, name: string, suffix: string) => `${prefix}${name}${suffix}`,
    );
}

function sourceTextForApplication(application: A2Yaml): string {
    return applicationToSourceText(application);
}
