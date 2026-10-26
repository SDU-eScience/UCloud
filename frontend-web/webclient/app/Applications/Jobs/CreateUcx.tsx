import * as React from "react";
import {useEffect, useMemo, useRef, useState} from "react";
import {MainContainer, Card, Flex, Text} from "@/ui-components";
import {injectStyle} from "@/Unstyled";
import {Client} from "@/Authentication/HttpClientInstance";
import UcxView, {UcxFrameRenderArgs, UcxRpcHandler, UcxSpinner} from "@/UCX/UcxView";
import {getStoredProject} from "@/Project/ReduxState";
import {Application, ApplicationGroup} from "@/Applications/AppStoreApi";
import {AppHeader} from "@/Applications/View";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {sendFailureNotification, sendInformationNotification, sendSuccessNotification} from "@/Notifications";
import {useNavigate} from "react-router-dom";
import AppRoutes from "@/Routes";
import {getProviderTitle} from "@/Providers/ProviderTitle";

interface CreateUcxJobProps {
    application: Application;
    appGroup: ApplicationGroup | null;
}

interface SwitchProviderPayload {
    provider?: unknown;
    handoff?: unknown;
}

interface CreateUcxJobRendererProps extends UcxFrameRenderArgs {
    application: Application;
    appGroup: ApplicationGroup | null;
    handoffPending: boolean;
    handoffProvider?: string;
    handoffError: string;
}

const providerSwitchTimeoutMs = 120000;

function providerLabel(provider: string | undefined): string {
    return provider ? getProviderTitle(provider) : "the selected service provider";
}

export const CreateUcxJob: React.FunctionComponent<CreateUcxJobProps> = ({application, appGroup}) => {
    const url = Client.computeURL("/api", "/hpc/apps/ucx/connect")
        .replace("http://", "ws://")
        .replace("https://", "wss://");

    const navigate = useNavigate();

    const [activeProvider, setActiveProvider] = useState<string | undefined>(undefined);
    const [handoffPending, setHandoffPending] = useState(false);
    const [handoffProvider, setHandoffProvider] = useState<string | undefined>(undefined);
    const [handoffError, setHandoffError] = useState("");

    const activeProviderRef = useRef<string | undefined>(undefined);
    const previousProviderRef = useRef<string | undefined>(undefined);
    const pendingHandoffRef = useRef<unknown>(null);
    const switchingRef = useRef(false);
    const switchAttemptRef = useRef(0);
    const switchTimerRef = useRef<number | null>(null);
    const switchStartTimerRef = useRef<number | null>(null);
    const switchGenerationRef = useRef(0);

    function clearSwitchTimer() {
        if (switchTimerRef.current != null) {
            window.clearTimeout(switchTimerRef.current);
            switchTimerRef.current = null;
        }
    }

    function clearStartTimer() {
        if (switchStartTimerRef.current != null) {
            window.clearTimeout(switchStartTimerRef.current);
            switchStartTimerRef.current = null;
        }
    }

    function clearSwitchTimers() {
        clearStartTimer();
        clearSwitchTimer();
    }

    function handleSwitchFailure(provider: string | undefined) {
        if (!switchingRef.current || switchStartTimerRef.current != null) return;

        clearStartTimer();
        switchGenerationRef.current++;

        if (switchAttemptRef.current === 0) {
            switchAttemptRef.current = 1;
            const fallback = previousProviderRef.current;
            const message = `Could not connect to ${providerLabel(provider)}. Returning to the previous provider.`;
            setHandoffError(message);
            sendFailureNotification(message);
            setHandoffProvider(fallback);
            activeProviderRef.current = fallback;
            setActiveProvider(fallback);
            armSwitchTimer(fallback);
            return;
        }

        switchingRef.current = false;
        setHandoffPending(false);
        setHandoffProvider(undefined);
        pendingHandoffRef.current = null;
        setHandoffError("The provider switch could not complete. Try again.");
        sendFailureNotification("The provider switch could not complete.");
    }

    function armSwitchTimer(provider: string | undefined) {
        clearSwitchTimer();
        switchTimerRef.current = window.setTimeout(() => {
            handleSwitchFailure(provider);
        }, providerSwitchTimeoutMs);
    }

    function connectToProvider(provider: string, handoff: unknown) {
        clearStartTimer();
        const generation = ++switchGenerationRef.current;

        previousProviderRef.current = activeProviderRef.current;
        activeProviderRef.current = provider;
        pendingHandoffRef.current = handoff ?? {};
        switchingRef.current = true;
        switchAttemptRef.current = 0;
        setHandoffPending(true);
        setHandoffProvider(provider);
        setHandoffError("");

        switchStartTimerRef.current = window.setTimeout(() => {
            switchStartTimerRef.current = null;
            if (switchGenerationRef.current !== generation) return;

            setActiveProvider(provider);
            armSwitchTimer(provider);
        }, 0);
    }

    function handleMounted() {
        if (!switchingRef.current) return;

        clearSwitchTimers();
        switchGenerationRef.current++;
        switchingRef.current = false;
        switchAttemptRef.current = 0;
        pendingHandoffRef.current = null;
        setHandoffPending(false);
        setHandoffProvider(undefined);
        setHandoffError("");
    }

    useEffect(() => {
        return () => {
            clearSwitchTimers();
        };
    }, []);

    const handlers = useMemo<Record<string, UcxRpcHandler>>(() => {
        return {
            "switchProvider": raw => {
                const payload = raw as SwitchProviderPayload;
                const provider = typeof payload.provider === "string" ? payload.provider.trim() : "";
                if (provider === "") {
                    throw new Error("Missing service provider");
                }
                if (switchingRef.current) {
                    throw new Error("A provider switch is already in progress");
                }
                if (provider === activeProviderRef.current) {
                    return {};
                }

                const handoff = payload.handoff !== null && typeof payload.handoff === "object"
                    ? payload.handoff
                    : {};
                connectToProvider(provider, handoff);
                return {};
            },
            "frontend": payload => {
                console.log(payload);
                sendSuccessNotification("Testing");
                return {
                    "message": "hello from frontend!"
                };
            },
            "uiSendMessage": raw => {
                const payload = raw as { message: string, success: boolean };
                if (payload.success) {
                    sendSuccessNotification(payload.message);
                } else {
                    sendFailureNotification(payload.message);
                }
            },
            "stackOpen": raw => {
                const payload = raw as { id: string };
                navigate(AppRoutes.stacks.view(payload.id));
            }
        };
    }, [navigate]);

    return <UcxView
        key={activeProvider ?? "initial"}
        url={url}
        authToken={async () => {
            const accessToken = await Client.receiveAccessTokenOrRefreshIt();
            const project = getStoredProject() ?? "";
            return `${accessToken}\n${project}`;
        }}
        sysHello={() => JSON.stringify({
            name: application.metadata.name,
            version: application.metadata.version,
            provider: activeProviderRef.current,
            handoff: pendingHandoffRef.current ?? undefined,
        })}
        rpcHandlers={handlers}
        onMounted={handleMounted}
        onTransportError={message => {
            if (!handoffPending || switchAttemptRef.current !== 0) return;
            if (message.includes("Forbidden") || message.includes("Authentication failed")) {
                handleSwitchFailure(activeProviderRef.current);
            }
        }}
        renderFrame={({connected, transportError, content}) => (
            <CreateUcxJobRenderer connected={connected} transportError={transportError} content={content}
                                  application={application} appGroup={appGroup}
                                  handoffPending={handoffPending} handoffProvider={handoffProvider}
                                  handoffError={handoffError}/>
        )}
    />;
};

