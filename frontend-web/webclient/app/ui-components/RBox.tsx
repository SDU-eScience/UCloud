import * as React from "react";
import RatingBadge from "./RatingBadge";
import Box from "./Box";
import * as Heading from "./Heading"
import {ReduxObject, ResponsiveReduxObject} from "DefaultObjects";
import {connect} from 'react-redux'


interface RBoxStateProps {
    responsiveState: ResponsiveReduxObject | undefined
}

type RBoxProps = RBoxStateProps;

// Responsive Box used for dev / testing
const RBox = ({responsiveState}: RBoxProps) => {
    let message = "";
    if (!responsiveState) {
        message += "undef";
    } else {
        message += `${responsiveState.mediaType}`;
    }

    return (
        <RatingBadge mx={"3px"} bg={["headerBg", "gray", "red", "green", "purple", "orange"]}>
            <Box textAlign="center">
                <Heading.h6>{message}</Heading.h6>
            </Box>
        </RatingBadge>
    )
};


const mapStateToProps = ({responsive}: ReduxObject): RBoxStateProps => ({
    responsiveState: responsive
});

export default connect<RBoxStateProps>(mapStateToProps)(RBox);