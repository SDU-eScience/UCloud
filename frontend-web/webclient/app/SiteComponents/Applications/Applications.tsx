import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "../Pagination";
import { Table, Button, Icon } from "semantic-ui-react";
import { getSortingIcon } from "../../UtilityFunctions";
import { connect } from "react-redux";
import { fetchApplications, setLoading, toPage, updateApplicationsPerPage, updateApplications } from "../../Actions/Applications";
import { updatePageTitle } from "../../Actions/Status";
import "../Styling/Shared.scss";
import { Application } from "../../types/types";
import { SortBy, SortOrder } from "../Files/Files";


interface ApplicationsState {
    lastSorting: {
        name: SortBy
        asc: SortOrder
    }
}

interface ApplicationsProps {
    // Redux Store
    applications: Application[]
    loading: boolean
    applicationsPerPage: number
    currentApplicationsPage: number

    // Redux operations
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchApplications: () => void
    updateApplications: (applications: Application[]) => void
    toPage: (pageNumber: number) => void
    updateApplicationsPerPage: (applicationsPerPage: number) => void
}

class Applications extends React.Component<ApplicationsProps, ApplicationsState> {
    constructor(props) {
        super(props);
        this.state = {
            lastSorting: {
                name: SortBy.PATH,
                asc: SortOrder.ASCENDING,
            }
        };
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications();
    }

    render() {
        const { applications, loading, applicationsPerPage, currentApplicationsPage, toPage, updateApplicationsPerPage } = this.props;
        const currentlyShownApplications = applications.slice(currentApplicationsPage * applicationsPerPage, currentApplicationsPage * applicationsPerPage + applicationsPerPage);
        return (
            <React.Fragment>
                <Pagination.List
                    loading={loading}
                    itemsPerPage={applicationsPerPage}
                    currentPage={currentApplicationsPage}
                    pageRenderer={(page) =>
                        (<Table basic="very">
                            <Table.Header>
                                <Table.Row>
                                    <Table.HeaderCell>
                                        Application Name/>
                                    </Table.HeaderCell>
                                    <Table.HeaderCell>
                                        Version
                                    </Table.HeaderCell>
                                    <Table.HeaderCell />
                                </Table.Row>
                            </Table.Header>
                            <ApplicationsList applications={page.items} />
                        </Table>)
                    }
                    results={{ items: currentlyShownApplications, itemsPerPage: applicationsPerPage, itemsInTotal: applications.length, pageNumber: currentApplicationsPage }} // Remove when pagination is introduced
                    onItemsPerPageChanged={(size) => updateApplicationsPerPage(size)}
                    onPageChanged={(page) => toPage(page)}
                    onRefresh={() => null}
                    onErrorDismiss={() => null}
                />
            </React.Fragment>);
    }
}

const ApplicationsList = ({ applications }) => {
    let applicationsList = applications.map((app, index) =>
        <SingleApplication key={index} app={app} />
    );
    return (
        <Table.Body>
            {applicationsList}
        </Table.Body>)
};

const SingleApplication = ({ app }) => (
    <Table.Row>
        <Table.Cell title={app.description}>{app.prettyName}</Table.Cell>
        <Table.Cell title={app.description}>{app.info.version}</Table.Cell>
        <Table.Cell>
            <Link to={`/applications/${app.info.name}/${app.info.version}/`}>
                <Button>Run</Button>
            </Link>
        </Table.Cell>
    </Table.Row>
);


const mapDispatchToProps = (dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("Applications")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchApplications: () => dispatch(fetchApplications()),
    updateApplications: (applications: Application[]) => dispatch(updateApplications(applications)),
    toPage: (pageNumber: number) => dispatch(toPage(pageNumber)),
    updateApplicationsPerPage: (applicationsPerPage: number) => dispatch(updateApplicationsPerPage(applicationsPerPage))
})
const mapStateToProps = ({ applications }) => applications;

export default connect(mapStateToProps, mapDispatchToProps)(Applications);