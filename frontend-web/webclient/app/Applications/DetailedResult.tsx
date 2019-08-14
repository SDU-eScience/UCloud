import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import {Cloud} from "Authentication/SDUCloudObject";
import {shortUUID, errorMessageOrDefault} from "UtilityFunctions";
import {Link} from "react-router-dom";
import {connect} from "react-redux";
import {setLoading, updatePageTitle} from "Navigation/Redux/StatusActions";
import {
    DetailedResultProps,
    DetailedResultState,
    StdElement,
    DetailedResultOperations,
    AppState,
    WithAppInvocation
} from ".";
import {fileTablePage} from "Utilities/FileUtilities";
import {hpcJobQuery, cancelJobDialog, inCancelableState, cancelJob} from "Utilities/ApplicationUtilities";
import {Dispatch} from "redux";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import {Flex, Box, List, Card, ContainerForText, ExternalLink, Button} from "ui-components";
import {Step, StepGroup} from "ui-components/Step";
import styled from "styled-components";
import {TextSpan} from "ui-components/Text";
import Icon from "ui-components/Icon";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import * as Heading from "ui-components/Heading";
import {JobStateIcon} from "./JobStateIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {Spacer} from "ui-components/Spacer";
import {EmbeddedFileTable} from "Files/FileTable";

const Panel = styled(Box)`
    margin-bottom: 1em;
`;

Panel.displayName = "Panel";

class DetailedResult extends React.Component<DetailedResultProps, DetailedResultState> {
    private stdoutEl: StdElement;
    private stderrEl: StdElement;

    constructor(props: Readonly<DetailedResultProps>) {
        super(props);
        this.state = {
            name: "",
            complete: false,
            appState: AppState.VALIDATED,
            status: "",
            stdout: "",
            stderr: "",
            stdoutLine: 0,
            stderrLine: 0,
            stdoutOldTop: -1,
            stderrOldTop: -1,
            reloadIntervalId: -1,
            promises: new PromiseKeeper(),
            appType: undefined,
            webLink: undefined
        };
        this.props.setPageTitle(shortUUID(this.jobId));
    }

    get jobId(): string {
        return this.props.match.params.jobId;
    }

