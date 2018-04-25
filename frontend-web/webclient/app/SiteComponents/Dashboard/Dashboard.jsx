import React from "react";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { NotificationIcon, getParentPath } from "../../UtilityFunctions";
import { Link } from "react-router-dom";
import { Cloud } from "../../../authentication/SDUCloudObject"
import { sortFilesByTypeAndName, favorite, sortFilesByModified, toLowerCaseAndCapitalize, getFilenameFromPath } from "../../UtilityFunctions";
import { updatePageTitle } from "../../Actions/Status";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites } from "../../Actions/Dashboard";
import { connect } from "react-redux";
import "./Dashboard.scss";
import { Card, Table, List, Tab, Container, Icon } from "semantic-ui-react";

class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        const { dispatch, favoriteFiles, recentFiles, recentAnalyses, activity } = this.props;
        dispatch(updatePageTitle("Dashboard"))
        if (!favoriteFiles.length && !recentFiles.length && !recentAnalyses.length && !activity.length) {
            dispatch(updatePageTitle("Dashboard"));
            dispatch(setAllLoading(true));
            dispatch(fetchFavorites());
            dispatch(fetchRecentFiles());
            dispatch(fetchRecentAnalyses());
            //dispatch(fetchRecentActivity());
        }
    }


    render() {
        const { favoriteFiles, recentFiles, recentAnalyses, activity, dispatch,
            favoriteLoading, recentLoading, analysesLoading, activityLoading } = this.props;
        const favoriteOrUnfavorite = (filePath) =>
            dispatch(receiveFavorites(favorite(favoriteFiles, filePath, Cloud).filter(file => file.favorited)));
        return (
            <React.StrictMode>
                <section>
                    <Container className="containerMargin">
                        <Card.Group>
                            <DashboardFavoriteFiles
                                files={favoriteFiles}
                                isLoading={favoriteLoading}
                                favorite={(filePath) => favoriteOrUnfavorite(filePath)}
                            />
                            <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                            <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                            <DashboardRecentActivity activities={activity} isLoading={activityLoading} />
                        </Card.Group>
                    </Container>
                </section>
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
                <Icon name="star" onClick={() => favorite(file.path)} />
            </List.Content>
            <ListFileContent path={file.path} type={file.type} />
        </List.Item>)
    );

    return (
        <Card>
            <Card.Content>
                <Card.Header>
                    Favorite files
                </Card.Header>
                <BallPulseLoading loading={isLoading} />
                {noFavorites}
                <List divided size={"large"}>
                    {filesList}
                </List>
            </Card.Content >
        </Card >)
};

const ListFileContent = ({ path, type }) =>
    <React.Fragment>
        <List.Icon name={type === "FILE" ? "file" : "folder"} />
        <List.Content>
            <List.Header>
                <Link to={`files/${type === "FILE" ? getParentPath(path) : path}`}>
                    {getFilenameFromPath(path)}
                </Link>
            </List.Header>
        </List.Content>
    </React.Fragment>


const DashboardRecentFiles = ({ files, isLoading }) => {
    const noRecents = files.length || isLoading ? "" : <h3 className="text-center">
        <small>No recent files found</small>
    </h3>;
    let yesterday = (new Date).getTime() - 1000 * 60 * 60 * 24;
    const filesList = files.map((file, i) => {
        let modified = new Date(file.modifiedAt);
        let timeString = modified >= yesterday ? toEUTimeString(modified.toLocaleTimeString()) : modified.toLocaleDateString();
        return (
            <List.Item key={i} className="itemPadding">
                <List.Content floated="right">
                    <List.Description>{timeString}</List.Description>
                </List.Content>
                <ListFileContent path={file.path} type={file.type} />
            </List.Item>
        );
    });

    return (
        <Card>
            <Card.Content>
                <Card.Header>
                    Recently used files
                </Card.Header>
                <BallPulseLoading loading={isLoading} />
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
            <Card.Header>
                Recent Analyses
            </Card.Header>
            <BallPulseLoading loading={isLoading} />
            {isLoading || analyses.length ? null :
                (<h3 className="text-center">
                    <small>No analyses found</small>
                </h3>)
            }
            <List divided size={"large"}>
                {analyses.map((analysis, index) =>
                    <List.Item key={index} className="itemPadding">
                        <List.Content floated="right">
                            {toLowerCaseAndCapitalize(analysis.state)}
                        </List.Content>
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

const statusToIconName = (status) => status === "SUCCESS" ? "check" : "x";
const statusToColor = (status) => status === "SUCCESS" ? "green" : "red";

const DashboardRecentActivity = ({ activity, isLoading }) => (
    <Card>
        <Card.Content>
            <Card.Header>
                Recent Activity
                </Card.Header>
            <h3 className="text-center">
                <small>No activity found</small>
            </h3>
        </Card.Content>
    </Card>
);

const mapStateToProps = (state) => {
    const {
        favoriteFiles,
        recentFiles,
        recentAnalyses,
        activity,
        favoriteLoading,
        recentLoading,
        analysesLoading,
        activityLoading,
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
        favoriteFilesLength: favoriteFiles.length // Hack to ensure rerendering
    };
}

const toEUTimeString = (timeString) => timeString.split(".").join(":");

export default connect(mapStateToProps)(Dashboard)
