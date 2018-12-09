import * as React from "react";
import { iconFromFilePath, toLowerCaseAndCapitalize } from "UtilityFunctions";
import { Cloud } from "Authentication/SDUCloudObject"
import { favoriteFile, getParentPath, getFilenameFromPath, replaceHomeFolder, isDirectory } from "Utilities/FileUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites, setErrorMessage } from "./Redux/DashboardActions";
import { connect } from "react-redux";
import * as moment from "moment";
import { FileIcon } from "UtilityComponents";
import { DASHBOARD_FAVORITE_ERROR } from "./Redux/DashboardReducer";
import { DashboardProps, DashboardOperations, DashboardStateProps } from ".";
import { Notification, NotificationEntry } from "Notifications";
import { Analysis } from "Applications";
import { File, FileType } from "Files";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { Error, Box, Flex, Card, Text, Link, theme, Icon, FtIcon } from "ui-components";
import { EllipsedText } from "ui-components/Text"
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import { CardGroup } from "ui-components/Card";
import { TextSpan } from "ui-components/Text";
import { fileTablePage } from "Utilities/FileUtilities";
import { notificationRead, readAllNotifications } from "Notifications/Redux/NotificationsActions";
import { History } from "history";
import { default as Spinner } from "LoadingIcon/LoadingIcon_new";

class Dashboard extends React.Component<DashboardProps & { history: History }> {
    constructor(props) {
        super(props);
        const { favoriteFiles, recentFiles, recentAnalyses } = props;
        props.updatePageTitle();
        if (!favoriteFiles.length && !recentFiles.length && !recentAnalyses.length) {
            props.setAllLoading(true);
        }
        props.fetchFavorites();
        props.fetchRecentFiles();
        props.fetchRecentAnalyses();
    }

    private onNotificationAction = (notification: Notification) => {
        // FIXME: Not DRY, reused
        switch (notification.type) {
            case "APP_COMPLETE":
                // TODO This is buggy! Does't update if already present on analyses page
                // TODO Should refactor these URLs somewhere else
                this.props.history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
                // TODO This is a bit lazy
                this.props.history.push("/shares");
                break;
        }
    }

    render() {
        const { favoriteFiles, recentFiles, recentAnalyses, notifications, favoriteLoading, recentLoading,
            analysesLoading, errors, ...props } = this.props;
        favoriteFiles.forEach(f => f.favorited = true);
        const favoriteOrUnfavorite = (file: File) => {
            favoriteFile(file, Cloud);
            this.props.receiveFavorites(favoriteFiles.filter(f => f.favorited));
        };

        return (
            <React.StrictMode>
                <Error error={errors.join(",\n")} clearError={props.errorDismiss} />
                <CardGroup>
                    <DashboardFavoriteFiles
                        files={favoriteFiles}
                        isLoading={favoriteLoading}
                        favorite={file => favoriteOrUnfavorite(file)}
                    />
                    <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                    <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                    <DashboardNotifications onNotificationAction={this.onNotificationAction} notifications={notifications} readAll={() => props.readAll()} />
                </CardGroup>
            </React.StrictMode>
        );
    }
}

const DashBoardCard = ({ title, isLoading, children }: { title: string, isLoading: boolean, children?: any }) => (
    <Card height="auto" width={290} boxShadowSize='sm' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
            <Heading.h4>{title}</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            {isLoading && <Spinner size={24} />}
            <Box pb="0.5em" />
            {children}
        </Box>
    </Card>
)

const DashboardFavoriteFiles = ({ files, isLoading, favorite }: { files: File[], isLoading: boolean, favorite: (file: File) => void }) => (
    <DashBoardCard title="Favorite Files" isLoading={isLoading}>
        {files.length || isLoading ? null : (<Heading.h6>No favorites found</Heading.h6>)}
        <List>
            {files.map((file, i) => (
                <Flex key={i} pt="0.8em" pb="6px">
                    <ListFileContent path={file.path} type={file.fileType} link={false} pixelsWide={200} />
                    <Box ml="auto" />
                    <Box><i className="fas fa-star" style={{ color: theme.colors.blue }} onClick={() => favorite(file)} /></Box>
                </Flex>)
            )}
        </List>
    </DashBoardCard>
);

