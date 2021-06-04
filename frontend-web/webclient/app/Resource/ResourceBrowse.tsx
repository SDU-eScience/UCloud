import * as React from "react";
import * as Pagination from "Pagination";
import {Resource, ResourceApi} from "UCloud/ResourceApi";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {accounting, PageV2} from "UCloud";
import {PropsWithChildren, ReactElement, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {bulkRequestOf, emptyPageV2} from "DefaultObjects";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useProjectId} from "Project";
import {useToggleSet} from "Utilities/ToggleSet";
import {useScrollStatus} from "Utilities/ScrollStatus";
import {PageRenderer} from "Pagination/PaginationV2";
import {Box, List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {Operation, Operations} from "ui-components/Operation";
import {dateToString} from "Utilities/DateUtilities";
import MainContainer from "MainContainer/MainContainer";
import {StickyBox} from "ui-components/StickyBox";
import Product = accounting.Product;
import {NamingField} from "UtilityComponents";

export interface ResourceBrowseProps<Res extends Resource> {
    api: ResourceApi<Res, never>;
    embedded?: boolean;

    // NOTE(Dan): The resource will be null when rendering a row for the inline creation of resources
    IconRenderer?: React.FunctionComponent<{ resource: Res | null }>;
    TitleRenderer?: React.FunctionComponent<{ resource: Res }>;
    StatsRenderer?: React.FunctionComponent<{ resource: Res }>;

    onSelect?: (resource: Res) => void;
    onInlineCreation?: (text: string, product: Product, cb: ResourceBrowseCallbacks<Res>) => void;

    createOperations?: (defaults: Operation<Res, ResourceBrowseCallbacks<Res>>[]) =>
        Operation<Res, ResourceBrowseCallbacks<Res>>[];
    withDefaultStats?: boolean;
}

interface ResourceBrowseCallbacks<Res extends Resource> {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    api: ResourceApi<Res, never>;
    startCreation: () => void;
    viewProperties: (res: Res) => void;
    onSelect?: (resource: Res) => void;
}

export const ResourceBrowse = <Res extends Resource>(
    {
        onSelect, api, IconRenderer, TitleRenderer, StatsRenderer, ...props
    }: PropsWithChildren<ResourceBrowseProps<Res>>
): ReactElement | null => {
    const [resources, fetchResources] = useCloudAPI<PageV2<Res>>({noop: true}, emptyPageV2);
    const [infScroll, setInfScroll] = useState(0);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const projectId = useProjectId();

    const toggleSet = useToggleSet(resources.data.items);
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const [isCreating, setIsCreating] = useState(false);

    const reload = useCallback(() => {
        setInfScroll(prev => prev + 1);
        fetchResources(api.browse({itemsPerPage: 50}));
        toggleSet.uncheckAll();
    }, [projectId]);

    const loadMore = useCallback(() => {
        if (resources.data.next) {
            fetchResources(api.browse({next: resources.data.next, itemsPerPage: 50}));
        }
    }, [resources.data.next]);

    const callbacks: ResourceBrowseCallbacks<Res> = useMemo(() => ({
        api,
        invokeCommand,
        commandLoading,
        reload,
        onSelect,
        startCreation: () => {
            if (props.onInlineCreation != null) {
                setIsCreating(true);
            }
        },
        viewProperties: res => 0
    }), [api, invokeCommand, commandLoading, reload, props.onInlineCreation]);

    const inlineInputRef = useRef<HTMLInputElement>(null);
    const onInlineCreate = useCallback(() => {
        if (inlineInputRef.current && props.onInlineCreation) {
            // props.onInlineCreation(inlineInputRef.current.value, null, callbacks);
        }
        setIsCreating(false);
    }, [props.onInlineCreation, inlineInputRef, callbacks, setIsCreating]);

    const operations: Operation<Res, ResourceBrowseCallbacks<Res>>[] = useMemo(() => {
        const defaults: Operation<Res, ResourceBrowseCallbacks<Res>>[] = [
            {
                text: "Use",
                primary: true,
                enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined,
                canAppearInLocation: loc => loc === "IN_ROW",
                onClick: (selected, cb) => {
                    cb.onSelect!(selected[0]);
                }
            },
            {
                text: "Create " + api.title.toLowerCase(),
                icon: "upload",
                color: "blue",
                primary: true,
                canAppearInLocation: loc => loc !== "IN_ROW",
                enabled: (selected) => selected.length === 0,
                onClick: (selected, cb) => cb.startCreation()
            },
            {
                text: "Permissions",
                icon: "share",
                enabled: (selected) => selected.length === 1,
                onClick: (selected, cb) => {
                    if (!props.embedded) {
                        // TODO Show inline permission dialog
                        cb.viewProperties(selected[0]);
                    } else {
                        cb.viewProperties(selected[0]);
                    }
                }
            },
            {
                text: "Delete",
                icon: "trash",
                color: "red",
                confirm: true,
                enabled: (selected) => selected.length >= 1,
                onClick: async (selected, cb) => {
                    await cb.invokeCommand(cb.api.remove(bulkRequestOf(...selected.map(it => ({id: it.id})))));
                    cb.reload();
                }
            },
            {
                text: "Properties",
                icon: "properties",
                enabled: (selected) => selected.length === 1,
                onClick: (selected, cb) => {
                    cb.viewProperties(selected[0]);
                }
            }
        ];

        if (props.createOperations) return props.createOperations(defaults);
        return defaults;
    }, [props.createOperations, api, props.embedded]);

    const pageRenderer = useCallback<PageRenderer<Res>>(items => {
        return <List childPadding={"8px"} bordered={false}>
            {!isCreating ? null :
                <ListRow
                    icon={IconRenderer ? <IconRenderer resource={null} /> : null}
                    left={
                        <NamingField
                            confirmText={"Create"}
                            onCancel={() => setIsCreating(false)}
                            onSubmit={onInlineCreate}
                            inputRef={inlineInputRef}
                        />
                    }
                    right={null}
                />
            }
            {items.map(it =>
                <ListRow
                    key={it.id}
                    icon={IconRenderer ? <IconRenderer resource={it} /> : null}
                    left={TitleRenderer ? <TitleRenderer resource={it} /> : <>{api.title} ({it.id})</>}
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    leftSub={
                        <ListStatContainer>
                            {props.withDefaultStats !== false ?
                                <>
                                    <ListRowStat icon={"calendar"}>{dateToString(it.createdAt)}</ListRowStat>
                                    <ListRowStat icon={"user"}>{it.owner.createdBy}</ListRowStat>
                                    <ListRowStat icon={"cubeSolid"}>
                                        {it.specification.product.id} / {it.specification.product.category}
                                    </ListRowStat>
                                </> : null
                            }
                            {StatsRenderer ? <StatsRenderer resource={it}/> : null}
                        </ListStatContainer>
                    }
                    right={
                        <Operations
                            selected={toggleSet.checked.items}
                            location={"IN_ROW"}
                            entityNameSingular={api.title}
                            entityNamePlural={api.titlePlural}
                            extra={callbacks}
                            operations={operations}
                            row={it}
                        />
                    }
                />
            )}
        </List>
    }, [toggleSet, isCreating]);

    useEffect(() => reload(), [reload]);

    if (props.embedded !== true) {
        useTitle(api.titlePlural);
        useLoading(commandLoading || resources.loading);
        useRefreshFunction(reload);
    }

    const main = <>
        <Pagination.ListV2
            page={resources.data}
            onLoadMore={loadMore}
            infiniteScrollGeneration={infScroll}
            loading={resources.loading}
            pageRenderer={pageRenderer}
            customEmptyPage={`No ${api.titlePlural} available. Click "Create ${api.title}" to create a new one.`}
        />
    </>;

    if (props.embedded) {
        return <Box ref={scrollingContainerRef}>
            <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}>
                <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                            entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                            extra={callbacks} operations={operations} />
            </StickyBox>
            {main}
        </Box>;
    } else {
        return <MainContainer
            main={main}
            sidebar={
                <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                            entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                            extra={callbacks} operations={operations} />
            }
        />
    }
};
