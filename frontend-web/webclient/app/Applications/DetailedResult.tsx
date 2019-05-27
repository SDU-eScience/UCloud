import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { shortUUID, errorMessageOrDefault } from "UtilityFunctions";
import { Link } from "react-router-dom";
import FilesTable from "Files/FilesTable";
import { List as PaginationList } from "Pagination";
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { ReduxObject, DetailedResultReduxObject, emptyPage } from "DefaultObjects";
import { DetailedResultProps, DetailedResultState, StdElement, DetailedResultOperations, AppState, WithAppInvocation } from ".";
import { File, SortBy, SortOrder } from "Files";
import { allFileOperations, fileTablePage, filepathQuery, favoritesQuery, resolvePath } from "Utilities/FileUtilities";
import { favoriteFileFromPage } from "Utilities/FileUtilities";
import { hpcJobQuery, cancelJobQuery, cancelJobDialog } from "Utilities/ApplicationUtilities";
import { Dispatch } from "redux";
import { detailedResultError, fetchPage, setLoading, receivePage } from "Applications/Redux/DetailedResultActions";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Flex, Box, List, Card, ContainerForText, ExternalLink, Button } from "ui-components";
import { Step, StepGroup } from "ui-components/Step";
import styled from "styled-components";
import { TextSpan } from "ui-components/Text";
import Icon from "ui-components/Icon";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { FileSelectorModal } from "Files/FileSelector";
import { Page } from "Types";
import * as Heading from "ui-components/Heading";
import { JobStateIcon } from "./JobStateIcon";
import { MainContainer } from "MainContainer/MainContainer";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";
import { SnackType } from "Snackbar/Snackbars";
import { dialogStore } from "Dialog/DialogStore";
import { addNotificationEntry } from "Utilities/ReduxUtilities";

const Panel = styled(Box)`
    margin-bottom: 1em;
`;

class DetailedResult extends React.Component<DetailedResultProps, DetailedResultState> {
    private stdoutEl: StdElement;
    private stderrEl: StdElement;

    constructor(props: Readonly<DetailedResultProps>) {
        super(props);
        this.state = {
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
            fsShown: false,
            fsLoading: false,
            fsPage: emptyPage,
            fsIsFavorite: false,
            fsPath: Cloud.homeFolder,
            fsError: undefined,
            fsDisallowedPaths: [],
            fsCallback: () => undefined,
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
            const { name, version } = this.state.app;

            const { response } = await this.state.promises.makeCancelable(
                Cloud.get<WithAppInvocation>(`/hpc/apps/${encodeURI(name)}/${encodeURI(version)}`)
            ).promise;
            this.setState(() => ({ appType: response.invocation.applicationType }));
        }
    }

    public componentDidMount() {
        const reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
        const { state } = this;
        this.fetchSelectorFiles(state.fsPath, state.fsPage.pageNumber, state.fsPage.itemsInTotal);
        this.setState(() => ({ reloadIntervalId }));
    }

