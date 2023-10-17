import * as React from "react";
import {default as ReactModal} from "react-modal";
import {Flex, Input} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {default as NetworkIPApi} from "@/UCloud/NetworkIPApi";
import * as UCloud from "@/UCloud";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {callAPI} from "@/Authentication/DataHook";
import {NetworkIP} from "@/UCloud/NetworkIPApi";
import {checkProviderMismatch} from "../Create";
import {NetworkIPBrowse} from "@/Applications/NetworkIP/NetworkIPBrowse";

interface NetworkIPProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.NetworkIP;
}

export const NetworkIPParameter: React.FunctionComponent<NetworkIPProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const [open, setOpen] = useState(false);
    const doOpen = useCallback(() => {
        setOpen(true);
    }, [setOpen]);
    const doClose = useCallback(() => {
        setOpen(false)
    }, [setOpen]);

    const onUse = useCallback((network: NetworkIP) => {
        NetworkIPSetter(props.parameter, {type: "network", id: network.id});
        WidgetSetProvider(props.parameter, network.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
        setOpen(false);
    }, [props.parameter, setOpen, props.errors]);

    const valueInput = () => document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    const visualInput = () => document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null;

    useLayoutEffect(() => {
        const listener = async () => {
            const value = valueInput();
            if (value) {
                const networkId = value!.value;
                const network = await callAPI<NetworkIP>(NetworkIPApi.retrieve({id: networkId}));
                const visual = visualInput();
                if (visual) {
                    visual.value = network.status.ipAddress ?? "No address";
                }
            }
        };

        const value = valueInput();
        value!.addEventListener("change", listener);
        return () => {
            value!.removeEventListener("change", listener);
        }
    }, []);

    const filters = React.useMemo(() => {
        const f = {
            filterState: "READY"
        };
        if (props.provider) f["filterProvider"] = props.provider
        return f;
    }, [props.provider]);

    return (<Flex>
        <Input
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No public IP selected"}
            cursor="pointer"
            error={error}
            onClick={doOpen}
        />
        <input type="hidden" id={widgetId(props.parameter)} />
        <ReactModal
            isOpen={open}
            ariaHideApp={false}
            style={largeModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={doClose}
        >
            <NetworkIPBrowse
                opts={{
                    additionalFilters: filters,
                    embedded: true,
                    isModal: true,
                    selection: {
                        onSelect: onUse,
                        onSelectRestriction(res) {
                            const errorMessage = checkProviderMismatch(res, "Public IPs");
                            if (errorMessage) return errorMessage;
                            return res.status.boundTo.length === 0;
                        },
                    }
                }}

            />
        </ReactModal>
    </Flex>);
}

export const NetworkIPValidator: WidgetValidator = (param) => {
    if (param.type === "network_ip") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "network", id: elem.value}};
    }

    return {valid: true};
};

export const NetworkIPSetter: WidgetSetter = (param, value) => {
    if (param.type !== "network_ip") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.Network).id;
    selector.dispatchEvent(new Event("change"));
};
