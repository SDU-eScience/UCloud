import * as React from "react";
import { Spinner } from "LoadingIcon/LoadingIcon"
import PromiseKeeper from "PromiseKeeper";
import { Cloud } from "Authentication/SDUCloudObject";
import { shortUUID, failureNotification } from "UtilityFunctions";
import { Container, List, Card, Icon, Popup, Step, SemanticICONS, Grid } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { FilesTable } from "Files/Files";
import { List as PaginationList } from "Pagination";
import "./wizard.scss";
import "./Applications.scss";
import { connect } from "react-redux";
import "Styling/Shared.scss";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { emptyPage } from "DefaultObjects";
import { DetailedResultProps, DetailedResultState, StdElement } from ".";
import { File, SortBy } from "Files";
import { filepathQuery, favoriteFileFromPage } from "Utilities/FileUtilities";
import { hpcJobQuery } from "Utilities/ApplicationUtilities";

class DetailedResult extends React.Component<DetailedResultProps, DetailedResultState> {
    private stdoutEl: StdElement;
    private stderrEl: StdElement;

    constructor(props: DetailedResultProps) {
        super(props);
        this.state = {
            loading: false,
            complete: false,
            appState: "VALIDATED",
            status: "",
            app: { name: "", version: "" },
            stdout: "",
            stderr: "",
            stdoutLine: 0,
            stderrLine: 0,
            stdoutOldTop: -1,
            stderrOldTop: -1,
            reloadIntervalId: -1,
            page: emptyPage,
            promises: new PromiseKeeper()
        };
        this.props.dispatch(updatePageTitle(`Results for Job: ${shortUUID(this.jobId)}`));
    }

    get jobId(): string {
        return this.props.match.params.jobId;
    }

    componentDidMount() {
        this.retrieveStdStreams();
        let reloadIntervalId = window.setInterval(() => this.retrieveStdStreams(), 1_000);
        this.setState({ reloadIntervalId: reloadIntervalId });
    }

