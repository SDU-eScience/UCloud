import * as React from "react";
import {SpaceProps} from "styled-system";
import Icon, {IconName} from "ui-components/Icon";
import {colors, ThemeColor} from "ui-components/theme";
import {JobState} from "Applications/Jobs/index";

export const JobStateIcon: React.FunctionComponent<{
    state?: JobState;
    isExpired: boolean;
    size?: number | string;
    color?: ThemeColor;
} & SpaceProps> = ({isExpired, ...props}) => {
    if (!props.state) return null;
    let iconName: IconName;
    // let defaultColor: ThemeColor = "iconColor";
    let defaultColor;

    if (isExpired) {
        return (
            <Icon
                name="chrono"
                color="orange"
                size={props.size}
                {...props}
            />
        );
    }

    switch (props.state) {
        case "IN_QUEUE":
            iconName = "calendar";
            break;
        case "RUNNING":
            iconName = "chrono";
            break;
        /*
        case JobState.READY:
            iconName = "chrono";
            defaultColor = "green";
            break;
         */
        case "SUCCESS":
            iconName = "check";
            defaultColor = "green";
            break;
        case "FAILURE":
            iconName = "close";
            defaultColor = "red";
            break;
        case "EXPIRED":
            iconName = "chrono";
            defaultColor = "orange";
            break;
        default:
            iconName = "ellipsis";
            break;
    }

    const color = props.color !== undefined ? props.color : defaultColor;

    return (
        <Icon
            name={iconName}
            color={color ? colors[color] : undefined}
            size={props.size}
            {...props}
        />
    );
};
