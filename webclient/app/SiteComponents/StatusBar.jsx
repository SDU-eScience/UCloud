import React from 'react';

class StatusBar extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            status: { title: "Title", body: "Body"},
        }
    }

    statusToButton() {
        return "";
    }

    render() {
        return (<button href="/statusoverview" className={"btn btn-info hidden-md center-text " + this.statusToButton()}
                        title={this.state.status.body}>{this.state.status.title}</button>)
    }
}

export default StatusBar;