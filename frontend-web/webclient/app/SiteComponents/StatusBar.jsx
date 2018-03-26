import React from "react";
import PropTypes from "prop-types";
import { Link } from "react-router-dom";
import { Button } from "react-bootstrap";
import { updatePageTitle } from '../Actions/Status';
import { connect } from "react-redux";

const StatusBar = ({ status, dispatch }) => {
    return (
        <Link to={"/status"}>
            <Button className={"btn btn-info center-text " + statusToButton(status)} title={status.body}>{status.title}</Button>
        </Link>);
}

const statusToButton = (status) => {
    switch (status.level) {
        case "NO ISSUES":
            return 'bg-green-500';
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return 'bg-yellow-500';
        case "ERROR":
            return 'bg-red-500';
    }
}

StatusBar.propTypes = {
    status: PropTypes.object.isRequired
}

const mapStateToProps = (state) => ({ status: state.status.status });

export default connect(mapStateToProps)(StatusBar);