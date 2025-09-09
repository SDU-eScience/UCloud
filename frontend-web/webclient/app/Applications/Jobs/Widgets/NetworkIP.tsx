import * as React from "react";
import {Flex, Input} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {default as NetworkIPApi} from "@/UCloud/NetworkIPApi";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {useCallback, useLayoutEffect} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {callAPI, noopCall} from "@/Authentication/DataHook";
import {NetworkIP} from "@/UCloud/NetworkIPApi";
import {checkProviderMismatch, getProviderField} from "../Create";
import {NetworkIPBrowse} from "@/Applications/NetworkIP/NetworkIPBrowse";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";

interface NetworkIPProps extends WidgetProps {
    parameter: ApplicationParameterNS.NetworkIP;
}

export const NetworkIPParameter: React.FunctionComponent<NetworkIPProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const doOpen = useCallback(() => {
        dialogStore.addDialog(<NetworkIPBrowse
            opts={{
                additionalFilters: filters,
                isModal: true,
                selection: {
                    text: "Use",
                    onClick: (ip) => {
                        onUse(ip);
                        dialogStore.success();
                    },
                    show(res) {
                        return res.status.boundTo.length === 0;
                    },
                    provider: getProviderField() ?? null,
                },
            }}
        />, noopCall, true, largeModalStyle);
    }, []);


    const onUse = useCallback((network: NetworkIP) => {
        NetworkIPSetter(props.parameter, {type: "network", id: network.id});
        WidgetSetProvider(props.parameter, network.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
    }, [props.parameter, props.errors]);

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
