import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button, Dropdown, MenuItem } from 'react-bootstrap';

class Wall extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-overlap bg-indigo-500">
                    <div className="media m0 pv">
                        <div className="media-left"><a href="#"><img src="img/user/01.jpg" alt="User" className="media-object img-circle thumb64"/></a></div>
                        <div className="media-body media-middle">
                            <h4 className="media-heading">Christy Griffin</h4><span className="text-muted">Aliquam viverra nibh at ipsum dapibus pulvinar et eu ligula.</span>
                        </div>
                    </div>
                </div>
                <div className="container-lg">
                    <Row>
                        <Col md={8}>
                            <div className="card">
                                <div className="card-body">
                                    <form action="" className="mt">
                                        <div className="input-group mda-input-group">
                                            <div className="mda-form-group">
                                                <div className="mda-form-control">
                                                    <textarea rows="1" aria-multiline="true" tabIndex="0" aria-invalid="false" className="no-resize form-control"></textarea>
                                                    <div className="mda-form-control-line"></div>
                                                    <label className="m0">What's on your mind?</label>
                                                </div><span className="mda-form-msg right">Any message here</span>
                                            </div><span className="input-group-btn">
                                                <button type="button" className="btn btn-flat btn-success btn-circle"><em className="ion-checkmark-round"></em></button></span>
                                        </div>
                                    </form>
                                </div>
                                <div className="card-body">
                                    {/* Inner card */}
                                    <div className="card">
                                        <div className="card-heading">
                                            {/* START dropdown */}
                                            <div className="pull-right">
                                                <Dropdown pullRight id="dd1">
                                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                      <em className="ion-android-more-vertical"></em>
                                                    </Dropdown.Toggle>
                                                    <Dropdown.Menu className="md-dropdown-menu" >
                                                        <MenuItem eventKey="1">Hide post</MenuItem>
                                                        <MenuItem eventKey="2">Stop following</MenuItem>
                                                        <MenuItem divider/>
                                                        <MenuItem eventKey="3">Report</MenuItem>
                                                    </Dropdown.Menu>
                                                </Dropdown>
                                            </div>
                                            {/* END dropdown */}
                                            <div className="media m0">
                                                <div className="media-left"><a href="#"><img src="img/user/06.jpg" alt="User" className="media-object img-circle thumb48"/></a></div>
                                                <div className="media-body media-middle pt-sm">
                                                    <p className="media-heading m0 text-bold">Stephen Palmer</p><small className="text-muted"><em className="ion-earth text-muted mr-sm"></em><span>2 hours</span></small>
                                                </div>
                                            </div>
                                            <div className="p">Ut egestas consequat faucibus. Donec id lectus tortor. Maecenas at porta purus. Etiam feugiat risus massa. Vivamus fermentum libero vel felis aliquet interdum. </div>
                                        </div>
                                        <div className="card-footer">
                                            <button type="button" className="btn btn-flat btn-primary">Like</button>
                                            <button type="button" className="btn btn-flat btn-primary">Share</button>
                                            <button type="button" className="btn btn-flat btn-primary">Comment</button>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body">
                                    {/* Inner card */}
                                    <div className="card">
                                        <div className="card-heading">
                                            {/* START dropdown */}
                                            <div className="pull-right">
                                                <Dropdown pullRight id="dd2">
                                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                      <em className="ion-android-more-vertical"></em>
                                                    </Dropdown.Toggle>
                                                    <Dropdown.Menu className="md-dropdown-menu" >
                                                        <MenuItem eventKey="1">Hide post</MenuItem>
                                                        <MenuItem eventKey="2">Stop following</MenuItem>
                                                        <MenuItem divider/>
                                                        <MenuItem eventKey="3">Report</MenuItem>
                                                    </Dropdown.Menu>
                                                </Dropdown>
                                            </div>
                                            {/* END dropdown */}
                                            <div className="media m0">
                                                <div className="media-left"><a href="#"><img src="img/user/05.jpg" alt="User" className="media-object img-circle thumb48"/></a></div>
                                                <div className="media-body media-middle pt-sm">
                                                    <p className="media-heading m0 text-bold">Ricky Wagner</p><small className="text-muted"><em className="ion-earth text-muted mr-sm"></em><span>10 hours</span></small>
                                                </div>
                                            </div>
                                        </div>
                                        <div className="card-item"><img src="img/pic6.jpg" alt="MaterialImg" className="fw img-responsive"/>
                                            <div className="card-item-text bg-transparent">
                                                <p>The sun was shinning</p>
                                            </div>
                                        </div>
                                        <div className="card-footer">
                                            <button type="button" className="btn btn-flat btn-primary">Like</button>
                                            <button type="button" className="btn btn-flat btn-primary">Share</button>
                                            <button type="button" className="btn btn-flat btn-primary">Comment</button>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-body">
                                    {/* Inner card */}
                                    <div className="card reader-block">
                                        <div className="card-heading">
                                            {/* START dropdown */}
                                            <div className="pull-right">
                                                <Dropdown pullRight id="dd3">
                                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                      <em className="ion-android-more-vertical"></em>
                                                    </Dropdown.Toggle>
                                                    <Dropdown.Menu className="md-dropdown-menu" >
                                                        <MenuItem eventKey="1">Hide post</MenuItem>
                                                        <MenuItem eventKey="2">Stop following</MenuItem>
                                                        <MenuItem divider/>
                                                        <MenuItem eventKey="3">Report</MenuItem>
                                                    </Dropdown.Menu>
                                                </Dropdown>
                                            </div>
                                            {/* END dropdown */}
                                            <div className="media m0">
                                                <div className="media-left"><a href="#"><img src="img/user/06.jpg" alt="User" className="media-object img-circle thumb48"/></a></div>
                                                <div className="media-body media-middle pt-sm">
                                                    <p className="media-heading m0 text-bold">Stephen Palmer</p><small className="text-muted"><em className="ion-earth text-muted mr-sm"></em><span>Yesterday</span></small>
                                                </div>
                                            </div>
                                            <div className="p">
                                                <div className="mb">Donec a purus auctor dui hendrerit accumsan non quis augue nisl sed iaculis.</div><a href="#"><img src="img/pic1.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic2.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic3.jpg" alt="Pic" className="mr-sm thumb48"/></a><a href="#"><img src="img/pic4.jpg" alt="Pic" className="mr-sm thumb48"/></a>
                                            </div>
                                        </div>
                                        <div className="card-footer">
                                            <button type="button" className="btn btn-flat btn-primary">Like</button>
                                            <button type="button" className="btn btn-flat btn-primary">Share</button>
                                            <button type="button" className="btn btn-flat btn-primary">Comment</button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                        <Col md={4}>
                            <div className="push-down"></div>
                            <div className="card card-transparent">
                                <h5 className="card-heading">Friends</h5>
                                <div className="mda-list">
                                    <div className="mda-list-item"><img src="img/user/01.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Eric Graves</a></h3>
                                            <div className="text-muted text-ellipsis">Ut ac nisi id mauris</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/02.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Bruce Ramos</a></h3>
                                            <div className="text-muted text-ellipsis">Sed lacus nisl luctus</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/03.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Marie Hall</a></h3>
                                            <div className="text-muted text-ellipsis">Donec congue sagittis mi</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/04.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Russell Hart</a></h3>
                                            <div className="text-muted text-ellipsis">Donec convallis arcu sit</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/05.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Eric Graves</a></h3>
                                            <div className="text-muted text-ellipsis">Ut ac nisi id mauris</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/06.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Jessie Cox</a></h3>
                                            <div className="text-muted text-ellipsis">Sed lacus nisl luctus</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/01.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Jonathan Soto</a></h3>
                                            <div className="text-muted text-ellipsis">Donec congue sagittis mi</div>
                                        </div>
                                    </div>
                                    <div className="mda-list-item"><img src="img/user/05.jpg" alt="List user" className="mda-list-item-img thumb48"/>
                                        <div className="mda-list-item-text mda-2-line">
                                            <h3><a href="#">Guy Carpenter</a></h3>
                                            <div className="text-muted text-ellipsis">Donec convallis arcu sit</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </Col>
                    </Row>
                </div>
            </section>
        );
    }
}

export default Wall;
