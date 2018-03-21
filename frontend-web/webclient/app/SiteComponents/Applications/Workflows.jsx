import React from 'react';
import {Table, Button} from 'react-bootstrap';
import {BallPulseLoading} from '../LoadingIcon/LoadingIcon'
import pubsub from "pubsub-js";
import {Card} from "../Cards";
import {Link} from "react-router-dom";
import PromiseKeeper from "../../PromiseKeeper";

class Workflows extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            promises: new PromiseKeeper(),
            loading: false,
            workflows: [],
            lastSorting: {
                name: "name",
                asc: true,
            },
        }
    }

    getSortingIcon(name) {
        if (this.state.lastSorting.name === name) {
            return this.state.lastSorting.asc ? "ion-chevron-down" : "ion-chevron-up";
        }
        return "";
    }

    componentDidMount() {
        this.getWorkflows();
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    getWorkflows() {
        this.setState({loading: true});
        // TODO ADD KEEPER OF PROMISES HERE
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
                                <WorkflowTable workflows={this.state.workflows}/>
                            </div>
                        </Card>
                    </div>
                    <div className="col-lg-2 visible-lg">
                        <div>
                            <Link to="/generateworkflow"><Button
                                className="btn btn-primary ripple btn-block ion-android-upload">Generate workflow
                            </Button></Link>
                            <br/>
                            <hr/>
                        </div>
                    </div>
                </div>
            </section>)
    }
}

const WorkflowTable = (props) => {
    if (!props.workflows.length) {
        return (
            <h3 className="text-center">
                <small>No workflows found.</small>
            </h3>)
    }
    return (
        <Table className="table table-striped table-hover mv-lg">
            <thead>
            <tr>
                <th>Name</th>
                <th>Applications</th>
            </tr>
            </thead>
            <WorkflowsList workflows={props.workflows}/>
        </Table>)
};

const WorkflowsList = (props) => {
    let workflowsList = props.workflows.map(workflow =>
        <tr className="gradeA row-settings">
            <td>{workflow.name}</td>
            <td><ApplicationList applications={workflow.applications}/></td>
        </tr>
    );
    return (
        <tbody>
        {workflowsList}
        </tbody>)
};

const ApplicationList = (props) => {
    let applicationsList = props.applications.map(app =>
        <div>
            {app.info.name}<br/>
        </div>
    );
    return (
        <td>
            {applicationsList}
        </td>)
};

export default Workflows