    componentWillUnmount() {
        if (this.state.reloadIntervalId) window.clearInterval(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
    }

    scrollIfNeeded() {
        if (!this.stdoutEl || !this.stderrEl) return;

        if (this.stdoutEl.scrollTop === this.state.stdoutOldTop || this.state.stderrOldTop === -1) {
            this.stdoutEl.scrollTop = this.stdoutEl.scrollHeight;
        }

        if (this.stderrEl.scrollTop === this.state.stderrOldTop || this.state.stderrOldTop === -1) {
            this.stderrEl.scrollTop = this.stderrEl.scrollHeight;
        }

        this.setState({
            stdoutOldTop: this.stdoutEl.scrollTop,
            stderrOldTop: this.stderrEl.scrollTop,
        });
    }

    retrieveStdStreams() {
        if (this.state.complete) {
            this.retrieveStateWhenCompleted();
            return;
        }
        this.setState({ loading: true });
        this.state.promises.makeCancelable(
            Cloud.get(hpcJobQuery(this.jobId, this.state.stdoutLine, this.state.stderrLine))
        ).promise.then(
            ({ response }) => {
                this.setState({
                    stdout: this.state.stdout.concat(response.stdout),
                    stderr: this.state.stderr.concat(response.stderr),
                    stdoutLine: response.stdoutNextLine,
                    stderrLine: response.stderrNextLine,

                    app: response.application,
                    status: response.status,
                    appState: response.state,
                    complete: response.complete
                });

                this.scrollIfNeeded();
                if (response.complete) this.retrieveStateWhenCompleted();
            },

            failure => {
                failureNotification("An error occurred retrieving StdOut and StdErr from the job.");
                console.log(failure);
            }).finally(() => this.setState({ loading: false }));
    }

    retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.state.page.items.length || this.state.loading) return;
        this.setState({ loading: true });
        this.retrieveFilesPage(this.state.page.pageNumber, this.state.page.itemsPerPage)
    }

    retrieveFilesPage(pageNumber: number, itemsPerPage: number) {
        this.state.promises.makeCancelable(
            Cloud.get(filepathQuery(`/home/${Cloud.username}/Jobs/${this.jobId}`, pageNumber, itemsPerPage))
        ).promise.then(({ response }) => {
            this.setState({
                page: response,
                loading: false
            });
            window.clearInterval(this.state.reloadIntervalId);
        });
    }

    favoriteFile = (file: File) => this.setState(() => ({ page: favoriteFileFromPage(this.state.page, [file], Cloud) }));
    
    renderProgressPanel = () => (
        <div className="job-result-box">
            <h4>Progress</h4>
            <ProgressTracker>
                <ProgressTrackerItem
                    icon="check"
                    active={this.isStateActive("VALIDATED")}
                    complete={this.isStateComplete("VALIDATED")}
                    title={"Validated"} />
                <ProgressTrackerItem
                    icon="hourglass half"
                    active={this.isStateActive("PENDING")}
                    complete={this.isStateComplete("PENDING")}
                    title={"Pending"} />
                <ProgressTrackerItem
                    icon="calendar"
                    active={this.isStateActive("SCHEDULED")}
                    complete={this.isStateComplete("SCHEDULED")}
                    title={"Scheduled"} />
                <ProgressTrackerItem
                    icon="stopwatch"
                    active={this.isStateActive("RUNNING")}
                    complete={this.isStateComplete("RUNNING")}
                    title={"Running"} />
            </ProgressTracker>
        </div>
    );

    renderInfoPanel() {
        let entries = [
            { key: "Application Name", value: this.state.app.name },
            { key: "Application Version", value: this.state.app.version },
            { key: "Status", value: this.state.status },
        ];

        let domEntries = entries.map((it, idx) => <List.Item className="itemPadding" key={idx}><b>{it.key}</b>: {it.value}</List.Item>);

        switch (this.state.appState) {
            case "SUCCESS":
                domEntries.push(
                    <List.Item key="app-info itemPadding" className="itemPadding">
                        <List.Content>
                            Application has completed successfully. Click <Link
                                to={`/files//home/${Cloud.username}/Jobs/${this.jobId}`}> here</Link> to go to the output.
                            </List.Content>
                    </List.Item >
                );
                break;
            case "SCHEDULED":
                domEntries.push(
                    <List.Item key="app-info" className="itemPadding">
                        <List.Content>
                            Your application is currently in the Slurm queue on ABC2 <Spinner loading color="primary" />
                        </List.Content>
                    </List.Item>
                );
                break;
            case "PENDING":
                domEntries.push(
                    <List.Item key="app-info" className="itemPadding">
                        <List.Content>
                            We are currently transferring your job from SDUCloud to ABC2 <Spinner loading color="primary" />
                        </List.Content>
                    </List.Item>
                );
                break;
            case "RUNNING":
                domEntries.push(
                    <List.Item key="app-info" className="itemPadding">
                        <List.Content>
                            Your job is currently being executed on ABC2 <Spinner loading color="primary" />
                        </List.Content>
                    </List.Item>
                );
                break;
        }

        return (
            <div className="job-result-box">
                <h4>Job Information</h4>
                <Card fluid size="large">
                    <Card.Content>
                        <List divided>
                            {domEntries}
                        </List>
                    </Card.Content>
                </Card>
            </div >
        );
    }

    renderStreamPanel() {
        if (this.state.complete && this.state.stdout === "" && this.state.stderr === "") return null;
        return (
            <div className="job-result-box">
                <h4>
                    Standard Streams
                    &nbsp;
                    <Popup
                        inverted
                        trigger={<Icon name="info circle" />}
                        content={<span>Streams are collected from <code>stdout</code> and <code>stderr</code> of your application.</span>}
                        on="hover"
                    />
                </h4>
                <div className="card">
                    <div className={"card-body"}>
                        <Grid columns={2}>
                            <Grid.Column>
                                <h4>Output</h4>
                                <pre className="stream"
                                    ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></pre>
                            </Grid.Column>
                            <Grid.Column>
                                <h4>Information</h4>
                                <pre className="stream"
                                    ref={el => this.stderrEl = el}><code>{this.state.stderr}</code></pre>
                            </Grid.Column>
                        </Grid>
                    </div>
                </div>
            </div>
        );
    }

    renderFilePanel() {
        const { page } = this.state;
        if (!page.items.length) return null;
        return (
            <div>
                <h4>Output Files</h4>
                <PaginationList
                    page={page}
                    onRefresh={() => this.retrieveFilesPage(page.itemsPerPage, page.itemsPerPage)}
                    pageRenderer={(page) =>
                        <FilesTable
                            sortBy={SortBy.PATH}
                            fileOperations={[]}
                            files={page.items}
                            refetchFiles={() => null}
                            sortFiles={() => null}
                            sortingIcon={() => null}
                            onCheckFile={() => null}
                            sortingColumns={[SortBy.MODIFIED_AT, SortBy.ACL]}
                            onFavoriteFile={(files: File[]) => this.favoriteFile(files[0])}
                        />}
                    onPageChanged={pageNumber => this.retrieveFilesPage(pageNumber, page.itemsPerPage)}
                    onItemsPerPageChanged={itemsPerPage => this.retrieveFilesPage(0, itemsPerPage)}
                />
            </div>
        );
    }

    render() {
        return (
            <Container className="container-margin">
                <div className="row">{this.renderProgressPanel()}</div>
                <div className="row">{this.renderInfoPanel()}</div>
                <div className="row">{this.renderFilePanel()}</div>
                <div className="row">{this.renderStreamPanel()}</div>
            </Container>
        );
    }

    static stateToOrder(state) {
        switch (state) {
            case "VALIDATED":
                return 0;
            case "PENDING":
                return 1;
            case "SCHEDULED":
                return 2;
            case "RUNNING":
                return 3;
            case "SUCCESS":
                return 4;
            case "FAILURE":
                return 4;
            default:
                return 0;
        }
    }

    isStateComplete(queryState) {
        return DetailedResult.stateToOrder(queryState) < DetailedResult.stateToOrder(this.state.appState);
    }

    isStateActive(queryState) {
        return DetailedResult.stateToOrder(this.state.appState) === DetailedResult.stateToOrder(queryState);
    }
}

const ProgressTracker = ({ children }) => (
    <Step.Group size="tiny" fluid>{children}</Step.Group>
);

const ProgressTrackerItem = (props: { complete: boolean, active: boolean, title: string, icon: SemanticICONS }) => (
    <Step
        completed={props.complete}
        active={props.active}
    >
        <Icon name={props.icon} />
        <Step.Content>
            <Step.Title>{props.title}</Step.Title>
        </Step.Content>
    </Step>
);

export default connect()(DetailedResult);