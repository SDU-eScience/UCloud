import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class Invoice extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    <div className="card">
                        <div className="card-body">
                            <Row>
                                <Col xs={6}>
                                    <h3 className="m0">Centric Inc.</h3><small className="text-muted">Nulla viverra dignissim metus ac placerat</small>
                                </Col>
                                <Col xs={6} className="text-right">
                                    <h5 className="mv">Invoice no. #2456544</h5>
                                </Col>
                            </Row>
                            <hr />
                            <Row>
                                <Col md={2}>
                                    <h4 className="m0">Deliver to:</h4>
                                </Col>
                                <Col md={10}>
                                    <address><strong>Twitter, Inc.</strong><br/>  1355 Market Street, Suite 900<br/>  San Francisco, CA 94103<br/><abbr title="Phone">P:</abbr> (123) 456-7890</address>
                                    <address className="mb0"><strong>Full Name</strong><br/><a href="mailto:#">first.last@example.com</a></address>
                                </Col>
                            </Row>
                            <div className="mv-lg"></div>
                            <div className="table-responsive mv-lg">
                                <table className="table table-bordered">
                                    <thead>
                                        <tr>
                                            <th>Item #</th>
                                            <th>Description</th>
                                            <th>Quantity</th>
                                            <th>Unit Price</th>
                                            <th className="text-right">Total</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr>
                                            <td>1001</td>
                                            <td>Iphone 5s - 64Gb</td>
                                            <td>3</td>
                                            <td>$ 200</td>
                                            <td className="text-right">$ 600</td>
                                        </tr>
                                        <tr>
                                            <td>2002</td>
                                            <td>Iphone 6s - 128Gb</td>
                                            <td>5</td>
                                            <td>$ 500</td>
                                            <td className="text-right">$ 2500</td>
                                        </tr>
                                        <tr>
                                            <td>3010</td>
                                            <td>Ipad 11z - 512Gb</td>
                                            <td>1</td>
                                            <td>$ 650</td>
                                            <td className="text-right">$ 650</td>
                                        </tr>
                                        <tr>
                                            <td>3009</td>
                                            <td>iMac ProRetina 17</td>
                                            <td>6</td>
                                            <td>$ 1100</td>
                                            <td className="text-right">$ 6600</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                            <Row>
                                <Col md={6}>
                                    <p><strong>Bottom words for this invoice</strong></p>
                                    <p><small>Nulla egestas faucibus tincidunt. Praesent non nulla et ligula luctus mattis eget at lacus. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet. Suspendisse vehicula, magna non tristique tincidunt, massa nisi luctus tellus, vel laoreet sem lectus ut nibh. Vestibulum purus ipsum.</small></p>
                                </Col>
                                <hr className="visible-xs visible-sm" />
                                <Col md={1} lg={3}></Col>
                                <Col md={5} lg={3} className="text-right">
                                    <p className="clearfix"><strong className="pull-left">Sub Total:</strong><span className="pull-right">10300.00</span></p>
                                    <p className="clearfix"><strong className="pull-left">Promo code:</strong><span className="pull-right">ABC-XYZ</span></p>
                                    <p className="clearfix"><strong className="pull-left">Tax (10%):</strong><span className="pull-right">1030.00</span></p>
                                    <hr />
                                    <h3 className="m0 text-right">USD 11330.00</h3>
                                </Col>
                            </Row>
                            <hr />
                            <div className="clearfix">
                                <button type="button" className="btn btn-info pull-left mr">Edit</button>
                                <button type="button" className="btn btn-default pull-left">Print</button>
                                <button type="button" className="btn btn-success pull-right">Send Invoice</button>
                            </div>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default Invoice;
