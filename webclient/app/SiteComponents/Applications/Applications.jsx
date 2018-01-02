import React from 'react';
import LoadingIcon from '../LoadingIcon';
import {Applications as apps} from "../../MockObjects";
import {Link, BrowserRouter} from 'react-router'

import {Table} from 'react-bootstrap';
import {Card, CardHeading} from "../Cards";

class Applications extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            applications: apps
        }
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <LoadingIcon loading={!this.state.applications.length}/>
                        <Card xs={6} sm={12}>
                            <Table className="table-datatable table table-striped table-hover mv-lg">
                                <thead>
                                <tr>
                                    <th/>
                                    <th>Application Name</th>
                                    <th>Author</th>
                                    <th>Rating</th>
                                    <th className="sort-numeric">Version</th>
                                    <th/>
                                </tr>
                                </thead>
                                <ApplicationsList applications={this.state.applications}/>
                            </Table>
                        </Card>
                    </div>
                    <div className="col-lg-2 visible-lg">
                        <div>
                            <button className="btn btn-primary ripple btn-block ion-android-upload"
                                    onClick="newApplication()"> Upload Application
                            </button>
                            <br/>
                            <hr/>
                        </div>
                    </div>
                </div>
            </section>)
    }
}

function ApplicationsList(props) {
    console.log("Applications");
    let applications = props.applications.slice();
    let i = 0;
    let applicationsList = applications.map((app) =>
        <SingleApplication key={i++} app={app}/>
    );
    return (
        <tbody>
        {applicationsList}
        </tbody>)
}

function SingleApplication(props) {
    return (
        <tr className="gradeA row-settings">
            <PrivateIcon isPrivate={props.app.info.isPrivate}/>
            <td title={props.app.info.description}>{props.app.info.name}</td>
            <td title={props.app.info.description}>{props.app.info.author}</td>
            <td title="Rating for the application">{props.app.info.rating} / 5</td>
            <td title={props.app.info.description}>{props.app.info.version}</td>
            <th>

                <Link to={'/runApp/' + props.app.info.name + '/' + props.app.info.version}>
                    <button className="btn btn-info">Run</button>
                </Link>
            </th>
        </tr>
    )
}

function PrivateIcon(props) {
    console.log("Is private");
    if (props.isPrivate) {
        return (
            <td title="The app is private and can only be seen by the creator and people it was shared with">
                <em className="ion-locked"/></td>
        )
    } else {
        return (<td title="The application is openly available for everyone"><em className="ion-unlocked"/></td>)
    }
}

export default Applications