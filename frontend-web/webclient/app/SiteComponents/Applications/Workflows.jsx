import React from 'react';
import { Table} from 'react-bootstrap';
import LoadingIcon from '../LoadingIcon'
import pubsub from "pubsub-js";
import { Card } from "../Cards";

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
        $.getJSON("/api/getWorkflows").then((data) => {
            data.sort((a, b) => {
                return a.name.localeCompare(b.name);
            });
            this.setState({
                loading: false,
                workflows: data,
            });
        });
    }

    render() {
        return (
            <section>
                <div className="container-fluid">
                    <div className="col-lg-10">
                        <LoadingIcon loading={this.state.loading}/>
                        <Card xs={6} sm={12}>
                            <div className="card-body">
                                <Table className="table-datatable table table-striped table-hover mv-lg">
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
    let workflows = props.workflows.slice();
    let workflowsList = workflows.map( workflow =>
        <tr className="gradeA row-settings">
            <td>{ workflow.name }}</td>
            <ApplicationList applications={workflow.applications}/>
        </tr>
    );
    return (
        <tbody>
            {workflowsList}
        </tbody>)
}

function ApplicationList(props) {
    let applications = props.applications.slice();
    let applicationsList = applications.map( app =>
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