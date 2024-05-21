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
        border-top-right-radius: 6px;
        border-bottom-right-radius: 6px;
    }
    
    ${k} > .${ButtonClass}:first-child, .first {
        border-top-left-radius: 6px;
        border-bottom-left-radius: 6px;
    }
`);

const ButtonGroup: React.FunctionComponent<BoxProps & { children?: React.ReactNode }> = ({height = "35px",...props}) => {
    return <Box className={ButtonGroupClass} height={height} {...props} />;
};

ButtonGroup.displayName = "ButtonGroup";

export default ButtonGroup;
