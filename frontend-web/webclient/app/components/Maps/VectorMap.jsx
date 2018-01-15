import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import './VectorMap.scss';
import VectorMapRun from './VectorMap.run';

class VectorMap extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        VectorMapRun();
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    <div className="card">
                        <div className="card-heading">
                            <div className="pull-right"><span className="mr text-muted">Show last year events</span>
                                <label className="switch switch-info">
                                    <input type="checkbox"/><span></span>
                                </label>
                            </div>
                            <div className="card-title">World Events</div>
                        </div>
                        <div className="card-body">
                            <div id="vector-map-world" style={{height:'240px'}} className="vector-map"></div>
                        </div>
                    </div>
                    <div className="card">
                        <div className="card-heading">
                            <div className="card-title">Usa Makers</div>
                        </div>
                        <div className="card-body">
                            <div id="vector-map-usa" style={{height:'240px'}} className="vector-map"></div>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default VectorMap;
