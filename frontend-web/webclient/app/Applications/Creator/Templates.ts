// Creator templates
// =====================================================================================================================
// These templates provide valid minimum A2 values for the explicit development entry points: a
// blank custom application, a blank managed application, and a full parameter example.
//
// The template service also satisfies the CreatorService boundary for focused development work.
//
// The `custom-` name prefix is an internal storage detail. The interface never displays it.
// Users enter and see only the logical application name. The blank custom template uses an empty
// name; backend source retrieval strips the prefix before returning the draft.

import {A2Yaml} from "@/Applications/Creator/A2";
import {
    CreatorOperationContext,
    CreatorService,
    CreatorCustomMeta,
    emptyValidationState,
    CreatorValidationResponse,
    CreatorRenderResponse,
} from "@/Applications/Creator/Draft";
// Parse and serialization live in SourceParser.ts so the YAML editor and the template service
// share one implementation. This module only keeps the template constructors.
import {applicationToSourceText, parseSourceText} from "@/Applications/Creator/SourceParser";

// Blank templates
// -------------------------------------------------------------------------------------------------------------------

export function blankCustomApplication(provider: string): A2Yaml {
    return {
        name: "",
        version: "1.0",
        software: {type: "Container", image: ""},
        title: "",
        description: "",
        parameters: {},
        parametersOrder: [],
        sbatch: {},
        invocation: "",
        environment: {},
        extensions: [],
    };
}

export function blankManagedApplication(): A2Yaml {
    return {
        name: "",
        version: "1.0",
        software: {type: "Container", image: ""},
        title: "",
        description: "",
        parameters: {},
        parametersOrder: [],
        sbatch: {},
        invocation: "",
        environment: {},
        extensions: [],
    };
}

// Full template: one parameter of each supported standard type
// -------------------------------------------------------------------------------------------------------------------

export function fullParameterTemplate(): A2Yaml {
    const parameters: Record<string, any> = {
        textField: {
            type: "Text",
            title: "Text field",
            description: "A short text parameter.",
            optional: true,
        },
        textAreaField: {
            type: "TextArea",
            title: "Text area",
            description: "A multi-line text parameter.",
            optional: true,
        },
        boolField: {
            type: "Boolean",
            title: "Boolean",
            description: "A boolean parameter.",
            defaultValue: false,
            optional: true,
        },
        integerField: {
            type: "Integer",
            title: "Integer",
            description: "An integer parameter.",
            defaultValue: 0,
            min: 0,
            max: 100,
            step: 1,
            optional: true,
        },
        floatField: {
            type: "FloatingPoint",
            title: "Floating point",
            description: "A floating point parameter.",
            defaultValue: 0.0,
            min: 0,
            max: 1,
            step: 0.1,
            optional: true,
        },
        enumerationField: {
            type: "Enumeration",
            title: "Enumeration",
            description: "An enumeration parameter.",
            defaultValue: "a",
            options: [
                {title: "Option A", value: "a"},
                {title: "Option B", value: "b"},
            ],
            optional: true,
        },
        fileField: {
            type: "File",
            title: "File",
            description: "A file parameter.",
            optional: true,
        },
        directoryField: {
            type: "Directory",
            title: "Directory",
            description: "A directory parameter.",
            optional: true,
        },
        licenseField: {
            type: "License",
            title: "License",
            description: "A license server parameter.",
            optional: true,
        },
        publicIpField: {
            type: "PublicIP",
            title: "Public IP",
            description: "A public IP address parameter.",
            optional: true,
        },
        workflowField: {
            type: "Workflow",
            title: "Workflow",
            description: "A workflow parameter.",
            optional: false,
            parameters: {},
        },
    };

    return {
        name: "example-app",
        version: "1.0",
        software: {type: "Container", image: "dreg.cloud.sdu.dk/ucloud-apps/example:1.0"},
        title: "Example application",
        description: "An example application with each supported standard parameter type.",
        parameters,
        parametersOrder: [
            "textField",
            "textAreaField",
            "boolField",
            "integerField",
            "floatField",
            "enumerationField",
            "fileField",
            "directoryField",
            "licenseField",
            "jobField",
            "publicIpField",
            "workflowField",
        ],
        sbatch: {},
        invocation: "{{ textField }} --flag {{ integerField }}",
        environment: {},
        extensions: [],
    };
}

// Source text serialization
// -------------------------------------------------------------------------------------------------------------------
// The parse and serialization helpers moved to SourceParser.ts in milestone 4 so the YAML editor
// and the template service share one implementation. The old `applicationToSourceText` and
// `parseSourceText` symbols are re-exported here for callers that imported them from Templates.

export {applicationToSourceText, parseSourceText};


// Custom application metadata template
// -------------------------------------------------------------------------------------------------------------------
// Provider, category, group, flavor, and publication are not part of the A2 YAML. Templates supply
// placeholder values for focused development work. Publication is unavailable in a personal
// workspace; the templates default to personal so publication stays false.

export function templateCustomMeta(provider: string): CreatorCustomMeta {
    return {
        provider: provider || "",
        category: "",
        group: "",
        flavor: "",
        publishedToProject: false,
        canPublish: false,
    };
}

// Template service
// -------------------------------------------------------------------------------------------------------------------

export function templateApplicationForContext(context: CreatorOperationContext): A2Yaml {
    switch (context.operation) {
        case "newCustom":
            return blankCustomApplication(context.provider ?? "");
        case "newManaged":
            return blankManagedApplication();
        case "newVersion":
        case "fork":
            return fullParameterTemplate();
    }
}

export function templateCustomMetaForContext(context: CreatorOperationContext): CreatorCustomMeta | null {
    switch (context.operation) {
        case "newCustom":
            return templateCustomMeta(context.provider ?? "");
        case "newManaged":
            return null;
        case "newVersion":
            return null;
        case "fork":
            return templateCustomMeta(context.provider ?? "");
    }
}

export const templateService: CreatorService = {
    async loadSource(context) {
        const application = templateApplicationForContext(context);
        const customMeta = templateCustomMetaForContext(context);
        return {
            application,
            sourceText: applicationToSourceText(application),
            customMeta,
        };
    },

    async validate(_request): Promise<CreatorValidationResponse> {
        return {errors: emptyValidationState().errors};
    },

    async renderInvocation(_request): Promise<CreatorRenderResponse> {
        return {errors: [], rateLimit: {limit: 0, remaining: 0}};
    },

    async loadCustomEligibility() {
        return {providers: [], canPublish: false};
    },

    async loadCustomPlacement() {
        return {groups: [], categories: []};
    },

    async save(_application, _sourceText, _context, _customMeta) {
        // Placeholder for focused development templates.
    },
};
