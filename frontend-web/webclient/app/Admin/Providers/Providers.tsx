import * as React from "react";
import MainContainer from "MainContainer/MainContainer";
import {Client} from "Authentication/HttpClientInstance";
import {useCloudAPI} from "Authentication/DataHook";
import * as UCloud from "UCloud"
import {emptyPageV2} from "DefaultObjects";
import {ListV2} from "Pagination";
import * as Heading from "ui-components/Heading";
import {Button, List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {useHistory} from "react-router";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useToggleSet} from "Utilities/ToggleSet";

function Providers(): JSX.Element | null {
    const [providers, fetchProviders] = useCloudAPI<UCloud.PageV2<UCloud.provider.Provider>>(
        UCloud.provider.providers.browse({itemsPerPage: 25}),
        emptyPageV2
    );

    useTitle("Providers");
    const history = useHistory();
    const toggleSet = useToggleSet(providers.data.items);

    console.log(providers.data.items[0]);
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
                {items.map(p => {
                    const {manifest} = p.specification;
                    const isDockerEnabled = manifest.features.compute.docker.enabled;
                    const isVMEnabled = manifest.features.compute.virtualMachine.enabled;
                    const isHttps = p.specification.https;
                    return (
                        <ListRow
                            key={p.id}
                            left={p.id}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat icon={isHttps ? "check" : "close"}>
                                        HTTPS
                                    </ListRowStat>
                                    <ListRowStat icon={isVMEnabled ? "check" : "close"}>
                                        VM enabled
                                    </ListRowStat>
                                    <ListRowStat icon={isDockerEnabled ? "check" : "close"}>
                                        Docker enabled
                                    </ListRowStat>
                                </ListStatContainer>
                            }
                            isSelected={toggleSet.checked.has(p)}
                            select={() => toggleSet.toggle(p)}
                            right={"TODO"}
                        />
                    )
                })}
            </List>
        )
    }
}

export default Providers;
