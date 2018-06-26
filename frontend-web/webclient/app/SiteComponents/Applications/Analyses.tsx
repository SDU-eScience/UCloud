import * as React from "react";
import { toLowerCaseAndCapitalize, shortUUID } from "../../UtilityFunctions"
import { updatePageTitle } from "../../Actions/Status";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { Table, Responsive } from "semantic-ui-react";
import { Link } from "react-router-dom";
import { List } from "../Pagination/List";
import { connect } from "react-redux";
import "../Styling/Shared.scss";
import { setLoading, fetchAnalyses } from "../../Actions/Analyses";
import { Page, Analysis } from "../../types/types";

interface AnalysesProps {
    // Redux Store
    page: Page<Analysis>
    loading: boolean
    // Redux Operations
    updatePageTitle: (title: string) => void
    setLoading: (loading: boolean) => void
    fetchAnalyses: (itemsPerPage: number, pageNumber: number) => void
}

interface AnalysesState {
    reloadIntervalId: number
}

class Analyses extends React.Component<AnalysesProps, AnalysesState> {
    constructor(props) {
        super(props);
        this.state = {
            reloadIntervalId: -1
        };
        this.props.updatePageTitle("Results");
    }

    componentDidMount() {
        this.getAnalyses(false);
        let reloadIntervalId = window.setInterval(() => {
            this.getAnalyses(true)
        }, 10000);
        this.setState({ reloadIntervalId });
    }

    componentWillUnmount() {
        clearInterval(this.state.reloadIntervalId);
    }

    getAnalyses(silent) {
        const { page } = this.props;
        if (!silent) {
            this.props.setLoading(true)
        }
        this.props.fetchAnalyses(page.itemsPerPage, page.pageNumber);
    }

    render() {
        const { page, loading } = this.props;
        return (
            <React.StrictMode>
                <List
                    loading={loading}
                    itemsPerPage={page.itemsPerPage}
                    currentPage={page.pageNumber}
                    pageRenderer={(page) =>
                        <Table basic="very" unstackable className="mobile-padding">
                            <TableHeader />
                            <Table.Body>
                                {page.items.map((a, i) => <Analysis analysis={a} key={i} />)}
                            </Table.Body>
                        </Table>
                    }
                    results={page}
                    onItemsPerPageChanged={(size) => this.props.fetchAnalyses(size, 0)}
                    onPageChanged={(pageNumber) => this.props.fetchAnalyses(page.itemsPerPage, pageNumber)}
                    onRefresh={() => null}
                    onErrorDismiss={() => null}
                />
            </React.StrictMode>
        )
    }
}

const TableHeader = () => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell>App Name</Table.HeaderCell>
            <Table.HeaderCell>Job Id</Table.HeaderCell>
            <Table.HeaderCell>State</Table.HeaderCell>
            <Responsive as={Table.HeaderCell} minWidth={768}>Status</Responsive>
            <Responsive as={Table.HeaderCell} minWidth={768}>Started at</Responsive>
            <Responsive as={Table.HeaderCell} minWidth={768}>Last updated at</Responsive>
        </Table.Row>
    </Table.Header>
)

const Analysis = ({ analysis }) => {
    const jobIdField = analysis.status === "COMPLETE" ?
        (<Link to={`/files/${Cloud.jobFolder}/${analysis.jobId}`}>{analysis.jobId}</Link>) : analysis.jobId;
    return (
        <Table.Row>
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
            <Responsive as={Table.Cell} minWidth={768}>{analysis.status}</Responsive>
            <Responsive as={Table.Cell} minWidth={768}>{formatDate(analysis.createdAt)}</Responsive>
            <Responsive as={Table.Cell} minWidth={768}>{formatDate(analysis.modifiedAt)}</Responsive>
        </Table.Row>)
};

const formatDate = (millis) => {
    // TODO Very primitive
    let d = new Date(millis);
    return `${pad(d.getDate(), 2)}/${pad(d.getMonth() + 1, 2)}/${pad(d.getFullYear(), 2)} ${pad(d.getHours(), 2)}:${pad(d.getMinutes(), 2)}:${pad(d.getSeconds(), 2)}`
};

const pad = (value, length) => (value.toString().length < length) ? pad("0" + value, length) : value;

const mapDispatchToProps = (dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("Results")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchAnalyses: (itemsPerPage: number, pageNumber: number) => dispatch(fetchAnalyses(itemsPerPage, pageNumber))

})
const mapStateToProps = (state) => state.analyses;
export default connect(mapStateToProps, mapDispatchToProps)(Analyses);