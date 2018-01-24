import React from 'react';
import {ActivityCardExample1} from "../../MockObjects";
import LoadingIcon from "../LoadingIcon";
import {DateRangePicker, T} from "../DateAndTimePickers";
import {Table, Button} from "react-bootstrap";

class UserAuditing extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            userId: props.params.id,
            activityCards: [],
        }
    }

    componentDidMount() {
        // Don't do here?
    }

    render() {
        return (
            <section>
                <select>
                    <option value="any" selected>Any</option>
                    <option value="hpcjobs">HPC Jobs</option>
                    <option value="files">Files</option>
                </select>
                <Button>Search</Button>
                <T/>
                <ActivityCard/>
                <ActivityEvents/>
            </section>
        )
    }
}

function ActivityCard(props) {
    return (null);
}

function ActivityEvents(props) {
    return (null);
}

export default UserAuditing;