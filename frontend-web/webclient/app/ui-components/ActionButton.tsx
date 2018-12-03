import Box from "./Box";
import * as React from "react";
import { Text, LoadingButton } from "ui-components";
import { LoadableContent } from "LoadableContent";
import { ButtonProps } from "./Button";

export const ActionButton: React.StatelessComponent<{ loadable: LoadableContent } & ButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>> = props => {
    return <Box>
        <LoadingButton m={0} {...props} loading={props.loadable.loading}>{props.children}</LoadingButton>
        {props.loadable.error ? <Text color="red" m={0}>{props.loadable.error.errorMessage}</Text> : null}
    </Box>;
};

export default ActionButton;