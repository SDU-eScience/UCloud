import {AppOrTool} from "Applications/api";
import {AppLogo, hashF} from "Applications/Card";
import {Cloud} from "Authentication/SDUCloudObject";
import * as React from "react";
import {useEffect, useState} from "react";
import styled from "styled-components";
import Image from "ui-components/Image";

interface AppToolLogoProps {
    name: string;
    size?: string;
    type: AppOrTool;
    cacheBust?: string;
}

export const AppToolLogo: React.FunctionComponent<AppToolLogoProps> = props => {
    const [hasLoadedImage, setLoadedImage] = useState(true);
    const size = props.size !== undefined ? props.size : "48px";
    const context = props.type === "APPLICATION" ? "apps" : "tools";

    useEffect(() => setLoadedImage(true), [props.cacheBust]);

    return <>
        <img
            onErrorCapture={() => {
                setLoadedImage(false);
                // For some reason the state is not always correctly set. This is the worst possible work around.
                setTimeout(() => setLoadedImage(false), 50);
            }}
            style={hasLoadedImage ? {width: size, height: size, objectFit: "contain"} : {display: "none"}}
            src={Cloud.computeURL("/api", `/hpc/${context}/logo/${props.name}?cacheBust=${props.cacheBust}`)}
            alt={props.name}/>

        {hasLoadedImage ? null : <AppLogo size={size} hash={hashF(props.name)}/>}
    </>;
};

const AppToolImage = styled(Image)`
    object-fit: contain;
    max-width: unset;
`;
