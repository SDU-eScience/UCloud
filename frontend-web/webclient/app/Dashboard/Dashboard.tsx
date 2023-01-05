import {bulkRequestOf, emptyPage, emptyPageV2, defaultSearch, useSearch} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "@/Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link, Markdown, Text} from "@/ui-components";
import Error from "@/ui-components/Error";
import * as Heading from "@/ui-components/Heading";
import List from "@/ui-components/List";
import {SidebarPages} from "@/ui-components/Sidebar";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import {DashboardOperations, DashboardProps} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {APICallState, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {GridCardGroup} from "@/ui-components/Grid";
import {Spacer} from "@/ui-components/Spacer";
import {dateToString} from "@/Utilities/DateUtilities";
import {dispatchSetProjectAction} from "@/Project/Redux";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {
    GrantApplicationFilter,
    IngoingGrantApplicationsResponse,
} from "@/Project/Grant";
import {GrantApplicationList} from "@/Project/Grant/GrantApplications";
import * as UCloud from "@/UCloud";
import {PageV2} from "@/UCloud";
import {api as FilesApi, UFile} from "@/UCloud/FilesApi";
import metadataApi, {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useNavigate} from "react-router";
import {
    Product,
    productCategoryEquals,
    ProductMetadata,
    productTypeToIcon,
    productTypeToTitle,
    retrieveUsage,
    UsageChart,
    usageExplainer
} from "@/Accounting";
import {Job, api as JobsApi} from "@/UCloud/JobsApi";
import {ItemRow} from "@/ui-components/Browse";
import {useToggleSet} from "@/Utilities/ToggleSet";
import {BrowseType} from "@/Resource/BrowseType";
import {Client} from "@/Authentication/HttpClientInstance";
import {browseGrantApplications, GrantApplication} from "@/Project/Grant/GrantApplicationTypes";
import {Connect} from "@/Providers/Connect";
import {NotificationDashboardCard} from "@/Notifications";
import {grantsLink} from "@/UtilityFunctions";
import {isAdminOrPI, useProjectId} from "@/Project/Api";
import {useProject} from "@/Project/cache";
import { ProviderTitle } from "@/Providers/ProviderTitle";
import { ProviderLogo } from "@/Providers/ProviderLogo";
import AppRoutes from "@/Routes";

const MY_WORKSPACE = "My Workspace";

function Dashboard(props: DashboardProps): JSX.Element {
    useSearch(defaultSearch);

    const [news] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const [recentRuns, fetchRuns] = useCloudAPI<PageV2<Job>>({noop: true}, emptyPage);

    const [products, fetchProducts] = useCloudAPI<PageV2<Product>>({noop: true}, emptyPageV2);
    const [usage, fetchUsage] = useCloudAPI<{charts: UsageChart[]}>({noop: true}, {charts: []});

    const [outgoingApps, fetchOutgoingApps] = useCloudAPI<PageV2<GrantApplication>>(
        {noop: true},
        emptyPageV2
    );

    const [ingoingApps, fetchIngoingApps] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPageV2
    );

    const [favoriteFiles, fetchFavoriteFiles] = useCloudAPI<PageV2<FileMetadataAttached>>(
        {noop: true},
        emptyPageV2
    );

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {
        props.setAllLoading(loading);
        fetchProducts(UCloud.accounting.products.browse({
            itemsPerPage: 250,
            filterUsable: true,
            includeBalance: true
        }));
        fetchOutgoingApps(browseGrantApplications({
            itemsPerPage: 10,
            includeIngoingApplications: false,
            includeOutgoingApplications: true,
            filter: GrantApplicationFilter.ACTIVE
        }));
        fetchIngoingApps(browseGrantApplications({
            itemsPerPage: 10,
            includeIngoingApplications: true,
            includeOutgoingApplications: false,
            filter: GrantApplicationFilter.ACTIVE
        }));
        fetchFavoriteFiles(metadataApi.browse({
            filterActive: true,
            filterTemplate: "Favorite",
            itemsPerPage: 10
        }));
        fetchUsage(retrieveUsage({}));
        fetchRuns(JobsApi.browse({itemsPerPage: 10, sortBy: "MODIFIED_AT"}));
    }

    const main = (
        <GridCardGroup minmax={435} gridGap={16}>
            <DashboardNews news={news} />

            <DashboardFavoriteFiles
                favoriteFiles={favoriteFiles}
                onDeFavorite={() => fetchFavoriteFiles(metadataApi.browse({
                    filterActive: true,
                    filterTemplate: "Favorite",
                    itemsPerPage: 10
                }))}
            />

            <DashboardRuns runs={recentRuns} />

            <NotificationDashboardCard />
            <DashboardResources products={products} />
            <DashboardProjectUsage charts={usage} />
            <DashboardGrantApplications outgoingApps={outgoingApps} ingoingApps={ingoingApps} />
            <Connect embedded />
        </GridCardGroup>
    );

    return (<MainContainer main={main} />);
}

