import {appendToXterm, useXTerm} from "Applications/Jobs/xterm";
import {Client, WSFactory} from "Authentication/HttpClientInstance";
import {EmbeddedFileTable} from "Files/FileTable";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {useEffect, useState} from "react";
import {connect} from "react-redux";
import {useRouteMatch} from "react-router";
import {Link} from "react-router-dom";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, ButtonGroup, Card, ContainerForText, ExternalLink, Flex, Hide, List} from "ui-components";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import * as Heading from "ui-components/Heading";
import Icon from "ui-components/Icon";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {Step, StepGroup} from "ui-components/Step";
import {TextSpan} from "ui-components/Text";
import {cancelJob, cancelJobDialog, hpcJobQuery, inCancelableState} from "Utilities/ApplicationUtilities";
import {fileTablePage} from "Utilities/FileUtilities";
import {errorMessageOrDefault, shortUUID} from "UtilityFunctions";
import {
    ApplicationType,
    extendDuration,
    FollowStdStreamResponse,
    isJobStateFinal,
    JobState,
    JobWithStatus,
    WithAppInvocation
} from ".";
import {JobStateIcon} from "./JobStateIcon";
import {runApplication} from "./Pages";
import {pad} from "./View";
import {callAPIWithErrorHandler} from "Authentication/DataHook";
import {isStateComplete, stateToOrder, stateToTitle} from "Applications/api";

interface DetailedResultOperations {
    setPageTitle: (jobId: string) => void;
    setLoading: (loading: boolean) => void;
    setRefresh: (refresh?: () => void) => void;
}

type DetailedResultProps = DetailedResultOperations;

