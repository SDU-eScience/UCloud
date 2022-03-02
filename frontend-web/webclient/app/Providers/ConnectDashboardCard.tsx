import * as React from "react";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {Text, Button, Icon, List, Link} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {useCloudAPI} from "@/Authentication/DataHook";
import {emptyPageV2} from "@/DefaultObjects";
import {useCallback, useEffect} from "react";
import {PageV2, provider} from "@/UCloud";
import IntegrationApi = provider.im;

export const ConnectDashboardCard: React.FunctionComponent = props => {
    const [providers, fetchProviders] = useCloudAPI<PageV2<provider.IntegrationBrowseResponseItem>>(
        {noop: true},
        emptyPageV2
    );

    const reload = useCallback(() => {
        fetchProviders(IntegrationApi.browse({}));
    }, []);

    useEffect(reload, [reload]);
    const shouldConnect = providers.data.items.some(it => !it.connected);

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
                    icon={<Icon name={"deiCLogo"}/>}
                    left={<>{it.providerTitle}</>}
                    right={it.connected ? <Icon name={"check"} color={"green"}/> : <Button>Connect</Button>}
                />
            ))}
        </List>
        {/*<Button fullWidth mt={"16px"} mb={"8px"}>*/}
        {/*    Connect with existing account*/}
        {/*</Button>*/}
    </HighlightedCard>;
};