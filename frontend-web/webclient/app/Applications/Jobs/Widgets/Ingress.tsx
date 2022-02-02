import * as React from "react";
import {Flex} from "@/ui-components";
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import * as UCloud from "@/UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {PointerInput} from "@/Applications/Jobs/Widgets/Peer";
import {useCallback, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import AppParameterValueNS = compute.AppParameterValueNS;
import {useCloudCommand} from "@/Authentication/DataHook";
import IngressBrowse from "@/Applications/Ingresses/Browse";
import IngressApi, {Ingress} from "@/UCloud/IngressApi";
import {BrowseType} from "@/Resource/BrowseType";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

interface IngressProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Ingress;
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

    const onUse = useCallback((network: Ingress) => {
        IngressSetter(props.parameter, {type: "ingress", id: network.id});
        setOpen(false);
    }, [props.parameter, setOpen]);

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
                        const ingress = await invokeCommand<Ingress>(IngressApi.retrieve({id: id}), {defaultErrorHandler: false});
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

    const filters = React.useMemo(() => ({
        filterState: "READY"
    }), []);

    return (<Flex>
        <PointerInput
            id={widgetId(props.parameter) + "visual"}
            placeholder={"No public link selected"}
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
            <IngressBrowse
                computeProvider={props.provider}
                onSelect={onUse}
                additionalFilters={filters}
                onSelectRestriction={res => res.status.boundTo.length === 0}
                browseType={BrowseType.Embedded}
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

function findElement(param: ApplicationParameterNS.Ingress): HTMLSelectElement | null {
    return document.getElementById(widgetId(param)) as HTMLSelectElement | null;
}
