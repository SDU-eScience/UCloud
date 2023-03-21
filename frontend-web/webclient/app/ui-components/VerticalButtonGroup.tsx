import {injectStyle} from "@/Unstyled";
import {ButtonClass} from "./Button";
import Flex, {FlexCProps} from "./Flex";
import * as React from "react";

const VerticalButtonGroupClass = injectStyle("vertical-button-group", k => `
    ${k} {
        height: 98%;
        flex-direction: column;
        /* leave some space on top if buttons grow on hover */
        margin-top: 4px;
    }
    
    ${k} .${ButtonClass} {
        width: 100%;
        margin-bottom: 8px;
    }
`);

const VerticalButtonGroup: React.FunctionComponent<FlexCProps & { children?: React.ReactNode }> = props => {
    return <Flex {...props} className={VerticalButtonGroupClass} />;
};

VerticalButtonGroup.displayName = "VerticalButtonGroup";

export default VerticalButtonGroup;
