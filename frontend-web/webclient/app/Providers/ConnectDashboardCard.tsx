import * as React from "react";
import HighlightedCard from "@/ui-components/HighlightedCard";
import { Text, Button, Icon, List, Link } from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import { ListRow } from "@/ui-components/List";
import { useCloudAPI, useCloudCommand } from "@/Authentication/DataHook";
import { emptyPageV2 } from "@/DefaultObjects";
import { useCallback, useEffect } from "react";
import { inDevEnvironment, onDevSite } from "@/UtilityFunctions";
import { PageV2, provider } from "@/UCloud";
import IntegrationApi = provider.im;
import {
    hasUploadedSigningKeyToProvider,
    retrieveOrInitializePublicSigningKey,
    signIntentToCall
} from "@/Authentication/MessageSigning";

export const ConnectDashboardCard: React.FunctionComponent = props => {
    if (!inDevEnvironment() && !onDevSite()) return null;

    const [providers, fetchProviders] = useCloudAPI<PageV2<provider.IntegrationBrowseResponseItem>>(
        {noop: true},
        emptyPageV2
    );

    const [commandLoading, invokeCommand] = useCloudCommand();
    const reload = useCallback(() => {
        fetchProviders(IntegrationApi.browse({}));
    }, []);

    useEffect(reload, [reload]);
    const shouldConnect = providers.data.items.some(it =>
        !it.connected ||
        (it.requiresMessageSigning === true && !hasUploadedSigningKeyToProvider(it.providerTitle))
    );
    const connectToProvider = useCallback(async (providerData: provider.IntegrationBrowseResponseItem) => {
        const res = await invokeCommand<provider.IntegrationConnectResponse>(
            IntegrationApi.connect({provider: providerData.provider})
        );

        if (res) {
            if (providerData.requiresMessageSigning) {
                console.log("Got key", retrieveOrInitializePublicSigningKey());
                console.log("Example signature", signIntentToCall({ path: "/api/files/browse", method: "GET" }));
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

