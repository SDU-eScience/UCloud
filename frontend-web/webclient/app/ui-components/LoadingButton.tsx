import * as React from "react";
import { Button, Icon } from ".";

const LoadingButton = ({ loading, color, content, type }) => loading ?
    <Button color={color} disabled type={type}>
        {content} {/* // FIXME should have rotating icon */}
    </Button> :
    <Button color={color} type={type}>
        {content}
    </Button>

export default LoadingButton;
