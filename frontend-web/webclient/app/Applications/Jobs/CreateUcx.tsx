import * as React from "react";
import {MainContainer, Card, Flex, Text} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {Client} from "@/Authentication/HttpClientInstance";
import UcxView from "@/UCX/UcxView";
import {getStoredProject} from "@/Project/ReduxState";
import {Application, ApplicationGroup} from "@/Applications/AppStoreApi";
import {AppHeader} from "@/Applications/View";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {useMemo} from "react";
import {UcxRpcHandler} from "@/UCX/UcxView";
import {sendSuccessNotification} from "@/Notifications";

interface CreateUcxJobProps {
    application: Application;
    appGroup: ApplicationGroup | null;
}

export const CreateUcxJob: React.FunctionComponent<CreateUcxJobProps> = ({application, appGroup}) => {
    const url = Client.computeURL("/api", "/hpc/apps/ucx/connect")
        .replace("http://", "ws://")
        .replace("https://", "wss://");

    const handlers = useMemo<Record<string, UcxRpcHandler>>(() => {
        return {
            "frontend": payload => {
                console.log(payload)
                sendSuccessNotification("Testing");
                return {
                    "message": "hello from frontend!"
                };
            }
        }
    }, []);

    return <UcxView
        url={url}
        authToken={async () => {
            const accessToken = await Client.receiveAccessTokenOrRefreshIt();
            const project = getStoredProject() ?? "";
            return `${accessToken}\n${project}`;
        }}
        sysHello={() => JSON.stringify({
            name: application.metadata.name,
            version: application.metadata.version,
        })}
        rpcHandlers={handlers}
        renderFrame={({connected, transportError, content}) => (
            <MainContainer
                main={<>
                    <Flex mx="50px" mt="32px">
                        <AppHeader
                            title={appGroup?.specification?.title ?? application.metadata.title}
                            application={application}
                            flavors={appGroup?.status?.applications ?? []}
                            allVersions={application.versions ?? []}
                        />
                        <Flex flexGrow={1} />
                        <UtilityBar />
                    </Flex>

                    <div className={WrapperClass}>
                        <Card className={CardClass}>
                            <Text color={connected ? "successMain" : "warningMain"}>
                                {connected ? "Connected" : "Connecting..."}
                            </Text>
                            {transportError === "" ? null : <Text color="errorMain">Transport error: {transportError}</Text>}
                            {content}
                        </Card>
                    </div>
                </>}
            />
        )}
    />;
};

const WrapperClass = injectStyle("ucx-create-job-wrapper", k => `
    ${k} {
        display: flex;
        justify-content: center;
        padding: 24px 50px;
    }
`);

const CardClass = injectStyle("ucx-create-job-card", k => `
    ${k} {
        width: min(1100px, 100%);
        padding: 16px;
        border-radius: 12px;
        display: flex;
        flex-direction: column;
        gap: 8px;
    }
`);
