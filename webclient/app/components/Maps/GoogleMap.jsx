import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import './GoogleMap.scss';
import GoogleMapRun from './GoogleMap.run';

class GoogleMap extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        GoogleMapRun();
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    <Row>
                        <Col md={6}>
                            <div className="card card-default">
                                <div className="card-heading">Basic</div>
                                <div className="card-body">
                                    <div id="map" className="gmap"></div>
                                </div>
                            </div>
                        </Col>
                        <Col md={6}>
                            <div className="card card-default">
                                <div className="card-heading">Markers</div>
                                <div className="card-body">
                                    <div id="map-markers" className="gmap"></div>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    <div className="card card-default">
                        <div className="card-heading">Panorama</div>
                        <div className="card-body">
                            <div id="panorama" className="gmap"></div>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default GoogleMap;
