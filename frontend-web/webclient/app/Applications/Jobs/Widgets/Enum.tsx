import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Select} from "@/ui-components";
import {compute} from "@/UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;

interface EnumProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Enumeration;
}

export const EnumParameter: React.FunctionComponent<EnumProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    return <Flex>
        <Select id={widgetId(props.parameter)} error={error}>
            <option value={""} />
            {props.parameter.options.map(it => (
                <option key={it.value} value={it.value}>{it.name}</option>
            ))}
        </Select>
    </Flex>;
};

export const EnumValidator: WidgetValidator = (param) => {
    if (param.type === "enumeration") {
        const elem = findElement(param);
        if (elem === null) {
            return {valid: true};
        } else {
            if (elem.value === "") return {valid: true};

            for (const opt of param.options) {
                if (opt.value === elem.value) {
                    return {valid: true, value: {type: "text", value: elem.value}};
                }
            }
            return {valid: false, message: `Invalid value: ${elem.value}`};
        }
    }

    return {valid: true};
};

export const EnumSetter: WidgetSetter = (param, value) => {
    if (param.type !== "enumeration") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.Text).value;
};
