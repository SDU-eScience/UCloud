import * as React from "react";
import {useState} from "react";
import {Client} from "@/Authentication/HttpClientInstance";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {AppLogo, hashF} from "@/Applications/AppToolLogo";

interface LogoProps {
    projectId: string;
    size?: string;
    cacheBust?: string;
}

export const ProjectLogo: React.FunctionComponent<LogoProps> = props => {
    const [hasLoadedImage, setLoadedImage] = useState(true);
    const size = props.size !== undefined ? props.size : "40px";

    const url = Client.computeURL("/api", buildQueryString(`/grants/v2/retrieveLogo`, props));

    return (
        <>
            <img
                onErrorCapture={() => {
                    setLoadedImage(false);
                    // For some reason the state is not always correctly set. This is the worst possible work around.
                    setTimeout(() => setLoadedImage(false), 50);
                }}
                key={url}
                style={hasLoadedImage ? {width: size, height: size, objectFit: "contain"} : {display: "none"}}
                src={url}
                alt={props.projectId}
            />

            {hasLoadedImage ? null : <AppLogo size={size} hash={hashF(props.projectId)} />}
        </>
    );
};
