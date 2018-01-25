import React from 'react';
import LoadingIcon from '../LoadingIcon'
import {WebSocketSupport} from '../../UtilityFunctions'
import pubsub from "pubsub-js";
import {Cloud} from "../../../authentication/SDUCloudObject";
import {Card} from "../Cards";
import {Table} from 'react-bootstrap';

class Analyses extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            analyses: [],
            loading: false,
            currentAnalysis: null,
            comment: "",
        }
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getAnalyses();
    }

    getAnalyses() {
        this.setState({
            loading: true
        });
        Cloud.get("/hpc/jobs").then(analyses => {
            analyses.forEach(analysis => {
                Cloud.get(`/hpc/jobs/${analysis.jobId}`).then(a => {
                });
                analysis.name = "Hello app";
            });
            this.setState(() => ({
                loading: false,
                analyses: analyses,
            }));
        });
    }

    render() {
        const noAnalysis = this.state.analyses.length ? '' : <h3 className="text-center">
            <small>No analyses found.</small>
        </h3>;

        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <LoadingIcon loading={this.state.loading}/>
                        <Card xs={6} sm={12}>
                            <WebSocketSupport/>
                            {noAnalysis}
                            <div className="card-body">
                                <Table className="table table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th>App Name</th>
                                        <th>Job Id</th>
                                        <th>Status</th>
                                        <th>Comments</th>
                                    </tr>
                                    </thead>
                                    <AnalysesList analyses={this.state.analyses}/>
                                </Table>
                            </div>
                        </Card>
                    </div>
                </div>
            </section>
        )
    }
}

function AnalysesList(props) {
    if (!props.analyses && !props.analyses[0].name) {
        return null;
    }
    let i = 0;
    const analysesList = props.analyses.map(analysis =>
        <tr key={i++} className="gradeA row-settings">
            <td>{analysis.name}</td>
            <td>{analysis.jobId}</td>
            <td>{analysis.status}</td>
            <td>
                <AnalysesButton analysis={analysis} comments={analysis.comments}/>
            </td>
        </tr>
    );

    return (
        <tbody>
        {analysesList}
        </tbody>)
}

function AnalysesButton(props) {
    if (props.comments) {
        return (
            <button className="btn btn-primary">
                Show {props.comments.length} comments
            </button>
        )
    } else {
        return (<button className="btn btn-secondary">Write comment</button>)
    }
}

export default Analyses
