import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { Button, Segment } from "semantic-ui-react";


const zenodoRedirectPath = (returnTo: string) => `/zenodo/request?returnTo=${returnTo}`;

const ZenodoRedirect = () =>
    Cloud.post(zenodoRedirectPath(window.location.href)).then(({ response }) => {
        const redirectTo = response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    }); // FIXME Error handling

export const NotConnectedToZenodo = () => (
    <Segment>
        <h1>You are not connected to Zenodo</h1>
        <Button onClick={() => ZenodoRedirect()}>Connect to Zenodo</Button>
    </Segment>
);