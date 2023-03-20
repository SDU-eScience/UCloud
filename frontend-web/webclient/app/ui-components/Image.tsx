import {SpaceProps} from "styled-system";
import {injectStyle, unbox} from "@/Unstyled";
import * as React from "react";

export const ImageClass = injectStyle("image", k => `
    ${k} {
        max-width: 100%;
        height: auto;
    }
`);

const Image: React.FunctionComponent<SpaceProps & React.ImgHTMLAttributes<HTMLImageElement>> = props => {
    const className = ImageClass + " " + (props.className ?? "")
    const style = {...unbox(props), ...(props.style ?? {})};
    // noinspection HtmlRequiredAltAttribute
    return <img {...props} className={className} style={style} />;
};

Image.displayName = "Image";

export default Image;
