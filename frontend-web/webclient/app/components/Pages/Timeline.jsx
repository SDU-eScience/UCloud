import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import './Timeline.scss';

class Timeline extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid className="container-md">
                    {/* START timeline */}
                    <ul className="timeline-alt">
                        <li data-datetime="Now" className="timeline-separator"></li>
                        {/* START timeline item */}
                        <li>
                            <div className="timeline-badge bg-info"></div>
                            <div className="timeline-panel">
                                <div className="card">
                                    <div className="card-body">
                                        <p><strong>Client Meeting</strong></p>
                                        <small>Pellentesque ut diam velit, eget porttitor risus. Nullam posuere euismod volutpat.</small>
                                        <div className="text-right"><a href="">Av 123 St - Floor 2</a></div>
                                    </div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline item */}
                        <li className="timeline-inverted">
                            <div className="timeline-badge bg-pink-400"></div>
                            <div className="timeline-panel">
                                <div className="card">
                                    <div className="card-body"><em className="mr ion-ios-telephone icon-lg"></em>Call with Michael</div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline separator */}
                        <li data-datetime="Yesterday" className="timeline-separator"></li>
                        {/* END timeline separator */}
                        {/* START timeline item */}
                        <li>
                            <div className="timeline-badge bg-purple-500"></div>
                            <div className="timeline-panel">
                                <div className="card">
                                    <div className="card-body">
                                        <p><strong>Conference</strong></p>
                                        <p>Join development group</p><small><a href="skype:echo123?call"><em className="ion-ios-telephone mr-sm icon-lg"></em> Call the Skype Echo</a></small>
                                    </div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline item */}
                        <li className="timeline-inverted">
                            <div className="timeline-badge bg-green-500"></div>
                            <div className="timeline-panel">
                                <div className="card right">
                                    <div className="card-body">
                                        <p><strong>Appointment</strong></p>
                                        <p>Sed posuere consectetur est at lobortis. Aenean eu leo quam. Pellentesque ornare sem lacinia quam.</p>
                                    </div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline item */}
                        <li>
                            <div className="timeline-badge bg-info"></div>
                            <div className="timeline-panel">
                                <div className="card bg-blue-500">
                                    <div className="card-body">
                                        <p><strong>Fly</strong></p>
                                        <p>Sed posuere consectetur est at lobortis. Aenean eu leo quam. Pellentesque ornare sem lacinia quam venenatis vestibulum.</p>
                                    </div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline separator */}
                        <li data-datetime="2 days ago" className="timeline-separator"></li>
                        {/* END timeline separator */}
                        {/* START timeline item */}
                        <li className="timeline-inverted">
                            <div className="timeline-badge bg-warning"></div>
                            <div className="timeline-panel">
                                <div className="card">
                                    <div className="card-body">
                                        <p><strong>Relax</strong></p>
                                        <p>Listen some music</p>
                                    </div>
                                </div>
                            </div>
                        </li>
                        {/* END timeline item */}
                        {/* START timeline item */}
                        <li className="timeline-end"><a href="" className="timeline-badge bg-blue-grey-200"></a></li>
                        {/* END timeline item */}
                    </ul>
                    {/* END timeline */}
                </Grid>
            </section>
        );
    }
}

export default Timeline;
