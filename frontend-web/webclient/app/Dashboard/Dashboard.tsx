import * as React from "react";
import { toLowerCaseAndCapitalize } from "UtilityFunctions";
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
import { Analysis, AppState } from "Applications";
import { File } from "Files";
import { Dispatch } from "redux";
import { ReduxObject } from "DefaultObjects";
import { Error, Box, Flex, Card, Text, Link, Icon } from "ui-components";
import { EllipsedText } from "ui-components/Text"
import * as Heading from "ui-components/Heading";
import List from "ui-components/List";
import { CardGroup } from "ui-components/Card";
import { TextSpan } from "ui-components/Text";
import { fileTablePage } from "Utilities/FileUtilities";
import { notificationRead, readAllNotifications } from "Notifications/Redux/NotificationsActions";
import { History } from "history";
import { default as Spinner } from "LoadingIcon/LoadingIcon_new";
import * as UF from "UtilityFunctions";

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
                this.props.history.push(`/applications/results/${notification.meta.jobId}`);
                break;
            case "SHARE_REQUEST":
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
                    <ListFileContent file={file} link={false} pixelsWide={200} />
                    <Box ml="auto" />
                    <Icon name="starFilled" color="blue" cursor="pointer" onClick={() => favorite(file)} />
                </Flex>)
            )}
        </List>
    </DashBoardCard>
);

const ListFileContent = ({ file, link, pixelsWide }: { file: File, link: boolean, pixelsWide: number }) => {
    const iconType = UF.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder);
    return (
        <>
            <FileIcon fileIcon={iconType} link={link} />
            <Link ml="0.5em" to={fileTablePage(isDirectory(file) ? file.path : getParentPath(file.path))}>
                <EllipsedText fontSize={2} width={pixelsWide}>
                    {getFilenameFromPath(replaceHomeFolder(file.path, Cloud.homeFolder))}
                </EllipsedText>
            </Link>
        </>
    );
}

const DashboardRecentFiles = ({ files, isLoading }: { files: File[], isLoading: boolean }) => (
    <DashBoardCard title="Recently used files" isLoading={isLoading}>
        <List>
            {files.map((file, i) => (
                <Flex key={i} pt="0.8em" pb="6px">
                    <ListFileContent file={file} link={file.link} pixelsWide={130} />
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
                    <TextSpan fontSize={2}>{UF.prettierString(analysis.state)}</TextSpan>
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
            <Icon name="checkDouble" m="5px" cursor="pointer" color="iconColor" color2="iconColor2" title="Mark all as read" onClick={readAll} />
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

const statusToIconName = (status: AppState) => {
    switch (status) {
        case AppState.SUCCESS:
            return "check";
        case AppState.FAILURE:
            return "close";
        default:
            return "ellipsis";
    }
}
const statusToColor = (status: AppState) => status === AppState.FAILURE ? "red" : "green";


status === "FAILURE" ? "red" : "green";

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
