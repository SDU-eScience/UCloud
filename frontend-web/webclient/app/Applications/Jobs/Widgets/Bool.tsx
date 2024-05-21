import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetValidationAnswer} from "./index";
import {Select} from "@/ui-components";
import {compute} from "@/UCloud";
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {ApplicationParameter, ApplicationParameterNS} from "@/Applications/AppStoreApi";

interface BoolProps extends WidgetProps {
    parameter: ApplicationParameterNS.Bool;
}

export const BoolParameter: React.FunctionComponent<BoolProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const defaultValue: boolean | undefined = props.parameter.defaultValue != undefined ?
        (props.parameter.defaultValue as unknown as AppParameterValueNS.Bool).value : undefined;
    return <Flex>
        <Select defaultValue={defaultValue?.toString()} id={widgetId(props.parameter)} error={error}>
            <option value={""} />
            <option value="true">{props.parameter.trueValue}</option>
            <option value="false">{props.parameter.falseValue}</option>
        </Select>
    </Flex>;
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
