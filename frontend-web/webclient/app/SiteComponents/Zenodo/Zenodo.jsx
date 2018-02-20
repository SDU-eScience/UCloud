import React from "react";
import {Button} from "react-bootstrap";
import {Cloud} from "../../../authentication/SDUCloudObject"
import SectionContainerCard from "../SectionContainerCard";

class ZenodoHome extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            publications: [],
        }
    }

    componentWillMount() {
        //Cloud.get(`/zenodo/publications/`)
    }

    ZenodoRedirect() {
        //Cloud.post(`/zenodo/request?returnTo=${window.location.href}`)
        Cloud.get(`/../mock-api/mock_zenodo_auth.json?returnTo=${window.location.href}`).then((data) => {
            const redirectTo = data.redirectTo;
            if (redirectTo) window.location.href = redirectTo;
        })
    }

    render() {
        if (!this.state.publications.length) {
            return (<NotLoggedIn logIn={this.ZenodoRedirect}/>);
        } else {
            return (null);
        }
    }
}

function NotLoggedIn(props) {
    return (
        <SectionContainerCard>
            <h1>You are not connected to Zenodo</h1>
            <Button onClick={() => props.logIn()}>Connect to Zenodo</Button>
        </SectionContainerCard>
    );
}


export default ZenodoHome;