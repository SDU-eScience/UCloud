import * as React from "react";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {Text, Button, Icon, List, Link} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {apiUpdate, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import {EventHandler, MouseEvent, useCallback, useEffect} from "react";
import {PageV2, provider} from "@/UCloud";
import IntegrationApi = provider.im;
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {
    hasUploadedSigningKeyToProvider,
    retrieveOrInitializePublicSigningKey,
    markSigningKeyAsUploadedToProvider,
} from "@/Authentication/MessageSigning";
import {sendNotification} from "@/Notifications";
import BaseLink from "@/ui-components/BaseLink";
import {LocalStorageCache} from "@/Utilities/LocalStorageCache";
import {doNothing, timestampUnixMs} from "@/UtilityFunctions";
import {useHistory} from "react-router";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {Feature, hasFeature} from "@/Features";
import MainContainer from "@/MainContainer/MainContainer";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {Operations} from "@/ui-components/Operation";

const lastConnectionAt = new LocalStorageCache<number>("last-connection-at");

function canConnectToProvider(data: provider.IntegrationBrowseResponseItem): boolean {
    return !data.connected ||
        (data.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(data.providerTitle));
}

export const Connect: React.FunctionComponent<{ embedded?: boolean }> = props => {
    if (!hasFeature(Feature.PROVIDER_CONNECTION)) return null;

    const [providers, fetchProviders] = useCloudAPI<PageV2<provider.IntegrationBrowseResponseItem>>(
        {noop: true},
        emptyPageV2
    );

    const [commandLoading, invokeCommand] = useCloudCommand();
    const history = useHistory();
    const reload = useCallback(() => {
        fetchProviders(IntegrationApi.browse({}));
    }, []);

    useEffect(reload, [reload]);
    const shouldConnect = providers.data.items.some(it => canConnectToProvider(it));

    useEffect(() => {
        for (const p of providers.data.items) {
            if (canConnectToProvider(p)) {
                sendNotification({
                    icon: "key",
                    title: `Connection required`,
                    body: <>
                        You must <BaseLink href="#">re-connect</BaseLink> with
                        '<ProviderTitle providerId={p.providerTitle}/>' to continue using it.
                    </>,
                    isPinned: true,
                    uniqueId: `${p.providerTitle}-${lastConnectionAt.retrieve() ?? 0}`,
                    onAction: () => {
                        history.push("/providers/connect");
                    }
                });
            }
        }
    }, [providers.data]);

    const connectToProvider = useCallback(async (providerData: provider.IntegrationBrowseResponseItem) => {
        lastConnectionAt.update(timestampUnixMs());
        const res = await invokeCommand<provider.IntegrationConnectResponse>(
            IntegrationApi.connect({provider: providerData.provider})
        );

        if (res) {
            if (providerData.requiresMessageSigning) {
                const postOptions: RequestInit = {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "Accept": "application/json",
                    },
                    mode: "cors",
                    credentials: "omit",
                    body: JSON.stringify({
                        publicKey: retrieveOrInitializePublicSigningKey()
                    })
                };

                fetch(res.redirectTo, postOptions)
                    .then(it => it.json())
                    .then(resp => {
                        const redirectTo = resp["redirectTo"];
                        if (!redirectTo || typeof redirectTo !== "string") throw "Invalid response from server";

                        // TODO(Dan): There is no guarantee that this was _actually_ successful. But we are also never
                        //   notified by anything that this went well, so we have no other choice that to just assume
                        //   that authentication is going to go well. Worst case, the user will have their key
                        //   invalidated when they attempt to make a request.

                        markSigningKeyAsUploadedToProvider(providerData.providerTitle);

                        document.location.href = redirectTo;
                    })
                    .catch(() => {
                        snackbarStore.addFailure(
                            "UCloud was not able to initiate a connection. Try again later.",
                            true
                        );
                    });
            } else {
                document.location.href = res.redirectTo;
            }
        }
    }, []);

    const body = <>
        {!shouldConnect ? null :
            <Text color={"gray"} mb={8}>
                <Icon name={"warning"} color={"orange"} mr={"8px"}/>
                Connect with the services below to use their resources
            </Text>
        }
        <List>
            {providers.data.items.map(it => {
                const canConnect = !it.connected ||
                    (it.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(it.providerTitle));

                const openFn: React.MutableRefObject<(left: number, top: number) => void> = {current: doNothing};
                const onContextMenu: EventHandler<MouseEvent<never>> = e => {
                    e.stopPropagation();
                    e.preventDefault();
                    openFn.current(e.clientX, e.clientY);
                };

                return (
                    <ListRow
                        onContextMenu={onContextMenu}
                        key={it.provider}
                        icon={<ProviderLogo providerId={it.providerTitle} size={40}/>}
                        left={<ProviderTitle providerId={it.providerTitle}/>}
                        right={!canConnect ?
                            <>
                                <Icon name={"check"} color={"green"}/>
                                <Operations
                                    location={"IN_ROW"}
                                    operations={[
                                        {
                                            confirm: true,
                                            color: "red",
                                            text: "Unlink",
                                            icon: "close",
                                            enabled: () => {
                                                // TODO(Dan): Generalize this for more providers
                                                return it.providerTitle !== "ucloud" && it.providerTitle !== "aau";
                                            },
                                            onClick: async () => {
                                                await invokeCommand(
                                                    apiUpdate(
                                                        { provider: it.providerTitle },
                                                        "/api/providers/integration",
                                                        "clearConnection"
                                                    )
                                                );

                                                reload();
                                            }
                                        }
                                    ]}
                                    selected={[]}
                                    extra={null}
                                    entityNameSingular={"Provider"}
                                    row={it}
                                    openFnRef={openFn}
                                    forceEvaluationOnOpen
                                />
                            </> :
                            <Button onClick={() => connectToProvider(it)}>Connect</Button>
                        }
                    />
                );
            })}
        </List>
    </>;

    if (props.embedded) {
        return <HighlightedCard
            color={"darkOrange"}
            icon={"key"}
            title={<Link to={"/providers/connect"}><Heading.h3>Providers</Heading.h3></Link>}
        >
            {body}
        </HighlightedCard>;
    } else {
        // NOTE(Dan): You are not meant to swap the embedded property on a mounted component. We should be fine even
        // though we are breaking rules of hooks.
        useTitle("Connect to Providers");
        return <MainContainer main={body}/>;
    }
};

export default Connect;
