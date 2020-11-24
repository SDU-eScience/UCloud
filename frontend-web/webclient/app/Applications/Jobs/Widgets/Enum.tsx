import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Checkbox, Select} from "ui-components";
import {compute} from "UCloud";
import ApplicationParameter = compute.ApplicationParameter;
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "ui-components/Flex";

interface EnumProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Enumeration;
}

export const EnumParameter: React.FunctionComponent<EnumProps> = props => {
    return <Flex>
        <Select id={widgetId(props.parameter)}>
            {props.parameter.options.map(it => (
                <option key={it.value} value={it.value}>{it.name}</option>
            ))}
        </Select>
    </Flex>;
};

export const EnumValidator: WidgetValidator = (param) => {
    if (param.type === "enumeration") {
        return {valid: false, message: "NYI"};
    }

    return {valid: true};
};

export const EnumSetter: WidgetSetter = (param, value) => {
    if (param.type !== "enumeration") return;
    if (value.type !== "text") return;

    const selector = findElement(param);
    selector.value = value.value ? "true" : "false";
};

function findElement(param: ApplicationParameterNS.Enumeration): HTMLSelectElement {
    return document.getElementById(widgetId(param)) as HTMLSelectElement;
}
