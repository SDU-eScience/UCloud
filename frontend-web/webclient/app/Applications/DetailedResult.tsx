import {useXTerm} from "Applications/xterm";
import {Cloud, WSFactory} from "Authentication/SDUCloudObject";
import {EmbeddedFileTable} from "Files/FileTable";
import {History} from "history";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {useEffect, useState} from "react";
import {connect} from "react-redux";
import {match} from "react-router";
import {Link} from "react-router-dom";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Card, ContainerForText, ExternalLink, Flex, List} from "ui-components";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import * as Heading from "ui-components/Heading";
import Icon from "ui-components/Icon";
import {Spacer} from "ui-components/Spacer";
import {Step, StepGroup} from "ui-components/Step";
import {TextSpan} from "ui-components/Text";
import {cancelJob, cancelJobDialog, hpcJobQuery, inCancelableState} from "Utilities/ApplicationUtilities";
import {fileTablePage} from "Utilities/FileUtilities";
import {errorMessageOrDefault, shortUUID} from "UtilityFunctions";
import {ApplicationType, FollowStdStreamResponse, isJobStateFinal, JobState, JobWithStatus, WithAppInvocation} from ".";
import {JobStateIcon} from "./JobStateIcon";
import {pad} from "./View";

interface DetailedResultOperations {
    setPageTitle: (jobId: string) => void;
    setLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
}

interface DetailedResultProps extends DetailedResultOperations {
    match: match<{ jobId: string }>;
    history: History;
}

