import React from 'react';
import {BallPulseLoading} from '../LoadingIcon'
import pubsub from "pubsub-js";
import PromiseKeeper from "../../PromiseKeeper";
import {Cloud} from "../../../authentication/SDUCloudObject";
import {shortUUID} from "../../UtilityFunctions";
import {ListGroup, ListGroupItem} from "react-bootstrap";
import {Link} from "react-router-dom";
import {FilesTable} from "../Files";
import "./wizard.scss";

export default class DetailedResult extends React.Component {
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
    }

    get jobId() {
        return this.props.match.params.jobId;
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', `Results for Job: ${shortUUID(this.jobId)}`);

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
        return (
            <div>
                <h4>Progress</h4>
                <div className="card">
                    <div className="card-body">
                        <ProgressTracker>
                            <ProgressTrackerItem
                                active={this.isStateActive("VALIDATED")}
                                complete={this.isStateComplete("VALIDATED")}
                                title={"Validated"}/>
                            <ProgressTrackerItem
                                active={this.isStateActive("PENDING")}
                                complete={this.isStateComplete("PENDING")}
                                title={"Pending"}/>
                            <ProgressTrackerItem
                                active={this.isStateActive("SCHEDULED")}
                                complete={this.isStateComplete("SCHEDULED")}
                                title={"Scheduled"}/>
                            <ProgressTrackerItem
                                active={this.isStateActive("RUNNING")}
                                complete={this.isStateComplete("RUNNING")}
                                title={"Running"}/>
                            <ProgressTrackerItem
                                active={this.isStateActive("SUCCESS")}
                                complete={this.isStateComplete("SUCCESS")}
                                title={"Success"}/>
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

        if (this.state.appState === "SUCCESS") {
            domEntries.push(
                <ListGroupItem key="app-complete">
                    Application has completed successfully. Click <Link
                    to={`/files//home/${Cloud.username}/Jobs/${this.jobId}`}>here</Link> to go to the output.
                </ListGroupItem>
            );
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
        return (this.state.complete && this.state.stdout === "" && this.state.stderr === "") ? null : (
            <div>
                <h4>Standard Streams</h4>
                <div className="card">
                    <div className={"card-body"}>
                        <div className={"row"}>
                            <div className="col-md-6">
                                <h4>Output Stream</h4>
                                <pre style={{height: "500px", overflow: "auto"}}
                                     ref={el => this.stdoutEl = el}><code>{this.state.stdout}</code></pre>
                            </div>

                            <div className="col-md-6">
                                <h4>Error Stream</h4>
                                <pre style={{height: "500px", overflow: "auto"}}
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

                <div className="row">
                    <BallPulseLoading loading={this.state.loading}/>
                </div>
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

const ProgressTracker = (props) => <ul
    className={"progress-tracker progress-tracker--word progress-tracker--word-center"}>{props.children}</ul>;
const ProgressTrackerItem = (props) =>
    <li
        className={"progress-step " +
        ((props.complete) ? "is-complete " : "") +
        ((props.active) ? "is-active " : "")}
    >
        <span className="progress-marker"/>
        <span className={"progress-text"}>
            <h4 className={"progress-title visible-md visible-lg"}>{props.title}</h4>
        </span>
    </li>;