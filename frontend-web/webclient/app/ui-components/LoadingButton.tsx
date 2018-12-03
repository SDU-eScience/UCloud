import * as React from "react";
import { Button, Icon } from ".";

const LoadingButton = ({ loading, ...props }) => loading ?
    <Button color={props.color} disabled {...props}>
        <i className="fas fa-circle-notch fa-spin" />
    </Button> :
    <Button color={props.color} {...props}>
        {props.content}{props.children}
    </Button>

export default LoadingButton;
