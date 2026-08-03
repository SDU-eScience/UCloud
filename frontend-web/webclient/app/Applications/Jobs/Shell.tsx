import * as React from "react";
import {useXTerm} from "@/Applications/Jobs/XTermLib";
import {Client, WSFactory} from "@/Authentication/HttpClientInstance";
import {useCallback, useEffect, useState} from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useParams} from "react-router-dom";
import {shortUUID} from "@/UtilityFunctions";
import {usePage} from "@/Navigation/Redux";
import {TermAndShellWrapper} from "@/Applications/Jobs/TermAndShellWrapper";
import {bulkResponseOf, bulkRequestOf} from "@/UtilityFunctions";
import {default as JobsApi, InteractiveSession, isJobStateFinal, JobState} from "@/UCloud/JobsApi";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {BulkResponse} from "@/UCloud";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Terminal} from "@xterm/xterm";
import {callAPI} from "@/Authentication/DataHook";
import Icon from "@/ui-components/Icon";
import {UcxSpinner} from "@/UCX/UcxView";

export const INTEGRATED_TERMINAL_RECONNECT_ATTEMPTS = 5;
const JOB_RECONNECT_ATTEMPTS = 1;

export const Shell: React.FunctionComponent = () => {
    const params = useParams<{jobId: string, rank: string}>();
    const jobId = params.jobId!;
    const rank = params.rank!;
    const [sessionResp, openSession] = useCloudAPI<BulkResponse<InteractiveSession>>(
        {noop: true},
        bulkResponseOf()
    );

    usePage(`Job ${shortUUID(jobId)} [Node: ${parseInt(rank, 10) + 1}]`, SidebarTabId.APPLICATIONS);

    const doReconnect = useCallback(() => {
        return openSession(JobsApi.openInteractiveSession(
            bulkRequestOf({id: jobId, rank: parseInt(rank, 10), sessionType: "SHELL"}))
        );
    }, [jobId, openSession, rank]);

    useEffect(() => {
        doReconnect();
    }, [doReconnect]);

    const sessionWithProvider = sessionResp.data.responses.length > 0 ? sessionResp.data.responses[0] : null;
    return <ShellWithSession
        sessionWithProvider={sessionWithProvider}
        connectionError={sessionResp.error?.why}
        reconnect={doReconnect}
        maxReconnectAttempts={JOB_RECONNECT_ATTEMPTS}
        jobId={jobId}
    />
};

