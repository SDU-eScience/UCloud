import React from 'react';
import pubsub from 'pubsub-js';
import {Dropdown, MenuItem} from 'react-bootstrap';
import {LinkContainer} from 'react-router-bootstrap';
import { Cloud } from "../../authentication/SDUCloudObject"

import './Header.scss';
import './HeaderMenuLinks.scss';
import StatusBar from "../SiteComponents/StatusBar";

class Header extends React.Component {

    constructor(props) {
        super(props);
        this.state = {
            pageTitle: ''
        };
    }

    componentWillMount() {
        this.pubsub_token = pubsub.subscribe('setPageTitle', (ev, title) => {
            this.setState({pageTitle: title});
        });
    }

    componentWillUnmount() {
        pubsub.unsubscribe(this.pubsub_token);
    }

    showSettings() {
        pubsub.publish('showsettings');
    }

    render() {
        const ddMenuItem = (<span>
                                <em className="ion-person"/><sup className="badge bg-danger">3</sup>
                            </span>);

        return (
            <header className="header-container">
                <nav>
                    <ul className="visible-xs visible-sm">
                        <li><a id="sidebar-toggler" href="#"
                               className="menu-link menu-link-slide"><span><em/></span></a></li>
                    </ul>
                    <ul className="hidden-xs">
                        <li><a id="offcanvas-toggler" href="#" className="menu-link menu-link-slide"><span><em/></span></a>
                        </li>
                    </ul>
                    <h2 className="header-title">{this.state.pageTitle}</h2>

                    <ul className="pull-right">
                        <li>
                            <StatusBar/>
                        </li>
                        <Dropdown id="basic-nav-dropdown" pullRight componentClass="li">
                            <Dropdown.Toggle useAnchor noCaret className="has-badge ripple">
                                <em className="ion-person"/>
                                <sup className="badge bg-danger"/>
                            </Dropdown.Toggle>
                            <Dropdown.Menu className="md-dropdown-menu">
                                <MenuItem onClick={() => {Cloud.logout()}}><em className="ion-log-out icon-fw"/>Logout</MenuItem>
                            </Dropdown.Menu>
                        </Dropdown>
                        <li>
                            <a href="#" className="ripple" onClick={this.showSettings}>
                                <em className="ion-gear-b"/>
                            </a>
                        </li>
                    </ul>

                </nav>
            </header>
        );
    }
}

export default Header;
