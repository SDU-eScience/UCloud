import React from 'react';
import pubsub from 'pubsub-js';
import { Breadcrumb, Grid, Row, Col, Jumbotron, Alert, ProgressBar, ButtonToolbar, OverlayTrigger, Button, Tooltip, Popover, ListGroup, ListGroupItem, Modal, Carousel, Accordion, Panel, Tabs, Tab, Well, Pagination} from 'react-bootstrap';

import BootstrapRun from './Bootstrapui.run.js';

class Bootstrapui extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        BootstrapRun();
    }

    constructor(props) {
        super(props);
        this.state = {
            showModal: false,
            activePage: 1
        };
    }

    close() {
        this.setState({ showModal: false });
    }

    open() {
        this.setState({ showModal: true });
    }

    handleSelect(eventKey) {
        this.setState({
          activePage: eventKey
        });
    }

    render() {

        const tooltip = (
          <Tooltip id="tooltip"><strong>Holy guacamole!</strong> Check this info.</Tooltip>
        );

        const LinkWithTooltip = React.createClass({
          render() {
            let tooltip = <Tooltip id={this.props.id}>{this.props.tooltip}</Tooltip>;

            return (
              <OverlayTrigger
                overlay={tooltip} placement="top"
                delayShow={300} delayHide={150}
              >
                <a href={this.props.href}>{this.props.children}</a>
              </OverlayTrigger>
            );
          }
        });

        const popoverLeft = (
          <Popover id="popover-positioned-left" title="Popover left">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverTop = (
          <Popover id="popover-positioned-top" title="Popover top">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverBottom = (
          <Popover id="popover-positioned-bottom" title="Popover bottom">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverRight = (
          <Popover id="popover-positioned-right" title="Popover right">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverClick = (
          <Popover id="popover-trigger-click" title="Popover bottom">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverHoverFocus = (
          <Popover id="popover-trigger-hover-focus" title="Popover bottom">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverFocus = (
          <Popover id="popover-trigger-focus" title="Popover bottom">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        const popoverClickRootClose = (
          <Popover id="popover-trigger-click-root-close" title="Popover bottom">
            <strong>Holy guacamole!</strong> Check this info.
          </Popover>
        );

        ///////

        return (
            <section>
                {/* Breadcrumb next to view title */}
                <Breadcrumb>
                    <Breadcrumb.Item href="#">
                      Home
                    </Breadcrumb.Item>
                    <Breadcrumb.Item href="#">
                      Dashboard
                    </Breadcrumb.Item>
                    <Breadcrumb.Item active>
                      Bootstrap
                    </Breadcrumb.Item>
                </Breadcrumb>
                <Grid fluid>
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            <Jumbotron>
                                <div className="container">
                                    <h1>Bootstrap Jumbotron</h1>
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing.</p>
                                    <p><a role="button" className="btn btn-primary btn-lg">Learn more</a></p>
                                </div>
                            </Jumbotron>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={6}>
                            {/* START panel */}
                            <div className="panel">
                                <div className="panel-heading">Alert Styles</div>
                                <div className="panel-body">
                                    <Alert bsStyle="success">Lorem ipsum dolor sit amet, consectetur adipisicing elit.<a href="#" className="alert-link">Alert Link</a></Alert>
                                    <Alert bsStyle="info">Lorem ipsum dolor sit amet, consectetur adipisicing elit.<a href="#" className="alert-link">Alert Link</a></Alert>
                                    <Alert bsStyle="danger">Lorem ipsum dolor sit amet, consectetur adipisicing elit.<a href="#" className="alert-link">Alert Link</a></Alert>
                                    <Alert bsStyle="warning">Lorem ipsum dolor sit amet, consectetur adipisicing elit.<a href="#" className="alert-link">Alert Link</a></Alert>
                                </div>
                            </div>
                            {/* END panel */}
                            {/* START panel */}
                            <div className="panel">
                                <div className="panel-heading">Progress Bar</div>
                                <div className="panel-body">
                                    <h4>Static</h4>
                                    <ProgressBar now={60} />
                                    <ProgressBar now={60} bsStyle="success"/>
                                    <ProgressBar now={60} bsStyle="danger"/>
                                    <h4>Striped</h4>
                                    <ProgressBar now={40} striped bsStyle="success"/>
                                    <ProgressBar now={20} striped bsStyle="info"/>
                                    <ProgressBar now={45} />
                                    <h4>Stacked</h4>
                                    <ProgressBar>
                                        <ProgressBar striped bsStyle="success" now={35} key={1} />
                                        <ProgressBar bsStyle="warning" now={20} key={2} />
                                        <ProgressBar active bsStyle="danger" now={10} key={3} />
                                    </ProgressBar>
                                </div>
                            </div>
                            {/* END panel */}
                            {/* START panel */}
                            <div className="panel">
                                <div className="panel-heading">Tooltips</div>
                                <div className="panel-body">
                                    <div>
                                        <ButtonToolbar>
                                            <OverlayTrigger placement="left" overlay={tooltip}>
                                              <Button bsStyle="default">Holy guacamole!</Button>
                                            </OverlayTrigger>
                                            <OverlayTrigger placement="top" overlay={tooltip}>
                                              <Button bsStyle="default">Holy guacamole!</Button>
                                            </OverlayTrigger>
                                            <OverlayTrigger placement="bottom" overlay={tooltip}>
                                              <Button bsStyle="default">Holy guacamole!</Button>
                                            </OverlayTrigger>
                                            <OverlayTrigger placement="right" overlay={tooltip}>
                                              <Button bsStyle="default">Holy guacamole!</Button>
                                            </OverlayTrigger>
                                        </ButtonToolbar>
                                        <p className="muted" style={{ marginBottom: 0 }}>
                                            Tight pants next level keffiyeh <LinkWithTooltip tooltip="Default tooltip" href="#" id="tooltip-1">you probably</LinkWithTooltip> haven't
                                            heard of them. Photo booth beard raw denim letterpress vegan messenger bag stumptown. Farm-to-table seitan, mcsweeney's
                                            fixie sustainable quinoa 8-bit american apparel <LinkWithTooltip tooltip={<span>Another <strong>tooltip</strong></span>} href="#" id="tooltip-2">have a</LinkWithTooltip>
                                            terry richardson vinyl chambray. Beard stumptown, cardigans banh mi lomo thundercats. Tofu biodiesel williamsburg marfa, four
                                            loko mcsweeney's cleanse vegan chambray. A really ironic artisan <LinkWithTooltip tooltip="Another one here too" href="#" id="tooltip-3">whatever keytar</LinkWithTooltip>,
                                            scenester farm-to-table banksy Austin <LinkWithTooltip tooltip="The last tip!" href="#" id="tooltip-4">twitter handle</LinkWithTooltip> freegan
                                            cred raw denim single-origin coffee viral.
                                        </p>
                                    </div>
                                </div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={6}>
                            {/* START panel */}
                            <div className="panel">
                                <div className="panel-heading">Modals</div>
                                <div className="panel-body">
                                    <p>Click to get the full Modal experience!</p>
                                    <Button bsStyle="primary" bsSize="large" onClick={this.open.bind(this)}>Launch demo modal</Button>
                                    <Modal show={this.state.showModal} onHide={this.close.bind(this)}>
                                      <Modal.Header closeButton>
                                        <Modal.Title>Modal heading</Modal.Title>
                                      </Modal.Header>
                                      <Modal.Body>
                                        <h4>Text in a modal</h4>
                                        <p>Duis mollis, est non commodo luctus, nisi erat porttitor ligula.</p>

                                        <hr />

                                        <h4>Overflowing text to show scroll behavior</h4>
                                        <p>Cras mattis consectetur purus sit amet fermentum. Cras justo odio, dapibus ac facilisis in, egestas eget quam. Morbi leo risus, porta ac consectetur ac, vestibulum at eros.</p>
                                        <p>Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor.</p>
                                        <p>Aenean lacinia bibendum nulla sed consectetur. Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Donec sed odio dui. Donec ullamcorper nulla non metus auctor fringilla.</p>
                                        <p>Cras mattis consectetur purus sit amet fermentum. Cras justo odio, dapibus ac facilisis in, egestas eget quam. Morbi leo risus, porta ac consectetur ac, vestibulum at eros.</p>
                                        <p>Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor.</p>
                                        <p>Aenean lacinia bibendum nulla sed consectetur. Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Donec sed odio dui. Donec ullamcorper nulla non metus auctor fringilla.</p>
                                        <p>Cras mattis consectetur purus sit amet fermentum. Cras justo odio, dapibus ac facilisis in, egestas eget quam. Morbi leo risus, porta ac consectetur ac, vestibulum at eros.</p>
                                        <p>Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Vivamus sagittis lacus vel augue laoreet rutrum faucibus dolor auctor.</p>
                                        <p>Aenean lacinia bibendum nulla sed consectetur. Praesent commodo cursus magna, vel scelerisque nisl consectetur et. Donec sed odio dui. Donec ullamcorper nulla non metus auctor fringilla.</p>
                                      </Modal.Body>
                                      <Modal.Footer>
                                        <Button onClick={this.close.bind(this)}>Close</Button>
                                      </Modal.Footer>
                                    </Modal>
                                </div>
                            </div>
                            {/* END panel */}
                            {/* START panel */}
                            <div className="panel">
                                <div className="panel-heading">Popovers</div>
                                <div className="panel-body">
                                    <h4>Positions</h4>
                                    <ButtonToolbar>
                                        <OverlayTrigger trigger="click" placement="left" overlay={popoverLeft}>
                                          <Button>Holy guacamole!</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger="click" placement="top" overlay={popoverTop}>
                                          <Button>Holy guacamole!</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger="click" placement="bottom" overlay={popoverBottom}>
                                          <Button>Holy guacamole!</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger="click" placement="right" overlay={popoverRight}>
                                          <Button>Holy guacamole!</Button>
                                        </OverlayTrigger>
                                    </ButtonToolbar>
                                    <h4>Trigger behaviors</h4>
                                    <ButtonToolbar>
                                        <OverlayTrigger trigger="click" placement="bottom" overlay={popoverClick}>
                                          <Button>Click</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger={['hover', 'focus']} placement="bottom" overlay={popoverHoverFocus}>
                                          <Button>Hover + Focus</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger="focus" placement="bottom" overlay={popoverFocus}>
                                          <Button>Focus</Button>
                                        </OverlayTrigger>
                                        <OverlayTrigger trigger="click" rootClose placement="bottom" overlay={popoverClickRootClose}>
                                          <Button>Click w/rootClose</Button>
                                        </OverlayTrigger>
                                    </ButtonToolbar>
                                </div>
                            </div>
                            {/* END panel */}
                            {/* END row */}
                            <div className="panel">
                                <div className="panel-heading">Carousel</div>
                                <div className="panel-body">
                                    <Carousel>
                                        <Carousel.Item>
                                          <img width={900} height={500} alt="900x500" src="img/pic1.jpg"/>
                                          <Carousel.Caption>
                                            <h3>First slide label</h3>
                                            <p>Nulla vitae elit libero, a pharetra augue mollis interdum.</p>
                                          </Carousel.Caption>
                                        </Carousel.Item>
                                        <Carousel.Item>
                                          <img width={900} height={500} alt="900x500" src="img/pic2.jpg"/>
                                          <Carousel.Caption>
                                            <h3>Second slide label</h3>
                                            <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit.</p>
                                          </Carousel.Caption>
                                        </Carousel.Item>
                                        <Carousel.Item>
                                          <img width={900} height={500} alt="900x500" src="img/pic3.jpg"/>
                                          <Carousel.Caption>
                                            <h3>Third slide label</h3>
                                            <p>Praesent commodo cursus magna, vel scelerisque nisl consectetur.</p>
                                          </Carousel.Caption>
                                        </Carousel.Item>
                                    </Carousel>
                                </div>
                            </div>
                            <div className="panel">
                                <div className="panel-heading">List groups</div>
                                <div className="panel-body">
                                    <ListGroup>
                                        <ListGroupItem href="#" active>Link 1</ListGroupItem>
                                        <ListGroupItem href="#">Link 2</ListGroupItem>
                                        <ListGroupItem href="#" disabled>Link 3</ListGroupItem>
                                    </ListGroup>
                                </div>
                            </div>
                        </Col>
                    </Row>
                    <h4 className="page-header">Panel Styles</h4>
                    {/* START row */}
                    <Row>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo7" className="panel panel-default">
                                <div className="panel-heading">
                                    <div className="panel-title">Default Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo8" className="panel panel-primary">
                                <div className="panel-heading">
                                    <div className="panel-title">Primary Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo9" className="panel panel-success">
                                <div className="panel-heading">
                                    <div className="panel-title">Success Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo10" className="panel panel-info">
                                <div className="panel-heading">
                                    <div className="panel-title">Info Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo11" className="panel panel-warning">
                                <div className="panel-heading">
                                    <div className="panel-title">Warning Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={4}>
                            {/* START panel */}
                            <div id="panelDemo12" className="panel panel-danger">
                                <div className="panel-heading">
                                    <div className="panel-title">Danger Panel</div>
                                </div>
                                <div className="panel-body">
                                    <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                                </div>
                                <div className="panel-footer">Panel Footer</div>
                            </div>
                            {/* END panel */}
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={12}>
                            {/* START panel */}
                            <div id="panelDemo13" className="panel">
                                <div className="panel-heading">Collapsible Accordion Panel Group</div>
                                <div className="panel-body">
                                    <Accordion>
                                        <Panel header="Collapsible Group Item #1" eventKey="1">
                                          Anim pariatur cliche reprehenderit, enim eiusmod high life accusamus terry richardson ad squid. 3 wolf moon officia aute, non cupidatat skateboard dolor brunch. Food truck quinoa nesciunt laborum eiusmod. Brunch 3 wolf moon tempor, sunt aliqua put a bird on it squid single-origin coffee nulla assumenda shoreditch et. Nihil anim keffiyeh helvetica, craft beer labore wes anderson cred nesciunt sapiente ea proident. Ad vegan excepteur butcher vice lomo. Leggings occaecat craft beer farm-to-table, raw denim aesthetic synth nesciunt you probably haven't heard of them accusamus labore sustainable VHS.
                                        </Panel>
                                        <Panel header="Collapsible Group Item #2" eventKey="2">
                                          Anim pariatur cliche reprehenderit, enim eiusmod high life accusamus terry richardson ad squid. 3 wolf moon officia aute, non cupidatat skateboard dolor brunch. Food truck quinoa nesciunt laborum eiusmod. Brunch 3 wolf moon tempor, sunt aliqua put a bird on it squid single-origin coffee nulla assumenda shoreditch et. Nihil anim keffiyeh helvetica, craft beer labore wes anderson cred nesciunt sapiente ea proident. Ad vegan excepteur butcher vice lomo. Leggings occaecat craft beer farm-to-table, raw denim aesthetic synth nesciunt you probably haven't heard of them accusamus labore sustainable VHS.
                                        </Panel>
                                        <Panel header="Collapsible Group Item #3" eventKey="3">
                                          Anim pariatur cliche reprehenderit, enim eiusmod high life accusamus terry richardson ad squid. 3 wolf moon officia aute, non cupidatat skateboard dolor brunch. Food truck quinoa nesciunt laborum eiusmod. Brunch 3 wolf moon tempor, sunt aliqua put a bird on it squid single-origin coffee nulla assumenda shoreditch et. Nihil anim keffiyeh helvetica, craft beer labore wes anderson cred nesciunt sapiente ea proident. Ad vegan excepteur butcher vice lomo. Leggings occaecat craft beer farm-to-table, raw denim aesthetic synth nesciunt you probably haven't heard of them accusamus labore sustainable VHS.
                                        </Panel>
                                    </Accordion>
                                </div>
                            </div>
                            {/* END panel */}
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={6}>
                            {/* START panel */}
                            <div id="panelDemo14" className="panel">
                                <div className="panel-heading">Basic Tabs</div>
                                <div className="panel-body">
                                    <div>
                                        <Tabs defaultActiveKey={2} id="uncontrolled-tab-example">
                                            <Tab eventKey={1} title="Home">Maecenas elementum, justo in pulvinar bibendum, tellus nulla adipiscing libero, at rutrum velit sapien vel velit. Phasellus hendrerit ullamcorper est eget tincidunt. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={2} title="Profile">Nullam eu lacus in nibh rutrum ornare at eget tellus. Nullam rutrum convallis lacus non mattis. Praesent dapibus justo dolor. Duis convallis placerat egestas. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={3} title="Messages">Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={4} title="Settings">Sed id lacus enim, sit amet imperdiet orci. Phasellus sed dui massa, vitae dapibus augue. Nam vehicula rhoncus nisl, et ullamcorper augue ullamcorper semper. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                        </Tabs>
                                    </div>
                                </div>
                            </div>
                            {/* END panel */}
                        </Col>
                        <Col lg={6}>
                            {/* START panel */}
                            <div id="panelDemo15" className="panel">
                                <div className="panel-heading">Pill Tabs</div>
                                <div className="panel-body">
                                    <div>
                                        <Tabs bsStyle="pills" defaultActiveKey={2} id="uncontrolled-tab-example">
                                            <Tab eventKey={1} title="Home">Maecenas elementum, justo in pulvinar bibendum, tellus nulla adipiscing libero, at rutrum velit sapien vel velit. Phasellus hendrerit ullamcorper est eget tincidunt. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={2} title="Profile">Nullam eu lacus in nibh rutrum ornare at eget tellus. Nullam rutrum convallis lacus non mattis. Praesent dapibus justo dolor. Duis convallis placerat egestas. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={3} title="Messages">Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                            <Tab eventKey={4} title="Settings">Sed id lacus enim, sit amet imperdiet orci. Phasellus sed dui massa, vitae dapibus augue. Nam vehicula rhoncus nisl, et ullamcorper augue ullamcorper semper. Aenean non neque a tortor tempor consequat a ut ligula. Mauris eros nibh, adipiscing ac commodo vel, molestie mattis magna. Curabitur adipiscing, tellus ut congue tempus, dui lacus cursus risus, nec pellentesque mi purus eget augue. Fusce ullamcorper placerat tortor, placerat consequat diam cursus posuere.</Tab>
                                        </Tabs>
                                    </div>
                                </div>
                            </div>
                            {/* END panel */}
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START row */}
                    <Row>
                        <Col lg={4}>
                            <Well bsSize="large">
                                <h4 className="mt0">Large Well</h4>
                                <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                            </Well>
                        </Col>
                        <Col lg={4}>
                            <Well>
                                <h4 className="mt0">Normal Well</h4>
                                <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                            </Well>
                        </Col>
                        <Col lg={4}>
                            <Well bsSize="small">
                                <h4 className="mt0">Small Well</h4>
                                <p>Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vestibulum tincidunt est vitae ultrices accumsan. Aliquam ornare lacus adipiscing, posuere lectus et, fringilla augue.</p>
                            </Well>
                        </Col>
                    </Row>
                    {/* END row */}
                    {/* START panel */}
                    <div className="panel">
                        <div className="panel-heading">
                            <h3 className="panel-title">Pagination</h3>
                        </div>
                        <div className="panel-body">
                            <div className="text-center">
                                <Pagination
                                  bsSize="large"
                                  items={10}
                                  activePage={this.state.activePage}
                                  onSelect={this.handleSelect.bind(this)} />
                                <br />

                                <Pagination
                                  bsSize="medium"
                                  items={10}
                                  activePage={this.state.activePage}
                                  onSelect={this.handleSelect.bind(this)} />
                                <br />

                                <Pagination
                                  bsSize="small"
                                  items={10}
                                  activePage={this.state.activePage}
                                  onSelect={this.handleSelect.bind(this)} />
                            </div>
                            <h5>Rounded buttons</h5>
                            <div className="text-center">
                                <Pagination
                                  className="pagination-rounded"
                                  bsSize="large"
                                  items={10}
                                  activePage={this.state.activePage}
                                  onSelect={this.handleSelect.bind(this)} />
                            </div>
                        </div>
                    </div>
                    {/* END panel */}
                </Grid>
            </section>
        );
    }
}

export default Bootstrapui;