const DetailedResult: React.FunctionComponent<DetailedResultProps> = props => {
    const [status, setStatus] = useState<string>("");
    const [appState, setAppState] = useState<JobState>(JobState.VALIDATED);
    const [failedState, setFailedState] = useState<JobState | null>(null);
    const [jobWithStatus, setJobWithStatus] = useState<JobWithStatus | null>(null);
    const [application, setApplication] = useState<WithAppInvocation | null>(null);
    const [interactiveLink, setInteractiveLink] = useState<string | null>(null);
    const [timeLeft, setTimeLeft] = useState<number>(-1);
    const jobId = props.match.params.jobId;
    const outputFolder = jobWithStatus && jobWithStatus.outputFolder ? jobWithStatus.outputFolder : "";
    const [xtermRef, appendToXterm, resetXterm] = useXTerm();

    async function fetchJob() {
        const {response} = await Cloud.get<JobWithStatus>(hpcJobQuery(jobId));
        setJobWithStatus(response);
        setAppState(response.state);
        setStatus(response.status);
        setFailedState(response.failedState);
    }

    async function fetchWebLink() {
        const {response} = await Cloud.get(`/hpc/jobs/query-web/${jobId}`);
        setInteractiveLink(response.path);
    }

    async function fetchApplication() {
        if (jobWithStatus === null) return;
        const {response} = await Cloud.get<WithAppInvocation>(
            `/hpc/apps/${encodeURI(jobWithStatus.metadata.name)}/${encodeURI(jobWithStatus.metadata.version)}`
        );

        setApplication(response);
    }

    async function onCancelJob() {
        cancelJobDialog({
            jobId,
            onConfirm: async () => {
                try {
                    await cancelJob(Cloud, jobId);
                } catch (e) {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred cancelling the job"));
                }
            }
        });
    }

    useEffect(() => {
        // Re-initialize most stuff when the job id changes
        props.setPageTitle(shortUUID(jobId));
        fetchJob();
        resetXterm();

        const connection = WSFactory.open(
            "/hpc/jobs", {
                init: conn => {
                    conn.subscribe({
                        call: "hpc.jobs.followWS",
                        payload: {jobId, stdoutLineStart: 0, stderrLineStart: 0},
                        handler: message => {
                            const streamEntry = message.payload as FollowStdStreamResponse;
                            if (streamEntry.state !== null) setAppState(streamEntry.state);
                            if (streamEntry.status !== null) setStatus(streamEntry.status);
                            if (streamEntry.failedState !== null) setFailedState(streamEntry.failedState);
                            if (streamEntry.stdout !== null) appendToXterm(streamEntry.stdout);
                        }
                    });
                }
            }
        );

        return () => {
            connection.close();
        };
    }, [jobId]);

    useEffect(() => {
        // Fetch the application when we know about the job
        fetchApplication();
    }, [jobWithStatus]);

    useEffect(() => {
        // Fetch information about the interactive button on appState change
        if (jobWithStatus === null || application === null) return;
        if (appState === JobState.RUNNING && interactiveLink === null) {
            switch (application.invocation.applicationType) {
                case ApplicationType.VNC:
                    setInteractiveLink(`/novnc?jobId=${jobId}`);
                    break;
                case ApplicationType.WEB:
                    fetchWebLink();
                    break;
            }
        }
    }, [appState, interactiveLink, jobWithStatus, application]);

    useEffect(() => {
        // Re-fetch job if we don't know about the output folder at the end of the job
        if (appState === JobState.SUCCESS && outputFolder === "") {
            fetchJob();
        }

        let intervalId: number = -1;
        if (appState === JobState.RUNNING && timeLeft === -1 && jobWithStatus !== null &&
            (jobWithStatus.maxTime !== null || jobWithStatus.expiresAt !== null)) {
            const expiresAt = jobWithStatus.expiresAt ? jobWithStatus.expiresAt : Date.now() + jobWithStatus.maxTime!;
            intervalId = window.setInterval(() => {
                setTimeLeft(expiresAt - Date.now());
            }, 500);
        }

        return () => {
            if (intervalId !== -1) window.clearInterval(intervalId);
        };
    }, [appState]);

    return <MainContainer
        main={application === null || jobWithStatus == null ? <LoadingIcon size={24}/> :
            <ContainerForText>
                <Panel>
                    <StepGroup>
                        <StepTrackerItem
                            stateToDisplay={JobState.VALIDATED}
                            currentState={appState}
                            failedState={failedState}/>
                        <StepTrackerItem
                            stateToDisplay={JobState.PREPARED}
                            currentState={appState}
                            failedState={failedState}/>
                        <StepTrackerItem
                            stateToDisplay={JobState.SCHEDULED}
                            currentState={appState}
                            failedState={failedState}/>
                        <StepTrackerItem
                            stateToDisplay={JobState.RUNNING}
                            currentState={appState}
                            failedState={failedState}/>
                        <StepTrackerItem
                            stateToDisplay={JobState.TRANSFER_SUCCESS}
                            currentState={appState}
                            failedState={failedState}/>
                    </StepGroup>
                </Panel>


                <Panel>
                    <Heading.h4>Job Information</Heading.h4>
                    <Card height="auto" p="14px 14px 14px 14px">
                        <List>
                            {jobWithStatus === null || jobWithStatus.name === null ? null :
                                <InfoBox><b>Name:</b> {jobWithStatus.name}</InfoBox>
                            }

                            <InfoBox>
                                <b>Application:</b>{" "}
                                {jobWithStatus.metadata.title} v{jobWithStatus.metadata.version}
                            </InfoBox>

                            <InfoBox><b>Status:</b> {status}</InfoBox>

                            {appState !== JobState.SUCCESS ? null :
                                <InfoBox>
                                    Application has completed successfully.
                                    Click <Link to={fileTablePage(outputFolder)}>here</Link> to go to the
                                    output.
                                </InfoBox>
                            }

                            {appState !== JobState.RUNNING || timeLeft <= 0 ? null :
                                <InfoBox>
                                    <b>Time remaining:</b>{" "}
                                    <TimeRemaining timeInMs={timeLeft}/>
                                </InfoBox>
                            }
                        </List>
                    </Card>
                </Panel>

                <Spacer
                    left={
                        appState !== JobState.RUNNING || interactiveLink === null ? null :
                            <InteractiveApplicationLink
                                type={application.invocation.applicationType}
                                interactiveLink={interactiveLink}/>
                    }
                    right={
                        !inCancelableState(appState) ? null :
                            <Button ml="8px" color="red" onClick={() => onCancelJob()}>Cancel job</Button>
                    }
                />

                {outputFolder === "" || appState !== JobState.SUCCESS ? null :
                    <Panel>
                        <Heading.h4>Output Files</Heading.h4>
                        <EmbeddedFileTable path={outputFolder}/>
                    </Panel>
                }

                {isJobStateFinal(appState) ? null :
                    <Box width="100%" mt={24}>
                        <Heading.h4>
                            Standard Streams
                            &nbsp;
                            <Dropdown>
                                <Icon name="info" color="white" color2="black" size="1em"/>
                                <DropdownContent
                                    width="400px"
                                    visible
                                    colorOnHover={false}
                                    color="white"
                                    backgroundColor="black"
                                >
                                    <TextSpan fontSize={1}>
                                        Streams are collected
                                        from <code>stdout</code> and <code>stderr</code> of your application.
                                    </TextSpan>
                                </DropdownContent>
                            </Dropdown>
                        </Heading.h4>
                        <Flex flexDirection="column">
                            <Box width={1} backgroundColor="midGray" mt={"12px"} pl={"12px"}>
                                <Heading.h5>Output</Heading.h5>
                            </Box>
                            <Box width={1} backgroundColor="lightGray">
                                <div ref={xtermRef}/>
                            </Box>
                        </Flex>
                    </Box>
                }
            </ContainerForText>
        }
    />;
};

