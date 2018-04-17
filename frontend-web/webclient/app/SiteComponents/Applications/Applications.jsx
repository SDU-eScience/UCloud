import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { Link } from "react-router-dom";
import { PaginationButtons, EntriesPerPageSelector } from "../Pagination";
import { Table, Button } from "react-bootstrap";
import { Card } from "../Cards";
import { getSortingIcon } from "../../UtilityFunctions";
import { connect } from "react-redux";
import { fetchApplications, setLoading, toPage, updateApplicationsPerPage, updateApplications } from "../../Actions/Applications";
import { updatePageTitle } from "../../Actions/Status";

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
            <section>
                <div className="container" style={{ "marginTop": "60px" }}>
                    <div>
                        <BallPulseLoading loading={loading} />
                        <Card>
                            <div className="card-body">
                                <Table responsive className="table table-hover mv-lg">
                                    <thead>
                                        <tr>
                                            <th onClick={() => this.sortByNumber("visibility")}><span className="text-left">Visibility<span
                                                className={`pull-right ${getSortingIcon(this.state.lastSorting, "visibility")}`} /></span></th>
                                            <th onClick={() => this.sortByString("name")}><span className="text-left">Application Name<span
                                                className={`pull-right ${getSortingIcon(this.state.lastSorting, "name")}`} /></span></th>
                                            <th onClick={() => this.sortByString("version")}>
                                                <span className="text-left">Version
                                                    <span className={`pull-right ${getSortingIcon(this.state.lastSorting, "version")}`} />
                                                </span>
                                            </th>
                                            <th />
                                        </tr>
                                    </thead>
                                    <ApplicationsList applications={currentlyShownApplications} />
                                </Table>
                            </div>
                        </Card>
                        <PaginationButtons
                            loading={loading}
                            toPage={(page) => dispatch(toPage(page))}
                            currentPage={currentApplicationsPage}
                            totalPages={totalPages}
                        />
                        <EntriesPerPageSelector
                            entriesPerPage={applicationsPerPage}
                            handlePageSizeSelection={(size) => dispatch(updateApplicationsPerPage(size))}
                            totalPages={totalPages}
                        >
                            Applications per page
                        </EntriesPerPageSelector>
                    </div>
                </div>
            </section>);
    }
}

const ApplicationsList = ({ applications }) => {
    let applicationsList = applications.map((app, index) =>
        <SingleApplication key={index} app={app} />
    );
    return (
        <tbody>
            {applicationsList}
        </tbody>)
};

const SingleApplication = ({ app }) => (
    <tr className="gradeA row-settings">
        <PrivateIcon isPrivate={app.info.isPrivate} />
        <td title={app.description}>{app.prettyName}</td>
        <td title={app.description}>{app.info.version}</td>
        <th>
            <Link to={`/applications/${app.info.name}/${app.info.version}/`}>
                <Button className="btn btn-info">Run</Button>
            </Link>
        </th>
    </tr>
);

const PrivateIcon = ({ isPrivate }) =>
    isPrivate ? (
        <td title="The app is private and can only be seen by the creator and people it was shared with">
            <em className="ion-locked" />
        </td>
    ) : (
        <td title="The application is openly available for everyone"><em className="ion-unlocked" /></td>
    );

const mapStateToProps = (state) => {
    return { applications, loading, applicationsPerPage, currentApplicationsPage } = state.applications;
}

export default connect(mapStateToProps)(Applications);