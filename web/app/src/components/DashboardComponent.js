import $ from 'jquery'
import React from 'react'
import LoadingIcon from './LoadingIconComponent'

class DashboardComponent extends React.Component {
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
    }
  }

  componentDidMount() {
    this.getFavouriteFiles();
    this.getMostRecentFiles();
    this.getRecentAnalyses();
    this.getRecentActivity();
  }

  getFavouriteFiles() {
    this.setState({
      favouriteLoading: true,
    });
    $.getJSON("/api/getFavouritesSubset").then( (files) => {
      files.sort((a, b) => {
        if (a.type === "DIRECTORY" && b.type !== "DIRECTORY")
          return -1;
        else if (b.type === "DIRECTORY" && a.type !== "DIRECTORY")
          return 1;
        else {
          return a.path.name.localeCompare(b.path.name);
        }
      });
      this.setState({
        favouriteFiles: files,
        favouriteLoading: false,
      });
    });
  }

  getMostRecentFiles() {
    this.setState({
      recentLoading: true
    });
    $.getJSON("/api/getMostRecentFiles").then( (files) => {
      files.sort( (a, b) => {
        return b.modifiedAt - a.modifiedAt;
      });
      this.setState({
        recentLoading: false,
        recentFiles: files
      });
    });
  }

  getRecentAnalyses() {
    this.setState({
      analysesLoading: true
    });
    $.getJSON("/api/getRecentWorkflowStatus").then((analyses) => {
      analyses.sort();
      this.setState({
        analysesLoading: false,
        recentAnalyses: analyses
      });
    });
  }

  getRecentActivity() {
    this.setState({
      activityLoading: true
    });
    $.getJSON("/api/getRecentActivity").then( (activity) => {
      activity.sort();
      this.setState({
        activity: activity,
        activityLoading: false,
      })
    });
  }

  render() {
    return(
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
  const noFavourites = props.files.length ? '' : <h3 className="text-center"><small>No favourites found.</small></h3>;
  const files = props.files;
  const filesList = files.map( (file) =>
    <tr key={file.path.uri}>
      <td><a href="#">{ file.path.name }</a></td>
      <td><em className="ion-star"></em></td>
    </tr>
  );

  return (
    <div className="col-sm-3">
      <div className="card">
        <h5 className="card-heading pb0">
          Favourite files
        </h5>
        <LoadingIcon loading={props.isLoading}/>
        {noFavourites}
        <table className="table-datatable table table-hover mv-lg">
          <thead>
          <tr>
            <th>File</th>
            <th>Starred</th>
          </tr>
          </thead>
          <tbody>
            {filesList}
          </tbody>
        </table>
      </div>
    </div>)
}

function DashboardRecentFiles(props) {
  const noRecents = props.files.length ? '' : <h3 className="text-center"><small>No recent files found</small></h3>;
  const files = props.files;
  const filesList = files.map( (file) =>
    <tr key={file.path.uri}>
      <td><a href="#">{ file.path.name }</a></td>
      <td>{ new Date(file.modifiedAt).toLocaleString() }</td>
    </tr>
  );

  return (
    <div className="col-sm-3">
      <div className="card">
        <h5 className="card-heading pb0">
          Recently used files
        </h5>
        <LoadingIcon loading={props.isLoading}/>
        {noRecents}
        <table className="table-datatable table table-hover mv-lg">
          <thead>
          <tr>
            <th>File</th>
            <th>Modified</th>
          </tr>
          </thead>
          <tbody>
            {filesList}
          </tbody>
        </table>
      </div>
    </div>)

}

function DashboardAnalyses(props) {
  const noAnalyses = props.analyses.length ? '' : <h3 className="text-center"><small>No analyses found</small></h3>;
  const analyses = props.analyses;
  const analysesList = analyses.map( (analysis) =>
    <tr key={analysis.name}>
      <td><a href="#">{ analysis.name }</a></td>
      <td>{ analysis.status }</td>
    </tr>
  );

  return (
    <div className="col-sm-3">
      <div className="card">
        <h5 className="card-heading pb0">
          Recent Analyses
        </h5>
        <LoadingIcon loading={props.isLoading}/>
        {noAnalyses}
        <table className="table-datatable table table-hover mv-lg">
          <thead>
          <tr>
            <th>Name</th>
            <th>Status</th>
          </tr>
          </thead>
          <tbody>
          {analysesList}
          </tbody>
        </table>
      </div>
    </div>)
}

function DashboardRecentActivity(props) {
  const noActivity = props.activities.length ? '' : <h3 className="text-center"><small>No activity found</small></h3>;
  const activities = props.activities;
  let i = 0;
  const activityList = activities.map( (activity) =>
    <tr key={i++} className="msg-display clickable">
      <td className="wd-xxs">
        <NotificationIcon type={activity.type}/>
      </td>
      <th className="mda-list-item-text mda-2-line">
        <small>{ activity.message }</small>
        <br/>
          <small className="text-muted">{ new Date(activity.timestamp).toLocaleString() }</small>
      </th>
      <td className="text">{ activity.body }</td>
    </tr>
  );

  return(
    <div className="col-sm-3 ">
      <div className="card">
        <h5 className="card-heading pb0">
          Activity
        </h5>
        <loading-icon loading={props.isLoading}/>
        {noActivity}
        <div>
          <table className="table-datatable table table-hover mv-lg">
            <tbody>
            {activityList}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function NotificationIcon(props) {
  if (props.type === "Complete") {
    return (<div className="initial32 bg-green-500">✓</div>)
  } else if (props.type === "In Progress") {
    return (<div className="initial32 bg-blue-500">...</div>)
  } else if (props.type === "Pending") {
    return (<div className="initial32 bg-blue-500"/>)
  } else if (props.type === "Failed") {
    return (<div className="initial32 bg-red-500">&times;</div>)
  } else {
    return (<div>Unknown type</div>)
  }
}

export { DashboardComponent }
