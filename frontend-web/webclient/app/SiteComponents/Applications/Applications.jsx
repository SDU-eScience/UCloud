import React from 'react';
import {BallPulseLoading} from '../LoadingIcon';
import {Link} from 'react-router';

import {Table} from 'react-bootstrap';
import {Card} from "../Cards";
import pubsub from "pubsub-js";
import {Cloud} from "../../../authentication/SDUCloudObject";

class Applications extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            applications: [],
        }
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        this.getApplications();
    }

    getApplications() {
        this.setState({loading: true});
        Cloud.get("/hpc/apps").then(apps => {
            this.setState({
                applications: apps.sort((a,b) => {return a.info.name.localeCompare(b.info.name)}),
                loading: false
            });
        });
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <BallPulseLoading loading={!this.state.applications.length}/>
                        <Card xs={6} sm={12}>
                            <div className="card-body">
                                <Table className="table table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th>Visibility</th>
                                        <th>Application Name</th>
                                        <th>Version</th>
                                        <th/>
                                    </tr>
                                    </thead>
                                    <ApplicationsList applications={this.state.applications}/>
                                </Table>
                            </div>
                        </Card>
                    </div>
                    <div className="col-lg-2 visible-lg">
                        <div>
                            <button className="btn btn-primary ripple btn-block"><span
                                className="ion-android-upload pull-left"/> Upload Application
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
    let applications = props.applications.slice();
    let i = 0;
    let applicationsList = applications.map(app =>
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
            <td title={props.app.info.description}>{props.app.info.version}</td>
            <th>
                <Link to={'/applications/' + props.app.info.name + '/' + props.app.info.version}>
                    <button className="btn btn-info">Run</button>
                </Link>
            </th>
        </tr>
    )
}

function PrivateIcon(props) {
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