import Text, {TextProps} from "./Text";
import * as React from "react";
import {classConcat, injectStyle} from "@/Unstyled";

export const TruncateClass = injectStyle("truncate", k => `
    ${k} {
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
`);

const Truncate: React.FunctionComponent<TextProps & {children?: React.ReactNode;}> = props => {
    return <Text {...props} className={classConcat(TruncateClass, props.className)} />;
}

Truncate.displayName = "Truncate";

export default Truncate;
