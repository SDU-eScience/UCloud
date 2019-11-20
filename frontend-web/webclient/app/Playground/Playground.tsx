import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {EveryIcon} from "ui-components/Icon";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <EveryIcon />
    );
    return <MainContainer main={main} />;
};
