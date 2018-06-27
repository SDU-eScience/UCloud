import * as React from "react";
import { connect } from "react-redux";
import { updatePageTitle } from "../../Actions/Status";
import { SemanticCOLORS, Segment, Header } from "semantic-ui-react";

const Status = ({ status, updatePageTitle }) => {
    updatePageTitle();
    return (
        <React.StrictMode>
            <Segment color={levelToColor(status.level)}>
                <Header as="h2">
                    {status.title} 
                </Header>
            </Segment>
            <Segment padded="very" content={status.body} />
        </React.StrictMode>
    );
};

const levelToColor = (level: string): SemanticCOLORS => {
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

const mapDispatchToProps = (dispatch) => ({ updatePageTitle: () => dispatch(updatePageTitle("System Status")) });
const mapStateToProps = (state) => ({ status: state.status.status });
export default connect(mapStateToProps, mapDispatchToProps)(Status);