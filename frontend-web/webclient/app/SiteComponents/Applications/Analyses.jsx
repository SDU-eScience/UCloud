import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { WebSocketSupport, toLowerCaseAndCapitalize, shortUUID } from "../../UtilityFunctions"
import { updatePageTitle } from "../../Actions/Status";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Container, Table } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { connect } from "react-redux";
import "../Styling/Shared.scss";
import { setLoading, fetchAnalyses } from "../../Actions/Analyses";

class Analyses extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            reloadIntervalId: -1
        };
        this.props.dispatch(updatePageTitle("Analyses"));
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
        const noAnalysis = this.props.analyses.length ? "" : (<h3 className="text-center">
            <small>No analyses found.</small>
        </h3>);

        return (
            <React.StrictMode>
                <BallPulseLoading loading={this.props.loading} />
                <WebSocketSupport />
                {noAnalysis}
                <Table basic="very">
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>App Name</Table.HeaderCell>
                            <Table.HeaderCell>Job Id</Table.HeaderCell>
                            <Table.HeaderCell>State</Table.HeaderCell>
                            <Table.HeaderCell>Status</Table.HeaderCell>
                            <Table.HeaderCell>Started at</Table.HeaderCell>
                            <Table.HeaderCell>Last updated at</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <AnalysesList analyses={this.props.analyses} />
                    <Table.Footer>
                        <Table.Row>
                            <Table.Cell colSpan="6" textAlign="center">
                                <PaginationButtons
                                    totalPages={this.props.totalPages}
                                    currentPage={this.props.pageNumber}
                                    toPage={(pageNumber) => dispatch(fetchAnalyses(analysesPerPage, pageNumber))}
                                />
                            </Table.Cell>
                        </Table.Row>
                    </Table.Footer>
                </Table>
                <EntriesPerPageSelector
                    entriesPerPage={this.props.analysesPerPage}
                    onChange={(pageSize) => dispatch(fetchAnalyses(pageSize, 0))}
                    totalPages={this.props.totalPages}
                >
                    {" Analyses per page"}
                </EntriesPerPageSelector>
            </React.StrictMode>
        )
    }
}

const AnalysesList = ({ analyses, children }) => {
    if (!analyses && !analyses[0].name) {
        return null;
    }
    const analysesList = analyses.map((analysis, index) => {
        const jobIdField = analysis.status === "COMPLETE" ?
            (<Link to={`/files/${Cloud.jobFolder}/${analysis.jobId}`}>{analysis.jobId}</Link>) : analysis.jobId;
        return (
            <Table.Row key={index} className="gradeA row-settings">
                <Table.Cell>
                    <Link to={`/applications/${analysis.appName}/${analysis.appVersion}`}>
                        {analysis.appName}@{analysis.appVersion}
                    </Link>
                </Table.Cell>
                <Table.Cell>
                    <Link to={`/analyses/${jobIdField}`}>
                        <span title={jobIdField}>{shortUUID(jobIdField)}</span>
                    </Link>
                </Table.Cell>
                <Table.Cell>{toLowerCaseAndCapitalize(analysis.state)}</Table.Cell>
                <Table.Cell>{analysis.status}</Table.Cell>
                <Table.Cell>{formatDate(analysis.createdAt)}</Table.Cell>
                <Table.Cell>{formatDate(analysis.modifiedAt)}</Table.Cell>
            </Table.Row>)
    });
    return (
        <Table.Body>
            {analysesList}
        </Table.Body>)
};

const formatDate = (millis) => {
    // TODO Very primitive
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`
};

const pad = (value, length) => (value.toString().length < length) ? pad("0" + value, length) : value;

const mapStateToProps = ({ analyses }) => ({ loading, analyses, analysesPerPage, pageNumber, totalPages } = analyses);
export default connect(mapStateToProps)(Analyses);