const DetailedResult: React.FunctionComponent<DetailedResultProps> = props => {
    const [status, setStatus] = useState<string>("");
    const [appState, setAppState] = useState<JobState>(JobState.IN_QUEUE);
    const [failedState, setFailedState] = useState<JobState | null>(null);
    const [jobWithStatus, setJobWithStatus] = useState<JobWithStatus | null>(null);
    const [application, setApplication] = useState<WithAppInvocation | null>(null);
    const [interactiveLink, setInteractiveLink] = useState<string | null>(null);
    const [timeLeft, setTimeLeft] = useState<number>(-1);
    const {termRef, terminal} = useXTerm();
    const promises = usePromiseKeeper();

    const match = useRouteMatch<{jobId: string}>();

    const jobId = match.params.jobId;
    const outputFolder = jobWithStatus?.outputFolder ?? "";

    useSidebarPage(SidebarPages.Runs);

    async function fetchJob(): Promise<void> {
        try {
            const {response} = await promises.makeCancelable(Client.get<JobWithStatus>(hpcJobQuery(jobId))).promise;
            setJobWithStatus(response);
            setAppState(response.state);
            setStatus(response.status);
            setFailedState(response.failedState);
        } catch (e) {
            if (e.isCanceled) return;
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching job"), false);
        }
    }

    async function fetchWebLink(): Promise<void> {
        try {
            const {response} = await promises.makeCancelable(Client.get(`/hpc/jobs/query-web/${jobId}`)).promise;
            setInteractiveLink(response.path);
        } catch (e) {
            if (e.isCanceled) return;
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching weblink"), false);
        }
    }

    async function fetchApplication(): Promise<void> {
        if (jobWithStatus === null) return;
        try {
            const {response} = await promises.makeCancelable(Client.get<WithAppInvocation>(
                `/hpc/apps/${encodeURI(jobWithStatus.metadata.name)}/${encodeURI(jobWithStatus.metadata.version)}`
            )).promise;
            setApplication(response);
        } catch (e) {
            if (e.isCanceled) return;
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred fetching application"), false);
        }
    }

    async function extend(hours: number): Promise<void> {
        await promises.makeCancelable(
            callAPIWithErrorHandler(
                extendDuration({
                    jobId: jobWithStatus!.jobId,
                    extendWith: {hours, minutes: 0, seconds: 0}
                })
            )
        ).promise;

        const {response} = await promises.makeCancelable(Client.get<JobWithStatus>(hpcJobQuery(jobId))).promise;
        setJobWithStatus(response);
    }

    useEffect(() => {
        // Re-initialize most stuff when the job id changes
        props.setPageTitle(shortUUID(jobId));
        fetchJob();
        terminal.reset();

        const connection = WSFactory.open(
            "/hpc/jobs", {
                init: conn => {
                    conn.subscribe({
                        call: "hpc.jobs.followWS",
                        payload: {jobId, stdoutLineStart: 0, stderrLineStart: 0},
                        handler: message => {
                            const streamEntry = message.payload as FollowStdStreamResponse;
                            if (streamEntry.state !== null) {
                                if (streamEntry.state !== JobState.RUNNING && timeLeft !== -1) setTimeLeft(-1);
                                setAppState(streamEntry.state);
                            }
                            if (streamEntry.status !== null) setStatus(streamEntry.status);
                            if (streamEntry.failedState !== null) setFailedState(streamEntry.failedState);
                            if (streamEntry.stdout !== null && (streamEntry.rank == null || streamEntry.rank === 0)) {
                                appendToXterm(terminal, streamEntry.stdout);
                            }
                        }
                    });
                }
            });

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
        if ((appState === JobState.SUCCESS || appState === JobState.FAILURE) && outputFolder === "") {
            fetchJob();
        }

        let intervalId: number = -1;
        if (appState === JobState.RUNNING && jobWithStatus !== null &&
            (jobWithStatus.maxTime !== null || jobWithStatus.expiresAt !== null)) {
            const expiresAt = jobWithStatus.expiresAt ? jobWithStatus.expiresAt : Date.now() + jobWithStatus.maxTime!;
            intervalId = window.setInterval(() => {
                setTimeLeft(expiresAt - Date.now());
            }, 500);
        }

        return () => {
            if (intervalId !== -1) window.clearInterval(intervalId);
        };
    }, [appState, jobWithStatus]);

    return (
        <MainContainer
            main={application === null || jobWithStatus == null ? <LoadingIcon size={24}/> : (
                <ContainerForText>
                    <Panel>
                        <StepGroup>
                            <StepTrackerItem
                                stateToDisplay={JobState.IN_QUEUE}
                                currentState={appState}
                                failedState={failedState}
                            />
                            <StepTrackerItem
                                stateToDisplay={JobState.RUNNING}
                                currentState={appState}
                                failedState={failedState}
                            />
                        </StepGroup>
                    </Panel>

                    <Panel width={1}>
                        <Heading.h4>Job Information</Heading.h4>
                        <Card borderRadius="6px" height="auto" p="14px 14px 14px 14px">
                            <List>
                                {jobWithStatus === null || jobWithStatus.name === null ? null : (
                                    <InfoBox><b>Name:</b> {jobWithStatus.name}</InfoBox>
                                )}

                                <InfoBox>
                                    <b>Application:</b>{" "}
                                    {jobWithStatus.metadata.title} v{jobWithStatus.metadata.version}
                                    <Link to={runApplication(jobWithStatus.metadata)}>
                                        <Button ml="10px" px="10px" py="5px">Run app again</Button>
                                    </Link>
                                </InfoBox>

                                <InfoBox><b>Status:</b> {status}</InfoBox>

                                {appState !== JobState.SUCCESS ? null : (
                                    <InfoBox>
                                        Application has completed successfully.
                                        Click <Link to={fileTablePage(outputFolder)}>
                                        <Button px="10px" py="5px">here</Button>
                                    </Link> to go to the output.
                                    </InfoBox>
                                )}

                                {appState !== JobState.RUNNING || timeLeft <= 0 ? null : (
                                    <InfoBox>
                                        <Flex>
                                            <b>Time remaining:&nbsp;</b>
                                            <TimeRemaining timeInMs={timeLeft}/>

                                            <Box flexGrow={1}/>

                                            <ButtonGroup>
                                                <Button onClick={() => {
                                                    extend(1);
                                                }}>+1 hr</Button>
                                                <Button onClick={() => {
                                                    extend(10);
                                                }}>+10 hrs</Button>
                                                <Button onClick={() => {
                                                    extend(24);
                                                }}>+24 hrs</Button>
                                                <Button onClick={() => {
                                                    extend(48);
                                                }}>+48 hrs</Button>
                                            </ButtonGroup>
                                        </Flex>
                                    </InfoBox>
                                )}
                            </List>
                        </Card>
                    </Panel>

                    <Spacer
                        width={1}
                        left={
                            <>
                                {appState !== JobState.RUNNING || interactiveLink === null ? null : (
                                    <InteractiveApplicationLink
                                        type={application.invocation.applicationType}
                                        interactiveLink={interactiveLink}
                                    />
                                )}

                                {appState !== JobState.RUNNING ? null : (
                                    <Link to={`/applications/shell/${jobId}/0`}>
                                        <Button color={"green"} type={"button"}>Go to shell</Button>
                                    </Link>
                                )}
                            </>
                        }
                        right={
                            !inCancelableState(appState) ? null :
                                <Button ml="8px" color="red" onClick={() => onCancelJob(jobId)}>Cancel job</Button>
                        }
                    />

                    {outputFolder === "" || appState !== JobState.SUCCESS && appState !== JobState.FAILURE ? null : (
                        <Panel width={1}>
                            <Heading.h4>Output Files</Heading.h4>
                            <EmbeddedFileTable disableNavigationButtons={true} path={outputFolder}/>
                        </Panel>
                    )}

                    {isJobStateFinal(appState) ? null : (
                        <Box width={1} mt={24}>
                            <Flex flexDirection="column">
                                <Box
                                    width={1}
                                    backgroundColor="midGray"
                                    mt="12px"
                                    pl="12px"
                                    style={{borderRadius: "5px 5px 0px 0px"}}
                                >
                                    <Heading.h4>
                                        Output
                                        &nbsp;
                                        <Dropdown>
                                            <Icon name="info" color="white" color2="black" size="1em"/>
                                            <DropdownContent
                                                width="400px"
                                                visible
                                                colorOnHover={false}
                                                color="white"
                                                backgroundColor="--black"
                                            >
                                                <TextSpan fontSize={1}>
                                                    Streams are collected
                                                    from <code>stdout</code> and <code>stderr</code> of your
                                                    application.
                                                </TextSpan>
                                            </DropdownContent>
                                        </Dropdown>
                                    </Heading.h4>
                                </Box>
                                <Box width={1} backgroundColor="lightGray">
                                    <div ref={termRef}/>
                                </Box>
                            </Flex>
                        </Box>
                    )}
                </ContainerForText>
            )}
        />
    );
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
            return (
                <ExternalLink href={props.interactiveLink}>
                    <Button color="green">Go to web interface</Button>
                </ExternalLink>
            );
        case ApplicationType.VNC:
            return <Link to={props.interactiveLink}><Button color="green">Go to interface</Button></Link>;
        case ApplicationType.BATCH:
            return null;
    }
};

