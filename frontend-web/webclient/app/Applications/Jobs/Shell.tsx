import * as React from "react";
import {useXTerm} from "Applications/Jobs/xterm";
import {WSFactory} from "Authentication/HttpClientInstance";
import {useEffect, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {useParams} from "react-router";
import {Box, Button} from "ui-components";
import {isLightThemeStored, shortUUID, useNoFrame} from "UtilityFunctions";
import styled from "styled-components";
import {useTitle} from "Navigation/Redux/StatusActions";

const ShellWrapper = styled.div`
    display: flex;
    height: 100vh;
    width: 100vw;
    flex-direction: column;
    padding: 16px;
    
    &.light {
        background: #ffffff;
    }
    
    &.dark {
        background: #282a36;
    }
    
    .term {
        width: 100%;
        height: 100%;
    }
    
    .warn {
        position: fixed;
        bottom: 0;
        left: 0;
        z-index: 1000000;
        width: 100vw;
        display: flex;
        padding: 16px;
        align-items: center;
        background: black;
        color: white;
    }
`;

export const Shell: React.FunctionComponent = () => {
    return null;
    /*
    const {termRef, terminal, fitAddon} = useXTerm();
    const {jobId, rank} = useParams<{ jobId: string, rank: string }>();
    const [path, fetchPath] = useCloudAPI<QueryShellParametersResponse>(
        queryShellParameters({jobId, rank: parseInt(rank, 10)}),
        {path: ""}
    );
    const [closed, setClosed] = useState<boolean>(false);
    const [reconnect, setReconnect] = useState<number>(0);
    useNoFrame();
    useTitle(`Job ${shortUUID(jobId)} [Rank: ${parseInt(rank, 10) + 1}]`);

    useEffect(() => {
        fetchPath(queryShellParameters({jobId, rank: parseInt(rank, 10)}));
    }, [jobId, rank]);

    useEffect(() => {
        const wsPath = path.data.path.replace("/api/", "/");
        if (wsPath === "") return;
        if (termRef.current === null) return;
        setClosed(false);

        const wsConnection = WSFactory.open(wsPath, {
            reconnect: false,
            init: async conn => {
                await conn.subscribe({
                    call: "app.compute.kubernetes.shell.open",
                    payload: {
                        type: "initialize",
                        jobId,
                        rank,
                        cols: terminal.cols,
                        rows: terminal.rows
                    },
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as { data: string } | any;
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
                call: "app.compute.kubernetes.shell.open",
                payload: {
                    type: "input",
                    data
                }
            });
        });

        terminal.onResize((dims) => {
            wsConnection.call({
                call: "app.compute.kubernetes.shell.open",
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
    }, [termRef.current, path.data.path, reconnect]);

    return <ShellWrapper className={isLightThemeStored() ? "light" : "dark"}>
        {!closed ? null : (
            // NOTE(Dan): Theme cannot change in practice, as a result we can safely use the stored value
            <Box className={`warn`}>
                <Box flexGrow={1}>Your connection has been closed!</Box>
                <Button ml={"16px"} onClick={() => {
                    setReconnect(reconnect + 1);
                }}>Reconnect</Button>
            </Box>
        )}

        <div className={"term"} ref={termRef}/>
    </ShellWrapper>;
     */
};
