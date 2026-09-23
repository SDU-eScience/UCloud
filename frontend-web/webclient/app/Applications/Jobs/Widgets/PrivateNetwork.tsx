import * as React from "react";
import {Flex, Input, Select} from "@/ui-components";
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
import {retrieveNetworkReservations} from "@/Applications/PrivateNetwork/Reservations";

interface PrivateNetworkProps extends WidgetProps {
    parameter: ApplicationParameterNS.PrivateNetwork;
}

export const PrivateNetworkParameter: React.FunctionComponent<PrivateNetworkProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const [addresses, setAddresses] = React.useState<string[]>([]);
    const [selectedIp, setSelectedIp] = React.useState("");
    const [networkSelected, setNetworkSelected] = React.useState(false);
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
        setSelectedIp("");
        setAddresses([]);
        PrivateNetworkSetter(props.parameter, {type: "private_network", id: network.id});
        WidgetSetProvider(props.parameter, network.specification.product.provider);
        props.onValueChange?.();
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
    }, [props.parameter, props.errors, props.onValueChange]);

    const valueInput = () => document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    const visualInput = () => document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null;

    useLayoutEffect(() => {
        const listener = async () => {
            const value = valueInput();
            if (value) {
                const id = value.value;
                setNetworkSelected(id !== "");
                if (!id) {
                    setAddresses([]);
                    setSelectedIp("");
                    return;
                }
                const network = await callAPI<PrivateNetwork>(PrivateNetworkApi.retrieve({id}));
                if (value.value !== id) return;
                setSelectedIp((document.getElementById(widgetId(props.parameter) + "ip") as HTMLInputElement)?.value ?? "");
                const visual = visualInput();
                if (visual) {
                    visual.value = network.specification.name || network.specification.subdomain || network.id;
                }
                retrieveNetworkReservations(id).then(reservations => {
                    if (value.value !== id) return;
                    setAddresses(reservations.flatMap(it =>
                        it.status.ipAddress && it.permissions.myself.some(permission => permission === "EDIT" || permission === "ADMIN") ?
                            [it.status.ipAddress] : []));
                }).catch(() => {
                    if (value.value === id) setAddresses([]);
                });
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

    return (<Flex flexDirection="column" gap="8px" width="100%">
        <Input
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No private network selected"}
            cursor="pointer"
            error={error}
            onClick={doOpen}
            readOnly
            data-field-activator
        />
        <input type="hidden" id={widgetId(props.parameter)} />
        {!networkSelected ? null : <div>
            <Select id={widgetId(props.parameter) + "ip-select"} aria-label="Private network IP address"
                value={selectedIp} onChange={e => {
                setSelectedIp(e.target.value);
                const input = document.getElementById(widgetId(props.parameter) + "ip") as HTMLInputElement;
                input.value = e.target.value;
                props.onValueChange?.();
            }}>
                <option value="">Automatic IP</option>
                {selectedIp && !addresses.includes(selectedIp) ? <option value={selectedIp}>{selectedIp}</option> : null}
                {addresses.map(ip => <option key={ip} value={ip}>{ip}</option>)}
            </Select>
        </div>}
        <input type="hidden" id={widgetId(props.parameter) + "ip"} />
    </Flex>);
}

export const PrivateNetworkValidator: WidgetValidator = (param) => {
    if (param.type === "private_network") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};
        const ip = (document.getElementById(widgetId(param) + "ip") as HTMLInputElement | null)?.value;
        return {valid: true, value: {type: "private_network", id: elem.value, ...(ip ? {ips: [ip]} : {})}};
    }

    return {valid: true};
};

export const PrivateNetworkSetter: WidgetSetter = (param, value) => {
    if (param.type !== "private_network") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.PrivateNetwork).id;
    const ipInput = document.getElementById(widgetId(param) + "ip") as HTMLInputElement | null;
    if (ipInput) ipInput.value = (value as AppParameterValueNS.PrivateNetwork).ips?.[0] ?? "";
    selector.dispatchEvent(new Event("change"));
};
