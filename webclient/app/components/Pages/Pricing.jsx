import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Pricing extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid className="container-md">
                    <div className="text-center mb-lg pb-lg">
                      <div className="h2 text-bold">Start your own app today</div>
                      <p>Demonstration plans in different options to fit your project</p>
                    </div>
                    <Row>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bb bg-pink-300 text-white">
                            <div className="text-bold">STARTER</div>
                            <h3 className="mv-lg"><sup>$</sup><span className="text-lg">17</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Not ready for resale</span></p>
                          </div>
                          <div className="card-body text-center bt"><a href="" className="btn btn-default btn-flat">SELECT THIS PLAN</a></div>
                        </div>
                      </Col>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bg-info">
                            <div className="text-bold">IDEAL</div>
                            <h3 className="mv-lg"><sup>$</sup><span className="text-lg">49</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Not ready for resale</span></p>
                          </div>
                          <div className="card-body text-center bt"><a href="" className="btn btn-info btn-raised">BEST OPTION</a></div>
                        </div>
                      </Col>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bb bg-deep-purple-500">
                            <div className="text-bold">PREMIUM</div>
                            <h3 className="mv-lg"><sup>$</sup><span className="text-lg">95</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Ready for resale*</span></p>
                          </div>
                          <div className="card-body text-center bt"><a href="" className="btn btn-default btn-flat">SELECT THIS PLAN</a></div>
                        </div>
                      </Col>
                    </Row>
                    <div className="text-center mb-lg pb-lg">
                      <div className="h2 text-bold">Alternative setup</div>
                    </div>
                    <Row>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bb text-danger"><em className="ion-star icon-2x"></em>
                            <h3 className="mv-lg text-bold"><sup>$</sup><span className="text-lg">17</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Not ready for resale</span></p>
                          </div>
                          <div className="card-body text-center bt text-info"><a href="" className="btn btn-danger btn-flat">SELECT THIS PLAN</a></div>
                        </div>
                      </Col>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bb text-success"><em className="ion-help-buoy icon-2x"></em>
                            <h3 className="mv-lg text-bold"><sup>$</sup><span className="text-lg">49</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Not ready for resale</span></p>
                          </div>
                          <div className="card-body text-center bt"><a href="" className="btn btn-success btn-raised">BEST OPTION</a></div>
                        </div>
                      </Col>
                      {/* PLAN */}
                      <Col sm={4}>
                        <div className="card b">
                          <div className="card-body text-center bb text-warning"><em className="ion-wand icon-2x"></em>
                            <h3 className="mv-lg text-bold"><sup>$</sup><span className="text-lg">95</span><span className="text-xs">/mo</span></h3>
                          </div>
                          <div className="card-body text-center">
                            <p className="mb-lg"><span>Unlimited Styles</span></p>
                            <p className="mb-lg"><span>RTL &amp; Translation</span></p>
                            <p className="mb-lg"><span>Customer support</span></p>
                            <p className="mb-lg"><span>Free Updates</span></p>
                            <p className="mb-lg"><span>Limited to one client</span></p>
                            <p className="mb-lg"><span>Multiple installation</span></p>
                            <p className="mb-lg"><span>Ready for resale*</span></p>
                          </div>
                          <div className="card-body text-center bt"><a href="" className="btn btn-warning btn-flat">SELECT THIS PLAN</a></div>
                        </div>
                      </Col>
                    </Row>
                </Grid>
            </section>
        );
    }
}

export default Pricing;
