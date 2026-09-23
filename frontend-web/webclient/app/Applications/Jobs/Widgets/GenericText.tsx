import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {TextArea, Input} from "@/ui-components";
import { ApplicationParameterNS } from "@/Applications/AppStoreApi";

type GenericTextType =
    ApplicationParameterNS.Text |
    ApplicationParameterNS.TextArea |
    ApplicationParameterNS.Integer |
    ApplicationParameterNS.FloatingPoint;

interface GenericTextProps extends WidgetProps {
    parameter: GenericTextType;
}

function readGenericDefaultValue(defaultValue: unknown): string | number | undefined {
    if (typeof defaultValue === "string" || typeof defaultValue === "number") {
        return defaultValue;
    }

    if (defaultValue != null && typeof defaultValue === "object" && "value" in defaultValue) {
        const wrappedValue = (defaultValue as {value?: unknown}).value;
        if (typeof wrappedValue === "string" || typeof wrappedValue === "number") {
            return wrappedValue;
        }
    }

    return undefined;
}

export const GenericTextParameter: React.FunctionComponent<GenericTextProps> = props => {
    let placeholder = "Text";
    if (props.parameter.type === "integer") {
        placeholder = "Integer (example: 42)"
    } else if (props.parameter.type === "floating_point") {
        placeholder = "Number (example 12.34)"
    }

    const configuredDefault = readGenericDefaultValue(props.parameter.defaultValue);
    const defaultValue = props.parameter.optional ? undefined : configuredDefault;
    if (props.parameter.optional && configuredDefault !== undefined) placeholder = configuredDefault.toString();

    const error = props.errors[props.parameter.name] != null;

    let elem = <Input
        id={widgetId(props.parameter)}
        defaultValue={defaultValue}
        placeholder={placeholder}
        error={error}
    />;

    if (props.parameter.type === "integer") {
        elem = <Input
            id={widgetId(props.parameter)}
            type="number"
            defaultValue={defaultValue}
            min={props.parameter.min}
            max={props.parameter.max}
            step={props.parameter.step ?? 1}
            placeholder={placeholder}
            error={error}
        />
    } else if (props.parameter.type === "floating_point") {
        elem = <Input
            id={widgetId(props.parameter)}
            type="number"
            defaultValue={defaultValue}
            min={props.parameter.min}
            max={props.parameter.max}
            step={props.parameter.step ?? "any"}
            placeholder={placeholder}
            error={error}
        />
    }


    return elem;
};

export const GenericTextAreaAppParameter: React.FunctionComponent<GenericTextProps> = props => {
    let placeholder = "File content";
    const configuredDefault = readGenericDefaultValue(props.parameter.defaultValue);
    if (props.parameter.optional && configuredDefault !== undefined) placeholder = configuredDefault.toString();
    const error = props.errors[props.parameter.name] != null;
    return <TextArea
        id={widgetId(props.parameter)}
        defaultValue={props.parameter.optional ? undefined : configuredDefault}
        placeholder={placeholder}
        resize="vertical"
        width="100%"
        height="300px"
        error={error}
    />;
};

export const GenericTextValidator: WidgetValidator = (param) => {
    const elem = findElement(param);
    if (elem === null) return {valid: true};

    if (param.type === "text") {
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "text", value: elem.value}};
    } else if (param.type === "textarea") {
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "text", value: elem.value}};
    } else if (param.type === "integer") {
        if (elem.value === "") return {valid: true};
        if (/^[+-]?\d+$/.test(elem.value)) {
            return {valid: true, value: {type: "integer", value: parseInt(elem.value, 10)}};
        } else {
            return {valid: false, message: "Invalid integer supplied. Example: 42"};
        }
    } else if (param.type === "floating_point") {
        if (elem.value === "") return {valid: true};
        if (/^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$/.test(elem.value)) {
            return {valid: true, value: {type: "floating_point", value: parseFloat(elem.value)}};
        } else {
            return {valid: false, message: "Invalid number supplied. Example: 12.34"};
        }
    }

    return {valid: true};
};

export const GenericTextSetter: WidgetSetter = (param, value) => {
    if (param.type !== "text" && param.type !== "textarea" && param.type != "integer" && param.type != "floating_point") return;

    const selector = findElement(param as GenericTextType);
    if (selector == null) throw "Missing element for " + param.name;
    selector.value = value["value"];
};
