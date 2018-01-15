import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Contacts extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    <div className="mb-lg text-right">
                        <button type="button" className="btn btn-info"><em className="ion-plus mr-sm"></em>Add Contact</button>
                    </div>
                    <div className="mb-lg">
                        <form role="form">
                            <div className="mda-form-control">
                                <input type="text" placeholder="Search Name, Job, etc..." className="form-control input-lg" />
                            </div>
                        </form>
                    </div>
                    <Row>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/02.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Floyd Ortiz<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/03.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Nina King<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/04.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Tracy Powell<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/05.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Lynn Howell<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/06.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Pearl Ray<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/07.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Adrian Davis<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/02.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Terri Pearson<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/03.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Rachel Fernandez<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                        <Col md={4} sm={6}>
                            <div className="card">
                                <div className="card-body">
                                    {/* START dropdown */}
                                    <div className="pull-right dropdown visible-lg visible-md">
                                        <button type="button" data-toggle="dropdown" className="btn btn-flat btn-flat-icon"><em className="ion-android-more-vertical"></em></button>
                                        <ul role="menu" className="dropdown-menu md-dropdown-menu dropdown-menu-right">
                                            <li><a href="">Edit</a></li>
                                            <li><a href="">Block</a></li>
                                            <li><a href="">Delete</a></li>
                                        </ul>
                                    </div>
                                    {/* END dropdown */}
                                    <Row>
                                        <Col lg={4} md={8}><a href=""><img src="img/user/04.jpg" alt="Contact" className="fw img-responsive" /></a></Col>
                                    </Row>
                                    <h5>Annie Holt<small className="text-muted">Art director</small></h5>
                                    <p className="mt"><em className="ion-briefcase mr-sm"></em><span>Company Inc.</span></p>
                                    <p className="mt">Proin est sapien, convallis non hendrerit nec, laoreet ut ipsum. Sed pharetra euismod dolor, id feugiat ante volutpat eget.</p>
                                </div>
                                <div className="card-footer text-center">
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-email icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-facebook icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-twitter icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-linkedin icon-lg icon-fw"></em></button>
                                    <button type="button" className="btn btn-default btn-xs"><em className="ion-social-skype icon-lg icon-fw"></em></button>
                                </div>
                            </div>
                        </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Contacts;