interface DashboardFavoriteFilesProps {
    favoriteFiles: APICallState<PageV2<FileMetadataAttached>>;

    onDeFavorite(): void;
}

const DashboardFavoriteFiles = (props: DashboardFavoriteFilesProps): JSX.Element => {
    const [, invokeCommand] = useCloudCommand();

    const [favoriteTemplateId, setId] = React.useState("");
    React.useEffect(() => {
        fetchTemplate();
    }, []);

    const navigate = useNavigate();

    const favorites = props.favoriteFiles.data.items.filter(it => it.metadata.specification.document.favorite);

    return (
        <HighlightedCard
            color="darkBlue"
            isLoading={props.favoriteFiles.loading}
            icon="starFilled"
            title="Favorites"
            minWidth="100%"
            error={props.favoriteFiles.error?.why}
        >
            {favorites.length !== 0 ? null : (
                <NoResultsCardBody title={"No favorites"}>
                    <Text textAlign="center" width="100%">
                        As you as add favorites, they will appear here.
                    </Text>
                    <Link to={"/drives"} mt={8}>
                        <Button mt={8}>Explore files</Button>
                    </Link>
                </NoResultsCardBody>
            )}
            <List childPadding="8px">
                {favorites.map(it => (<Flex key={it.path}>
                    <Icon cursor="pointer" mr="6px" name="starFilled" color="blue" onClick={async () => {
                        if (!favoriteTemplateId) return;
                        try {
                            await invokeCommand(
                                metadataApi.delete(bulkRequestOf({
                                    changeLog: "Remove favorite",
                                    id: it.metadata.id
                                })),
                                {defaultErrorHandler: false}
                            );
                            props.onDeFavorite();
                        } catch (e) {
                            snackbarStore.addFailure("Failed to unfavorite", false);
                        }
                    }} />
                    <Text cursor="pointer" fontSize="20px" mb="6px" mt="-3px" onClick={async () => {
                        const result = await invokeCommand<UFile>(FilesApi.retrieve({id: it.path}))
                        if (result?.status.type === "FILE") {
                            navigate(buildQueryString("/files", {path: getParentPath(it.path)}));
                        } else {
                            navigate(buildQueryString("/files", {path: it.path}))
                        }
                    }}>{fileName(it.path)}</Text>
                </Flex>))}
            </List>
        </HighlightedCard>
    );

    async function fetchTemplate() {
        const page = await invokeCommand<PageV2<FileMetadataTemplateNamespace>>(
            MetadataNamespaceApi.browse(({filterName: "favorite", itemsPerPage: 50}))
        );
        const ns = page?.items?.[0];
        if (ns) {
            setId(ns.id);
        }
    }
}

export interface NewsPost {
    id: number;
    title: string;
    subtitle: string;
    body: string;
    postedBy: string;
    showFrom: number;
    hideFrom: number | null;
    hidden: boolean;
    category: string;
}

interface NewsRequestProps extends PaginationRequest {
    filter?: string;
    withHidden: boolean;
}

export function newsRequest(payload: NewsRequestProps): APICallParameters<PaginationRequest> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/news/list", payload)
    };
}

export const NoResultsCardBody: React.FunctionComponent<{title: string; children: React.ReactNode}> = props => (
    <Flex
        alignItems="center"
        justifyContent="center"
        height="calc(100% - 60px)"
        minHeight="250px"
        mt="-30px"
        width="100%"
        flexDirection="column"
    >
        <Heading.h4>{props.title}</Heading.h4>
        {props.children}
    </Flex>
);

function DashboardProjectUsage(props: {charts: APICallState<{charts: UsageChart[]}>}): JSX.Element | null {
    return (
        <HighlightedCard
            title={<Link to={`/project/resources/${Client.projectId ?? MY_WORKSPACE}`}><Heading.h3>Resource usage</Heading.h3></Link>}
            icon="hourglass"
            color="yellow"
        >
            {props.charts.data.charts.length !== 0 ? null : (
                <NoResultsCardBody title={"No usage"}>
                    <Text textAlign="center">
                        As you use the platform, usage will appear here.
                    </Text>
                </NoResultsCardBody>
            )}
            {props.charts.data.charts.length === 0 ? null : <Text color="darkGray" fontSize={1}>Past 30 days</Text>}
            <Table>
                <tbody>
                    {props.charts.data.charts.map((it, idx) => (
                        <TableRow key={idx}>
                            <TableCell>
                                <Icon name={productTypeToIcon(it.type)} mr={8} />
                                {productTypeToTitle(it.type)}
                            </TableCell>
                            <TableCell textAlign={"right"}>
                                {usageExplainer(it.periodUsage, it.type, it.chargeType, it.unit)}
                            </TableCell>
                        </TableRow>
                    ))}
                </tbody>
            </Table>
        </HighlightedCard>
    );
}

