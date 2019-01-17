import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject"
import { favoriteFile, getParentPath, getFilenameFromPath, replaceHomeFolder, isDirectory } from "Utilities/FileUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites, setErrorMessage } from "./Redux/DashboardActions";
import { connect } from "react-redux";
import * as moment from "moment";
import { FileIcon, RefreshButton } from "UtilityComponents";
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
import { GridCardGroup } from "ui-components/Grid";
import { TextSpan } from "ui-components/Text";
import { fileTablePage } from "Utilities/FileUtilities";
import { notificationRead, readAllNotifications } from "Notifications/Redux/NotificationsActions";
import { History } from "history";
import Spinner from "LoadingIcon/LoadingIcon";
import * as UF from "UtilityFunctions";
import * as Accounting from "Accounting";
import { MainContainer } from "MainContainer/MainContainer";
import { fetchUsage } from "Accounting/Redux/AccountingActions";
import { Spacer } from "ui-components/Spacer";

const DashboardCard = ({ title, isLoading, children }: { title: string, isLoading: boolean, children?: React.ReactNode }) => (
    <Card height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3} alignItems="center">
            <Heading.h4>{title}</Heading.h4>
        </Flex>
        <Box px={3} py={1}>
            {isLoading && <Spinner size={24} />}
            <Box pb="0.5em" />
            {!isLoading ? children : null}
        </Box>
    </Card>
);

class Dashboard extends React.Component<DashboardProps & { history: History }> {
    constructor(props: any) {
        super(props);
        const { favoriteFiles, recentFiles, recentAnalyses } = props;
        props.updatePageTitle();
        let loading = false;
        if (!favoriteFiles.length && !recentFiles.length && !recentAnalyses.length) {
            loading = true;
        }
        this.reload(loading);
    }

    private reload(loading: boolean) {
        const { ...props } = this.props;
        props.setAllLoading(loading)
        props.fetchFavorites();
        props.fetchRecentFiles();
        props.fetchRecentAnalyses();
        props.fetchUsage();

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

    private favoriteOrUnfavorite = (file: File) => {
        favoriteFile(file, Cloud);
        this.props.receiveFavorites(this.props.favoriteFiles.filter(f => f.favorited));
    };

    render() {
        const { favoriteFiles, recentFiles, recentAnalyses, notifications, favoriteLoading, recentLoading,
            analysesLoading, errors, ...props } = this.props;
        favoriteFiles.forEach(f => f.favorited = true);
        const main = (
            <React.StrictMode>
                <Error error={errors.join(",\n")} clearError={props.errorDismiss} />
                <Spacer
                    left={<></>}
                    right={<Box pb="5px"><RefreshButton
                        loading={favoriteLoading || recentLoading || analysesLoading}
                        onClick={() => this.reload(true)}
                    /></Box>}
                />
                <GridCardGroup minmax={290}>
                    <DashboardFavoriteFiles
                        files={favoriteFiles}
                        isLoading={favoriteLoading}
                        favorite={file => this.favoriteOrUnfavorite(file)}
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
                        onNotificationAction={this.onNotificationAction}
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
            </React.StrictMode>
        );

        return (
            <MainContainer
                main={main}
            />
        );
    }
}


const DashboardFavoriteFiles = ({ files, isLoading, favorite }: { files: File[], isLoading: boolean, favorite: (file: File) => void }) => (
    <DashboardCard title="Favorite Files" isLoading={isLoading}>
        {files.length || isLoading ? null : (<Heading.h6>No favorites found</Heading.h6>)}
        <List>
            {files.map((file, i) => (
                <Flex alignItems="center" key={i} pt="0.5em" pb="6.4px">
                    <ListFileContent file={file} link={false} pixelsWide={200} />
                    <Icon ml="auto" size="1em" name="starFilled" color="blue" cursor="pointer" onClick={() => favorite(file)} />
                </Flex>)
            )}
        </List>
    </DashboardCard>
);

const ListFileContent = ({ file, link, pixelsWide }: { file: File, link: boolean, pixelsWide: number }) => {
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
}

const DashboardRecentFiles = ({ files, isLoading }: { files: File[], isLoading: boolean }) => (
    <DashboardCard title="Recently Used Files" isLoading={isLoading}>
        <List>
            {files.map((file, i) => (
                <Flex alignItems="center" key={i} pt="0.5em" pb="0.3em">
                    <ListFileContent file={file} link={file.link} pixelsWide={130} />
                    <Box ml="auto" />
                    <Text fontSize={1} color="grey">{moment(new Date(file.modifiedAt)).fromNow()}</Text>
                </Flex>
            ))}
        </List>
    </DashboardCard>
);

const DashboardAnalyses = ({ analyses, isLoading }: { analyses: Analysis[], isLoading: boolean }) => (
    <DashboardCard title="Recent Jobs" isLoading={isLoading}>
        {isLoading || analyses.length ? null : (<Heading.h6>No results found</Heading.h6>)}
        <List>
            {analyses.map((analysis: Analysis, index: number) =>
                <Flex key={index} alignItems="center" pt="0.5em" pb="8.4px">
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
    </DashboardCard>
);

interface DashboardNotificationProps {
    onNotificationAction: (notification: Notification) => void
    notifications: Notification[]
    readAll: () => void
}

const DashboardNotifications = ({ notifications, readAll, onNotificationAction }: DashboardNotificationProps) => (
    <Card height="auto" width={1} boxShadow="sm" borderWidth={1} borderRadius={6} style={{ overflow: "hidden" }}>
        <Flex bg="lightGray" color="darkGray" p={3}>
            <Heading.h4>Recent Notifications</Heading.h4>
            <Box ml="auto" />
            <Icon name="checkDouble" cursor="pointer" color="iconColor" color2="iconColor2" title="Mark all as read" onClick={readAll} />
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
        case AppState.SCHEDULED:
            return "calendar";
        case AppState.RUNNING:
            return "chrono";
        case AppState.VALIDATED:
            return "checkDouble";
        default:
            return "ellipsis";
    }
}
const statusToColor = (status: AppState) => status === AppState.FAILURE ? "red" : "green";

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    errorDismiss: () => dispatch(setErrorMessage(DASHBOARD_FAVORITE_ERROR, undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Dashboard")),
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
    // FIXME: Make action instead
    receiveFavorites: files => dispatch(receiveFavorites(files))
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
