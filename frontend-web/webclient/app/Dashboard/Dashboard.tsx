import {JobWithStatus} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {formatDistanceToNow} from "date-fns/esm";
import {emptyPage} from "DefaultObjects";
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
import {Box, Button, Card, Flex, Icon, Link, Text, Markdown} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {SidebarPages} from "ui-components/Sidebar";
import {EllipsedText} from "ui-components/Text";
import {fileTablePage, sizeToString} from "Utilities/FileUtilities";
import {
    getFilenameFromPath,
    getParentPath,
    isDirectory
} from "Utilities/FileUtilities";
import {FileIcon} from "UtilityComponents";
import * as UF from "UtilityFunctions";
import {DashboardOperations, DashboardProps, DashboardStateProps} from ".";
import {
    fetchRecentAnalyses,
    setAllLoading
} from "./Redux/DashboardActions";
import {JobStateIcon} from "Applications/JobStateIcon";
import {isRunExpired} from "Utilities/ApplicationUtilities";
import {IconName} from "ui-components/Icon";
import {listFavorites, useFavoriteStatus} from "Files/favorite";
import {useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import styled from "styled-components";
import {GridCardGroup} from "ui-components/Grid";
import {Spacer} from "ui-components/Spacer";
import {
    retrieveBalance,
    RetrieveBalanceResponse,
    retrieveQuota,
    RetrieveQuotaResponse,
    WalletBalance
} from "Accounting";
import {getProjectNames} from "Utilities/ProjectUtilities";
import {useProjectStatus} from "Project/cache";
import {dateToString} from "Utilities/DateUtilities";
import theme, {ThemeColor} from "ui-components/theme";
import {dispatchSetProjectAction} from "Project/Redux";
import Table, {TableCell, TableRow} from "ui-components/Table";
import {Balance} from "Accounting/Balance";

export const DashboardCard: React.FunctionComponent<{
    title?: React.ReactNode;
    subtitle?: React.ReactNode;
    color: ThemeColor;
    isLoading: boolean;
    icon?: IconName,
    height?: string,
    minHeight?: string
    onClick?: () => void;
}> = ({title, subtitle, onClick, color, isLoading, icon = undefined, children, height = "auto", minHeight}) => (
    <Card
        onClick={onClick}
        overflow="hidden"
        height={height}
        width={1}
        boxShadow="sm"
        borderWidth={0}
        borderRadius={6}
        minHeight={minHeight}
    >
        <Box style={{borderTop: `5px solid var(--${color}, #f00)`}}/>
        <Box px={3} py={1} height={"100%"}>
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
                <Box flexGrow={1}/>
                {subtitle ? <Box color={theme.colors.gray}>{subtitle}</Box> : null}
            </Flex>
            {!isLoading ? children : <Spinner/>}
        </Box>
    </Card>
);

function Dashboard(props: DashboardProps & { history: History }): JSX.Element {
    const favorites = useFavoriteStatus();
    const [favoritePage, setFavoriteParams] = useCloudAPI<Page<File>>(
        listFavorites({itemsPerPage: 10, page: 0}),
        emptyPage
    );

    const projectNames = getProjectNames(useProjectStatus());

    const [news] = useCloudAPI<Page<NewsPost>>(newsRequest({
        itemsPerPage: 10,
        page: 0,
        withHidden: false,
    }), emptyPage);

    const [quota, fetchQuota] = useCloudAPI<RetrieveQuotaResponse>(
        retrieveQuota({
            path: Client.activeHomeFolder,
            includeUsage: true
        }),
        {quotaInBytes: 0, quotaUsed: 0}
    );

    const [wallets, setWalletsParams] = useCloudAPI<RetrieveBalanceResponse>(
        retrieveBalance({
            id: undefined,
            type: undefined,
            includeChildren: false
        }), {wallets: []}
    );

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
        setWalletsParams(retrieveBalance({
            id: undefined,
            type: undefined,
            includeChildren: false
        }));
        props.fetchRecentAnalyses();
    }

    const favoriteOrUnfavorite = async (file: File): Promise<void> => {
        await favorites.toggle(file.path);
        setFavoriteParams(listFavorites({itemsPerPage: 10, page: 0}));
    };

    const {
        recentAnalyses,
        notifications,
        analysesLoading,
        recentJobsError
    } = props;

    const onNotificationAction = (notification: Notification): void =>
        UF.onNotificationAction(props.history, props.setActiveProject, notification, projectNames);

    const main = (
        <DashboardGrid minmax={435} gridGap={16}>
            <DashboardNews news={news.data.items} loading={news.loading}/>
            <DashboardFavoriteFiles
                error={favoritePage.error?.why}
                files={favoritePage.data.items}
                isLoading={favoritePage.loading}
                favorite={favoriteOrUnfavorite}
            />

            <DashboardAnalyses
                error={recentJobsError}
                analyses={recentAnalyses}
                isLoading={analysesLoading}
            />

            <DashboardNotifications
                onNotificationAction={onNotificationAction}
                notifications={notifications}
                readAll={props.readAll}
            />

            <DashboardResources
                wallets={wallets.data.wallets}
                quota={quota.data}
                loading={wallets.loading || quota.loading}
            />
        </DashboardGrid>
    );

    return (<MainContainer main={main}/>);
}
const DashboardGrid = styled(GridCardGroup)`
`;


