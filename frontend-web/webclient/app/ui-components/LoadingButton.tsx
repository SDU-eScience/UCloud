import * as React from "react";
import { Button, Icon } from ".";
import { ButtonProps } from "./Button";

const LoadingButton = ({ loading, color, content, ...props }) => loading ?
    <Button color={color} disabled {...props}>
        {content} {/* // FIXME should have rotating icon */}
    </Button> :
    <Button color={color} {...props}>
        {content}
    </Button>

export default LoadingButton;
