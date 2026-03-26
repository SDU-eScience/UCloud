import * as React from "react";
import {Flex, Input} from "@/ui-components";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {noopCall, useCloudCommand} from "@/Authentication/DataHook";
import PublicLinkApi, {PublicLink} from "@/UCloud/PublicLinkApi";
import {checkProviderMismatch} from "../Create";
import {PublicLinkBrowse} from "@/Applications/PublicLinks/PublicLinkBrowse";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {sendFailureNotification} from "@/Notifications";

interface IngressProps extends WidgetProps {
    parameter: ApplicationParameterNS.Ingress;
}

export const IngressParameter: React.FunctionComponent<IngressProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const portRequired = props.application.invocation.applicationType !== "WEB";

    const doOpen = useCallback(() => {
        dialogStore.addDialog(<PublicLinkBrowse
            opts={{
                selection: {
                    text: "Use",
                    onClick: (network) => {
                        onUse(network);
                        dialogStore.success();
                    },
                    show(res) {
                        const errorMessage = checkProviderMismatch(res, "Public links");
                        if (errorMessage) return errorMessage;
                        return res.status.boundTo.length === 0;
                    }
                },
                isModal: true,
                additionalFilters: filters
            }}
        />, noopCall, true, largeModalStyle);
    }, []);

    const onUse = useCallback((network: PublicLink) => {
        IngressSetter(props.parameter, {type: "ingress", id: network.id});
        WidgetSetProvider(props.parameter, network.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
    }, [props.parameter, props.errors]);

    const valueInput = () => {
        return document.getElementById(widgetId(props.parameter)) as HTMLInputElement | null;
    }
    const visualInput = () => {
        return document.getElementById(widgetId(props.parameter) + "visual") as HTMLInputElement | null
    };

    const [, invokeCommand] = useCloudCommand();

    useLayoutEffect(() => {
        const listener = async () => {
            const value = valueInput();
            if (value) {
                const id = value!.value;
                const visual = visualInput();
                if (visual) {
                    try {
                        const ingress = await invokeCommand<PublicLink>(PublicLinkApi.retrieve({id: id}), {defaultErrorHandler: false});
                        visual.value = ingress?.specification.domain ?? "Address not found";
                    } catch (e) {
                        sendFailureNotification("Failed to import custom links.");
                        visual.value = "Address not found";
                    }
                }
            }
        };

        const value = valueInput();
        if (!value) return;
        value.addEventListener("change", listener);
        return () => {
            value.removeEventListener("change", listener);
        }
    }, []);

    const filters = React.useMemo(() => {
        const filters = {
            filterState: "READY",
        };
        if (props.provider) filters["filterProvider"] = props.provider;
        return filters;
    }, []);

    return (<Flex gap="8px">
        <Input
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No public link selected"}
            cursor="pointer"
            error={error}
            onClick={doOpen}
        />
        {props.bindLinkToPort ? (
            <Input
                id={widgetId(props.parameter) + "-port"}
                type="number"
                min={1}
                max={65535}
                style={{width: "140px"}}
                placeholder={portRequired ? "Port (required)" : "Port"}
                required={portRequired}
                data-required={portRequired ? "true" : "false"}
            />
        ) : null}
        <input type="hidden" id={widgetId(props.parameter)} />
    </Flex>);
}

export const IngressValidator: WidgetValidator = (param) => {
    if (param.type === "ingress") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};

        const result: AppParameterValueNS.Ingress = {type: "ingress", id: elem.value};
        const portElem = document.getElementById(widgetId(param) + "-port") as HTMLInputElement | null;
        const portRequired = portElem?.getAttribute("data-required") === "true";

        if (portElem && portElem.value === "" && portRequired) {
            return {valid: false, message: "Port is required"};
        }

        if (portElem && portElem.value !== "") {
            const port = parseInt(portElem.value, 10);
            if (isNaN(port) || port < 1 || port > 65535) {
                return {valid: false, message: "Port must be a number between 1 and 65535"};
            }
            result.port = port;
        }

        return {valid: true, value: result};
    }

    return {valid: true};
};

export const IngressSetter: WidgetSetter = (param, value) => {
    if (param.type !== "ingress") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    const ingress = value as AppParameterValueNS.Ingress;
    selector.value = ingress.id;

    const port = document.getElementById(widgetId(param) + "-port") as HTMLInputElement | null;
    if (port) {
        port.value = ingress.port?.toString() ?? "";
    }

    selector.dispatchEvent(new Event("change"));
};
