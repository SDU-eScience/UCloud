import React from 'react';
import {BallPulseLoading} from '../LoadingIcon/LoadingIcon'
import {WebSocketSupport, toLowerCaseAndCapitalize, shortUUID} from '../../UtilityFunctions'
import pubsub from "pubsub-js";
import {Cloud} from "../../../authentication/SDUCloudObject";
import {Card} from "../Cards";
import {Table} from 'react-bootstrap';
import {Link} from "react-router-dom";
import {PaginationButtons, EntriesPerPageSelector} from "../Pagination"
import PromiseKeeper from "../../PromiseKeeper";

class Analyses extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            analyses: [],
            loading: false,
            itemsInTotal: 0,
            currentPage: 0,
            analysesPerPage: 15,
            totalPages: 0,
            reloadIntervalId: -1
        };
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getAnalyses(false);
        const reloadIntervalId = setInterval(() => {
            this.getAnalyses(true)
        }, 10000);
        this.setState(() => {reloadIntervalId});
        this.toPage = this.toPage.bind(this);
        this.handlePageSizeSelection = this.handlePageSizeSelection.bind(this);
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
        this.state.promises.cancelPromises();
    }

    getAnalyses(silent) {
        if (!silent) {
            this.setState({
                loading: true
            });
        }

        this.state.promises.makeCancelable(Cloud.get(`/hpc/jobs/?itemsPerPage=${this.state.analysesPerPage}&page=${this.state.currentPage}`)).promise.then(({response}) => {
            this.setState(() => ({
                loading: false,
                analyses: response.items,
                analysesPerPage: response.itemsPerPage,
                pageNumber: response.pageNumber,
                totalPages: response.pagesInTotal 
            }));
        });
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
        this.getAnalyses(false);
    }

    render() {
        const noAnalysis = this.state.analyses.length ? '' : <h3 className="text-center">
            <small>No analyses found.</small>
        </h3>;

        return (
            <section>
                <div className="container-fluid">
                    <div>
                        <BallPulseLoading loading={this.state.loading}/>
                        <Card>
                            <WebSocketSupport/>
                            {noAnalysis}
                            <div className="card-body">
                                <Table responsive className="table table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th>App Name</th>
                                        <th>Job Id</th>
                                        <th>State</th>
                                        <th>Status</th>
                                        <th>Started at</th>
                                        <th>Last updated at</th>
                                    </tr>
                                    </thead>
                                    <AnalysesList analyses={this.state.analyses}/>
                                </Table>
                            </div>
                        </Card>
                        <PaginationButtons totalPages={this.state.totalPages}
                                           currentPage={this.state.currentPage}
                                           toPage={this.toPage}/>
                        <EntriesPerPageSelector entriesPerPage={this.state.analysesPerPage}
                                                handlePageSizeSelection={this.handlePageSizeSelection}/>
                    </div>
                </div>
            </section>
        )
    }
}

const AnalysesList = (props) => {
    if (!props.analyses && !props.analyses[0].name) {
        return null;
    }
    const analysesList = props.analyses.map((analysis, index) => {
            const jobIdField = analysis.status === "COMPLETE" ?
                (<Link to={`/files/${Cloud.jobFolder}/${analysis.jobId}`}>{analysis.jobId}</Link>) : analysis.jobId;
            return (
                <tr key={index} className="gradeA row-settings">
                    <td>
                        <Link to={`/applications/${analysis.appName}/${analysis.appVersion}`}>
                            {analysis.appName}@{analysis.appVersion}
                        </Link>
                    </td>
                    <td>
                        <Link to={`/analyses/${jobIdField}`}>
                            <span title={jobIdField}>{shortUUID(jobIdField)}</span>
                        </Link>
                    </td>
                    <td>{toLowerCaseAndCapitalize(analysis.state)}</td>
                    <td>{analysis.status}</td>
                    <td>{formatDate(analysis.createdAt)}</td>
                    <td>{formatDate(analysis.modifiedAt)}</td>
                </tr>)
        }
    );
    return (
        <tbody>
        {analysesList}
        </tbody>)
};

const formatDate = (millis) => {
    // TODO Very primitive
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`
};

const pad = (value, length) => (value.toString().length < length) ? pad("0" + value, length) : value;

export default Analyses
