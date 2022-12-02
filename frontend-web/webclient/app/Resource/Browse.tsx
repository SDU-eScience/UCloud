import * as React from "react";
import { PropsWithChildren, ReactElement, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
    ResolvedSupport,
    Resource,
    ResourceApi,
    ResourceBrowseCallbacks,
    ResourceSpecification,
    ResourceStatus,
    ResourceUpdate,
    SupportByProvider,
    UCLOUD_CORE
} from "@/UCloud/ResourceApi";
import { useCloudAPI, useCloudCommand } from "@/Authentication/DataHook";
import { bulkRequestOf } from "@/DefaultObjects";
import { useLoading, useTitle } from "@/Navigation/Redux/StatusActions";
import { useToggleSet } from "@/Utilities/ToggleSet";
import { PageRenderer } from "@/Pagination/PaginationV2";
import { Box, Checkbox, Flex, Icon, Label, Link, List, Tooltip, Truncate } from "@/ui-components";
import { Spacer } from "@/ui-components/Spacer";
import { ListRowStat } from "@/ui-components/List";
import { Operations } from "@/ui-components/Operation";
import { dateToString } from "@/Utilities/DateUtilities";
import MainContainer from "@/MainContainer/MainContainer";
import { NamingField } from "@/UtilityComponents";
import { doNothing, preventDefault, timestampUnixMs, useEffectSkipMount } from "@/UtilityFunctions";
import { Client } from "@/Authentication/HttpClientInstance";
import { useSidebarPage } from "@/ui-components/Sidebar";
import * as Heading from "@/ui-components/Heading";
import { NavigateFunction, useLocation, useNavigate } from "react-router";
import { EnumFilterWidget, EnumOption, ResourceFilter, StaticPill } from "@/Resource/Filter";
import { useResourceSearch } from "@/Resource/Search";
import { getQueryParamOrElse } from "@/Utilities/URIUtilities";
import { useDispatch } from "react-redux";
import { ItemRenderer, ItemRow, ItemRowMemo, StandardBrowse, useRenamingState } from "@/ui-components/Browse";
import { useAvatars } from "@/AvataaarLib/hook";
import { Avatar } from "@/AvataaarLib";
import { Product, ProductType, productTypeToIcon } from "@/Accounting";
import { BrowseType } from "./BrowseType";
import { snackbarStore } from "@/Snackbar/SnackbarStore";
import { FixedSizeList } from "react-window";
import { default as AutoSizer } from "react-virtualized-auto-sizer";
import { useGlobal } from "@/Utilities/ReduxHooks";
import { ProviderLogo } from "@/Providers/ProviderLogo";
import { Feature, hasFeature } from "@/Features";
import { ProviderTitle } from "@/Providers/ProviderTitle";
import { isAdminOrPI, useProjectId } from "@/Project/Api";
import { useProject } from "@/Project/cache";
import { useUState } from "@/Utilities/UState";
import { connectionState } from "@/Providers/ConnectionState";
import { ProductSelector } from "@/Products/Selector";

export interface ResourceBrowseProps<Res extends Resource, CB> extends BaseResourceBrowseProps<Res> {
    api: ResourceApi<Res, never>;

    // Inline creation
    onInlineCreation?: (text: string, product: Product, cb: ResourceBrowseCallbacks<Res> & CB) => Res["specification"] | APICallParameters;
    inlinePrefix?: (productWithSupport: ResolvedSupport) => string;
    inlineSuffix?: (productWithSupport: ResolvedSupport) => string;
    inlineCreationMode?: "TEXT" | "NONE";
    inlineProduct?: Product;
    productFilterForCreate?: (product: ResolvedSupport) => boolean;

    // Headers
    header?: JSX.Element;
    headerSize?: number;

    // Rename
    onRename?: (text: string, resource: Res, cb: ResourceBrowseCallbacks<Res>) => Promise<void>;

    // Properties and navigation
    navigateToChildren?: (navigate: NavigateFunction, resource: Res) => "properties" | void;
    propsForInlineResources?: Record<string, any>;
    viewPropertiesInline?: (res: Res) => boolean;

    // Empty page
    emptyPage?: JSX.Element;

