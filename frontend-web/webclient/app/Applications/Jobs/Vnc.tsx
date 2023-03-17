import * as React from "react";
import * as UCloud from "@/UCloud";
import jobs = UCloud.compute.jobs;
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useCloudAPI} from "@/Authentication/DataHook";
import {errorMessageOrDefault, isAbsoluteUrl, shortUUID, useNoFrame} from "@/UtilityFunctions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {useParams} from "react-router";
import {useCallback, useEffect, useLayoutEffect, useState} from "react";
import {compute} from "@/UCloud";
import JobsOpenInteractiveSessionResponse = compute.JobsOpenInteractiveSessionResponse;
import RFB from "@novnc/novnc/core/rfb";
import * as VncLog from '@novnc/novnc/core/util/logging.js';
import {Box, Button} from "@/ui-components";
import {TermAndShellWrapper} from "@/Applications/Jobs/TermAndShellWrapper";
import {bulkRequestOf} from "@/DefaultObjects";

interface ConnectionDetails {
    url: string;
    password?: string
}

export const Vnc: React.FunctionComponent = () => {
    const params = useParams<{jobId: string, rank: string}>();
    const jobId = params.jobId!;
    const rank = params.rank!
    const [isConnected, setConnected] = React.useState(false);
    const [sessionResp] = useCloudAPI<JobsOpenInteractiveSessionResponse | null>(
        jobs.openInteractiveSession(bulkRequestOf({sessionType: "VNC", id: jobId, rank: parseInt(rank, 10)})),
        null
    );

    const [connectionDetails, setConnectionDetails] = useState<ConnectionDetails | null>(null);
    useTitle(`Remote Desktop: ${shortUUID(jobId)} [Node: ${parseInt(rank, 10) + 1}]`);
    useNoFrame();

    useEffect(() => {
        if (sessionResp.data !== null && sessionResp.data.responses.length > 0) {
            const {providerDomain, session} = sessionResp.data.responses[0];
            if (session.type !== "vnc") {
                snackbarStore.addFailure(
                    "Unexpected response from UCloud. Unable to open remote desktop!",
                    false
                );
                return;
            }

            const url = (isAbsoluteUrl(session.url) ? session.url : providerDomain + session.url)
                .replace("http://", "ws://")
                .replace("https://", "wss://");
            const password = session.password ? session.password : undefined;
            setConnectionDetails({url, password});
        }
    }, [sessionResp.data]);

    const connect = useCallback(() => {
        if (connectionDetails === null) return;
        VncLog.initLogging("warn");

        try {
            const rfb = new RFB(
                document.getElementsByClassName("contents")[0],
                connectionDetails.url, {
                credentials: {password: connectionDetails.password},
                wsProtocols: ["binary"]
            }
            );

            rfb.scaleViewport = true;
            rfb.addEventListener("disconnect", () => setConnected(false));
            setConnected(true);
        } catch (e) {
            console.warn(e);
            snackbarStore.addFailure(
                errorMessageOrDefault(e, "An error occurred connecting to the remote desktop"),
                false
            );
        }
    }, [connectionDetails]);

    useLayoutEffect(() => {
        connect();
    }, [connectionDetails]);

    return <TermAndShellWrapper addPadding={false}>
        {isConnected || sessionResp.data == null ? null : (
            <div className={`warn`}>
                <Box flexGrow={1}>Your connection has been closed!</Box>
                <Button ml={"16px"} onClick={connect}>Reconnect</Button>
            </div>
        )}

        <div className={"contents"} />
    </TermAndShellWrapper>;
};

export default Vnc;
