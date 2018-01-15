import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Colors extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid className="container-lg">
                    <div className="card">
                      <div className="card-heading">
                        <div className="card-title">Material Design Color Palette</div>
                      </div>
                      <div className="card-body">
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-red-500">
                              <div className="mb-lg">Red</div>.bg-red-500
                            </div>
                            <div className="p bg-red-50">.bg-red-50</div>
                            <div className="p bg-red-100">.bg-red-100</div>
                            <div className="p bg-red-200">.bg-red-200</div>
                            <div className="p bg-red-300">.bg-red-300</div>
                            <div className="p bg-red-400">.bg-red-400</div>
                            <div className="p bg-red-500">.bg-red-500</div>
                            <div className="p bg-red-600">.bg-red-600</div>
                            <div className="p bg-red-700">.bg-red-700</div>
                            <div className="p bg-red-800">.bg-red-800</div>
                            <div className="p bg-red-900">.bg-red-900</div>
                            <div className="p bg-red-a100">.bg-red-a100</div>
                            <div className="p bg-red-a200">.bg-red-a200</div>
                            <div className="p bg-red-a400">.bg-red-a400</div>
                            <div className="p bg-red-a700">.bg-red-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-pink-500">
                              <div className="mb-lg">Pink</div>.bg-pink-500
                            </div>
                            <div className="p bg-pink-50">.bg-pink-50</div>
                            <div className="p bg-pink-100">.bg-pink-100</div>
                            <div className="p bg-pink-200">.bg-pink-200</div>
                            <div className="p bg-pink-300">.bg-pink-300</div>
                            <div className="p bg-pink-400">.bg-pink-400</div>
                            <div className="p bg-pink-500">.bg-pink-500</div>
                            <div className="p bg-pink-600">.bg-pink-600</div>
                            <div className="p bg-pink-700">.bg-pink-700</div>
                            <div className="p bg-pink-800">.bg-pink-800</div>
                            <div className="p bg-pink-900">.bg-pink-900</div>
                            <div className="p bg-pink-a100">.bg-pink-a100</div>
                            <div className="p bg-pink-a200">.bg-pink-a200</div>
                            <div className="p bg-pink-a400">.bg-pink-a400</div>
                            <div className="p bg-pink-a700">.bg-pink-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-purple-500">
                              <div className="mb-lg">Purple</div>.bg-purple-500
                            </div>
                            <div className="p bg-purple-50">.bg-purple-50</div>
                            <div className="p bg-purple-100">.bg-purple-100</div>
                            <div className="p bg-purple-200">.bg-purple-200</div>
                            <div className="p bg-purple-300">.bg-purple-300</div>
                            <div className="p bg-purple-400">.bg-purple-400</div>
                            <div className="p bg-purple-500">.bg-purple-500</div>
                            <div className="p bg-purple-600">.bg-purple-600</div>
                            <div className="p bg-purple-700">.bg-purple-700</div>
                            <div className="p bg-purple-800">.bg-purple-800</div>
                            <div className="p bg-purple-900">.bg-purple-900</div>
                            <div className="p bg-purple-a100">.bg-purple-a100</div>
                            <div className="p bg-purple-a200">.bg-purple-a200</div>
                            <div className="p bg-purple-a400">.bg-purple-a400</div>
                            <div className="p bg-purple-a700">.bg-purple-a700</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-deep-purple-500">
                              <div className="mb-lg">Deep-purple</div>.bg-deep-purple-500
                            </div>
                            <div className="p bg-deep-purple-50">.bg-deep-purple-50</div>
                            <div className="p bg-deep-purple-100">.bg-deep-purple-100</div>
                            <div className="p bg-deep-purple-200">.bg-deep-purple-200</div>
                            <div className="p bg-deep-purple-300">.bg-deep-purple-300</div>
                            <div className="p bg-deep-purple-400">.bg-deep-purple-400</div>
                            <div className="p bg-deep-purple-500">.bg-deep-purple-500</div>
                            <div className="p bg-deep-purple-600">.bg-deep-purple-600</div>
                            <div className="p bg-deep-purple-700">.bg-deep-purple-700</div>
                            <div className="p bg-deep-purple-800">.bg-deep-purple-800</div>
                            <div className="p bg-deep-purple-900">.bg-deep-purple-900</div>
                            <div className="p bg-deep-purple-a100">.bg-deep-purple-a100</div>
                            <div className="p bg-deep-purple-a200">.bg-deep-purple-a200</div>
                            <div className="p bg-deep-purple-a400">.bg-deep-purple-a400</div>
                            <div className="p bg-deep-purple-a700">.bg-deep-purple-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-indigo-500">
                              <div className="mb-lg">Indigo</div>.bg-indigo-500
                            </div>
                            <div className="p bg-indigo-50">.bg-indigo-50</div>
                            <div className="p bg-indigo-100">.bg-indigo-100</div>
                            <div className="p bg-indigo-200">.bg-indigo-200</div>
                            <div className="p bg-indigo-300">.bg-indigo-300</div>
                            <div className="p bg-indigo-400">.bg-indigo-400</div>
                            <div className="p bg-indigo-500">.bg-indigo-500</div>
                            <div className="p bg-indigo-600">.bg-indigo-600</div>
                            <div className="p bg-indigo-700">.bg-indigo-700</div>
                            <div className="p bg-indigo-800">.bg-indigo-800</div>
                            <div className="p bg-indigo-900">.bg-indigo-900</div>
                            <div className="p bg-indigo-a100">.bg-indigo-a100</div>
                            <div className="p bg-indigo-a200">.bg-indigo-a200</div>
                            <div className="p bg-indigo-a400">.bg-indigo-a400</div>
                            <div className="p bg-indigo-a700">.bg-indigo-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-blue-500">
                              <div className="mb-lg">Blue</div>.bg-blue-500
                            </div>
                            <div className="p bg-blue-50">.bg-blue-50</div>
                            <div className="p bg-blue-100">.bg-blue-100</div>
                            <div className="p bg-blue-200">.bg-blue-200</div>
                            <div className="p bg-blue-300">.bg-blue-300</div>
                            <div className="p bg-blue-400">.bg-blue-400</div>
                            <div className="p bg-blue-500">.bg-blue-500</div>
                            <div className="p bg-blue-600">.bg-blue-600</div>
                            <div className="p bg-blue-700">.bg-blue-700</div>
                            <div className="p bg-blue-800">.bg-blue-800</div>
                            <div className="p bg-blue-900">.bg-blue-900</div>
                            <div className="p bg-blue-a100">.bg-blue-a100</div>
                            <div className="p bg-blue-a200">.bg-blue-a200</div>
                            <div className="p bg-blue-a400">.bg-blue-a400</div>
                            <div className="p bg-blue-a700">.bg-blue-a700</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-light-blue-500">
                              <div className="mb-lg">Light-blue</div>.bg-light-blue-500
                            </div>
                            <div className="p bg-light-blue-50">.bg-light-blue-50</div>
                            <div className="p bg-light-blue-100">.bg-light-blue-100</div>
                            <div className="p bg-light-blue-200">.bg-light-blue-200</div>
                            <div className="p bg-light-blue-300">.bg-light-blue-300</div>
                            <div className="p bg-light-blue-400">.bg-light-blue-400</div>
                            <div className="p bg-light-blue-500">.bg-light-blue-500</div>
                            <div className="p bg-light-blue-600">.bg-light-blue-600</div>
                            <div className="p bg-light-blue-700">.bg-light-blue-700</div>
                            <div className="p bg-light-blue-800">.bg-light-blue-800</div>
                            <div className="p bg-light-blue-900">.bg-light-blue-900</div>
                            <div className="p bg-light-blue-a100">.bg-light-blue-a100</div>
                            <div className="p bg-light-blue-a200">.bg-light-blue-a200</div>
                            <div className="p bg-light-blue-a400">.bg-light-blue-a400</div>
                            <div className="p bg-light-blue-a700">.bg-light-blue-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-cyan-500">
                              <div className="mb-lg">Cyan</div>.bg-cyan-500
                            </div>
                            <div className="p bg-cyan-50">.bg-cyan-50</div>
                            <div className="p bg-cyan-100">.bg-cyan-100</div>
                            <div className="p bg-cyan-200">.bg-cyan-200</div>
                            <div className="p bg-cyan-300">.bg-cyan-300</div>
                            <div className="p bg-cyan-400">.bg-cyan-400</div>
                            <div className="p bg-cyan-500">.bg-cyan-500</div>
                            <div className="p bg-cyan-600">.bg-cyan-600</div>
                            <div className="p bg-cyan-700">.bg-cyan-700</div>
                            <div className="p bg-cyan-800">.bg-cyan-800</div>
                            <div className="p bg-cyan-900">.bg-cyan-900</div>
                            <div className="p bg-cyan-a100">.bg-cyan-a100</div>
                            <div className="p bg-cyan-a200">.bg-cyan-a200</div>
                            <div className="p bg-cyan-a400">.bg-cyan-a400</div>
                            <div className="p bg-cyan-a700">.bg-cyan-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-teal-500">
                              <div className="mb-lg">Teal</div>.bg-teal-500
                            </div>
                            <div className="p bg-teal-50">.bg-teal-50</div>
                            <div className="p bg-teal-100">.bg-teal-100</div>
                            <div className="p bg-teal-200">.bg-teal-200</div>
                            <div className="p bg-teal-300">.bg-teal-300</div>
                            <div className="p bg-teal-400">.bg-teal-400</div>
                            <div className="p bg-teal-500">.bg-teal-500</div>
                            <div className="p bg-teal-600">.bg-teal-600</div>
                            <div className="p bg-teal-700">.bg-teal-700</div>
                            <div className="p bg-teal-800">.bg-teal-800</div>
                            <div className="p bg-teal-900">.bg-teal-900</div>
                            <div className="p bg-teal-a100">.bg-teal-a100</div>
                            <div className="p bg-teal-a200">.bg-teal-a200</div>
                            <div className="p bg-teal-a400">.bg-teal-a400</div>
                            <div className="p bg-teal-a700">.bg-teal-a700</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-green-500">
                              <div className="mb-lg">Green</div>.bg-green-500
                            </div>
                            <div className="p bg-green-50">.bg-green-50</div>
                            <div className="p bg-green-100">.bg-green-100</div>
                            <div className="p bg-green-200">.bg-green-200</div>
                            <div className="p bg-green-300">.bg-green-300</div>
                            <div className="p bg-green-400">.bg-green-400</div>
                            <div className="p bg-green-500">.bg-green-500</div>
                            <div className="p bg-green-600">.bg-green-600</div>
                            <div className="p bg-green-700">.bg-green-700</div>
                            <div className="p bg-green-800">.bg-green-800</div>
                            <div className="p bg-green-900">.bg-green-900</div>
                            <div className="p bg-green-a100">.bg-green-a100</div>
                            <div className="p bg-green-a200">.bg-green-a200</div>
                            <div className="p bg-green-a400">.bg-green-a400</div>
                            <div className="p bg-green-a700">.bg-green-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-light-green-500">
                              <div className="mb-lg">Light-green</div>.bg-light-green-500
                            </div>
                            <div className="p bg-light-green-50">.bg-light-green-50</div>
                            <div className="p bg-light-green-100">.bg-light-green-100</div>
                            <div className="p bg-light-green-200">.bg-light-green-200</div>
                            <div className="p bg-light-green-300">.bg-light-green-300</div>
                            <div className="p bg-light-green-400">.bg-light-green-400</div>
                            <div className="p bg-light-green-500">.bg-light-green-500</div>
                            <div className="p bg-light-green-600">.bg-light-green-600</div>
                            <div className="p bg-light-green-700">.bg-light-green-700</div>
                            <div className="p bg-light-green-800">.bg-light-green-800</div>
                            <div className="p bg-light-green-900">.bg-light-green-900</div>
                            <div className="p bg-light-green-a100">.bg-light-green-a100</div>
                            <div className="p bg-light-green-a200">.bg-light-green-a200</div>
                            <div className="p bg-light-green-a400">.bg-light-green-a400</div>
                            <div className="p bg-light-green-a700">.bg-light-green-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-lime-500">
                              <div className="mb-lg">Lime</div>.bg-lime-500
                            </div>
                            <div className="p bg-lime-50">.bg-lime-50</div>
                            <div className="p bg-lime-100">.bg-lime-100</div>
                            <div className="p bg-lime-200">.bg-lime-200</div>
                            <div className="p bg-lime-300">.bg-lime-300</div>
                            <div className="p bg-lime-400">.bg-lime-400</div>
                            <div className="p bg-lime-500">.bg-lime-500</div>
                            <div className="p bg-lime-600">.bg-lime-600</div>
                            <div className="p bg-lime-700">.bg-lime-700</div>
                            <div className="p bg-lime-800">.bg-lime-800</div>
                            <div className="p bg-lime-900">.bg-lime-900</div>
                            <div className="p bg-lime-a100">.bg-lime-a100</div>
                            <div className="p bg-lime-a200">.bg-lime-a200</div>
                            <div className="p bg-lime-a400">.bg-lime-a400</div>
                            <div className="p bg-lime-a700">.bg-lime-a700</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-yellow-500">
                              <div className="mb-lg">Yellow</div>.bg-yellow-500
                            </div>
                            <div className="p bg-yellow-50">.bg-yellow-50</div>
                            <div className="p bg-yellow-100">.bg-yellow-100</div>
                            <div className="p bg-yellow-200">.bg-yellow-200</div>
                            <div className="p bg-yellow-300">.bg-yellow-300</div>
                            <div className="p bg-yellow-400">.bg-yellow-400</div>
                            <div className="p bg-yellow-500">.bg-yellow-500</div>
                            <div className="p bg-yellow-600">.bg-yellow-600</div>
                            <div className="p bg-yellow-700">.bg-yellow-700</div>
                            <div className="p bg-yellow-800">.bg-yellow-800</div>
                            <div className="p bg-yellow-900">.bg-yellow-900</div>
                            <div className="p bg-yellow-a100">.bg-yellow-a100</div>
                            <div className="p bg-yellow-a200">.bg-yellow-a200</div>
                            <div className="p bg-yellow-a400">.bg-yellow-a400</div>
                            <div className="p bg-yellow-a700">.bg-yellow-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-amber-500">
                              <div className="mb-lg">Amber</div>.bg-amber-500
                            </div>
                            <div className="p bg-amber-50">.bg-amber-50</div>
                            <div className="p bg-amber-100">.bg-amber-100</div>
                            <div className="p bg-amber-200">.bg-amber-200</div>
                            <div className="p bg-amber-300">.bg-amber-300</div>
                            <div className="p bg-amber-400">.bg-amber-400</div>
                            <div className="p bg-amber-500">.bg-amber-500</div>
                            <div className="p bg-amber-600">.bg-amber-600</div>
                            <div className="p bg-amber-700">.bg-amber-700</div>
                            <div className="p bg-amber-800">.bg-amber-800</div>
                            <div className="p bg-amber-900">.bg-amber-900</div>
                            <div className="p bg-amber-a100">.bg-amber-a100</div>
                            <div className="p bg-amber-a200">.bg-amber-a200</div>
                            <div className="p bg-amber-a400">.bg-amber-a400</div>
                            <div className="p bg-amber-a700">.bg-amber-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-orange-500">
                              <div className="mb-lg">Orange</div>.bg-orange-500
                            </div>
                            <div className="p bg-orange-50">.bg-orange-50</div>
                            <div className="p bg-orange-100">.bg-orange-100</div>
                            <div className="p bg-orange-200">.bg-orange-200</div>
                            <div className="p bg-orange-300">.bg-orange-300</div>
                            <div className="p bg-orange-400">.bg-orange-400</div>
                            <div className="p bg-orange-500">.bg-orange-500</div>
                            <div className="p bg-orange-600">.bg-orange-600</div>
                            <div className="p bg-orange-700">.bg-orange-700</div>
                            <div className="p bg-orange-800">.bg-orange-800</div>
                            <div className="p bg-orange-900">.bg-orange-900</div>
                            <div className="p bg-orange-a100">.bg-orange-a100</div>
                            <div className="p bg-orange-a200">.bg-orange-a200</div>
                            <div className="p bg-orange-a400">.bg-orange-a400</div>
                            <div className="p bg-orange-a700">.bg-orange-a700</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-deep-orange-500">
                              <div className="mb-lg">Deep-orange</div>.bg-deep-orange-500
                            </div>
                            <div className="p bg-deep-orange-50">.bg-deep-orange-50</div>
                            <div className="p bg-deep-orange-100">.bg-deep-orange-100</div>
                            <div className="p bg-deep-orange-200">.bg-deep-orange-200</div>
                            <div className="p bg-deep-orange-300">.bg-deep-orange-300</div>
                            <div className="p bg-deep-orange-400">.bg-deep-orange-400</div>
                            <div className="p bg-deep-orange-500">.bg-deep-orange-500</div>
                            <div className="p bg-deep-orange-600">.bg-deep-orange-600</div>
                            <div className="p bg-deep-orange-700">.bg-deep-orange-700</div>
                            <div className="p bg-deep-orange-800">.bg-deep-orange-800</div>
                            <div className="p bg-deep-orange-900">.bg-deep-orange-900</div>
                            <div className="p bg-deep-orange-a100">.bg-deep-orange-a100</div>
                            <div className="p bg-deep-orange-a200">.bg-deep-orange-a200</div>
                            <div className="p bg-deep-orange-a400">.bg-deep-orange-a400</div>
                            <div className="p bg-deep-orange-a700">.bg-deep-orange-a700</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-brown-500">
                              <div className="mb-lg">Brown</div>.bg-brown-500
                            </div>
                            <div className="p bg-brown-50">.bg-brown-50</div>
                            <div className="p bg-brown-100">.bg-brown-100</div>
                            <div className="p bg-brown-200">.bg-brown-200</div>
                            <div className="p bg-brown-300">.bg-brown-300</div>
                            <div className="p bg-brown-400">.bg-brown-400</div>
                            <div className="p bg-brown-500">.bg-brown-500</div>
                            <div className="p bg-brown-600">.bg-brown-600</div>
                            <div className="p bg-brown-700">.bg-brown-700</div>
                            <div className="p bg-brown-800">.bg-brown-800</div>
                            <div className="p bg-brown-900">.bg-brown-900</div>
                          </Col>
                          <Col sm={4}>
                            <div className="p bg-blue-grey-500">
                              <div className="mb-lg">Blue-grey</div>.bg-blue-grey-500
                            </div>
                            <div className="p bg-blue-grey-50">.bg-blue-grey-50</div>
                            <div className="p bg-blue-grey-100">.bg-blue-grey-100</div>
                            <div className="p bg-blue-grey-200">.bg-blue-grey-200</div>
                            <div className="p bg-blue-grey-300">.bg-blue-grey-300</div>
                            <div className="p bg-blue-grey-400">.bg-blue-grey-400</div>
                            <div className="p bg-blue-grey-500">.bg-blue-grey-500</div>
                            <div className="p bg-blue-grey-600">.bg-blue-grey-600</div>
                            <div className="p bg-blue-grey-700">.bg-blue-grey-700</div>
                            <div className="p bg-blue-grey-800">.bg-blue-grey-800</div>
                            <div className="p bg-blue-grey-900">.bg-blue-grey-900</div>
                          </Col>
                        </Row>
                        <Row className="mb-lg">
                          <Col sm={4}>
                            <div className="p bg-grey-500">
                              <div className="mb-lg">Grey</div>.bg-grey-500
                            </div>
                            <div className="p bg-grey-50">.bg-grey-50</div>
                            <div className="p bg-grey-100">.bg-grey-100</div>
                            <div className="p bg-grey-200">.bg-grey-200</div>
                            <div className="p bg-grey-300">.bg-grey-300</div>
                            <div className="p bg-grey-400">.bg-grey-400</div>
                            <div className="p bg-grey-500">.bg-grey-500</div>
                            <div className="p bg-grey-600">.bg-grey-600</div>
                            <div className="p bg-grey-700">.bg-grey-700</div>
                            <div className="p bg-grey-800">.bg-grey-800</div>
                            <div className="p bg-grey-900">.bg-grey-900</div>
                          </Col>
                        </Row>
                      </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default Colors;

