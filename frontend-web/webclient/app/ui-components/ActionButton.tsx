import {LoadableContent} from "LoadableContent";
import * as React from "react";
import Button from "ui-components/Button";
import Text from "ui-components/Text";
import {ButtonProps} from "./Button";

export const ActionButton: React.FunctionComponent<{
    loadable: LoadableContent;
} & ButtonProps & React.ButtonHTMLAttributes<HTMLButtonElement>> = props => {
    return (
        <div>
            <Button m={0} {...props} disabled={props.loadable.loading}>{props.children}</Button>
            {props.loadable.error ? <Text color="red" m={0}>{props.loadable.error.errorMessage}</Text> : null}
        </div>
    );
};

ActionButton.displayName = "ActionButton";

export default ActionButton;
