import {ReduxObject, ResponsiveReduxObject} from "DefaultObjects";
import * as React from "react";
import {connect} from "react-redux";
import Box from "./Box";
import * as Heading from "./Heading";
import RatingBadge from "./RatingBadge";


interface RBoxStateProps {
    responsiveState: ResponsiveReduxObject | undefined;
}

type RBoxProps = RBoxStateProps;

// Responsive Box used for dev / testing
const RBox = ({responsiveState}: RBoxProps) => {
    const message = !responsiveState ? "undef" : `${responsiveState.mediaType}`;

    return (
        <RatingBadge mx={"3px"} bg={["headerBg", "gray", "red", "green", "purple", "orange"]}>
            <Box textAlign="center">
                <Heading.h6>{message}</Heading.h6>
            </Box>
        </RatingBadge>
    );
};


const mapStateToProps = ({responsive}: ReduxObject): RBoxStateProps => ({
    responsiveState: responsive
});

export default connect<RBoxStateProps>(mapStateToProps)(RBox);