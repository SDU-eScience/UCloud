import * as React from "react";
import {Cloud} from "Authentication/SDUCloudObject"
import {
    favoriteFile,
    getParentPath,
    getFilenameFromPath,
    replaceHomeFolder,
    isDirectory
} from "Utilities/FileUtilities";
import {updatePageTitle, setActivePage} from "Navigation/Redux/StatusActions";
import {
    setAllLoading,
    fetchFavorites,
    fetchRecentAnalyses,
    fetchRecentFiles,
    receiveFavorites
} from "./Redux/DashboardActions";
import {connect} from "react-redux";
import * as moment from "moment";
import {FileIcon} from "UtilityComponents";
import {DashboardProps, DashboardOperations, DashboardStateProps} from ".";
import {Notification, NotificationEntry} from "Notifications";
import {Analysis, AppState} from "Applications";
import {File} from "Files";
import {Dispatch} from "redux";
import {ReduxObject} from "DefaultObjects";
import {Box, Flex, Card, Text, Link, Icon} from "ui-components";
import {EllipsedText} from "ui-components/Text"
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import {GridCardGroup} from "ui-components/Grid";
import {TextSpan} from "ui-components/Text";
import {fileTablePage} from "Utilities/FileUtilities";
import {notificationRead, readAllNotifications} from "Notifications/Redux/NotificationsActions";
import {History} from "history";
import Spinner from "LoadingIcon/LoadingIcon";
import * as UF from "UtilityFunctions";
import * as Accounting from "Accounting";
import {MainContainer} from "MainContainer/MainContainer";
import {fetchUsage} from "Accounting/Redux/AccountingActions";
import {SidebarPages} from "ui-components/Sidebar";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";

const DashboardCard: React.FunctionComponent<{title: string, isLoading: boolean}> = ({title, isLoading, children}) => (
    <Card height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} style={{overflow: "hidden"}}>
        <Flex bg="lightGray" color="darkGray" px={3} py={2} alignItems="center">
            <Heading.h4>{title}</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            {isLoading && <Spinner size={24} />}
            <Box pb="0.5em" />
            {!isLoading ? children : null}
        </Box>
    </Card>
);

function Dashboard(props: DashboardProps & {history: History}) {

    React.useEffect(() => {
        props.onInit();
        reload(true);
        props.setRefresh(() => reload(true));
        return () => props.setRefresh();
    }, []);

    function reload(loading: boolean) {
        props.setAllLoading(loading)
        props.fetchFavorites();
        props.fetchRecentFiles();
        props.fetchRecentAnalyses();
        props.fetchUsage();
    }

    const onNotificationAction = (notification: Notification) => {
        // FIXME: Not DRY, reused
        switch (notification.type) {
            case "APP_COMPLETE":
                props.history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
                props.history.push("/shares");
                break;
        }
    };

    const favoriteOrUnfavorite = (file: File) => {
        favoriteFile(file, Cloud);
        props.receiveFavorites(favoriteFiles.filter(f => f.favorited));
    };

    const {favoriteFiles, recentFiles, recentAnalyses, notifications, favoriteLoading, recentLoading, analysesLoading} = props;
    favoriteFiles.forEach(f => f.favorited = true);
    const main = (
        <>
            <GridCardGroup minmax={290}>
                <DashboardFavoriteFiles
                    files={favoriteFiles}
                    isLoading={favoriteLoading}
                    favorite={file => favoriteOrUnfavorite(file)}
                />

                <DashboardRecentFiles
                    files={recentFiles}
                    isLoading={recentLoading}
                />

                <DashboardAnalyses
                    analyses={recentAnalyses}
                    isLoading={analysesLoading}
                />

                <DashboardNotifications
                    onNotificationAction={onNotificationAction}
                    notifications={notifications}
                    readAll={() => props.readAll()}
                />

                <DashboardCard title={"Storage Used"} isLoading={false}>
                    <Accounting.Usage resource={"storage"} subResource={"bytesUsed"} />
                </DashboardCard>

                <DashboardCard title={"Compute Time Used"} isLoading={false}>
                    <Accounting.Usage resource={"compute"} subResource={"timeUsed"} />
                </DashboardCard>
            </GridCardGroup>
        </>
    );

    return (<MainContainer main={main} />);
}


const DashboardFavoriteFiles = ({files, isLoading, favorite}: {files: File[], isLoading: boolean, favorite: (file: File) => void}) => (
    <DashboardCard title="Favorite Files" isLoading={isLoading}>
        {files.length || isLoading ? null : (<Heading.h6>No favorites found</Heading.h6>)}
        <List>
            {files.map(file => (
                <Flex alignItems="center" key={file.fileId!} pt="0.5em" pb="6.4px">
                    <ListFileContent file={file} link={false} pixelsWide={200} />
                    <Icon ml="auto" size="1em" name="starFilled" color="blue" cursor="pointer" onClick={() => favorite(file)} />
                </Flex>)
            )}
        </List>
    </DashboardCard>
);

