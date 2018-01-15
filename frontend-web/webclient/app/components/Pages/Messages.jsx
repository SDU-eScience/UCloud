import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button, Dropdown, MenuItem, Modal } from 'react-bootstrap';

import MessagesRun from './Messages.run';

class Messages extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    constructor(props) {
        super(props);
        this.state = {
            showModalMsg: false,
            showModalNew: false
        };
    }

    closeMsg() {
        this.setState({ showModalMsg: false });
    }

    openMsg() {
        this.setState({ showModalMsg: true });
    }

    closeNew() {
        this.setState({ showModalNew: false });
    }

    openNew() {
        this.setState({ showModalNew: true });
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <p className="ph">Today</p>
                    <div className="card">
                        <table className="table table-hover table-fixed va-middle">
                            <tbody>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)} className="wd-xxs">
                                        <div className="initial32 bg-indigo-500">E</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis wd-sm">Erin Alvarez</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Cras nunc velit, bibendum vel imperdiet eget, posuere nec diam.</td>
                                    <td className="wd-xxs">
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd1">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/04.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd2">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-green-500">K</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Kelly Hernandez</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Integer sit amet vulputate mauris. Praesent nulla est, molestie at auctor vulputate, semper eu sapien. Fusce dolor lacus, interdum eu tincidunt sed, aliquet vel turpis.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd3">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/06.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd4">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/05.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Oscar Stanley</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd5">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <p className="ph">Yesterday</p>
                    <div className="card">
                        <table className="table table-hover table-fixed va-middle">
                            <tbody>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)} className="wd-xxs">
                                        <div className="initial32 bg-blue-500">T</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis wd-sm">Timmothy Terry</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Mauris hendrerit nibh eu nisi congue dapibus.</td>
                                    <td className="wd-xxs">
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd6">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/01.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd7">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-green-500">N</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nina Brown</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Etiam eu ipsum dui, et tincidunt orci. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd8">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/06.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd9">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/03.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Myrtle James</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd10">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-amber-500">T</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Ted Vasquez</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent vel nisi nibh.Integer imperdiet, turpis nec viverra egestas, magna sapien convallis velit, nec vestibulum turpis dui vel dui.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd11">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-pink-500">L</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Lillian Snyder</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent vel nisi nibh.Integer imperdiet, turpis nec viverra egestas, magna sapien convallis velit, nec vestibulum turpis dui vel dui.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd12">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    <p className="ph">2 days ago</p>
                    <div className="card">
                        <table className="table table-hover table-fixed va-middle">
                            <tbody>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)} className="wd-xxs">
                                        <div className="initial32 bg-blue-500">T</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis wd-sm">Timmothy Terry</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Vestibulum ante ipsum primis in faucibus orci luctus et ultrices posuere cubilia Curae; Mauris hendrerit nibh eu nisi congue dapibus.</td>
                                    <td className="wd-xxs">
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd13">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/01.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd14">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-green-500">N</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nina Brown</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Etiam eu ipsum dui, et tincidunt orci. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd15">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/06.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Nathan Richardson</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd16">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}><img src="img/user/03.jpg" alt="user" className="img-circle thumb32"/></td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Myrtle James</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent leo quam, faucibus at facilisis id, rhoncus sit amet metus. Sed pretium malesuada leo sit amet convallis. Phasellus dolor velit, dictum a pulvinar eu, convallis a ante.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd17">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-amber-500">T</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Ted Vasquez</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent vel nisi nibh.Integer imperdiet, turpis nec viverra egestas, magna sapien convallis velit, nec vestibulum turpis dui vel dui.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd18">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                                <tr className="msg-display clickable">
                                    <td onClick={this.openMsg.bind(this)}>
                                        <div className="initial32 bg-pink-500">L</div>
                                    </td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Lillian Snyder</td>
                                    <td onClick={this.openMsg.bind(this)} className="text-ellipsis">Praesent vel nisi nibh.Integer imperdiet, turpis nec viverra egestas, magna sapien convallis velit, nec vestibulum turpis dui vel dui.</td>
                                    <td>
                                        {/* START dropdown */}
                                        <div className="pull-right">
                                            <Dropdown pullRight id="dd19">
                                                <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                                  <em className="ion-android-more-vertical"></em>
                                                </Dropdown.Toggle>
                                                <Dropdown.Menu className="md-dropdown-menu" >
                                                    <MenuItem eventKey="1"><em className="ion-reply icon-fw"></em>Reply</MenuItem>
                                                    <MenuItem eventKey="2"><em className="ion-forward icon-fw"></em>Forward</MenuItem>
                                                    <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Spam</MenuItem>
                                                </Dropdown.Menu>
                                            </Dropdown>
                                        </div>
                                        {/* END dropdown */}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                    {/* Floating button for compose */}
                    <div className="floatbutton">
                        <ul className="mfb-component--br mfb-zoomin">
                            <li className="mfb-component__wrap">
                                <a onClick={this.openNew.bind(this)} id="compose" href="#" className="mfb-component__button--main">
                                    <i className="mfb-component__main-icon--resting ion-edit"></i>
                                    <i className="mfb-component__main-icon--active ion-edit"></i></a>
                                <ul className="mfb-component__list"></ul>
                            </li>
                        </ul>
                    </div>

                    {/* Modal content example for messages */}
                    <Modal show={this.state.showModalMsg} onHide={this.closeMsg.bind(this)} className="modal-right modal-auto-size">
                        <Modal.Header closeButton>
                            {/*<Modal.Title>Modal heading</Modal.Title>*/}
                            <div className="media m0 pv">
                                <div className="media-left"><a href="#"><img src="img/user/04.jpg" alt="User" className="media-object img-circle thumb64"/></a></div>
                                <div className="media-body media-middle">
                                    <h4 className="media-heading">Patrick Brooks</h4><span className="text-muted">patrick.brooks@mail.com</span>
                                </div>
                            </div>
                        </Modal.Header>
                        <Modal.Body>
                            {/* START dropdown */}
                            <div className="pull-right">
                                <Dropdown pullRight id="dd20">
                                    <Dropdown.Toggle noCaret className="btn-flat btn-flat-icon">
                                      <em className="ion-android-more-vertical"></em>
                                    </Dropdown.Toggle>
                                    <Dropdown.Menu className="md-dropdown-menu" >
                                        <MenuItem eventKey="1"><em className="ion-star icon-fw"></em>Favorite</MenuItem>
                                        <MenuItem eventKey="2"><em className="ion-ios-box icon-fw"></em>Archive</MenuItem>
                                        <MenuItem eventKey="3"><em className="ion-trash-a icon-fw"></em>Discard</MenuItem>
                                    </Dropdown.Menu>
                                </Dropdown>
                            </div>
                            {/* END dropdown */}
                            <p className="text-muted">26 aug 2015 10:30 am</p>
                            <h4 className="mt">Nam vel tristique dolor.</h4>
                            <div className="reader-block">
                                <p>Praesent vel nisi nibh. Vestibulum purus ipsum, rutrum varius aliquam id, rhoncus eget neque. Curabitur sodales nisl eu enim suscipit eu faucibus dui mattis.</p>
                                <p>Aenean risus nulla, aliquam sed aliquam vitae, ultricies non elit. Suspendisse nunc massa, euismod eu egestas quis, molestie sit amet mauris. Mauris eu lacus massa, vel condimentum lectus. Quisque quam justo, cursus sit amet pretium vel, viverra vel leo. Nullam lobortis consectetur hendrerit. Aenean rhoncus, est vel vestibulum tristique, ante lectus elementum augue, eu dictum turpis dui ut ipsum. Lorem ipsum dolor sit amet, consectetur adipiscing elit.</p>
                                <p>Pellentesque ac ligula varius nisl laoreet pretium quis quis tellus. Praesent et mauris lacus, non volutpat augue.</p>
                            </div>
                        </Modal.Body>
                        <Modal.Footer>
                            <div className="text-left">
                                <p>Reply</p>
                            </div>
                            <div className="media m0 pv">
                                <div className="media-left"><a href="#"><img src="img/user/01.jpg" alt="User" className="media-object img-circle thumb32"/></a></div>
                                <div className="media-body media-middle">
                                    <form action="">
                                        <div className="mda-form-group">
                                            <div className="mda-form-control pt0">
                                                <textarea rows="3" aria-multiline="true" tabIndex="0" aria-invalid="false" placeholder="Write here..." className="form-control"></textarea>
                                                <div className="mda-form-control-line"></div>
                                            </div>
                                        </div>
                                    </form>
                                </div>
                            </div>
                            <Button onClick={this.closeMsg.bind(this)} bsStyle="success">Send</Button>
                        </Modal.Footer>
                    </Modal>
                    {/* End Modal content example for messages */}

                    {/* Modal content example for compose */}
                    <Modal show={this.state.showModalNew} onHide={this.closeNew.bind(this)}>
                        <Modal.Body>
                            <form action="">
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <input rows="3" aria-multiline="true" tabIndex="0" aria-invalid="false" className="form-control"/>
                                        <div className="mda-form-control-line"></div>
                                        <label>To:</label>
                                    </div>
                                </div>
                                <div className="mda-form-group">
                                    <div className="mda-form-control">
                                        <textarea rows="3" aria-multiline="true" tabIndex="0" aria-invalid="false" className="form-control"></textarea>
                                        <div className="mda-form-control-line"></div>
                                        <label>Message</label>
                                    </div>
                                </div>
                                <Button onClick={this.closeNew.bind(this)} bsStyle="success">Send</Button>
                            </form>
                        </Modal.Body>
                    </Modal>
                    {/* End Modal content example for compose */}
                </div>
            </section>
        );
    }
}

export default Messages;
