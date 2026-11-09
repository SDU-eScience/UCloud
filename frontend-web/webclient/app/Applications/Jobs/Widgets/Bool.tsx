import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetValidationAnswer} from "./index";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {ApplicationParameter, ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {EnumParameter} from "@/Applications/Jobs/Widgets/Enum";

interface BoolProps extends WidgetProps {
    parameter: ApplicationParameterNS.Bool;
}

const YesNoKeywords: Record<string, number> = {
    allow: 3,
    has: 3,
    can: 3,
    should: 3,
    required: 2,
    eligible: 2,
    include: 2,
    accept: 2,
    initialize: 3,
};

const OnOffKeywords: Record<string, number> = {
    enable: 4,
    active: 2,
    logging: 3,
    notifications: 2,
    sync: 3,
    cache: 3,
    debug: 3,
    tracking: 3,
    mode: 1,
};

function readBoolDefaultValue(defaultValue: unknown): boolean | undefined {
    if (typeof defaultValue === "boolean") {
        return defaultValue;
    }

    if (defaultValue != null && typeof defaultValue === "object" && "value" in defaultValue) {
        const wrappedValue = (defaultValue as {value?: unknown}).value;
        if (typeof wrappedValue === "boolean") {
            return wrappedValue;
        }
    }

    return undefined;
}

function booleanOptionLabels(parameter: ApplicationParameterNS.Bool): [string, string] {
    if (parameter.trueValue.trim().toLowerCase() !== "true" || parameter.falseValue.trim().toLowerCase() !== "false") {
        return [parameter.trueValue, parameter.falseValue];
    }

    const words = new Set(`${parameter.name} ${parameter.title} ${parameter.description}`
        .toLowerCase()
        .match(/[a-z0-9]+/g) ?? []);
    const score = (keywords: Record<string, number>) => Object.entries(keywords).reduce((result, [keyword, weight]) => {
        const matches = [keyword, `${keyword}s`, `${keyword}d`, `${keyword}ed`, `${keyword}ing`]
            .some(word => words.has(word));
        return result + (matches ? weight : 0);
    }, 0);
    const yesNoScore = score(YesNoKeywords);
    const onOffScore = score(OnOffKeywords);
    if (yesNoScore === 0 && onOffScore === 0) return ["True", "False"];
    return yesNoScore >= onOffScore ? ["Yes", "No"] : ["On", "Off"];
}

export const BoolParameter: React.FunctionComponent<BoolProps> = props => {
    const defaultValue = readBoolDefaultValue(props.parameter.defaultValue);
    const effectiveDefault = defaultValue ?? false;
    const [trueLabel, falseLabel] = booleanOptionLabels(props.parameter);
    const parameter: ApplicationParameterNS.Enumeration = {
        ...props.parameter,
        type: "enumeration",
        defaultValue: effectiveDefault.toString(),
        options: [
            {name: trueLabel, value: "true"},
            {name: falseLabel, value: "false"},
        ],
    };
    return <EnumParameter {...props} parameter={parameter} />;
};

export function BoolValidator(param: ApplicationParameter): WidgetValidationAnswer {
    if (param.type === "boolean") {
        const elem = findElement(param);
        if (elem === null || elem.value === "") {
            return {valid: true}; // Checked later if mandatory
        } else if (elem.value === "false") {
            return {valid: true, value: {type: "boolean", value: false}};
        } else if (elem.value === "true") {
            return {valid: true, value: {type: "boolean", value: true}};
        }
    }

    return {valid: true};
}

export function BoolSetter(param: ApplicationParameter, value: compute.AppParameterValue): void {
    if (param.type !== "boolean") return;

    const selector = findElement(param);
    if (!selector) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.Bool).value ? "true" : "false";
}
