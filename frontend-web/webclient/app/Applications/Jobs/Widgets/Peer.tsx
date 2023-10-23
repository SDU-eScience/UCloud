import * as React from "react";
import * as UCloud from "@/UCloud";
import {widgetId, WidgetProps, WidgetSetProvider, WidgetSetter, WidgetValidationAnswer} from "./index";
import {compute} from "@/UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "@/ui-components/Flex";
import {useCallback, useMemo, useState} from "react";
import Box from "@/ui-components/Box";
import Input from "@/ui-components/Input";
import Label from "@/ui-components/Label";
import AppParameterValueNS = compute.AppParameterValueNS;
import {default as ReactModal} from "react-modal";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {checkProviderMismatch} from "../Create";
import JobBrowse from "../JobsBrowse";

interface PeerProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Peer;
}

export const PeerParameter: React.FunctionComponent<PeerProps> = props => {
    return <Flex mb={8}>
        <div>
            <Label>
                Hostname
                <Input
                    placeholder={"Example: spark-cluster"}
                    id={widgetId(props.parameter) + "name"}
                    value={props.parameter.title.length !== 0 ? props.parameter.name : undefined}
                    disabled={props.parameter.title.length !== 0}
                />
            </Label>
        </div>

        <Box flexGrow={1} ml={2}>
            <Label>
                Job
            </Label>

            <JobSelector
                parameter={props.parameter}
                suggestedApplication={props.parameter.suggestedApplication}
                errors={props.errors}
                setErrors={props.setErrors}
            />
        </Box>
    </Flex>;
};

export function PeerValidator(param: compute.ApplicationParameter): WidgetValidationAnswer {
    if (param.type === "peer") {
        const nameElem = findElementName(param);
        const jobElem = findElementJob(param);
        if (nameElem === null || jobElem === null) return {valid: true};
        if (nameElem.value === "" && jobElem.value === "") return {valid: true};

        if (nameElem.value === "" || jobElem.value === "") {
            return {valid: false, message: "All fields must be filled out."};
        }

        return {valid: true, value: {type: "peer", jobId: jobElem.value, hostname: nameElem.value}};
    }

    return {valid: true};
}

export const PeerSetter: WidgetSetter = (param, value) => {
    if (param.type !== "peer") return;

    const name = findElementName(param);
    const job = findElementJob(param);
    if (name === null || job === null) throw "Missing element for: " + param;

    const peerValue = value as AppParameterValueNS.Peer;
    name.value = peerValue.hostname;
    job.value = peerValue.jobId;
};

function findElementName(param: ApplicationParameterNS.Peer): HTMLInputElement | null {
    return document.getElementById(widgetId(param) + "name") as HTMLInputElement | null;
}

function findElementJob(param: ApplicationParameterNS.Peer): HTMLInputElement | null {
    return document.getElementById(widgetId(param) + "job") as HTMLInputElement | null;
}

interface JobSelectorProps {
    parameter: ApplicationParameterNS.Peer;
    suggestedApplication?: string;
    errors: Record<string, string>;
    setErrors: (errors: Record<string, string>) => void;
}

const JobSelector: React.FunctionComponent<JobSelectorProps> = props => {
    const [allowAutoConfigure, setAllowAutoConfigure] = useState<boolean>(true);
    const [open, setOpen] = useState(false);
    const doOpen = useCallback(() => {
        setOpen(true);
    }, [setOpen]);
    const doClose = useCallback(() => {
        setOpen(false)
    }, [setOpen]);

    React.useEffect(() => {
        if (props.suggestedApplication === null && allowAutoConfigure) {
            setAllowAutoConfigure(false);
        }
    }, [props.suggestedApplication, allowAutoConfigure]);

    const filters = useMemo(() => {
        const fi = ({filterState: "RUNNING"})
        if (props.suggestedApplication) fi["filterApplication"] = props.suggestedApplication;
        return fi;
    }, [props.suggestedApplication]);

    return (<Flex>
        <Input
            id={widgetId(props.parameter) + "job"}
            placeholder={"No selected run"}
            onClick={doOpen}
            cursor="pointer"
            style={{height: "39px"}}
            readOnly
        />
        <ReactModal
            isOpen={open}
            ariaHideApp={false}
            style={largeModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            onRequestClose={doClose}
        >
            <JobBrowse
                opts={{
                    additionalFilters: filters,
                    embedded: true,
                    selection: {
                        text: "Use",
                        onSelectRestriction(job) {
                            const errorMessage = checkProviderMismatch(job, "Jobs");
                            if (errorMessage) return errorMessage;
                            return true;
                        },
                        onSelect(job) {
                            const el = document.getElementById(widgetId(props.parameter) + "job");
                            if (el) {
                                (el as HTMLInputElement).value = job.id;
                            }
                            WidgetSetProvider({name: props.parameter.name + "job"}, job.specification.product.provider);
                            if (props.errors[props.parameter.name]) {
                                delete props.errors[props.parameter.name];
                                props.setErrors({...props.errors});
                            }
                            setAllowAutoConfigure(false);
                            doClose();

                        }
                    }
                }}
            />
        </ReactModal>
    </Flex>);
};