const ListFileContent = ({ path, type, link, pixelsWide }: { path: string, type: FileType, link: boolean, pixelsWide: number }) => (
    <>
        <FileIcon  ext={"..."/* iconFromFilePath(path, type, Cloud.homeFolder) */} link={link} icon={isDirectory({ fileType: type })?"ftFolder":undefined} />
        <Link ml="0.5em" to={fileTablePage(isDirectory({ fileType: type }) ? path : getParentPath(path))}>
            <EllipsedText fontSize={2} width={pixelsWide}>
                {getFilenameFromPath(replaceHomeFolder(path, Cloud.homeFolder))}
            </EllipsedText>
        </Link>
    </>
);

const DashboardRecentFiles = ({ files, isLoading }: { files: File[], isLoading: boolean }) => (
    <DashBoardCard title="Recently used files" isLoading={isLoading}>
        <List>
            {files.map((file, i) => (
                <Flex key={i} pt="0.8em" pb="6px">
                    <ListFileContent path={file.path} type={file.fileType} link={file.link} pixelsWide={130} />
                    <Box ml="auto" />
                    <Text fontSize={1} color="grey">{moment(new Date(file.modifiedAt)).fromNow()}</Text>
                </Flex>
            ))}
        </List>
    </DashBoardCard>
);

const DashboardAnalyses = ({ analyses, isLoading }: { analyses: Analysis[], isLoading: boolean }) => (
    <DashBoardCard title="Recent Jobs" isLoading={isLoading}>
        {isLoading || analyses.length ? null : (<Heading.h6>No results found</Heading.h6>)}
        <List>
            {analyses.map((analysis: Analysis, index: number) =>
                <Flex key={index} pt="0.8em" pb="6px">
                    <Icon name={statusToIconName(analysis.state)}
                        color={statusToColor(analysis.state)}
                        size="1.5em"
                        pr="0.3em"
                    />
                    <Link to={`/applications/results/${analysis.jobId}`}><TextSpan fontSize={2}>{analysis.appName}</TextSpan></Link>
                    <Box ml="auto" />
                    <TextSpan fontSize={2}>{toLowerCaseAndCapitalize(analysis.state)}</TextSpan>
                </Flex>
            )}
        </List>
    </DashBoardCard>
);

interface DashboardNotificationProps {
    onNotificationAction: (notification: Notification) => void
    notifications: Notification[]
    readAll: () => void
}

const DashboardNotifications = ({ notifications, readAll, onNotificationAction }: DashboardNotificationProps) => (
    <Card height="auto" width={290} boxShadowSize='sm' borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3}>
            <Heading.h4>Recent notifications</Heading.h4>
            <Box ml="auto" />
            <i style={{ margin: "5px", cursor: "pointer" }} title="Mark all as read" onClick={readAll} className="fas fa-check-double"></i>
        </Flex>
        <Box px={3} py={1}>
            {notifications.length === 0 ? <Heading.h6>No notifications</Heading.h6> : null}
            <List>
                {notifications.slice(0, 7).map((n, i) =>
                    <Flex key={i}>
                        <NotificationEntry notification={n} onAction={onNotificationAction} />
                    </Flex>
                )}
            </List>
        </Box>
    </Card>
);

const statusToIconName = (status: string) => status === "SUCCESS" ? "check" : "close";
const statusToColor = (status: string) => status === "SUCCESS" ? "green" : "red";

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    errorDismiss: () => dispatch(setErrorMessage(DASHBOARD_FAVORITE_ERROR, undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Dashboard")),
    setAllLoading: (loading) => dispatch(setAllLoading(loading)),
    fetchFavorites: async () => dispatch(await fetchFavorites()),
    fetchRecentFiles: async () => dispatch(await fetchRecentFiles()),
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    notificationRead: async id => dispatch(await notificationRead(id)),
    readAll: async () => dispatch(await readAllNotifications()),
    // FIXME: Make action instead
    receiveFavorites: (files) => dispatch(receiveFavorites(files))
});

const mapStateToProps = (state: ReduxObject): DashboardStateProps => {
    const {
        errors,
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        favoriteLoading,
        recentLoading,
        analysesLoading,
    } = state.dashboard;
    return {
        errors,
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        favoriteLoading,
        recentLoading,
        analysesLoading,
        notifications: state.notifications.page.items,
        favoriteFilesLength: favoriteFiles.length // Hack to ensure re-rendering
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
