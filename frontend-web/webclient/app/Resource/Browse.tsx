import * as React from "react";
import * as Pagination from "Pagination";
import {ResolvedSupport, Resource, ResourceApi, ResourceBrowseCallbacks, SupportByProvider} from "UCloud/ResourceApi";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
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
import {ProductSelector} from "Resource/ProductSelector";
import {timestampUnixMs, useEffectSkipMount} from "UtilityFunctions";
import {Client} from "Authentication/HttpClientInstance";
import {useSidebarPage} from "ui-components/Sidebar";
import {ResourceProperties} from "Resource/Properties";
import * as Heading from "ui-components/Heading";
import {useHistory, useLocation} from "react-router";
import {ResourceFilter} from "Resource/Filter";
import {useResourceSearch} from "Resource/Search";
import {getQueryParamOrElse} from "Utilities/URIUtilities";
import {useDispatch} from "react-redux";
import * as H from "history";
import ProductReference = accounting.ProductReference;

export interface ResourceBrowseProps<Res extends Resource> extends BaseResourceBrowseProps<Res> {
    api: ResourceApi<Res, never>;

    onInlineCreation?: (text: string, product: Product, cb: ResourceBrowseCallbacks<Res>) => Res["specification"] | APICallParameters;
    inlinePrefix?: (productWithSupport: ResolvedSupport) => string;
    inlineSuffix?: (productWithSupport: ResolvedSupport) => string;
    inlineCreationMode?: "TEXT" | "NONE";
    inlineProduct?: Product;

    withDefaultStats?: boolean;
    additionalFilters?: Record<string, string>;
    header?: JSX.Element;
    headerSize?: number;
    onRename?: (text: String, resource: Res, cb: ResourceBrowseCallbacks<Res>) => Promise<void>;

    navigateToChildren?: (history: H.History, resource: Res) => void;
}

export interface BaseResourceBrowseProps<Res extends Resource> {
    embedded?: boolean;
    isSearch?: boolean;

    onSelect?: (resource: Res) => void;
}

