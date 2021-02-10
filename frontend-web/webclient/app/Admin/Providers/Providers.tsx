import * as React from "react";
import MainContainer from "MainContainer/MainContainer";
import {Client} from "Authentication/HttpClientInstance";
import {useCloudAPI} from "Authentication/DataHook";
import * as UCloud from "UCloud"
import {emptyPageV2} from "DefaultObjects";
import {ListV2} from "Pagination";
import * as Heading from "ui-components/Heading";
import {Button, List} from "ui-components";
import {ListRow} from "ui-components/List";
import {useHistory} from "react-router";
import {useTitle} from "Navigation/Redux/StatusActions";

function Providers(): JSX.Element | null {
    const [providers, fetchProviders, params] = useCloudAPI<UCloud.PageV2<UCloud.provider.Provider>>(
        UCloud.provider.providers.browse({itemsPerPage: 25}),
        emptyPageV2
    );

    useTitle("Providers");
    const history = useHistory();

    console.log(providers);
    if (!Client.userIsAdmin) return null;
    return <MainContainer
        header={<Heading.h2>Providers</Heading.h2>}
        main={
            <ListV2
                loading={providers.loading}
                onLoadMore={() => fetchProviders(
                    UCloud.provider.providers.browse({itemsPerPage: 25, next: providers.data.next})
                )}
                page={providers.data}
                pageRenderer={pageRenderer}
                customEmptyPage={"No providers found"}
            />
        }
        sidebar={<Button fullWidth onClick={() => history.push("/admin/providers/create")}>Add Provider</Button>}
    />

    function pageRenderer(items: UCloud.provider.Provider[]): React.ReactNode {
        return (
            <List>
                {items.map(p => (
                    <ListRow
                        key={p.id}
                        left={p.id}
                        isSelected={false}
                        right={"TODO"}
                    />
                ))}
            </List>
        )
    }
}

export default Providers;
