import * as React from "react";
import { Cloud } from "../authentication/SDUCloudObject";
import { Button, Segment } from "semantic-ui-react";

const ZenodoRedirect = () =>
    Cloud.post(`/zenodo/request?returnTo=${window.location.href}`).then(({ response }) => {
        const redirectTo = response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    });

export const NotConnectedToZenodo = () => (
    <Segment>
        <h1>You are not connected to Zenodo</h1>
        <Button onClick={() => ZenodoRedirect()}>Connect to Zenodo</Button>
    </Segment>
);