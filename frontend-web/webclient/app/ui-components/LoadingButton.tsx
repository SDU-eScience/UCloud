import * as React from "react";
import { Button, Icon } from ".";

const LoadingButton = ({ loading, color, content, type, ...props }) => loading ?
    <Button color={color} disabled type={type} {...props}>
        {content} {/* // FIXME should have rotating icon */}
    </Button> :
    <Button color={color} type={type} {...props}>
        {content}
    </Button>

export default LoadingButton;
