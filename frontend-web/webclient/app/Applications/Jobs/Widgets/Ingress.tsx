import * as React from "react";
import {Flex, Input} from "@/ui-components";
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {findElement, widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import AppParameterValueNS = compute.AppParameterValueNS;
import {useCloudCommand} from "@/Authentication/DataHook";
import PublicLinkApi, {PublicLink} from "@/UCloud/PublicLinkApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {checkProviderMismatch} from "../Create";
import {PublicLinkBrowse} from "@/Applications/PublicLinks/PublicLinkBrowse";
import {CardClass} from "@/ui-components/Card";
import { ApplicationParameterNS } from "@/Applications/AppStoreApi";

interface IngressProps extends WidgetProps {
    parameter: ApplicationParameterNS.Ingress;
}

export const IngressParameter: React.FunctionComponent<IngressProps> = props => {
    const error = props.errors[props.parameter.name] != null;
    const [open, setOpen] = useState(false);
    const doOpen = useCallback(() => {
        setOpen(true);
    }, [setOpen]);
    const doClose = useCallback(() => {
        setOpen(false)
    }, [setOpen]);

    const onUse = useCallback((network: PublicLink) => {
        IngressSetter(props.parameter, {type: "ingress", id: network.id});
        WidgetSetProvider(props.parameter, network.specification.product.provider);
        if (props.errors[props.parameter.name]) {
            delete props.errors[props.parameter.name];
            props.setErrors({...props.errors});
        }
        setOpen(false);
    }, [props.parameter, setOpen, props.errors]);

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
                        snackbarStore.addFailure("Failed to import custom links.", false);
                        visual.value = "Address not found";
                    }
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
        const filters = {
            filterState: "READY",
        };
        if (props.provider) filters["filterProvider"] = props.provider;
        return filters;
    }, []);

    return (<Flex>
        <Input
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No public link selected"}
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
            className={CardClass}
        >
            <PublicLinkBrowse
                opts={{
                    selection: {
                        text: "Use",
                        onClick: onUse,
                        show(res) {
                            const errorMessage = checkProviderMismatch(res, "Public links");
                            if (errorMessage) return errorMessage;
                            return res.status.boundTo.length === 0;
                        }
                    },
                    isModal: true,
                    additionalFilters: filters
                }}
            />
        </ReactModal>
    </Flex>);
}

export const IngressValidator: WidgetValidator = (param) => {
    if (param.type === "ingress") {
        const elem = findElement(param);
        if (elem === null) return {valid: true};
        if (elem.value === "") return {valid: true};
        return {valid: true, value: {type: "ingress", id: elem.value}};
    }

    return {valid: true};
};

export const IngressSetter: WidgetSetter = (param, value) => {
    if (param.type !== "ingress") return;

    const selector = findElement(param);
    if (selector === null) throw "Missing element for: " + param.name;
    selector.value = (value as AppParameterValueNS.Ingress).id;
    selector.dispatchEvent(new Event("change"));
};
