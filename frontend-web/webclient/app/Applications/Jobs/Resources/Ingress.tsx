import * as React from "react";
import * as UCloud from "UCloud"
import {Box, Button, Checkbox, Flex, Icon, Input, Label, SelectableText, SelectableTextWrapper, Text} from "ui-components";
import {TextSpan} from "ui-components/Text";
import Warning from "ui-components/Warning";
import Create from "Applications/Ingresses/Create";
import Browse from "Applications/Ingresses/Browse";
import ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {validateMachineReservation} from "../Widgets/Machines";
import {Spacer} from "ui-components/Spacer";
import {snackbarStore} from "Snackbar/SnackbarStore";

export const IngressResource: React.FunctionComponent<{
    application: UCloud.compute.Application;
    enabled: boolean;
    addRow(): void;
    setEnabled: React.Dispatch<React.SetStateAction<boolean>>;
}> = props =>
        props.application.invocation.applicationType !== "WEB" ? null : (
            <div>
                <div>
                    <Spacer
                        left={
                            <Label mb={10}>
                                <Checkbox size={28} checked={props.enabled} onChange={() => {
                                    props.setEnabled(!props.enabled);
                                }} />
                                <TextSpan>Public link</TextSpan>
                            </Label>
                        }
                        right={!props.enabled ? null : (
                            <Button onClick={props.addRow} height="32px" width="160px">Add ingress</Button>
                        )}
                    />
                </div>
                <Box mb="6px">
                    <Warning warning="By enabling this setting, anyone with a link can gain access to the application." />
                </Box>
            </div>
        );

interface IngressRowProps {
    refs: {id: React.RefObject<HTMLInputElement>; domain: React.RefObject<HTMLInputElement>};
    provider: string | undefined;
    onRemove?(): void;
}

export function IngressRow(props: IngressRowProps): JSX.Element {
    const [url, setUrl] = React.useState({domain: "", id: ""});
    const [modalOpen, setModalOpen] = React.useState(false);
    const [isBrowsing, setIsBrowsing] = React.useState(true);

    React.useEffect(() => {
        if (!props.refs.id.current) return
        if (!props.refs.domain.current) return;

        const domain = props.refs.domain.current;
        const id = props.refs.id.current;

        domain.value = url.domain;
        id.value = url.id;
    }, [props.refs.domain, props.refs.id, url]);

    return (<>
        {props.onRemove ? <>
            <Label fontSize={1}>
                <Flex>
                    {!props.onRemove ? null : (
                        <>
                            <Box ml="auto" />
                            <Text color="red" cursor="pointer" mb="4px" onClick={props.onRemove} selectable={false}>
                                Remove <Icon ml="6px" size={16} name="close" />
                            </Text>
                        </>
                    )}
                </Flex>
            </Label>
        </> : null}
        <Input
            mb="4px"
            placeholder="Click to select public IP..."
            readOnly
            value={url.domain}
            ref={props.refs.domain}
            onClick={() => setModalOpen(true)}
        />
        <Input readOnly hidden ref={props.refs.id} />
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
                <Box minWidth="600px">
                    {isBrowsing ?
                        <Browse computeProvider={props.provider} onSelect={ingress => {
                            setUrl(ingress);
                            setModalOpen(false);
                        }} /> :
                        <Create computeProvider={props.provider} onCreateFinished={() => setIsBrowsing(true)} />}
                </Box>
            </Box>
        </ReactModal>
    </>)
}

export function getProviderField(): string | undefined {
    try {
        const validatedMachineReservation = validateMachineReservation();
        return validatedMachineReservation?.provider;
    } catch (e) {
        return undefined;
    }
}

interface ValidatedIngresses {
    errors: string[];
    values: UCloud.compute.AppParameterValueNS.Ingress[];
}

export function validateIngresses(urls: {domain: React.RefObject<HTMLInputElement>, id: React.RefObject<HTMLInputElement>}[], enabled: boolean): ValidatedIngresses {
    const result: ValidatedIngresses = {errors: [], values: []};
    if (!enabled) return result;

    urls.forEach(url => {
        if (url.domain.current?.value && url.id.current?.value) {
            result.values.push({id: url.id.current.value, type: "ingress"});
        }

        // TODO: Handle errors
    });
    if (result.values.length === 0) {
        result.errors.push("Ingress is enabled, but none are provided.");
        snackbarStore.addFailure("Ingress is enabled, but none are provided.", false);
    }

    return result;
}