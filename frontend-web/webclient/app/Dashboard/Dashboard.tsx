import {Client} from "Authentication/HttpClientInstance";
import {emptyPage, emptyPageV2} from "DefaultObjects";
import {File} from "Files";
import {History} from "history";
import Spinner from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {setActivePage, updatePageTitle} from "Navigation/Redux/StatusActions";
import {Notification, NotificationEntry} from "Notifications";
import {notificationRead, readAllNotifications} from "Notifications/Redux/NotificationsActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {Box, Button, Card, Flex, Icon, Link, Markdown, Text} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {SidebarPages} from "ui-components/Sidebar";
import {EllipsedText} from "ui-components/Text";
import {fileTablePage, getFilenameFromPath, getParentPath, isDirectory, sizeToString} from "Utilities/FileUtilities";
import {FileIcon} from "UtilityComponents";
import * as UF from "UtilityFunctions";
import {DashboardOperations, DashboardProps, DashboardStateProps} from ".";
import {setAllLoading} from "./Redux/DashboardActions";
import {IconName} from "ui-components/Icon";
import {listFavorites, useFavoriteStatus} from "Files/favorite";
import {APICallState, useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {GridCardGroup} from "ui-components/Grid";
import {Spacer} from "ui-components/Spacer";
import {
    retrieveQuota, RetrieveQuotaResponse, transformUsageChartForCharting, usage, UsageResponse,
} from "Accounting";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {useProjectStatus} from "Project/cache";
import {dateToString} from "Utilities/DateUtilities";
import theme, {ThemeColor} from "ui-components/theme";
import {dispatchSetProjectAction} from "Project/Redux";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {Balance} from "Accounting/Balance";
import {
    GrantApplication,
    GrantApplicationFilter,
    ingoingGrantApplications,
    IngoingGrantApplicationsResponse,
    listOutgoingApplications
} from "Project/Grant";
import {GrantApplicationList} from "Project/Grant/IngoingApplications";
import {creditFormatter, durationOptions} from "Project/ProjectUsage";
import {computeUsageInPeriod} from "Project/ProjectDashboard";
import {useProjectManagementStatus} from "Project";
import * as UCloud from "UCloud";
import {accounting, PageV2} from "UCloud";
import Product = accounting.Product;
import {groupBy} from "Utilities/CollectionUtilities";
import {JobStateIcon} from "Applications/Jobs/JobStateIcon";
import {isRunExpired} from "Utilities/ApplicationUtilities";
import formatDistanceToNow from "date-fns/formatDistanceToNow";
import {jobAppTitle, jobTitle} from "Applications/Jobs";

export const DashboardCard: React.FunctionComponent<{
    title?: React.ReactNode;
    subtitle?: React.ReactNode;
    color: ThemeColor;
    isLoading?: boolean;
    icon?: IconName,
    height?: string,
    minHeight?: string,
    width?: string,
    minWidth?: string,
    onClick?: () => void;
}> = ({title, subtitle, onClick, color, isLoading = false, icon = undefined, children, height = "auto", minHeight, width = "100%", minWidth}) => (
    <Card
        onClick={onClick}
        overflow="hidden"
        height={height}
        width={width}
        minWidth={minWidth}
        boxShadow="sm"
        borderWidth={0}
        borderRadius={6}
        minHeight={minHeight}
    >
        <Box style={{borderTop: `5px solid var(--${color}, #f00)`}} />
        <Box px={3} py={1} height={"100%"} className={"dashboard-card-inner"}>
            <Flex alignItems="center">
                {icon !== undefined ? (
                    <Icon
                        name={icon}
                        m={8}
                        ml={0}
                        size="20"
                        color={theme.colors.darkGray}
                    />
                ) : null}
                {typeof title === "string" ? <Heading.h3>{title}</Heading.h3> : title ? title : null}
                <Box flexGrow={1} />
                {subtitle ? <Box color={theme.colors.gray}>{subtitle}</Box> : null}
            </Flex>
            {!isLoading ? children : <Spinner />}
        </Box>
    </Card>
);

function Dashboard(props: DashboardProps & {history: History}): JSX.Element {
    const favorites = useFavoriteStatus();
    const [favoritePage, setFavoriteParams] = useCloudAPI<Page<File>>(
        {noop: true},
        emptyPage
    );

    const projectNames = getProjectNames(useProjectStatus());

    const [news] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const [quota, fetchQuota] = useCloudAPI<RetrieveQuotaResponse>(
        {noop: true},
        {quotaInBytes: 0, quotaUsed: 0, quotaInTotal: 0}
    );

    const [products, fetchProducts] = useCloudAPI<PageV2<Product>>({noop: true}, emptyPageV2);

    const [outgoingApps, fetchOutgoingApps] = useCloudAPI<Page<GrantApplication>>(
        {noop: true},
        emptyPage
    );

    const [ingoingApps, fetchIngoingApps] = useCloudAPI<IngoingGrantApplicationsResponse>(
        {noop: true},
        emptyPage
    );

    const [jobs, fetchJobs] = useCloudAPI<PageV2<UCloud.compute.Job>>(UCloud.compute.jobs.browse({
        itemsPerPage: 10,
    }), emptyPage);

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {
        props.setAllLoading(loading);
        fetchQuota(retrieveQuota({
            path: Client.activeHomeFolder,
            includeUsage: true
        }));
        setFavoriteParams(listFavorites({itemsPerPage: 10, page: 0}));
        fetchProducts(UCloud.accounting.products.browse({
            itemsPerPage: 250,
            filterUsable: true,
            includeBalance: true
        }));
        fetchOutgoingApps(listOutgoingApplications({
            itemsPerPage: 10,
            page: 0,
            filter: GrantApplicationFilter.SHOW_ALL
        }));
        fetchIngoingApps(ingoingGrantApplications({
            itemsPerPage: 10,
            page: 0,
            filter: GrantApplicationFilter.ACTIVE
        }));
        fetchJobs(UCloud.compute.jobs.browse({itemsPerPage: 10, includeApplication: true}));
    }

    const favoriteOrUnfavorite = async (file: File): Promise<void> => {
        await favorites.toggle(file.path);
        setFavoriteParams(listFavorites({itemsPerPage: 10, page: 0}));
    };

    const {
        notifications,
    } = props;

    const onNotificationAction = (notification: Notification): void =>
        UF.onNotificationAction(props.history, props.setActiveProject, notification, projectNames, props.notificationRead);

    const main = (
        <GridCardGroup minmax={435} gridGap={16}>
            <DashboardNews news={news.data.items} loading={news.loading} />
            <DashboardFavoriteFiles
                error={favoritePage.error?.why}
                files={favoritePage.data.items}
                isLoading={favoritePage.loading}
                favorite={favoriteOrUnfavorite}
            />

            <DashboardAnalyses runs={jobs} />

            <DashboardNotifications
                onNotificationAction={onNotificationAction}
                notifications={notifications}
                readAll={props.readAll}
            />

            <DashboardResources
                products={products.data.items}
                quota={quota.data}
                loading={products.loading || quota.loading}
            />
            <DashboardProjectUsage />
            <DashboardGrantApplications outgoingApps={outgoingApps} ingoingApps={ingoingApps} />
        </GridCardGroup>
    );

    return (<MainContainer main={main} />);
}

const DashboardFavoriteFiles = ({files, isLoading, favorite, error}: {
    files: File[];
    isLoading: boolean;
    favorite: (file: File) => void;
    error?: string
}): JSX.Element => (
    <DashboardCard
        title={<Link to={fileTablePage(Client.favoritesFolder)}><Heading.h3>Favorite Files</Heading.h3></Link>}
        color="blue"
        isLoading={isLoading}
        icon={"starFilled"}
    >
        {files.length || error ? null : (
            <NoResultsCardBody title="No favorite files">
                <Text>
                    Click the <Icon name="starEmpty" /> next to one of your files to mark it as a favorite.
                    All of your favorite files will appear here.
                    <Link to={fileTablePage(Client.activeHomeFolder)}>
                        <Button fullWidth mt={8}>Explore files</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
        )}
        <Error error={error} />
        <List>
            {files.slice(0, 7).map(file => (
                <Flex alignItems="center" key={file.path} pt="0.5em" pb="6.4px">
                    <ListFileContent file={file} pixelsWide={200} />
                    <Icon
                        ml="auto"
                        size="1em"
                        name="starFilled"
                        color="blue"
                        cursor="pointer"
                        onClick={() => favorite(file)}
                    />
                </Flex>
            ))}
        </List>
    </DashboardCard>
);

const ListFileContent = ({file, pixelsWide}: {file: File; pixelsWide: number}): JSX.Element => {
    const iconType = UF.iconFromFilePath(file.path, file.fileType);
    const projects = getProjectNames(useProjectStatus());
    return (
        <Flex alignItems="center">
            <FileIcon fileIcon={iconType} />
            <Link ml="0.5em" to={fileTablePage(isDirectory(file) ? file.path : getParentPath(file.path))}>
                <EllipsedText fontSize={2} width={pixelsWide}>
                    {getFilenameFromPath(file.path, projects)}
                </EllipsedText>
            </Link>
        </Flex>
    );
};

const DashboardAnalyses: React.FunctionComponent<{runs: APICallState<PageV2<UCloud.compute.Job>>}> = ({runs}) => (
    <DashboardCard
        title={<Link to={"/applications/results"}><Heading.h3>Recent Runs</Heading.h3></Link>}
        color="purple"
        isLoading={runs.loading}
        icon={"apps"}
    >
        {runs.data.items.length || runs.error ? null : (
            <NoResultsCardBody title={"No recent application runs"}>
                <Text>
                    When you run an application on UCloud the results will appear here.

                    <Link to={"/applications/overview"} mt={8}>
                        <Button fullWidth mt={8}>Explore applications</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
        )}
        <Error error={runs.error?.why} />
        <List>
            {runs.data.items.slice(0, 7).map((run: UCloud.compute.Job, index: number) => (
                <Flex key={index} alignItems="center" pt="0.5em" pb="8.4px">
                    <JobStateIcon
                        size="1.2em"
                        pr="0.3em"
                        state={run.status.state}
                        isExpired={isRunExpired(run)}
                        mr="8px"
                    />
                    <Link to={`/applications/jobs/${run.id}`}>
                        <EllipsedText width={175} fontSize={3}>
                            {jobTitle(run)} ({jobAppTitle(run)})
                        </EllipsedText>
                    </Link>
                    <Box ml="auto" />
                    <Text fontSize={1} color="grey">
                        {formatDistanceToNow(
                            new Date(
                                run.updates[run.updates.length - 1]?.timestamp ?? run.status.startedAt
                            ), {addSuffix: true}
                        )}
                    </Text>
                </Flex>
            ))}
        </List>
    </DashboardCard>
);

interface DashboardNotificationProps {
    onNotificationAction: (notification: Notification) => void;
    notifications: Notification[];
    readAll: () => void;
}

const DashboardNotifications = (props: DashboardNotificationProps): JSX.Element => (
    <DashboardCard
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
                    <NotificationEntry notification={n} onAction={props.onNotificationAction} />
                </Flex>
            ))}
        </List>
    </DashboardCard>
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