const ListFileContent = ({file, link, pixelsWide}: {file: File, link: boolean, pixelsWide: number}) => {
    const iconType = UF.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder);
    return (
        <Flex alignItems="center">
            <FileIcon fileIcon={iconType} link={link} />
            <Link ml="0.5em" to={fileTablePage(isDirectory(file) ? file.path : getParentPath(file.path))}>
                <EllipsedText fontSize={2} width={pixelsWide}>
                    {getFilenameFromPath(replaceHomeFolder(file.path, Cloud.homeFolder))}
                </EllipsedText>
            </Link>
        </Flex>
    );
};

const DashboardRecentFiles = ({files, isLoading}: {files: File[], isLoading: boolean}) => (
    <DashboardCard title="Recently Used Files" isLoading={isLoading}>
        {files.length || isLoading ? null : (<Heading.h6>No recent files found</Heading.h6>)}
        <List>
            {files.map((file, i) => (
                <Flex key={i} alignItems="center" pt="0.5em" pb="0.3em">
                    <ListFileContent file={file} link={file.link} pixelsWide={130} />
                    <Box ml="auto" />
                    <Text fontSize={1} color="grey">{moment(new Date(file.modifiedAt!)).fromNow()}</Text>
                </Flex>
            ))}
        </List>
    </DashboardCard>
);

const DashboardAnalyses = ({analyses, isLoading}: {analyses: Analysis[], isLoading: boolean}) => (
    <DashboardCard title="Recent Jobs" isLoading={isLoading}>
        {isLoading || analyses.length ? null : (<Heading.h6>No results found</Heading.h6>)}
        <List>
            {analyses.map((analysis: Analysis, index: number) =>
                <Flex key={index} alignItems="center" pt="0.5em" pb="8.4px">
                    <Icon name={statusToIconName(analysis.state)}
                        color={statusToColor(analysis.state)}
                        size="1.2em"
                        pr="0.3em"
                    />
                    <Link to={`/applications/results/${analysis.jobId}`}><TextSpan fontSize={2}>{analysis.metadata.title}</TextSpan></Link>
                    <Box ml="auto" />
                    <Text fontSize={1} color="grey">{moment(new Date(analysis.modifiedAt!)).fromNow()}</Text>
                </Flex>
            )}
        </List>
    </DashboardCard>
);

interface DashboardNotificationProps {
    onNotificationAction: (notification: Notification) => void
    notifications: Notification[]
    readAll: () => void
}

const DashboardNotifications = ({notifications, readAll, onNotificationAction}: DashboardNotificationProps) => (
    <Card height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} style={{overflow: "hidden"}}>
        <Flex bg="lightGray" color="darkGray" px={3} py={2}>
            <Heading.h4>Recent Notifications</Heading.h4>
            <Box ml="auto" />
            <Icon name="checkDouble" cursor="pointer" color="iconColor" color2="iconColor2" title="Mark all as read" onClick={readAll} />
        </Flex>
        {notifications.length === 0 ? <Heading.h6 pl={"16px"} pt="10px">No notifications</Heading.h6> : null}
        <List>
            {notifications.slice(0, 7).map((n, i) =>
                <Flex key={i}>
                    <NotificationEntry notification={n} onAction={onNotificationAction} />
                </Flex>
            )}
        </List>
    </Card>
);

const statusToIconName = (status: AppState) => {
    switch (status) {
        case AppState.SUCCESS:
            return "check";
        case AppState.FAILURE:
            return "close";
        case AppState.SCHEDULED:
            return "calendar";
        case AppState.RUNNING:
            return "chrono";
        case AppState.VALIDATED:
            return "checkDouble";
        default:
            return "ellipsis";
    }
};

const statusToColor = (status: AppState) => status === AppState.FAILURE ? "red" : "green";

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    onInit: () => {
        dispatch(updatePageTitle("Dashboard"));
        dispatch(setActivePage(SidebarPages.None));
    },
    setAllLoading: loading => dispatch(setAllLoading(loading)),
    fetchFavorites: async () => dispatch(await fetchFavorites()),
    fetchRecentFiles: async () => dispatch(await fetchRecentFiles()),
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    fetchUsage: async () => {
        dispatch(await fetchUsage("storage", "bytesUsed"))
        dispatch(await fetchUsage("compute", "timeUsed"))
    },
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    // FIXME: Make action instead (favoriteFile)
    receiveFavorites: files => dispatch(receiveFavorites(files)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => ({
    ...state.dashboard,
    notifications: state.notifications.items,
    favoriteFilesLength: state.dashboard.favoriteFiles.length // Hack to ensure re-rendering
});

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