    public componentWillUnmount() {
        if (this.state.reloadIntervalId) window.clearTimeout(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
        this.props.receivePage(emptyPage);
    }

    fileOperations = () => allFileOperations({
        stateless: true,
        history: this.props.history,
        fileSelectorOperations: {
            fetchFilesPage: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
            fetchPageFromPath: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
            setDisallowedPaths: (paths) => this.setState(() => ({ fsDisallowedPaths: paths })),
            showFileSelector: fsShown => this.setState(() => ({ fsShown })),
            setFileSelectorCallback: callback => this.setState(() => ({ fsCallback: callback }))
        },
        onDeleted: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
        onSensitivityChange: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
        setLoading: () => this.props.setLoading(true),
        addSnack: snack => this.props.addSnack(snack)
    });

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
                this.props.history.push(`/novnc?jobId=${this.jobId}`);
                return;

            } else if (this.state.appType === "WEB" && !this.state.webLink) {
                this.props.setLoading(false);
                /* FIXME: Wrap in PromiseKeeper */
                const { response } = await Cloud.get(`/hpc/jobs/query-web/${this.jobId}`);
                this.setState(() => ({ webLink: response.path }));
            }
        }
        try {
            this.props.setLoading(true);
            const { response } = await this.state.promises.makeCancelable(
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
                this.setState(() => ({ reloadIntervalId }));
            }
        } catch (e) {
            if (!e.isCanceled)
                this.props.addSnack({
                    message: "An error occurred retrieving Information and Output from the job.",
                    type: SnackType.Failure
                });
        } finally {
            this.props.setLoading(false);
        }
    }

    private retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.props.page.items.length) return;
        this.props.setLoading(true);
        this.retrieveFilesPage(this.props.page.pageNumber, this.props.page.itemsPerPage)
    }

    private retrieveFilesPage(pageNumber: number, itemsPerPage: number) {
        this.props.fetchPage(this.state.outputFolder!!, pageNumber, itemsPerPage);
        window.clearTimeout(this.state.reloadIntervalId);
    }

    private async fetchFavorites(pageNumber: number, itemsPerPage: number) {
        this.setState(() => ({ fsLoading: true }))
        try {
            const { response } = await this.state.promises.makeCancelable(Cloud.get(favoritesQuery(pageNumber, itemsPerPage))).promise;
            this.setState(() => ({
                fsIsFavorite: true,
                fsPath: "Favorites",
                fsPage: response
            }));
        } catch (e) {
            this.setState(() => ({ fsError: errorMessageOrDefault(e, "An error occurred fetching favorites") }));
        } finally {
            this.setState(() => ({ fsLoading: false }))
        }
    }

    private readonly favoriteFile = async (file: File) => this.props.receivePage(await favoriteFileFromPage(this.props.page, [file], Cloud));

    private renderProgressPanel = () => (
        <Panel>
            <StepGroup>
                <StepTrackerItem
                    stateToDisplay={AppState.VALIDATED}
                    currentState={this.state.appState} />
                <StepTrackerItem
                    stateToDisplay={AppState.PREPARED}
                    currentState={this.state.appState} />
                <StepTrackerItem
                    stateToDisplay={AppState.SCHEDULED}
                    currentState={this.state.appState} />
                <StepTrackerItem
                    stateToDisplay={AppState.RUNNING}
                    currentState={this.state.appState} />
                <StepTrackerItem
                    stateToDisplay={AppState.TRANSFER_SUCCESS}
                    currentState={this.state.appState} />
            </StepGroup>
        </Panel>
    );

    private renderInfoPanel() {
        const { app } = this.state;
        if (app === undefined) return null;

        let entries = [
            { key: "Application", value: `${app.title} v${app.version}` },
            { key: "Status", value: this.state.status },
        ];

        let domEntries = entries.map(it => <Box pt="0.8em" pb="0.8em" key={it.key}><b>{it.key}</b>: {it.value}</Box>);

        switch (this.state.appState) {
            case AppState.SUCCESS:
                domEntries.push(
                    <Box key={AppState.SUCCESS} pt="0.8em" pb="0.8em">
                        Application has completed successfully.
                        Click <Link to={fileTablePage(this.state.outputFolder!!)}>here</Link> to go to the output.
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
            <Panel width="100%">
                <Heading.h4>
                    Standard Streams
                    &nbsp;
                    <Dropdown>
                        <Icon name="info" color="white" color2="black" />
                        <DropdownContent width="200px" visible colorOnHover={false} color="white" backgroundColor="black">
                            <TextSpan fontSize={1}>Streams are collected from <code>stdout</code> and <code>stderr</code> of your application.</TextSpan>
                        </DropdownContent>
                    </Dropdown>
                </Heading.h4>
                <Flex flexDirection="row">
                    <Box width={1 / 2}>
                        <Heading.h5>Output</Heading.h5>
                        <Stream ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></Stream>
                    </Box>
                    <Box width={1 / 2}>
                        <Heading.h5>Information</Heading.h5>
                        <Stream ref={el => this.stderrEl = el}><code>{this.state.stderr}</code></Stream>
                    </Box>
                </Flex>
            </Panel>
        );
    }

    private renderFilePanel() {
        const { page } = this.props;
        if (!page.items.length) return null;
        const { state } = this;
        return (
            <Panel>
                <Heading.h4>Output Files</Heading.h4>
                <PaginationList
                    loading={this.props.loading}
                    page={page}
                    pageRenderer={page =>
                        <FilesTable
                            notStickyHeader
                            sortOrder={SortOrder.ASCENDING}
                            sortBy={SortBy.PATH}
                            fileOperations={this.fileOperations()}
                            files={page.items}
                            refetchFiles={() => null}
                            sortFiles={() => null}
                            onCheckFile={() => null}
                            sortingColumns={[SortBy.FILE_TYPE, SortBy.SIZE]}
                            onFavoriteFile={(files: File[]) => this.favoriteFile(files[0])}
                        />}
                    onPageChanged={pageNumber => this.retrieveFilesPage(pageNumber, page.itemsPerPage)}
                />
                <FileSelectorModal
                    isFavorites={this.state.fsIsFavorite}
                    fetchFavorites={(pageNumber, itemsPerPage) => this.fetchFavorites(pageNumber, itemsPerPage)}
                    show={state.fsShown}
                    onHide={() => this.setState(() => ({ fsShown: false }))}
                    path={state.fsPath}
                    fetchFiles={(path, pageNumber, itemsPerPage) => this.fetchSelectorFiles(path, pageNumber, itemsPerPage)}
                    loading={state.fsLoading}
                    errorMessage={state.fsError}
                    onErrorDismiss={() => this.setState(() => ({ fsError: undefined }))}
                    onlyAllowFolders
                    canSelectFolders
                    page={state.fsPage}
                    setSelectedFile={state.fsCallback}
                    disallowedPaths={state.fsDisallowedPaths}
                />
            </Panel>
        );
    }

    private renderWebLink() {
        if (this.state.appState !== AppState.RUNNING || this.state.appType !== "WEB" || !this.state.webLink) return null;
        return <ExternalLink href={this.state.webLink}><Button color="green">Go to web interface</Button></ExternalLink>
    }

    private async cancelJob() {
        cancelJobDialog({
            jobId: this.jobId,
            onConfirm: () => {
                try {
                    this.state.promises.makeCancelable(Cloud.delete(cancelJobQuery, { jobId: this.jobId }));
                } catch (e) {
                    this.props.addSnack({
                        type: SnackType.Failure,
                        message: errorMessageOrDefault(e, "An error occurred cancelling the job.")
                    });
                }
            }
        });
    }

    private renderCancelButton() {
        if (!inCancelableState(this.state.appState)) return null;
        return <Button ml="8px" color="red" onClick={() => this.cancelJob()}>Cancel job</Button>
    }

    private async fetchSelectorFiles(path: string, pageNumber: number, itemsPerPage: number): Promise<void> {
        try {
            const r = await this.state.promises.makeCancelable(Cloud.get<Page<File>>(filepathQuery(path, pageNumber, itemsPerPage))).promise;
            this.setState(() => ({ fsPage: r.response, fsPath: resolvePath(path), fsIsFavorite: false }))
        } catch (e) {
            this.setState(() => ({ fsError: errorMessageOrDefault(e, "An error occurred fetching files") }));
        }
    }

    public render() {
        return (
            <MainContainer
                main={
                    <ContainerForText>
                        {this.renderProgressPanel()}
                        {this.renderInfoPanel()}
                        {this.renderFilePanel()}
                        {this.renderWebLink()}
                        {this.renderCancelButton()}
                        {this.renderStreamPanel()}
                    </ContainerForText>
                } />
        )
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
}

const isStateComplete = (state: AppState, currentState: AppState) =>
    stateToOrder(state) < stateToOrder(currentState);

const stateToTitle = (state: AppState): string => {
    switch (state) {
        case AppState.FAILURE: return "Failure";
        case AppState.PREPARED: return "Pending";
        case AppState.RUNNING: return "Running";
        case AppState.SCHEDULED: return "Scheduled";
        case AppState.SUCCESS: return "Success";
        case AppState.TRANSFER_SUCCESS: return "Transferring";
        case AppState.VALIDATED: return "Validated";
        default: return "Unknown";
    }
}

const StepTrackerItem: React.StatelessComponent<{ stateToDisplay: AppState, currentState: AppState }> = ({ stateToDisplay, currentState }) => {
    const active = stateToDisplay === currentState;
    const complete = isStateComplete(stateToDisplay, currentState);
    const failed = currentState === AppState.FAILURE;
    return (
        <Step active={active}>
            {complete ?
                <Icon name={failed ? "close" : "check"} color={failed ? "red" : "green"} mr="0.7em" size="30px" /> :
                <JobStateIcon state={stateToDisplay} mr="0.7em" size="30px" />
            }
            <TextSpan fontSize={3}>{stateToTitle(stateToDisplay)}</TextSpan>
        </Step>
    );
};

function inCancelableState(state: AppState) {
    return state === AppState.VALIDATED || state === AppState.PREPARED || state === AppState.SCHEDULED || state === AppState.RUNNING;
}

const Stream = styled.pre`
    height: 500px;
    overflow: auto;
`;

const mapStateToProps = ({ detailedResult }: ReduxObject): DetailedResultReduxObject & { favoriteCount: number } => ({
    ...detailedResult,
    favoriteCount: detailedResult.page.items.filter(it => it.favorited).length
});

const mapDispatchToProps = (dispatch: Dispatch): DetailedResultOperations => ({
    detailedResultError: error => dispatch(detailedResultError(error)),
    setLoading: loading => dispatch(setLoading(loading)),
    setPageTitle: jobId => dispatch(updatePageTitle(`Results for Job: ${jobId}`)),
    receivePage: page => dispatch(receivePage(page)),
    fetchPage: async (folder, pageNumber, itemsPerPage) => {
        dispatch(setLoading(true));
        dispatch(await fetchPage(folder, pageNumber, itemsPerPage));
    },
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    addSnack: snack => addNotificationEntry(dispatch, snack),
});

export default connect<DetailedResultReduxObject, DetailedResultOperations>(mapStateToProps, mapDispatchToProps)(DetailedResult);