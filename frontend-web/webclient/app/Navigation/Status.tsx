import * as React from "react";
import { Link } from "react-router-dom";
import { Button, SemanticCOLORS } from "semantic-ui-react";
import { connect } from "react-redux";
import { Status, StatusLevel } from ".";

interface StatusProps { status: Status }
const Status = ({ status }: StatusProps) => (
    <Button
        className="center-text"
        fluid
        color={statusToColor(status.level)}
        as={Link}
        to={"/status"}
        content={status.title}
        title={status.body}
    />
);

export const statusToColor = (level: StatusLevel): SemanticCOLORS => {
    switch (level) {
        case "NO ISSUES":
            return "green";
        case "MAINTENANCE":
        case "UPCOMING MAINTENANCE":
            return "yellow";
        case "ERROR":
            return "red";
    }
}

const mapStateToProps = (state) => ({ status: state.status.status });
export default connect(mapStateToProps)(Status);