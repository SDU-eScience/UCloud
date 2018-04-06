import React from "react";
import { BallPulseLoading } from "./LoadingIcon/LoadingIcon";
import { NotificationIcon, getParentPath } from "./../UtilityFunctions";
import { Table, Row } from "react-bootstrap"
import { Link } from "react-router-dom";
import { Cloud } from "../../authentication/SDUCloudObject"
import { sortFilesByTypeAndName, favorite, sortFilesByModified, toLowerCaseAndCapitalize } from "../UtilityFunctions";
import PromiseKeeper from "../PromiseKeeper";
import { updatePageTitle } from "../Actions/Status";
import { setAllLoading, fetchFavorites, fetchRecentAnalyses, fetchRecentFiles, receiveFavorites } from "../Actions/Dashboard";
import { connect } from "react-redux";

class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        const { dispatch, favoriteFiles, recentFiles, recentAnalyses, activity } = this.props;
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
            <section>
                <div className="container" style={{ marginTop: "60px" }} >
                    <Row>
                        <DashboardFavoriteFiles
                            files={favoriteFiles}
                            isLoading={favoriteLoading}
                            favorite={(filePath) => favoriteOrUnfavorite(filePath)}
                        />
                        <DashboardRecentFiles files={recentFiles} isLoading={recentLoading} />
                        <DashboardAnalyses analyses={recentAnalyses} isLoading={analysesLoading} />
                        <DashboardRecentActivity activities={activity} isLoading={activityLoading} />
                    </Row>
                </div>
            </section>
        );
    }
}


const DashboardFavoriteFiles = ({ files, isLoading, favorite }) => {
    const noFavorites = files.length || isLoading ? '' : <h3 className="text-center">
        <small>No favorites found.</small>
    </h3>;
    const filesList = files.map((file) => {
        if (file.type === "DIRECTORY") {
            return (
                <tr key={file.path.path}>
                    <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                    <td onClick={() => favorite(file.path.path)} className="text-center"><em className="ion-star" /></td>
                </tr>)
        } else {
            return (
                <tr key={file.path.path}>
                    <td><Link to={`files/${getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                    <td onClick={() => favorite(file.path.path)} className="text-center"><em className="ion-star" /></td>
                </tr>)
        }
    });

    return (
        <div className="col-md-6 col-lg-4 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Favorite files
                </h5>
                <BallPulseLoading loading={isLoading} />
                {noFavorites}
                <Table responsive className="table table-hover mv-lg">
                    <thead>
                        <tr>
                            <th>File</th>
                            <th className="text-center">Starred</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filesList}
                    </tbody>
                </Table>
            </div>
        </div>)
};

const DashboardRecentFiles = ({ files, isLoading }) => {
    const noRecents = files.length || isLoading ? '' : <h3 className="text-center">
        <small>No recent files found</small>
    </h3>;
    let yesterday = (new Date).getTime() - 1000 * 60 * 60 * 24;
    const filesList = files.map((file) => {
        let modified = new Date(file.modifiedAt);
        let timeString = modified >= yesterday ? modified.toLocaleTimeString() : modified.toLocaleDateString();
        if (file.type === "DIRECTORY") {
            return (
                <tr key={file.path.path}>
                    <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                    <td>{timeString}</td>
                </tr>)
        } else {
            return (
                <tr key={file.path.path}>
                    <td><Link to={`files/${getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                    <td>{timeString}</td>
                </tr>)
        }
    });

    return (
        <div className="col-md-6 col-lg-4 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Recently used files
                </h5>
                <BallPulseLoading loading={isLoading} />
                {noRecents}
                <Table responsive className="table table-hover mv-lg">
                    <thead>
                        <tr>
                            <th>File</th>
                            <th>Modified</th>
                        </tr>
                    </thead>
                    <tbody>
                        {filesList}
                    </tbody>
                </Table>
            </div>
        </div>)
};

const DashboardAnalyses = ({ analyses, isLoading }) => (
    <div className="col-md-6 col-lg-4 align-self-center">
        <div className="card">
            <h5 className="card-heading pb0">
                Recent Analyses
            </h5>
            <BallPulseLoading loading={isLoading} />
            {isLoading || analyses.length ? null :
                (<h3 className="text-center">
                    <small>No analyses found</small>
                </h3>)
            }
            <Table className="table table-hover mv-lg">
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>State</th>
                    </tr>
                </thead>
                <tbody>
                    {analyses.map((analysis, index) =>
                        <tr key={index}>
                            <td><Link to={`/analyses/${analysis.jobId}`}>{analysis.appName}</Link></td>
                            <td>{toLowerCaseAndCapitalize(analysis.state)}</td>
                        </tr>
                    )}
                </tbody>
            </Table>
        </div>
    </div>
);

const DashboardRecentActivity = ({ activity, isLoading }) => (
    <div className="col-md-6 col-lg-4 align-self-center">
        <div className="card">
            <h5 className="card-heading pb0">
                Recent Activity
            </h5>
            <h3 className="text-center">
                <small>No activity found</small>
            </h3>
        </div>
    </div>
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

export default connect(mapStateToProps)(Dashboard)
