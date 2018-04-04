import React from "react";
import {Dropdown, MenuItem} from "react-bootstrap";
import { Cloud } from "../../authentication/SDUCloudObject"
import { connect } from "react-redux";
import './Header.scss';
import './HeaderMenuLinks.scss';
import StatusBar from "../SiteComponents/StatusBar";


const Header = ({ title }) => (
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
            <h2 className="header-title">{title}</h2>
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
                        <MenuItem onClick={() => Cloud.logout()}><em className="ion-log-out icon-fw"/>Logout</MenuItem>
                    </Dropdown.Menu>
                </Dropdown>
            </ul>
        </nav>
    </header>
);

const mapStateToProps = (state) => ({ title: state.status.title })

export default connect(mapStateToProps)(Header);
