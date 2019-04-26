import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { Button } from "ui-components";
import { Snack, SnackType } from "Snackbar/Snackbars";



const zenodoRedirectPath = (returnTo: string) => `/zenodo/request?returnTo=${encodeURIComponent(returnTo)}`;

const ZenodoRedirect = (addSnack: (snack: Snack) => void) =>
    Cloud.post(zenodoRedirectPath(window.location.href)).then(({ response }) => {
        const redirectTo = response.redirectTo;
        if (redirectTo) window.location.href = redirectTo;
    }).catch(() => addSnack({ message: `An error occurred redirecting to ${window.location.href}`, type: SnackType.Failure }));

export const NotConnectedToZenodo = ({ addSnack }: { addSnack: (snack: Snack) => void }) => (
    <>
        <h1>You are not connected to Zenodo</h1>
        <Button onClick={() => ZenodoRedirect(addSnack)}>Connect to Zenodo</Button>
    </>
);