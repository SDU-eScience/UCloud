import {bulkRequestOf, emptyPage, emptyPageV2} from "DefaultObjects";
import {History} from "history";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {Notification, NotificationEntry} from "Notifications";
import {notificationRead, readAllNotifications} from "Notifications/Redux/NotificationsActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Flex, Icon, Link, Markdown, Text} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {SidebarPages} from "ui-components/Sidebar";
import {fileName, getParentPath} from "Utilities/FileUtilities";
import * as UF from "UtilityFunctions";
import {DashboardOperations, DashboardProps, DashboardStateProps} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {APICallState, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {GridCardGroup} from "ui-components/Grid";
import {Spacer} from "ui-components/Spacer";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {useProjectStatus} from "Project/cache";
import {dateToString} from "Utilities/DateUtilities";
import {dispatchSetProjectAction} from "Project/Redux";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {
    GrantApplication,
    GrantApplicationFilter,
    ingoingGrantApplications,
    IngoingGrantApplicationsResponse,
    listOutgoingApplications
} from "Project/Grant";
import {GrantApplicationList} from "Project/Grant/IngoingApplications";
import {useProjectManagementStatus} from "Project";
import * as UCloud from "UCloud";
import {accounting, PageV2} from "UCloud";
import {groupBy} from "Utilities/CollectionUtilities";
import FilesApi, {UFile} from "UCloud/FilesApi";
import metadataApi, {FileMetadataAttached} from "UCloud/MetadataDocumentApi";
import MetadataNamespaceApi, {FileMetadataTemplateNamespace} from "UCloud/MetadataNamespaceApi";
import HighlightedCard from "ui-components/HighlightedCard";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {useHistory} from "react-router";
import {
    explainUsage,
    Product,
    productCategoryEquals,
    ProductMetadata,
    productTypes, productTypeToIcon, productTypeToTitle,
    retrieveUsage,
    UsageChart,
    usageExplainer
} from "Accounting";

function Dashboard(props: DashboardProps & { history: History }): JSX.Element {
    const projectNames = getProjectNames(useProjectStatus());

    const [news] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const [products, fetchProducts] = useCloudAPI<PageV2<Product>>({noop: true}, emptyPageV2);
    const [usage, fetchUsage] = useCloudAPI<{ charts: UsageChart[] }>({noop: true}, {charts: []});

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
        fetchOutgoingApps(listOutgoingApplications({
            itemsPerPage: 10,
            filter: GrantApplicationFilter.SHOW_ALL
        }));
        fetchIngoingApps(ingoingGrantApplications({
            itemsPerPage: 10,
            filter: GrantApplicationFilter.ACTIVE
        }));
        fetchFavoriteFiles(metadataApi.browse({
            filterActive: true,
            filterTemplate: "Favorite",
            itemsPerPage: 10
        }));
        fetchUsage(retrieveUsage({}));
    }

    const {
        notifications,
    } = props;

    const onNotificationAction = (notification: Notification): void =>
        UF.onNotificationAction(props.history, props.setActiveProject, notification, projectNames, props.notificationRead);

    const main = (
        <GridCardGroup minmax={435} gridGap={16}>
            <DashboardNews news={news.data.items} loading={news.loading}/>

            <DashboardFavoriteFiles
                favoriteFiles={favoriteFiles}
                onDeFavorite={() => fetchFavoriteFiles(metadataApi.browse({
                    filterActive: true,
                    filterTemplate: "Favorite",
                    itemsPerPage: 10
                }))}
            />

            <DashboardNotifications
                onNotificationAction={onNotificationAction}
                notifications={notifications}
                readAll={props.readAll}
            />

            <DashboardResources
                products={products.data.items}
                loading={products.loading}
            />
            <DashboardProjectUsage charts={usage.data.charts}/>
            <DashboardGrantApplications outgoingApps={outgoingApps} ingoingApps={ingoingApps}/>
        </GridCardGroup>
    );

    return (<MainContainer main={main}/>);
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

    const favorites = props.favoriteFiles.data.items.filter(it => it.metadata.specification.document.favorite)//.slice(0, 7);

    return (
        <HighlightedCard
            color="darkBlue"
            isLoading={props.favoriteFiles.loading}
            icon="starFilled"
            title="Favorites"
        >
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
                    }}/>
                    <Text fontSize="20px" mb="6px" mt="-3px" onClick={async () => {
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
    notifications: Notification[];
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
    >
        {props.notifications.length === 0 ?
            <NoResultsCardBody title={"No notifications"}>
                <Text>
                    As you as use UCloud notifications will appear here.

                    <Link to={"/applications/overview"} mt={8}>
                        <Button fullWidth mt={8}>Explore UCloud</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
            : null
        }
        <List>
            {props.notifications.slice(0, 7).map((n, i) => (
                <Flex key={i}>
                    <NotificationEntry notification={n} onAction={props.onNotificationAction}/>
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

export const NoResultsCardBody: React.FunctionComponent<{ title: string }> = props => (
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

function DashboardProjectUsage(props: {charts: UsageChart[]}): JSX.Element | null {
    return (
        <HighlightedCard
            title={<Link to={"/project/resources"}><Heading.h3>Usage</Heading.h3></Link>}
            icon="hourglass"
            color="yellow"
        >
            <Text color="darkGray" fontSize={1}>Past 30 days</Text>
            <Table>
                <tbody>
                {props.charts.map((it, idx) => (
                    <TableRow key={idx}>
                        <TableCell>
                            <Icon name={productTypeToIcon(it.type)} mr={8}/>
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

function DashboardResources({products, loading}: {
    products: Product[];
    loading: boolean
}): JSX.Element | null {
    const wallets: (ProductMetadata & { balance: number })[] = [];

    for (const product of products) {
        const metadata: (ProductMetadata & { balance: number }) = {
            category: product.category,
            freeToUse: product.freeToUse,
            productType: product.productType,
            chargeType: product.chargeType,
            hiddenInGrantApplications: product.hiddenInGrantApplications,
            unitOfPrice: product.unitOfPrice,
            balance: product.balance!
        };

        if (wallets.find(it => productCategoryEquals(it.category, metadata.category)) === undefined) {
            wallets.push(metadata);
        }
    }

    wallets.sort((a, b) => (a.balance < b.balance) ? 1 : -1);
    const applyLinkButton = <Link to={"/project/grants-landing"}>
        <Button fullWidth mb={"4px"}>Apply for resources</Button>
    </Link>;

    return (
        <HighlightedCard
            title={<Link to={"/project/resources"}><Heading.h3>Resources</Heading.h3></Link>}
            color="red"
            isLoading={loading}
            icon={"grant"}
        >
            {products.length === 0 ? (
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
                        <Box flexGrow={1}/>
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
    >
        {ingoingApps.error !== undefined ? null : (
            <Error error={ingoingApps.error}/>
        )}

        {outgoingApps.error !== undefined ? null : (
            <Error error={outgoingApps.error}/>
        )}
        {ingoingApps.data.items.length ? <Heading.h5 color="gray" my="4px">Ingoing</Heading.h5> : null}
        {ingoingApps.error ? null : (<GrantApplicationList applications={ingoingApps.data.items.slice(0, 5)} slim/>)}

        {both ? <Heading.h5 color="gray" my="4px">Outgoing</Heading.h5> : null}
        {outgoingApps.error ? null : (
            <>
                {outgoingApps.data.items.length !== 0 ? null : (
                    <>
                        <Heading.h3>No recent outgoing grant applications</Heading.h3>
                        <Text>
                            Apply for resources to use storage and compute on UCloud.
                            <Link to={"/project/grants-landing"}>
                                <Button fullWidth mt={8}>Apply for resources</Button>
                            </Link>
                        </Text>
                    </>
                )}
                <GrantApplicationList applications={outgoingApps.data.items.slice(0, 5)} slim/>
            </>
        )}
    </HighlightedCard>;
};

function DashboardNews({news, loading}: { news: NewsPost[]; loading: boolean }): JSX.Element | null {
    return (
        <HighlightedCard
            title={<Link to="/news/list/"><Heading.h3>News</Heading.h3></Link>}
            color="orange"
            isLoading={loading}
            icon={"favIcon"}
        >
            <Box>
                {news.slice(0, 1).map(post => (
                    <Box key={post.id} mb={32}>
                        <Link to={`/news/detailed/${post.id}`}>
                            <Heading.h3>{post.title} </Heading.h3>
                        </Link>

                        <Spacer
                            left={<Heading.h5>{post.subtitle}</Heading.h5>}
                            right={<Heading.h5>{dateToString(post.showFrom)}</Heading.h5>}
                        />

                        <Box maxHeight={300} overflow={"auto"}>
                            <Markdown
                                source={post.body}
                                unwrapDisallowed
                            />
                        </Box>
                    </Box>
                ))}
                {news.length === 0 ? "No posts found" : null}
            </Box>

            <Spacer
                left={null}
                right={<Link to="/news/list/">View more</Link>}
            />
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
    ...state.dashboard,
    notifications: state.notifications.items,
});

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
