import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button } from 'react-bootstrap';

class BsGrid extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <Grid fluid>
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3>Example: Stacked-to-horizontal</h3>
                                    <p>Using a single set of<code>.col-md-*</code>grid classes, you can create a default grid system that starts out stacked on mobile devices and tablet devices (the extra small to small range) before becoming horizontal on desktop (medium) devices. Place grid columns in any<code>.row</code>.</p>
                                    <Row>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                        <Col md={1}>
                                            <div className="box-placeholder">.col-md-1</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col md={8}>
                                            <div className="box-placeholder">.col-md-8</div>
                                        </Col>
                                        <Col md={4}>
                                            <div className="box-placeholder">.col-md-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col md={4}>
                                            <div className="box-placeholder">.col-md-4</div>
                                        </Col>
                                        <Col md={4}>
                                            <div className="box-placeholder">.col-md-4</div>
                                        </Col>
                                        <Col md={4}>
                                            <div className="box-placeholder">.col-md-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col md={6}>
                                            <div className="box-placeholder">.col-md-6</div>
                                        </Col>
                                        <Col md={6}>
                                            <div className="box-placeholder">.col-md-6</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3>Example: Mobile and desktop</h3>
                                    <p>Don't want your columns to simply stack in smaller devices? Use the extra small and medium device grid classes by adding<code>.col-xs-*</code><code>.col-md-*</code>to your columns. See the example below for a better idea of how it all works.</p>
                                    <Row>
                                        <Col xs={12} md={8}>
                                            <div className="box-placeholder">.col-xs-12 .col-md-8</div>
                                        </Col>
                                        <Col xs={6} md={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-md-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col xs={6} md={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-md-4</div>
                                        </Col>
                                        <Col xs={6} md={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-md-4</div>
                                        </Col>
                                        <Col xs={6} md={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-md-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col xs={6}>
                                            <div className="box-placeholder">.col-xs-6</div>
                                        </Col>
                                        <Col xs={6}>
                                            <div className="box-placeholder">.col-xs-6</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3>Example: Mobile, tablet, desktops</h3>
                                    <p>Build on the previous example by creating even more dynamic and powerful layouts with tablet<code>.col-sm-*</code>classes.</p>
                                    <Row>
                                        <Col xs={12} sm={6} md={8}>
                                            <div className="box-placeholder">.col-xs-12 .col-sm-6 .col-md-8</div>
                                        </Col>
                                        <Col xs={6} md={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-md-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col xs={6} sm={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-4</div>
                                        </Col>
                                        <Col xs={6} sm={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-4</div>
                                        </Col>
                                        <div className="clearfix visible-xs">
                                            <div className="box-placeholder"></div>
                                        </div>
                                        <Col xs={6} sm={4}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-4</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3 id="grid-responsive-resets">Responsive column resets</h3>
                                    <p>With the four tiers of grids available you're bound to run into issues where, at certain breakpoints, your columns don't clear quite right as one is taller than the other. To fix that, use a combination of a<code>.clearfix</code>and our<a href="#responsive-utilities">responsive utility classes</a>.</p>
                                    <Row>
                                        <Col xs={6} sm={3}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-3<br/>Resize your viewport or check it out on your phone for an example.</div>
                                        </Col>
                                        <Col xs={6} sm={3}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-3</div>
                                        </Col>
                                        <div className="clearfix visible-xs"></div>
                                        <Col xs={6} sm={3}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-3</div>
                                        </Col>
                                        <Col xs={6} sm={3}>
                                            <div className="box-placeholder">.col-xs-6 .col-sm-3</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3 id="grid-offsetting">Offsetting columns</h3>
                                    <p>Move columns to the right using<code>.col-md-offset-*</code>classes. These classes increase the left margin of a column by<code>*</code>columns. For example,<code>.col-md-offset-4</code>moves<code>.col-md-4</code>over four columns.</p>
                                    <Row>
                                        <Col md={4}>
                                            <div className="box-placeholder">.col-md-4</div>
                                        </Col>
                                        <Col md={4} mdOffset={4}>
                                            <div className="box-placeholder">.col-md-4 .col-md-offset-4</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col md={3} mdOffset={3}>
                                            <div className="box-placeholder">.col-md-3 .col-md-offset-3</div>
                                        </Col>
                                        <Col md={3} mdOffset={3}>
                                            <div className="box-placeholder">.col-md-3 .col-md-offset-3</div>
                                        </Col>
                                    </Row>
                                    <Row>
                                        <Col md={6} mdOffset={3}>
                                            <div className="box-placeholder">.col-md-6 .col-md-offset-3</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3 id="grid-nesting">Nesting columns</h3>
                                    <p>To nest your content with the default grid, add a new<code>.row</code>and set of<code>.col-md-*</code>columns within an existing<code>.col-md-*</code>column. Nested rows should include a set of columns that add up to 12.</p>
                                    <Row>
                                        <div className="col-md-9">
                                            <div className="box-placeholder">Level 1: .col-md-9
                                                <Row>
                                                    <Col md={6}>
                                                        <div className="box-placeholder">Level 2: .col-md-6</div>
                                                    </Col>
                                                    <Col md={6}>
                                                        <div className="box-placeholder">Level 2: .col-md-6</div>
                                                    </Col>
                                                </Row>
                                            </div>
                                        </div>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <div className="panel panel-default">
                                <div className="panel-body">
                                    <h3 id="grid-column-ordering">Column ordering</h3>
                                    <p>Easily change the order of our built-in grid columns with<code>.col-md-push-*</code>and<code>.col-md-pull-*</code>modifier classes.</p>
                                    <Row>
                                        <Col md={9} mdPush={3}>
                                            <div className="box-placeholder">.col-md-9 .col-md-push-3</div>
                                        </Col>
                                        <Col md={3} mdPull={9}>
                                            <div className="box-placeholder">.col-md-3 .col-md-pull-9</div>
                                        </Col>
                                    </Row>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    {/* END row */}
                </Grid>
            </section>
        );
    }
}

export default BsGrid;
