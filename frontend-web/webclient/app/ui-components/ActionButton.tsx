import Box from "./Box";
import * as React from "react";
import Button from "ui-components/Button";
import Text from "ui-components/Text";
import { LoadableContent } from "LoadableContent";
import { ButtonProps } from "./Button";

export const ActionButton: React.FunctionComponent<{ loadable: LoadableContent } & ButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>> = props => {
    return <Box>
        <Button m={0} {...props} disabled={props.loadable.loading}>{props.children}</Button>
        {props.loadable.error ? <Text color="red" m={0}>{props.loadable.error.errorMessage}</Text> : null}
    </Box>;
};

ActionButton.displayName = "ActionButton"

export default ActionButton;