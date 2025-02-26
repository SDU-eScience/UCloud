import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {compute} from "@/UCloud";
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {useCallback} from "react";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {License} from "@/UCloud/LicenseApi";
import {checkProviderMismatch} from "../Create";
import {Input} from "@/ui-components";
import {LicenseBrowse} from "@/Applications/LicenseBrowse";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {noopCall} from "@/Authentication/DataHook";

interface LicenseProps extends WidgetProps {
    parameter: ApplicationParameterNS.LicenseServer;
}

export const LicenseParameter: React.FunctionComponent<LicenseProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const doOpen = useCallback(() => {
        dialogStore.addDialog(<LicenseBrowse
            opts={{
                isModal: true,
                additionalFilters: filters,
                selection: {
                    text: "Use",
                    onClick: (license) => {
                        onUse(license);
                        dialogStore.success();
                    },
                    show(res) {
                        const errorMessage = checkProviderMismatch(res, "Licenses");
                        if (errorMessage) return errorMessage;
                        return true;
                    },
                }
            }}
        />, noopCall, true, largeModalStyle);
    }, []);

    const onUse = useCallback((license: License) => {
        LicenseSetter(props.parameter, {type: "license_server", id: license.id});
        WidgetSetProvider(props.parameter, license.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
    }, [props.parameter, props.errors]);

    const filters = React.useMemo(() => ({filterState: "READY"}), []);

    return <Flex>
        <Input
            id={widgetId(props.parameter)}
            error={error}
            placeholder="Select license server..."
            cursor="pointer"
            onClick={doOpen}
        />
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

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.License).id;
};
