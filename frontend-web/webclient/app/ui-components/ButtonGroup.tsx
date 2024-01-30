import {ButtonClass} from "./Button";
import {injectStyle} from "@/Unstyled";
import * as React from "react";
import Box from "@/ui-components/Box";
import {BoxProps} from "./Types";

export const ButtonGroupClass = injectStyle("button-group", k => `
    ${k} {
        display: flex;
    }
    
    ${k} .${ButtonClass} {
        height: 100%;
        width: 100%;
        padding: 0 10px;
        border-radius: 0;
        white-space: nowrap;
    }
    
    ${k} > .${ButtonClass}:last-child, .last {
        border-top-right-radius: 3px;
        border-bottom-right-radius: 3px;
    }
    
    ${k} > .${ButtonClass}:first-child, .first {
        border-top-left-radius: 3px;
        border-bottom-left-radius: 3px;
    }
`);

const ButtonGroup: React.FunctionComponent<BoxProps & { children?: React.ReactNode }> = props => {
    return <Box className={ButtonGroupClass} {...props} />;
};

ButtonGroup.displayName = "ButtonGroup";
ButtonGroup.defaultProps = {
    height: "35px"
};

export default ButtonGroup;
