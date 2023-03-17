import * as React from "react";
import {useXTerm} from "@/Applications/Jobs/xterm";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {useEffect, useState} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useParams} from "react-router";
import {Box, Button} from "@/ui-components";
import {isLightThemeStored, shortUUID, useNoFrame} from "@/UtilityFunctions";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {TermAndShellWrapper} from "@/Applications/Jobs/TermAndShellWrapper";
import {bulkRequestOf, bulkResponseOf} from "@/DefaultObjects";
import {default as JobsApi, InteractiveSession} from "@/UCloud/JobsApi";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {BulkResponse} from "@/UCloud/index";

export const Shell: React.FunctionComponent = () => {
    const {termRef, terminal, fitAddon} = useXTerm();
    const params = useParams<{jobId: string, rank: string}>();
    const jobId = params.jobId!;
    const rank = params.rank!;
    const [sessionResp, openSession] = useCloudAPI<BulkResponse<InteractiveSession>>(
        {noop: true},
        bulkResponseOf()
    );

    const [closed, setClosed] = useState<boolean>(false);
    const [reconnect, setReconnect] = useState<number>(0);
    useNoFrame();
    useTitle(`Job ${shortUUID(jobId)} [Node: ${parseInt(rank, 10) + 1}]`);

    useEffect(() => {
        openSession(JobsApi.openInteractiveSession(
            bulkRequestOf({id: jobId, rank: parseInt(rank, 10), sessionType: "SHELL"}))
        );
    }, [jobId, rank]);

    const sessionWithProvider = sessionResp.data.responses.length > 0 ? sessionResp.data.responses[0] : null;
    let sessionIdentifier: string | null = null;
    if (sessionWithProvider?.session?.type === "shell") {
        sessionIdentifier = sessionWithProvider.session.sessionIdentifier;
    }

    useEffect(() => {
        if (sessionIdentifier === null || sessionWithProvider === null) return;
        if (termRef.current === null) return;
        setClosed(false);

        const wsConnection = WSFactory.open(
                `${sessionWithProvider.providerDomain}/ucloud/${sessionWithProvider.providerId}/websocket?session=${sessionIdentifier}&usernameHint=${b64EncodeUnicode(Client.activeUsername!)}`,
            {
                reconnect: false,
                includeAuthentication: false,
                init: async conn => {
                    setClosed(false);
                    await conn.subscribe({
                        call: `jobs.compute.${sessionWithProvider.providerId}.shell.open`,
                        payload: {
                            type: "initialize",
                            sessionIdentifier,
                            cols: terminal.cols,
                            rows: terminal.rows
                        },
                        handler: message => {
                            if (message.type === "message") {
                                const payload = message.payload as {data: string} | any;
                                if ("data" in payload) {
                                    terminal.write(payload.data);
                                }
                            }
                        }
                    });
                },
                onClose: () => {
                    setClosed(true);
                },
            });

        terminal.onData((data) => {
            wsConnection.call({
                call: `jobs.compute.${sessionWithProvider.providerId}.shell.open`,
                payload: {
                    type: "input",
                    data
                }
            });
        });

        terminal.onResize((dims) => {
            wsConnection.call({
                call: `jobs.compute.${sessionWithProvider.providerId}.shell.open`,
                payload: {
                    type: "resize",
                    ...dims
                }
            });
        });

        fitAddon.fit();

        const resizeListener = (): void => {
            fitAddon.fit();
        };
        window.addEventListener("resize", resizeListener);

        return () => {
            wsConnection.close();
            window.removeEventListener("resize", resizeListener);
        };
    }, [termRef.current, sessionIdentifier, reconnect]);

    return <TermAndShellWrapper className={isLightThemeStored() ? "light" : "dark"} addPadding={true}>
        {!closed ? null : (
            // NOTE(Dan): Theme cannot change in practice, as a result we can safely use the stored value
            <div className={`warn`}>
                <Box flexGrow={1}>Your connection has been closed!</Box>
                <Button ml={"16px"} onClick={() => {
                    setReconnect(reconnect + 1);
                }}>Reconnect</Button>
            </div>
        )}

        <div className={"contents"} ref={termRef} />
    </TermAndShellWrapper>;
};

export default Shell;
