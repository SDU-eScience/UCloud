import * as React from "react";
import { AppState } from "Applications";
import Icon, { IconName, IconProps } from "ui-components/Icon";
import { ThemeColor, colors } from "ui-components/theme";
import { SpaceProps } from "styled-system";

export const JobStateIcon: React.StatelessComponent<{ state: AppState, size?: number | string, color?: ThemeColor } & SpaceProps> = (props) => {
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
            iconName = "ellipsis"
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