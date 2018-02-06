import React from 'react';
import {BallPulseLoading} from '../LoadingIcon'
import {WebSocketSupport, toLowerCaseAndCapitalize} from '../../UtilityFunctions'
import pubsub from "pubsub-js";
import {Cloud} from "../../../authentication/SDUCloudObject";
import {Card} from "../Cards";
import {Table} from 'react-bootstrap';
import {Link} from 'react-router'
import {PaginationButtons, EntriesPerPageSelector} from "../Pagination"

class Analyses extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            analyses: [],
            loading: false,
            currentPage: 0,
            analysesPerPage: 10,
            totalPages: () => Math.ceil(this.state.analyses.length / this.state.analysesPerPage),

            reloadIntervalId: -1
        };
        this.nextPage = this.nextPage.bind(this);
        this.previousPage = this.previousPage.bind(this);
        this.toPage = this.toPage.bind(this);
        this.handlePageSizeSelection = this.handlePageSizeSelection.bind(this);
        this.getCurrentAnalyses = this.getCurrentAnalyses.bind(this);
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getAnalyses(false);

        let reloadIntervalId = setInterval(() => {
            this.getAnalyses(true)
        }, 10000);
        this.setState({reloadIntervalId: reloadIntervalId});
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
    }

    getAnalyses(silent) {
        if (!silent) {
            this.setState({
                loading: true
            });
        }

        Cloud.get("/hpc/jobs").then(analyses => {
            this.setState(() => ({
                loading: false,
                analyses: analyses,
            }));
        });
    }

    getCurrentAnalyses() {
        let analysesPerPage = this.state.analysesPerPage;
        let currentPage = this.state.currentPage;
        return this.state.analyses.slice(currentPage * analysesPerPage, currentPage * analysesPerPage + analysesPerPage);
    }

    handlePageSizeSelection(newPageSize) {
        this.setState(() => ({
            analysesPerPage: newPageSize,
        }));
    }

    toPage(n) {
        this.setState(() => ({
            currentPage: n,
        }));
    }

    nextPage() {
        this.setState(() => ({
            currentPage: this.state.currentPage + 1,
        }));
    }

    previousPage() {
        this.setState(() => ({
            currentPage: this.state.currentPage - 1,
        }));
    }

    render() {
        const noAnalysis = this.state.analyses.length ? '' : <h3 className="text-center">
            <small>No analyses found.</small>
        </h3>;

        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <BallPulseLoading loading={this.state.loading}/>
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
                                        <th>Started at</th>
                                        <th>Last updated at</th>
                                    </tr>
                                    </thead>
                                    <AnalysesList analyses={this.getCurrentAnalyses()}/>
                                </Table>
                            </div>
                        </Card>
                        <PaginationButtons totalPages={this.state.totalPages} currentPage={this.state.currentPage}
                                           toPage={this.toPage} nextPage={this.nextPage}
                                           previousPage={this.previousPage}/>
                        <EntriesPerPageSelector entriesPerPage={this.state.analysesPerPage}
                                                handlePageSizeSelection={this.handlePageSizeSelection}/>
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
            <td><Link
                to={`/applications/${analysis.appName}/${analysis.appVersion}`}>{analysis.appName}@{analysis.appVersion}</Link>
            </td>
            <td>{analysis.jobId}</td>
            <td>{toLowerCaseAndCapitalize(analysis.status)}</td>
            <td>{formatDate(analysis.createdAt)}</td>
            <td>{formatDate(analysis.modifiedAt)}</td>
        </tr>
    );

    return (
        <tbody>
        {analysesList}
        </tbody>)
}

function formatDate(millis) {
    // TODO Very primitive
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`
}

function pad(value, length) {
    return (value.toString().length < length) ? pad("0" + value, length) : value;
}

export default Analyses
