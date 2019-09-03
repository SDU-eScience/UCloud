import {JobState} from "Applications";
import * as React from "react";
import {SpaceProps} from "styled-system";
import Icon, {IconName} from "ui-components/Icon";
import {colors, ThemeColor} from "ui-components/theme";

export const JobStateIcon: React.FunctionComponent<{
    state: JobState,
    size?: number | string,
    color?: ThemeColor
} & SpaceProps> = (props) => {
    let iconName: IconName;
    let defaultColor: ThemeColor = "iconColor";

    switch (props.state) {
        case JobState.VALIDATED:
            iconName = "checkDouble";
            break;
        case JobState.PREPARED:
            iconName = "hourglass";
            break;
        case JobState.SCHEDULED:
            iconName = "calendar";
            break;
        case JobState.RUNNING:
            iconName = "chrono";
            break;
        case JobState.TRANSFER_SUCCESS:
            iconName = "move";
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

    return <Icon
        name={iconName}
        color={color ? colors[color] : undefined}
        size={props.size}
        {...props}
    />;
};
