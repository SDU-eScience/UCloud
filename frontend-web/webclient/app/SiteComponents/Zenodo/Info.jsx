import React from "react";
import {Jumbotron, Table} from "react-bootstrap";
import pubsub from "pubsub-js";
import {PUBLICATIONS} from "../../MockObjects";
import SectionContainerCard from "../SectionContainerCard";
import {BallPulseLoading} from "../LoadingIcon";

class ZenodoInfo extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            loading: false,
            job: {},
            jobID: window.decodeURIComponent(props.match.params.jobID),
        };
    }

    componentWillMount() {
        this.setState(() => ({
            loading: true,
        }));
        pubsub.publish('setPageTitle', "Info");
        // FIXME: Mimic loading of job from JobID from server
        setTimeout(() => this.setState({
            loading: false,
            job: PUBLICATIONS["In Progress"].find((it) => it.id === this.state.jobID),
        }), 2000);
        // Get info from JobID
    }

    render() {
        if (this.state.loading) {
            return (<SectionContainerCard><BallPulseLoading loading={this.state.loading}/></SectionContainerCard>)
        }
        return (
            <SectionContainerCard>
                <Jumbotron>
                    <h3>{this.state.job.id}</h3>
                </Jumbotron>
                <FilesList files={null}/>
            </SectionContainerCard>
        );
    }
}

function FilesList(props) {
    if (props.files === null) {
        return null
    }
    const filesList = props.files.map((file) =>
        <tr>
            <td>{file.name}</td>
            <td>{file.status}</td>
        </tr>
    );
    return (
        <Table>
            <thead>
            <tr>
                <th>File name</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody>
            {filesList}
            </tbody>
        </Table>
    );
}

export default ZenodoInfo;