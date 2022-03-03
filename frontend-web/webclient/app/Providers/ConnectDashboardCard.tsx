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
    const shouldConnect = providers.data.items.some(it => !it.connected);
    const connectToProvider = useCallback(async (provider: string) => {
        const res = await invokeCommand<provider.IntegrationConnectResponse>(IntegrationApi.connect({provider}));
        if (res) document.location.href = res.redirectTo;
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
            {providers.data.items.map(it => (
                <ListRow
                    key={it.provider}
                    icon={<Icon name={"deiCLogo"}/>}
                    left={<>{it.providerTitle}</>}
                    right={it.connected ?
                        <Icon name={"check"} color={"green"}/> :
                        <Button onClick={() => connectToProvider(it.provider)}>Connect</Button>
                    }
                />
            ))}
        </List>
        {/*<Button fullWidth mt={"16px"} mb={"8px"}>*/}
        {/*    Connect with existing account*/}
        {/*</Button>*/}
    </HighlightedCard>;
};

