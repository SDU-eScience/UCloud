import * as localForage from "localforage";
import {Client} from "@/Authentication/HttpClientInstance";
import * as React from "react";
import {useEffect, useState} from "react";
import {appColors} from "@/ui-components/theme";

interface AppToolLogoProps {
    name: string;
    size?: string;
    type: LogoType;
}

export type LogoType = "APPLICATION" | "TOOL" | "GROUP";

export const AppToolLogo: React.FunctionComponent<AppToolLogoProps> = props => {
    const size = props.size !== undefined ? props.size : "48px";

    const [dataUrl, setDataUrl] = useState<string | null | "loading">("loading");
    useEffect(() => {
        let didCancel = false;
        /* NOTE(jonas): `props.name` is sometimes an empty string, why? */
        if (!props.name) return;
        (async () => {
            const fetchedLogo = props.type === "APPLICATION" ?
                await appLogoCache.fetchLogo(props.name) :
                props.type === "GROUP" ? 
                    await groupLogoCache.fetchLogo(props.name) :
                    await toolLogoCache.fetchLogo(props.name);

            if (!didCancel) {
                setDataUrl(fetchedLogo);
            }
        })();

        return () => {
            didCancel = true;
        };
    }, [props.name]);

    return dataUrl === "loading" ?
        <div style={{width: size, height: size}} /> :
        dataUrl === null ?
            <AppLogo size={size} hash={hashF(props.name)} /> :
            <img src={dataUrl} alt={props.name}
                style={{width: size, height: size, objectFit: "contain"}}
            />;
};

class LogoCache {
    private readonly context: string;

    constructor(context: string) {
        this.context = context;
        (async () => {
            const now = window.performance &&
                window.performance["now"] &&
                window.performance.timing &&
                window.performance.timing.navigationStart ?
                window.performance.now() + window.performance.timing.navigationStart : Date.now();
            const expiry = await localForage.getItem<number>("logoCacheExpiry");
            if (expiry !== null && expiry >= now) {
                this.clear();
            }

            localForage.setItem("logoCacheExpiry", now + (1000 * 60 * 60 * 24 * 3));
        })();

    }

    public async fetchLogo(name: string): Promise<string | null> {
        const itemKey = `${this.context}/${name}`;
        const retrievedItem = await localForage.getItem<Blob | false>(itemKey);
        if (retrievedItem === null) {
            // No cache entry at all
            const url = Client.computeURL("/api", `/hpc/${this.context}/logo?name=${encodeURIComponent(name)}`);
            try {
                const blob = await (await fetch(url)).blob();
                if (blob.type.indexOf("image/") === 0) {
                    localForage.setItem(itemKey, blob);
                    return URL.createObjectURL(blob);
                } else {
                    return null;
                }
            } catch (e) {
                console.warn(e);
                return null;
            }
        } else {
            if (retrievedItem === false) {
                return null;
            } else if (retrievedItem.type.indexOf("image/") === 0) {
                return URL.createObjectURL(retrievedItem);
            } else {
                return null;
            }
        }
    }

    public forget(name: string) {
        localForage.removeItem(`${this.context}/${name}`);
    }

    public clear() {
        const itemsToRemove: string[] = [];
        localForage.iterate((value, key) => {
            if (key.indexOf(this.context) === 0) {
                itemsToRemove.push(key);
            }
        });

        itemsToRemove.forEach(key => localForage.removeItem(key));
    }
}

export const appLogoCache = new LogoCache("apps");
export const toolLogoCache = new LogoCache("tools");
export const groupLogoCache = new LogoCache("apps/group");

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
const CENTER_C = nColors - 1;
export const AppLogoRaw = ({rot, color1Offset, color2Offset, appC, size}: AppLogoRawProps): JSX.Element => {
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
                <use href="#hex_th___" fill="#fff" />
                <use href="#hex_to___" fill={appColors[appC][c1[0]]} />
                <use href="#hex_to___" fill={appColors[appC][c1[1]]} transform={ROT120} />
                <use href="#hex_to___" fill={appColors[appC][c1[2]]} transform={ROT240} />
                <use href="#hex_ti___" fill={appColors[CENTER_C][c2[0]]} />
                <use href="#hex_ti___" fill={appColors[CENTER_C][c2[1]]} transform={ROT120} />
                <use href="#hex_ti___" fill={appColors[CENTER_C][c2[2]]} transform={ROT240} />
            </g>
        </svg>
    );
};

export const AppLogo = ({size, hash}: {size: string, hash: number}): JSX.Element => {
    const i1 = (hash >>> 30) & 3;
    const i2 = (hash >>> 20) & 3;
    const rot = [0, 15, 30];
    const i3 = (hash >>> 10) % rot.length;
    const appC = appColor(hash);

    return <AppLogoRaw rot={rot[i3]} color1Offset={i1} color2Offset={i2} appC={appC} size={size} />;
};

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