import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import './List.scss';

class Lists extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid className="container-lg">
                    <Row>
                        <Col md={6}>
                            <div className="card">
                                <div className="card-heading">Image with text items</div>
                                <div className="mda-list">
                                    <div className="mda-list-item"><img src="img/user/01.jpg" alt="List user" className="mda-list-item-img"/>
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Eric Graves</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/02.jpg" alt="List user" className="mda-list-item-img"/>
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Catherine Crawford</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/03.jpg" alt="List user" className="mda-list-item-img"/>
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Rosemary Jimenez</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/04.jpg" alt="List user" className="mda-list-item-img"/>
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Guy Carpenter</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="card bg-primary">
                                <div className="mda-list">
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-call icon-2x"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) 456 789</h3>
                                            <h4 className="text-muted">Home</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-android-star icon-lg"></em></div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-phone-portrait icon-2x"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) +15 321 654</h3>
                                            <h4 className="text-muted">Mobile</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-android-star icon-lg"></em></div>
                                    </div>
                                </div>
                            </div>
                            <div className="card bg-pink-500">
                                <div className="mda-list">
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-call icon-2x"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) 456 789</h3>
                                            <h4 className="text-muted">Home</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-bookmark icon-lg"></em></div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-phone-portrait icon-2x"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) +15 321 654</h3>
                                            <h4 className="text-muted">Mobile</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-bookmark icon-lg"></em></div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col md={6}>
                            <div className="card">
                                <div className="card-heading">Bordered list with Icons &amp; text items</div>
                                <div className="mda-list mda-list-bordered">
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon bg-info"><em className="ion-coffee icon-lg"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>Min Li Chan</h3>
                                            <h4>Brunch this weekend?</h4>
                                        </div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon bg-primary"><em className="ion-star icon-lg"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>Min Li Chan</h3>
                                            <h4>Brunch this weekend?</h4>
                                        </div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon bg-deep-purple-500"><em className="ion-cloud icon-lg"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>Min Li Chan</h3>
                                            <h4>Brunch this weekend?</h4>
                                        </div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-initial bg-pink-500">M</div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>Min Li Chan</h3>
                                            <h4>Brunch this weekend?</h4>
                                        </div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-initial bg-green-500">S</div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>Debra Steward</h3>
                                            <h4>Brunch this weekend?</h4>
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="card">
                                <div className="card-heading">Text tems</div>
                                <ul className="mda-list">
                                    <li className="mda-list-item">
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Min Li Chan</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </li>
                                    <li className="mda-list-item">
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Min Li Chan</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </li>
                                    <li className="mda-list-item">
                                        <div className="mda-list-item-text">
                                            <h3><a href="#">Min Li Chan</a></h3>
                                            <h4>Brunch this weekend?</h4>
                                            <p> I&apos;ll be in your neighborhood doing errands</p>
                                        </div>
                                    </li>
                                </ul>
                            </div>
                        </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Lists;


