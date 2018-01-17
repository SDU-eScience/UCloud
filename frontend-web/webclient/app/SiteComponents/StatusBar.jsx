import React from 'react';
import { Link } from 'react-router';
import { Statuses} from "../MockObjects";

class StatusBar extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            status: Statuses[0],
        };
    }

    componentDidMount() {
        this.setState({
            status: Statuses[0],
        });
    }

    statusToButton() {
        if (this.state.status.level === 'NO ISSUES') {
            return 'bg-green-500';
        } else if (this.state.status.level === 'MAINTENANCE' || this.state.status.level === 'UPCOMING MAINTENANCE') {
            return 'bg-yellow-500';
        } else if (this.state.status.level === 'ERROR') {
            return 'bg-red-500';
        }
    }

    render() {
        return (
            <Link to={"/status"}>
                <button className={"btn btn-info hidden-md center-text " + this.statusToButton()} title={this.state.status.body}>{this.state.status.title}</button>
            </Link>);
    }
}

export default StatusBar;