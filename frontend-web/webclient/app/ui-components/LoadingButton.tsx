import * as React from "react";
import { Button, Icon } from ".";
import { ThemeColor } from "./theme";

const LoadingButton = ({ loading, color, content, ...props }: { loading: boolean, color?: ThemeColor, content?: string, children } & any) => loading ?
    <Button color={color} disabled {...props}>
        <i className="fas fa-circle-notch fa-spin" />
    </Button> :
    <Button color={color} {...props}>
        {content}{props.children}
    </Button>

export default LoadingButton;
