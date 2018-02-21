import $ from 'jquery'
import React from 'react'
import {BallPulseLoading} from './LoadingIcon'
import {NotificationIcon, getParentPath} from "./../UtilityFunctions";
import {Table} from 'react-bootstrap'
import pubsub from "pubsub-js";
import {Link} from "react-router-dom";
import {Cloud} from '../../authentication/SDUCloudObject'
import {sortFilesByTypeAndName, favorite, sortFilesByModified, toLowerCaseAndCapitalize} from "../UtilityFunctions";


class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            favoriteFiles: [],
            favoriteLoading: false,
            recentFiles: [],
            recentLoading: false,
            recentAnalyses: [],
            analysesLoading: false,
            activity: [],
            activityLoading: false,
        };
        this.getFavoriteFiles = this.getFavoriteFiles.bind(this);
        this.getMostRecentFiles = this.getMostRecentFiles.bind(this);
        this.getRecentActivity = this.getRecentActivity.bind(this);
        this.getRecentAnalyses = this.getRecentAnalyses.bind(this);
        this.favoriteOrUnfavorite = this.favoriteOrUnfavorite.bind(this);
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getFavoriteFiles();
        this.getMostRecentFiles();
        this.getRecentAnalyses();
        /*this.getRecentActivity();*/
    }

    getFavoriteFiles() {
        this.setState(() => ({
            favoriteLoading: true,
        }));
        Cloud.get(`/files?path=${Cloud.homeFolder}`).then(favorites => {
            let actualFavorites = favorites.filter(file => file.favorited);
            let subsetFavorites = sortFilesByTypeAndName(actualFavorites.slice(0, 10));
            this.setState(() => ({
                favoriteFiles: subsetFavorites,
                favoriteLoading: false,
            }));
        });
    }

    getMostRecentFiles() {
        this.setState(() => ({
            recentLoading: true
        }));
        Cloud.get(`/files?path=${Cloud.homeFolder}`).then(recent => {
            let recentSubset = sortFilesByModified(recent.slice(0, 10));
            this.setState(() => ({
                recentFiles: recentSubset,
                recentLoading: false,
            }));
        });
    }

    getRecentAnalyses() {
        this.setState(() => ({
            analysesLoading: true
        }));
        Cloud.get("/hpc/jobs").then(analyses => {
            this.setState(() => ({
                analysesLoading: false,
                recentAnalyses: analyses.slice(0, 10),
            }));
        });
    }

    getRecentActivity() {
        this.setState(() => ({
            activityLoading: true
        }));
        $.getJSON("/api/getRecentActivity").then((activity) => {
            activity.sort((a, b) => {
                return a.ts - b.ts;
            });
            this.setState(() => ({
                activity: activity,
                activityLoading: false,
            }));
        });
    }

    favoriteOrUnfavorite(fileUri) {
        this.setState(() => ({
            favoriteFiles: favorite(this.state.favoriteFiles, fileUri).filter(file => file.favorited),
        }));
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <DashboardFavoriteFiles files={this.state.favoriteFiles} isLoading={this.state.favoriteLoading}
                                             favorite={this.favoriteOrUnfavorite}/>
                    <DashboardRecentFiles files={this.state.recentFiles} isLoading={this.state.recentLoading}/>
                    <DashboardAnalyses analyses={this.state.recentAnalyses} isLoading={this.state.analysesLoading}/>
                    <DashboardRecentActivity activities={this.state.activity} isLoading={this.state.activityLoading}/>
                </div>
            </section>
        )
    }
}

function DashboardFavoriteFiles(props) {
    const noFavorites = props.files.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No favorites found.</small>
    </h3>;
    const filesList = props.files.map((file) => {
            if (file.type === "DIRECTORY") {
                return (
                    <tr key={file.path.uri}>
                        <td onMouseEnter={console.log("HI")}><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                        <td onClick={() => props.favorite(file.path.uri)}><em className="ion-star text-center"/></td>
                    </tr>)
            } else {
                return (
                    <tr key={file.path.uri}>
                        <td><Link to={`files/${getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                        <td onClick={() => props.favorite(file.path.uri)} className="text-center"><em className="ion-star"/></td>
                    </tr>)
            }
        }
    );

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Favorite files
                </h5>
                <BallPulseLoading loading={props.isLoading}/>
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
}

function DashboardRecentFiles(props) {
    const noRecents = props.files.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No recent files found</small>
    </h3>;
    const files = props.files;
    let yesterday = (new Date).getTime() - 1000 * 60 * 60 * 24;
    const filesList = files.map((file) => {
        let modified = new Date(file.modifiedAt);
        let timeString = modified >= yesterday ? modified.toLocaleTimeString() : modified.toLocaleDateString();
        if (file.type === "DIRECTORY") {
            return (
                <tr key={file.path.uri}>
                    <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                    <td>{timeString}</td>
                </tr>)
        } else {
            return (
                <tr key={file.path.uri}>
                    <td><Link to={`files/${getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                    <td>{timeString}</td>
                </tr>)
        }
    });

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Recently used files
                </h5>
                <BallPulseLoading loading={props.isLoading}/>
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

}

function DashboardAnalyses(props) {
    const noAnalyses = props.analyses.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No analyses found</small>
    </h3>;
    const analyses = props.analyses;
    let i = 0;
    const analysesList = analyses.map((analysis) =>
        <tr key={i++}>
            <td>{analysis.appName}</td>
            <td>{toLowerCaseAndCapitalize(analysis.status)}</td>
        </tr>
    );

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Recent Analyses
                </h5>
                <BallPulseLoading loading={props.isLoading}/>
                {noAnalyses}
                <Table className="table table-hover mv-lg">
                    <thead>
                    <tr>
                        <th>Name</th>
                        <th>Status</th>
                    </tr>
                    </thead>
                    <tbody>
                    {analysesList}
                    </tbody>
                </Table>
            </div>
        </div>)
}

function DashboardRecentActivity(props) {
    const noActivity = props.activities.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No activity found</small>
    </h3>;
    const activities = props.activities;
    let i = 0;
    const activityList = activities.map((activity) =>
        <tr key={i++} className="msg-display clickable">
            <td className="wd-xxs">
                <NotificationIcon type={activity.type}/>
            </td>
            <th className="mda-list-item-text mda-2-line">
                <small>{activity.message}</small>
                <br/>
                <small className="text-muted">{new Date(activity.timestamp).toLocaleString()}</small>
            </th>
            <td className="text">{activity.body}</td>
        </tr>
    );

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Activity
                </h5>
                <BallPulseLoading loading={props.isLoading}/>
                {noActivity}
                <div>
                    <Table className="table table-hover mv-lg">
                        <tbody>
                        {activityList}
                        </tbody>
                    </Table>
                </div>
            </div>
        </div>
    );
}

export default Dashboard
