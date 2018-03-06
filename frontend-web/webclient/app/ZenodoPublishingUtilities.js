import React from "react";
import {Cloud} from "../authentication/SDUCloudObject";
import SectionContainerCard from "./SiteComponents/SectionContainerCard";
import {Button} from "react-bootstrap";

function ZenodoRedirect() {
    Cloud.post(`/zenodo/request?returnTo=${window.location.href}`).then((data) => {
        const redirectTo = data.response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    });
}

export function NotConnectedToZenodo(props) {
    return (
        <SectionContainerCard>
            <h1>You are not connected to Zenodo</h1>
            <Button onClick={() => ZenodoRedirect()}>Connect to Zenodo</Button>
        </SectionContainerCard>)
}