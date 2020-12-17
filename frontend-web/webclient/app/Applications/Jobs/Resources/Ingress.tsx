import * as React from "react";
import * as UCloud from "UCloud"
import {Box, Checkbox, Input, Label, SelectableText, SelectableTextWrapper} from "ui-components";
import {TextSpan} from "ui-components/Text";
import Warning from "ui-components/Warning";
import Create from "Applications/Ingresses/Create";
import Browse from "Applications/Ingresses/Browse";
import ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {validateMachineReservation} from "../Widgets/Machines";

export const IngressResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    inputRef: React.RefObject<HTMLInputElement>;
    enabled: boolean;
    setEnabled: React.Dispatch<React.SetStateAction<boolean>>;
}> = props => {
    const [url, setUrl] = React.useState<string>("");
    const [modalOpen, setModalOpen] = React.useState(false);
    const [isBrowsing, setIsBrowsing] = React.useState(true);

    let provider: string | undefined = undefined;

    try {
        const validatedMachineReservation = validateMachineReservation();
        provider = validatedMachineReservation?.provider;
    } catch (e) { /* EMPTY */}

    React.useEffect(() => {
        if (!props.inputRef) return;

        const current = props.inputRef.current;
        if (current === null) return;

        current.value = url;
    }, [props.inputRef, url]);

    return props.application.invocation.applicationType !== "WEB" ? null : (
        <div>
            <div>
                <Label mb={10}>
                    <Checkbox size={28} checked={props.enabled} onChange={() => {
                        props.setEnabled(!props.enabled);
                    }} />
                    <TextSpan>Public link</TextSpan>
                </Label>
            </div>

            <div>
                <Warning warning="By enabling this setting, anyone with a link can gain access to the application." />
                {props.enabled ? (
                    <>
                        <Input
                            mt="8px"
                            placeholder="Click to select public IP..."
                            readOnly
                            value={url}
                            ref={props.inputRef}
                            onClick={() => setModalOpen(true)}
                        />
                        <ReactModal
                            isOpen={modalOpen}
                            style={defaultModalStyle}
                            shouldCloseOnEsc
                            shouldCloseOnOverlayClick
                            onRequestClose={() => setModalOpen(false)}
                        >
                            <Box my="12px">
                                <SelectableTextWrapper>
                                    <SelectableText mr={3} selected={isBrowsing} onClick={() => setIsBrowsing(true)}>
                                        Browse
                                    </SelectableText>
                                    <SelectableText selected={!isBrowsing} onClick={() => setIsBrowsing(!true)}>
                                        Create
                                    </SelectableText>
                                </SelectableTextWrapper>
                                {isBrowsing ?
                                    <Browse computeProvider={provider} onSelect={url => {setUrl(url); setModalOpen(false);}} /> :
                                    <Create computeProvider={provider} onCreateFinished={() => setIsBrowsing(true)} />}
                            </Box>
                        </ReactModal>
                    </>
                ) : (<></>)}
            </div>
        </div>
    );
};
