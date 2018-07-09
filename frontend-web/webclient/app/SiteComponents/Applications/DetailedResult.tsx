import * as React from "react";
import { Spinner } from "../LoadingIcon/LoadingIcon"
import PromiseKeeper from "../../PromiseKeeper";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { shortUUID, failureNotification } from "../../UtilityFunctions";
import { Container, List, Card, Icon, Popup } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { FilesTable } from "../Files/Files";
import { List as PaginationList } from "../Pagination/index";
import "./wizard.scss";
import "./Applications.scss";
import { connect } from "react-redux";
import "../Styling/Shared.scss";
import { updatePageTitle } from "../../Actions/Status";
import { emptyPage, Page, File } from "../../types/types";

interface DetailedResultProps {

}

type Any = any

interface DetailedResultState {
    page: Page<File>
    loading: boolean
    complete: boolean
    appState: string
    status: string
    app: {
        name: string
        version: string
    }
    stdout: string
    stderr: string
    stdoutLine: number
    stderrLine: number
    stdoutOldTop: number,
    stderrOldTop: number,
    reloadIntervalId: number
    promises: PromiseKeeper
}

class DetailedResult extends React.Component<any, DetailedResultState> {
    private stdoutEl: any; // FIXME Find more specific type
    private stderrEl: any; // FIXME Find more specific type

    constructor(props) {
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
        let reloadIntervalId = window.setInterval(() => this.retrieveStdStreams(), 1000);
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
            Cloud.get(`hpc/jobs/follow/${this.jobId}?` +
                `stdoutLineStart=${this.state.stdoutLine}&stdoutMaxLines=1000` +
                `&stderrLineStart=${this.state.stderrLine}&stderrMaxLines=1000`
            ).then(
                ({ response }) => {
                    this.setState({
                        stdout: this.state.stdout + response.stdout,
                        stderr: this.state.stderr + response.stderr,
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
                }).then(() => this.setState({ loading: false }))
            // Initially this: .finally(() => this.setState({ loading: false }))
            // However, Finally is TC-39, not part of the standard yet. (https://github.com/tc39/proposal-promise-finally)
        );
    }

    retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.state.page.items.length || this.state.loading) return;
        this.setState({ loading: true });
        this.retrieveFilesPage(this.state.page.pageNumber, this.state.page.itemsPerPage)
    }

    retrieveFilesPage(pageNumber, itemsPerPage) {
        Cloud.get(`files?path=/home/${Cloud.username}/Jobs/${this.jobId}&page=${pageNumber}&itemsPerPage=${itemsPerPage}`).then(({ response }) => {
            this.setState({ page: response, loading: false });
        });
    }

    renderProgressPanel() {
        const isFailure = this.state.appState === "FAILURE";
        const isSuccess = this.state.appState === "SUCCESS";
        const lastStep = isFailure ? "FAILURE" : "SUCCESS";
        const lastStepName = isFailure ? "Failure" : "Success";

        return (
            <div>
                <h4>Progress</h4>
                <ProgressTracker>
                    <ProgressTrackerItem
                        error={isFailure}
                        success={isSuccess}
                        active={this.isStateActive("VALIDATED")}
                        complete={this.isStateComplete("VALIDATED")}
                        title={"Validated"} />
                    <ProgressTrackerItem
                        error={isFailure}
                        success={isSuccess}
                        active={this.isStateActive("PENDING")}
                        complete={this.isStateComplete("PENDING")}
                        title={"Pending"} />
                    <ProgressTrackerItem
                        error={isFailure}
                        success={isSuccess}
                        active={this.isStateActive("SCHEDULED")}
                        complete={this.isStateComplete("SCHEDULED")}
                        title={"Scheduled"} />
                    <ProgressTrackerItem
                        error={isFailure}
                        success={isSuccess}
                        active={this.isStateActive("RUNNING")}
                        complete={this.isStateComplete("RUNNING")}
                        title={"Running"} />
                    <ProgressTrackerItem
                        error={isFailure}
                        success={isSuccess}
                        active={this.isStateActive(lastStep)}
                        complete={this.isStateComplete(lastStep)}
                        title={lastStepName} />
                </ProgressTracker>
            </div>
        );
    }

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
            <div>
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
            <div>
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
                        <div className={"row"}>
                            <div className="col-md-6">
                                <h4>Output</h4>
                                <pre className="stream"
                                    ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></pre>
                            </div>

                            <div className="col-md-6">
                                <h4>Information</h4>
                                <pre className="stream"
                                    ref={el => this.stderrEl = el}><code>{this.state.stderr}</code></pre>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    renderFilePanel() {
        const { page } = this.state;
        if (!this.state.page.items.length) return null;
        return (
            <div>
                <h4>Output Files</h4>
                <PaginationList
                    page={page}
                    pageRenderer={(page) =>
                        <FilesTable
                            files={page.items}
                            sortingIcon={() => null}
                            onCheckFile={() => null}
                            setFileSelectorCallback={() => null}
                            fetchFiles={() => null}
                            onFavoriteFile={() => null}
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
    <ul className={"progress-tracker progress-tracker--word progress-tracker--word-center"}>{children}</ul>
);
const ProgressTrackerItem = (props) => (
    <li
        className={
            "progress-step " +
            ((props.complete) ? "is-complete " : "") +
            ((props.active) ? "is-active " : "") +
            ((props.error) ? "error" : "") +
            ((props.success) ? "success" : "")
        }
    >
        <span className="progress-marker" />
        <span className={"progress-text"}>
            <h4 className={"progress-title visible-md visible-lg"}>{props.title}</h4>
        </span>
    </li>
);

export default connect()(DetailedResult);