export const CreateUcxJobRenderer: React.FunctionComponent<CreateUcxJobRendererProps> =
    ({connected, transportError, content, appGroup, application, handoffPending, handoffProvider, handoffError}) => {
        useEffect(() => {
            if (transportError && !handoffPending) {
                sendInformationNotification(transportError);
            }
        }, [handoffPending, transportError]);

        return <MainContainer
            main={<>
                <Flex className={CreateUcxHeaderClass}>
                    <AppHeader
                        title={appGroup?.specification?.title ?? application.metadata.title}
                        application={application}
                        flavors={appGroup?.status?.applications ?? []}
                        allVersions={application.versions ?? []}
                        showSelectors={false}
                    />
                    <Flex flexGrow={1}/>
                    <UtilityBar/>
                </Flex>

                <Flex className={CreateUcxContentClass} flexDirection="column" gap="16px">
                    {handoffPending || handoffError !== "" ? (
                        <Card p="16px" style={handoffPending
                            ? {border: "1px solid var(--warningMain)"}
                            : {border: "1px solid var(--errorMain)"}}>
                            <div role="status" aria-live="polite" style={{display: "flex", alignItems: "center", gap: 12}}>
                                {handoffPending ? <UcxSpinner size={24}/> : null}
                                <Text>
                                    {handoffPending
                                        ? `Switching to ${providerLabel(handoffProvider)}. Your configuration will be carried over where possible. Provider-specific selections may need to be made again.`
                                        : handoffError}
                                </Text>
                            </div>
                        </Card>
                    ) : null}
                    {content}
                </Flex>
            </>}
        />
    };

const CreateUcxHeaderClass = injectStyle("create-ucx-header", key => `
    ${key} {
        margin: 32px 50px 0;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px 0;
        }
    }
`);

const CreateUcxContentClass = injectStyle("create-ucx-content", key => `
    ${key} {
        margin: 32px 50px 0;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px 0;
        }
    }
`);
