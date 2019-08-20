import {AppState} from "Applications";
import * as React from "react";
import {SpaceProps} from "styled-system";
import Icon, {IconName} from "ui-components/Icon";
import {colors, ThemeColor} from "ui-components/theme";

export const JobStateIcon: React.FunctionComponent<{
    state: AppState,
    size?: number | string,
    color?: ThemeColor
} & SpaceProps> = (props) => {
    let iconName: IconName;
    let defaultColor: ThemeColor = "iconColor";

    switch (props.state) {
        case AppState.VALIDATED:
            iconName = "checkDouble";
            break;
        case AppState.PREPARED:
            iconName = "hourglass";
            break;
        case AppState.SCHEDULED:
            iconName = "calendar";
            break;
        case AppState.RUNNING:
            iconName = "chrono";
            break;
        case AppState.TRANSFER_SUCCESS:
            iconName = "move";
            break;
        case AppState.SUCCESS:
            iconName = "check";
            defaultColor = "green";
            break;
        case AppState.FAILURE:
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
