import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {TextArea, Input} from "@/ui-components";
import {compute} from "@/UCloud";
import styled from "styled-components";
import AppParameterValueNS = compute.AppParameterValueNS;

type GenericTextType =
    UCloud.compute.ApplicationParameterNS.Text |
    UCloud.compute.ApplicationParameterNS.TextArea |
    UCloud.compute.ApplicationParameterNS.Integer |
    UCloud.compute.ApplicationParameterNS.FloatingPoint;

interface GenericTextProps extends WidgetProps {
    parameter: GenericTextType;
}

export const GenericTextParameter: React.FunctionComponent<GenericTextProps> = props => {
    let placeholder = "Text";
    if (props.parameter.type === "integer") {
        placeholder = "Integer (example: 42)"
    } else if (props.parameter.type === "floating_point") {
        placeholder = "Number (example 12.34)"
    }

    // NOTE(Brian): This is a bit hacky. The defaultValue is not actually sent as a parameter on submit.
    // If changed, the correct value should be sent as a parameter, and otherwise the backend will
    // handle the defaultValue.
    let defaultValue: AppParameterValueNS.FloatingPoint | AppParameterValueNS.Text | AppParameterValueNS.Integer | undefined = undefined;
    
    if (props.parameter.defaultValue != undefined) {
        if (props.parameter.type === "integer") {
            defaultValue = props.parameter.defaultValue as unknown as AppParameterValueNS.Integer;
        } else if (props.parameter.type === "floating_point") {
            defaultValue = props.parameter.defaultValue as unknown as AppParameterValueNS.FloatingPoint;
        } else {
            defaultValue = props.parameter.defaultValue as unknown as AppParameterValueNS.Text;
        }
    }

    const error = props.errors[props.parameter.name] != null;

    let elem = <Input
        id={widgetId(props.parameter)}
        defaultValue={defaultValue?.value}
        placeholder={placeholder}
        error={error}
    />;

    // NOTE(Brian): The use of input fields of type "number" have previously been removed for
    // some reason. I have re-added them here, since we don't remember the exact reason for the removal.
    // If they misbehave we can simply remove the following section.
    if (props.parameter.type === "integer") {
        elem = <Input
            id={widgetId(props.parameter)}
            type={"number"}
            defaultValue={defaultValue?.value}
            min={props.parameter.min}
            max={props.parameter.max}
            step={props.parameter.step ?? 1}
            placeholder={placeholder}
            error={error}
        />
    } else if (props.parameter.type === "floating_point") {
        elem = <Input
            id={widgetId(props.parameter)}
            type={"number"}
            defaultValue={defaultValue?.value}
            min={props.parameter.min}
            max={props.parameter.max}
            step={props.parameter.step ?? "any"}
            placeholder={placeholder}
            error={error}
        />
    }


    return elem;
};

const TextAreaApp = styled(TextArea)`
    width: 100%;
    height: 300px;
    resize: vertical;
`;

export const GenericTextAreaAppParameter: React.FunctionComponent<GenericTextProps> = props => {
    let placeholder = "File content";
    const error = props.errors[props.parameter.name] != null;
    return <TextAreaApp
        id={widgetId(props.parameter)}
        placeholder={placeholder}
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
