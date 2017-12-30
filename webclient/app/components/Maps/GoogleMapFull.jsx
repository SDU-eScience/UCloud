import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import GoogleMapFullRun from './GoogleMapFull.run';

class GoogleMapFull extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        $(GoogleMapFullRun);
    }

    render() {
        return (
            <section>
                <div className="container-full">
                    <Row className="fh bg-white">
                        <Col md={3} className="fh hidden-sm hidden-xs pr0">
                            <div className="p-lg">
                                <h5 className="text-center">Places</h5>
                                <p className="text-center">Click on each item to focus a marker in the map</p>
                            </div>
                            <div id="markers-list" className="list-group list-group-unstyle">
                                <a data-panto-marker="0" className="list-group-item">
                                    <em className="pull-right ion-ios-arrow-forward"></em>
                                    Canada
                                </a>
                                <a data-panto-marker="1" className="list-group-item">
                                    <em className="pull-right ion-ios-arrow-forward"></em>
                                    New York
                                </a>
                                <a data-panto-marker="2" className="list-group-item">
                                    <em className="pull-right ion-ios-arrow-forward">
                                    </em>Toronto</a>
                                <a data-panto-marker="3" className="list-group-item">
                                    <em className="pull-right ion-ios-arrow-forward"></em>
                                    San Francisco
                                </a>
                                <a data-panto-marker="4" className="list-group-item">
                                    <em className="pull-right ion-ios-arrow-forward"></em>
                                    Utah
                                </a>
                            </div>
                        </Col>
                        <Col md={9} className="fh pl0">
                            <div id="mapfull-markers" className="gmap fh"></div>
                        </Col>
                    </Row>
                </div>
            </section>
        );
    }
}

export default GoogleMapFull;