    // Tweaks to row information
    // TODO(Dan): This should probably live inside of ResourceApi?
    withDefaultStats?: boolean;
    showCreatedAt?: boolean;
    showCreatedBy?: boolean;
    showProduct?: boolean;
    showGroups?: boolean;

    // Callback information which needs to be passed to the operations
    extraCallbacks?: any;

    // Hooks and tweaks to how resources are loaded
    onResourcesLoaded?: (newItems: Res[]) => void;
    shouldFetch?: () => boolean;
    resources?: Res[]; // NOTE(Dan): if resources != null then no normal fetches will occur

    // Sidebar and filters
    additionalFilters?: Record<string, string>;
    extraSidebar?: JSX.Element;
}

export interface BaseResourceBrowseProps<Res extends Resource> {
    browseType: BrowseType;
    isSearch?: boolean;

    onSelect?: (resource: Res) => void;
    onSelectRestriction?: (resource: Res) => boolean;
}

function getStoredSortDirection(title: string): "ascending" | "descending" | null {
    return localStorage.getItem(`${title}:sortDirection`) as "ascending" | "descending" | null;
}

function getStoredSortColumn(title: string): string | null {
    return localStorage.getItem(`${title}:sortColumn`);
}

function setStoredSortColumn(title: string, column?: string): void {
    if (column) localStorage.setItem(`${title}:sortColumn`, column);
}

function setStoredSortDirection(title: string, order: "ascending" | "descending"): void {
    localStorage.setItem(`${title}:sortDirection`, order);
}

function getStoredFilters(title: string): Record<string, string> | null {
    const data = localStorage.getItem(`${title}:filters`);
    if (!data) return null;
    try {
        const parsed = JSON.parse(data);
        if (typeof parsed === "object") {
            return parsed as Record<string, string>;
        } else {
            return null;
        }
    } catch (e) {
        return null;
    }
}

function setStoredFilters(title: string, filters: Record<string, string>) {
    localStorage.setItem(`${title}:filters`, JSON.stringify(filters));
}

