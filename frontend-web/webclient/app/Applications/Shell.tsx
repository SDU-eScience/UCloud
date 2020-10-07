import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {useXTerm} from "Applications/xterm";
import {WSFactory} from "Authentication/HttpClientInstance";
import {useEffect} from "react";

export const ShellDemo: React.FunctionComponent = props => {
    const {termRef, terminal} = useXTerm();
    let streamId: string | null = null;

    useEffect(() => {
        const wsConnection = WSFactory.open("/app/compute/kubernetes/shell", {
            init: async conn => {
                await conn.subscribe({
                    call: "app.compute.kubernetes.shell.demo",
                    payload: {
                        type: "initialize",
                        jobId: "foobar"
                    },
                    handler: message => {
                        if (message.type === "message") {
                            const payload = message.payload as { streamId: string, data: string } | { streamId: string };
                            streamId = payload.streamId;
                            if ("data" in payload) {
                                console.log("Writing", payload.data);
                                terminal.write(payload.data);
                            }
                        }
                    }
                });
            }
        });

        terminal.onData((data) => {
            wsConnection.call({
                call: "app.compute.kubernetes.shell.demo",
                payload: {
                    type: "input",
                    streamId,
                    data
                }
            });
        });

        return () => {
            wsConnection.close();
        };
    }, []);

    return <MainContainer
        header={<Heading.h2>Shell Demo</Heading.h2>}
        main={
            <>
                <div ref={termRef}/>
            </>
        }
    />;
};
