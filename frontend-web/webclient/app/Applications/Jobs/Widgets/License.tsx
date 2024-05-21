import * as React from "react";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {compute} from "@/UCloud";
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {useCallback, useState} from "react";
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {License} from "@/UCloud/LicenseApi";
import {checkProviderMismatch} from "../Create";
import {Input} from "@/ui-components";
import {LicenseBrowse} from "@/Applications/LicenseBrowse";
import {CardClass} from "@/ui-components/Card";
import { ApplicationParameterNS } from "@/Applications/AppStoreApi";

interface LicenseProps extends WidgetProps {
    parameter: ApplicationParameterNS.LicenseServer;
}

export const LicenseParameter: React.FunctionComponent<LicenseProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const [open, setOpen] = useState(false);
    const doOpen = useCallback(() => {
        setOpen(true);
    }, [setOpen]);
    const doClose = useCallback(() => {
        setOpen(false)
    }, [setOpen]);

    const onUse = useCallback((license: License) => {
        LicenseSetter(props.parameter, {type: "license_server", id: license.id});
        WidgetSetProvider(props.parameter, license.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
        setOpen(false);
    }, [props.parameter, setOpen, props.errors]);

    const filters = React.useMemo(() => ({filterState: "READY"}), []);

    return <Flex>
        <ReactModal
            isOpen={open}
            style={largeModalStyle}
            onRequestClose={doClose}
            ariaHideApp={false}
            className={CardClass}
            shouldCloseOnEsc
        >
            <LicenseBrowse
                opts={{
                    isModal: true,
                    additionalFilters: filters,
                    selection: {
                        text: "Use",
                        onClick: onUse,
                        show(res) {
                            const errorMessage = checkProviderMismatch(res, "Licenses");
                            if (errorMessage) return errorMessage;
                            return true;
                        },
                    }
                }}
            />
        </ReactModal>
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
