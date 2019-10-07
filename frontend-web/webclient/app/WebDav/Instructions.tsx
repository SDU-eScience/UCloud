import * as React from "react";

export const Instructions: React.FunctionComponent<{ token: string }> = props => {
    return (
        <>
            Your token is: {props.token}
        </>
    );
};
