import * as React from "react";
import {useEffect, useState} from "react";
import {appColors, useIsLightThemeStored} from "@/ui-components/theme";
import * as AppStore from "@/Applications/AppStoreApi";
import {injectStyle} from "@/Unstyled";

interface AppToolLogoProps {
    name: string;
    size?: string;
    type: LogoType;
    isLightOverride?: boolean;
}

export type LogoType = "APPLICATION" | "TOOL" | "GROUP";

export const AppToolLogo: React.FunctionComponent<AppToolLogoProps> = props => {
    let isLight = useIsLightThemeStored();
    if (props.isLightOverride !== undefined) isLight = props.isLightOverride;
    const size = props.size !== undefined ? props.size : "48px";

    const [dataUrl, setDataUrl] = useState<string | null | "loading">("loading");
    useEffect(() => {
        let didCancel = false;
        /* NOTE(jonas): `props.name` is sometimes an empty string, why? */
        if (!props.name) return;
        if (props.type === "TOOL") {
            setDataUrl(null);
        } else {
            (async () => {
                const url = props.type === "GROUP" ?
                    AppStore.retrieveGroupLogo({id: parseInt(props.name), includeText: false, darkMode: !isLight})
                    : AppStore.retrieveAppLogo({name: props.name, includeText: false, darkMode: !isLight});

                try {
                    const blob = await (await fetch(url)).blob();
                    if (!didCancel) {
                        if (blob.type.indexOf("image/") === 0) {
                            setDataUrl(URL.createObjectURL(blob));
                        } else {
                            setDataUrl(null);
                        }
                    }
                } catch (e) {
                }
            })();
        }

        return () => {
            didCancel = true;
        };
    }, [props.name, isLight]);

    if (dataUrl == null) return null;
    return <img
        src={dataUrl}
        alt={"Logo"}
        style={{
            maxHeight: size,
            maxWidth: size,
        }}
    />;
}

const nColors = appColors.length;

interface AppLogoRawProps {
    color1Offset: number;
    color2Offset: number;
    appC: number;
    rot: number;
    size: string;
}


const ROT120 = "rotate(120 0 0)";
const ROT240 = "rotate(240 0 0)";
const S32 = Math.sqrt(3) * .5;
const R1 = 0.5; // inner radius of outer element (outer radius is 1)
const R2 = 0.7; // outer radius of inner element
const R3 = (1 + R2) * .5; // radius of white background hexagon
const CENTER_COLORS = ["#C9D3DF", "#8393A7", "#53657D"]
export const AppLogoRaw = ({rot, color1Offset, color2Offset, appC, size}: AppLogoRawProps): React.ReactNode => {
    const c1 = [color1Offset % 3, (color1Offset + 1) % 3, (color1Offset + 2) % 3];
    const c2 = [color2Offset % 3, (color2Offset + 1) % 3, (color2Offset + 2) % 3];

    return (
        <svg
            width={size}
            height={size}
            viewBox={`-1 -${S32} 2 ${2 * S32}`}
            fillRule="evenodd"
            clipRule="evenodd"
        >
            <defs>
                <path id="hex_to___" d={`M-${R1} 0H-1L-0.5 ${S32}H0.5L${(0.5 * R1)} ${S32 * R1}H-${0.5 * R1}Z`} />
                <path id="hex_ti___" d={`M0 0H${R2}L${0.5 * R2} -${S32 * R2}H-${0.5 * R2}Z`} fillOpacity=".55" />
                <path
                    id="hex_th___"
                    d={`M-${R3} 0L-${0.5 * R3} ${S32 * R3}H${0.5 * R3}L${R3} 0L${0.5 * R3} -${S32 * R3}H-${0.5 * R3}Z`}
                />
            </defs>
            <g transform={`rotate(${rot} 0 0)`}>
                <use href="#hex_th___" fill="#fff"/>
                <use href="#hex_to___" fill={appColors[appC][c1[0]]}/>
                <use href="#hex_to___" fill={appColors[appC][c1[1]]} transform={ROT120}/>
                <use href="#hex_to___" fill={appColors[appC][c1[2]]} transform={ROT240}/>
                <use href="#hex_ti___" fill={CENTER_COLORS[c2[0]]}/>
                <use href="#hex_ti___" fill={CENTER_COLORS[c2[1]]} transform={ROT120}/>
                <use href="#hex_ti___" fill={CENTER_COLORS[c2[2]]} transform={ROT240}/>
            </g>
        </svg>
    );
};

export const AppLogo = ({size, hash}: { size: string, hash: number }): React.ReactNode => {
    const i1 = (hash >>> 30) & 3;
    const i2 = (hash >>> 20) & 3;
    const rot = [0, 15, 30];
    const i3 = (hash >>> 10) % rot.length;
    const appC = appColor(hash);

    return <AppLogoRaw rot={rot[i3]} color1Offset={i1} color2Offset={i2} appC={appC} size={size} />;
};

const SafeLogoStyle = injectStyle("safe-app-logo", k => `
    ${k} {
        display: flex;
        background: var(--appLogoBackground);
        padding: 4px;
        border-radius: 5px;
        border: var(--backgroundCardBorder);
        align-items: center;
        justify-content: center;
    }
`);
export const SafeLogo: React.FunctionComponent<{
    name: string;
    type: "APPLICATION" | "TOOL" | "GROUP";
    size: string;
    isLightOverride?: boolean;
}> = props => {
    const sizeInPixels = parseInt(props.size.toString().replace("px", ""));
    const paddingInPixels = sizeInPixels / 8;
    return <div
        style={{padding: `${paddingInPixels}px`, width: `${sizeInPixels + paddingInPixels * 2}px`, textAlign: "center"}}
    >
        <AppToolLogo size={props.size} name={props.name} type={props.type} isLightOverride={props.isLightOverride}/>
    </div>;
}

export function hashF(str: string): number {
    let hash = 5381;
    let i = str.length;

    while (i) {
        hash = (hash * 33) ^ str.charCodeAt(--i);
    }

    /* JavaScript does bitwise operations (like XOR, above) on 32-bit signed
     * integers. Since we want the results to be always positive, convert the
     * signed int to an unsigned by doing an unsigned bitshift. */

    return hash >>> 0;

}

export function appColor(hash: number): number {
    return (hash >>> 22) % (nColors - 1); // last color not used
}