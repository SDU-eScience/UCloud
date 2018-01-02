import $ from 'jquery';
import React from 'react';
import LoadingIcon from '../LoadingIcon'
import {WebSocketSupport} from '../../UtilityFunctions'

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
        this.getAnalyses();
    }

    getAnalyses() {
        this.setState({
            loading: true
        });
        $.getJSON("/api/getAnalyses").then(analyses => {
            analyses.forEach(it => {
                it.jobId = Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000) + '-' + Math.floor(Math.random() * 100000)
            });
            analyses.sort((a, b) => {
                return a.name.localeCompare(b.name);
            });
            this.setState({
                loading: false,
                analyses: analyses,
            });
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
                        <div className="card">
                            <div className="card-body">
                                <WebSocketSupport/>
                                {noAnalysis}
                                <table id="table-options" className="table-datatable table table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th>Workflow Name</th>
                                        <th>Job Id</th>
                                        <th>Status</th>
                                        <th>Comments</th>
                                    </tr>
                                    </thead>
                                    <AnalysesList analyses={this.state.analyses}/>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        )
    }
}

function AnalysesList(props) {
    const analyses = props.analyses.slice();
    let i = 0;
    const analysesList = analyses.map(analysis =>
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
    let analysis = props.analysis;
    if (props.comments.length) {
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
