import {AppOrTool} from "Applications/api";
import {AppLogo, hashF} from "Applications/Card";
import {Client} from "Authentication/HttpClientInstance";
import * as React from "react";
import {useState} from "react";

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

    const url = Client.computeURL("/api", `/hpc/${context}/logo/${props.name}`);

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
                alt={props.name}
            />

            {hasLoadedImage ? null : <AppLogo size={size} hash={hashF(props.name)} />}
        </>
    );
};
