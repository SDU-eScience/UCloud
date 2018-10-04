import * as React from "react";
import { connect } from "react-redux";
import { updatePageTitle } from "./Redux/StatusActions";
import { Segment } from "semantic-ui-react";
import { statusToColor } from "Navigation/Status";
import { Dispatch } from "redux";

const Status = ({ status, updatePageTitle }) => {
    updatePageTitle();
    return (
        <React.StrictMode>
            <Segment size="huge" content={status.title} color={statusToColor(status.level)} />
            <Segment padded="very" content={status.body} />
        </React.StrictMode>
    );
};

const mapDispatchToProps = (dispatch: Dispatch) => ({ updatePageTitle: () => dispatch(updatePageTitle("System Status")) });
const mapStateToProps = ({ status }) => ({ status: status.status });
export default connect(mapStateToProps, mapDispatchToProps)(Status);