import React from 'react'
import {BallPulseLoading} from './LoadingIcon'
import {NotificationIcon, getParentPath} from "./../UtilityFunctions";
import {Table, Row} from 'react-bootstrap'
import pubsub from "pubsub-js";
import {Link} from "react-router-dom";
import {Cloud} from '../../authentication/SDUCloudObject'
import {sortFilesByTypeAndName, favorite, sortFilesByModified, toLowerCaseAndCapitalize} from "../UtilityFunctions";
import PromiseKeeper from "../PromiseKeeper";


class Dashboard extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
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
        this.state.promises.makeCancelable(Cloud.get(`/files?path=${Cloud.homeFolder}`)).promise.then(req => {
            let actualFavorites = req.response.filter(file => file.favorited);
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
        this.state.promises.makeCancelable(Cloud.get(`/files?path=${Cloud.homeFolder}`)).promise.then(req => {
            let recentSubset = sortFilesByModified(req.response.slice(0, 10));
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
        this.state.promises.makeCancelable(Cloud.get("/hpc/jobs")).promise.then(req => {
            this.setState(() => ({
                analysesLoading: false,
                recentAnalyses: req.response.items.slice(0, 10),
            }));
        });
    }

    getRecentActivity() {
        this.setState(() => ({
            activityLoading: false
        }));
    }

    favoriteOrUnfavorite(fileUri) {
        this.setState(() => ({
            favoriteFiles: favorite(this.state.favoriteFiles, fileUri, Cloud).filter(file => file.favorited),
        }));
    }

    componentWillUnmount() {
        this.state.promises.cancelPromises();
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <Row>
                        <DashboardFavoriteFiles files={this.state.favoriteFiles} isLoading={this.state.favoriteLoading}
                                                favorite={this.favoriteOrUnfavorite}/>
                        <DashboardRecentFiles files={this.state.recentFiles} isLoading={this.state.recentLoading}/>
                        <DashboardAnalyses analyses={this.state.recentAnalyses} isLoading={this.state.analysesLoading}/>
                        <DashboardRecentActivity activities={this.state.activity} isLoading={this.state.activityLoading}/>
                    </Row>
                </div>
            </section>
        )
    }
}

const DashboardFavoriteFiles = (props) => {
    const noFavorites = props.files.length || props.isLoading ? '' : <h3 className="text-center">
        <small>No favorites found.</small>
    </h3>;
    const filesList = props.files.map((file) => {
            if (file.type === "DIRECTORY") {
                return (
                    <tr key={file.path.path}>
                        <td><Link to={`files/${file.path.path}`}>{file.path.name}</Link></td>
                        <td onClick={() => props.favorite(file.path.path)}><em className="ion-star text-center"/></td>
                    </tr>)
            } else {
                return (
                    <tr key={file.path.path}>
                        <td><Link to={`files/${getParentPath(file.path.path)}`}>{file.path.name}</Link></td>
                        <td onClick={() => props.favorite(file.path.path)} className="text-center"><em
                            className="ion-star"/></td>
                    </tr>)
            }
        }
    );

    return (
        <div className="col-md-6 col-lg-4 align-self-center">
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
};

const DashboardRecentFiles = (props) => {
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
};

const DashboardAnalyses = ({analyses, isLoading}) => (
    <div className="col-md-6 col-lg-4 align-self-center">
        <div className="card">
            <h5 className="card-heading pb0">
                Recent Analyses
            </h5>
            <BallPulseLoading loading={isLoading}/>
            {isLoading || analyses.length ? null :
                (<h3 className="text-center">
                    <small>No analyses found</small>
                </h3>)
            }
            <Table className="table table-hover mv-lg">
                <thead>
                <tr>
                    <th>Name</th>
                    <th>Status</th>
                </tr>
                </thead>
                <tbody>
                {analyses.map((analysis, index) =>
                    <tr key={index}>
                        <td>{analysis.appName}</td>
                        <td>{toLowerCaseAndCapitalize(analysis.status)}</td>
                    </tr>
                )}
                </tbody>
            </Table>
        </div>
    </div>
);

const DashboardRecentActivity = (props) => (
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

export default Dashboard
