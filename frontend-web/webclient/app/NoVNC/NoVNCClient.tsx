import RFB from "@novnc/novnc/core/rfb";
import {Client} from "Authentication/HttpClientInstance";
import {MainContainer} from "MainContainer/MainContainer";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {useHistory, useLocation} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Button, Heading, Icon, OutlineButton} from "ui-components";
import {cancelJobDialog, cancelJobQuery} from "Utilities/ApplicationUtilities";
import {errorMessageOrDefault, requestFullScreen} from "UtilityFunctions";

function NoVNCClient(): JSX.Element | null {
    const [isConnected, setConnected] = React.useState(false);
    const [isCancelled, setCancelled] = React.useState(false);
    const [rfb, setRFB] = React.useState<RFB | undefined>(undefined);
    const [password, setPassword] = React.useState("");
    const [path, setPath] = React.useState("");
    const promiseKeeper = usePromiseKeeper();
    const jobId = new URLSearchParams(useLocation().search).get("jobId");
    const history = useHistory();

    React.useEffect(() => {
        if (jobId != null) {
            promiseKeeper.makeCancelable(Client.get(`/hpc/jobs/query-vnc/${jobId}`)).promise.then(it => {
                setPassword(it.response.password);
                setPath(it.response.path);
            }).catch(() => undefined);
        }
        return () => {
            if (isConnected) rfb!.disconnect();
        };
    }, []);

    function onConnect(): void {
        try {
            const protocol = window.location.protocol === "http:" ? "ws:" : "wss:";
            const rfbClient = new RFB(document.getElementsByClassName("noVNC")[0], `${protocol}//${window.location.host}${path}`, {
                credentials: {password}
            });

            /* FIXME: Doesn't seem to work properly, e.g. if connection fails */
            rfbClient.addEventListener("disconnect", () => setConnected(false));
            /* FIXME END */
            setRFB(rfbClient);
            setConnected(true);
        } catch (e) {
            console.warn(e);
            snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred connecting"), false);
        }
    }

    function disconnect(): void {
        rfb?.disconnect();
        setConnected(false);
    }

    function toFullScreen(): void {
        requestFullScreen(
            document.getElementsByClassName("noVNC")[0]!,
            () => snackbarStore.addFailure("Fullscreen is not supported for this browser.", false)
        );
    }

    function cancelJob(): void {
        if (!jobId) return;
        cancelJobDialog({
            jobId,
            onConfirm: async () => {
                try {
                    await Client.delete(cancelJobQuery, {jobId});
                    snackbarStore.addSuccess("Job has been terminated", false);
                    setCancelled(true);
                    history.push(`/applications/results/${jobId}`);
                } catch (e) {
                    snackbarStore.addFailure(errorMessageOrDefault(e, "An error occurred cancelling the job."), false);
                }
            }
        });
    }

    const mountNode = <div className="noVNC" />;
    const main = (
        <>
            <Heading mb="5px">
                {isConnected ? <OutlineButton ml="15px" mr="10px" onClick={disconnect}>Disconnect</OutlineButton> : (
                    <div>
                        <Button disabled={isCancelled} ml="15px" onClick={onConnect}>
                            Connect
                        </Button>
                        {isCancelled ? null : (
                            <Button ml="8px" color="red" onClick={cancelJob}>
                                Cancel Job
                            </Button>
                        )}
                    </div>
                )}
                {isConnected ? <ExpandingIcon name="fullscreen" onClick={toFullScreen} /> : null}
            </Heading>
            {mountNode}
        </>
    );

    return <MainContainer main={main} />;
}

const ExpandingIcon = styled(Icon)`
    &:hover {
        transform: scale(1.02);
    }
`;

export default NoVNCClient;
