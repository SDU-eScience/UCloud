import {bulkRequestOf, emptyPage, emptyPageV2, defaultSearch, useSearch} from "@/DefaultObjects";
import {MainContainer} from "@/MainContainer/MainContainer";
import {setRefreshFunction} from "@/Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "@/Navigation/Redux/StatusActions";
import {Notification, NotificationEntry} from "@/Notifications";
import {notificationRead, readAllNotifications} from "@/Notifications/Redux/NotificationsActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link, Markdown, Text} from "@/ui-components";
import Error from "@/ui-components/Error";
import * as Heading from "@/ui-components/Heading";
import List from "@/ui-components/List";
import {SidebarPages} from "@/ui-components/Sidebar";
import {fileName, getParentPath} from "@/Utilities/FileUtilities";
import * as UF from "@/UtilityFunctions";
import {DashboardOperations, DashboardProps, DashboardStateProps} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {APICallState, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {GridCardGroup} from "@/ui-components/Grid";
import {Spacer} from "@/ui-components/Spacer";
import {getProjectNames} from "@/Utilities/ProjectUtilities";
import {useProjectStatus} from "@/Project/cache";
import {dateToString} from "@/Utilities/DateUtilities";
import {dispatchSetProjectAction} from "@/Project/Redux";
import Table, {TableCell, TableRow} from "@/ui-components/Table";
import {
    GrantApplicationFilter,
    IngoingGrantApplicationsResponse,
} from "@/Project/Grant";
import {GrantApplicationList} from "@/Project/Grant/IngoingApplications";
import * as UCloud from "@/UCloud";
import {PageV2} from "@/UCloud";
import {api as FilesApi, UFile} from "@/UCloud/FilesApi";
import metadataApi, {FileMetadataAttached} from "@/UCloud/MetadataDocumentApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "@/UCloud/MetadataNamespaceApi";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {useHistory} from "react-router";
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
import {ConnectDashboardCard} from "@/Providers/ConnectDashboardCard";
import {useProjectId} from "@/Project";
import {Client} from "@/Authentication/HttpClientInstance";
import {GrantApplication} from "@/Project/Grant/GrantApplicationTypes";

interface BrowseApplicationsRequest {
    filter: "SHOW_ALL" | "ACTIVE" | "INACTIVE";

    includeIngoingApplications: boolean;
    includeOutgoingApplications: boolean;

    itemsPerPage?: number;
    next?: string;
    consistency?: "PREFER" | "REQUIRE";
    itemsToSkip?: number;
}

function browseGrantsApplications(request: BrowseApplicationsRequest): APICallParameters<BrowseApplicationsRequest> {
    return {
        method: "GET",
        path: buildQueryString("/grant/browse", request),
        parameters: request,
        payload: request
    }
}

function Dashboard(props: DashboardProps): JSX.Element {
    const history = useHistory();
    useSearch(defaultSearch);
    const projectNames = getProjectNames(useProjectStatus());

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
        fetchOutgoingApps(browseGrantsApplications({
            itemsPerPage: 10,
            includeIngoingApplications: true,
            includeOutgoingApplications: false,
            filter: GrantApplicationFilter.SHOW_ALL
        }));
        fetchIngoingApps(browseGrantsApplications({
            itemsPerPage: 10,
            includeIngoingApplications: false,
            includeOutgoingApplications: true,
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

    const {
        notifications,
    } = props;

    const onNotificationAction = (notification: Notification): void =>
        UF.onNotificationAction(history, props.setActiveProject, notification, projectNames, props.notificationRead);

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

            <DashboardNotifications
                onNotificationAction={onNotificationAction}
                notifications={notifications}
                readAll={props.readAll}
            />

            <DashboardResources products={products} />
            <DashboardProjectUsage charts={usage} />
            <DashboardGrantApplications outgoingApps={outgoingApps} ingoingApps={ingoingApps} />
            <ConnectDashboardCard />
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

    const history = useHistory();

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
                    <Text width="100%">
                        As you as add favorites, they will appear here.
                        <Link to={"/drives"} mt={8}>
                            <Button fullWidth mt={8}>Explore files</Button>
                        </Link>
                    </Text>
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
                            history.push(buildQueryString("/files", {path: getParentPath(it.path)}));
                        } else {
                            history.push(buildQueryString("/files", {path: it.path}))
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

interface DashboardNotificationProps {
    onNotificationAction: (notification: Notification) => void;
    notifications: {items: Notification[]; error?: string};
    readAll: () => void;
}

const DashboardNotifications = (props: DashboardNotificationProps): JSX.Element => (
    <HighlightedCard
        color="darkGreen"
        isLoading={false}
        icon="notification"
        title="Recent Notifications"
        subtitle={
            <Icon
                name="checkDouble"
                cursor="pointer"
                color="iconColor"
                color2="iconColor2"
                title="Mark all as read"
                onClick={props.readAll}
            />
        }
        error={props.notifications.error}
    >
        {props.notifications.items.length !== 0 ? null :
            <NoResultsCardBody title={"No notifications"}>
                <Text>
                    As you as use UCloud notifications will appear here.

                    <Link to={"/applications/overview"} mt={8}>
                        <Button fullWidth mt={8}>Explore UCloud</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
        }
        <List>
            {props.notifications.items.slice(0, 7).map((n, i) => (
                <Flex key={i}>
                    <NotificationEntry notification={n} onAction={props.onNotificationAction} />
                </Flex>
            ))}
        </List>
    </HighlightedCard>
);

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

export const NoResultsCardBody: React.FunctionComponent<{title: string}> = props => (
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
            title={<Link to={"/project/resources"}><Heading.h3>Usage</Heading.h3></Link>}
            icon="hourglass"
            color="yellow"
        >
            {props.charts.data.charts.length !== 0 ? null : (
                <NoResultsCardBody title={"No usage"}>
                    <Text>
                        As you use the platform, usage will appear here.

                        <Link to={"/drives"} mt={8}>
                            <Button fullWidth mt={8}>Explore files</Button>
                        </Link>
                        <Link to={"/applications/overview"} mt={8}>
                            <Button fullWidth mt={8}>Explore applications</Button>
                        </Link>
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
    const history = useHistory();
    const toggle = useToggleSet([]);
    return <HighlightedCard
        color="gray"
        title={<Link to={"/jobs"}><Heading.h3>Recent Runs</Heading.h3></Link>}
        icon="results"
        isLoading={runs.loading}
        error={runs.error?.why}
    >
        {runs.data.items.length === 0 ? (
            <NoResultsCardBody title={"No previous jobs found"}>
                <Text>
                    <Link to="/applications/overview">
                        View applications
                    </Link>
                </Text>
            </NoResultsCardBody>
        ) :
            <List>
                {runs.data.items.slice(0, 7).map(job =>
                    <ItemRow
                        key={job.id}
                        item={job}
                        browseType={BrowseType.Card}
                        navigate={() => history.push(`/jobs/properties/${job.id}`)}
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

    const projectId = useProjectId()


    wallets.sort((a, b) => (a.balance < b.balance) ? 1 : -1);
    const applyLinkButton = <Link to={projectId ? "/project/grants/existing" : "/project/grants/personal"}>
        <Button fullWidth mb={"4px"}>Apply for resources</Button>
    </Link>;

    return (
        <HighlightedCard
            title={<Link to={"/project/resources"}><Heading.h3>Resource Allocations</Heading.h3></Link>}
            color="red"
            isLoading={products.loading}
            icon={"grant"}
            error={products.error?.why}
        >
            {wallets.length === 0 ? (
                <NoResultsCardBody title={"No available resources"}>
                    <Text>
                        Apply for resources to use storage and compute on UCloud.
                        {applyLinkButton}
                    </Text>
                </NoResultsCardBody>
            ) :
                <>
                    {/* height is 100% - height of Heading 36px  */}
                    <Flex flexDirection="column" height={"calc(100% - 36px)"}>
                        <Box mx="8px" my="5px">
                            <Table>
                                <tbody>
                                    {wallets.slice(0, 7).map((n, i) => (
                                        <TableRow key={i}>
                                            <TableCell>{n.category.provider} / {n.category.name}</TableCell>
                                            <TableCell textAlign={"right"}>
                                                {usageExplainer(n.balance, n.productType, n.chargeType, n.unitOfPrice)}
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </tbody>
                            </Table>
                        </Box>
                        <Box flexGrow={1} />
                        {applyLinkButton}
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

    const title = (none ? <Link to={"/project/grants/outgoing"}><Heading.h3>Grant Applications</Heading.h3></Link>
        : both ? <Heading.h3>Grant Applications</Heading.h3>
            : <Link to={`/project/grants/${anyOutgoing ? "outgoing" : "ingoing"}`}>
                <Heading.h3>Grant Applications</Heading.h3>
            </Link>
    );

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
                {outgoingApps.data.items.length !== 0 ? null : (
                    <>
                        <NoResultsCardBody title={"No recent outgoing applications"}>
                            Apply for resources to use storage and compute on UCloud.
                            <Link to={Client.hasActiveProject ? "/project/grants/existing/" : "/project/grants/personal"} width={"100%"}>
                                <Button fullWidth mt={8}>Apply for resources</Button>
                            </Link>
                        </NoResultsCardBody>
                    </>
                )}
                <GrantApplicationList applications={outgoingApps.data.items.slice(0, 5)} slim />
            </>
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
                        <Link to={`/news/detailed/${post.id}`}>
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
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => ({
    notifications: {items: state.notifications.items, error: state.notifications.error},
});

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
