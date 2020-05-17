import * as Accounting from "Accounting";
import {fetchUsage} from "Accounting/Redux/AccountingActions";
import {JobWithStatus} from "Applications";
import {Client} from "Authentication/HttpClientInstance";
import {formatDistanceToNow} from "date-fns/esm";
import {emptyPage, ReduxObject} from "DefaultObjects";
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
import {Box, Button, Card, Flex, Icon, Link, Text, theme} from "ui-components";
import Error from "ui-components/Error";
import {GridCardGroup} from "ui-components/Grid";
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {SidebarPages} from "ui-components/Sidebar";
import {EllipsedText} from "ui-components/Text";
import {fileTablePage} from "Utilities/FileUtilities";
import {
    getFilenameFromPath,
    getParentPath,
    isDirectory,
    replaceHomeOrProjectFolder
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
import {listFavorites, useFavoriteStatus} from "Files/favorite";
import {useCloudAPI} from "Authentication/DataHook";
import {Page} from "Types";

export const DashboardCard: React.FunctionComponent<{title: string; color: string; isLoading: boolean}> = ({title, color, isLoading, children}) => (
    <Card overflow="hidden" height="auto" width={1} boxShadow="sm" borderWidth={0} borderRadius={6}>
        <Flex px={3} py={2} alignItems="center" style={{borderTop:`5px solid ${color}`}} >
            <Heading.h3>{title}</Heading.h3>
        </Flex>
        <Box px={3} py={1}>         
            {!isLoading ? children : <Spinner /> }
        </Box>
    </Card>
);

function Dashboard(props: DashboardProps & {history: History}): JSX.Element {
    const favorites = useFavoriteStatus();
    const [favoritePage, setFavoriteParams] = useCloudAPI<Page<File>>(
        listFavorites({itemsPerPage: 10, page: 0}),
        emptyPage
    );
    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean): void {
        props.setAllLoading(loading);
        setFavoriteParams(listFavorites({itemsPerPage: 10, page: 0}));
        props.fetchRecentAnalyses();
        props.fetchUsage();
    }

    const onNotificationAction = (notification: Notification): void => {
        // FIXME: Not DRY, reused
        switch (notification.type) {
            case "APP_COMPLETE":
                props.history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
                props.history.push("/shares");
                break;
            case "REVIEW_PROJECT":
                props.history.push("/projects/");
                break;
            case "PROJECT_INVITE":
                props.history.push("/projects/");
                break;
        }
    };

    const favoriteOrUnfavorite = async (file: File) => {
        await favorites.toggle(file.path);
        setFavoriteParams(listFavorites({itemsPerPage: 10, page: 0}));
    };

    const {
        recentAnalyses,
        notifications,
        analysesLoading,
        recentJobsError
    } = props;

    const main = (
        <>
            <GridCardGroup minmax={315}>
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
                <DashboardCard title="Resources"  color={theme.colors.red} isLoading={false}>
                    <Accounting.Usage resource="storage" subResource="bytesUsed" renderTitle/>
                    <Box pb="12px"/>
                    <Accounting.Usage resource="compute" subResource="timeUsed" renderTitle/>
                </DashboardCard>
            </GridCardGroup>
        </>
    );

    return (<MainContainer main={main} />);
}


const DashboardFavoriteFiles = ({
    files,
    isLoading,
    favorite,
    error
}: {files: File[]; isLoading: boolean; favorite: (file: File) => void; error?: string}): JSX.Element => (
        <DashboardCard title="Favorite Files" color={theme.colors.blue} isLoading={isLoading}>
            {files.length || error ? null : (
                <NoEntries
                    text="Your favorite files will appear here"
                    to={fileTablePage(Client.homeFolder)}
                    buttonText="Explore files"
                />
            )}
            <Error error={error} />
            <List>
                {files.map(file => (
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

interface NoEntriesProps {
    text: string;
    to: string;
    buttonText: string;
}

const NoEntries = (props: NoEntriesProps): JSX.Element => (
    <Box textAlign="center">
        <Text fontSize="16px" my="8px">{props.text}</Text>
        <Link to={props.to}><Button>{props.buttonText}</Button></Link>
    </Box>
);

const ListFileContent = ({file, pixelsWide}: {file: File; pixelsWide: number}): JSX.Element => {
    const iconType = UF.iconFromFilePath(file.path, file.fileType);
    return (
        <Flex alignItems="center">
            <FileIcon fileIcon={iconType} />
            <Link ml="0.5em" to={fileTablePage(isDirectory(file) ? file.path : getParentPath(file.path))}>
                <EllipsedText fontSize={2} width={pixelsWide}>
                    {getFilenameFromPath(replaceHomeOrProjectFolder(file.path, Client))}
                </EllipsedText>
            </Link>
        </Flex>
    );
};

const DashboardAnalyses = ({
    analyses,
    isLoading,
    error,
}: {analyses: JobWithStatus[]; isLoading: boolean; error?: string}): JSX.Element => (
        <DashboardCard title="Recent Jobs"  color={theme.colors.purple} isLoading={isLoading}>
            {analyses.length || error ? null : (
                <NoEntries
                    text="No recent jobs"
                    buttonText="Explore apps"
                    to="/applications/overview"
                />
            )}
            <Error error={error} />
            <List>
                {analyses.map((analysis: JobWithStatus, index: number) => (
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
                        <Box ml="auto" />
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
    <Card height="auto" width={1} overflow="hidden" boxShadow="sm" borderWidth={0} borderRadius={6}>
        <Flex px={3} py={2} style={{borderTop:`5px solid ${theme.colors.darkGreen}`}}>
            <Heading.h3>Recent Notifications</Heading.h3>
            <Box ml="auto" />
            <Icon
                name="checkDouble"
                cursor="pointer"
                color="iconColor"
                color2="iconColor2"
                title="Mark all as read"
                onClick={props.readAll}
            />
        </Flex>
        {props.notifications.length === 0 ? <Heading.h6 pl="16px" pt="10px">No notifications</Heading.h6> : null}
        <List>
            {props.notifications.slice(0, 7).map((n, i) => (
                <Flex key={i}>
                    <NotificationEntry notification={n} onAction={props.onNotificationAction} />
                </Flex>
            ))}
        </List>
    </Card>
);

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Dashboard"));
        dispatch(setActivePage(SidebarPages.None));
    },
    setAllLoading: loading => dispatch(setAllLoading(loading)),
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    fetchUsage: async () => {
        dispatch(await fetchUsage("storage", "bytesUsed"));
        dispatch(await fetchUsage("compute", "timeUsed"));
    },
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => ({
    ...state.dashboard,
    notifications: state.notifications.items,
});

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
