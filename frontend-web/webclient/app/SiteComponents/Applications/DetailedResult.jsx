import React from 'react';
import {Spinner} from '../LoadingIcon/LoadingIcon'
import PromiseKeeper from "../../PromiseKeeper";
import {Cloud} from "../../../authentication/SDUCloudObject";
import {shortUUID} from "../../UtilityFunctions";
import {Glyphicon, ListGroup, ListGroupItem, OverlayTrigger, Tooltip} from "react-bootstrap";
import {Link} from "react-router-dom";
import {FilesTable} from "../Files/Files";
import "./wizard.scss";
import "./Applications.scss";
import { connect } from "react-redux";
import { updatePageTitle } from "../../Actions/Status";

class DetailedResult extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            complete: false,
            appState: "VALIDATED",
            status: "",
            app: {"name": "", "version": ""},
            stdout: "",
            stderr: "",
            stdoutLine: 0,
            stderrLine: 0,
            stdoutOldTop: -1,
            stderrOldTop: -1,
            promises: new PromiseKeeper()
        };
        this.props.dispatch(updatePageTitle(`Results for Job: ${shortUUID(this.jobId)}`));
    }

    get jobId() {
        return this.props.match.params.jobId;
    }

    componentDidMount() {
        this.retrieveStdStreams();
        let reloadIntervalId = setInterval(() => this.retrieveStdStreams(), 1000);
        this.setState({reloadIntervalId: reloadIntervalId});
    }

    componentWillUnmount() {
        if (this.state.reloadIntervalId) clearInterval(this.state.reloadIntervalId);
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

        this.setState({loading: true});
        this.state.promises.makeCancelable(
            Cloud.get(`hpc/jobs/follow/${this.jobId}?` +
                `stdoutLineStart=${this.state.stdoutLine}&stdoutMaxLines=1000` +
                `&stderrLineStart=${this.state.stderrLine}&stderrMaxLines=1000`
            ).then(
                ({response}) => {
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
                    console.log(failure);
                }
            ).finally(() => this.setState({loading: false}))
        );
    }

    retrieveStateWhenCompleted() {
        if (!this.state.complete) return;
        if (this.state.files || this.state.loading) return;
        this.setState({loading: true});

        Cloud.get(`files?path=/home/${Cloud.username}/Jobs/${this.jobId}`).then(({response}) => {
            this.setState({files: response, loading: false});
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
                <div className="card">
                    <div className="card-body">
                        <ProgressTracker>
                            <ProgressTrackerItem
                                error={isFailure}
                                success={isSuccess}
                                active={this.isStateActive("VALIDATED")}
                                complete={this.isStateComplete("VALIDATED")}
                                title={"Validated"}/>
                            <ProgressTrackerItem
                                error={isFailure}
                                success={isSuccess}
                                active={this.isStateActive("PENDING")}
                                complete={this.isStateComplete("PENDING")}
                                title={"Pending"}/>
                            <ProgressTrackerItem
                                error={isFailure}
                                success={isSuccess}
                                active={this.isStateActive("SCHEDULED")}
                                complete={this.isStateComplete("SCHEDULED")}
                                title={"Scheduled"}/>
                            <ProgressTrackerItem
                                error={isFailure}
                                success={isSuccess}
                                active={this.isStateActive("RUNNING")}
                                complete={this.isStateComplete("RUNNING")}
                                title={"Running"}/>
                            <ProgressTrackerItem
                                error={isFailure}
                                success={isSuccess}
                                active={this.isStateActive(lastStep)}
                                complete={this.isStateComplete(lastStep)}
                                title={lastStepName}/>
                        </ProgressTracker>
                    </div>
                </div>
            </div>
        );
    }

    renderInfoPanel() {
        let entries = [
            {key: "Application Name", value: this.state.app.name},
            {key: "Application Version", value: this.state.app.version},
            {key: "Status", value: this.state.status},
        ];

        let domEntries = entries.map((it, idx) => <ListGroupItem key={idx}><b>{it.key}</b>: {it.value}</ListGroupItem>);

        switch (this.state.appState) {
            case "SUCCESS":
                domEntries.push(
                    <ListGroupItem key="app-info">
                        Application has completed successfully. Click <Link
                        to={`/files//home/${Cloud.username}/Jobs/${this.jobId}`}>here</Link> to go to the output.
                    </ListGroupItem>
                );
                break;
            case "SCHEDULED":
                domEntries.push(
                    <ListGroupItem key="app-info">
                        Your application is currently in the Slurm queue on ABC2 <Spinner loading color="primary"/>
                    </ListGroupItem>
                );
                break;
            case "PENDING":
                domEntries.push(
                    <ListGroupItem key="app-info">
                        We are currently transferring your job from SDUCloud to ABC2 <Spinner loading color="primary"/>
                    </ListGroupItem>
                );
                break;
            case "RUNNING":
                domEntries.push(
                    <ListGroupItem key="app-info">
                        Your job is currently being executed on ABC2 <Spinner loading color="primary"/>
                    </ListGroupItem>
                );
                break;
        }

        return (
            <div>
                <h4>Job Information</h4>
                <div className={"card"}>
                    <div className={"card-body"}>
                        <div className={"row"}>
                            <ListGroup>
                                {domEntries}
                            </ListGroup>
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    renderStreamPanel() {
        if (this.state.complete && this.state.stdout === "" && this.state.stderr === "") return null;
        let tooltip = <Tooltip placement="right" className="in" id="tooltip-right">
            Streams are collected from <code>stdout</code> and <code>stderr</code> of your application.
        </Tooltip>;

        return (
            <div>
                <h4>
                    Standard Streams
                    &nbsp;
                    <OverlayTrigger placement="right" overlay={tooltip}>
                        <Glyphicon glyph="info-sign"/>
                    </OverlayTrigger>
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
        if (!this.state.files) return null;
        return (
            <div>
                <h4>Output Files</h4>
                <FilesTable files={this.state.files} forceInlineButtons/>
            </div>
        );
    }

    render() {
        return (
            <div className="container">
                <div className="row">{this.renderProgressPanel()}</div>
                <div className="row">{this.renderInfoPanel()}</div>
                <div className="row">{this.renderFilePanel()}</div>
                <div className="row">{this.renderStreamPanel()}</div>
            </div>
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

const ProgressTracker = (props) => (
    <ul className={"progress-tracker progress-tracker--word progress-tracker--word-center"}>{props.children}</ul>
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
        <span className="progress-marker"/>
        <span className={"progress-text"}>
        <h4 className={"progress-title visible-md visible-lg"}>{props.title}</h4>
    </span>
    </li>
);

export default connect()(DetailedResult);