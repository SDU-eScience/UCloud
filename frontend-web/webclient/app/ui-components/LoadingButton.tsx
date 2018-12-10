import * as React from "react";
import { Button, Icon } from ".";
import { ButtonProps } from "./Button";
import { ThemeColor } from "./theme";

const LoadingButton = ({ loading, ...props }: ButtonProps & { loading: boolean, children: any, color: ThemeColor } & any) => loading ?
    <Button color={props.color} disabled {...props}>
        <i className="fas fa-circle-notch fa-spin" />
    </Button> :
    <Button color={props.color} {...props}>
        {props.content}{props.children}
    </Button>

export default LoadingButton;
