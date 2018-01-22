import React from 'react';
import { defaultStatus } from "../DefaultObjects";
import pubsub from "pubsub-js";

class Status extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            status: defaultStatus,
        };
    }

    componentDidMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
        // Cloud.get("/systemstatus/");
    }

    statusToColor() {
        if (this.state.status.level === 'NO ISSUES') {
            return 'bg-green-500';
        } else if (this.state.status.level === 'MAINTENANCE' || this.state.status.level === 'UPCOMING MAINTENANCE') {
            return 'bg-yellow-500';
        } else if (this.state.status.level === 'ERROR') {
            return 'bg-red-500';
        }
    };

    render() {
        return (
            <section>
                <div className={"container-overlap " + this.statusToColor()}>
                    <div className="media m0 pv">
                        <div className="media-left"><a href="#"/></div>
                        <div className="media-body media-middle">
                            <h4 className="media-heading">{this.state.status.title}</h4>
                        </div>
                    </div>
                </div>
                <div className="container-fluid">
                    <div className="row">
                        <div className="col">
                            <form className="card">
                                <div className="card-body">
                                    <h3 className="text-inherit editable editable-pre-wrapped editable-click editable-disabled">{this.state.status.body}</h3>
                                </div>
                            </form>
                        </div>
                    </div>
                </div>
            </section>);
    }
}

export default Status;