export const ShellWithSession: React.FunctionComponent<{
    sessionWithProvider: InteractiveSession | null;
    autofit?: boolean;
    xtermRef?: React.RefObject<Terminal | null>;
    focusedTerminalRef?: React.RefObject<Terminal | null>;
    reconnect: () => void | Promise<void>;
    connectionError?: string;
    maxReconnectAttempts?: number;
    jobId?: string;
    onTitleChange?: (title: string) => void;
}> = ({sessionWithProvider, connectionError, autofit, xtermRef, focusedTerminalRef, reconnect, maxReconnectAttempts = 0, jobId, onTitleChange}) => {
    const {termRef, terminal, fitAddon} = useXTerm({autofit});
    const [closed, setClosed] = useState<boolean>(false);
    const [reconnecting, setReconnecting] = useState(false);
    const [jobEndedMessage, setJobEndedMessage] = useState<string | null>(null);
    const [retryTick, setRetryTick] = useState(0);
    const reconnectTimerRef = React.useRef<number | null>(null);
    const reconnectInFlightRef = React.useRef(false);
    const reconnectAttemptRef = React.useRef(0);
    const jobEndedRef = React.useRef<string | null>(null);
    let sessionIdentifier: string | null = null;
    if (sessionWithProvider?.session?.type === "shell") {
        sessionIdentifier = sessionWithProvider.session.sessionIdentifier;
    }

    const reconnectMessage = jobEndedMessage ?? (jobId ? "The connection to the job was lost." : "The terminal connection was lost.");
    jobEndedRef.current = jobEndedMessage;

    React.useEffect(() => {
        reconnectAttemptRef.current = 0;
        setJobEndedMessage(null);
    }, [jobId]);

    const scheduleReconnect = React.useCallback(() => {
        if (!closed || jobEndedMessage || reconnectInFlightRef.current || reconnectTimerRef.current !== null) return;

        if (reconnectAttemptRef.current >= maxReconnectAttempts) {
            setReconnecting(false);
            return;
        }

        setReconnecting(true);
        reconnectTimerRef.current = window.setTimeout(() => {
            reconnectTimerRef.current = null;
            if (jobEndedRef.current) {
                setReconnecting(false);
                return;
            }
            reconnectInFlightRef.current = true;
            reconnectAttemptRef.current += 1;
            Promise.resolve().then(reconnect).catch(() => undefined).finally(() => {
                reconnectInFlightRef.current = false;
                setRetryTick(current => current + 1);
            });
        }, 500);
    }, [closed, jobEndedMessage, maxReconnectAttempts, reconnect]);

    React.useEffect(() => {
        scheduleReconnect();
    }, [connectionError, retryTick, scheduleReconnect]);

    React.useEffect(() => {
        if (connectionError) {
            setClosed(true);
        }
    }, [connectionError]);

    React.useEffect(() => {
        if (jobEndedMessage && reconnectTimerRef.current !== null) {
            window.clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
    }, [jobEndedMessage]);

    React.useEffect(() => {
        if (!closed || !jobId) return;

        let disposed = false;
        void callAPI(JobsApi.retrieve({id: jobId})).then(job => {
            if (!disposed && isJobStateFinal(job.status.state)) {
                setJobEndedMessage(jobEndedMessageForState(job.status.state));
                setReconnecting(false);
            }
        }).catch(() => undefined);

        return () => {
            disposed = true;
        };
    }, [closed, jobId]);

    const retryNow = React.useCallback(() => {
        if (jobEndedMessage) return;
        reconnectAttemptRef.current = 0;
        setClosed(true);
        setRetryTick(current => current + 1);
    }, [jobEndedMessage]);

    React.useEffect(() => {
        return () => {
            if (reconnectTimerRef.current !== null) {
                window.clearTimeout(reconnectTimerRef.current);
            }
        };
    }, []);

    useEffect(() => {
        if (xtermRef) {
            xtermRef.current = terminal;
        }
    }, [xtermRef, terminal]);

    useEffect(() => {
        if (!focusedTerminalRef) return;
        focusedTerminalRef.current = terminal;
        return () => {
            if (focusedTerminalRef.current === terminal) focusedTerminalRef.current = null;
        };
    }, [focusedTerminalRef, terminal]);

    useEffect(() => {
        if (!onTitleChange) return;

        const titleChange = terminal.onTitleChange(onTitleChange);
        const iconTitleChange = terminal.parser.registerOscHandler(1, title => {
            onTitleChange(title);
            return true;
        });

        return () => {
            titleChange.dispose();
            iconTitleChange.dispose();
        };
    }, [terminal, onTitleChange]);

    useEffect(() => {
        if (sessionIdentifier === null || sessionWithProvider === null) return;
        if (termRef.current === null) return;
        if (reconnectTimerRef.current !== null) {
            window.clearTimeout(reconnectTimerRef.current);
            reconnectTimerRef.current = null;
        }
        setClosed(false);

        let disposed = false;
        const wsConnection = WSFactory.open(
            `${sessionWithProvider.providerDomain}/ucloud/${sessionWithProvider.providerId}/websocket?session=${sessionIdentifier}&usernameHint=${b64EncodeUnicode(Client.activeUsername!)}`,
            {
                reconnect: false,
                includeAuthentication: false,
                init: async conn => {
                    setClosed(false);
                    setReconnecting(false);
                    reconnectAttemptRef.current = 0;
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
                    if (disposed) return;
                    setClosed(true);
                },
            });

        const dataListener = terminal.onData((data) => {
            wsConnection.call({
                call: `jobs.compute.${sessionWithProvider.providerId}.shell.open`,
                payload: {
                    type: "input",
                    data
                }
            });
        });

        const resizeListener = terminal.onResize((dims) => {
            wsConnection.call({
                call: `jobs.compute.${sessionWithProvider.providerId}.shell.open`,
                payload: {
                    type: "resize",
                    ...dims
                }
            });
        });

        fitAddon.fit();

        const windowResizeListener = (): void => {
            fitAddon.fit();
        };
        window.addEventListener("resize", windowResizeListener);

        return () => {
            disposed = true;
            wsConnection.close();
            dataListener.dispose();
            resizeListener.dispose();
            window.removeEventListener("resize", windowResizeListener);
        };
    }, [sessionIdentifier, sessionWithProvider, terminal]);

    return <TermAndShellWrapper addPadding>
        {closed || reconnecting ? (
            <div className={`warn`} role={reconnecting ? "status" : "alert"}>
                {reconnecting ? <UcxSpinner size={22} /> : <Icon name="heroExclamationTriangle" color="warningMain" size={22} />}
                <span style={{flexGrow: 1}}>{reconnecting ? "Reconnecting..." : reconnectMessage}</span>
                {!reconnecting && !jobEndedMessage ? <button type="button" className="reconnect-button" onClick={retryNow}>Reconnect?</button> : null}
            </div>
        ) : null}

        <div className={"contents"} ref={termRef} />
    </TermAndShellWrapper>;
}

function jobEndedMessageForState(state: JobState): string {
    switch (state) {
        case "SUCCESS":
            return "The job has completed.";
        case "FAILURE":
            return "The job has failed.";
        case "EXPIRED":
            return "The job has expired.";
        default:
            return "The connection to the job was lost.";
    }
}

export default Shell;
