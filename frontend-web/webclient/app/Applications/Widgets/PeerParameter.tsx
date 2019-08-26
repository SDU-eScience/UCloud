import {BaseParameter, MandatoryField, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as Types from "Applications";
import * as React from "react";
import Input from "ui-components/Input";
import styled from "styled-components";
import Flex from "ui-components/Flex";
import Text from "ui-components/Text";
import {Link} from "react-router-dom";
import {RefObject, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {Analysis, Application, AppState, RunAppState, WithAppMetadata} from "Applications";
import {Page} from "Types";
import {listByName, listJobs, ListJobsProps} from "Applications/api";
import {emptyPage} from "DefaultObjects";
import {runApplication, viewApplication} from "Applications/Pages";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import * as Heading from "ui-components/Heading";
import Divider from "ui-components/Divider";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import * as Pagination from "Pagination";
import {shortUUID} from "UtilityFunctions";
import {dateToString} from "Utilities/DateUtilities";
import OutlineButton from "ui-components/OutlineButton";
import {Refresh} from "Navigation/Header";
import Label from "ui-components/Label";

interface PeerParameterProps extends ParameterProps {
    parameter: Types.PeerParameter
}

export const PeerParameter: React.FunctionComponent<PeerParameterProps> = props => {
    return <BaseParameter parameter={props.parameter}>
        <JobSelector
            parameterRef={props.parameterRef as RefObject<HTMLInputElement>}
            suggestedApplication={props.parameter.suggestedApplication}
        />
    </BaseParameter>;
};

interface AdditionalPeerParameterProps {
    jobIdRef: RefObject<HTMLInputElement>
    nameRef: RefObject<HTMLInputElement>
    onRemove: () => void
    hideLabels?: boolean
}

export const AdditionalPeerParameter: React.FunctionComponent<AdditionalPeerParameterProps> = props => {
    return <Flex mb={8}>
        <Box>
            <Label>
                {props.hideLabels ? null : <>Hostname <MandatoryField/></>}
                <Input placeholder={"Example: spark-cluster"} ref={props.nameRef}/>
            </Label>
        </Box>

        <Box flexGrow={1} ml={2}>
            <Label>
                {props.hideLabels ? null : <>Job <MandatoryField/></>}
                <JobSelector parameterRef={props.jobIdRef} suggestedApplication={null}/>
            </Label>
        </Box>

        <Box ml={2}>
            <Label>
                {props.hideLabels ? null : <br/>}
                <Button type={"button"} height={"35px"} onClick={() => props.onRemove()}>✗</Button>
            </Label>
        </Box>
    </Flex>;
};

interface JobSelectorProps {
    parameterRef: RefObject<HTMLInputElement>
    suggestedApplication: string | null
}

const JobSelector: React.FunctionComponent<JobSelectorProps> = props => {
    const [selectedPeer, setSelectedPeer] = useState<string | undefined>(undefined);
    const [allowAutoConfigure, setAllowAutoConfigure] = useState<boolean>(true);

    const [isSelectorOpen, setSelectorOpen] = useState<boolean>(false);

    const [suggestedApplicationApi] = useCloudAPI<Page<WithAppMetadata>>(
        props.suggestedApplication ?
            listByName({name: props.suggestedApplication, itemsPerPage: 50, page: 0}) :
            {noop: true},
        {...emptyPage, itemsPerPage: -1}
    );

    const [availablePeers, fetchAvailablePeers, peerParams] = useCloudAPI<Page<Analysis>, ListJobsProps>(
        {noop: true},
        {...emptyPage, itemsPerPage: -1}
    );

    const suggestedApplication = suggestedApplicationApi.data.items.length > 0 ?
        suggestedApplicationApi.data.items[0] : null;

    if (props.suggestedApplication === null && allowAutoConfigure) {
        setAllowAutoConfigure(false);
    }

    if ((suggestedApplicationApi.data.itemsPerPage !== -1 || isSelectorOpen) && peerParams.noop) {
        // Load available peers once we have loaded the suggested application (if one exists)
        const name = suggestedApplication ? suggestedApplication.metadata.name : undefined;
        const version = suggestedApplication ? suggestedApplication.metadata.version : undefined;
        fetchAvailablePeers(listJobs({
            itemsPerPage: 50,
            page: 0,
            application: name,
            version: version,
            filter: AppState.RUNNING
        }));
    }

    if (selectedPeer === undefined && availablePeers.data.items.length > 0 && allowAutoConfigure) {
        // Auto-configure a job if one can be selected
        setSelectedPeer(availablePeers.data.items[0].jobId);
        setAllowAutoConfigure(false);
    }

    return <>
        <Flex>
            <PointerInput
                readOnly
                placeholder={"No selected job"}
                ref={props.parameterRef as RefObject<HTMLInputElement>}
                value={selectedPeer ? selectedPeer : ""}
                onClick={() => {
                    setAllowAutoConfigure(false);
                    setSelectorOpen(true);
                }}
            />
        </Flex>

        {suggestedApplication === null ? null :
            <Text>
                This application requires you to run {" "}
                <Link to={viewApplication(suggestedApplication.metadata)} target={"_blank"}>
                    {suggestedApplication.metadata.title}.
                </Link>
                {" "}
                Would you like to start {" "}
                <Link to={runApplication(suggestedApplication.metadata)} target={"_blank"}>
                    a new one?
                </Link>
            </Text>
        }

        <ReactModal
            isOpen={isSelectorOpen}
            onRequestClose={() => setSelectorOpen(false)}
            shouldCloseOnEsc={true}
            ariaHideApp={false}
            style={defaultModalStyle}
        >
            <Box>
                <Flex alignItems={"center"}>
                    <Box flexGrow={1}>
                        <Heading.h3>Jobs</Heading.h3>
                    </Box>
                    <Box>
                        {!(peerParams.parameters && peerParams.parameters.application) ? null :
                            <OutlineButton
                                type={"button"}
                                mr={8}
                                onClick={() => {
                                    fetchAvailablePeers(listJobs({
                                        ...(peerParams.parameters!),
                                        application: undefined,
                                        version: undefined
                                    }))
                                }}
                            >
                                Show all
                            </OutlineButton>
                        }
                        <Refresh
                            spin={availablePeers.loading}
                            onClick={() => fetchAvailablePeers(listJobs(peerParams.parameters!))}
                        />
                    </Box>
                </Flex>
                <Divider/>

                <Pagination.List
                    page={availablePeers.data}
                    customEmptyPage={<Box width={500}>
                        You don't currently have any running jobs. You can start a new job by selecting an application
                        (in "Apps") and submitting it to be run.
                    </Box>}
                    onPageChanged={newPage => {
                        const params = peerParams.parameters;
                        if (!params) return;
                        fetchAvailablePeers(listJobs({...params, page: newPage}));
                    }}
                    loading={availablePeers.loading}
                    pageRenderer={page => {
                        return page.items.map(item => (
                            <Flex mb={8}>
                                <Box flexGrow={1}>
                                    {item.metadata.title}
                                    {" "}
                                    ({item.name ? item.name : shortUUID(item.jobId)})
                                    <br/>
                                    {dateToString(item.createdAt)}
                                </Box>
                                <Button
                                    type={"button"}
                                    onClick={() => {
                                        setSelectedPeer(item.jobId);
                                        setSelectorOpen(false);
                                    }}
                                >
                                    Select
                                </Button>
                            </Flex>
                        ));
                    }}
                />
            </Box>
        </ReactModal>
    </>;
};

export const PointerInput = styled(Input)`
    cursor: pointer;
`;