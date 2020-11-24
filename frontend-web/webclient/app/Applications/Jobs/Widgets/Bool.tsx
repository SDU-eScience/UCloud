import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Checkbox, Select} from "ui-components";
import {compute} from "UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "ui-components/Flex";

interface BoolProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Bool;
}

export const BoolParameter: React.FunctionComponent<BoolProps> = props => {
    return <Flex>
        <Select id={widgetId(props.parameter)}>
            <option value="true">{props.parameter.trueValue}</option>
            <option value="false">{props.parameter.falseValue}</option>
        </Select>
    </Flex>;
};

export const BoolValidator: WidgetValidator = (param) => {
    if (param.type === "boolean") {
        // TODO Check data
        const elem = findElement(param);
        if (elem.value === "false") {
            return {valid: true, value: {type: "boolean", value: false}};
        } else {
            return {valid: false, message: "NYI"};
        }
    }

    return {valid: true};
};

export const BoolSetter: WidgetSetter = (param, value) => {
    if (param.type !== "boolean") return;
    if (value.type !== "boolean") return;

    const selector = findElement(param);
    selector.value = value.value ? "true" : "false";
};

function findElement(param: ApplicationParameterNS.Bool): HTMLSelectElement {
    return document.getElementById(widgetId(param)) as HTMLSelectElement;
}