function DashboardRuns({runs}: {
    runs: APICallState<UCloud.PageV2<Job>>;
}): JSX.Element {
    const navigate = useNavigate();
    const toggle = useToggleSet([]);
    return <HighlightedCard
        color="gray"
        title={<Link to={"/jobs"}><Heading.h3>Recent runs</Heading.h3></Link>}
        icon="results"
        isLoading={runs.loading}
        error={runs.error?.why}
    >
        {runs.data.items.length === 0 ? (
            <NoResultsCardBody title={"No previous jobs found"}>
                <Link to="/applications/overview" mt={8}>
                    <Button mt={8}>View applications</Button>
                </Link>
            </NoResultsCardBody>
        ) :
            <List>
                {runs.data.items.slice(0, 7).map(job =>
                    <ItemRow
                        key={job.id}
                        item={job}
                        browseType={BrowseType.Card}
                        navigate={() => navigate(`/jobs/properties/${job.id}`)}
                        renderer={JobsApi.renderer}
                        toggleSet={toggle}
                        operations={[] as ReturnType<typeof JobsApi.retrieveOperations>}
                        callbacks={{}}
                        itemTitle={JobsApi.title}
                    />
                )}
            </List>}
    </HighlightedCard>;
}

function DashboardResources({products}: {
    products: APICallState<PageV2<Product>>;
}): JSX.Element | null {
    const wallets = React.useMemo(() => {
        const wallets: (ProductMetadata & {balance: number})[] = [];

        for (const product of products.data.items) {
            const metadata: (ProductMetadata & {balance: number}) = {
                category: product.category,
                freeToUse: product.freeToUse,
                productType: product.productType,
                chargeType: product.chargeType,
                hiddenInGrantApplications: product.hiddenInGrantApplications,
                unitOfPrice: product.unitOfPrice,
                balance: product.balance!
            };

            if (!product.freeToUse) {
                if (wallets.find(it => productCategoryEquals(it.category, metadata.category)) === undefined) {
                    wallets.push(metadata);
                }
            }
        }
        return wallets;
    }, [products.data.items]);

    const projectId = useProjectId();


    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    wallets.sort((a, b) => {
        let compare: number = 0;

        compare = a.category.provider.localeCompare(b.category.provider);
        if (compare !== 0) return compare;

        compare = a.productType.localeCompare(b.productType);
        if (compare !== 0) return compare;

        compare = a.category.name.localeCompare(b.category.name);
        if (compare !== 0) return compare;

        return (a.balance < b.balance) ? 1 : -1;
    });

    const applyLinkButton = <Link to={projectId ? "/project/grants/existing" : "/project/grants/personal"} mt={8}>
        <Button mt={8}>Apply for resources</Button>
    </Link>;

    return (
        <HighlightedCard
            title={<Link to={`/project/allocations/${Client.projectId ?? MY_WORKSPACE}`}><Heading.h3>Resource allocations</Heading.h3></Link>}
            color="red"
            isLoading={products.loading}
            icon={"grant"}
            error={products.error?.why}
        >
            {wallets.length === 0 ? (
                <NoResultsCardBody title={"No available resources"}>
                    {!canApply ? null : <Text>
                        Apply for resources to use storage and compute on UCloud.
                    </Text>}
                    {applyLinkButton}
                </NoResultsCardBody>
            ) :
                <>
                    {/* height is 100% - height of Heading 36px  */}
                    <Flex flexDirection="column" height={"calc(100% - 36px)"}>
                        <Box my="5px">
                            <Table>
                                <tbody>
                                    {wallets.slice(0, 7).map((n, i) => (
                                        <TableRow key={i}>
                                            <TableCell>
                                                <Flex alignItems="center" gap="8px">
                                                    <ProviderLogo providerId={n.category.provider} size={32} />
                                                    <ProviderTitle providerId={n.category.provider} /> / {n.category.name}
                                                </Flex>
                                            </TableCell>
                                            <TableCell textAlign={"right"}>
                                                {usageExplainer(n.balance, n.productType, n.chargeType, n.unitOfPrice)}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </tbody>
                            </Table>
                        </Box>
                        <Box flexGrow={1} />
                    </Flex>
                </>
            }
        </HighlightedCard>
    );
}

const DashboardGrantApplications: React.FunctionComponent<{
    outgoingApps: APICallState<PageV2<GrantApplication>>,
    ingoingApps: APICallState<PageV2<GrantApplication>>
}> = ({outgoingApps, ingoingApps}) => {
    const none = outgoingApps.data.items.length === 0 && ingoingApps.data.items.length === 0;
    const both = outgoingApps.data.items.length > 0 && ingoingApps.data.items.length > 0;
    const anyOutgoing = outgoingApps.data.items.length > 0;

    const title = (none ? <Link to={`/project/grants/outgoing/${Client.projectId ?? MY_WORKSPACE}`}><Heading.h3>Grant applications</Heading.h3></Link>
        : both ? <Heading.h3>Grant Applications</Heading.h3>
            : <Link to={`/project/grants/${anyOutgoing ? "outgoing" : "ingoing"}/${Client.projectId ?? MY_WORKSPACE}`}>
                <Heading.h3>Grant Applications</Heading.h3>
            </Link>
    );


    const project = useProject();
    const canApply = !Client.hasActiveProject || isAdminOrPI(project.fetch().status.myRole);

    if (!canApply) return null;

    return <HighlightedCard
        title={title}
        color="green"
        minWidth="450px"
        isLoading={outgoingApps.loading}
        icon="mail"
        error={outgoingApps.error?.why ?? ingoingApps.error?.why}
    >
        {ingoingApps.error !== undefined ? null : (
            <Error error={ingoingApps.error} />
        )}

        {outgoingApps.error !== undefined ? null : (
            <Error error={outgoingApps.error} />
        )}
        {ingoingApps.data.items.length ? <Heading.h5 color="gray" my="4px">Ingoing</Heading.h5> : null}
        {ingoingApps.error ? null : (<GrantApplicationList applications={ingoingApps.data.items.slice(0, 5)} slim />)}

        {both ? <Heading.h5 color="gray" my="4px">Outgoing</Heading.h5> : null}
        {outgoingApps.error ? null : (
            <>
                {outgoingApps.data.items.length !== 0 || ingoingApps.data.items.length > 0 ? null : (
                    <>
                        <NoResultsCardBody title={"No recent outgoing applications"}>
                            <Text>
                                Apply for resources to use storage and compute on UCloud.
                            </Text>
                            <Link to={grantsLink(Client)} mt={8}>
                                <Button mt={8}>Apply for resources</Button>
                            </Link>
                        </NoResultsCardBody>
                    </>
                )}

                {outgoingApps.data.items.length === 0 ? null : (
                    <GrantApplicationList applications={outgoingApps.data.items.slice(0, 5)} slim />
                )}
            </>
        )}
        {outgoingApps.error || (outgoingApps.data.items.length === 0 && ingoingApps.data.items.length <= 0) ? null : (
            <Link to={grantsLink(Client)} width={"100%"}>
                <Button fullWidth my={8}>Apply for resources</Button>
            </Link>
        )}
    </HighlightedCard>;
};

function DashboardNews({news}: {news: APICallState<Page<NewsPost>>}): JSX.Element | null {
    return (
        <HighlightedCard
            title={<Link to="/news/list/"><Heading.h3>News</Heading.h3></Link>}
            color="orange"
            isLoading={news.loading}
            icon={"favIcon"}
            error={news.error?.why}
        >
            {news.data.items.length !== 0 ? null : (
                <NoResultsCardBody title={"No news"}>
                    <Text>
                        As announcements are made, they will be shared here.
                    </Text>
                </NoResultsCardBody>
            )}
            <Box>
                {news.data.items.slice(0, 1).map(post => (
                    <Box key={post.id} mb={32}>
                        <Link to={AppRoutes.news.detailed(post.id)}>
                            <Heading.h3>{post.title} </Heading.h3>
                        </Link>

                        <Spacer
                            left={<Heading.h5>{post.subtitle}</Heading.h5>}
                            right={<Heading.h5>{dateToString(post.showFrom)}</Heading.h5>}
                        />

                        <Box maxHeight={300} overflow={"auto"}>
                            <Markdown unwrapDisallowed>
                                {post.body}
                            </Markdown>
                        </Box>
                    </Box>
                ))}
            </Box>

            {news.data.items.length === 0 ? null : (
                <Spacer
                    left={null}
                    right={<Link to="/news/list/">View more</Link>}
                />)}
        </HighlightedCard>
    );
}

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Dashboard"));
        dispatch(setActivePage(SidebarPages.None));
    },
    setActiveProject: projectId => dispatchSetProjectAction(dispatch, projectId),
    setAllLoading: loading => dispatch(setAllLoading(loading)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect(null, mapDispatchToProps)(Dashboard);
