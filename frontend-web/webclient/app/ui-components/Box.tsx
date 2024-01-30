import * as React from "react";

import {classConcat, extractEventHandlers, injectStyleSimple, unbox, unboxDataTags} from "@/Unstyled";
import {BoxProps} from "./Types";

export const BoxClass = injectStyleSimple("box", ``);
const Box: React.FunctionComponent<BoxProps & {
    children?: React.ReactNode;
    divRef?: React.RefObject<HTMLDivElement>;
    title?: string;
    style?: React.CSSProperties;
    className?: string;
}> = props => {
    return <div
        className={classConcat(BoxClass, props.className)}
        style={{...unbox(props), ...(props.style ?? {})}}
        {...unboxDataTags(props as Record<string, string>)}
        title={props.title}
        ref={props.divRef}
        children={props.children}
        {...extractEventHandlers(props)}
    />;
}

Box.displayName = "Box";

export default Box;