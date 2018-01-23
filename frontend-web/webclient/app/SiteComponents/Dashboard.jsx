import $ from 'jquery'
import React from 'react'
import LoadingIcon from './LoadingIcon'
import {NotificationIcon} from "./../UtilityFunctions";
import {Table} from 'react-bootstrap'
import pubsub from "pubsub-js";
import {Link} from 'react-router';
import {Cloud} from '../../authentication/SDUCloudObject'
import {sortFilesByTypeAndName} from "../UtilityFunctions";


class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            favouriteFiles: [],
            favouriteLoading: false,
            recentFiles: [],
            recentLoading: false,
            recentAnalyses: [],
            analysesLoading: false,
            activity: [],
            activityLoading: false,
        };
        this.getFavouriteFiles = this.getFavouriteFiles.bind(this);
        this.getMostRecentFiles = this.getMostRecentFiles.bind(this);
        this.getRecentActivity = this.getRecentActivity.bind(this);
        this.getRecentAnalyses = this.getRecentAnalyses.bind(this);
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getFavouriteFiles();
        this.getMostRecentFiles();
        this.getRecentAnalyses();
        /*this.getRecentActivity();*/
    }

    getFavouriteFiles() {
        this.setState(() => ({
            favouriteLoading: true,
        }));
        Cloud.get(`/files?path=/home/${Cloud.username}/`).then(favourites => {
            let subsetFavorites = sortFilesByTypeAndName(favourites.slice(0, 10));
            this.setState(() => ({
                favouriteFiles: subsetFavorites,
                favouriteLoading: false,
            }));
        });
    }

    getMostRecentFiles() {
        this.setState(() => ({
            recentLoading: true
        }));
        Cloud.get(`/files?path=/home/${Cloud.username}/`).then(recent => {
            let recentSubset = recent.slice(0, 10);
            recentSubset.sort((a, b) => {
                return b.modifiedAt - a.modifiedAt;
            });
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
            analyses.forEach(analysis => {
                analysis.name = "Hello app";
            });
            analyses.sort((a, b) => {
                return a.name.localeCompare(b.name);
            });
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
            activity.sort();
            this.setState(() => ({
                activity: activity,
                activityLoading: false,
            }));
        });
    }

    static getParentPath(path) {
        let splitPath = path.split("/");
        let parentPath = "";
        for (let i = 0; i < splitPath.length - 1; i++) {
            parentPath += splitPath[i] + "/";
        }
        return parentPath;
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <DashboardFavouriteFiles files={this.state.favouriteFiles} isLoading={this.state.favouriteLoading}/>
                    <DashboardRecentFiles files={this.state.recentFiles} isLoading={this.state.recentLoading}/>
                    <DashboardAnalyses analyses={this.state.recentAnalyses} isLoading={this.state.analysesLoading}/>
                    <DashboardRecentActivity activities={this.state.activity} isLoading={this.state.activityLoading}/>
                </div>
            </section>
        )
    }
}

function DashboardFavouriteFiles(props) {
    const noFavourites = props.files.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No favourites found.</small>
    </h3>;
    const filesList = props.files.map((file) => {
            if (file.type === "DIRECTORY") {
                return (
                    <tr key={file.path.uri}>
                        <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                        <td><em className="ion-star"/></td>
                    </tr>)
            } else {
                return (
                    <tr key={file.path.uri}>
                        <td><Link to={`files/${Dashboard.getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                        <td><em className="ion-star"/></td>
                    </tr>)
            }
        }
    );

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Favourite files
                </h5>
                <LoadingIcon loading={props.isLoading}/>
                {noFavourites}
                <Table responsive className="table-datatable table table-hover mv-lg">
                    <thead>
                    <tr>
                        <th>File</th>
                        <th>Starred</th>
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
    const filesList = files.map((file) => {
        if (file.type === "DIRECTORY") {
            return (
                <tr key={file.path.uri}>
                    <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                    <td>{new Date(file.modifiedAt).toLocaleString()}</td>
                </tr>)
        } else {
            return (
                <tr key={file.path.uri}>
                    <td><Link to={`files/${Dashboard.getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                    <td>{new Date(file.modifiedAt).toLocaleString()}</td>
                </tr>)
        }
    });

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Recently used files
                </h5>
                <LoadingIcon loading={props.isLoading}/>
                {noRecents}
                <Table className="table-datatable table table-hover mv-lg">
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
            <td><a href="#">{analysis.name}</a></td>
            <td>{analysis.status}</td>
        </tr>
    );

    return (
        <div className="col-sm-3 align-self-center">
            <div className="card">
                <h5 className="card-heading pb0">
                    Recent Analyses
                </h5>
                <LoadingIcon loading={props.isLoading}/>
                {noAnalyses}
                <Table className="table-datatable table table-hover mv-lg">
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
                <loading-icon loading={props.isLoading}/>
                {noActivity}
                <div>
                    <Table className="table-datatable table table-hover mv-lg">
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
