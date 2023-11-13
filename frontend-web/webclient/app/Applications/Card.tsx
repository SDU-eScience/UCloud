import {AppToolLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Flex, Icon, Relative} from "@/ui-components";
import Link, {LinkProps} from "@/ui-components/Link";
import Markdown from "@/ui-components/Markdown";
import {TextClass} from "@/ui-components/Text";
import theme, {ThemeColor} from "@/ui-components/theme";
import {classConcat, injectStyle, injectStyleSimple} from "@/Unstyled";
import { CardClass } from "@/ui-components/Card";
import {compute} from "@/UCloud";
import ApplicationSummaryWithFavorite = compute.ApplicationSummaryWithFavorite;

interface ApplicationCardProps {
    onFavorite?: (app: ApplicationSummaryWithFavorite) => void;
    title: string;
    description?: string;
    logo: string;
    type: AppCardType;
    isFavorite?: boolean;
    link?: string;
    colorBySpecificTag?: string;
    application?: ApplicationSummaryWithFavorite
}

const AppCardBase = injectStyle("app-card-base", k => `
    ${k} {
        padding: 10px;
        width: 100%;
        display: flex;
        align-items: center;
    }

    ${k} > img {
        width: 32px;
        height: 32px;
        margin-right: 16px;
        border-radius: 5px;
        flex-shrink: 0;
    }

    ${k} > strong {
        margin-right: 16px;
        font-weight: 700;
        flex-shrink: 0;
    }

    ${k} > .${TextClass} {
        color: var(--gray, #f00);
        flex-grow: 1;
    }

    ${k} > .${TextClass} > p {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
`);

export const Tag = ({label, bg = "black"}: {label: string; bg?: ThemeColor}): JSX.Element => (
    <div style={{
        marginRight: "3px",
        background: `var(--${bg})`,
        display: "inline-block",
        lineHeight: 1.5,
        color: "white",
        textTransform: "uppercase",
        fontSize: "10px",
        fontWeight: 700,
        borderRadius: "6px",
        padding: "3px"
    }}>
        {label}
    </div>
);

const appColors = theme.appColors;

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

function SmallCard(props: LinkProps) {
    return <Link className={SmallCardClass} {...props} />
}

const SmallCardClass = injectStyle("small-card", k => `
    ${k} {
        display: flex;
        padding: 16px;
        width: 150px;
        height: 50px;
        border-radius: 10px;
        font-size: ${theme.fontSizes[2]}px;
        text-align: center;
        align-items: center;
        justify-content: center;
        box-shadow: ${theme.shadows.sm};
        transition: transform ${theme.timingFunctions.easeIn} ${theme.duration.fastest} ${theme.transitionDelays.xsmall};
    }

    ${k}:hover {
        transition: transform ${theme.timingFunctions.easeOut} ${theme.duration.fastest} ${theme.transitionDelays.xsmall};
        color: var(--white, #f00);
    }
`);

const MultiLineTruncateClass = injectStyleSimple("multiline-truncate", `
    display: -webkit-box;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-align: left;
    font-size: 13px;
`);

const TallApplicationCard = injectStyle("tall-application-card", k => `
    ${k} {
        width: 166px;
        height: 225px;
        cursor: pointer;
    }

    ${k} > div.image {
        width: 75px;
        height: 75px;
        margin-bottom: 10px;
    }
    
    ${k} > div.image > * {
        width: 52px;
        height: 52px;
    }

    ${k}[data-xl="true"] {
        height: 290px;
    }
`);

const TitleAndDescriptionClass = injectStyleSimple("title-and-description", "");

const WideApplicationCard = injectStyle("wide-application-card", k => `
    ${k} {
        display: flex;
        width: 312px;
        height: 142px;
    }

    ${k} > div.image {
        width: 75px;
        height: 75px;
        margin-top: auto;
        margin-bottom: auto;
        text-align: center;
    }

    ${k} > div.${TitleAndDescriptionClass} {
        text-align: left;
        width: calc(322px - 100px);
        padding-left: 15px;
        margin-top: 5px;
    }

    ${k}[data-xl="true"] {
        width: 432px;
        height: 202px;
    }

    ${k}[data-xl="true"] > div.image {
        width: 100px;
        height: 100px;
    }

    ${k}[data-xl="true"] > div.${TitleAndDescriptionClass} {
        width: calc(432px - 140px);
    }
`);

