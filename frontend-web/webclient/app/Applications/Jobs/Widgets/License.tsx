import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {Select} from "ui-components";
import {compute} from "UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "ui-components/Flex";
import {useCloudAPI} from "Authentication/DataHook";
import LicenseServerId = compute.license.LicenseServerId;

interface LicenseProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.LicenseServer;
}

export const LicenseParameter: React.FunctionComponent<LicenseProps> = props => {
    const [licenses] = useCloudAPI<LicenseServerId[]>(UCloud.compute.license.findByTag({
        tags: props.parameter.tagged,
    }), []);

    const error = props.errors[props.parameter.name] != null;
    return <Flex>
        <Select id={widgetId(props.parameter)} error={error}>
            <option value="none" disabled>
                No license server selected
            </option>
            {licenses.data.map(server => (
                <option key={server.id} value={server.id}>
                    {server.name}
                </option>
            ))}
        </Select>
    </Flex>;
};

export const LicenseValidator: WidgetValidator = (param) => {
    if (param.type === "license_server") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "license_server", id: elem.value, address: "", port: 0}};
    }

    return {valid: true};
};

export const LicenseSetter: WidgetSetter = (param, value) => {
    if (param.type !== "license_server") return;
    if (value.type !== "license_server") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = value.id;
};

function findElement(param: ApplicationParameterNS.LicenseServer): HTMLSelectElement | null {
    return document.getElementById(widgetId(param)) as HTMLSelectElement | null;
}
