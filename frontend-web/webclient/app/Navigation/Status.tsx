import * as React from "react";
import { Link } from "react-router-dom";
import { connect } from "react-redux";
import { Status, StatusLevel } from ".";
import { Button } from "ui-components";

interface StatusProps { status: Status }
const Status = ({ status }: StatusProps) => (
    <Link to={"/status"}>
        <Button color={statusToColor(status.level)} title={status.body}>{status.title}</Button>
    </Link>
);

export const statusToColor = (level: StatusLevel): "green" | "yellow" | "red" => {
    switch (level) {
        case "NO ISSUES":
            return "green";
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return "yellow";
        case "ERROR":
            return "red";
    }
};

const mapStateToProps = (state) => ({ status: state.status.status });
export default connect(mapStateToProps)(Status);