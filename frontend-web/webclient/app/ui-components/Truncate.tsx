import Text, {TextProps} from "./Text";
import * as React from "react";
import {injectStyle} from "@/Unstyled";

const TruncateClass = injectStyle("truncate", k => `
    ${k} {
        flex: 1;
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
`);

const Truncate: React.FunctionComponent<TextProps & { children?: React.ReactNode; }> = props => {
    return <Text {...props} className={TruncateClass} />;
}

Truncate.displayName = "Truncate";

export default Truncate;
