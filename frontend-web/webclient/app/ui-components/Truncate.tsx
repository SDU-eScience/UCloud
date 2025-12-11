import Text, {TextProps} from "./Text";
import * as React from "react";
import {classConcat, extractDataTags, injectStyle} from "@/Unstyled";

export const TruncateClass = injectStyle("truncate", k => `
    ${k} {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
`);

const Truncate: React.FunctionComponent<TextProps & {children?: React.ReactNode;}> = props => {
    return <Text {...props} {...extractDataTags(props)} className={classConcat(TruncateClass, props.className)} />;
}

Truncate.displayName = "Truncate";

export default Truncate;
