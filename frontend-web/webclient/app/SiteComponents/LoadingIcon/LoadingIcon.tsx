import * as React from "react";
import { Icon } from "semantic-ui-react";
import "loaders.css/loaders.css";
import "./colors.scss";

export const Spinner = ({ loading, color }) => (loading) ?
    <i className={"loader-inner ball-pulse " + color}>
        <div />
        <div />
        <div />
    </i> : null;

export const DefaultLoading = ({ size = null, ...props }) => (props.loading) ?
    <Icon name="circle notched" size={size} className={props.className} loading />
    : null;