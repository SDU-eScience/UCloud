import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {compute} from "UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {PointerInput} from "Applications/Jobs/Widgets/Peer";
import * as Licenses from "Applications/Licenses";
import {useCallback, useState} from "react";
import ReactModal from "react-modal";
import License = compute.License;
import {Box} from "ui-components";
import {largeModalStyle} from "Utilities/ModalUtilities";

interface LicenseProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.LicenseServer;
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
        setOpen(false);
    }, [props.parameter, setOpen]);

    return <Flex>
        <ReactModal
            isOpen={open}
            style={largeModalStyle}
            onRequestClose={doClose}
            ariaHideApp={false}
            shouldCloseOnEsc
        >
            <Licenses.Browse
                tagged={props.parameter.tagged}
                // TODO Provider
                standalone={false}
                onUse={onUse}
            />
        </ReactModal>
        <PointerInput
            id={widgetId(props.parameter)}
            error={error}
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

function findElement(param: ApplicationParameterNS.LicenseServer): HTMLSelectElement | null {
    return document.getElementById(widgetId(param)) as HTMLSelectElement | null;
}