export const ResourceBrowse = <Res extends Resource>(
    {
        onSelect, api, ...props
    }: PropsWithChildren<ResourceBrowseProps<Res>>
): ReactElement | null => {
    const [productsWithSupport, fetchProductsWithSupport] = useCloudAPI<SupportByProvider>({noop: true},
        {productsByProvider: {}})
    const includeOthers = !props.embedded;
    const [selectedProduct, setSelectedProduct] = useState<Product | null>(props.inlineProduct ?? null);
    const [resources, fetchResources] = useCloudAPI<PageV2<Res>>({noop: true}, emptyPageV2);
    const [renaming, setRenaming] = useState<Res | null>(null);
    const [renamingValue, setRenamingValue] = useState("");
    const [infScroll, setInfScroll] = useState(0);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const projectId = useProjectId();
    const [filters, setFilters] = useState<Record<string, string>>({});
    const [sortDirection, setSortDirection] = useState<"ascending" | "descending">("ascending");
    const [sortColumn, setSortColumn] = useState<string | undefined>(undefined);
    const history = useHistory();
    const location = useLocation();
    const query = getQueryParamOrElse(location.search, "q", "");

    const toggleSet = useToggleSet(resources.data.items);
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const [isCreating, setIsCreating] = useState(false);
    const dispatch = useDispatch();

    const [inlineInspecting, setInlineInspecting] = useState<Res | null>(null);
    const closeProperties = useCallback(() => setInlineInspecting(null), [setInlineInspecting]);

    const products: Product[] = useMemo(() => {
        const allProducts: Product[] = [];
        for (const provider of Object.keys(productsWithSupport.data.productsByProvider)) {
            for (const productWithSupport of productsWithSupport.data.productsByProvider[provider]) {
                allProducts.push(productWithSupport.product);
            }
        }
        return allProducts;
    }, [productsWithSupport]);

    const selectedProductWithSupport: ResolvedSupport | null = useMemo(() => {
        if (selectedProduct) {
            return productsWithSupport.data.productsByProvider[selectedProduct.category.provider]
                .find(it => it.product.id === selectedProduct.id &&
                    it.product.category.id === selectedProduct.category.id) ?? null;
        }
        return null;
    }, [selectedProduct, productsWithSupport]);

    const reload = useCallback(() => {
        setInfScroll(prev => prev + 1);
        fetchProductsWithSupport(api.retrieveProducts());
        if (props.isSearch) {
            fetchResources(api.search({
                itemsPerPage: 50, flags: {includeOthers, ...filters}, query,
                sortDirection, sortBy: sortColumn, ...props.additionalFilters
            }));
        } else {
            fetchResources(api.browse({
                itemsPerPage: 50, includeOthers, ...filters, sortBy: sortColumn,
                sortDirection, ...props.additionalFilters
            }));
        }
        toggleSet.uncheckAll();
    }, [projectId, filters, query, props.isSearch, sortColumn, sortDirection, props.additionalFilters]);

    const loadMore = useCallback(() => {
        if (resources.data.next) {
            if (props.isSearch) {
                fetchResources(api.search({
                    itemsPerPage: 50, flags: {includeOthers, ...filters}, query,
                    next: resources.data.next, sortDirection, sortBy: sortColumn, ...props.additionalFilters
                }));
            } else {
                fetchResources(api.browse({
                    next: resources.data.next, itemsPerPage: 50, includeOthers,
                    ...filters, sortBy: sortColumn, sortDirection, ...props.additionalFilters
                }));
            }
        }
    }, [resources.data.next, filters, query, props.isSearch, sortColumn, sortDirection, props.additionalFilters]);

    useEffectSkipMount(() => {
        reload();
    }, [reload, props.additionalFilters]);
    useEffectSkipMount(() => {
        setSelectedProduct(props.inlineProduct ?? null);
    }, [props.inlineProduct]);

    const callbacks: ResourceBrowseCallbacks<Res> = useMemo(() => ({
        api,
        isCreating,
        invokeCommand,
        commandLoading,
        reload,
        embedded: props.embedded == true,
        onSelect,
        dispatch,
        startRenaming: (res, value) => {
            setRenaming(res);
            setRenamingValue(value);
        },
        startCreation: () => {
            if (props.onInlineCreation != null) {
                setSelectedProduct(props.inlineProduct ?? null);
                setIsCreating(true);
            }
        },
        viewProperties: res => {
            if (props.embedded) {
                setInlineInspecting(res);
            } else {
                history.push(`/${api.routingNamespace}/properties/${encodeURIComponent(res.id)}`);
            }
        }
    }), [api, invokeCommand, commandLoading, reload, isCreating, props.onInlineCreation, history, dispatch,
        props.inlineProduct]);

    const onProductSelected = useCallback(async (product: Product) => {
        if (props.inlineCreationMode !== "NONE") {
            setSelectedProduct(product);
        } else {
            if (!props.onInlineCreation) return;
            const spec = props.onInlineCreation("", product, callbacks);
            setIsCreating(false);
            if ("path" in spec && "method" in spec) {
                await callbacks.invokeCommand(spec);
            } else {
                await callbacks.invokeCommand(api.create(bulkRequestOf(spec as Res["specification"])));
            }
            callbacks.reload();
        }
    }, [setSelectedProduct, props.inlineCreationMode, props.onInlineCreation, callbacks]);

    const inlineInputRef = useRef<HTMLInputElement>(null);
    const onInlineCreate = useCallback(async () => {
        if (inlineInputRef.current && props.onInlineCreation) {
            const prefix = props?.inlinePrefix?.(selectedProductWithSupport!) ?? "";
            const suffix = props?.inlineSuffix?.(selectedProductWithSupport!) ?? "";
            const spec = props.onInlineCreation(
                prefix + inlineInputRef.current.value + suffix,
                selectedProduct!,
                callbacks
            );

            if ("path" in spec && "method" in spec) {
                await callbacks.invokeCommand(spec);
            } else {
                await callbacks.invokeCommand(api.create(bulkRequestOf(spec as Res["specification"])));
            }
            callbacks.reload();
        }
        setIsCreating(false);
    }, [props.onInlineCreation, inlineInputRef, callbacks, setIsCreating, selectedProduct]);

    const renameInputRef = useRef<HTMLInputElement>(null);
    const onRename = useCallback(async () => {
        const text = renameInputRef.current?.value;
        if (text && renaming) {
            await props.onRename?.(text, renaming, callbacks);
            callbacks.reload();
            setRenaming(null);
        }
    }, [props.onRename, renaming, callbacks]);

    const operations: Operation<Res, ResourceBrowseCallbacks<Res>>[] = useMemo(() => {
        return api.retrieveOperations();
    }, [callbacks, api]);

    const onSortUpdated = useCallback((dir, column) => {
        setSortColumn(column);
        setSortDirection(dir)
    }, []);
    const pageRenderer = useCallback<PageRenderer<Res>>(items => {
        return <List childPadding={"8px"} bordered={false}>
            {!isCreating ? null :
                <ListRow
                    icon={api.IconRenderer ? <api.IconRenderer resource={null} size={"36px"}/> : null}
                    left={
                        !selectedProduct ?
                            <ProductSelector products={products} onProductSelected={onProductSelected}/>
                            :
                            <NamingField
                                confirmText={"Create"}
                                onCancel={() => setIsCreating(false)}
                                onSubmit={onInlineCreate}
                                inputRef={inlineInputRef}
                                prefix={props.inlinePrefix && selectedProductWithSupport ?
                                    props.inlinePrefix(selectedProductWithSupport) : null}
                                suffix={props.inlineSuffix && selectedProductWithSupport ?
                                    props.inlineSuffix(selectedProductWithSupport) : null}
                            />
                    }
                    leftSub={
                        <ListStatContainer>
                            {props.withDefaultStats === true || !selectedProduct ? null :
                                <>
                                    <ListRowStat icon={"calendar"}>{dateToString(timestampUnixMs())}</ListRowStat>
                                    <ListRowStat icon={"user"}>{Client.username}</ListRowStat>
                                    <ListRowStat icon={"cubeSolid"}>
                                        {selectedProduct.id} / {selectedProduct.category.id}
                                    </ListRowStat>
                                </>
                            }

                        </ListStatContainer>
                    }
                    right={null}
                />
            }
            {items.length > 0 || isCreating ? null :
                <>
                    No {api.titlePlural.toLowerCase()} available. Click &quot;Create {api.title.toLowerCase()}&quot;
                    to create a new one.
                </>
            }
            {items.map(it =>
                <ListRow
                    key={it.id}
                    icon={api.IconRenderer ? <api.IconRenderer resource={it} size={"36px"}/> : null}
                    left={
                        renaming?.id === it.id ?
                            <NamingField onCancel={() => setRenaming(null)} confirmText={"Rename"}
                                         inputRef={renameInputRef} onSubmit={onRename} defaultValue={renamingValue}/>
                            : api.InlineTitleRenderer ?
                            <api.InlineTitleRenderer resource={it}/> : <>{api.title} ({it.id})</>}
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    navigate={() => props.navigateToChildren?.(history, it)}
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
                            {api.StatsRenderer ? <api.StatsRenderer resource={it}/> : null}
                        </ListStatContainer>
                    }
                    right={
                        <>
                            {api.ImportantStatsRenderer ? <api.ImportantStatsRenderer resource={it}/> : null}
                            <Operations
                                selected={toggleSet.checked.items}
                                location={"IN_ROW"}
                                entityNameSingular={api.title}
                                entityNamePlural={api.titlePlural}
                                extra={callbacks}
                                operations={operations}
                                row={it}
                            />
                        </>
                    }
                />
            )}
        </List>
    }, [toggleSet, isCreating, selectedProduct, props.withDefaultStats, selectedProductWithSupport, renaming]);

    useEffect(() => reload(), [projectId, query]);

    if (!props.embedded) {
        useTitle(api.titlePlural);
        useLoading(commandLoading || resources.loading);
        useRefreshFunction(reload);
        useSidebarPage(api.page);
        useResourceSearch(api);
    }

    const main = !inlineInspecting ? <>
        <Pagination.ListV2
            page={resources.data}
            onLoadMore={loadMore}
            infiniteScrollGeneration={infScroll}
            loading={resources.loading}
            pageRenderer={pageRenderer}
            customEmptyPage={pageRenderer([])}
        />
    </> : <>
        <api.Properties api={api} resource={inlineInspecting} reload={reload} embedded={true}
                        closeProperties={closeProperties}/>
    </>;

    if (props.embedded) {
        return <Box ref={scrollingContainerRef}>
            <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}>
                {inlineInspecting ?
                    <Heading.h3 flexGrow={1}>{api.titlePlural}</Heading.h3> :
                    <>
                        <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                                    entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                                    extra={callbacks} operations={operations}/>
                        {props.header}
                    </>
                }
            </StickyBox>
            {main}
        </Box>;
    } else {
        return <MainContainer
            header={props.header}
            headerSize={props.headerSize}
            main={main}
            sidebar={
                inlineInspecting ? null :
                    <>
                        <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                                    entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                                    extra={callbacks} operations={operations}/>

                        <ResourceFilter pills={api.filterPills} filterWidgets={api.filterWidgets}
                                        sortEntries={api.sortEntries} sortDirection={sortDirection}
                                        onSortUpdated={onSortUpdated} properties={filters} setProperties={setFilters}
                                        onApplyFilters={reload}/>
                    </>
            }
        />
    }
};
