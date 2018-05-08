import * as React from "react";
import { Icon } from "semantic-ui-react";

export const FileIcon = ({ name, size, link, color }) =>
    link ?
        <Icon.Group size={size}>
            <Icon name={name} color={color} />
            <Icon corner color="grey" name="share" />
        </Icon.Group> :
        <Icon name={name} size={size} color={color} />