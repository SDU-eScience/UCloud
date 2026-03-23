import * as React from "react";
import {Card, MainContainer, Text} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {Client} from "@/Authentication/HttpClientInstance";
import UcxView from "@/UCX/UcxView";

const UCX_DEMO_AUTH_TOKEN = "ucx-demo-proxy-token";
const UCX_DEMO_SYSHELLO = "ucx-demo-webclient-syshello";

const UcxCreateDemo: React.FunctionComponent = () => {
    const url = Client.computeURL("/api", "/ucxCreateDemo")
        .replace("http://", "ws://")
        .replace("https://", "wss://");

    return <UcxView
        url={url}
        authToken={UCX_DEMO_AUTH_TOKEN}
        sysHello={UCX_DEMO_SYSHELLO}
        rpcHandlers={{
            "client.ping": async payload => ({
                ok: true,
                from: "webclient",
                echo: payload["message"] ?? null,
            }),
        }}
        renderFrame={({connected, transportError, content}) => <MainContainer
            main={<div className={WrapperClass}>
                <Card className={CardClass}>
                    <h2>UCX Create Demo</h2>
                    <Text color={connected ? "successMain" : "warningMain"}>
                        {connected ? "Connected" : "Connecting..."}
                    </Text>
                    {transportError === "" ? null : <Text color="errorMain">Transport error: {transportError}</Text>}
                    {content}
                </Card>
            </div>}
        />}
    />;
};

const WrapperClass = injectStyle("ucx-create-wrapper", k => `
    ${k} {
        display: flex;
        justify-content: center;
        padding: 24px;
    }
`);

const CardClass = injectStyle("ucx-create-card", k => `
    ${k} {
        width: min(920px, 100%);
        padding: 16px;
        border-radius: 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
`);

export default UcxCreateDemo;
