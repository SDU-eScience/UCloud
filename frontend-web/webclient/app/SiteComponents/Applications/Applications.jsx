import React from 'react';
import {BallPulseLoading} from '../LoadingIcon/LoadingIcon';
import {Link} from "react-router-dom";
import {PaginationButtons, EntriesPerPageSelector} from "../Pagination";
import {Table, Button} from 'react-bootstrap';
import {Card} from "../Cards";
import pubsub from "pubsub-js";
import {Cloud} from "../../../authentication/SDUCloudObject";
import PromiseKeeper from "../../PromiseKeeper";

class Applications extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            applications: [],
            currentPage: 0,
            applicationsPerPage: 10,
            lastSorting: {
                name: "name",
                asc: true,
            }
        };
        this.sortByName = this.sortByName.bind(this);
        this.sortByVisibility = this.sortByVisibility.bind(this);
        this.sortByVersion = this.sortByVersion.bind(this);
        this.sortingIcon = this.sortingIcon.bind(this);
        this.toPage = this.toPage.bind(this);
        this.getCurrentApplications = this.getCurrentApplications.bind(this);
        this.handlePageSizeSelection = this.handlePageSizeSelection.bind(this);
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getApplications();
    }

    getApplications() {
        this.setState({loading: true});
        this.state.promises.makeCancelable(Cloud.get("/hpc/apps")).promise.then(req => {
            this.setState(() => ({
                applications: req.response.sort((a, b) => {
                    return a.prettyName.localeCompare(b.prettyName)
                }),
                loading: false
            }));
        });
    }

    sortingIcon(name) {
        if (this.state.lastSorting.name === name) {
            return this.state.lastSorting.asc ? "ion-chevron-down" : "ion-chevron-up";
        }
        return "";
    }

    sortByVisibility() {
        let apps = this.state.applications.slice();
        let asc = !this.state.lastSorting.asc;
        let order = asc ? 1 : -1;
        apps.sort((a, b) => {
            return (a.isPrivate - b.isPrivate) * order;
        });
        this.setState(() => ({
            applications: apps,
            lastSorting: {
                name: "visibility",
                asc: asc,
            },
        }));
    }

    sortByName() {
        let apps = this.state.applications.slice();
        let asc = !this.state.lastSorting.asc;
        let order = asc ? 1 : -1;
        apps.sort((a, b) => {
            return a.prettyName.localeCompare(b.prettyName) * order;
        });
        this.setState(() => ({
            applications: apps,
            lastSorting: {
                name: "name",
                asc: asc,
            },
        }));
    }

    sortByVersion() {
        let apps = this.state.applications.slice();
        let asc = !this.state.lastSorting.asc;
        let order = asc ? 1 : -1;
        apps.sort((a, b) => {
            return a.info.version.localeCompare(b.info.version) * order;
        });
        this.setState(() => ({
            applications: apps,
            lastSorting: {
                name: "version",
                asc: asc,
            },
        }));
    }

    getCurrentApplications() {
        let applicationsPerPage = this.state.applicationsPerPage;
        let currentPage = this.state.currentPage;
        return this.state.applications.slice(currentPage * applicationsPerPage, currentPage * applicationsPerPage + applicationsPerPage);
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

    toPage(n) {
        this.setState(() => ({
            currentPage: n,
        }));
    }

    handlePageSizeSelection(newPageSize) {
        this.setState(() => ({
            applicationsPerPage: newPageSize,
        }));
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    render() {
        return (
            <section>
                <div className="container" style={{ "marginTop": "60px" }}>
                    <div>
                        <BallPulseLoading loading={!this.state.applications.length}/>
                        <Card>
                            <div className="card-body">
                                <Table responsive className="table table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th onClick={() => this.sortByVisibility()}><span className="text-left">Visibility<span
                                            className={"pull-right " + this.sortingIcon("visibility")}/></span></th>
                                        <th onClick={() => this.sortByName()}><span className="text-left">Application Name<span
                                            className={"pull-right " + this.sortingIcon("name")}/></span></th>
                                        <th onClick={() => this.sortByVersion()}><span
                                            className="text-left">Version<span
                                            className={"pull-right " + this.sortingIcon("version")}/></span></th>
                                        <th/>
                                    </tr>
                                    </thead>
                                    <ApplicationsList applications={this.getCurrentApplications()}/>
                                </Table>
                            </div>
                        </Card>
                        <PaginationButtons
                            toPage={this.toPage}
                            currentPage={this.state.currentPage}
                            totalPages={Math.ceil(this.state.applications.length / this.state.applicationsPerPage)}
                        />
                        <EntriesPerPageSelector entriesPerPage={this.state.applicationsPerPage}
                                                handlePageSizeSelection={this.handlePageSizeSelection}/> Applications
                        per page
                    </div>
                </div>
            </section>)
    }
}

const ApplicationsList = (props) => {
    let applications = props.applications.slice();
    let i = 0;
    let applicationsList = applications.map(app =>
        <SingleApplication key={i++} app={app}/>
    );
    return (
        <tbody>
        {applicationsList}
        </tbody>)
};

const SingleApplication = (props) => (
    <tr className="gradeA row-settings">
        <PrivateIcon isPrivate={props.app.info.isPrivate}/>
        <td title={props.app.description}>{props.app.prettyName}</td>
        <td title={props.app.description}>{props.app.info.version}</td>
        <th>
            <Link to={`/applications/${props.app.info.name}/${props.app.info.version}/`}>
                <Button className="btn btn-info">Run</Button>
            </Link>
        </th>
    </tr>
);

const PrivateIcon = (props) => {
    if (props.isPrivate) {
        return (
            <td title="The app is private and can only be seen by the creator and people it was shared with">
                <em className="ion-locked"/></td>
        )
    } else {
        return (<td title="The application is openly available for everyone"><em className="ion-unlocked"/></td>)
    }
};

export default Applications