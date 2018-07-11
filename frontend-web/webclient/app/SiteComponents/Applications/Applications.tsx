import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "../Pagination";
import { Table, Button } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    toPage,
    updateApplicationsPerPage,
    updateApplications
} from "../../Actions/Applications";
import { updatePageTitle } from "../../Actions/Status";
import "../Styling/Shared.scss";
import { Page, Application } from "../../Types";
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps } from ".";

class Applications extends React.Component<ApplicationsProps> {
    constructor(props: ApplicationsProps) {
        super(props);
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications();
    }

    render() {
        const { applications, loading, toPage, updateApplicationsPerPage } = this.props;

        return (
            <React.Fragment>
                <Pagination.List
                    loading={loading}
                    pageRenderer={(page: Page<Application>) =>
                        (<Table basic="very">
                            <Table.Header>
                                <Table.Row>
                                    <Table.HeaderCell>
                                        Application Name
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
                    page={applications}
                    onItemsPerPageChanged={(size) => updateApplicationsPerPage(size)}
                    onPageChanged={(page) => toPage(page)}
                    onRefresh={() => null}
                    onErrorDismiss={() => null}
                />
            </React.Fragment>);
    }
}

const ApplicationsList = ({ applications }: { applications: Application[] }) => {
    let applicationsList = applications.map((app, index) =>
        <SingleApplication key={index} app={app} />
    );
    return (
        <Table.Body>
            {applicationsList}
        </Table.Body>)
};

const SingleApplication = ({ app }: { app: Application }) => {
    return (
        <Table.Row>
            <Table.Cell title={app.description.description}>{app.description.title}</Table.Cell>
            <Table.Cell title={app.description.description}>{app.description.info.version}</Table.Cell>
            <Table.Cell>
                <Link to={`/applications/${app.description.info.name}/${app.description.info.version}/`}>
                    <Button>Run</Button>
                </Link>
            </Table.Cell>
        </Table.Row>
    )
};

const mapDispatchToProps = (dispatch): ApplicationsOperations => ({
    updatePageTitle: () => dispatch(updatePageTitle("Applications")),
    setLoading: (loading: boolean) => dispatch(setLoading(loading)),
    fetchApplications: () => dispatch(fetchApplications()),
    updateApplications: (applications: Page<Application>) => dispatch(updateApplications(applications)),
    toPage: (pageNumber: number) => dispatch(toPage(pageNumber)),
    updateApplicationsPerPage: (applicationsPerPage: number) => dispatch(updateApplicationsPerPage(applicationsPerPage))
})
const mapStateToProps = ({ applications }): ApplicationsStateProps => applications;

export default connect(mapStateToProps, mapDispatchToProps)(Applications);