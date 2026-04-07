import * as React from "react";
import {Flex, Input} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {useCallback, useLayoutEffect} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {callAPI, noopCall} from "@/Authentication/DataHook";
import PrivateNetworkApi, {PrivateNetwork} from "@/UCloud/PrivateNetworkApi";
import {checkProviderMismatch} from "../Create";
import {PrivateNetworkBrowse} from "@/Applications/PrivateNetwork/PrivateNetworkBrowse";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";

interface PrivateNetworkProps extends WidgetProps {
    parameter: ApplicationParameterNS.PrivateNetwork;
}

export const PrivateNetworkParameter: React.FunctionComponent<PrivateNetworkProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const doOpen = useCallback(() => {
        dialogStore.addDialog(<PrivateNetworkBrowse
            opts={{
                additionalFilters: filters,
                isModal: true,
                selection: {
                    text: "Use",
                    onClick: (network) => {
                        onUse(network);
                        dialogStore.success();
                    },
                    show(res) {
                        const errorMessage = checkProviderMismatch(res, "Private networks");
                        if (errorMessage) return errorMessage;
                        return true;
                    },
                }
            }}
        />, noopCall, true, largeModalStyle);
    }, []);


    const onUse = useCallback((network: PrivateNetwork) => {
        PrivateNetworkSetter(props.parameter, {type: "private_network", id: network.id});
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
                const id = value.value;
                const network = await callAPI<PrivateNetwork>(PrivateNetworkApi.retrieve({id}));
                const visual = visualInput();
                if (visual) {
                    visual.value = network.specification.name || network.specification.subdomain || network.id;
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
        const f: Record<string, string> = {};
        if (props.provider) f["filterProvider"] = props.provider;
        return f;
    }, [props.provider]);

    return (<Flex>
        <Input
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No private network selected"}
            cursor="pointer"
            error={error}
            onClick={doOpen}
        />
        <input type="hidden" id={widgetId(props.parameter)} />
    </Flex>);
}

export const PrivateNetworkValidator: WidgetValidator = (param) => {
    if (param.type === "private_network") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "private_network", id: elem.value}};
    }

    return {valid: true};
};

export const PrivateNetworkSetter: WidgetSetter = (param, value) => {
    if (param.type !== "private_network") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.PrivateNetwork).id;
    selector.dispatchEvent(new Event("change"));
};
