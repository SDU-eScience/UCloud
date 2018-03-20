import React from 'react';
import {BallPulseLoading} from '../LoadingIcon'
import pubsub from "pubsub-js";
import PromiseKeeper from "../../PromiseKeeper";
import {Cloud} from "../../../authentication/SDUCloudObject";

export default class DetailedResult extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            stdout: "",
            stderr: "",
            stdoutLine: 0,
            stderrLine: 0,
            stdoutOldTop: -1,
            stderrOldTop: -1,
            promises: new PromiseKeeper()
        };
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', "Results for Job");

        this.retrieveStdStreams();
        let reloadIntervalId = setInterval(() => this.retrieveStdStreams(), 3000);
        this.setState({reloadIntervalId: reloadIntervalId});
    }

    componentWillUnmount() {
        if (this.state.reloadIntervalId) clearInterval(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
    }

    scroll() {
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
        this.setState({loading: true});
        this.state.promises.makeCancelable(
            Cloud.get(`hpc/jobs/follow/${this.props.match.params.jobId}?` +
                `stdoutLineStart=${this.state.stdoutLine}&stdoutMaxLines=1000` +
                `&stderrLineStart=${this.state.stderrLine}&stderrMaxLines=1000`
            ).then(
                ({response}) => {
                    this.setState({
                        stdout: this.state.stdout + response.stdout,
                        stderr: this.state.stderr + response.stderr,
                        stdoutLine: response.stdoutNextLine,
                        stderrLine: response.stderrNextLine,
                    });
                    this.scroll()
                },

                failure => {
                    console.log(failure);
                }
            ).finally(() => this.setState({loading: false}))
        );
    }

    render() {
        return (
            <div>
                <div className="row">
                    <div className="col-md-6">
                        <h4>stdout</h4>
                        <pre style={{height: "500px", overflow: "auto"}} ref={el => this.stdoutEl = el}>
                            <code>{this.state.stdout}</code>
                        </pre>
                    </div>

                    <div className="col-md-6">
                        <h4>stderr</h4>
                        <pre style={{height: "500px", overflow: "auto"}} ref={el => this.stderrEl = el}>
                            <code>{this.state.stderr}</code>
                        </pre>
                    </div>
                </div>

                <div className="row">
                    <BallPulseLoading loading={this.state.loading}/>
                </div>
            </div>
        );
    }
}