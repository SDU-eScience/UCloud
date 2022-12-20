import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {TextArea, Input} from "@/ui-components";
import {compute} from "@/UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import styled from "styled-components";

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

    const error = props.errors[props.parameter.name] != null;
    return <Input
        id={widgetId(props.parameter)}
        placeholder={placeholder}
        error={error}
    />;
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
