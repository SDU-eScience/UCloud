import * as React from "react";
import { DefaultLoading } from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { shortUUID, failureNotification } from "UtilityFunctions";
import { Link } from "react-router-dom";
import { FilesTable } from "Files/FilesTable";
import { List as PaginationList, EntriesPerPageSelector } from "Pagination";
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { ReduxObject, DetailedResultReduxObject } from "DefaultObjects";
import { DetailedResultProps, DetailedResultState, StdElement, DetailedResultOperations, AppState } from ".";
import { File, SortBy, SortOrder } from "Files";
import { AllFileOperations, fileTablePage } from "Utilities/FileUtilities";
import { favoriteFileFromPage } from "Utilities/FileUtilities";
import { hpcJobQuery } from "Utilities/ApplicationUtilities";
import { History } from "history";
import { Dispatch } from "redux";
import { detailedResultError, fetchPage, setLoading, receivePage } from "Applications/Redux/DetailedResultActions";
import { RefreshButton } from "UtilityComponents";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Flex, Box, List, Card } from "ui-components";
import { Step, StepGroup } from "ui-components/Step";
import styled from "styled-components";
import { TextSpan } from "ui-components/Text";
import Icon, { IconName } from "ui-components/Icon";

class DetailedResult extends React.Component<DetailedResultProps, DetailedResultState> {
    private stdoutEl: StdElement;
    private stderrEl: StdElement;

    constructor(props: any) {
        super(props);
        this.state = {
            complete: false,
            appState: AppState.VALIDATED,
            status: "",
            app: { name: "", version: "" },
            stdout: "",
            stderr: "",
            stdoutLine: 0,
            stderrLine: 0,
            stdoutOldTop: -1,
            stderrOldTop: -1,
            reloadIntervalId: -1,
            promises: new PromiseKeeper()
        };
        this.props.setPageTitle(shortUUID(this.jobId));
    }

    get jobId(): string {
        return this.props.match.params.jobId;
    }

    componentDidMount() {
        let reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
        this.setState(() => ({ reloadIntervalId: reloadIntervalId }));
    }

    componentWillUnmount() {
        if (this.state.reloadIntervalId) window.clearTimeout(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
    }

    static fileOperations = (history: History) => AllFileOperations(true, false, false, history);

    scrollIfNeeded() {
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

    retrieveStdStreams() {
        if (this.state.complete) {
            this.retrieveStateWhenCompleted();
            return;
        }
        this.props.setLoading(true);
        this.state.promises.makeCancelable(
            Cloud.get(hpcJobQuery(this.jobId, this.state.stdoutLine, this.state.stderrLine))
        ).promise.then(
            ({ response }) => {
                this.setState(() => ({
                    stdout: this.state.stdout.concat(response.stdout),
                    stderr: this.state.stderr.concat(response.stderr),
                    stdoutLine: response.stdoutNextLine,
                    stderrLine: response.stderrNextLine,

                    app: response.application,
                    status: response.status,
                    appState: response.state,
                    complete: response.complete
                }));

                this.scrollIfNeeded();
                if (response.complete) this.retrieveStateWhenCompleted();
                else {
                    let reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
                    this.setState(() => ({ reloadIntervalId: reloadIntervalId }));
                }
            },

            failure => {
                if (!failure.isCanceled)
                    failureNotification("An error occurred retrieving Information and Output from the job.");
            }).then(
                () => this.props.setLoading(false),
                () => this.props.setLoading(false)
            );// FIXME, should be .finally(() => this.props.setLoading(false));, blocked by ts-jest
    }

    retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.props.page.items.length || this.props.loading) return;
        this.props.setLoading(true);
        this.retrieveFilesPage(this.props.page.pageNumber, this.props.page.itemsPerPage)
    }

    retrieveFilesPage(pageNumber: number, itemsPerPage: number) {
        this.props.fetchPage(this.jobId, pageNumber, itemsPerPage);
        window.clearTimeout(this.state.reloadIntervalId);
    }

    favoriteFile = (file: File) => this.props.receivePage(favoriteFileFromPage(this.props.page, [file], Cloud));

    renderProgressPanel = () => (
        <div className="job-result-box">
            <h4>Progress</h4>
            <StepGroup>
                <StepTrackerItem
                    icon="checkDouble"
                    active={this.isStateActive(AppState.VALIDATED)}
                    complete={this.isStateComplete(AppState.VALIDATED)}
                    title={"Validated"} />
                <StepTrackerItem
                    icon="hourglass"
                    active={this.isStateActive(AppState.PREPARED)}
                    complete={this.isStateComplete(AppState.PREPARED)}
                    title={"Pending"} />
                <StepTrackerItem
                    icon="calendar"
                    active={this.isStateActive(AppState.SCHEDULED)}
                    complete={this.isStateComplete(AppState.SCHEDULED)}
                    title={"Scheduled"} />
                <StepTrackerItem
                    icon="chrono"
                    active={this.isStateActive(AppState.RUNNING)}
                    complete={this.isStateComplete(AppState.RUNNING)}
                    title={"Running"} />
                <StepTrackerItem
                    icon="move"
                    active={this.isStateActive(AppState.TRANSFER_SUCCESS)}
                    complete={this.isStateComplete(AppState.TRANSFER_SUCCESS)}
                    title={"Transferring"} />
            </StepGroup>
        </div>
    );