const StepTrackerItem: React.FunctionComponent<{
    stateToDisplay: JobState;
    currentState: JobState;
    failedState: JobState | null;
}> = ({stateToDisplay, currentState, failedState}) => {
    const active = stateToDisplay === currentState;
    const complete = isStateComplete(stateToDisplay, currentState);
    const failedNum = failedState ? stateToOrder(failedState) : 10;
    const thisFailed = stateToOrder(stateToDisplay) >= failedNum;

    return (
        <Step active={active}>
            <JobStateIcon
                isExpired={false}
                state={stateToDisplay}
                color={complete && thisFailed ? "red" : undefined}
                mr="0.7em"
                size="30px"
            />
            <Hide sm xs md lg>
                <TextSpan fontSize={3}>{stateToTitle(stateToDisplay)}</TextSpan>
            </Hide>
        </Step>
    );
};

async function onCancelJob(jobId: string): Promise<void> {
    cancelJobDialog({
        jobId,
        onConfirm: async () => {
            try {
                await cancelJob(Client, jobId);
            } catch (e) {
                snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred cancelling the job"), false);
            }
        }
    });
}

const mapDispatchToProps = (dispatch: Dispatch): DetailedResultOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    setPageTitle: jobId => dispatch(updatePageTitle(`Results for Job: ${jobId}`)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

export default connect(null, mapDispatchToProps)(DetailedResult);
