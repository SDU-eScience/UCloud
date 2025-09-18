import * as React from "react";
import * as UCloud from "@/UCloud";
import jobs = UCloud.compute.jobs;
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useCloudAPI} from "@/Authentication/DataHook";
import {errorMessageOrDefault, isAbsoluteUrl, shortUUID} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import {useParams} from "react-router";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import {compute} from "@/UCloud";
import JobsOpenInteractiveSessionResponse = compute.JobsOpenInteractiveSessionResponse;
import RFB from "@novnc/novnc/lib/rfb";
import {initLogging} from '@novnc/novnc/lib/util/logging';
import {Box, Button} from "@/ui-components";
import {TermAndShellWrapper} from "@/Applications/Jobs/TermAndShellWrapper";
import {bulkRequestOf} from "@/UtilityFunctions";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

interface ConnectionDetails {
    url: string;
    password?: string
}

export const Vnc: React.FunctionComponent = () => {
    const params = useParams<{ jobId: string, rank: string }>();
    const jobId = params.jobId!;
    const rank = params.rank!;
    const [isConnected, setConnected] = React.useState(false);
    const [sessionResp] = useCloudAPI<JobsOpenInteractiveSessionResponse | null>(
        jobs.openInteractiveSession(bulkRequestOf({sessionType: "VNC", id: jobId, rank: parseInt(rank, 10)})),
        null
    );

    const pasteEventsToCancel = useRef<EventListener[]>([]);

    const [connectionDetails, setConnectionDetails] = useState<ConnectionDetails | null>(null);
    usePage(`Remote Desktop: ${shortUUID(jobId)} [Node: ${parseInt(rank, 10) + 1}]`, SidebarTabId.APPLICATIONS);

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

        return () => {
            for (const listener of pasteEventsToCancel.current) {
                document.removeEventListener("paste", listener);
            }
        }
    }, [sessionResp.data]);

    const connect = useCallback(() => {
        if (connectionDetails === null) return;
        initLogging("debug");

        try {
            const mountPoint = document.getElementsByClassName("contents")[0];
            for (const listener of pasteEventsToCancel.current) {
                document.removeEventListener("paste", listener);
                mountPoint.removeEventListener("paste", listener);
            }

            pasteEventsToCancel.current = [];

            const rfb = new RFB(
                mountPoint,
                connectionDetails.url,
                {
                    credentials: {password: connectionDetails.password},
                    wsProtocols: ["binary"]
                }
            );

            rfb.scaleViewport = true;

            const listener = (ev) => onLocalClipboard(rfb, ev);
            pasteEventsToCancel.current.push(listener);

            rfb.addEventListener("disconnect", () => {
                setConnected(false);
                document.removeEventListener("paste", listener);
                mountPoint.removeEventListener("paste", listener);
                pasteEventsToCancel.current = pasteEventsToCancel.current.filter(it => it !== listener);
            });

            rfb.addEventListener("clipboard", (ev) => onRemoteClipboard(ev));
            document.addEventListener("paste", listener);
            mountPoint.addEventListener("paste", listener);

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

        <div className={"contents"}/>
    </TermAndShellWrapper>;
};

// Clipboard helpers
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Handle text copied from the remote host and try to place it into the
 * local clipboard. Falls back to a hidden `<textarea>` for browsers that
 * block the Clipboard API without a user gesture.
 */
async function onRemoteClipboard(ev: CustomEvent): Promise<void> {
    const text = (ev.detail as { text?: string }).text ?? "";
    if (!text) return;

    try {
        await navigator.clipboard.writeText(text);
    } catch {
        const el = document.createElement("textarea");
        el.value = text;
        el.style.position = "fixed";
        el.style.opacity = "0";
        document.body.appendChild(el);
        el.select();
        document.execCommand("copy");
        document.body.removeChild(el);
    }
}

/**
 * Forward locally pasted text to the remote host.
 */
function onLocalClipboard(rfb: RFB, ev: ClipboardEvent): void {
    const text = ev.clipboardData?.getData("text") ?? "";
    console.log("vnc local clipboard paste");
    if (text) {
        rfb.clipboardPasteFrom(text);
    }
}

export default Vnc;
