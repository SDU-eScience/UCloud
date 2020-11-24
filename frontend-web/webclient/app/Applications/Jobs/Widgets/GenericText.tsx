import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Input} from "ui-components";
import {compute} from "UCloud";

type GenericTextType =
    UCloud.compute.ApplicationParameterNS.Text |
    UCloud.compute.ApplicationParameterNS.Integer |
    UCloud.compute.ApplicationParameterNS.FloatingPoint;

interface GenericTextProps extends WidgetProps {
    parameter: GenericTextType;
}

export const GenericTextParameter: React.FunctionComponent<GenericTextProps> = props => {
    return <Input id={widgetId(props.parameter)} />;
};

export const GenericTextValidator: WidgetValidator = (param) => {
    if (param.type === "text") {
        return {valid: false, message: "NYI"};
    } else if (param.type === "integer") {
        return {valid: false, message: "NYI"};
    } else if (param.type === "floating_point") {
        return {valid: false, message: "NYI"};
    }

    return {valid: true};
};

export const GenericTextSetter: WidgetSetter = (param, value) => {
    if (param.type !== "text" && value.type !== "text") return;
    if (param.type !== "integer" && value.type !== "integer") return;
    if (param.type !== "floating_point" && value.type !== "floating_point") return;

    const selector = findElement(param as GenericTextType);
    selector.value = value["value"];
};

function findElement(param: GenericTextType): HTMLInputElement {
    return document.getElementById(widgetId(param)) as HTMLInputElement;
}
