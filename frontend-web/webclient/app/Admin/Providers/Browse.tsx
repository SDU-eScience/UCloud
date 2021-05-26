import * as React from "react";
import MainContainer from "MainContainer/MainContainer";
import {Client} from "Authentication/HttpClientInstance";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as UCloud from "UCloud"
import {emptyPageV2} from "DefaultObjects";
import {ListV2} from "Pagination";
import * as Heading from "ui-components/Heading";
import {Button, Link, List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {useHistory} from "react-router";
import {useTitle} from "Navigation/Redux/StatusActions";
import {useToggleSet} from "Utilities/ToggleSet";
import {Operation, Operations} from "ui-components/Operation";
import {addStandardDialog} from "UtilityComponents";

const entityName = "Provider";

function Browse(): JSX.Element | null {
    const [providers, fetchProviders] = useCloudAPI<UCloud.PageV2<UCloud.provider.Provider>>(
        UCloud.provider.providers.browse({itemsPerPage: 25}),
        emptyPageV2
    );

    const [loading, invokeCommand] = useCloudCommand();

    useTitle("Providers");
    const history = useHistory();
    const toggleSet = useToggleSet(providers.data.items);

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
        sidebar={
            <>
                <Button fullWidth onClick={() => history.push("/admin/providers/create")}>Add Provider</Button>
                <Operations
                    selected={toggleSet.checked.items}
                    location="SIDEBAR"
                    entityNameSingular={entityName}
                    operations={operations}
                    extra={{invokeCommand}}
                />
            </>
        }
    />

    function pageRenderer(items: UCloud.provider.Provider[]): React.ReactNode {
        return (
            <List>
                {items.map(p => {
                    const isHttps = p.specification.https;
                    return (
                        <ListRow
                            key={p.id}
                            left={<Link to={`/admin/providers/view/${p.id}`}>{p.id}</Link>}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat icon={isHttps ? "check" : "close"}>
                                        HTTPS
                                    </ListRowStat>
                                </ListStatContainer>
                            }
                            isSelected={toggleSet.checked.has(p)}
                            select={() => toggleSet.toggle(p)}
                            right={
                                <Operations
                                    selected={toggleSet.checked.items}
                                    row={p}
                                    entityNameSingular={entityName}
                                    extra={{invokeCommand}}
                                    location="IN_ROW"
                                    operations={operations}
                                />
                            }
                        />
                    )
                })}
            </List>
        )
    }
}

const operations: Operation<UCloud.provider.Provider, {invokeCommand: InvokeCommand}>[] = [
    {
        enabled: selected => selected.length > 0,
        onClick: (selected, extra) =>
            addStandardDialog({
                title: "WARNING!",
                message: <>
                    <div>Are you sure you want to renew the provider token?</div>
                    <div>This will invalidate every current security token.</div>
                </>,
                confirmText: "Confirm",
                cancelButtonColor: "blue",
                confirmButtonColor: "red",
                cancelText: "Cancel",
                onConfirm: async () => {
                    extra.invokeCommand(UCloud.provider.providers.renewToken(
                        {type: "bulk", items: selected.map(it => ({id: it.id}))}
                    ))
                }
            }),
        text: "Renew token"
    }
    // UCloud.provider.providers.updateAcl
    // UCloud.provider.providers.updateManifest
];

export default Browse;