    public async componentDidUpdate() {
        if (!this.state.appType && this.state.app) {
            const {name, version} = this.state.app;
            const {response} = await this.state.promises.makeCancelable(
                Cloud.get<WithAppInvocation>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            this.setState(() => ({appType: response.invocation.applicationType}));
        }
    }

    public componentDidMount() {
        const reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
        this.setState(() => ({reloadIntervalId}));
    }

    public componentWillUnmount() {
        if (this.state.reloadIntervalId) window.clearTimeout(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
    }

    private scrollIfNeeded() {
        if (!this.stdoutEl || !this.stderrEl) return;

        if (this.stdoutEl.scrollTop === this.state.stdoutOldTop || this.state.stderrOldTop === -1) {
            this.stdoutEl.scrollTop = this.stdoutEl.scrollHeight;
        }

        if (this.stderrEl.scrollTop === this.state.stderrOldTop || this.state.stderrOldTop === -1) {
            this.stderrEl.scrollTop = this.stderrEl.scrollHeight;
        }

        const outTop = this.stdoutEl.scrollTop;
        const errTop = this.stderrEl.scrollTop;

        this.setState(() => ({
            stdoutOldTop: outTop,
            stderrOldTop: errTop
        }));
    }

    private async retrieveStdStreams() {
        if (this.state.complete) {
            this.retrieveStateWhenCompleted();
            return;
        } else if (this.state.appState === AppState.RUNNING) {
            if (this.state.appType === "VNC") {
                this.props.setLoading(false);
                this.setState({webLink: `/novnc?jobId=${this.jobId}`})
            } else if (this.state.appType === "WEB" && !this.state.webLink) {
                this.props.setLoading(false);
                const {response} = await this.state.promises.makeCancelable(
                    Cloud.get(`/hpc/jobs/query-web/${this.jobId}`)
                ).promise;
                this.setState(() => ({webLink: response.path}));
            }
        }
        try {
            this.props.setLoading(true);
            const {response} = await this.state.promises.makeCancelable(
                Cloud.get(hpcJobQuery(this.jobId, this.state.stdoutLine, this.state.stderrLine))
            ).promise;

            this.setState(() => ({
                stdout: this.state.stdout.concat(response.stdout),
                stderr: this.state.stderr.concat(response.stderr),
                stdoutLine: response.stdoutNextLine,
                stderrLine: response.stderrNextLine,

                app: response.metadata,
                status: response.status,
                appState: response.state,
                complete: response.complete,
                outputFolder: response.outputFolder
            }));

            this.scrollIfNeeded();
            if (response.complete) this.retrieveStateWhenCompleted();
            else {
                const reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
                this.setState(() => ({reloadIntervalId}));
            }
        } catch (e) {
            if (!e.isCanceled)
                snackbarStore.addSnack({
                    message: "An error occurred retrieving Information and Output from the job.",
                    type: SnackType.Failure
                });
        } finally {
            this.props.setLoading(false);
        }
    }

    private retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        window.clearTimeout(this.state.reloadIntervalId);
    }

    private renderProgressPanel = () => (
        <Panel>
            <StepGroup>
                <StepTrackerItem
                    stateToDisplay={AppState.VALIDATED}
                    currentState={this.state.appState}/>
                <StepTrackerItem
                    stateToDisplay={AppState.PREPARED}
                    currentState={this.state.appState}/>
                <StepTrackerItem
                    stateToDisplay={AppState.SCHEDULED}
                    currentState={this.state.appState}/>
                <StepTrackerItem
                    stateToDisplay={AppState.RUNNING}
                    currentState={this.state.appState}/>
                <StepTrackerItem
                    stateToDisplay={AppState.TRANSFER_SUCCESS}
                    currentState={this.state.appState}/>
            </StepGroup>
        </Panel>
    );

    private renderInfoPanel() {
        const {app} = this.state;
        if (app === undefined) return null;

        let entries = [
            {key: "Application", value: `${app.title} v${app.version}`},
            {key: "Status", value: this.state.status},
        ];

        if (this.state.name) entries.unshift([{key: "Name", value: this.state.name}]);

        let domEntries = entries.map(it => <Box pt="0.8em" pb="0.8em" key={it.key}><b>{it.key}</b>: {it.value}</Box>);

        switch (this.state.appState) {
            case AppState.SUCCESS:
                domEntries.push(
                    <Box key={AppState.SUCCESS} pt="0.8em" pb="0.8em">
                        Application has completed successfully.
                        Click <Link to={fileTablePage(this.state.outputFolder!)}>here</Link> to go to the output.
                    </Box>
                );
                break;
        }

        return (
            <Panel>
                <Heading.h4>Job Information</Heading.h4>
                <Card height="auto" p="14px 14px 14px 14px">
                    <List>
                        {domEntries}
                    </List>
                </Card>
            </Panel>
        );
    }

    private renderStreamPanel() {
        if (this.state.complete && this.state.stdout === "" && this.state.stderr === "") return null;
        return (
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
                        <Stream ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></Stream>
                    </Box>
                    <Box width={1} backgroundColor="midGray" mt={"12px"} pl={"12px"}>
                        <Heading.h5>Information</Heading.h5>
                    </Box>
                    <Box width={1} backgroundColor="lightGray">
                        <Stream ref={el => this.stderrEl = el}><code>{this.state.stderr}</code></Stream>
                    </Box>
                </Flex>
            </Box>
        );
    }

    private renderFilePanel() {
        if (this.state.outputFolder === "" || this.state.appState !== AppState.SUCCESS) return null;

        return (
            <Panel>
                <Heading.h4>Output Files</Heading.h4>
                <EmbeddedFileTable path={this.state.outputFolder}/>
            </Panel>
        );
    }

    private renderWebLink() {
        const {appType} = this.state;
        if (this.state.appState === AppState.RUNNING && this.state.webLink)
            if (appType === "WEB")
                return (<ExternalLink href={this.state.webLink}>
                    <Button color="green">Go to web interface</Button>
                </ExternalLink>);
            else if (appType === "VNC")
                return <Link to={this.state.webLink}><Button color="green">Go to web interface</Button></Link>;
        return null;
    }

    private cancelJob() {
        cancelJobDialog({
            jobId: this.jobId,
            onConfirm: async () => {
                try {
                    await cancelJob(Cloud, this.jobId)
                } catch (e) {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred cancelling the job"));
                }
            }
        });
    }

    private renderCancelButton() {
        if (!inCancelableState(this.state.appState) || !this.state.app) return null;
        return <Button ml="8px" color="red" onClick={() => this.cancelJob()}>Cancel job</Button>
    }

    public render() {
        return <MainContainer
            main={this.state.app ?
                <ContainerForText>
                    {this.renderProgressPanel()}
                    {this.renderInfoPanel()}
                    {this.renderFilePanel()}
                    <Spacer left={this.renderWebLink()} right={this.renderCancelButton()}/>
                    {this.renderStreamPanel()}
                </ContainerForText> : <LoadingIcon size={24}/>}
        />
    }
}

const stateToOrder = (state: AppState): 0 | 1 | 2 | 3 | 4 | 5 => {
    switch (state) {
        case AppState.VALIDATED:
            return 0;
        case AppState.PREPARED:
            return 1;
        case AppState.SCHEDULED:
            return 2;
        case AppState.RUNNING:
            return 3;
        case AppState.TRANSFER_SUCCESS:
            return 4;
        case AppState.SUCCESS:
            return 5;
        case AppState.FAILURE:
            return 5;
        default:
            return 0;
    }
};

const isStateComplete = (state: AppState, currentState: AppState) =>
    stateToOrder(state) < stateToOrder(currentState);

const stateToTitle = (state: AppState): string => {
    switch (state) {
        case AppState.FAILURE:
            return "Failure";
        case AppState.PREPARED:
            return "Pending";
        case AppState.RUNNING:
            return "Running";
        case AppState.SCHEDULED:
            return "Scheduled";
        case AppState.SUCCESS:
            return "Success";
        case AppState.TRANSFER_SUCCESS:
            return "Transferring";
        case AppState.VALIDATED:
            return "Validated";
        default:
            return "Unknown";
    }
};

const StepTrackerItem: React.FunctionComponent<{ stateToDisplay: AppState, currentState: AppState }> = ({
                                                                                                            stateToDisplay, currentState
                                                                                                        }) => {
    const active = stateToDisplay === currentState;
    const complete = isStateComplete(stateToDisplay, currentState);
    const failed = currentState === AppState.FAILURE;
    return (
        <Step active={active}>
            {complete ?
                <Icon name={failed ? "close" : "check"} color={failed ? "red" : "green"} mr="0.7em" size="30px"/> :
                <JobStateIcon state={stateToDisplay} mr="0.7em" size="30px"/>
            }
            <TextSpan fontSize={3}>{stateToTitle(stateToDisplay)}</TextSpan>
        </Step>
    );
};

const Stream = styled.pre`
    height: 500px;
    overflow: auto;
`;

Stream.displayName = "Stream";

const mapDispatchToProps = (dispatch: Dispatch): DetailedResultOperations => ({
    setLoading: loading => dispatch(setLoading(loading)),
    setPageTitle: jobId => dispatch(updatePageTitle(`Results for Job: ${jobId}`)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
});

export default connect(null, mapDispatchToProps)(DetailedResult);
