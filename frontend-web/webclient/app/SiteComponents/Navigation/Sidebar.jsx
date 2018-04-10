import React from "react";
import PropTypes from "prop-types";
import { Link } from "react-router-dom";
import { Glyphicon } from "react-bootstrap";
import SidebarRun from "./Sidebar.run";
import { Cloud } from "../../../authentication/SDUCloudObject";
import { BallPulseLoading } from "../LoadingIcon/LoadingIcon";
import { connect } from "react-redux";
import { fetchSidebarOptions, setSidebarLoading } from "../../Actions/Sidebar";

class Sidebar extends React.Component {
    constructor(props) {
        super(props);
        const { dispatch } = this.props;
        dispatch(setSidebarLoading(true));
        dispatch(fetchSidebarOptions());
    }

    render() {
        const { loading, options } = this.props;
        if (options.length) {
            SidebarRun();
        }
        return (
            <aside className="sidebar-container">
                <div className="sidebar-header">
                    <div className="pull-right pt-lg text-muted hidden"><em className="ion-close-round" /></div>
                    <a href="/app/dashboard" className="sidebar-header-logo">
                        <img src="img/logo.png" data-svg-replace="img/logo.svg" alt="Logo" />
                        <span className="sidebar-header-logo-text">SDUCloud</span>
                    </a>
                </div>
                <div className="sidebar-content">
                    <div className="sidebar-toolbar text-center">
                        <a href=""><img src="/img/user/01.jpg" alt="Profile" className="img-circle thumb64" /></a>
                        <div className="mt">Welcome, {Cloud.userInfo.firstNames}</div>
                    </div>
                    <nav className="sidebar-nav">
                        <SidebarOptions options={options} loading={loading} />
                    </nav>
                </div>
            </aside>
        );
    }
}

const SidebarOptions = ({ options, loading }) =>
    loading ?
        (<BallPulseLoading loading={true} />) :
        (<ul>
            {options.map((option, index) =>
                <SingleSidebarOption key={index} option={option} />
            )}
        </ul>);

const SingleSidebarOption = ({ option }) => (
    <li>
        <SidebarOption option={option} />
        {option.children ? (
            <ul>
                {option.children.map((option, i) =>
                    <li key={i}>
                        <SidebarOption option={option} />
                    </li>)}
            </ul>) : null
        }
    </li>
);

const iconStyle = {
    color: "#448aff",
    marginRight: "5px",
    fontSize: "16px"
};

const SidebarOption = ({ option }) => {
    if (option.icon || option.href === "#") {
        const arrowRight = option.href === "#" ? <span className="pull-right nav-caret"><em className="ion-ios-arrow-right" /></span> : null;
        return (
            <Link to={option.href}>
                <span className="nav-icon" />
                <i style={iconStyle} className={option.icon} /> {option.name}
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

const mapStateToProps = (state) => ({ options, loading } = state.sidebar);
export default connect(mapStateToProps)(Sidebar);