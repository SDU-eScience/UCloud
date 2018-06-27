import * as React from "react";
import { Link } from "react-router-dom";
import { Button } from "semantic-ui-react";
import { connect } from "react-redux";
import { Status } from "../../types/types";

interface StatusProps { status: Status }
const Status = ({ status }: StatusProps) => (
    <Button 
        className={`btn btn-info center-text ${statusToButton(status)}`}
        fluid
        as={Link}
        to={"/status"}
        content={status.title}
        title={status.body}
    />
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

const mapStateToProps = (state) => ({ status: state.status.status });
export default connect(mapStateToProps)(Status);