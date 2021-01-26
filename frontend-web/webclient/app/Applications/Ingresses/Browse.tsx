import * as React from "react";
import * as UCloud from "UCloud";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {emptyPageV2} from "DefaultObjects";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useProjectId} from "Project";
import * as Pagination from "Pagination";
import {compute} from "UCloud";
import Ingress = compute.Ingress;
import {PageRenderer} from "Pagination/PaginationV2";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {Box, List} from "ui-components";
import {creditFormatter} from "Project/ProjectUsage";
import {Operation, Operations} from "ui-components/Operation";
import {prettierString, shortUUID} from "UtilityFunctions";
import {useToggleSet} from "Utilities/ToggleSet";
import {StickyBox} from "ui-components/StickyBox";
import {useScrollStatus} from "Utilities/ScrollStatus";
import Create from "Applications/Ingresses/Create";

const Browse: React.FunctionComponent<{ computeProvider?: string; onSelect?: (selection: Ingress) => void }> = props => {
    const projectId = useProjectId();
    const [infScrollId, setInfScrollId] = useState(0);
    const [ingresses, fetchIngresses] = useCloudAPI({noop: true}, emptyPageV2);
    const [, invokeCommand] = useCloudCommand();

    const toggleSet = useToggleSet(ingresses.data.items);
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const [isCreating, setIsCreating] = useState(false);

    const reload = useCallback(() => {
        fetchIngresses(UCloud.compute.ingresses.browse({}));
        setInfScrollId(id => id + 1);
        toggleSet.uncheckAll();
    }, []);

    const loadMore = useCallback(() => {
        if (ingresses.data.next) {
            fetchIngresses(UCloud.compute.ingresses.browse({next: ingresses.data.next}));
        }
    }, [ingresses.data.next]);

    useEffect(reload, [reload, projectId]);

    const callbacks: IngressCallbacks = useMemo(() => ({
        invokeCommand, reload, setIsCreating,
        onSelect: ingress => {
            props.onSelect?.(ingress);
        }
    }), [props.onSelect]);

    const pageRenderer = useCallback<PageRenderer<Ingress>>(items => {
        return <List childPadding={"8px"} bordered={false}>
            {items.map(it =>
                <ListRow
                    key={it.id}
                    left={it.specification.domain}
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    leftSub={
                        <ListStatContainer>
                            <ListRowStat>
                                {it.specification.product.category} ({it.specification.product.provider})
                            </ListRowStat>
                            <ListRowStat>{prettierString(it.status.state)}</ListRowStat>
                            {!it.status.boundTo ? null :
                                <ListRowStat>Bound to {shortUUID(it.status.boundTo)}</ListRowStat>}
                            <ListRowStat>
                                {creditFormatter(it.billing.pricePerUnit)}
                            </ListRowStat>
                        </ListStatContainer>
                    }
                    right={
                        <Operations selected={toggleSet.checked.items} location={"IN_ROW"}
                                    entityNameSingular={entityName}
                                    extra={callbacks} operations={operations} row={it}/>
                    }
                />
            )}
        </List>
    }, [toggleSet]);

    return <Box ref={scrollingContainerRef}>
        <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}>
            <Operations selected={toggleSet.checked.items} location={"TOPBAR"} entityNameSingular={entityName}
                        extra={callbacks} operations={operations}/>
        </StickyBox>

        {!isCreating ? null :
            <Create computeProvider={props.computeProvider} onCreateFinished={() => {
                setIsCreating(false);
                reload();
            }}/>
        }

        <Box style={{display: isCreating ? "none" : undefined}}>
            <Pagination.ListV2
                page={ingresses.data}
                onLoadMore={loadMore}
                infiniteScrollGeneration={infScrollId}
                loading={ingresses.loading}
                pageRenderer={pageRenderer}
            />
        </Box>
    </Box>;
};

interface IngressCallbacks {
    invokeCommand: InvokeCommand;
    onSelect: (ingress: Ingress) => void;
    setIsCreating: (isCreating: boolean) => void;
    reload: () => void;
}

const entityName = "Public link";
const operations: Operation<Ingress, IngressCallbacks>[] = [
    {
        text: "Use",
        primary: true,
        enabled: (selected) => {
            if (selected.length !== 1) return false;
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
            if (selected.length === 0) return false;

            const areReady = selected.every(it => it.status.state === "READY" && it.status.boundTo == null);
            if (!areReady) {
                if (selected.length === 1) return "You cannot delete a public link which is currently in use";
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
    },
    {
        text: "Create link",
        icon: "upload",
        color: "blue",
        primary: true,
        canAppearInLocation: (loc) => loc !== "IN_ROW",
        enabled: (selected) => {
            return selected.length === 0;
        },
        onClick: (selected, cb) => {
            cb.setIsCreating(true);
        }
    }
];

export default Browse;
