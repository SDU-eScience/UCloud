import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Select} from "@/ui-components";
import {compute} from "@/UCloud";
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";

interface EnumProps extends WidgetProps {
    parameter: ApplicationParameterNS.Enumeration;
}

export const EnumParameter: React.FunctionComponent<EnumProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const defaultValue: string | undefined = props.parameter.defaultValue != undefined ?
        (props.parameter.defaultValue as unknown as AppParameterValueNS.Text).value : undefined;

    return <Flex>
        <Select defaultValue={defaultValue} id={widgetId(props.parameter)} error={error}>
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
