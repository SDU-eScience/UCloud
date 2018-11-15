import * as React from "react";
import { connect } from "react-redux";
import { Dispatch } from "redux";

class Projects extends React.Component<any, any> {

    componentDidMount() {
        //fetch projects
    }

    render() {
        return null;
    }
}

const mapStateToProps = (state) => ({});
const mapDispatchToProps = (dispatch: Dispatch) => ({});

export default connect(mapStateToProps, mapDispatchToProps)(Projects);