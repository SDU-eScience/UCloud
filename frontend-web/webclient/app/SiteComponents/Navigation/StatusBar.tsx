import * as React from "react";
import { Link } from "react-router-dom";
import { Button } from "react-bootstrap/lib";
import { updatePageTitle } from "../../Actions/Status";
import { connect } from "react-redux";
import { Status } from "../../types/types";

interface StatusBarProps { status: Status }
const StatusBar = ({ status }: StatusBarProps) => (
    <Link to={"/status"}>
        <Button className={`btn btn-info center-text ${statusToButton(status)}`} title={status.body}>{status.title}</Button>
    </Link>
);

const statusToButton = (status: Status) => {
    switch (status.level) {
        case "NO ISSUES":
            return "bg-green-500";
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return "bg-yellow-500";
        case "ERROR":
            return "bg-red-500";
    }
}

const mapStateToProps = (state: any) => ({ status: state.status.status });
export default connect(mapStateToProps)(StatusBar);