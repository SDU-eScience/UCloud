import * as React from "react";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {Text, Button, Icon, List, Link} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import {useCallback, useEffect} from "react";
import {inDevEnvironment, onDevSite} from "@/UtilityFunctions";
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
import {timestampUnixMs} from "@/UtilityFunctions";
import {useHistory} from "react-router";

const lastConnectionAt = new LocalStorageCache<number>("last-connection-at");

function canConnectToProvider(data: provider.IntegrationBrowseResponseItem): boolean {
    return !data.connected ||
        (data.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(data.providerTitle));
}

export const ConnectDashboardCard: React.FunctionComponent = props => {
    if (!inDevEnvironment() && !onDevSite()) return null;

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
                        You must <BaseLink href="#">re-connect</BaseLink> with '{p.providerTitle}' to continue 
                        using it.
                    </>,
                    isPinned: true,
                    uniqueId: `${p.providerTitle}-${lastConnectionAt.retrieve() ?? 0}`,
                    onAction: () => {
                        history.push("/");
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
                        // notified by anything that this went well, so we have no other choice that to just assume
                        // that authentication is going to go well. Worst case, the user will have their key
                        // invalidated when they attempt to make a request.
            
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

    return <HighlightedCard
        color={"darkOrange"}
        icon={"key"}
        title={<Link to={"/providers/connect"}><Heading.h3>Providers</Heading.h3></Link>}
    >
        {!shouldConnect ? null :
            <Text color={"gray"}>
                <Icon name={"warning"} color={"orange"} mr={"8px"}/>
                Connect with the services below to use their resources
            </Text>
        }
        <List>
            {providers.data.items.map(it => {
                const canConnect = !it.connected ||
                    (it.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(it.providerTitle));

                return (
                    <ListRow
                        key={it.provider}
                        icon={<Icon name={"deiCLogo"}/>}
                        left={<>{it.providerTitle}</>}
                        right={!canConnect ?
                            <Icon name={"check"} color={"green"}/> :
                            <Button onClick={() => connectToProvider(it)}>Connect</Button>
                        }
                    />
                );
            })}
        </List>
    </HighlightedCard>;
};