const Panel = styled(Box)`
    margin-bottom: 1em;
`;

Panel.displayName = "Panel";

const InfoBox = styled(Box)`
    padding-top: 0.8em;
    padding-bottom: 0.8em;
`;

const TimeRemaining: React.FunctionComponent<{ timeInMs: number }> = props => {
    const timeLeft = props.timeInMs;
    const seconds = (timeLeft / 1000) % 60;
    const minutes = ((timeLeft / (1000 * 60)) % 60);
    const hours = (timeLeft / (1000 * 60 * 60));
    return <>{pad(hours | 0, 2)}:{pad(minutes | 0, 2)}:{pad(seconds | 0, 2)}</>;
};

const InteractiveApplicationLink: React.FunctionComponent<{
    type: ApplicationType,
    interactiveLink: string
}> = props => {
    switch (props.type) {
        case ApplicationType.WEB:
            return <ExternalLink href={props.interactiveLink}>
                <Button color={"green"}>Go to web interface</Button>
            </ExternalLink>;
        case ApplicationType.VNC:
            return <Link to={props.interactiveLink}><Button color={"green"}>Go to interface</Button></Link>;
        case ApplicationType.BATCH:
            return null;
    }
};

const stateToOrder = (state: JobState): 0 | 1 | 2 | 3 | 4 | 5 => {
    switch (state) {
        case JobState.VALIDATED:
            return 0;
        case JobState.PREPARED:
            return 1;
        case JobState.SCHEDULED:
            return 2;
        case JobState.RUNNING:
            return 3;
        case JobState.TRANSFER_SUCCESS:
            return 4;
        case JobState.SUCCESS:
            return 5;
        case JobState.FAILURE:
            return 5;
        default:
            return 0;
    }
};

const isStateComplete = (state: JobState, currentState: JobState) =>
    stateToOrder(state) < stateToOrder(currentState);

const stateToTitle = (state: JobState): string => {
    switch (state) {
        case JobState.FAILURE:
            return "Failure";
        case JobState.PREPARED:
            return "Pending";
        case JobState.RUNNING:
            return "Running";
        case JobState.SCHEDULED:
            return "Scheduled";
        case JobState.SUCCESS:
            return "Success";
        case JobState.TRANSFER_SUCCESS:
            return "Transferring";
        case JobState.VALIDATED:
            return "Validated";
        default:
            return "Unknown";
    }
};

const StepTrackerItem: React.FunctionComponent<{
    stateToDisplay: JobState,
    currentState: JobState,
    failedState: JobState | null
}> = ({stateToDisplay, currentState, failedState}) => {
    const active = stateToDisplay === currentState;
    const complete = isStateComplete(stateToDisplay, currentState);
    const failedNum = failedState ? stateToOrder(failedState) : 10;
    const thisFailed = stateToOrder(stateToDisplay) >= failedNum;

    return (
        <Step active={active}>
            {complete ?
                <Icon name={thisFailed ? "close" : "check"} color={thisFailed ? "red" : "green"} mr="0.7em"
                      size="30px"/> :
                <JobStateIcon state={stateToDisplay} mr="0.7em" size="30px"/>
            }
            <TextSpan fontSize={3}>{stateToTitle(stateToDisplay)}</TextSpan>
        </Step>
    );
};

const mapDispatchToProps = (dispatch: Dispatch): DetailedResultOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    setPageTitle: jobId => dispatch(updatePageTitle(`Results for Job: ${jobId}`)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

export default connect(null, mapDispatchToProps)(DetailedResult);
