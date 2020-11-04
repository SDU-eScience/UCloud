import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {useXTerm} from "Applications/Jobs/xterm";
import {WSFactory} from "Authentication/HttpClientInstance";
import {useEffect, useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {queryShellParameters, QueryShellParametersResponse} from "Applications";
import {useParams} from "react-router";
import Warning from "ui-components/Warning";
import {Box, Button} from "ui-components";
import {shortUUID, useNoFrame} from "UtilityFunctions";
import styled from "styled-components";
import {useTitle} from "Navigation/Redux/StatusActions";

const ShellWrapper = styled.div`
    display: flex;
    height: 100vh;
    width: 100vw;
    flex-direction: column;
    
    .term {
        width: 100%;
        flex-grow: 1;
    }
`;

export const Shell: React.FunctionComponent = () => {
    const {termRef, terminal, fitAddon} = useXTerm();
    const {jobId, rank} = useParams<{ jobId: string, rank: string }>();
    const [path, fetchPath] = useCloudAPI<QueryShellParametersResponse>(
        queryShellParameters({jobId, rank: parseInt(rank, 10)}),
        {path: ""}
    );
    const [closed, setClosed] = useState<boolean>(false);
    const [reconnect, setReconnect] = useState<number>(0);
    useNoFrame();
    useTitle(`Job ${shortUUID(jobId)} [Rank: ${parseInt(rank) + 1}]`);

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

    return <ShellWrapper>
        {!closed ? null : (
            <Box m={"16px"}>
                <Warning>
                    Your connection has been closed!
                    <Button ml={"16px"} onClick={() => {
                        setReconnect(reconnect + 1);
                    }}>Reconnect</Button>
                </Warning>
            </Box>
        )}

        <div style={{width: "100vw", height: "100vh"}} ref={termRef}/>
    </ShellWrapper>;
};
