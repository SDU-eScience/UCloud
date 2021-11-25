import * as React from "react";
import {default as ReactModal} from "react-modal";
import {Flex} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {NetworkIPBrowse} from "@/Applications/NetworkIP/Browse";
import {default as NetworkIPApi, NetworkIPFlags} from "@/UCloud/NetworkIPApi";
import * as UCloud from "@/UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {PointerInput} from "@/Applications/Jobs/Widgets/Peer";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import AppParameterValueNS = compute.AppParameterValueNS;
import {callAPI} from "@/Authentication/DataHook";
import {NetworkIP} from "@/UCloud/NetworkIPApi";
import {BrowseType} from "@/Resource/BrowseType";

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
        setOpen(false);
    }, [props.parameter, setOpen]);

    const valueInput = () => {
        return document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    }
    const visualInput = () => {
        return document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null
    };

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

    const filters: NetworkIPFlags = React.useMemo(() => ({
        filterState: "UNAVAILABLE"
    }), []);

    return (<Flex>
        <PointerInput
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No public IP selected"}
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
                provider={props.provider}
                additionalFilters={filters}
                onSelect={onUse}
                browseType={BrowseType.Embedded}
                onSelectRestriction={res => res.status.boundTo.length === 0}
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

function findElement(param: ApplicationParameterNS.NetworkIP): HTMLSelectElement | null {
    return document.getElementById(widgetId(param)) as HTMLSelectElement | null;
}
