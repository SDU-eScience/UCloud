import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { WebSocketSupport, toLowerCaseAndCapitalize, shortUUID } from "../../UtilityFunctions"
import { updatePageTitle } from "../../Actions/Status";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Card } from "../Cards";
import { Table } from "react-bootstrap";
import { Link } from "react-router-dom";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { connect } from "react-redux";
import { setLoading, fetchAnalyses, setPageSize } from "../../Actions/Analyses";

class Analyses extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            reloadIntervalId: -1
        };
        this.props.dispatch(updatePageTitle(this.constructor.name));
    }

    componentWillMount() {
        this.getAnalyses(false);
        let reloadIntervalId = setInterval(() => {
            this.getAnalyses(true)
        }, 10000);
        this.setState({ reloadIntervalId });
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
    }

    getAnalyses(silent) {
        const { dispatch, analysesPerPage, pageNumber } = this.props;
        if (!silent) {
            dispatch(setLoading(true))
        }
        dispatch(fetchAnalyses(analysesPerPage, pageNumber));
    }

    render() {
        const { dispatch, analysesPerPage } = this.props;
        const noAnalysis = this.props.analyses.length ? '' : <h3 className="text-center">
            <small>No analyses found.</small>
        </h3>;

        return (
            <section>
                <div className="container" style={{ marginTop: "60px" }}>
                    <div>
                        <BallPulseLoading loading={this.props.loading} />
                        <Card>
                            <WebSocketSupport />
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
                                    <AnalysesList analyses={this.props.analyses} />
                                </Table>
                            </div>
                        </Card>
                        <PaginationButtons
                            totalPages={this.props.totalPages}
                            currentPage={this.props.pageNumber}
                            toPage={(pageNumber) => dispatch(fetchAnalyses(analysesPerPage, pageNumber))}
                        />
                        <EntriesPerPageSelector
                            entriesPerPage={this.props.analysesPerPage}
                            handlePageSizeSelection={(pageSize) => dispatch(fetchAnalyses(pageSize, 0))}
                            totalPages={this.props.totalPages}
                        >
                            Analyses per page
                        </EntriesPerPageSelector>
                    </div>
                </div>
            </section>
        )
    }
}

const AnalysesList = ({ analyses }) => {
    if (!analyses && !analyses[0].name) {
        return null;
    }
    const analysesList = analyses.map((analysis, index) => {
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
    });
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

const mapStateToProps = (state) => ({ loading, analyses, analysesPerPage, pageNumber, totalPages } = state.analyses);
export default connect(mapStateToProps)(Analyses);