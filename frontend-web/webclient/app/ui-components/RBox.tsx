import * as React from "react";
import { RatingBadge, Box } from "ui-components";
import * as Heading from "ui-components/Heading"
import { ReduxObject, ResponsiveReduxObject } from "DefaultObjects";
import { connect } from 'react-redux'


interface RBoxStateProps {
    responsiveState: ResponsiveReduxObject | undefined
}

interface RBoxProps extends RBoxStateProps {}

// Responsive Box used for dev / testing
const RBox = ({ responsiveState }: RBoxProps) => {
    let message = "";
    if (!responsiveState) {
        message += "undef";
    } else {
        message += `${responsiveState.mediaType}`;

        // if (responsiveState.lessThan.small) {
        //     message += 'Secret message for viewports smaller than than the "small" breakpoint!'
        // } else if (responsiveState.lessThan.medium) {
        //     message += 'Secret message for viewports between the "small" and "medium" breakpoints!'
        // } else {
        //     message += 'Message for viewports greater than the "medium" breakpoint.'
        // }
    }

    return (
        <RatingBadge mx={"3px"} bg={["headerBg", "gray", "red", "green", "purple", "orange"]}>
            <Box textAlign="center">
                <Heading.h6>{message}</Heading.h6>
            </Box>
        </RatingBadge>
    )
}


const mapStateToProps = ({ responsive }: ReduxObject): RBoxStateProps => ({
    responsiveState: responsive
});

export default connect<RBoxStateProps>(mapStateToProps)(RBox);