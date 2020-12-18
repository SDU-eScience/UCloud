import * as React from "react";
import * as UCloud from "UCloud";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import {useCallback, useEffect, useState} from "react";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {compute} from "UCloud";
import Ingress = compute.Ingress;
import {PageRenderer} from "Pagination/PaginationV2";
import {ListRow, ListRowStat} from "ui-components/List";
import {Button, Flex, Icon, Text} from "ui-components";
import {Client} from "Authentication/HttpClientInstance";
import {creditFormatter} from "Project/ProjectUsage";
import ClickableDropdown from "ui-components/ClickableDropdown";

const data: compute.Ingress[] = [{
    id: "id",
    domain: "domain",
    billing: {pricePerUnit: 100000, creditsCharged: 0},
    createdAt: 123890123,
    owner: {
        username: Client.username!,
    },
    product: {
        category: "category",
        id: "id",
        provider: "provider"
    },
    status: {
        state: "PREPARING",
        boundTo: undefined
    },
    updates: [],
    resolvedProduct: undefined
}, {
    id: "id2",
    domain: "domain2",
    billing: {pricePerUnit: 200000, creditsCharged: 1000000},
    createdAt: 123890123 + 1000000,
    owner: {
        username: Client.username + " foo",
    },
    product: {
        category: "category 2 ",
        id: "id 2",
        provider: "provider2"
    },
    status: {
        state: "PREPARING",
        boundTo: "foodbarz"
    },
    updates: [],
    resolvedProduct: undefined
}, {
    id: "id3",
    domain: "domain3",
    billing: {pricePerUnit: 300000, creditsCharged: 2000000},
    createdAt: 123890123 + 2000000,
    owner: {
        username: Client.username + " foo",
    },
    product: {
        category: "category 3 ",
        id: "id 3",
        provider: "provider3"
    },
    status: {
        state: "READY"
    },
    updates: [],
    resolvedProduct: undefined
}, {
    id: "id3",
    domain: "domain3",
    billing: {pricePerUnit: 300000, creditsCharged: 2000000},
    createdAt: 123890123 + 2000000,
    owner: {
        username: Client.username + " foo",
    },
    product: {
        category: "category 3 ",
        id: "id 3",
        provider: "provider3"
    },
    status: {
        state: "READY",
        boundTo: "foo"
    },
    updates: [],
    resolvedProduct: undefined
}];


const Browse: React.FunctionComponent<{computeProvider?: string; onSelect?: (selection: {id: string; domain: string}) => void}> = props => {
    const projectId = useProjectId();
    const [infScrollId, setInfScrollId] = useState(0);
    const [ingresses, fetchIngresses] = useCloudAPI(
        {noop: true},
        emptyPageV2
    );

    const reload = useCallback(() => {
        fetchIngresses(UCloud.compute.ingresses.browse({}));
        setInfScrollId(id => id + 1);
    }, []);

    const loadMore = useCallback(() => {
        if (ingresses.data.next) {
            fetchIngresses(UCloud.compute.ingresses.browse({next: ingresses.data.next}));
        }
    }, [ingresses.data.next]);

    useEffect(() => {
        reload();
    }, [reload, projectId]);

    const pageRenderer = useCallback<PageRenderer<Ingress>>(page => {
        return page.items.map(it => {
            const isDisabled = it.status.boundTo != null || it.status.state !== "READY";
            return (
                <ListRow
                    key={it.id}
                    left={it.domain}
                    leftSub={<>
                        <ListRowStat>{it.product.category} ({it.product.provider})</ListRowStat><SpacedDash />
                        <ListRowStat>{it.status.state}</ListRowStat><SpacedDash />
                        {it.status.boundTo ? <><ListRowStat>Bound to {it.status.boundTo}</ListRowStat><SpacedDash /></> : null}
                        <ListRowStat>{creditFormatter(it.billing.pricePerUnit)}</ListRowStat>
                    </>}
                    right={<>
                        {props.onSelect != null ? <Button
                            disabled={isDisabled}
                            onClick={() => props.onSelect!({domain: it.domain, id: it.id})}
                            height="35px"
                            width="80px"
                            mr="16px"
                        >Use</Button> : null}
                        <ClickableDropdown trigger={<Icon name="ellipsis" />}>
                            TODO
                        </ClickableDropdown>
                    </>}
                />
            )
        });
    }, []);

    return <Pagination.ListV2
        page={ingresses.data}
        onLoadMore={loadMore}
        infiniteScrollGeneration={infScrollId}
        loading={ingresses.loading}
        pageRenderer={pageRenderer}
    />;
};

function SpacedDash() {
    return <ListRowStat><Text mx="6px">-</Text></ListRowStat>
}

export default Browse;
