import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import Tooltip from "ui-components/Tooltip";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <Tooltip trigger="Hello">
            I'm being shown
        </Tooltip>
    );
    return <MainContainer main={main} />;
};
