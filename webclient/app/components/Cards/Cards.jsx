import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Dropdown, MenuItem } from 'react-bootstrap';

import './Cards.scss';
import RippleRun from '../Ripple/Ripple.run';

class Cards extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        RippleRun();
    }

    render() {
        return (
            <section>
                <Grid className="container-lg">
                    <Row>
                        <Col lg={8} xs={12}>
                            <Row>
                                <Col sm={6} xs={12}>
                                    <div className="card">
                                        <div className="card-heading bg-pink-500">
                                            {/* START dropdown */}
                                            <div className="pull-right">
                                                <Dropdown pullRight id="dd2">
                                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                      <em className="ion-android-more-vertical"></em>
                                                    </Dropdown.Toggle>
                                                    <Dropdown.Menu className="md-dropdown-menu" >
                                                        <MenuItem eventKey="1">Option 1</MenuItem>
                                                        <MenuItem eventKey="2">Option 2</MenuItem>
                                                        <MenuItem eventKey="3">Option 3</MenuItem>
                                                    </Dropdown.Menu>
                                                </Dropdown>
                                            </div>
                                            {/* END dropdown */}
                                            <div className="card-title">Paracosm</div>
                                        </div>
                                        <div className="card-body">
                                            <p>Nulla egestas faucibus tincidunt.</p>
                                            <p className="m0"><a href="#"><img src="img/pic1.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic2.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic3.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic4.jpg" alt="Pic" className="mr-sm thumb48"/></a></p>
                                        </div>
                                        <div className="card-footer">
                                            <button type="button" className="btn btn-flat btn-default">Action 1</button>
                                            <button type="button" className="btn btn-flat btn-default">Action 2</button>
                                        </div>
                                    </div>
                                </Col>
                                <Col sm={6} xs={12}>
                                    <div className="card">
                                        <div className="card-heading bg-primary">
                                            <div className="card-title">Paracosm</div>
                                        </div>
                                        <div className="card-offset">
                                            <div className="card-offset-item text-right">
                                                <button type="button" className="btn-raised btn btn-info btn-circle btn-lg"><em className="ion-android-add"></em></button>
                                            </div>
                                        </div>
                                        <div className="card-body pt0">
                                            <p className="mb-sm">Cras rutrum scelerisque auctor. Donec ultricies blandit venenatis. Nulla facilisi. Praesent diam diam, venenatis lorem par.</p>
                                        </div>
                                        <div className="card-footer">
                                            <button type="button" className="btn btn-flat btn-default">Action 1</button>
                                            <button type="button" className="btn btn-flat btn-default">Action 2</button>
                                        </div>
                                    </div>
                                </Col>
                            </Row>
                            <Row>
                                <Col sm={6} xs={12}>
                                    <div className="card">
                                        <div className="card-item"><img src="img/pic5.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                            <div className="card-item-text text-right">Paracosm</div>
                                        </div>
                                        <div className="card-offset">
                                            <div className="card-offset-item text-left">
                                                <button type="button" className="btn-raised btn btn-primary btn-circle btn-lg">25</button>
                                            </div>
                                        </div>
                                        <div className="card-body pt0">
                                            <h4 className="m0">Sky red</h4>
                                            <p className="m0"><span className="mr-sm text-muted">by</span><a href="#">John</a></p>
                                        </div>
                                    </div>
                                </Col>
                                <Col sm={6} xs={12}>
                                    <div className="card">
                                        <div className="card-item"><img src="img/pic4.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                            <div className="card-item-text">Paracosm</div>
                                        </div>
                                        <div className="card-offset">
                                            <div className="card-offset-item text-center">
                                                <button type="button" className="btn-raised btn btn-success btn-circle btn-lg"><em className="ion-share"></em></button>
                                            </div>
                                        </div>
                                        <div className="card-body pt0">
                                            <h4 className="m0">Peaceful view</h4>
                                            <p className="m0"><span className="mr-sm text-muted">by</span><a href="#">Zachary</a></p>
                                        </div>
                                    </div>
                                </Col>
                            </Row>
                        </Col>
                        <Col lg={4} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic1.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                    <div className="card-item-text bg-transparent">
                                        <h4 className="text-center">Jill Howell</h4>
                                    </div>
                                </div>
                                <div className="card-offset">
                                    <div className="card-offset-item text-right">
                                        <button type="button" className="btn-raised btn btn-danger btn-circle btn-lg"><em className="ion-ios-heart"></em></button>
                                    </div>
                                </div>
                                <div className="mda-list">
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-call icon-2x text-primary"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) 456 789</h3>
                                            <h4 className="text-muted">Home</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-chatbubbles icon-lg text-muted"></em></div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>(123) +15 321 654</h3>
                                            <h4 className="text-muted">Mobile</h4>
                                        </div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"><em className="ion-android-mail icon-2x text-primary"></em></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>jill@personal.com</h3>
                                            <h4 className="text-muted">Personal</h4>
                                        </div>
                                        <div className="mda-list-item-icon pull-right"><em className="ion-compose icon-lg text-muted"></em></div>
                                    </div>
                                    <div className="mda-list-item">
                                        <div className="mda-list-item-icon"></div>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3>jill.howell@work.com</h3>
                                            <h4 className="text-muted">Work</h4>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    <Row>
                        <Col lg={4} md={6} xs={12}>
                            <div className="card bg-primary">
                                <div className="card-body">
                                    <div className="pull-right"><em className="ion-calendar icon-2x text-muted"></em></div>
                                    <h4 className="mv-sm">New event</h4>
                                    <h5>22 Aug, at 10 a.m.</h5>
                                </div>
                                <div className="card-footer">
                                    <button type="button" className="btn btn-flat btn-primary text-white">Add to Calendar</button>
                                </div>
                            </div>
                        </Col>
                        <Col lg={4} md={6} xs={12}>
                            <div className="card bg-success">
                                <div className="card-body">
                                    <Row>
                                        <Col xs={8}>
                                            <em className="ion-ios-musical-notes icon-2x pull-left"></em>
                                            <div className="pl-xl">
                                                <h5 className="mv-sm">Pellentesque vitae</h5>
                                                <h6 className="m0">Aliquam non eros</h6>
                                            </div>
                                        </Col>
                                        <Col xs={4}>
                                            <a href="#"><img src="img/user/05.jpg" alt="Image" className="img-responsive img-rounded"/></a>
                                        </Col>
                                    </Row>
                                </div>
                                <div className="card-footer clearfix">
                                    <div className="pull-left">
                                        <button type="button" className="btn btn-flat btn-success text-white">Play now</button>
                                    </div>
                                    <div className="pull-right">
                                        <button type="button" className="btn btn-flat btn-success text-white">Next</button>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col lg={4} xs={12}>
                            <div className="card bg-info">
                                <div className="card-body">
                                    <p className="lead m0">A simple announce could be added here with any text for users who want to read this.</p>
                                </div>
                                <div className="card-footer text-right">
                                    <button type="button" className="btn btn-flat btn-info text-white">Ok, Let's Go!</button>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* Large card */}
                    <div className="card">
                        <div className="card-body ">
                            {/* START dropdown */}
                            <div className="pull-right">
                                <Dropdown pullRight id="dd1">
                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                      <em className="ion-android-more-vertical"></em>
                                    </Dropdown.Toggle>
                                    <Dropdown.Menu className="md-dropdown-menu" >
                                        <MenuItem eventKey="1">Option 1</MenuItem>
                                        <MenuItem eventKey="2">Option 2</MenuItem>
                                        <MenuItem eventKey="3">Option 3</MenuItem>
                                    </Dropdown.Menu>
                                </Dropdown>
                            </div>
                            {/* END dropdown */}
                            <h3 className="mt0">Technology</h3>
                            <p>Dolore ex deserunt aute fugiat aute nulla ea sunt aliqua nisi cupidatat eu. Nostrud in laboris labore nisi amet do dolor eu fugiat consectetur elit cillum esse. Pariatur occaecat nisi laboris tempor laboris eiusmod qui id Lorem esse commodo in. Exercitation aute dolore deserunt culpa consequat elit labore incididunt elit anim.</p>
                        </div>
                        <div className="card-footer">
                            <button type="button" className="btn btn-flat btn-primary">Read More</button>
                        </div>
                    </div>
                    <Row>
                        <Col lg={3} sm={6} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic3.jpg" alt="MaterialImg" className="fw img-responsive"/></div>
                                <div className="card-body">
                                    <h4 className="mt0">Freedom in the air</h4>
                                    <p className="m0">Nunc vitae ipsum elit, non lacinia dui. Sed tempor lacinia tempus. Etiam eget congue nulla. Sed quis eros libero, a euismod nisl.</p>
                                </div>
                            </div>
                        </Col>
                        <Col lg={3} sm={6} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic2.jpg" alt="MaterialImg" className="fw img-responsive"/></div>
                                <div className="card-body">
                                    <h4 className="mt0">Mountain lake</h4>
                                    <p>Cras et dui non erat ornare ornare eget non sapien.</p>
                                    <div className="text-right">
                                        <button type="button" className="btn btn-flat btn-primary">Share</button>
                                        <button type="button" className="btn btn-flat btn-primary">More</button>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col lg={3} sm={6} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic1.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                    <div className="card-item-text"><a href="#" className="link-white"><em className="ion-map mr icon-lg"></em></a><a href="#" className="link-white"><em className="ion-ios-heart-outline mr icon-lg"></em></a><a href="#" className="link-white"><em className="ion-image icon-lg"></em></a></div>
                                </div>
                                <div className="card-body">
                                    <h4 className="mt0">Forest trip</h4>
                                    <p>Nullam quis lorem a est auctor venenatis vel vitae ipsum.</p>
                                    <hr/>
                                    <div className="clearfix">
                                        <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">Bonnie</a></div>
                                        <div className="pull-right text-muted"><em className="ion-android-time mr-sm"></em><span>a week ago</span></div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col lg={3} sm={6} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic4.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                    <p className="card-item-text">Picture.png</p>
                                </div>
                            </div>
                        </Col>
                        <Col lg={3} sm={6} xs={12}>
                            <div className="card">
                                <div className="card-item"><img src="img/pic1.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                    <p className="card-item-text">Image.jpg</p>
                                </div>
                            </div>
                        </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Cards;
