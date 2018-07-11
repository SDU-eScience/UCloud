import * as React from "react";
import { Icon } from "semantic-ui-react";

export const FileIcon = ({ name, size, link = false, className = "", color }) =>
    link ?
        <Icon.Group className={className} size={size}>
            <Icon name={name} color={color} />
            <Icon corner color="grey" name="share" />
        </Icon.Group> :
        <Icon name={name} size={size} color={color} />

export const RefreshButton = ({ loading, onClick }) => (
    <Icon
        className="float-right"
        size="small"
        link
        circular
        name="sync"
        onClick={() => onClick()} loading={loading}
    />
);