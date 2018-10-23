import * as React from "react";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { iconFromFilePath, toLowerCaseAndCapitalize } from "UtilityFunctions";
import { Link } from "react-router-dom";
import { Cloud } from "Authentication/SDUCloudObject"
import { favoriteFile, getParentPath, getFilenameFromPath } from "Utilities/FileUtilities";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites, setErrorMessage } from "./Redux/DashboardActions";
import { connect } from "react-redux";
import { Card as SCard, List as SList, Icon as SIcon, Message as SMessage } from "semantic-ui-react";
import * as moment from "moment";
import { FileIcon } from "UtilityComponents";
import { DASHBOARD_FAVORITE_ERROR } from "./Redux/DashboardReducer";
import { DashboardProps, DashboardOperations, DashboardStateProps } from ".";
import { Notification } from "Notifications";
import { Analysis } from "Applications";
import { File, FileType } from "Files";
import { Dispatch } from "redux";
import Icon, { EveryIcon } from "ui-components/Icon";

class Dashboard extends React.Component<DashboardProps> {
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

    render() {
        const { favoriteFiles, recentFiles, recentAnalyses, notifications, favoriteLoading, recentLoading,
            analysesLoading, errors } = this.props;
        favoriteFiles.forEach((f: File) => f.favorited = true);
        const favoriteOrUnfavorite = (file: File) => {
            favoriteFile(file, Cloud);
            this.props.receiveFavorites(favoriteFiles.filter((f: File) => f.favorited));
        };

        return (
            <React.StrictMode>
                {errors.length ? <SMessage list={errors} onDismiss={this.props.errorDismiss} negative /> : null}
                <SCard.Group className="mobile-padding">
                    <DashboardFavoriteFiles
                        files={favoriteFiles}
                        isLoading={favoriteLoading}
                        favorite={(file: File) => favoriteOrUnfavorite(file)}
                    />
                    <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                    <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                    <DashboardNotifications notifications={notifications} />
                </SCard.Group>
            </React.StrictMode>
        );
    }
}

const DashboardFavoriteFiles = ({ files, isLoading, favorite }: { files: File[], isLoading: boolean, favorite: Function }) => {
    const noFavorites = files.length || isLoading ? null : (<h3><small>No favorites found</small></h3>);
    const filesList = files.map((file: File, i: number) =>
        (<SList.Item key={i} className="itemPadding">
            <SList.Content floated="right">
                <SIcon name="star" color="blue" onClick={() => favorite(file)} />
            </SList.Content>
            <ListFileContent path={file.path} type={file.fileType} link={false} pixelsWide={200} />
        </SList.Item>)
    );

    return (
        <SCard fluid={window.innerWidth <= 645}>
            <SCard.Content>
                <SCard.Header content="Favorite files" />
                <DefaultLoading loading={isLoading} />
                {noFavorites}
                <SList divided size={"large"}>
                    {filesList}
                </SList>
            </SCard.Content >
        </SCard>)
};

const ListFileContent = ({ path, type, link, pixelsWide }: { path: string, type: FileType, link: boolean, pixelsWide: 117 | 200 }) =>
    <SList.Content>
        <FileIcon name={iconFromFilePath(path, type, Cloud.homeFolder)} size={undefined} link={link} color="grey" />
        <Link to={`/files/${type === "FILE" ? getParentPath(path) : path}`}>
            <span className={`limited-width-string-${pixelsWide}px`}>{getFilenameFromPath(path)}</span>
        </Link>
    </SList.Content>


const DashboardRecentFiles = ({ files, isLoading }: { files: File[], isLoading: boolean }) => {
    return (
        <SCard fluid={window.innerWidth <= 645}>
            <SCard.Content>
                <SCard.Header content="Recently used files" />
                {isLoading || files.length ? null : (<h3><small>No recently used files</small></h3>)}
                <DefaultLoading loading={isLoading} />
                <SList divided size={"large"}>
                    {files.map((file, i) => (
                        <SList.Item key={i} className="itemPadding">
                            <SList.Content floated="right">
                                <SList.Description>{moment(new Date(file.modifiedAt)).fromNow()}</SList.Description>
                            </SList.Content>
                            <ListFileContent path={file.path} type={file.fileType} link={file.link} pixelsWide={117} />
                        </SList.Item>
                    ))}
                </SList>
            </SCard.Content>
        </SCard>);
};

const DashboardAnalyses = ({ analyses, isLoading }: { analyses: Analysis[], isLoading: boolean }) => (
    <SCard fluid={window.innerWidth <= 645}>
        <SCard.Content>
            <SCard.Header content="Recent Analyses" />
            <DefaultLoading loading={isLoading} />
            {isLoading || analyses.length ? null : (<h3><small>No Analyses found</small></h3>)}
            <SList divided size={"large"}>
                {analyses.map((analysis: Analysis, index: number) =>
                    <SList.Item key={index} className="itemPadding">
                        <SList.Content floated="right" content={toLowerCaseAndCapitalize(analysis.state)} />
                        <SList.Icon name={statusToIconName(analysis.state)} color={statusToColor(analysis.state)} />
                        <SList.Content>
                            <Link to={`/analyses/${analysis.jobId}`}>{analysis.appName}</Link>
                        </SList.Content>
                    </SList.Item>
                )}
            </SList>
        </SCard.Content>
    </SCard>
);

const DashboardNotifications = ({ notifications }: { notifications: Notification[] }) => (
    <SCard fluid={window.innerWidth <= 645}>
        <SCard.Content>
            <SCard.Header content="Recent notifications" />
            {notifications.length === 0 ? <h3><small>No notifications</small></h3> : null}
            <SList divided>
                {notifications.slice(0, 10).map((n: Notification, i: number) =>
                    <SList.Item key={i}>
                        <Notification notification={n} />
                    </SList.Item>
                )}
            </SList>
        </SCard.Content>
    </SCard>
);

const DashboardAccounting = () => { }

const Notification = ({ notification }: { notification: Notification }) => {
    switch (notification.type) {
        case "SHARE_REQUEST":
            return (
                <>
                    <SList.Content floated="right">
                        <SList.Description content={moment(new Date(notification.ts as number)).fromNow()} />
                    </SList.Content>
                    <SList.Icon name="share alternate" color="blue" verticalAlign="middle" />
                    <SList.Content header="Share Request" description={notification.message} />
                </>
            )
        default: {
            return null;
        }
    }
};

const statusToIconName = (status: string) => status === "SUCCESS" ? "check" : "x";
const statusToColor = (status: string) => status === "SUCCESS" ? "green" : "red";

const mapDispatchToProps = (dispatch: Dispatch): DashboardOperations => ({
    errorDismiss: () => dispatch(setErrorMessage(DASHBOARD_FAVORITE_ERROR, undefined)),
    updatePageTitle: () => dispatch(updatePageTitle("Dashboard")),
    setAllLoading: (loading) => dispatch(setAllLoading(loading)),
    fetchFavorites: async () => dispatch(await fetchFavorites()),
    fetchRecentFiles: async () => dispatch(await fetchRecentFiles()),
    fetchRecentAnalyses: async () => dispatch(await fetchRecentAnalyses()),
    // FIXME: Make action instead
    receiveFavorites: (files) => dispatch(receiveFavorites(files))
});

const mapStateToProps = (state): DashboardStateProps => {
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
