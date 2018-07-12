import * as React from "react";
import { Link } from "react-router-dom";
import * as Pagination from "../Pagination";
import { Table, Button } from "semantic-ui-react";
import { connect } from "react-redux";
import {
    fetchApplications,
    setLoading,
    updateApplications
} from "./Redux/ApplicationsActions";
import { updatePageTitle } from "../Navigation/Redux/StatusActions";
import "../Styling/Shared.scss";
import { Page, Application } from "../../Types";
import { ApplicationsProps, ApplicationsOperations, ApplicationsStateProps } from ".";

class Applications extends React.Component<ApplicationsProps> {
    constructor(props: ApplicationsProps) {
        super(props);
        props.updatePageTitle();
        props.setLoading(true);
        props.fetchApplications(props.page.pageNumber, props.page.itemsPerPage);
    }

    render() {
        const { page, loading, fetchApplications } = this.props;

        return (
            <React.Fragment>
                <Pagination.List
                    loading={loading}
                    onRefreshClick={() => this.props.fetchApplications(page.pageNumber, page.itemsPerPage)}
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
                    page={page}
                    onItemsPerPageChanged={(size) => fetchApplications(0, size)}
                    onPageChanged={(pageNumber) => fetchApplications(pageNumber, page.itemsPerPage)}
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
    fetchApplications: (pageNumber: number, itemsPerPage: number) => dispatch(fetchApplications(pageNumber, itemsPerPage)),
    updateApplications: (applications: Page<Application>) => dispatch(updateApplications(applications))
});

const mapStateToProps = ({ applications }): ApplicationsStateProps => applications;

export default connect(mapStateToProps, mapDispatchToProps)(Applications);