export const ApplicationCardClass = injectStyle("application-card", k => `
    ${k} {
        user-select: none;
    }

    ${k} > div.${TitleAndDescriptionClass} {
        font-size: var(--buttonText);
    }

    ${k} > div > span {
        text-align: left;
        font-size: var(--secondaryText);
        overflow-y: scroll;
        padding-left: 8px;
        padding-right: 8px;
        height: 90px;
    }
    
    ${k} > div.image > * {
        margin-left: auto;
        margin-right: auto;
        margin-top: auto;
        margin-bottom: auto;
    }

    ${k} > div > .${MultiLineTruncateClass} {
        color: var(--text);
        padding-top: 8px;
    }

    ${k} > .${TitleAndDescriptionClass} > div:first-child {
        text-overflow: ellipsis;
        overflow: hidden;
    }

    ${k} > div.image {
        background-color: var(--lightGray);
        border-radius: 12px;
        margin-left: auto;
        margin-right: auto;
        display: flex;
    }

    html.dark ${k} > div.image {
        background-color: white;
    }

    ${k} > div {
        text-align: center;
    }
`);

function MultiLineTruncate(props: React.PropsWithChildren<{lines: number}>): JSX.Element {
    const {lines, ...p} = props;
    return <div className={MultiLineTruncateClass} style={{
        WebkitLineClamp: props.lines,
    }} {...p} />;
}

const FAV_ICON_SIZE = "20px";

export enum AppCardStyle {
    WIDE,
    EXTRA_TALL,
    TALL,
    EXTRA_WIDE,
}

export enum AppCardType {
    GROUP,
    APPLICATION
}

export interface AppCardProps extends React.PropsWithChildren<ApplicationCardProps> {
    cardStyle: AppCardStyle;
}

const typeLineCount = {
    [AppCardStyle.TALL]: 5,
    [AppCardStyle.EXTRA_TALL]: 4,
    [AppCardStyle.WIDE]: 4,
    [AppCardStyle.EXTRA_WIDE]: 9
}

export function AppCard(props: AppCardProps): JSX.Element {
    return React.useMemo(() => {
        let lineCount = typeLineCount[props.cardStyle];
        let card: React.JSX.Element;
        const titleAndDescription =
            <div className={TitleAndDescriptionClass}>
                <div><b>{props.title}</b></div>
                <MultiLineTruncate lines={lineCount}>
                    <Markdown
                        disallowedElements={['br', 'a', 'p', 'strong', 'b', 'i']}
                        unwrapDisallowed={true}
                    >
                        {props.description ?? ""}
                    </Markdown>
                </MultiLineTruncate>
            </div>;
        switch (props.cardStyle) {
            case AppCardStyle.EXTRA_TALL:
            case AppCardStyle.TALL:
                const isExtraTall = props.cardStyle === AppCardStyle.EXTRA_TALL;
                card = <Flex 
                        flexDirection="column" 
                        className={classConcat(CardClass, ApplicationCardClass, TallApplicationCard)} 
                        data-xl={isExtraTall}
                    >
                        <div className="image">
                            <AppToolLogo
                                size={"52px"}
                                name={props.logo}
                                type={props.type === AppCardType.APPLICATION ?
                                    "APPLICATION" : "GROUP"
                                }
                            />
                        </div>
                        {titleAndDescription}
                    </Flex>;
                break;

            case AppCardStyle.WIDE:
            case AppCardStyle.EXTRA_WIDE:
                const isExtraWide = props.cardStyle === AppCardStyle.EXTRA_WIDE;
                card = <div 
                    className={classConcat(CardClass, ApplicationCardClass, WideApplicationCard)} 
                    data-xl={isExtraWide}
                >
                    <div className="image">
                        <AppToolLogo
                            size={isExtraWide ? "85px" : "65px"}
                            name={props.logo}
                            type={props.type === AppCardType.APPLICATION ?
                                "APPLICATION" : "GROUP"
                            }
                        />
                    </div>
                    {titleAndDescription}
                </div>
        }

        return props.link ? 
            props.type === AppCardType.APPLICATION ?
                <Flex>
                    <Link to={props.link}>
                        {card}
                    </Link>
                    <Relative top="6px" right="28px" width="0px" height="0px">
                        <Icon
                            cursor="pointer"
                            name={props.isFavorite ? "starFilled" : "starEmpty"}
                            color="blue"
                            hoverColor="blue"
                            size="20px"
                            onClick={() => props.onFavorite && props.application ?
                                props.onFavorite(props.application) : {}
                            }
                        />
                    </Relative>
                </Flex>
            :
                <Link to={props.link}>
                    {card}
                </Link>
        : <>{card}</>
    }, [props]);
}
