// Widget drawer parameter defaults
// =====================================================================================================================
// The widget drawer appends one parameter per click. Each new parameter needs a valid default
// with a unique name. The name starts with a short type-based identifier and appends a number when
// the base name is already in use: "text", "text2", "text3".
//
// Enumerations start with no options. The new row is selected so the user can enter the required
// options immediately. We do not add placeholder enum options that could be mistaken for user data.
//
// Workflow is a valid A2 type but does not appear in the first widget drawer. It is excluded from
// the drawer definitions.

import {A2Parameter, A2EnumOption} from "@/Applications/Creator/A2";

// The widget drawer groups items by purpose. "basic" holds simple value types. "resources" holds
// UCloud resource types that reference storage or network.
export type WidgetDrawerGroup = "basic" | "resources";

export interface WidgetDrawerItem {
    // The A2 parameter type to create.
    type: A2WidgetType;
    // A short label for the drawer button.
    label: string;
    // A one-line description shown under the label.
    description: string;
    // The drawer group the item belongs to.
    group: WidgetDrawerGroup;
}

// The parameter types the drawer can create. Workflow is intentionally excluded.
export type A2WidgetType =
    | "Text"
    | "TextArea"
    | "Boolean"
    | "Integer"
    | "FloatingPoint"
    | "Enumeration"
    | "File"
    | "Directory"
    | "License"
    | "PublicIP";

// The drawer items, grouped by purpose. Basic values come first, then UCloud resources. The
// order is the explicit display order.
export const WIDGET_DRAWER_ITEMS: WidgetDrawerItem[] = [
    {type: "Text", label: "Text", description: "A short single-line text value.", group: "basic"},
    {type: "TextArea", label: "Text area", description: "A multi-line text value.", group: "basic"},
    {type: "Boolean", label: "Boolean", description: "A true or false toggle.", group: "basic"},
    {type: "Integer", label: "Integer", description: "A whole number with optional range.", group: "basic"},
    {type: "FloatingPoint", label: "Floating point", description: "A decimal number with optional range.", group: "basic"},
    {type: "Enumeration", label: "Enumeration", description: "A fixed list of named options.", group: "basic"},
    {type: "File", label: "File", description: "An input file from UCloud storage.", group: "resources"},
    {type: "Directory", label: "Directory", description: "An input directory from UCloud storage.", group: "resources"},
    {type: "License", label: "License", description: "A license server the job can use.", group: "resources"},
    {type: "PublicIP", label: "Public IP", description: "A public IP address for the job.", group: "resources"},
];

// The base name used for each type. The first parameter of a type uses this name. Subsequent
// parameters append a number.
export const WIDGET_BASE_NAMES: Record<A2WidgetType, string> = {
    Text: "text",
    TextArea: "textArea",
    Boolean: "boolean",
    Integer: "integer",
    FloatingPoint: "floatingPoint",
    Enumeration: "enumeration",
    File: "file",
    Directory: "directory",
    License: "license",
    PublicIP: "publicIp",
};

// Default titles for each widget type. These give the user a useful starting point.
export const WIDGET_DEFAULT_TITLES: Record<A2WidgetType, string> = {
    Text: "Text",
    TextArea: "Text area",
    Boolean: "Boolean",
    Integer: "Integer",
    FloatingPoint: "Floating point",
    Enumeration: "Enumeration",
    File: "File",
    Directory: "Directory",
    License: "License",
    PublicIP: "Public IP",
};

// Generate a unique parameter name for the given type. Starts with the base name and appends a
// number when the base name or a numbered variant is already in use.
export function uniqueWidgetName(type: A2WidgetType, existingNames: string[]): string {
    const base = WIDGET_BASE_NAMES[type];
    const taken = new Set(existingNames);
    if (!taken.has(base)) return base;
    let n = 2;
    while (taken.has(`${base}${n}`)) n++;
    return `${base}${n}`;
}

// Create a valid default parameter of the given type. Enumerations start with an empty option list
// and no default; the user enters the options in the selected row's property panel. All parameters
// default to optional so the application starts valid.
export function createWidgetParameter(type: A2WidgetType): A2Parameter {
    const title = WIDGET_DEFAULT_TITLES[type];
    const common = {title, description: "", optional: true};
    switch (type) {
        case "Text":
            return {...common, type: "Text", defaultValue: null};
        case "TextArea":
            return {...common, type: "TextArea", defaultValue: null};
        case "Boolean":
            return {...common, type: "Boolean", defaultValue: false};
        case "Integer":
            return {...common, type: "Integer", defaultValue: 0, min: null, max: null, step: null};
        case "FloatingPoint":
            return {...common, type: "FloatingPoint", defaultValue: 0, min: null, max: null, step: null};
        case "Enumeration":
            // No placeholder options. The user enters real options in the property panel.
            return {...common, type: "Enumeration", options: [] as A2EnumOption[], defaultValue: null};
        case "File":
            return {...common, type: "File"};
        case "Directory":
            return {...common, type: "Directory"};
        case "License":
            return {...common, type: "License"};
        case "PublicIP":
            return {...common, type: "PublicIP"};
    }
}