function DashboardProjectUsage(): JSX.Element | null {
    const {projectId} =
        useProjectManagementStatus({isRootComponent: true, allowPersonalProject: true});
    const durationOption = durationOptions[3];
    const now = new Date().getTime();
    const [usageResponse, setUsageParams] = useCloudAPI<UsageResponse>(
        {noop: true},
        {charts: []}
    );
    React.useEffect(() => {
        setUsageParams(usage({
            bucketSize: durationOption.bucketSize,
            periodStart: now - durationOption.timeInPast,
            periodEnd: now
        }));
    }, [projectId]);

    const computeCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, "COMPUTE"));
    const computeCreditsUsedInPeriod = computeUsageInPeriod(computeCharts);
    const storageCharts = usageResponse.data.charts.map(it => transformUsageChartForCharting(it, "STORAGE"));
    const storageCreditsUsedInPeriod = computeUsageInPeriod(storageCharts);

    return (
        <DashboardCard title={<Link to={"/project/usage"}><Heading.h3>Usage</Heading.h3></Link>}
            icon="hourglass"
            color="yellow"
            isLoading={false}
        >
            <Text color="darkGray" fontSize={1}>Past 30 days</Text>
            <Table>
                <tbody>
                    <TableRow>
                        <TableCell>Storage</TableCell>
                        <TableCell
                            textAlign="right">{creditFormatter(storageCreditsUsedInPeriod)}</TableCell>
                    </TableRow>
                    <TableRow>
                        <TableCell>Compute</TableCell>
                        <TableCell
                            textAlign="right">{creditFormatter(computeCreditsUsedInPeriod)}</TableCell>
                    </TableRow>
                </tbody>
            </Table>
        </DashboardCard>
    );
}
function DashboardResources({products, loading, quota}: {
    products: Product[];
    quota: RetrieveQuotaResponse,
    loading: boolean
}): JSX.Element | null {
    const productsByCategory = groupBy(products, it => `${it.category.id}-${it.category.provider}`);
    const wallets: {category: string, provider: string, balance: number, isFreeWithBalanceCheck: boolean}[] = [];
    Object.values(productsByCategory).forEach(group => {
        if (group.length === 0) return;
        const category = group[0].category.id;
        const provider = group[0].category.provider;
        const balance = group[0].balance!;
        const isFreeWithBalanceCheck = group
            .every(it => "paymentModel" in it && it.paymentModel === "FREE_BUT_REQUIRE_BALANCE");

        wallets.push({category, provider, balance, isFreeWithBalanceCheck});
    });
    wallets.sort((a, b) => (a.balance < b.balance) ? 1 : -1);
    const applyLinkButton = <Link to={"/project/grants-landing"}>
        <Button fullWidth>Apply for resources</Button>
    </Link>;

    return (
        <DashboardCard
            title={<Link to={"/project/subprojects"}><Heading.h3>Resources</Heading.h3></Link>}
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
                    <Flex flexDirection="column" height={"calc(100% - 60px)"}>
                        <Box mx="8px" my="5px">
                            <Table>
                                <tbody>
                                    {wallets.slice(0, 7).map((n, i) => (
                                        <TableRow key={i}>
                                            <TableCell>{n.provider} / {n.category}</TableCell>
                                            <TableCell textAlign={"right"}>
                                                {!n.isFreeWithBalanceCheck ? null :
                                                    n.balance > 0 ? <Icon name={"check"} color={"green"} /> : null
                                                }
                                                {n.isFreeWithBalanceCheck ? null :
                                                    <Balance
                                                        amount={n.balance}
                                                        productCategory={{id: n.category, provider: n.provider}}
                                                    />
                                                }
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                    <TableRow>
                                        {/* This is hardcoded for now (pending issue #1246) */}
                                        <TableCell>ucloud / u1-cephfs (Quota)</TableCell>
                                        <TableCell textAlign={"right"}>
                                            {sizeToString(quota.quotaUsed ?? 0)}
                                            {" "}of{" "}
                                            {sizeToString(quota.quotaInBytes)}
                                            {" "}({(100 * (quota.quotaInBytes !== 0 ?
                                                ((quota.quotaUsed ?? 0) / quota.quotaInBytes) : 1
                                            )).toFixed(2)}%)
                                        </TableCell>
                                    </TableRow>
                                </tbody>
                            </Table>
                        </Box>
                        <Box flexGrow={1} />
                        {applyLinkButton}
                    </Flex>
                </>
            }
        </DashboardCard>
    );
}

const DashboardGrantApplications: React.FunctionComponent<{
    outgoingApps: APICallState<Page<GrantApplication>>,
    ingoingApps: APICallState<Page<GrantApplication>>
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

    return <DashboardCard
        title={title}
        color="green"
        minWidth="450px"
        isLoading={outgoingApps.loading}
        icon="mail"
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
                        <Heading.h3>No recent outgoing grant applications</Heading.h3>
                        <Text>
                            Apply for resources to use storage and compute on UCloud.
                            <Link to={"/project/grants-landing"}>
                                <Button fullWidth mt={8}>Apply for resources</Button>
                            </Link>
                        </Text>
                    </>
                )}
                <GrantApplicationList applications={outgoingApps.data.items.slice(0, 5)} slim />
            </>
        )}
    </DashboardCard>;
};

function DashboardNews({news, loading}: {news: NewsPost[]; loading: boolean}): JSX.Element | null {
    return (
        <DashboardCard
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
        </DashboardCard>
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
