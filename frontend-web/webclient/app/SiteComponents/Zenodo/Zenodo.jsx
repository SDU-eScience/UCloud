import React from "react";
import {Button, Table, ButtonToolbar} from "react-bootstrap";
import {Cloud} from "../../../authentication/SDUCloudObject"
import SectionContainerCard from "../SectionContainerCard";
import {PUBLICATIONS} from "../../MockObjects";
import { Link } from "react-router-dom";
import {Card} from "../Cards";
import {toLowerCaseAndCapitalize} from "../../UtilityFunctions";
import pubsub from "pubsub-js";

class ZenodoHome extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            publications: {},
            loading: false,
        };
    }

    componentWillMount() {
        pubsub.publish('setPageTitle', "Zenodo Overview");
        //Cloud.get(`/zenodo/publications/`)
        this.setState(() => ({
            loading: true,
        }));
        setTimeout(() => this.setState(() => ({publications: PUBLICATIONS, loading: false})), 500);
    }

    ZenodoRedirect() {
        //Cloud.post(`/zenodo/request?returnTo=${window.location.href}`)
        Cloud.get(`/../mock-api/mock_zenodo_auth.json?returnTo=${window.location.href}`).then((data) => {
            const redirectTo = data.redirectTo;
            if (redirectTo) window.location.href = redirectTo;
        });
    }

    render() {
        let publications = this.state.publications;
        if (!Object.keys(publications).length && !this.state.loading) {
            return (<NotLoggedIn logIn={this.ZenodoRedirect}/>);
        } else {
            return (
                <div className="container-fluid">
                    <PublishStatus publications={this.state.publications}/>
                    <PublishOptions/>
                </div>
            );
        }
    }
}

function PublishStatus(props) {
    return (
        <div className="col-md-8">
            <Card>
                <div className="card-body">
                    <h3>
                        <small>Connected to Zenodo</small>
                    </h3>
                    <h3 className="text-center">File upload progress</h3>
                    <Table>
                        <thead>
                        <tr>
                            <th>ID</th>
                            <th>Status</th>
                            <th>Actions</th>
                            <th>Info</th>
                        </tr>
                        </thead>
                        <PublicationList publications={props.publications}/>
                    </Table>
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
    if (!props.publications["In Progress"]) { return null; }
    const publicationList = props.publications["In Progress"].map((publication, index) => {
        let button = null;
        if (publication.ZenodoAction === "Something") {

        } else if (publication.ZenodoAction === "Something else") {

        }
        return (
            <tr key={index}>
                <td>{publication.id}</td>
                <td>{toLowerCaseAndCapitalize(publication.status)}</td>
                <td>{button}</td>
                <td><Link to={`/ZenodoInfo/${window.encodeURIComponent(publication.id)}`}><Button>Show More</Button></Link></td>
            </tr>
        )
    });
    return (
        <tbody>
            {publicationList}
        </tbody>
    );
}

export default ZenodoHome;