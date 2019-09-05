import {AppLogo, hashF} from "Applications/Card";
import {Cloud} from "Authentication/SDUCloudObject";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import Image from "ui-components/Image";

export const ToolLogo: React.FunctionComponent<{ tool: string, size?: string, cacheBust?: string }> = props => {
    const [hasLoadedImage, setLoadedImage] = useState(false);
    const size = props.size !== undefined ? props.size : "48px";

    useEffect(() => {
        setLoadedImage(false);
    }, [props.cacheBust]);

    return <>
        <ToolImage
            onLoad={() => setLoadedImage(true)}
            style={hasLoadedImage ? {width: size, height: size} : {display: "none"}}
            src={Cloud.computeURL("/api", `/hpc/tools/logo/${props.tool}?cacheBust=${props.cacheBust}`)}
        />

        {hasLoadedImage ? null : <AppLogo size={size} hash={hashF(props.tool)}/>}
    </>;
};

const ToolImage = styled(Image)`
    object-fit: contain;
`;
