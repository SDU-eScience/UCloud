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

export function truncateText(text: string, maxLength: number): string {
    if (text.length > maxLength - 3) {
        return text.substring(0, maxLength - 3) + "...";
    } else {
        return text;
    }
}

export default Truncate;
