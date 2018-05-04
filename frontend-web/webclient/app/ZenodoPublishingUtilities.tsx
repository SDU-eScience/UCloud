import * as React from "react";
import { Cloud } from "../authentication/SDUCloudObject";
import { SectionContainerCard } from "./SiteComponents/SectionContainerCard";
import { Button } from "semantic-ui-react";

const ZenodoRedirect = () =>
    Cloud.post(`/zenodo/request?returnTo=${window.location.href}`).then(({ response }) => {
        const redirectTo = response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    });

export const NotConnectedToZenodo = (): React.ReactNode => (
    <SectionContainerCard>
        <h1>You are not connected to Zenodo</h1>
        <Button onClick={() => ZenodoRedirect()}>Connect to Zenodo</Button>
    </SectionContainerCard>
);