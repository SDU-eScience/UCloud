// Local parameter validation
// =====================================================================================================================
// The editor runs local field validation after each stable edit. It does not call backend
// validation. The validation covers: empty names, duplicate names, numeric ranges, enumeration
// option duplicates, enumeration defaults, and unresolved invocation references.
//
// The validation returns a list of errors. Each error has a parameter name (or null for a
// global error) and a message. The editor shows field errors in the parameter panel and summary
// errors at the top of the page.

import {A2Yaml} from "@/Applications/Creator/A2";
import {CreatorValidationError, CreatorValidationState} from "@/Applications/Creator/Draft";

// Validate the full application. Returns errors for all parameters and references.
export function validateApplicationLocal(application: A2Yaml): CreatorValidationState {
    const errors: CreatorValidationError[] = [];

    const seenNames = new Set<string>();
    const names = application.parametersOrder;

    for (const name of names) {
        const param = application.parameters[name];
        if (!param) continue;

        // Empty name.
        if (!name || name.trim() === "") {
            errors.push({parameterName: name, message: "Parameter name must not be empty."});
            continue;
        }

        // Duplicate name.
        if (seenNames.has(name)) {
            errors.push({parameterName: name, message: `Duplicate parameter name: "${name}".`});
        }
        seenNames.add(name);

        // Type-specific validation.
        switch (param.type) {
            case "Integer":
            case "FloatingPoint":
                validateNumeric(param, name, errors);
                break;
            case "Enumeration":
                validateEnumeration(param, name, errors);
                break;
        }
    }

    // Reference validation: invocation references must point to existing parameters. The valid
    // name set excludes empty names. After a delete, the old references remain in the invocation
    // text and are reported as unresolved.
    const validNames = new Set(names.filter(n => n && n.trim() !== ""));
    validateInvocationReferences(application, validNames, errors);

    return {errors};
}

function validateNumeric(
    param: { type: string; min?: number | null; max?: number | null; step?: number | null; defaultValue?: number | null },
    name: string,
    errors: CreatorValidationError[],
): void {
    const min = param.min;
    const max = param.max;
    if (min != null && max != null && min > max) {
        errors.push({parameterName: name, message: "Minimum must not be greater than maximum."});
    }
    if (param.step != null && param.step <= 0) {
        errors.push({parameterName: name, message: "Step must be greater than zero."});
    }
    const def = param.defaultValue;
    if (def != null) {
        if (min != null && def < min) {
            errors.push({parameterName: name, message: "Default value is below the minimum."});
        }
        if (max != null && def > max) {
            errors.push({parameterName: name, message: "Default value is above the maximum."});
        }
    }
}

function validateEnumeration(
    param: { type: "Enumeration"; options: { title: string; value: string }[]; defaultValue?: string | null },
    name: string,
    errors: CreatorValidationError[],
): void {
    if (param.options.length === 0) {
        errors.push({parameterName: name, message: "Enumeration must have at least one option."});
        return;
    }
    const seenValues = new Set<string>();
    for (const opt of param.options) {
        if (seenValues.has(opt.value)) {
            errors.push({parameterName: name, message: `Duplicate enumeration value: "${opt.value}".`});
        }
        seenValues.add(opt.value);
    }
    const def = param.defaultValue;
    if (def != null && def !== "" && !seenValues.has(def)) {
        errors.push({
            parameterName: name,
            message: "Default value is not present in the option list.",
        });
    }
}

// Check the invocation for Jinja variable references that do not match any parameter name.
function validateInvocationReferences(
    application: A2Yaml,
    validNames: Set<string>,
    errors: CreatorValidationError[],
): void {
    const invocation = application.invocation ?? "";
    // Extract all bare variable names from {{ var }} tags. We do not parse dynamic expressions.
    const refPattern = /\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}/g;
    let match: RegExpExecArray | null;
    const unresolved = new Set<string>();
    while ((match = refPattern.exec(invocation)) !== null) {
        const ref = match[1];
        if (!validNames.has(ref)) {
            unresolved.add(ref);
        }
    }
    for (const ref of unresolved) {
        errors.push({
            parameterName: null,
            message: `Unresolved invocation reference: "{{ ${ref} }}".`,
        });
    }
}
