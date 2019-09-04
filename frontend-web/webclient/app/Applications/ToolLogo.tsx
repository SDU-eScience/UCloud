import {AppLogo, hashF} from "Applications/Card";
import {Cloud} from "Authentication/SDUCloudObject";
import * as React from "react";
import {useState} from "react";
import Image from "ui-components/Image";

export const ToolLogo: React.FunctionComponent<{ tool: string }> = props => {
    const [hasLoadedImage, setLoadedImage] = useState(false);
    const size = "48px";
    return <>
        <Image
            onLoad={() => setLoadedImage(true)}
            style={hasLoadedImage ? {maxWidth: size, maxHeight: size} : {display: "none"}}
            src={Cloud.computeURL("/api", `/hpc/tools/logo/${props.tool}`)}
        />

        {hasLoadedImage ? null : <AppLogo size={size} hash={hashF(props.tool)}/>}
    </>;
};
