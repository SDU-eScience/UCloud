import Flex, {FlexCProps} from "./Flex";
import {extractEventHandlers, injectStyle, unbox} from "@/Unstyled";
import * as React from "react";
import {BoxProps} from "@/ui-components/Box";

const SelectableTextContainerClass = injectStyle("tab-container", k => `
    ${k} {
        display: flex;
        border-bottom: 2px solid var(--borderColor);
        cursor: pointer;
    }
    
    ${k} > * {
        margin-right: 1em;
        cursor: pointer;
        font-size: 20px;
    }
    
    ${k} > [data-selected="true"] {
        border-bottom: 3px solid var(--primaryMain);
    }
`);


const SelectableTextWrapper: React.FunctionComponent<FlexCProps & { children?: React.ReactNode; }> = props => {
    return <Flex className={SelectableTextContainerClass} {...props} />;
}

const SelectableText: React.FunctionComponent<BoxProps & {
    selected: boolean;
    children?: React.ReactNode;
}> = props => {
    return <div
        style={unbox(props)}
        data-selected={props.selected}
        children={props.children}
        {...extractEventHandlers(props)}
    />;
}

SelectableText.displayName = "SelectableText";

export {SelectableTextWrapper, SelectableText};