    renderInfoPanel() {
        let entries = [
            { key: "Application Name", value: this.state.app.name },
            { key: "Application Version", value: this.state.app.version },
            { key: "Status", value: this.state.status },
        ];

        let domEntries = entries.map((it, idx) => <Box pt="0.8em" pb="0.8em" key={idx}><b>{it.key}</b>: {it.value}</Box>);

        switch (this.state.appState) {
            case AppState.SUCCESS:
                domEntries.push(
                    <Box pt="0.8em" pb="0.8em">
                        Application has completed successfully. Click
                            <Link to={fileTablePage(`/home/${Cloud.username}/Jobs/${this.jobId}`)}> here </Link>
                        to go to the output.
                    </Box>
                );
                break;
            case AppState.SCHEDULED:
                domEntries.push(
                    <Box pt="0.8em" pb="0.8em">
                        Your application is currently in the Slurm queue on ABC2 <DefaultLoading loading />
                    </Box>
                );
                break;
            case AppState.PREPARED:
                domEntries.push(
                    <Box pt="0.8em" pb="0.8em">
                        We are currently transferring your job from SDUCloud to ABC2 <DefaultLoading loading />
                    </Box>
                );
                break;
            case AppState.RUNNING:
                domEntries.push(
                    <Box pt="0.8em" pb="0.8em">
                        Your job is currently being executed on ABC2 <DefaultLoading loading />
                    </Box>
                );
                break;
        }

        return (
            <Box mb="0.5em">
                <h4>Job Information</h4>
                <Card height="auto" p="14px 14px 14px 14px">
                    <List>
                        {domEntries}
                    </List>
                </Card>
            </Box>
        );
    }

    renderStreamPanel() {
        if (this.state.complete && this.state.stdout === "" && this.state.stderr === "") return null;
        return (
            <Box width="100%">
                <h4>
                    Standard Streams
                    &nbsp;
                    <Dropdown>
                        <Icon name="information" />
                        <DropdownContent colorOnHover={false} color="white" backgroundColor="black">
                            <span>Streams are collected from <code>stdout</code> and <code>stderr</code> of your application.</span>
                        </DropdownContent>
                    </Dropdown>
                </h4>
                <Flex flexDirection="row">
                    <Box width={1 / 2}>
                        <h4>Output</h4>
                        <Stream ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></Stream>
                    </Box>
                    <Box width={1 / 2}>
                        <h4>Information</h4>
                        <Stream ref={el => this.stderrEl = el}><code>{this.state.stderr}</code></Stream>
                    </Box>
                </Flex>
            </Box>
        );
    }

    renderFilePanel() {
        const { page } = this.props;
        if (!page.items.length) return null;
        return (
            <div>
                <h4>Output Files</h4>
                <PaginationList
                    loading={this.props.loading}
                    page={page}
                    pageRenderer={page =>
                        <FilesTable
                            sortOrder={SortOrder.ASCENDING}
                            sortBy={SortBy.PATH}
                            fileOperations={DetailedResult.fileOperations(this.props.history)}
                            files={page.items}
                            refetchFiles={() => null}
                            sortFiles={() => null}
                            onCheckFile={() => null}
                            sortingColumns={[SortBy.MODIFIED_AT, SortBy.ACL]}
                            onFavoriteFile={(files: File[]) => this.favoriteFile(files[0])}
                            customEntriesPerPage={
                                <>
                                    <RefreshButton
                                        className="float-right"
                                        loading={false}
                                        onClick={() => this.retrieveFilesPage(page.pageNumber, page.itemsPerPage)}
                                    />
                                    <EntriesPerPageSelector
                                        entriesPerPage={page.itemsPerPage}
                                        content="Files per page"
                                        onChange={itemsPerPage => this.retrieveFilesPage(page.pageNumber, itemsPerPage)}
                                    />
                                </>
                            }
                        />}
                    customEntriesPerPage
                    onPageChanged={pageNumber => this.retrieveFilesPage(pageNumber, page.itemsPerPage)}
                    onItemsPerPageChanged={itemsPerPage => this.retrieveFilesPage(0, itemsPerPage)}
                />
            </div>
        );
    }

    render = () => (
        <Flex alignItems="center" flexDirection="column">
            <Box width={0.7}>
                <Box>{this.renderProgressPanel()}</Box>
                <Box>{this.renderInfoPanel()}</Box>
                <Box>{this.renderFilePanel()}</Box>
                <Box>{this.renderStreamPanel()}</Box>
            </Box>
        </Flex>
    );

    static stateToOrder(state: AppState): 0 | 1 | 2 | 3 | 4 | 5 {
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
    }

    isStateComplete = (queryState: AppState) =>
        DetailedResult.stateToOrder(queryState) < DetailedResult.stateToOrder(this.state.appState);


    isStateActive = (queryState: AppState) =>
        DetailedResult.stateToOrder(this.state.appState) === DetailedResult.stateToOrder(queryState);
}

const StepTrackerItem = (props: { complete: boolean, active: boolean, title: string, icon: IconName }) => (
    <Step active={props.active}>
        {props.complete ?
            <Icon name="check" color="green" mr="0.7em" size="30px" /> :
            <Icon name={props.icon} mr="0.7em" size="30px" color="iconColor" color2="iconColor2" />
        }
        <TextSpan fontSize={3}>{props.title}</TextSpan>
    </Step>
);


const Stream = styled.pre`
    height: 500px;
    overflow: auto;
`;


const mapStateToProps = ({ detailedResult }: ReduxObject): DetailedResultReduxObject => detailedResult;
const mapDispatchToProps = (dispatch: Dispatch): DetailedResultOperations => ({
    detailedResultError: error => dispatch(detailedResultError(error)),
    setLoading: loading => dispatch(setLoading(loading)),
    setPageTitle: jobId => dispatch(updatePageTitle(`Results for Job: ${jobId}`)),
    receivePage: page => dispatch(receivePage(page)),
    fetchPage: async (jobId, pageNumber, itemsPerPage) =>
        dispatch(await fetchPage(Cloud.username, jobId, pageNumber, itemsPerPage))
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedResult);