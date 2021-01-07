import * as React from "react";
import * as UCloud from "UCloud";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {compute} from "UCloud";
import Ingress = compute.Ingress;
import {PageRenderer} from "Pagination/PaginationV2";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {List} from "ui-components";
import {creditFormatter} from "Project/ProjectUsage";
import {Operation, Operations} from "ui-components/Operation";
import {prettierString, shortUUID} from "UtilityFunctions";

const Browse: React.FunctionComponent<{ computeProvider?: string; onSelect?: (selection: { id: string; domain: string }) => void }> = props => {
    const projectId = useProjectId();
    const [infScrollId, setInfScrollId] = useState(0);
    const [ingresses, fetchIngresses] = useCloudAPI({noop: true}, emptyPageV2);
    const [commandLoading, invokeCommand] = useCloudCommand();

    const reload = useCallback(() => {
        fetchIngresses(UCloud.compute.ingresses.browse({}));
        setInfScrollId(id => id + 1);
    }, []);

    const loadMore = useCallback(() => {
        if (ingresses.data.next) {
            fetchIngresses(UCloud.compute.ingresses.browse({next: ingresses.data.next}));
        }
    }, [ingresses.data.next]);

    useEffect(reload, [reload, projectId]);

    const callbacks: IngressCallbacks = useMemo(() => ({
        invokeCommand, reload,
        onSelect: ingress => {
            props.onSelect?.({id: ingress.id, domain: ingress.domain});
        }
    }), [props.onSelect]);

    const pageRenderer = useCallback<PageRenderer<Ingress>>(page => {
        return <List childPadding={"8px"} bordered={false}>
            {page.items.map(it =>
                <ListRow
                    key={it.id}
                    left={it.domain}
                    leftSub={
                        <ListStatContainer>
                            <ListRowStat>{it.product.category} ({it.product.provider})</ListRowStat>
                            <ListRowStat>{prettierString(it.status.state)}</ListRowStat>
                            {!it.status.boundTo ? null :
                                <ListRowStat>Bound to {shortUUID(it.status.boundTo)}</ListRowStat>}
                            <ListRowStat>
                                {creditFormatter(it.billing.pricePerUnit)}
                            </ListRowStat>
                        </ListStatContainer>
                    }
                    right={
                        <Operations selected={[]} dropdown entityNameSingular={"Public link"}
                                    extra={callbacks} operations={operations} row={it}/>}
                />
            )}
        </List>
    }, []);

    return <Pagination.ListV2
        page={ingresses.data}
        onLoadMore={loadMore}
        infiniteScrollGeneration={infScrollId}
        loading={ingresses.loading}
        pageRenderer={pageRenderer}
    />;
};

interface IngressCallbacks {
    invokeCommand: InvokeCommand;
    onSelect: (ingress: Ingress) => void;
    reload: () => void;
}

const operations: Operation<Ingress, IngressCallbacks>[] = [
    {
        text: "Use",
        primary: true,
        enabled: (selected) => {
            if (selected.length === 0) return false;
            const it = selected[0];
            return it.status.boundTo == null && it.status.state === "READY";
        },
        onClick: (selected, cb) => {
            cb.onSelect(selected[0]);
        }
    },
    {
        text: "Delete",
        confirm: true,
        icon: "trash",
        color: "red",
        enabled: (selected) => {
            const areReady = selected.every(it => it.status.state === "READY" && it.status.boundTo == null);
            if (!areReady) {
                if (selected.length === 0) return "You cannot delete a public link which is currently in use";
                return "Not all links are ready and not in use";
            }
            return true;
        },
        onClick: async (selected, cb) => {
            await cb.invokeCommand(
                UCloud.compute.ingresses.remove({
                    type: "bulk",
                    items: selected.map(it => ({id: it.id}))
                })
            );

            cb.reload();
        }
    }
];

export default Browse;
