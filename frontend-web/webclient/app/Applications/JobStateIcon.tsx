import {JobState} from "Applications";
import * as React from "react";
import {SpaceProps} from "styled-system";
import Icon, {IconName} from "ui-components/Icon";
import {colors, ThemeColor} from "ui-components/theme";

export const JobStateIcon: React.FunctionComponent<{
    state: JobState;
    isExpired: boolean;
    size?: number | string;
    color?: ThemeColor;
} & SpaceProps> = ({isExpired, ...props}) => {
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
        case JobState.IN_QUEUE:
            iconName = "calendar";
            break;
        case JobState.RUNNING:
            iconName = "chrono";
            defaultColor = "purple";
            break;
        case JobState.READY:
            iconName = "chrono";
            defaultColor = "green";
            break;
        case JobState.SUCCESS:
            iconName = "check";
            defaultColor = "green";
            break;
        case JobState.FAILURE:
            iconName = "close";
            defaultColor = "red";
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
