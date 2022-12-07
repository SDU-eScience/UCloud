import * as React from "react";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "./index";
import {compute} from "@/UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "@/ui-components/Flex";
import AppParameterValueNS = compute.AppParameterValueNS;
import {PointerInput} from "@/Applications/Jobs/Widgets/Peer";
import {useCallback, useState} from "react";
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {LicenseBrowse} from "@/Applications/Licenses";
import {License} from "@/UCloud/LicenseApi";
import {BrowseType} from "@/Resource/BrowseType";
import {getProviderField} from "../Resources/Ingress";
import {getProviderTitle} from "@/Providers/ProviderTitle";

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
        WidgetSetProvider(props.parameter, license.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
        setOpen(false);
    }, [props.parameter, setOpen]);

    const filters = React.useMemo(() => ({filterState: "READY"}), []);

    return <Flex>
        <ReactModal
            isOpen={open}
            style={largeModalStyle}
            onRequestClose={doClose}
            ariaHideApp={false}
            shouldCloseOnEsc
        >
            <LicenseBrowse
                tagged={props.parameter.tagged}
                // TODO(Dan) Provider
                additionalFilters={filters}
                onSelectRestriction={res => {
                    const provider = getProviderField();
                    const ingressProvider = res.specification.product.provider;
                    if (provider && provider !== ingressProvider) {
                        return `License from ${getProviderTitle(ingressProvider)} cannot be used with machines from ${getProviderTitle(provider)}`;
                    }
                    return true;
                }}
                browseType={BrowseType.Embedded}
                onSelect={onUse}
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
