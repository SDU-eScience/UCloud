import React from 'react';
import { Table} from 'react-bootstrap';
import { BallPulseLoading } from '../LoadingIcon'
import pubsub from "pubsub-js";
import { Card } from "../Cards";
import { Cloud } from "../../../authentication/SDUCloudObject"

class Workflows extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            workflows: [],
        }
    }

    componentDidMount() {
        this.getWorkflows();
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    getWorkflows() {
        this.setState({ loading: true });
        let workflows = [];//Cloud.get("/getWorkflows").then(workflows => {
            workflows.sort((a, b) => {
                return a.name.localeCompare(b.name);
            });
            this.setState({
                loading: false,
                workflows: workflows,
            });
        //});
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <BallPulseLoading loading={this.state.loading}/>
                        <Card xs={6} sm={12}>
                            <div className="card-body">
                                <Table className="table table-striped table-hover mv-lg">
                                    <thead>
                                    <tr>
                                        <th>Name</th>
                                        <th>Applications</th>
                                    </tr>
                                    </thead>
                                    <WorkflowsList workflows={this.state.workflows}/>
                                </Table>
                            </div>
                        </Card>
                    </div>
                    <div className="col-lg-2 visible-lg">
                        <div>
                            <button className="btn btn-primary ripple btn-block ion-android-upload"> Generate workflow
                            </button>
                            <br/>
                            <hr/>
                        </div>
                    </div>
                </div>
            </section>)
    }
}

function WorkflowsList(props) {
    let workflowsList = props.workflows.map( workflow =>
        <tr className="gradeA row-settings">
            <td>{ workflow.name }</td>
            <ApplicationList applications={workflow.applications}/>
        </tr>
    );
    return (
        <tbody>
            {workflowsList}
        </tbody>)
}

function ApplicationList(props) {
    let applicationsList = props.applications.map( app =>
        <div>
            { app.info.name }<br/>
        </div>
    );
    return (
    <td>
        {applicationsList}
    </td>)
}

export default Workflows