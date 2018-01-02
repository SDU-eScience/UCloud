import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button, ButtonGroup, ButtonToolbar, DropdownButton, SplitButton, MenuItem } from 'react-bootstrap';

class Buttons extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    renderDropdownButton(title, i) {
      return (
        <DropdownButton bsStyle={title.toLowerCase()} title={title} key={i} id={`dropdown-basic-${i}`}>
          <MenuItem eventKey="1">Action</MenuItem>
          <MenuItem eventKey="2">Another action</MenuItem>
          <MenuItem eventKey="3" active>Active Item</MenuItem>
          <MenuItem divider />
          <MenuItem eventKey="4">Separated link</MenuItem>
        </DropdownButton>
      );
    }

    renderDropdownButtonSplit(title, i) {
      return (
        <SplitButton bsStyle={title.toLowerCase()} title={title} key={i} id={`split-button-basic-${i}`}>
          <MenuItem eventKey="1">Action</MenuItem>
          <MenuItem eventKey="2">Another action</MenuItem>
          <MenuItem eventKey="3">Something else here</MenuItem>
          <MenuItem divider />
          <MenuItem eventKey="4">Separated link</MenuItem>
        </SplitButton>
      );
    }

    render() {
        const BUTTONS = ['Default', 'Primary', 'Success', 'Info', 'Warning', 'Danger', 'Link'];

        return (
            <section>
                <Grid className="container-lg">
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button colors</h5>
                            <Button bsStyle="default" className="mr mb-sm ripple">Default</Button>
                            <Button bsStyle="primary" className="mr mb-sm ripple">Primary</Button>
                            <Button bsStyle="success" className="mr mb-sm ripple">Success</Button>
                            <Button bsStyle="info" className="mr mb-sm ripple">Info</Button>
                            <Button bsStyle="warning" className="mr mb-sm ripple">Warning</Button>
                            <Button bsStyle="danger" className="mr mb-sm ripple">Danger</Button>
                            <Button className="mb-sm btn-inverse ripple">Inverse</Button>
                            <br/><br/>
                            <Button bsStyle="link" className="ripple">Button Link</Button>
                            <Button bsStyle="default" className="ripple mr"><strong>button tag</strong></Button>
                            <Button bsStyle="default" href="#" className="ripple"><strong>anchor tag</strong></Button>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Flat</h5>
                            <p>
                                <Button bsStyle="default" className="btn-flat ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-flat ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-flat ripple">Success</Button>
                                <Button bsStyle="info" className="btn-flat ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-flat ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-flat ripple">Danger</Button>
                                <Button className="btn-flat btn-inverse ripple">Inverse</Button>
                            </p>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Raised</h5>
                            <p>
                                <Button bsStyle="default" className="btn-raised mr ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-raised mr ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-raised mr ripple">Success</Button>
                                <Button bsStyle="info" className="btn-raised mr ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-raised mr ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-raised mr ripple">Danger</Button>
                                <Button className="btn-raised mr btn-inverse ripple">Inverse</Button>
                            </p>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Circle</h5>
                            <p>
                                <Button bsStyle="default" className="btn-circle mr ripple">D</Button>
                                <Button bsStyle="primary" className="btn-circle mr ripple">P</Button>
                                <Button bsStyle="success" className="btn-circle mr ripple">S</Button>
                                <Button bsStyle="info" className="btn-circle mr ripple">I</Button>
                                <Button bsStyle="warning" className="btn-circle mr ripple">W</Button>
                                <Button bsStyle="danger" className="btn-circle mr ripple">D</Button>
                                <Button className="btn-circle mr btn-inverse ripple">I</Button>
                            </p>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Labeled</h5>
                            {/* Success button with label */}
                            <button type="button" className="btn btn-labeled btn-success ripple"><span className="btn-label"><i className="ion-checkmark-round"></i></span>Success</button>
                            {/* Danger button with label */}
                            <button type="button" className="btn btn-labeled btn-danger ripple"><span className="btn-label"><i className="ion-close-round"></i></span>Danger</button>
                            {/* Info button with label */}
                            <button type="button" className="btn btn-labeled btn-info ripple"><span className="btn-label"><i className="ion-alert"></i></span>Info</button>
                            {/* Warning button with label */}
                            <button type="button" className="btn btn-labeled btn-warning ripple"><span className="btn-label"><i className="ion-nuclear"></i></span>Warning</button><br/><br/>
                            {/* Standard button with label */}
                            <button type="button" className="btn btn-labeled btn-default ripple"><span className="btn-label"><i className="ion-arrow-left-c"></i></span>Left</button>
                            {/* Standard button with label on the right side */}
                            <button type="button" className="btn btn-labeled btn-default ripple">Right<span className="btn-label btn-label-right"><i className="ion-arrow-right-c"></i></span></button>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button sizing</h5>
                            <Row>
                                <Col md={4}>
                                    <p>
                                        <Button bsStyle="primary" className="ripple btn-xl">X-Large button</Button>
                                        <Button bsStyle="default" className="ripple btn-xl">X-Large button</Button>
                                    </p>
                                </Col>
                                <Col md={4}>
                                    <p>
                                        <Button bsStyle="primary" bsSize="large" className="ripple">Large button</Button>
                                        <Button bsStyle="default" bsSize="large" className="ripple">Large button</Button>
                                    </p>
                                    <p>
                                        <Button bsStyle="primary" className="ripple">Default button</Button>
                                        <Button bsStyle="default" className="ripple">Default button</Button>
                                    </p>
                                </Col>
                                <Col md={4}>
                                    <p>
                                        <Button bsStyle="primary" bsSize="small" className="ripple">Small button</Button>
                                        <Button bsStyle="default" bsSize="small" className="ripple">Small button</Button>
                                    </p>
                                    <p>
                                        <Button bsStyle="primary" bsSize="xsmall" className="ripple">Extra small button</Button>
                                        <Button bsStyle="default" bsSize="xsmall" className="ripple">Extra small button</Button>
                                    </p>
                                </Col>
                            </Row>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button group</h5>
                            <p></p>
                            <ButtonGroup>
                                <Button>Left</Button>
                                <Button>Middle</Button>
                                <Button>Right</Button>
                            </ButtonGroup>
                            <p></p>
                            <p></p>
                            <ButtonToolbar>
                                <ButtonGroup>
                                  <Button>1</Button>
                                  <Button>2</Button>
                                  <Button>3</Button>
                                  <Button>4</Button>
                                </ButtonGroup>

                                <ButtonGroup>
                                  <Button>5</Button>
                                  <Button>6</Button>
                                  <Button>7</Button>
                                </ButtonGroup>

                                <ButtonGroup>
                                  <Button>8</Button>
                                </ButtonGroup>
                            </ButtonToolbar>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Pills</h5>
                            <p>Pill left</p>
                            <p>
                                <Button bsStyle="default" className="btn-pill-left mr ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-pill-left mr ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-pill-left mr ripple">Success</Button>
                                <Button bsStyle="info" className="btn-pill-left mr ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-pill-left mr ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-pill-left mr ripple">Danger</Button>
                                <Button className="btn-pill-left mr btn-inverse ripple">Inverse</Button>
                            </p>
                            <p>Pill right</p>
                            <p>
                                <Button bsStyle="default" className="btn-pill-right mr ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-pill-right mr ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-pill-right mr ripple">Success</Button>
                                <Button bsStyle="info" className="btn-pill-right mr ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-pill-right mr ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-pill-right mr ripple">Danger</Button>
                                <Button className="btn-pill-right mr btn-inverse ripple">Inverse</Button>
                            </p>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Oval</h5>
                                <Button bsStyle="default" className="btn-oval mr ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-oval mr ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-oval mr ripple">Success</Button>
                                <Button bsStyle="info" className="btn-oval mr ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-oval mr ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-oval mr ripple">Danger</Button>
                                <Button className="btn-oval mr btn-inverse ripple">Inverse</Button>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button Square</h5>
                                <Button bsStyle="default" className="btn-square mr ripple">Default</Button>
                                <Button bsStyle="primary" className="btn-square mr ripple">Primary</Button>
                                <Button bsStyle="success" className="btn-square mr ripple">Success</Button>
                                <Button bsStyle="info" className="btn-square mr ripple">Info</Button>
                                <Button bsStyle="warning" className="btn-square mr ripple">Warning</Button>
                                <Button bsStyle="danger" className="btn-square mr ripple">Danger</Button>
                                <Button className="btn-square mr btn-inverse ripple">Inverse</Button>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0">Button dropdown</h5>
                            <ButtonToolbar>{BUTTONS.map(this.renderDropdownButton)}</ButtonToolbar>
                        </div>
                    </div>
                    {/* END card */}
                    {/* START card */}
                    <div className="card">
                        <div className="card-body">
                            <h5 className="mt0 ellipsis">Split button dropdown</h5>
                                <ButtonToolbar>{BUTTONS.map(this.renderDropdownButtonSplit)}</ButtonToolbar>
                        </div>
                    </div>
                    {/* END card */}
                </Grid>
            </section>
        );
    }
}

export default Buttons;

