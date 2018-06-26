import * as React from "react";
import { DefaultLoading } from "../LoadingIcon/LoadingIcon";
import { getParentPath, iconFromFilePath } from "../../UtilityFunctions";
import { Link } from "react-router-dom";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { favorite, toLowerCaseAndCapitalize, getFilenameFromPath } from "../../UtilityFunctions";
import { updatePageTitle } from "../../Actions/Status";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites } from "../../Actions/Dashboard";
import { connect } from "react-redux";
import "./Dashboard.scss";
import "../Styling/Shared.scss";
import { Card, List, Icon } from "semantic-ui-react";
import * as moment from "moment";
import { FileIcon } from "../UtilityComponents";
import { File, Analysis } from "../../types/types";

interface DashboardProps {
    // Redux store props
    favoriteFiles: File[]
    recentFiles: File[]
    recentAnalyses: Analysis[]
    notifications: any[]
    favoriteLoading: boolean
    analysesLoading: boolean
    recentLoading: boolean
    
    // Redux operations
    receiveFavorites: (files: File[]) => void
    updatePageTitle: () => void
    setAllLoading: (loading: boolean) => void
    fetchFavorites: () => void
    fetchRecentFiles: () => void
    fetchRecentAnalyses: () => void
}

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
        const { favoriteFiles, recentFiles, recentAnalyses, notifications,
            favoriteLoading, recentLoading, analysesLoading } = this.props;
        const favoriteOrUnfavorite = (filePath) =>
            this.props.receiveFavorites(favorite(favoriteFiles, filePath, Cloud).filter(file => file.favorited));
        return (
            <React.StrictMode>
                <Card.Group className="mobile-padding">
                    <DashboardFavoriteFiles
                        files={favoriteFiles}
                        isLoading={favoriteLoading}
                        favorite={(filePath) => favoriteOrUnfavorite(filePath)}
                    />
                    <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                    <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                    <DashboardNotifications notifications={notifications} />
                </Card.Group>
            </React.StrictMode>
        );
    }
}

const DashboardFavoriteFiles = ({ files, isLoading, favorite }) => {
    const noFavorites = files.length || isLoading ? "" : (<h3 className="text-center">
        <small>No favorites found.</small>
    </h3>);
    const filesList = files.map((file, i) =>
        (<List.Item key={i} className="itemPadding">
            <List.Content floated="right">
                <Icon name="star" color="blue" onClick={() => favorite(file.path)} />
            </List.Content>
            <ListFileContent path={file.path} type={file.type} link={false} />
        </List.Item>)
    );

    return (
        <Card>
            <Card.Content>
                <Card.Header content="Favorite files" />
                <DefaultLoading loading={isLoading} />
                {noFavorites}
                <List divided size={"large"}>
                    {filesList}
                </List>
            </Card.Content >
        </Card >)
};

const ListFileContent = ({ path, type, link }) =>
    <React.Fragment>
        <List.Content>
            <FileIcon name={type === "FILE" ? iconFromFilePath(path) : "folder"} size={null} link={link} color="grey" />
            <Link to={`files/${type === "FILE" ? getParentPath(path) : path}`}>
                {getFilenameFromPath(path)}
            </Link>
        </List.Content>
    </React.Fragment>


const DashboardRecentFiles = ({ files, isLoading }) => {
    const noRecents = files.length || isLoading ? "" : (<h3 className="text-center">
        <small>No recent files found</small>
    </h3>);
    const filesList = files.sort((a, b) => b.modifiedAt - a.modifiedAt).map((file, i) => (
        <List.Item key={i} className="itemPadding">
            <List.Content floated="right">
                <List.Description>{moment(new Date(file.modifiedAt)).fromNow()}</List.Description>
            </List.Content>
            <ListFileContent path={file.path} type={file.type} link={file.link} />
        </List.Item>
    ));

    return (
        <Card>
            <Card.Content>
                <Card.Header content="Recently used files" />
                <DefaultLoading loading={isLoading} />
                {noRecents}
                <List divided size={"large"}>
                    {filesList}
                </List>
            </Card.Content>
        </Card>);
};

const DashboardAnalyses = ({ analyses, isLoading }) => (
    <Card>
        <Card.Content>
            <Card.Header content="Recent Analyses" />
            <DefaultLoading loading={isLoading} />
            {isLoading || analyses.length ? null :
                (<h3>
                    <small>No analyses found</small>
                </h3>)
            }
            <List divided size={"large"}>
                {analyses.map((analysis, index) =>
                    <List.Item key={index} className="itemPadding">
                        <List.Content floated="right" content={toLowerCaseAndCapitalize(analysis.state)} />
                        <List.Icon name={statusToIconName(analysis.state)} color={statusToColor(analysis.state)} />
                        <List.Content>
                            <List.Header>
                                <Link to={`/analyses/${analysis.jobId}`}>{analysis.appName}</Link>
                            </List.Header>
                        </List.Content>
                    </List.Item>
                )}
            </List>
        </Card.Content>
    </Card>
);

const DashboardNotifications = ({ notifications }) => (
    <Card>
        <Card.Content>
            <Card.Header content="Recent notifications" />
            {notifications.length === 0 ? <h3><small>No notifications</small></h3> : null}
            <List divided>
                {notifications.slice(0, 10).map((n, i) =>
                    <List.Item key={i}>
                        <Notification notification={n} />
                    </List.Item>
                )}
            </List>
        </Card.Content>
    </Card>
);

const Notification = ({ notification }) => {
    switch (notification.type) {
        case "SHARE_REQUEST":
            return (
                <React.Fragment>
                    <List.Content floated="right">
                        <List.Description content={moment(new Date(notification.ts)).fromNow()} />
                    </List.Content>
                    <List.Icon name="share alternate" color="blue" verticalAlign="middle" />
                    <List.Content header="Share Request" description={notification.message} />
                </React.Fragment>
            )
        default: {
            return null;
        }
    }
};

const statusToIconName = (status) => status === "SUCCESS" ? "check" : "x";
const statusToColor = (status) => status === "SUCCESS" ? "green" : "red";

const mapDispatchToProps = (dispatch) => ({
    updatePageTitle: () => dispatch(updatePageTitle("Dashboard")),
    setAllLoading: (loading) => dispatch(setAllLoading(loading)),
    fetchFavorites: () => dispatch(fetchFavorites()),
    fetchRecentFiles: () => dispatch(fetchRecentFiles()),
    fetchRecentAnalyses: () => dispatch(fetchRecentAnalyses()),
    receiveFavorites: (files) => dispatch(receiveFavorites(files))
});

const mapStateToProps = (state) => {
    const {
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        activity,
        favoriteLoading,
        recentLoading,
        analysesLoading,
        activityLoading
    } = state.dashboard;
    return {
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        activity,
        favoriteLoading,
        recentLoading,
        analysesLoading,
        activityLoading,
        notifications: state.notifications.page.items,
        favoriteFilesLength: favoriteFiles.length // Hack to ensure re-rendering
    };
};

export default connect(mapStateToProps, mapDispatchToProps)(Dashboard);
