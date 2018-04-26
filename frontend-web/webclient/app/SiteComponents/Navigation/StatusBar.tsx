import * as React from "react";
import { Link } from "react-router-dom";
import { Button } from "semantic-ui-react";
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
            return "green";
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return "yellow";
        case "ERROR":
            return "red";
    }
}

const mapStateToProps = (state: any) => ({ status: state.status.status });
export default connect(mapStateToProps)(StatusBar);