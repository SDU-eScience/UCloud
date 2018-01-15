import React from 'react';
import pubsub from 'pubsub-js';

class LayoutsTabs extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <ul id="myTabs" role="tablist" className="nav nav-tabs nav-justified">
                    <li role="presentation" className="active"><a id="home-tab" href="#home" role="tab" data-toggle="tab" aria-controls="home" aria-expanded="true">Home</a></li>
                    <li role="presentation"><a id="profile-tab" href="#profile" role="tab" data-toggle="tab" aria-controls="profile">Profile</a></li>
                    <li role="presentation" className="dropdown"><a id="profile-tab" href="#messages" role="tab" data-toggle="tab" aria-controls="messages">Messages</a></li>
                </ul>
                <div id="myTabContent" className="tab-content text-center">
                    <div id="home" role="tabpanel" aria-labelledby="home-tab" className="tab-pane fade in active">
                        <h4>Home view</h4>
                    </div>
                    <div id="profile" role="tabpanel" aria-labelledby="profile-tab" className="tab-pane fade">
                        <h4>Profile view</h4>
                    </div>
                    <div id="messages" role="tabpanel" aria-labelledby="messages-tab" className="tab-pane fade">
                        <h4>Messages view</h4>
                    </div>
                </div>
            </section>
        );
    }
}

export default LayoutsTabs;
