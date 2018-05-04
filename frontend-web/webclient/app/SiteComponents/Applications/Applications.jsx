import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Link } from "react-router-dom";
import { PaginationButtons, EntriesPerPageSelector, Container, Card } from "../Pagination";
import { Table, Button } from "semantic-ui-react";
import { getSortingIcon } from "../../UtilityFunctions";
import { connect } from "react-redux";
import { fetchApplications, setLoading, toPage, updateApplicationsPerPage, updateApplications } from "../../Actions/Applications";
import { updatePageTitle } from "../../Actions/Status";
import "../Styling/Shared.scss";

class Applications extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            lastSorting: {
                name: "name",
                asc: true,
            }
        };
        this.sortByString = this.sortByString.bind(this);
        this.sortByNumber = this.sortByNumber.bind(this);
        const { dispatch } = this.props;
        dispatch(updatePageTitle("Applications"));
        dispatch(setLoading(true));
        dispatch(fetchApplications());
    }

    sortByNumber(name) {
        let apps = this.props.applications.slice();
        let asc = !this.state.lastSorting.asc;
        let order = asc ? 1 : -1;
        apps.sort((a, b) => {
            return (a[name] - b[name]) * order;
        });
        this.setState(() => ({
            lastSorting: {
                name,
                asc
            }
        }));
        this.props.dispatch(updateApplications(apps));
    }

    sortByString(name) {
        let apps = this.props.applications.slice();
        let asc = !this.state.lastSorting.asc;
        let order = asc ? 1 : -1;
        apps.sort((a, b) => {
            return a.info[name].localeCompare(b.info[name]) * order;
        });
        this.setState(() => ({
            lastSorting: {
                name,
                asc
            }
        }));
        this.props.dispatch(updateApplications(apps));
    }

    render() {
        const { applications, loading, applicationsPerPage, currentApplicationsPage, dispatch } = this.props;
        const currentlyShownApplications = applications.slice(currentApplicationsPage * applicationsPerPage, currentApplicationsPage * applicationsPerPage + applicationsPerPage);
        const totalPages = Math.max(Math.ceil(applications.length / applicationsPerPage), 0);
        return (
            <section style={{ padding: "15px 15px 15px 15px"}}>
                <BallPulseLoading loading={loading} />
                <Table>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell onClick={() => this.sortByNumber("visibility")} textAlign="left">
                                Visibility <span className={`pull-right ${getSortingIcon(this.state.lastSorting, "visibility")}`} />
                            </Table.HeaderCell>
                            <Table.HeaderCell onClick={() => this.sortByString("name")} textAlign="left">
                                Application Name <span className={`pull-right ${getSortingIcon(this.state.lastSorting, "name")}`} />
                            </Table.HeaderCell>
                            <Table.HeaderCell onClick={() => this.sortByString("version")} textAlign="left">
                                Version<span className={`pull-right ${getSortingIcon(this.state.lastSorting, "version")}`} />
                            </Table.HeaderCell>
                            <Table.HeaderCell />
                        </Table.Row>
                    </Table.Header>
                    <ApplicationsList applications={currentlyShownApplications} />
                    <Table.Footer>
                        <Table.Row>
                            <Table.Cell>
                                <PaginationButtons
                                    loading={loading}
                                    toPage={(page) => dispatch(toPage(page))}
                                    currentPage={currentApplicationsPage}
                                    totalPages={totalPages}
                                />
                                <EntriesPerPageSelector
                                    entriesPerPage={applicationsPerPage}
                                    onChange={(size) => dispatch(updateApplicationsPerPage(size))}
                                    totalPages={totalPages}
                                >
                                    {" Applications per page"}
                                </EntriesPerPageSelector>
                            </Table.Cell>
                        </Table.Row>
                    </Table.Footer>
                </Table>
            </section >);
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
        <PrivateIcon isPrivate={app.info.isPrivate} />
        <Table.Cell title={app.description}>{app.prettyName}</Table.Cell>
        <Table.Cell title={app.description}>{app.info.version}</Table.Cell>
        <Table.Cell>
            <Link to={`/applications/${app.info.name}/${app.info.version}/`}>
                <Button>Run</Button>
            </Link>
        </Table.Cell>
    </Table.Row>
);

const PrivateIcon = ({ isPrivate }) =>
    isPrivate ? (
        <Table.Cell title="The app is private and can only be seen by the creator and people it was shared with">
            <em className="ion-locked" />
        </Table.Cell>
    ) : (
            <Table.Cell title="The application is openly available for everyone"><em className="ion-unlocked" /></Table.Cell>
        );

const mapStateToProps = (state) => {
    return { applications, loading, applicationsPerPage, currentApplicationsPage } = state.applications;
}

export default connect(mapStateToProps)(Applications);