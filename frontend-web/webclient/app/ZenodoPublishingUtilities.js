import React from "react";
import {Cloud} from "../authentication/SDUCloudObject";
import SectionContainerCard from "./SiteComponents/SectionContainerCard";
import {Button} from "react-bootstrap";

const ZenodoRedirect = () =>
    Cloud.post(`/zenodo/request?returnTo=${window.location.href}`).then(({response}) => {
        const redirectTo = data.response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    });

export const NotConnectedToZenodo = () => (
    <SectionContainerCard>
        <h1>You are not connected to Zenodo</h1>
        <Button onClick={() => ZenodoRedirect()}>Connect to Zenodo</Button>
    </SectionContainerCard>
);