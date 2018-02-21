import React from 'react';
import PropTypes from "prop-types";
import {Link} from 'react-router-dom'
import { SidebarOptionsList } from "../MockObjects";
import './Sidebar.scss';


import SidebarRun from './Sidebar.run';

import { Cloud } from '../../authentication/SDUCloudObject'
import {BallPulseLoading} from "./LoadingIcon";

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
        Cloud.get("/../mock-api/mock_sidebar_options.json").then((sidebarOptions) => {
            this.setState({options: sidebarOptions});
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

function SidebarOptions(props) {
    if (!props.options.length) return (<BallPulseLoading loading={true}/>);
    let i = 0;
    let optionsList = props.options.map(option =>
        <SingleSidebarOption key={i++} option={option}/>
    );
    return (
        <ul>
            {optionsList}
        </ul>
    )

}

function SingleSidebarOption(props) {
    if (props.option.href) {
        return (
            <li>
                <Link to={props.option.href} className="ripple">
                    <span className="pull-right nav-label"/><span
                    className="nav-icon"/>
                    <span>{props.option.name}</span>
                </Link>
            </li>)
    } else { // We have children we need to render
        let children = props.option.children.slice();
        let childrenHrefs = [];
        children.forEach((it) => {
            childrenHrefs.push(it.name);
        });
        let i = 0;
        let optionsList = children.map(option =>
            <li key={i++}>
                <NestedSidebarOption option={option}/>
            </li>
        );

        return (
            <li>
                    <a href="#" className="ripple">
                        <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"/></span><span
                        className="pull-right nav-label"/><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/connection-bars.svg" alt="MenuItem"
                                         className="hidden"/></span>
                        <span>{props.option.name}</span>
                    </a>
                <ul>
                    {optionsList}
                </ul>
            </li>
        )
    }
}

function NestedSidebarOption(props) {
    return (
        <Link to={props.option.href} className="ripple">
            <span className="pull-right nav-label"/><span>{props.option.name}</span>
        </Link>
    )
}

Sidebar.contextTypes = {
    router: PropTypes.object,
};

export default Sidebar;
