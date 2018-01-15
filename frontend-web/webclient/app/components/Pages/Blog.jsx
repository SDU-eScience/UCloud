import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Blog extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid className="bg-full bg-pic1">
                    <div className="container container-md">
                        <Row>
                            <Col md={8}>
                                <div className="card b0"><a href="#" className="card-item card-media bg-pic5"></a>
                                    <div className="card-offset p0">
                                        <div className="card-offset-item text-right">
                                            <button type="button" className="btn-raised btn btn-default btn-circle btn-lg">25</button>
                                        </div>
                                    </div>
                                    <div className="card-body pt0">
                                        <h5>Nam blandit fringilla faucibus.</h5>
                                        <div className="clearfix">
                                            <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                            <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                        </div>
                                    </div>
                                </div>
                            </Col>
                            <Col md={4}>
                                <div className="card b0"><a href="#" className="card-item card-media bg-pic2"></a>
                                    <div className="card-offset p0">
                                        <div className="card-offset-item text-right">
                                            <button type="button" className="btn-raised btn btn-default btn-circle btn-lg">25</button>
                                        </div>
                                    </div>
                                    <div className="card-body pt0">
                                        <h5>Maecenas at porta.</h5>
                                        <div className="clearfix">
                                            <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                            <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                        </div>
                                    </div>
                                </div>
                            </Col>
                        </Row>
                        <div className="card b0">
                            <div className="card-item"><a href="#" className="card-item card-media bg-pic4"></a></div>
                            <div className="card-offset p0">
                                <div className="card-offset-item text-right">
                                    <button type="button" className="btn-raised btn btn-default btn-circle btn-lg">52</button>
                                </div>
                            </div>
                            <div className="card-body pt0">
                                <h5>Vestibulum at nisl sit amet</h5>
                                <div className="clearfix">
                                    <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                    <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="card b0">
                            <div className="card-item"><a href="#" className="card-item card-media bg-pic6"></a></div>
                            <div className="card-offset p0">
                                <div className="card-offset-item text-right">
                                    <button type="button" className="btn-raised btn btn-default btn-circle btn-lg">1</button>
                                </div>
                            </div>
                            <div className="card-body pt0">
                                <h5>Nam blandit fringilla faucibus.</h5>
                                <div className="clearfix">
                                    <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                    <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="card b0">
                            <div className="card-item">
                                <div className="card-item card-media bg-gray-darker">
                                    <div className="card-media-quote"><a href="#" className="text-inherit">Phasellus ac turpis sit amet metus suscipit scelerisque quis ac velit.</a></div>
                                </div>
                            </div>
                            <div className="card-body bb pb0">
                                <p className="text-bold">Duis a neque odio, at varius elit. Pellentesque augue velit, dapibus vitae ultricies a, facilisis eu enim Nam in mauris mauris, a accumsan arcu. Integer ut convallis dui...</p>
                            </div>
                            <div className="card-body pt0">
                                <h5>Morbi quis purus velit.</h5>
                                <div className="clearfix">
                                    <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                    <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                </div>
                            </div>
                        </div>
                        <div className="card b0">
                            <div className="card-item"><a href="#" className="card-item card-media bg-pic3"></a></div>
                            <div className="card-offset p0">
                                <div className="card-offset-item text-right">
                                    <button type="button" className="btn-raised btn btn-default btn-circle btn-lg">3</button>
                                </div>
                            </div>
                            <div className="card-body pt0">
                                <h5>Cum sociis natoque penatibus</h5>
                                <div className="clearfix">
                                    <div className="pull-left"><span className="mr-sm text-muted">by</span><a href="#">John</a></div>
                                    <div className="pull-right"><span className="text-muted">2 days ago</span></div>
                                </div>
                            </div>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default Blog;
