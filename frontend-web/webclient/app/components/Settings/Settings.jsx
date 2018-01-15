import React from 'react';
import ReactDOM from 'react-dom';
import { Modal } from 'react-bootstrap';
import pubsub from 'pubsub-js';

import initSettings from './Settings.run';
import {initScreenfull} from '../Utils/Utils';
import './Settings.scss';

class Settings extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            showModal: false
        };
        // Used to update the language dropdown
        this.valueSelected = 'English'
    }

    componentDidMount() {
        initSettings();
        // https://github.com/facebook/react/issues/5053
        setTimeout(initScreenfull, 2000);
    }

    componentWillMount() {
        this.pubsub_token = pubsub.subscribe('showsettings', () => {
            this.open();
        });
    }

    componentWillUnmount() {
        pubsub.unsubscribe(this.pubsub_token);
    }

    onShowModal() {
        // Add class for white backdrop
        $('body').addClass('modal-backdrop-soft');
    }

    onHiddenModal() {
        // Remove class for white backdrop (if not will affect future modals)
        $('body').removeClass('modal-backdrop-soft');
    }

    close() {
        this.setState({ showModal: false });
    }

    open() {
        this.setState({ showModal: true });
    }

    setValueSelected(val) {
        this.valueSelected = val;
    }

    render() {
        return (
            <Modal show={this.state.showModal} bsSize="lg" onHide={this.close.bind(this)} className="modal modal-right fade modal-settings"
                onEnter={this.onShowModal.bind(this)} onExited={this.onHiddenModal.bind(this)}>
                <Modal.Header closeButton={true}>
                    <h4 className="modal-title"><span>Settings</span></h4>
                </Modal.Header>
                <Modal.Body>
                    <div className="clearfix mb">
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-1">
                                    <input type="radio" defaultChecked name="setting-theme" value="0" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-2">
                                    <input type="radio" name="setting-theme" value="1" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-3">
                                    <input type="radio" name="setting-theme" value="2" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-4">
                                    <input type="radio" name="setting-theme" value="3" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-5">
                                    <input type="radio" name="setting-theme" value="4" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-6">
                                    <input type="radio" name="setting-theme" value="5" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-7">
                                    <input type="radio" name="setting-theme" value="6" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-8">
                                    <input type="radio" name="setting-theme" value="7" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                        <div className="pull-left wd-tiny mb">
                            <div className="setting-color">
                                <label className="preview-theme-9">
                                    <input type="radio" name="setting-theme" value="8" /><span className="ion-checkmark-round"></span>
                                    <div className="t-grid">
                                        <div className="t-row">
                                            <div className="t-col preview-header"></div>
                                            <div className="t-col preview-sidebar"></div>
                                            <div className="t-col preview-content"></div>
                                        </div>
                                    </div>
                                </label>
                            </div>
                        </div>
                    </div>
                    <hr />
                    <p>
                        <label className="mda-checkbox">
                            <input id="sidebar-showheader" type="checkbox" defaultChecked /><em className="bg-indigo-500"></em>Sidebar header
                        </label>
                    </p>
                    <p>
                        <label className="mda-checkbox">
                            <input id="sidebar-showtoolbar" type="checkbox" defaultChecked /><em className="bg-indigo-500"></em>Sidebar toolbar
                        </label>
                    </p>
                    <p>
                        <label className="mda-checkbox">
                            <input id="sidebar-offcanvas" type="checkbox" /><em className="bg-indigo-500"></em>Sidebar offcanvas
                        </label>
                    </p>
                    <hr />
                    <p>Navigation icon</p>
                    <p>
                        <label className="mda-radio">
                            <input type="radio" name="headerMenulink" value="menu-link-close" /><em className="bg-indigo-500"></em>Close icon
                        </label>
                    </p>
                    <p>
                        <label className="mda-radio">
                            <input type="radio" name="headerMenulink" value="menu-link-slide" defaultChecked/><em className="bg-indigo-500"></em>Slide arrow
                        </label>
                    </p>
                    <p>
                        <label className="mda-radio">
                            <input type="radio" name="headerMenulink" value="menu-link-arrow" /><em className="bg-indigo-500"></em>Big arrow
                        </label>
                    </p>
                    <hr />
                    <button type="button" data-toggle-fullscreen="" className="btn btn-default btn-raised">Toggle fullscreen</button>
                    <hr />
                    <p>Change language</p>
                    {/* START Language list */}
                    <div className="btn-group">
                        <button type="button" data-toggle="dropdown" className="btn btn-default btn-block btn-raised">{this.valueSelected}</button>
                        <ul role="menu" className="dropdown-menu dropdown-menu-right animated fadeInUpShort">
                            <li><a href="#" data-set-lang="en" onClick={() => this.setValueSelected('English')}>English</a></li>
                            <li><a href="#" data-set-lang="es" onClick={() => this.setValueSelected('Spanish')}>Spanish</a></li>
                        </ul>
                    </div>
                    {/* END Language list */}
                </Modal.Body>
            </Modal>
        );
    }
}

export default Settings;



