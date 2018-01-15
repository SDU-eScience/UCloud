import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Gallery extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid className="container-unwrap">
                    <Row>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic4.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic4"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic1.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic1"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic5.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic5"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic3.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic3"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic6.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic6"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic2.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic2"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic1.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic1"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic2.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic2"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic3.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic3"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                        <Col lg={4} md={6}>
                            <div className="card">
                                <a href="img/pic6.jpg" data-gallery="" title="Unsplash images" className="card-item">
                                    <div className="card-item-image bg-pic6"></div>
                                    <div className="card-item-text bg-fade">
                                        <div className="clearfix">
                                            <h5 className="mv pull-left">Camera roll</h5>
                                            <h6 className="mv pull-right text-light">10:20</h6>
                                        </div>
                                    </div>
                                </a>
                            </div>
                        </Col>
                    </Row>
                </Grid>
                {/* The gallery preview container */}
                <div id="blueimp-gallery" className="blueimp-gallery">
                    <div className="slides">
                        <h3 className="title"></h3>
                            <a className="prev">&lsaquo;</a>
                            <a className="next">&rsaquo;</a>
                            <a className="close">&times;</a>
                            <a className="play-pause"></a>
                        <ol className="indicator"></ol>
                    </div>
                </div>
            </section>
        );
    }
}

export default Gallery;
