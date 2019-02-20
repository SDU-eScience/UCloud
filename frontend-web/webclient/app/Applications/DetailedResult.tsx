import * as React from "react";
import LoadingIcon from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { shortUUID, failureNotification, errorMessageOrDefault } from "UtilityFunctions";
import { Link } from "react-router-dom";
import { FilesTable } from "Files/FilesTable";
import { List as PaginationList } from "Pagination";
import { connect } from "react-redux";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { ReduxObject, DetailedResultReduxObject, emptyPage } from "DefaultObjects";
import { DetailedResultProps, DetailedResultState, StdElement, DetailedResultOperations, AppState } from ".";
import { File, SortBy, SortOrder } from "Files";
import { AllFileOperations, fileTablePage, filepathQuery, favoritesQuery, resolvePath } from "Utilities/FileUtilities";
import { favoriteFileFromPage } from "Utilities/FileUtilities";
import { hpcJobQuery } from "Utilities/ApplicationUtilities";
import { Dispatch } from "redux";
import { detailedResultError, fetchPage, setLoading, receivePage } from "Applications/Redux/DetailedResultActions";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Flex, Box, List, Card } from "ui-components";
import { Step, StepGroup } from "ui-components/Step";
import styled from "styled-components";
import { TextSpan } from "ui-components/Text";
import Icon, { IconName } from "ui-components/Icon";
import { setRefreshFunction } from "Navigation/Redux/HeaderActions";
import { FileSelectorModal } from "Files/FileSelector";
import { Page } from "Types";
import * as Heading from "ui-components/Heading";

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
            promises: new PromiseKeeper(),
            fsShown: false,
            fsLoading: false,
            fsPage: emptyPage,
            fsIsFavorite: false,
            fsPath: Cloud.homeFolder,
            fsError: undefined,
            fsDisallowedPaths: [],
            fsCallback: () => undefined
        };
        this.props.setPageTitle(shortUUID(this.jobId));
    }

    get jobId(): string {
        return this.props.match.params.jobId;
    }

    componentDidMount() {
        let reloadIntervalId = window.setTimeout(() => this.retrieveStdStreams(), 1_000);
        const { state } = this;
        this.fetchSelectorFiles(state.fsPath, state.fsPage.pageNumber, state.fsPage.itemsInTotal);
        this.setState(() => ({ reloadIntervalId: reloadIntervalId }));
    }

    componentWillUnmount() {
        if (this.state.reloadIntervalId) window.clearTimeout(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
        this.props.receivePage(emptyPage);
    }

    fileOperations = () => AllFileOperations({
        stateless: true,
        history: this.props.history,
        fileSelectorOps: {
            fetchFilesPage: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
            fetchPageFromPath: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
            setDisallowedPaths: (paths) => this.setState(() => ({ fsDisallowedPaths: paths })),
            showFileSelector: fsShown => this.setState(() => ({ fsShown })),
            setFileSelectorCallback: callback => this.setState(() => ({ fsCallback: callback }))
        },
        onDeleted: () => this.props.fetchPage(this.jobId, 0, this.props.page.itemsPerPage),
        setLoading: () => this.props.setLoading(true)
    });

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

    private retrieveStdStreams() {
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
            }).finally(() => this.props.setLoading(false));
    }

    private retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.props.page.items.length) return;
        this.props.setLoading(true);
        this.retrieveFilesPage(this.props.page.pageNumber, this.props.page.itemsPerPage)
    }

    private retrieveFilesPage(pageNumber: number, itemsPerPage: number) {
        this.props.fetchPage(this.jobId, pageNumber, itemsPerPage);
        window.clearTimeout(this.state.reloadIntervalId);
    }

    private async fetchFavorites(pageNumber: number, itemsPerPage: number) {
        this.setState(() => ({ fsLoading: true }))
        try {
            const result = await this.state.promises.makeCancelable(Cloud.get(favoritesQuery(pageNumber, itemsPerPage))).promise;
            this.setState(() => ({
                fsIsFavorite: true,
                fsPath: "Favorites",
                fsPage: result.response
            }));
        } catch (e) {
            this.setState(() => ({ fsError: errorMessageOrDefault(e, "An error occurred fetching favorites") }));
        } finally {
            this.setState(() => ({ fsLoading: false }))
        }
    }

    private readonly favoriteFile = async (file: File) => this.props.receivePage(await favoriteFileFromPage(this.props.page, [file], Cloud));

    renderProgressPanel = () => (
        <div>
            <h4>Progress</h4>
            <StepGroup>
                <StepTrackerItem
                    icon="checkDouble"
                    failed={this.isStateFailure()}
                    active={this.isStateActive(AppState.VALIDATED)}
                    complete={this.isStateComplete(AppState.VALIDATED)}
                    title="Validated" />
                <StepTrackerItem
                    icon="hourglass"
                    failed={this.isStateFailure()}
                    active={this.isStateActive(AppState.PREPARED)}
                    complete={this.isStateComplete(AppState.PREPARED)}
                    title="Pending" />
                <StepTrackerItem
                    icon="calendar"
                    failed={this.isStateFailure()}
                    active={this.isStateActive(AppState.SCHEDULED)}
                    complete={this.isStateComplete(AppState.SCHEDULED)}
                    title="Scheduled" />
                <StepTrackerItem
                    icon="chrono"
                    failed={this.isStateFailure()}
                    active={this.isStateActive(AppState.RUNNING)}
                    complete={this.isStateComplete(AppState.RUNNING)}
                    title="Running" />
                <StepTrackerItem
                    icon="move"
                    failed={this.isStateFailure()}
                    active={this.isStateActive(AppState.TRANSFER_SUCCESS)}
                    complete={this.isStateComplete(AppState.TRANSFER_SUCCESS)}
                    title="Transferring" />
            </StepGroup>
        </div>
    );

    renderInfoPanel() {
        let entries = [
            { key: "Application Name", value: this.state.app.name },
            { key: "Application Version", value: this.state.app.version },
            { key: "Status", value: this.state.status },
        ];

        let domEntries = entries.map(it => <Box pt="0.8em" pb="0.8em" key={it.key}><b>{it.key}</b>: {it.value}</Box>);

        switch (this.state.appState) {
            case AppState.SUCCESS:
                domEntries.push(
                    <Box key={AppState.SUCCESS} pt="0.8em" pb="0.8em">
                        Application has completed successfully.
                        Click <Link to={fileTablePage(`/home/${Cloud.username}/Jobs/${this.jobId}`)}>here</Link> to go to the output.
                    </Box>
                );
                break;
            case AppState.SCHEDULED:
                domEntries.push(
                    <Box key={AppState.SCHEDULED} pt="0.8em" pb="0.8em">
                        Your application is currently in the Slurm queue on ABC2 <LoadingIcon size={18} />
                    </Box>
                );
                break;
            case AppState.PREPARED:
                domEntries.push(
                    <Box key={AppState.PREPARED} pt="0.8em" pb="0.8em">
                        We are currently transferring your job from SDUCloud to ABC2 <LoadingIcon size={18} />
                    </Box>
                );
                break;
            case AppState.RUNNING:
                domEntries.push(
                    <Box key={AppState.RUNNING} pt="0.8em" pb="0.8em">
                        Your job is currently being executed on ABC2 <LoadingIcon size={18} />
                    </Box>
                );
                break;
            case AppState.FAILURE:
                domEntries.push(
                    <Box key={AppState.FAILURE} pt="0.8em" pb="0.8em">
                        Job failed
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
                        <Icon name="info" color="white" color2="black" />
                        <DropdownContent visible colorOnHover={false} color="white" backgroundColor="black">
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
        const { state } = this;
        return (
            <Box>
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
                            sortingColumns={[SortBy.MODIFIED_AT, SortBy.ACL]}
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
            </Box>
        );
    }
    fetchSelectorFiles(path: string, pageNumber: number, itemsPerPage: number): void {
        this.state.promises.makeCancelable(Cloud.get<Page<File>>(filepathQuery(path, pageNumber, itemsPerPage))).promise.then(r => {
            this.setState(() => ({ fsPage: r.response, fsPath: resolvePath(path), fsIsFavorite: false }))
        }).catch(it => this.setState(() => ({ fsError: errorMessageOrDefault(it, "An error occurred fetching files") })));
    }

    render = () => (
        <Flex alignItems="center" flexDirection="column">
            <Box width={0.7}>
                <Box>{this.renderProgressPanel()}</Box>
                <Box>{this.renderInfoPanel()}</Box>
                <Box>{this.renderFilePanel()}</Box>
                <Box>{this.renderStreamPanel()}</Box>
            </Box>
        </Flex>);

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

    isStateFailure = () => this.state.appState === AppState.FAILURE

    isStateActive = (queryState: AppState) =>
        DetailedResult.stateToOrder(this.state.appState) === DetailedResult.stateToOrder(queryState);
}

const StepTrackerItem = (props: { failed: boolean, complete: boolean, active: boolean, title: string, icon: IconName }) => (
    <Step active={props.active}>
        {props.complete ?
            <Icon name={props.failed ? "close" : "check"} color={props.failed ? "red" : "green"} mr="0.7em" size="30px" /> :
            <Icon name={props.icon} mr="0.7em" size="30px" color="iconColor" color2="iconColor2" />
        }
        <TextSpan fontSize={3}>{props.title}</TextSpan>
    </Step>
);


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
    fetchPage: async (jobId, pageNumber, itemsPerPage) => {
        dispatch(setLoading(true));
        dispatch(await fetchPage(Cloud.username || "", jobId, pageNumber, itemsPerPage));
    },
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect(mapStateToProps, mapDispatchToProps)(DetailedResult);