const DashboardFavoriteFiles = ({files, isLoading, favorite, error }: {
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
            <NoResultsCardBody title={"No favorite files"}>
                <Text>
                    Click the <Icon name={"starEmpty"}/> next to one of your files to mark it as a favorite.
                    All of your favorite files will appear here.
                    <Link to={fileTablePage(Client.activeHomeFolder)}>
                        <Button fullWidth mt={8}>Explore files</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
        )}
        <Error error={error}/>
        <List>
            {files.slice(0, 7).map(file => (
                <Flex alignItems="center" key={file.path} pt="0.5em" pb="6.4px">
                    <ListFileContent file={file} pixelsWide={200}/>
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

const ListFileContent = ({file, pixelsWide}: { file: File; pixelsWide: number }): JSX.Element => {
    const iconType = UF.iconFromFilePath(file.path, file.fileType);
    const projects = getProjectNames(useProjectStatus());
    return (
        <Flex alignItems="center">
            <FileIcon fileIcon={iconType}/>
            <Link ml="0.5em" to={fileTablePage(isDirectory(file) ? file.path : getParentPath(file.path))}>
                <EllipsedText fontSize={2} width={pixelsWide}>
                    {getFilenameFromPath(file.path, projects)}
                </EllipsedText>
            </Link>
        </Flex>
    );
};

const DashboardAnalyses = ({analyses, isLoading, error,}: {
    analyses: JobWithStatus[];
    isLoading: boolean;
    error?: string
}): JSX.Element => (
    <DashboardCard
        title={<Link to={"/applications/results"}><Heading.h3>Recent Runs</Heading.h3></Link>}
        color="purple"
        isLoading={isLoading}
        icon={"apps"}
    >
        {analyses.length || error ? null : (
            <NoResultsCardBody title={"No recent application runs"}>
                <Text>
                    When you run an application on UCloud the results will appear here.

                    <Link to={"/applications/overview"} mt={8}>
                        <Button fullWidth mt={8}>Explore applications</Button>
                    </Link>
                </Text>
            </NoResultsCardBody>
        )}
        <Error error={error}/>
        <List>
            {analyses.slice(0, 7).map((analysis: JobWithStatus, index: number) => (
                <Flex key={index} alignItems="center" pt="0.5em" pb="8.4px">
                    <JobStateIcon
                        size="1.2em"
                        pr="0.3em"
                        state={analysis.state}
                        isExpired={isRunExpired(analysis)}
                        mr="8px"
                    />
                    <Link to={`/applications/results/${analysis.jobId}`}>
                        <EllipsedText width={130} fontSize={2}>
                            {analysis.metadata.title}
                        </EllipsedText>
                    </Link>
                    <Box ml="auto"/>
                    <Text fontSize={1} color="grey">{formatDistanceToNow(new Date(analysis.modifiedAt!), {
                        addSuffix: true
                    })}</Text>
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
        color={"darkGreen"}
        isLoading={false}
        icon={"notification"}
        title={"Recent Notifications"}
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

export const NoResultsCardBody: React.FunctionComponent<{ title: string }> = props => {
    return <Flex
        alignItems={"center"}
        justifyContent={"center"}
        height={"calc(100% - 60px)"}
        minHeight={"250px"}
        mt={"-30px"}
        width={"100%"}
        flexDirection={"column"}
    >
        <Heading.h4>{props.title}</Heading.h4>
        {props.children}
    </Flex>;
};

function DashboardResources({wallets, loading, quota}: {
    wallets: WalletBalance[];
    quota: RetrieveQuotaResponse,
    loading: boolean
}): JSX.Element | null {
    wallets.sort((a, b) => (a.balance < b.balance) ? 1 : -1);
    const applyLinkButton = <Link to={"/project/grants-landing"}>
        <Button fullWidth mt={8}>Apply for resources</Button>
    </Link>;

    return (
        <DashboardCard
            title={<Link to={"/project/subprojects"}><Heading.h3>Resources</Heading.h3></Link>}
            color="red"
            isLoading={loading}
            icon={"grant"}
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
                    <Flex flexDirection={"column"} height={"calc(100% - 60px)"}>
                        <Box mx="8px" my="5px">
                            <Table>
                                <tbody>
                                {
                                    wallets.slice(0, 7).map((n, i) => (
                                        <TableRow key={i}>
                                            <TableCell>{n.wallet.paysFor.provider} / {n.wallet.paysFor.id}</TableCell>
                                            <TableCell>
                                                <Balance
                                                    textAlign={"right"}
                                                    amount={n.balance}
                                                    productCategory={n.wallet.paysFor}
                                                />
                                            </TableCell>
                                        </TableRow>
                                    ))
                                }
                                <TableRow>
                                    {/* This is hardcoded for now (pending issue #1246) */}
                                    <TableCell>ucloud / cephfs (Quota)</TableCell>
                                    <TableCell textAlign={"right"}>
                                        {sizeToString(quota.quotaUsed ?? 0)}
                                        {" "}of{" "}
                                        {sizeToString(quota.quotaInBytes)}
                                        {" "}({(100 * (quota.quotaInBytes !== 0 ?
                                            (quota.quotaUsed ?? 0 / quota.quotaInBytes) : 1
                                    )).toFixed(2)}%)
                                    </TableCell>
                                </TableRow>
                                </tbody>
                            </Table>
                        </Box>
                        <Box flexGrow={1}/>
                        {applyLinkButton}
                    </Flex>
                </>
            }
        </DashboardCard>
    );
}

function DashboardNews({news, loading}: { news: NewsPost[]; loading: boolean }): JSX.Element | null {
    return (
        <DashboardCard
            title={<Link to={"/news/list/"}><Heading.h3>News</Heading.h3></Link>}
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
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => ({
    ...state.dashboard,
    notifications: state.notifications.items,
});

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
