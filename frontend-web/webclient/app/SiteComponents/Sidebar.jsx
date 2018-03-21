import React from 'react';
import PropTypes from "prop-types";
import {Link} from 'react-router-dom'
import {SidebarOptionsList} from "../MockObjects";
import './Sidebar.scss';
import { Glyphicon } from "react-bootstrap";


import SidebarRun from './Sidebar.run';

import {Cloud} from '../../authentication/SDUCloudObject'
import {BallPulseLoading} from "./LoadingIcon/LoadingIcon";

class Sidebar extends React.Component {

    constructor(props, context) {
        super(props, context);
        this.state = {
            username: "",
            options: [],
        }
    }

    componentWillMount() {
        this.getUserName();
        this.getUserOptions();
    }

    componentDidMount() {
        SidebarRun();
    }

    getUserName() {
        this.setState(() => ({username: Cloud.userInfo.firstNames}));
    }

    getUserOptions() {
        Cloud.get("/../mock-api/mock_sidebar_options.json").then((res) => {
            this.setState({options: res.response});
        });
    }

    render() {
        return (
            <aside className="sidebar-container">
                <div className="sidebar-header">
                    <div className="pull-right pt-lg text-muted hidden"><em className="ion-close-round"/></div>
                    <a href="#" className="sidebar-header-logo"><img src="img/logo.png" data-svg-replace="img/logo.svg"
                                                                     alt="Logo"/><span
                        className="sidebar-header-logo-text">SDUCloud</span></a>
                </div>
                <div className="sidebar-content">
                    <div className="sidebar-toolbar text-center">
                        <a href=""><img src="/img/user/01.jpg" alt="Profile" className="img-circle thumb64"/></a>
                        <div className="mt">Welcome, {this.state.username}</div>
                    </div>
                    <nav className="sidebar-nav">
                        <SidebarOptions options={this.state.options}/>
                    </nav>
                </div>
            </aside>
        );
    }
}

const SidebarOptions = (props) =>
    !props.options.length ?
        (<BallPulseLoading loading={true}/>) :
        (<ul>
            {props.options.map((option, index) =>
                <SingleSidebarOption key={index} option={option}/>
            )}
        </ul>);

const SingleSidebarOption = (props) => (
    <li>
        <SidebarOption option={props.option}/>
        {props.option.children ? (
            <ul>
                {props.option.children.map((option, i) =>
                    <li key={i}>
                        <SidebarOption option={option}/>
                    </li>)}
            </ul>) : null
        }
    </li>
);

const SidebarOption = ({option}) => {
    if (option.icon || option.href === "#") {
        const arrowRight = option.href === "#" ? <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"/></span> : null;
        return (
            <Link to={option.href}>
                <span className="nav-icon"/>
                <i style={{ color: "#448aff", marginRight: "5px", fontSize: "16px"}} className={option.icon}/> {option.name}
                {arrowRight}
            </Link>
        ); 
    } else {
        return (
            <Link to={option.href}>
                {option.name}
            </Link>
        );
    }
};

Sidebar.contextTypes = {
    router: PropTypes.object,
};

export default Sidebar;