export function ResourceBrowse<Res extends Resource, CB = undefined>(
    {
        onSelect, api, ...props
    }: PropsWithChildren<ResourceBrowseProps<Res, CB>> & {/* HACK(Jonas) */disableSearch?: boolean/* HACK(Jonas): End */ }): ReactElement | null {
    const [productsWithSupport, fetchProductsWithSupport] = useCloudAPI<SupportByProvider>(
        { noop: true },
        { productsByProvider: {} }
    );

    const [headerSize] = useGlobal("mainContainerHeaderSize", 0);
    const isEmbedded = props.browseType === BrowseType.Embedded;
    const includeOthers = props.browseType !== BrowseType.Embedded;
    const [selectedProduct, setSelectedProduct] = useState<Product | null>(props.inlineProduct ?? null);
    const [renamingValue, setRenamingValue] = useState("");
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [filters, setFilters] = useState<Record<string, string>>(getStoredFilters(api.title) ?? {});
    const [sortDirection, setSortDirection] = useState<"ascending" | "descending">(getStoredSortDirection(api.title) ?? api.defaultSortDirection);
    const [sortColumn, setSortColumn] = useState<string | undefined>(getStoredSortColumn(api.title) ?? undefined);
    const navigate = useNavigate();
    const location = useLocation();
    const query = getQueryParamOrElse(location.search, "q", "");

    const reloadRef = useRef<() => void>(doNothing);
    const toggleSet = useToggleSet<Res>([]);
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    // Bug(Jonas): Causing rerenders on scrolling for modal showing properties.
    // const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const [isCreating, setIsCreating] = useState(false);
    const dispatch = useDispatch();
    const projectId = useProjectId();
    const project = useProject();
    const isWorkspaceAdmin = projectId === undefined ? true : !project.loading && isAdminOrPI(project.fetch().status.myRole);
    const canConsumeResources = api.isCoreResource ||
        projectId === undefined ||
        project.loading ||
        project.fetch().specification.canConsumeResources !== false;
    const providerConnection = useUState(connectionState);

    useEffect(() => toggleSet.uncheckAll(), [props.additionalFilters]);
    useEffectSkipMount(() => {
        setStoredFilters(api.title, filters);
    }, [filters]);

    const [inlineInspecting, setInlineInspecting] = useState<Res | null>(null);
    const closeProperties = useCallback(() => setInlineInspecting(null), [setInlineInspecting]);
    useEffect(() => {
        fetchProductsWithSupport(api.retrieveProducts())
    }, []);
    const renaming = useRenamingState<Res>(
        () => renamingValue, [renamingValue],
        (a, b) => a.id === b.id, [],

        async (item, text) => {
            await props.onRename?.(text, item, callbacks);
            callbacks.reload();
        },
        [props.onRename]
    );

    const sortDirections: EnumOption[] = [{
        icon: "sortAscending",
        title: "Ascending",
        value: "ascending",
        helpText: "Increasing in value, e.g. 1, 2, 3..."
    }, {
        icon: "sortDescending",
        title: "Descending",
        value: "descending",
        helpText: "Decreasing in value, e.g. 3, 2, 1..."
    }];

    const products: Product[] = useMemo(() => {
        const allProducts: Product[] = [];
        for (const provider of Object.keys(productsWithSupport.data.productsByProvider)) {
            const productsByProvider = productsWithSupport.data.productsByProvider[provider];
            if (!productsByProvider) continue;
            for (const productWithSupport of productsByProvider) {
                if (props.productFilterForCreate !== undefined && !props.productFilterForCreate(productWithSupport)) {
                    continue;
                }

                allProducts.push(productWithSupport.product as unknown as Product);
            }
        }
        return allProducts;
    }, [productsWithSupport, props.productFilterForCreate]);

    const selectedProductWithSupport: ResolvedSupport | null = useMemo(() => {
        if (selectedProduct) {
            const productsByProvider = productsWithSupport.data.productsByProvider[selectedProduct.category.provider]
            if (productsByProvider) {
                return productsByProvider.find(it =>
                    it.product.name === selectedProduct.name &&
                    it.product.category.name === selectedProduct.category.name
                ) ?? null;
            }
        }
        return null;
    }, [selectedProduct, productsWithSupport]);

    const generateFetch = useCallback((next?: string): APICallParameters => {
        if (props.resources != null) {
            return { noop: true };
        } else if (props.shouldFetch && !props.shouldFetch()) {
            return { noop: true };
        }

        if (props.isSearch) {
            return api.search({
                itemsPerPage: 100, flags: { includeOthers, ...filters }, query,
                next, sortDirection, sortBy: sortColumn, ...props.additionalFilters
            });
        } else {
            return api.browse({
                next, itemsPerPage: 100, includeOthers,
                ...filters, sortBy: sortColumn, sortDirection, ...props.additionalFilters
            });
        }
    }, [filters, query, props.isSearch, sortColumn, sortDirection, props.additionalFilters, props.shouldFetch, props.resources]);

    useEffectSkipMount(() => {
        setSelectedProduct(props.inlineProduct ?? null);
    }, [props.inlineProduct]);

    const viewProperties = useCallback((res: Res) => {
        if (isEmbedded && (props.viewPropertiesInline === undefined || props.viewPropertiesInline(res))) {
            setInlineInspecting(res);
        } else {
            navigate(`/${api.routingNamespace}/properties/${encodeURIComponent(res.id)}`);
        }
    }, [setInlineInspecting, isEmbedded, navigate, api, props.viewPropertiesInline]);

    const callbacks: ResourceBrowseCallbacks<Res> & CB = useMemo(() => ({
        api,
        isCreating,
        navigate,
        invokeCommand,
        commandLoading,
        reload: () => {
            toggleSet.uncheckAll();
            reloadRef.current()
        },
        embedded: isEmbedded,
        onSelect,
        onSelectRestriction: props.onSelectRestriction,
        dispatch,
        isWorkspaceAdmin,
        startRenaming(res: Res, value: string) {
            renaming.setRenaming(res);
            setRenamingValue(value);
        },
        startCreation() {
            if (props.onInlineCreation != null) {
                setSelectedProduct(props.inlineProduct ?? null);
                setIsCreating(true);
            }
        },
        cancelCreation() {
            setIsCreating(false);
        },
        viewProperties,
        ...props.extraCallbacks,
        supportByProvider: productsWithSupport.data
    }), [api, invokeCommand, commandLoading, reloadRef, isCreating, navigate, props.onInlineCreation, dispatch,
        viewProperties, props.inlineProduct, props.extraCallbacks, toggleSet, productsWithSupport.data]);

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

    const [inlineCreationLoading, setInlineCreationLoading] = useState(false);
    const inlineInputRef = useRef<HTMLInputElement>(null);
    const onInlineCreate = useCallback(async () => {
        if (inlineInputRef.current && props.onInlineCreation) {
            const prefix = props?.inlinePrefix?.(selectedProductWithSupport!) ?? "";
            const suffix = props?.inlineSuffix?.(selectedProductWithSupport!) ?? "";

            const trimmedValue = inlineInputRef.current.value.trim();
            if (!trimmedValue) {
                snackbarStore.addFailure("Title can't be blank or empty", false);
                return;
            }

            const spec = props.onInlineCreation(
                prefix + trimmedValue + suffix,
                selectedProduct!,
                callbacks
            );

            setInlineCreationLoading(true);
            if ("path" in spec && "method" in spec) {
                await callbacks.invokeCommand(spec);
            } else {
                const timeout = setTimeout(() => snackbarStore.addInformation(`${api.title} will be created shortly.`, false), 3_500);
                await callbacks.invokeCommand(api.create(bulkRequestOf(spec as Res["specification"])));
                clearTimeout(timeout);
            }
            setInlineCreationLoading(false);
            callbacks.reload();
        }
        setIsCreating(false);
    }, [props.onInlineCreation, inlineInputRef, callbacks, setIsCreating, selectedProduct]);

    const operations = useMemo(() => {
        return api.retrieveOperations()
            .map(it => {
                const copy = {...it};
                copy.enabled = (selected, cb, all) => {
                    const needsConnection = selected.some(r => 
                        providerConnection.canConnectToProvider(r.specification.product.provider)
                    );

                    const defaultAnswer = it.enabled(selected, cb, all);

                    if (defaultAnswer && needsConnection) {
                        return "You must connect to the provider before you can consume resources";
                    }

                    return defaultAnswer;
                };
                return copy;
            });
    }, [callbacks, api, providerConnection.lastRefresh]);

    const onSortUpdated = useCallback((dir: "ascending" | "descending", column?: string) => {
        setSortColumn(column);
        setSortDirection(dir);
        setStoredSortColumn(api.title, column);
        setStoredSortDirection(api.title, dir);
    }, []);

    const modifiedRenderer = useMemo((): ItemRenderer<Res> => {
        const renderer: ItemRenderer<Res> = { ...api.renderer };
        const RemainingStats = renderer.Stats;
        const NormalMainTitle = renderer.MainTitle;
        const RemainingImportantStats = renderer.ImportantStats;
        renderer.MainTitle = function mainTitle({ resource }) {
            if (resource === undefined) {
                return !selectedProduct ?
                    <ProductSelector products={products} onSelect={onProductSelected} selected={null} slim />
                    :
                    <NamingField
                        confirmText={"Create"}
                        onCancel={() => setIsCreating(false)}
                        onSubmit={onInlineCreate}
                        inputRef={inlineInputRef}
                        disabled={inlineCreationLoading}
                        prefix={props.inlinePrefix && selectedProductWithSupport ?
                            props.inlinePrefix(selectedProductWithSupport) : null}
                        suffix={props.inlineSuffix && selectedProductWithSupport ?
                            props.inlineSuffix(selectedProductWithSupport) : null}
                    />;
            } else {
                return NormalMainTitle ?
                    <NormalMainTitle browseType={props.browseType} resource={resource} callbacks={callbacks} /> : null;
            }
        };
        renderer.Stats = props.withDefaultStats !== false ? ({ resource }) => (<>
            {!resource ? <>
                {props.showCreatedAt === false ? null :
                    <ListRowStat icon="calendar">{dateToString(timestampUnixMs())}</ListRowStat>}
                {props.showCreatedBy === false ? null : <ListRowStat icon={"user"}>{Client.username}</ListRowStat>}
                {props.showProduct === false || !selectedProduct ? null : <>
                    <ListRowStat
                        icon="cubeSolid">{selectedProduct.name} / {selectedProduct.category.name}</ListRowStat>
                </>}
            </> : <>
                {props.showCreatedAt === false ? null :
                    <ListRowStat icon={"calendar"}>{dateToString(resource.createdAt)}</ListRowStat>}
                {props.showCreatedBy === false || resource.owner.createdBy === "_ucloud" ? null :
                    <div className="tooltip">
                        <ListRowStat icon={"user"}>{" "}{resource.owner.createdBy}</ListRowStat>
                        <div className="tooltip-content centered">
                            <UserBox username={resource.owner.createdBy} />
                        </div>
                    </div>
                }
                {props.showProduct === false || resource.specification.product.provider === UCLOUD_CORE ? null :
                    <div className="tooltip">
                        <ListRowStat icon={"cubeSolid"}>
                            {" "}{resource.specification.product.id} / {resource.specification.product.category}
                        </ListRowStat>
                    </div>
                }
                {
                    !resource.permissions.myself.includes("ADMIN") || resource.owner.project == null ? null :
                        (props.showGroups === false ||
                            resource.permissions.others == null ||
                            resource.permissions.others.length <= 1) ?
                            <ListRowStat>Not shared with any group</ListRowStat> :
                            <ListRowStat>{resource.permissions.others.length == 1 ? "" : resource.permissions.others.length - 1} {resource.permissions.others.length > 2 ? "groups" : "group"}</ListRowStat>
                }
            </>}
            {RemainingStats ?
                <RemainingStats browseType={props.browseType} resource={resource} callbacks={callbacks} /> : null}
        </>) : renderer.Stats;
        renderer.ImportantStats = ({ resource, callbacks, browseType }) => {
            return <>
                {RemainingImportantStats ?
                    <RemainingImportantStats resource={resource} callbacks={callbacks} browseType={browseType} /> :
                    null
                }

                {
                    !hasFeature(Feature.PROVIDER_CONNECTION) || !resource ? null :
                        !providerConnection.canConnectToProvider(resource.specification.product.provider) ? null :
                        <Link to="/providers/connect">
                            <Tooltip trigger={<Icon name="warning" size={40} color="orange" mx={16} />}>
                                Connection required! You must connect with this provider before you can consume
                                resources.
                            </Tooltip>
                        </Link>
                }

                {
                    hasFeature(Feature.PROVIDER_CONNECTION) ?
                        <Tooltip
                            trigger={
                                <ProviderLogo providerId={resource?.specification?.product?.provider ?? "?"} size={40} />
                            }
                        >
                            This resource is provided by{" "}
                            <ProviderTitle providerId={resource?.specification?.product?.provider ?? "?"} />
                        </Tooltip> : null
                }
            </>;
        };
        return renderer;
    }, [api, props.withDefaultStats, props.inlinePrefix, props.inlineSuffix, products, onProductSelected,
        onInlineCreate, inlineInputRef, selectedProductWithSupport, props.showCreatedAt, props.showCreatedBy,
        props.showProduct, props.showGroups, providerConnection.lastRefresh]);

    const sortOptions = useMemo(() =>
        api.sortEntries.map(it => ({
            icon: it.icon,
            title: it.title,
            value: it.column,
            helpText: it.helpText
        })),
        [api.sortEntries]
    );

    const pageSize = useRef(0);

    const navigateCallback = useCallback((item: Res) => {
        if (providerConnection.canConnectToProvider(item.specification.product.provider)) {
            snackbarStore.addFailure("You must connect to the provider before you can consume resources", true);
            return;
        }

        if (props.navigateToChildren) {
            const result = props.navigateToChildren?.(navigate, item)
            if (result === "properties") {
                viewProperties(item);
            }
        } else {
            viewProperties(item);
        }
    }, [props.navigateToChildren, viewProperties, providerConnection.lastRefresh]);

    const listItem = useCallback<(p: { style, index, data: Res[], isScrolling?: boolean }) => JSX.Element>(
        ({ style, index, data }) => {
            const it = data[index];
            return <div style={style} className={"list-item"}>
                <ItemRowMemo
                    key={it.id}
                    browseType={props.browseType}
                    navigate={navigateCallback}
                    renderer={modifiedRenderer} callbacks={callbacks} operations={operations}
                    item={it} itemTitle={api.title} itemTitlePlural={api.titlePlural}
                    toggleSet={toggleSet} renaming={renaming}
                />
            </div>
        },
        [navigateCallback, modifiedRenderer, callbacks, operations, api.title, api.titlePlural, toggleSet, renaming, 
            providerConnection.lastRefresh]
    );

    const pageRenderer = useCallback<PageRenderer<Res>>((items, opts) => {
        /* HACK(Jonas): to ensure the toggleSet knows of the page contents when checking all. */
        toggleSet.allItems.current = items;
        const allChecked = toggleSet.checked.items.length === items.length && items.length > 0;
        const sizeAllocationForEmbeddedAndCard = Math.min(500, items.length * 56);
        return <>
            {pageSize.current > 0 ? (
                <Spacer mr="8px" left={
                    <Label style={{ cursor: "pointer" }} width={"102px"}>
                        <Checkbox
                            style={{ marginTop: "-2px" }}
                            onChange={() => allChecked ? toggleSet.uncheckAll() : toggleSet.checkAll()}
                            checked={allChecked}
                        />
                        Select all
                    </Label>
                } right={
                    <Flex width="auto">
                        {api.sortEntries.length === 0 ? null : <EnumFilterWidget
                            expanded={false}
                            browseType={BrowseType.Card}
                            propertyName="column"
                            title={sortOptions.find(it => it.value === sortColumn)?.title ?? "Sort By"}
                            facedownChevron
                            id={0}
                            onExpand={doNothing}
                            properties={filters}
                            options={sortOptions}
                            onPropertiesUpdated={updated => onSortUpdated(sortDirection, updated.column)}
                            icon="properties"
                        />}
                        <Box mx="8px" />
                        <EnumFilterWidget
                            expanded={false}
                            browseType={BrowseType.Card}
                            propertyName="direction"
                            title="Sort Direction"
                            facedownChevron
                            id={0}
                            onExpand={doNothing}
                            properties={filters}
                            options={sortDirections}
                            onPropertiesUpdated={updated => onSortUpdated(updated.direction as "ascending" | "descending", sortColumn)}
                            icon={sortDirection === "ascending" ? "sortAscending" : "sortDescending"}
                        />
                    </Flex>
                } />) : <Box height="27px" />}
            <List onContextMenu={preventDefault}>
                {!isCreating ? null :
                    <ItemRow
                        browseType={props.browseType}
                        renderer={modifiedRenderer as ItemRenderer<unknown>}
                        itemTitle={api.title} itemTitlePlural={api.titlePlural} toggleSet={toggleSet}
                        operations={operations} callbacks={callbacks}
                    />
                }
                {items.length > 0 || isCreating ? null : props.emptyPage ? props.emptyPage :
                    <>
                        No {api.titlePlural.toLowerCase()} matches your search/filter criteria.
                        Click &quot;Create {api.title.toLowerCase()}&quot; to create a new one.
                    </>
                }

                {/*
                    TODO(Dan): This height is extremely fragile!

                    NOTE(Dan):
                    - 48px corresponds to the top nav-header
                    - 45px to deal with header of the browse component
                    - 48px to deal with load more button
                    - 6px from padding between header and content
                    - the rest depends entirely on the headerSize of the <MainContainer /> which we load from a
                      global value.
                */}
                <div style={props.browseType == BrowseType.MainContent ?
                    { height: `calc(100vh - 48px - 45px - ${opts.hasNext ? 48 : 0}px - ${headerSize}px - var(--termsize, 0px) - 6px)` } :
                    { height: `${sizeAllocationForEmbeddedAndCard}px` }}
                >
                    <AutoSizer children={({ width, height }) => (
                        <FixedSizeList
                            itemData={items}
                            itemCount={items.length}
                            itemSize={56}
                            width={width}
                            height={height}
                            children={listItem}
                            overscanCount={32}
                        />
                    )} />
                </div>
            </List>
        </>
    }, [toggleSet, isCreating, selectedProduct, props.withDefaultStats, selectedProductWithSupport, renaming,
        viewProperties, operations, providerConnection.lastRefresh]);

    if (!isEmbedded) {
        useTitle(api.titlePlural);
        useLoading(commandLoading);
        useSidebarPage(api.page);
        if (!props.disableSearch) useResourceSearch(api);
    }

    const main = !inlineInspecting ? <>
        <StandardBrowse isSearch={props.isSearch} browseType={props.browseType} pageSizeRef={pageSize}
            generateCall={generateFetch} pageRenderer={pageRenderer} reloadRef={reloadRef}
            setRefreshFunction={isEmbedded != true} onLoad={props.onResourcesLoaded}
            preloadedResources={props.resources} />
    </> : <>
        <api.Properties api={api} resource={inlineInspecting} reload={reloadRef.current} embedded={true}
            closeProperties={closeProperties} {...props.propsForInlineResources} />
    </>;

    const allPills = useMemo(() => {
        const result = [...api.filterPills];
        if (props.isSearch) {
            result.push(p => <StaticPill icon={"search"} title={"Query"} value={"Query: " + query} {...p} />)
        }
        return result;
    }, [api.filterPills, query, props.isSearch]);

    if (!canConsumeResources) {
        const main = <Flex height={"400px"} alignItems={"center"} justifyContent={"center"}>
            <div>
                <Heading.h3 style={{ textAlign: "center" }}>This project cannot consume resources</Heading.h3>
                <p>
                    This property is set for certain projects which are only meant for allocating resources. If you wish
                    to consume any of these resources for testing purposes, then please allocate resources to a small
                    separate test project. This can be done from the "Resource Allocations" menu in the project
                    management interface.
                </p>

                <p>
                    <b>NOTE:</b> All resources created prior to this update are still available. If you need to transfer
                    old resources to a new project, then please contact support.
                </p>
            </div>
        </Flex>;
        if (isEmbedded) {
            return <Box minWidth="700px">
                {main}
            </Box>;
        } else {
            return <MainContainer
                header={props.header}
                headerSize={props.headerSize}
                main={main}
            />;
        }
    }

    if (isEmbedded) {
        return <Box minWidth="700px" ref={scrollingContainerRef}>
            {/* Sticky box causes rerender. See "Bug"-tag above. */}
            {/* <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}> */}
            {inlineInspecting ?
                <Heading.h3 flexGrow={1}>{api.titlePlural}</Heading.h3> :
                <>
                    <Operations selected={toggleSet.checked.items} location={"TOPBAR"}
                        entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                        extra={callbacks} operations={operations} />
                    {props.header}
                    <ResourceFilter
                        pills={allPills} filterWidgets={api.filterWidgets} browseType={props.browseType}
                        sortEntries={api.sortEntries} sortDirection={sortDirection}
                        onSortUpdated={onSortUpdated} properties={filters} setProperties={setFilters}
                        readOnlyProperties={props.additionalFilters} />
                </>
            }
            {/* </StickyBox> */}
            {main}
        </Box>;
    } else {
        return <MainContainer
            header={props.header}
            headerSize={props.headerSize}
            main={main}
            sidebar={<Flex flexDirection={"column"} height={"100%"} pb={"16px"}>
                {inlineInspecting ? null :
                    <>
                        <Operations selected={toggleSet.checked.items} location={"SIDEBAR"}
                            entityNameSingular={api.title} entityNamePlural={api.titlePlural}
                            extra={callbacks} operations={operations} />

                        <ResourceFilter pills={allPills} filterWidgets={api.filterWidgets}
                            sortEntries={api.sortEntries} sortDirection={sortDirection}
                            onSortUpdated={onSortUpdated} properties={filters} setProperties={setFilters}
                            browseType={props.browseType}
                            readOnlyProperties={props.additionalFilters} />
                    </>
                }

                {!props.extraSidebar ? null : props.extraSidebar}
            </Flex>}
        />
    }
}

function UserBox(props: { username: string }) {
    const avatars = useAvatars();
    const avatar = avatars.avatar(props.username);
    return <div className="user-box" style={{ display: "relative" }}>
        <div className="centered"><Avatar style={{ marginTop: "-70px", width: "150px", marginBottom: "-70px" }}
            avatarStyle="circle" {...avatar} /></div>
        <div className="centered" style={{ display: "flex", justifyContent: "center" }}>
            <Truncate mt="18px" fontSize="2em" mx="24px" width="100%">{props.username}</Truncate>
        </div>
        {/* Re-add when we know what to render below  */}
        {/* <div style={{justifyContent: "left", textAlign: "left"}}>
            <div><b>INFO:</b> The lifespan of foxes depend on where they live.</div>
            <div><b>INFO:</b> A fox living in the city, usually lives 3-5 years.</div>
            <div><b>INFO:</b> A fox living in a forest, usually lives 12-15 years.</div>
        </div> */}
    </div>;
}
