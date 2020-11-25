import * as React from "react";
import * as UCloud from "UCloud";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "./index";
import {compute} from "UCloud";
import ApplicationParameterNS = compute.ApplicationParameterNS;
import Flex from "ui-components/Flex";
import {RefObject, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {JobState, JobWithStatus, WithAppMetadata} from "Applications";
import {listByName, listJobs, ListJobsProps} from "Applications/api";
import {emptyPage} from "DefaultObjects";
import Text from "ui-components/Text";
import {Link} from "react-router-dom";
import {runApplication, viewApplication} from "Applications/Pages";
import * as ReactModal from "react-modal";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import OutlineButton from "ui-components/OutlineButton";
import {Refresh} from "Navigation/Header";
import Divider from "ui-components/Divider";
import * as Pagination from "Pagination";
import {shortUUID} from "UtilityFunctions";
import {dateToString} from "Utilities/DateUtilities";
import Button from "ui-components/Button";
import styled from "styled-components";
import Input from "ui-components/Input";
import Label from "ui-components/Label";

interface PeerProps extends WidgetProps {
    parameter: UCloud.compute.ApplicationParameterNS.Peer;
}

export const PeerParameter: React.FunctionComponent<PeerProps> = props => {
    const error = props.errors[props.parameter.name] != null;
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
                error={error}
            />
        </Box>
    </Flex>;
};

export const PeerValidator: WidgetValidator = (param) => {
    if (param.type === "peer") {
        const nameElem = findElementName(param);
        const jobElem = findElementJob(param);
        if (nameElem === null || jobElem === null) return {valid: true};
        if (nameElem.value === "" && jobElem.value === "") return {valid: true};

        if (nameElem.value === "" || jobElem.value === "") {
            return {valid: false, message: "All fields must be filled out."};
        }

        return {valid: false, value: {type: "peer", jobId: jobElem.value, hostname: nameElem.value}};
    }

    return {valid: true};
};

export const PeerSetter: WidgetSetter = (param, value) => {
    if (param.type !== "peer") return;
    if (value.type !== "peer") return;

    const name = findElementName(param);
    const job = findElementJob(param);
    if (name === null || job === null) throw "Missing element for: " + param;

    name.value = value.hostname;
    job.value = value.jobId;
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
    error: boolean;
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

    const [availablePeers, fetchAvailablePeers, peerParams] = useCloudAPI<Page<JobWithStatus>, ListJobsProps>(
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
            version,
            filter: JobState.RUNNING
        }));
    }

    if (selectedPeer === undefined && availablePeers.data.items.length > 0 && allowAutoConfigure) {
        // Auto-configure a job if one can be selected
        setSelectedPeer(availablePeers.data.items[0].jobId);
        setAllowAutoConfigure(false);
    }

    return (
        <>
            <Flex>
                <PointerInput
                    readOnly
                    placeholder={"No selected job"}
                    id={widgetId(props.parameter) + "job"}
                    value={selectedPeer ? selectedPeer : ""}
                    onClick={() => {
                        setAllowAutoConfigure(false);
                        setSelectorOpen(true);
                    }}
                    error={props.error}
                />
            </Flex>

            {suggestedApplication === null ? null : (
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
            )}

            <ReactModal
                isOpen={isSelectorOpen}
                onRequestClose={() => setSelectorOpen(false)}
                shouldCloseOnEsc={true}
                ariaHideApp={false}
                style={defaultModalStyle}
            >
                <div>
                    <Flex alignItems={"center"}>
                        <Box flexGrow={1}>
                            <Heading.h3>Jobs</Heading.h3>
                        </Box>
                        <div>
                            {!(peerParams.parameters && peerParams.parameters.application) ? null : (
                                <OutlineButton
                                    type={"button"}
                                    mr={8}
                                    onClick={() => {
                                        fetchAvailablePeers(listJobs({
                                            ...(peerParams.parameters!),
                                            application: undefined,
                                            version: undefined
                                        }));
                                    }}
                                >
                                    Show all
                                </OutlineButton>
                            )}
                            <Refresh
                                spin={availablePeers.loading}
                                onClick={() => fetchAvailablePeers(listJobs(peerParams.parameters!))}
                            />
                        </div>
                    </Flex>
                    <Divider/>

                    <Pagination.List
                        page={availablePeers.data}
                        customEmptyPage={(
                            <Box width={500}>
                                You don&#39;t currently have any running jobs. You can start a new job by selecting an
                                application
                                (in &quot;Apps&quot;) and submitting it to be run.
                            </Box>
                        )}
                        onPageChanged={newPage => {
                            const params = peerParams.parameters;
                            if (!params) return;
                            fetchAvailablePeers(listJobs({...params, page: newPage}));
                        }}
                        loading={availablePeers.loading}
                        pageRenderer={page => {
                            return page.items.map((item, index) => (
                                <Flex key={index} mb={8}>
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
                </div>
            </ReactModal>
        </>
    );
};

export const PointerInput = styled(Input)`
    cursor: pointer;
`;
