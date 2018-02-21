import React from "react";
import {Button, Table, ButtonToolbar} from "react-bootstrap";
import {Cloud} from "../../../authentication/SDUCloudObject"
import SectionContainerCard from "../SectionContainerCard";
import {Link} from "react-router-dom";
import {Card} from "../Cards";
import {toLowerCaseAndCapitalize} from "../../UtilityFunctions";
import pubsub from "pubsub-js";
import {BallPulseLoading} from "../LoadingIcon";

class ZenodoHome extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            publications: {},
            connected: false,
            loading: false,
            sorting: {
                lastSorting: "lastUpdate",
                asc: true,
            }
        };
    }

    componentWillMount() {
        pubsub.publish('setPageTitle', "Zenodo Overview");
        this.setState(() => ({
            loading: true,
        }));
        Cloud.get("/../mock-api/mock_zenodo_publications.json").then((publications) => {
            this.setState(() => ({
                connected: publications.connected,
                publications: publications.inProgress,
                loading: false,
            }));
        });
    }

    ZenodoRedirect() {
        //Cloud.post(`/zenodo/request?returnTo=${window.location.href}`)
        Cloud.get(`/../mock-api/mock_zenodo_auth.json?returnTo=${window.location.href}`).then((data) => {
            const redirectTo = data.redirectTo;
            if (redirectTo) window.location.href = redirectTo;
        });
    }

    render() {
        if (!this.state.connected && !this.state.loading) {
            return (<NotLoggedIn logIn={this.ZenodoRedirect}/>);
        } else {
            return (
                <div className="container-fluid">
                    <PublishStatus publications={this.state.publications} loading={this.state.loading}/>
                    <PublishOptions/>
                </div>
            );
        }
    }
}

function PublishStatus(props) {
    let body = null;
    if (props.loading) {
        return (<BallPulseLoading loading={props.loading}/>
        );
    }
    if (!props.publications.length) {
        body = (
            <h3>
                <small className="text-center">No publications found.</small>
            </h3>
        );
    } else {
        body = (
            <div>
                <h3 className="text-center">File upload progress</h3>
                <Table>
                    <thead>
                    <tr>
                        <th>ID</th>
                        <th>Status</th>
                        <th>Actions</th>
                        <th>Last update</th>
                    </tr>
                    </thead>
                    <PublicationList publications={props.publications}/>
                </Table>
            </div>)
    }

    return (
        <div className="col-md-8">
            <Card>
                <div className="card-body">
                    <h3>
                        <small>Connected to Zenodo</small>
                    </h3>
                    {body}
                    <Link to="/ZenodoPublish/">
                        <Button>Create new upload</Button>
                    </Link>
                </div>
            </Card>
        </div>);
}

function PublishOptions(props) { // Remove?
    return (
        <div className="col-md-4">
            <Card>
                <div className="card-body">
                    <ButtonToolbar>
                        <Button onClick={() => console.log("Imagine Zenodo")}>View on Zenodo</Button>
                        <Button onClick={() => console.log("Imagine Zenodo")}>Publish on Zenodo</Button>
                    </ButtonToolbar>
                </div>
            </Card>
        </div>);
}

function NotLoggedIn(props) {
    return (
        <SectionContainerCard>
            <h1>You are not connected to Zenodo</h1>
            <Button onClick={() => props.logIn()}>Connect to Zenodo</Button>
        </SectionContainerCard>
    );
}

function PublicationList(props) {
    if (!props.publications) {
        return null;
    }
    const publicationList = props.publications.map((publication, index) =>
        <tr key={index}>
            <td>{publication.id}</td>
            <td>{toLowerCaseAndCapitalize(publication.status)}</td>
            <td>
                <a href={publication.zenodoAction} target="_blank"><Button>Action!</Button></a>
                <Link to={`/ZenodoInfo/${window.encodeURIComponent(publication.id)}`}><Button>Show
                    More</Button></Link>
            </td>
            <td>{new Date(publication.modifiedAt).toLocaleString()}</td>
        </tr>
    );
    return (
        <tbody>
        {publicationList}
        </tbody>
    );
}

export default ZenodoHome;