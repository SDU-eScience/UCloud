import * as React from "react";
import {Link, useSearchParams} from "react-router-dom";
import {
    browseWalletsV2,
    frequencyToSuffix,
    ProductCategoryV2, ProductType,
    WalletOwner,
    WalletV2,
} from "@/Accounting";
import {apiBrowse, callAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {Box, Flex, Icon, MainContainer} from "@/ui-components";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import {isAdminOrPI} from "@/Project";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import HexSpin from "@/LoadingIcon/LoadingIcon";
import {errorMessageOrDefault} from "@/UtilityFunctions";
import {dateToString} from "@/Utilities/DateUtilities";
import {PageV2, PaginationRequestV2} from "@/UCloud";
import AppRoutes from "@/Routes";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {injectStyle} from "@/Unstyled";
import {FixedSizeList, ListChildComponentProps} from "react-window";
import AutoSizer from "react-virtualized-auto-sizer";
import {fetchAll} from "@/Utilities/PageUtilities";
import {RichSelect, RichSelectProps} from "@/ui-components/RichSelect";
import {TooltipV2} from "@/ui-components/Tooltip";

type UsageBreakdownResourceType = "drive" | "job";

interface UsageBreakdownResource {
    type: UsageBreakdownResourceType;
    id: string;
}

interface UsageBreakdownResourceItem {
    usage: number;
    resource: UsageBreakdownResource;
    lastUpdatedAt: number;
    workspace: WalletOwner;
    workspaceTitle: string;
    title: string;
    createdBy: string;
    isExternallyFunded: boolean;
}

type UsageBreakdownItem = UsageBreakdownResourceItem;

type UsageBreakdownSortBy = "usage" | "reportedAt";
type UsageBreakdownSortDirection = "ascending" | "descending";

interface UsageBreakdownBrowseRequest extends PaginationRequestV2 {
    categoryName: string;
    categoryProvider: string;
    filterProject?: string;
    filterCreatedBy?: string;
    filterReportedAtMin?: number;
    filterReportedAtMax?: number;
    filterUsageMin?: number;
    filterUsageMax?: number;
    sortBy?: UsageBreakdownSortBy;
    sortDirection?: UsageBreakdownSortDirection;
}

interface UsageBreakdownBrowseResponse {
    items: UsageBreakdownItem[];
    itemsPerPage: number;
    next?: string;
    totalUsage: number;
    totalCount: number;
}

interface UsageBreakdownCategoryState {
    wallet: WalletV2;
    items: UsageBreakdownItem[];
    next?: string;
    totalUsage: number;
    totalCount: number;
    loadingMore: boolean;
    error: string | null;
}

interface UsageBreakdownPageState {
    workspace: string;
    loading: boolean;
    error: string | null;
    categories: UsageBreakdownCategoryState[];
}

interface UsageBreakdownCategorySelectOption {
    key: string;
    search: string;
    category: UsageBreakdownCategoryState;
}

interface UsageBreakdownProviderSelectOption {
    key: string;
    search: string;
}

interface UsageBreakdownEntityFilter {
    type: "project" | "createdBy";
    value: string;
}

interface UsageBreakdownVirtualListData {
    category: ProductCategoryV2;
    items: UsageBreakdownItem[];
    projectId: string | undefined;
    onFilterEntity: (type: UsageBreakdownEntityFilter["type"], value: string) => void;
}

const usageBreakdownContext = "/api/accounting/v2/usageBreakdown";
const usageBreakdownNumberFormatter = new Intl.NumberFormat("da-DK", {minimumFractionDigits: 1, maximumFractionDigits: 1});

const UsageBreakdownStyle = injectStyle("usage-breakdown", k => `
    ${k} .usage-breakdown-table {
        overflow-x: auto;
    }

    ${k} .usage-breakdown-grid-container {
        min-width: 1060px;
    }

    ${k} .usage-breakdown-grid {
        display: grid;
        grid-template-columns: minmax(200px, 0.8fr) minmax(280px, 1.3fr) minmax(220px, 1fr) 180px 180px;
    }

    ${k} .usage-breakdown-grid-header {
        background: var(--tableBackground);
        border: 1px solid var(--borderColor);
        border-radius: 8px 8px 0 0;
        font-weight: bold;
    }

    ${k} .usage-breakdown-grid-row {
        border-bottom: 1px solid var(--borderColor);
        box-sizing: border-box;
    }

    ${k} .usage-breakdown-grid-row:hover {
        background: var(--rowHover);
    }

    ${k} .usage-breakdown-grid-cell {
        align-items: center;
        display: flex;
        min-width: 0;
        padding: 10px 12px;
    }

    ${k} .usage-breakdown-grid-cell + .usage-breakdown-grid-cell {
        border-left: 1px solid var(--borderColor);
    }

    ${k} .usage-breakdown-grid-cell-usage {
        justify-content: flex-end;
        text-align: l;
    }

    ${k} .usage-breakdown-virtual-list {
        border-bottom: 1px solid var(--borderColor);
        border-left: 1px solid var(--borderColor);
        border-right: 1px solid var(--borderColor);
        box-sizing: border-box;
        overflow-x: hidden !important;
    }

    ${k} .usage-breakdown-list-container {
        height: calc(100vh - 360px);
        min-height: 240px;
    }

    ${k} .usage-breakdown-resource-id {
        color: var(--textSecondary);
        font-size: 12px;
        overflow-wrap: anywhere;
    }

    ${k} .usage-breakdown-resource-name,
    ${k} .usage-breakdown-workspace-name {
        min-width: 0;
        overflow: hidden;
    }

    ${k} .usage-breakdown-workspace {
        min-width: 0;
        overflow: hidden;
        width: 100%;
    }

    ${k} .usage-breakdown-workspace-name {
        flex: 1;
        width: 0;
    }

    ${k} .usage-breakdown-resource-name > a,
    ${k} .usage-breakdown-workspace-name > button,
    ${k} .usage-breakdown-workspace-name > span,
    ${k} .usage-breakdown-created-by > button {
        display: block;
        max-width: 100%;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    ${k} .usage-breakdown-workspace-name > button,
    ${k} .usage-breakdown-workspace-name > span,
    ${k} .usage-breakdown-created-by > button {
        width: 100%;
    }

    ${k} .usage-breakdown-resource-name > a {
        flex: 1;
        min-width: 0;
    }

    ${k} .usage-breakdown-created-by {
        overflow: hidden;
    }

    ${k} .usage-breakdown-link-button,
    ${k} .usage-breakdown-sort-button {
        background: transparent;
        border: 0;
        color: var(--primaryMain);
        cursor: pointer;
        font: inherit;
        padding: 0;
        text-align: left;
    }

    ${k} .usage-breakdown-sort-button {
        color: inherit;
        font-weight: bold;
    }

    ${k} .usage-breakdown-filter-panel {
        background: var(--tableBackground);
        border: 1px solid var(--borderColor);
        border-radius: 8px;
        padding: 12px 16px;
    }

    ${k} .usage-breakdown-period-button {
        background: transparent;
        border: 1px solid var(--borderColor);
        cursor: pointer;
        padding: 6px 10px;
    }

    ${k} .usage-breakdown-period-button:first-child {
        border-radius: 6px 0 0 6px;
    }

    ${k} .usage-breakdown-period-button:last-child {
        border-radius: 0 6px 6px 0;
    }

    ${k} .usage-breakdown-period-button-active {
        background: var(--primaryMain);
        border-color: var(--primaryMain);
        color: var(--primaryContrast);
    }

    ${k} .usage-breakdown-selectors > * {
        min-width: 0;
        width: 50%;
    }

    @media (max-width: 600px) {
        ${k} .usage-breakdown-page-header {
            align-items: flex-start;
            flex-direction: column;
            gap: 12px;
        }

        ${k} .usage-breakdown-list-container {
            height: calc(100vh - 500px);
        }

        ${k} .usage-breakdown-selectors {
            flex-direction: column;
        }

        ${k} .usage-breakdown-selectors > * {
            width: 100%;
        }
    }
`);

export function usageBreakdownBrowse(request: UsageBreakdownBrowseRequest): APICallParameters<UsageBreakdownBrowseRequest, UsageBreakdownBrowseResponse> {
    return apiBrowse(request, usageBreakdownContext);
}

function usageBreakdownCategoryKey(category: ProductCategoryV2): string {
    return JSON.stringify([category.provider, category.name]);
}

function usageBreakdownFormatNumber(value: number): string {
    return usageBreakdownNumberFormatter.format(value);
}

function usageBreakdownDecodedUsage(category: ProductCategoryV2, usage: number): number {
    return category.accountingUnit.floatingPoint ? usage / 1_000_000 : usage;
}

function usageBreakdownFormatUsage(category: ProductCategoryV2, usage: number): string {
    const decodedUsage = usageBreakdownDecodedUsage(category, usage);
    if (category.productType === "STORAGE") {
        return `${usageBreakdownFormatNumber(decodedUsage)} GB`;
    }

    const factorsToHours = {
        ONCE: 1,
        PERIODIC_MINUTE: 1 / 60,
        PERIODIC_HOUR: 1,
        PERIODIC_DAY: 24,
    };
    const value = decodedUsage * factorsToHours[category.accountingFrequency];
    return `${usageBreakdownFormatNumber(value)} ${category.accountingUnit.name}-hours`;
}

function usageBreakdownFormatRawUsage(category: ProductCategoryV2, usage: number): string {
    const rawUsage = usageBreakdownDecodedUsage(category, usage);
    const frequency = frequencyToSuffix(category.accountingFrequency, true);
    const unit = category.accountingUnit.displayFrequencySuffix && frequency ?
        `${category.accountingUnit.name}-${frequency}` : category.accountingUnit.namePlural;
    return `${usageBreakdownFormatNumber(rawUsage)} ${unit}`;
}

function usageBreakdownCategoryIcon(category: ProductCategoryV2): "ftFileSystem" | "gpu" | "heroCpuChip" {
    if (category.productType === "STORAGE") return "ftFileSystem";
    return category.accountingUnit.name === "GPU" ? "gpu" : category.name.startsWith("gpu-") ? "gpu" : "heroCpuChip";
}

async function usageBreakdownMapConcurrent<T, R>(items: T[], concurrency: number, fn: (item: T) => Promise<R>): Promise<R[]> {
    const result = new Array<R>(items.length);
    let nextIndex = 0;

    async function worker(): Promise<void> {
        while (nextIndex < items.length) {
            const index = nextIndex++;
            result[index] = await fn(items[index]);
        }
    }

    await Promise.all(Array.from({length: Math.min(concurrency, items.length)}, worker));
    return result;
}

function usageBreakdownWorkspaceId(workspace: WalletOwner): string {
    return workspace.type === "project" ? workspace.projectId : workspace.username;
}

function UsageBreakdownResourceName({item}: {item: UsageBreakdownItem}): React.ReactNode {
    const route = item.resource.type === "drive" ? AppRoutes.files.drive(item.resource.id) : AppRoutes.jobs.view(item.resource.id);

    return <Flex className="usage-breakdown-resource-name" alignItems="baseline" gap="8px">
        <Link to={route} title={item.title || item.resource.id}><b>{item.title || item.resource.id}</b></Link>
        {item.title ? <span className="usage-breakdown-resource-id">{item.resource.id}</span> : null}
    </Flex>;
}

function UsageBreakdownWorkspaceName({item, projectId, onFilterEntity}: {
    item: UsageBreakdownItem;
    projectId?: string;
    onFilterEntity: UsageBreakdownVirtualListData["onFilterEntity"];
}): React.ReactNode {
    const externalWorkspace = item.workspace.type === "project" ? item.workspace.projectId !== projectId : item.workspace.username !== Client.username;
    const workspaceId = usageBreakdownWorkspaceId(item.workspace);
    const title = item.workspaceTitle || workspaceId;

    return <Flex className="usage-breakdown-workspace" alignItems="center" gap="8px" minWidth={0}>
        <Box className="usage-breakdown-workspace-name" flexGrow={1} minWidth={0} title={title}>
            {externalWorkspace ? <button
                className="usage-breakdown-link-button"
                onClick={() => onFilterEntity(item.workspace.type === "project" ? "project" : "createdBy", workspaceId)}
                type="button"
            >{title}</button> : <span>{title}</span>}
        </Box>
        {!item.isExternallyFunded ? null : <TooltipV2 tooltip="This workspace also receives funding from outside this project. Not all usage shown for this resource is billed directly to this project.">
            <Icon name="heroExclamationTriangle" color="warningMain" size={18} />
        </TooltipV2>}
    </Flex>;
}

function usageBreakdownItemKey(item: UsageBreakdownItem): string {
    return `resource-${item.resource.type}-${item.resource.id}`;
}

function usageBreakdownVirtualItemKey(index: number, data: UsageBreakdownVirtualListData): string {
    return usageBreakdownItemKey(data.items[index]);
}

const UsageBreakdownVirtualListOuter = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>((props, ref) =>
    <div
        {...props}
        ref={ref}
        role="rowgroup"
        tabIndex={0}
        aria-label="Usage entries. Scroll or use the arrow keys to view more rows."
    />
);
UsageBreakdownVirtualListOuter.displayName = "UsageBreakdownVirtualListOuter";

function UsageBreakdownVirtualRow({index, style, data}: ListChildComponentProps<UsageBreakdownVirtualListData>): React.ReactNode {
    const item = data.items[index];
    return <div
        className="usage-breakdown-grid usage-breakdown-grid-row"
        role="row"
        aria-rowindex={index + 2}
        style={style}
    >
        <div className="usage-breakdown-grid-cell" role="cell">
            <UsageBreakdownResourceName item={item} />
        </div>
        <div className="usage-breakdown-grid-cell" role="cell">
            <UsageBreakdownWorkspaceName item={item} projectId={data.projectId} onFilterEntity={data.onFilterEntity} />
        </div>
        <div className="usage-breakdown-grid-cell usage-breakdown-created-by" role="cell">
            {item.resource.type !== "job" || !item.createdBy ? "-" : <button
                className="usage-breakdown-link-button"
                onClick={() => data.onFilterEntity("createdBy", item.createdBy)}
                title={item.createdBy}
                type="button"
            >{item.createdBy}</button>}
        </div>
        <div className="usage-breakdown-grid-cell" role="cell">
            {dateToString(item.lastUpdatedAt)}
        </div>
        <div className="usage-breakdown-grid-cell usage-breakdown-grid-cell-usage" role="cell">
            <TooltipV2 tooltip={usageBreakdownFormatRawUsage(data.category, item.usage)}>
                <b>{usageBreakdownFormatUsage(data.category, item.usage)}</b>
            </TooltipV2>
        </div>
    </div>;
}

function UsageBreakdownCategoryOption({element, onSelect, dataProps}: RichSelectProps<UsageBreakdownCategorySelectOption>): React.ReactNode {
    if (!element) return null;
    const category = element.category.wallet.paysFor;
    return <Flex
        alignItems="center"
        gap="12px"
        px="12px"
        py="8px"
        onClick={onSelect}
        {...dataProps}
    >
        <Icon name={usageBreakdownCategoryIcon(category)} size={28} />
        <Box flexGrow={1} minWidth={0}>
            <h3 style={{margin: 0}}>{category.name}</h3>
        </Box>
    </Flex>;
}

function UsageBreakdownProviderOption({element, onSelect, dataProps}: RichSelectProps<UsageBreakdownProviderSelectOption>): React.ReactNode {
    if (!element) return null;
    return <Flex
        alignItems="center"
        gap="12px"
        px="12px"
        py="8px"
        onClick={onSelect}
        {...dataProps}
    >
        <ProviderLogo providerId={element.key} size={28} />
        <h3 style={{margin: 0}}>{getProviderTitle(element.key)}</h3>
    </Flex>;
}

function UsageBreakdownCategory({category, projectId, loadMore, onFilterEntity, sortBy, sortDirection, onSort}: {
    category: UsageBreakdownCategoryState;
    projectId?: string;
    loadMore: (category: UsageBreakdownCategoryState) => void;
    onFilterEntity: UsageBreakdownVirtualListData["onFilterEntity"];
    sortBy: UsageBreakdownSortBy;
    sortDirection: UsageBreakdownSortDirection;
    onSort: (sortBy: UsageBreakdownSortBy) => void;
}): React.ReactNode {
    const paysFor = category.wallet.paysFor;
    const sortIndicator = (column: UsageBreakdownSortBy) => sortBy !== column ? "" : sortDirection === "ascending" ? " ↑" : " ↓";

    return <div>
        {category.items.length === 0 ?
            <p style={{color: "var(--textSecondary)"}}>
                {category.error ?? "No drive or job usage is currently available in this category."}
            </p> :
            <div className="usage-breakdown-table" role="table" aria-rowcount={category.items.length + 1}>
                <div className="usage-breakdown-grid-container">
                    <div className="usage-breakdown-grid usage-breakdown-grid-header" role="row" aria-rowindex={1}>
                        <div className="usage-breakdown-grid-cell" role="columnheader">Resource</div>
                        <div className="usage-breakdown-grid-cell" role="columnheader">Workspace</div>
                        <div className="usage-breakdown-grid-cell" role="columnheader">Created by</div>
                        <div className="usage-breakdown-grid-cell" role="columnheader">
                            <button className="usage-breakdown-sort-button" onClick={() => onSort("reportedAt")} type="button">
                                Last reported{sortIndicator("reportedAt")}
                            </button>
                        </div>
                        <div className="usage-breakdown-grid-cell" role="columnheader">
                            <button className="usage-breakdown-sort-button" onClick={() => onSort("usage")} type="button">
                                Usage{sortIndicator("usage")}
                            </button>
                        </div>
                    </div>
                    <div className="usage-breakdown-list-container">
                        <AutoSizer>
                            {({height, width}) => <FixedSizeList<UsageBreakdownVirtualListData>
                                className="usage-breakdown-virtual-list"
                                height={height}
                                itemCount={category.items.length}
                                itemData={{category: paysFor, items: category.items, projectId, onFilterEntity}}
                                itemKey={usageBreakdownVirtualItemKey}
                                itemSize={56}
                                onItemsRendered={({visibleStopIndex}) => {
                                    if (category.next && !category.loadingMore && !category.error && visibleStopIndex >= category.items.length - 20) {
                                        loadMore(category);
                                    }
                                }}
                                outerElementType={UsageBreakdownVirtualListOuter}
                                overscanCount={8}
                                width={width}
                            >
                                {UsageBreakdownVirtualRow}
                            </FixedSizeList>}
                        </AutoSizer>
                    </div>
                </div>
            </div>
        }

        {category.items.length === 0 || !category.error ? null : <p>{category.error}</p>}

    </div>;
}

const UsageBreakdownPage: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const project = useProject();
    const [searchParams, setSearchParams] = useSearchParams();
    const projectDetails = project.fetch();
    const projectDetailsReady = !projectId || projectDetails.id === projectId;
    const canView = !projectId || projectDetailsReady && isAdminOrPI(projectDetails.status.myRole);
    const [state, setState] = React.useState<UsageBreakdownPageState>({workspace: projectId ?? "", loading: true, error: null, categories: []});
    const selectedProviderFromQuery = searchParams.get("provider");
    const selectedCategoryFromQuery = searchParams.get("category");
    const selectedCategoryKey = selectedProviderFromQuery && selectedCategoryFromQuery ?
        JSON.stringify([selectedProviderFromQuery, selectedCategoryFromQuery]) : null;
    const filterProject = searchParams.get("filterProject");
    const filterCreatedBy = searchParams.get("filterCreatedBy");
    const period = searchParams.get("period");
    const filterDays: 30 | 90 | null = period === "30" ? 30 : period === "90" ? 90 : null;
    const entityFilter = React.useMemo<UsageBreakdownEntityFilter | null>(() => {
        const type = filterProject ? "project" : filterCreatedBy ? "createdBy" : null;
        const value = filterProject ?? filterCreatedBy;
        if (!type || !value) return null;
        return {type, value};
    }, [filterProject, filterCreatedBy]);
    const reportedAfter = React.useMemo(() => filterDays === null ? undefined : Date.now() - filterDays * 24 * 60 * 60 * 1000, [filterDays]);
    const sortBy: UsageBreakdownSortBy = searchParams.get("sortBy") === "usage" ? "usage" : "reportedAt";
    const sortDirection: UsageBreakdownSortDirection = searchParams.get("sortDirection") === "ascending" ? "ascending" : "descending";
    const requestGeneration = React.useRef(0);
    const availableCategoryKeys = React.useRef(new Set<string>());
    const previousProjectId = React.useRef(projectId);

    usePage("Usage breakdown", SidebarTabId.PROJECT);

    React.useEffect(() => {
        if (previousProjectId.current === projectId) return;
        previousProjectId.current = projectId;
        availableCategoryKeys.current.clear();
        const next = new URLSearchParams(searchParams);
        next.delete("filterProject");
        next.delete("filterCreatedBy");
        setSearchParams(next, {replace: true});
    }, [projectId, searchParams, setSearchParams]);

    React.useEffect(() => {
        let cancelled = false;
        const generation = ++requestGeneration.current;
        const workspace = projectId ?? "";
        if (!projectDetailsReady || !canView) {
            setState({workspace, loading: !projectDetailsReady, error: null, categories: []});
            return;
        }

        setState({workspace, loading: true, error: null, categories: []});
        (async () => {
            try {
                const wallets = await fetchAll<WalletV2>(next => {
                    if (cancelled || requestGeneration.current !== generation) {
                        return Promise.reject(new Error("Usage breakdown request cancelled"));
                    }
                    return callAPI<PageV2<WalletV2>>({
                        ...browseWalletsV2({itemsPerPage: 250, next}),
                        projectOverride: workspace,
                    });
                });
                if (cancelled || requestGeneration.current !== generation) return;
                const relevantWallets = wallets.filter(wallet =>
                    wallet.paysFor.productType === "COMPUTE" || wallet.paysFor.productType === "STORAGE"
                );
                const categories = await usageBreakdownMapConcurrent(relevantWallets, 6, async wallet => {
                    if (cancelled || requestGeneration.current !== generation) {
                        throw new Error("Usage breakdown request cancelled");
                    }
                    try {
                        const page = await callAPI<UsageBreakdownBrowseResponse>({
                            ...usageBreakdownBrowse({
                                itemsPerPage: 250,
                                categoryName: wallet.paysFor.name,
                                categoryProvider: wallet.paysFor.provider,
                                filterProject: entityFilter?.type === "project" ? entityFilter.value : undefined,
                                filterCreatedBy: entityFilter?.type === "createdBy" ? entityFilter.value : undefined,
                                filterReportedAtMin: wallet.paysFor.productType === "COMPUTE" ? reportedAfter : undefined,
                                sortBy,
                                sortDirection,
                            }),
                            projectOverride: workspace,
                        });
                        return {
                            wallet,
                            items: page.items,
                            next: page.next,
                            totalUsage: page.totalUsage,
                            totalCount: page.totalCount,
                            loadingMore: false,
                            error: null,
                        };
                    } catch (error) {
                        return {
                            wallet,
                            items: [],
                            totalUsage: 0,
                            totalCount: 0,
                            loadingMore: false,
                            error: errorMessageOrDefault(error, "Could not load this usage category"),
                        };
                    }
                });
                const selectableCategories = categories.filter(category => {
                    if (entityFilter === null && reportedAfter === undefined) return category.items.length > 0 || category.error !== null;
                    const key = usageBreakdownCategoryKey(category.wallet.paysFor);
                    if (availableCategoryKeys.current.size > 0) return availableCategoryKeys.current.has(key);
                    return key === selectedCategoryKey || category.items.length > 0 || category.error !== null;
                });
                if (entityFilter === null && reportedAfter === undefined) {
                    availableCategoryKeys.current = new Set(selectableCategories.map(category => usageBreakdownCategoryKey(category.wallet.paysFor)));
                }
                selectableCategories.sort((a, b) => usageBreakdownCategoryKey(a.wallet.paysFor).localeCompare(usageBreakdownCategoryKey(b.wallet.paysFor)));
                const initiallySelected = selectableCategories.find(category => usageBreakdownCategoryKey(category.wallet.paysFor) === selectedCategoryKey) ??
                    selectableCategories.find(category => category.items.length > 0) ?? selectableCategories[0];
                if (!cancelled && requestGeneration.current === generation) {
                    React.startTransition(() => {
                        setState({workspace, loading: false, error: null, categories: selectableCategories});
                    });
                    if (initiallySelected && usageBreakdownCategoryKey(initiallySelected.wallet.paysFor) !== selectedCategoryKey) {
                        const next = new URLSearchParams(searchParams);
                        next.set("provider", initiallySelected.wallet.paysFor.provider);
                        next.set("category", initiallySelected.wallet.paysFor.name);
                        setSearchParams(next, {replace: true});
                    }
                }
            } catch (error) {
                if (!cancelled && requestGeneration.current === generation) setState({
                    workspace,
                    loading: false,
                    error: errorMessageOrDefault(error, "Could not load the usage breakdown"),
                    categories: [],
                });
            }
        })();

        return () => {cancelled = true};
    }, [projectId, projectDetailsReady, canView, entityFilter, reportedAfter, sortBy, sortDirection]);

    async function loadMore(category: UsageBreakdownCategoryState): Promise<void> {
        if (!category.next || category.loadingMore) return;
        const generation = requestGeneration.current;
        const workspace = projectId ?? "";
        const categoryKey = usageBreakdownCategoryKey(category.wallet.paysFor);
        const next = category.next;
        setState(current => ({
            ...current,
            categories: current.workspace !== workspace || requestGeneration.current !== generation ? current.categories : current.categories.map(item => usageBreakdownCategoryKey(item.wallet.paysFor) === categoryKey ?
                {...item, loadingMore: true} : item),
        }));

        try {
            const page = await callAPI<UsageBreakdownBrowseResponse>({
                ...usageBreakdownBrowse({
                    itemsPerPage: 250,
                    next,
                    categoryName: category.wallet.paysFor.name,
                    categoryProvider: category.wallet.paysFor.provider,
                    filterProject: entityFilter?.type === "project" ? entityFilter.value : undefined,
                    filterCreatedBy: entityFilter?.type === "createdBy" ? entityFilter.value : undefined,
                    filterReportedAtMin: category.wallet.paysFor.productType === "COMPUTE" ? reportedAfter : undefined,
                    sortBy,
                    sortDirection,
                }),
                projectOverride: workspace,
            });
            setState(current => ({
                ...current,
                categories: current.workspace !== workspace || requestGeneration.current !== generation ? current.categories : current.categories.map(item => usageBreakdownCategoryKey(item.wallet.paysFor) === categoryKey ? {
                    ...item,
                    items: [...item.items, ...page.items],
                    next: page.next,
                    totalUsage: page.totalUsage,
                    totalCount: page.totalCount,
                    loadingMore: false,
                    error: null,
                } : item),
            }));
        } catch (error) {
            setState(current => ({
                ...current,
                categories: current.workspace !== workspace || requestGeneration.current !== generation ? current.categories : current.categories.map(item => usageBreakdownCategoryKey(item.wallet.paysFor) === categoryKey ? {
                    ...item,
                    loadingMore: false,
                    error: errorMessageOrDefault(error, "Could not load more usage entries"),
                } : item),
            }));
        }
    }

    function selectCategory(category: ProductCategoryV2): void {
        const next = new URLSearchParams(searchParams);
        next.set("provider", category.provider);
        next.set("category", category.name);
        setSearchParams(next);
    }

    function filterEntity(type: UsageBreakdownEntityFilter["type"], value: string): void {
        const next = new URLSearchParams(searchParams);
        next.delete(type === "project" ? "filterCreatedBy" : "filterProject");
        next.set(type === "project" ? "filterProject" : "filterCreatedBy", value);
        setSearchParams(next);
    }

    function setFilterDays(days: 30 | 90 | null): void {
        const next = new URLSearchParams(searchParams);
        if (days === null) next.delete("period");
        else next.set("period", String(days));
        setSearchParams(next);
    }

    function clearEntityFilter(): void {
        const next = new URLSearchParams(searchParams);
        next.delete("filterProject");
        next.delete("filterCreatedBy");
        setSearchParams(next);
    }

    function changeSort(column: UsageBreakdownSortBy): void {
        const next = new URLSearchParams(searchParams);
        if (column === sortBy) {
            next.set("sortDirection", sortDirection === "ascending" ? "descending" : "ascending");
        } else {
            next.set("sortBy", column);
            next.set("sortDirection", "descending");
        }
        setSearchParams(next);
    }

    if (!projectDetailsReady) {
        return <MainContainer main={<HexSpin />} />;
    }

    if (projectId && projectDetails.status.personalProviderProjectFor != null) {
        return <MainContainer main={<>
            <h2>Unavailable for this project</h2>
            <p>This project belongs to a provider which does not support UCloud accounting and project management.</p>
        </>} />;
    }

    if (!canView) {
        return <MainContainer main={<>
            <h2>Usage breakdown</h2>
            <p>You need to be a project administrator or principal investigator to view detailed usage.</p>
        </>} />;
    }

    const selectedCategory = state.categories.find(category =>
        usageBreakdownCategoryKey(category.wallet.paysFor) === selectedCategoryKey
    );
    const selectedProvider = selectedCategory?.wallet.paysFor.provider;
    const providerOptions: UsageBreakdownProviderSelectOption[] = Array.from(new Set(
        state.categories.map(category => category.wallet.paysFor.provider)
    )).map(provider => ({
        key: provider,
        search: getProviderTitle(provider),
    }));
    const selectedProviderOption = providerOptions.find(option => option.key === selectedProvider);
    const categoryOptions: UsageBreakdownCategorySelectOption[] = state.categories
        .filter(category => category.wallet.paysFor.provider === selectedProvider)
        .map(category => ({
            key: usageBreakdownCategoryKey(category.wallet.paysFor),
            search: category.wallet.paysFor.name,
            category,
        }))
        .sort((a,  b) => {
            const typeToPriority: Record<ProductType, number> = {
                STORAGE: 0,
                COMPUTE: 1,
                INFERENCE: 2,
                INGRESS: 3,
                LICENSE: 4,
                NETWORK_IP: 5,
                PRIVATE_NETWORK: 6,
            };
            const aCat = a.category.wallet.paysFor;
            const bCat = b.category.wallet.paysFor;

            if (typeToPriority[aCat.productType] < typeToPriority[bCat.productType]) {
                return -1;
            } else if (typeToPriority[aCat.productType] > typeToPriority[bCat.productType]) {
                return 1;
            }

            let c = aCat.accountingUnit.name.localeCompare(bCat.accountingUnit.name);
            if (c !== 0) return c;

            c = aCat.name.localeCompare(bCat.name);
            if (c !== 0) return c;
            return 0;
        })
    ;
    const selectedCategoryOption = categoryOptions.find(option => option.key === selectedCategoryKey);
    const selectedResourceType = selectedCategory?.items[0]?.resource.type ?? (selectedCategory?.wallet.paysFor.productType === "STORAGE" ? "drive" : "job");
    const selectedResourceLabel = `${selectedCategory?.totalCount ?? 0} ${selectedCategory?.totalCount === 1 ? selectedResourceType : selectedResourceType + "s"}`;
    const entityFilterLabel = !entityFilter ? null : entityFilter.type === "createdBy" ? entityFilter.value :
        selectedCategory?.items.find(item => item.workspace.type === "project" && item.workspace.projectId === entityFilter.value)?.workspaceTitle || entityFilter.value;

    return <MainContainer main={<Flex className={UsageBreakdownStyle} flexDirection="column" gap="24px">
        <Flex className="usage-breakdown-page-header" alignItems="center">
            <h3 className="title" style={{margin: 0}}>Usage breakdown</h3>
            <Box flexGrow={1} />
            <ProjectSwitcher />
        </Flex>

        {state.workspace !== (projectId ?? "") || state.loading ? <HexSpin /> : state.error ? <p>{state.error}</p> : state.categories.length === 0 ?
            <p>No usage information is currently available.</p> :
            <>
                <Flex className="usage-breakdown-selectors" gap="15px" maxWidth="800px">
                    <Box>
                        <RichSelect
                            items={providerOptions}
                            keys={["search"]}
                            selected={selectedProviderOption}
                            onSelect={option => {
                                const category = state.categories.find(item => item.wallet.paysFor.provider === option.key);
                                if (category) selectCategory(category.wallet.paysFor);
                            }}
                            RenderRow={UsageBreakdownProviderOption}
                            RenderSelected={UsageBreakdownProviderOption}
                            elementHeight={52}
                            fullWidth
                        />
                    </Box>
                    <Box>
                        <RichSelect
                            items={categoryOptions}
                            keys={["search"]}
                            selected={selectedCategoryOption}
                            onSelect={option => selectCategory(option.category.wallet.paysFor)}
                            RenderRow={UsageBreakdownCategoryOption}
                            RenderSelected={UsageBreakdownCategoryOption}
                            elementHeight={52}
                            fullWidth
                        />
                    </Box>
                </Flex>
                {!selectedCategory ? null : <Flex className="usage-breakdown-filter-panel" alignItems="center" flexWrap="wrap" gap="12px">
                    <Box>
                        <TooltipV2 tooltip={usageBreakdownFormatRawUsage(selectedCategory.wallet.paysFor, selectedCategory.totalUsage)}>
                            <b>{usageBreakdownFormatUsage(selectedCategory.wallet.paysFor, selectedCategory.totalUsage)} total</b>
                        </TooltipV2>
                        <div className="usage-breakdown-resource-id">
                            {entityFilter ? `${entityFilterLabel}: ` : ""}{selectedResourceLabel}{selectedResourceType === "job" && filterDays !== null ? ` in the last ${filterDays} days` : ""}
                        </div>
                    </Box>
                    <Box flexGrow={1} />
                    {selectedResourceType !== "job" ? null : <Flex>
                            {([{label: "No limit", value: null}, {label: "30 days", value: 30}, {label: "90 days", value: 90}] as const).map(option => <button
                                className={`usage-breakdown-period-button${filterDays === option.value ? " usage-breakdown-period-button-active" : ""}`}
                                key={option.label}
                                onClick={() => setFilterDays(option.value)}
                                type="button"
                            >{option.label}</button>)}
                        </Flex>}
                    {!entityFilter ? null : <>
                        <button className="usage-breakdown-link-button" onClick={clearEntityFilter} type="button">Clear filter</button>
                    </>}
                </Flex>}
                {!selectedCategory ? null : <UsageBreakdownCategory
                    category={selectedCategory}
                    projectId={projectId}
                    loadMore={loadMore}
                    onFilterEntity={filterEntity}
                    sortBy={sortBy}
                    sortDirection={sortDirection}
                    onSort={changeSort}
                />}
            </>
        }
    </Flex>} />;
};

export default UsageBreakdownPage;
