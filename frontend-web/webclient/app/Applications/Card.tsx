import * as React from "react";
import Markdown from "ui-components/Markdown"
import { Absolute, Icon, Flex, RatingBadge, Text } from "ui-components";
import Box from "ui-components/Box";
import Link from "ui-components/Link";
import { EllipsedText } from "ui-components/Text";
import * as Pages from "./Pages";
import { WithAppMetadata } from ".";
import styled, { css } from "styled-components";
import * as Heading from "ui-components/Heading"
import theme from "ui-components/theme"

interface ApplicationCardProps {
    onFavorite?: (name: string, version: string) => void
    app: WithAppMetadata
    isFavorite?: boolean
    linkToRun?: boolean
}

const AppCardActionsBase = styled.div``;

const AppCardBase = styled(Link)`
    padding: 10px;
    width: 100%;
    display: flex;
    align-items: center;

    & > img {
        width: 32px;
        height: 32px;
        margin-right: 16px;
        border-radius: 5px;
        flex-shrink: 0;
    }

    & > strong {
        margin-right: 16px;
        font-weight: bold;
        flex-shrink: 0;
    }

    & > ${EllipsedText} {
        color: ${(props) => props.theme.colors.gray};
        flex-grow: 1;
    }

    & > ${EllipsedText} > p {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
`;

export const ApplicationCardContainer = styled.div`
    display: flex;
    flex-direction: column;

    & > ${AppCardBase}:first-child {
        border: 1px solid ${props => props.theme.colors.borderGray};
        border-top-left-radius: 5px;
        border-top-right-radius: 5px;
    }

    & > ${AppCardBase} {
        border: 1px solid ${props => props.theme.colors.borderGray};
        border-top: 0;
    }

    & > ${AppCardBase}:last-child {
        border-bottom-left-radius: 5px;
        border-bottom-right-radius: 5px;
    }
`;

export const SlimApplicationCard: React.FunctionComponent<ApplicationCardProps> = (props) => {
    const { metadata } = props.app;
    return (
        <AppCardBase to={props.linkToRun ? Pages.runApplication(metadata) : Pages.viewApplication(metadata)}>
            <Box mr={16} >
                <AppLogo size={"32px"} hash={hashF(metadata.title)} />
            </Box>
            <strong>{metadata.title} v{metadata.version}</strong>
            <EllipsedText>
                <Markdown
                    source={metadata.description}
                    allowedTypes={["text", "root", "paragraph"]} />
            </EllipsedText>
            <AppCardActionsBase><Icon name="chevronDown" rotation={-90} /></AppCardActionsBase>
        </AppCardBase>
    );
};

export const AppCard = styled(Link)`

    padding: 10px;
    width: 100%;
    min-width: 350px;
    height: 128px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    border-radius: ${props => props.theme.radius};
    position: relative;
    overflow: hidden;
    box-shadow: ${({ theme }) => theme.shadows["sm"]};
    //box-shadow: inset 0 0 0 1px #c9d3df ; //inset border does not work on chrome with will-change

    transition: transform ${({ theme }) => theme.timingFunctions.easeIn} ${({ theme }) => theme.duration.fastest} ${({ theme }) => theme.transitionDelays.xsmall};
    will-change: transform;

    &:hover {
        transition: transform ${({ theme }) => theme.timingFunctions.easeOut} ${({ theme }) => theme.duration.fastest} ${({ theme }) => theme.transitionDelays.xsmall};
        transform: scale(1.02);
    }

    // Background
    &:before {
        pointer-events: none;
        content: "";
        position: absolute;
        width: 104%;
        height: 280%;
        top: 0;
        left: 0;
        z-index: -1;
        background-color: #ebeff3;
        background-image: url("data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyOCIgaGVpZ2h0PSI1MCI+CiAgPGcgdHJhbnNmb3JtPSJzY2FsZSgwLjUpIj4KPHBhdGggZD0iTTI4IDY2TDAgNTBMMCAxNkwyOCAwTDU2IDE2TDU2IDUwTDI4IDY2TDI4IDEwMCIgZmlsbD0ibm9uZSIgc3Ryb2tlPSIjYzlkM2RmNDQiIHN0cm9rZS13aWR0aD0iMS41Ij48L3BhdGg+CjxwYXRoIGQ9Ik0yOCAwTDI4IDM0TDAgNTBMMCA4NEwyOCAxMDBMNTYgODRMNTYgNTBMMjggMzQiIGZpbGw9Im5vbmUiIHN0cm9rZT0iI2M5ZDNkZjQ0IiBzdHJva2Utd2lkdGg9IjQiPjwvcGF0aD4KICA8L2c+Cjwvc3ZnPg==");
        background-repeat: repeat;
        transform: rotate(15deg) translate(0,-60%);
        transform-origin: 0 0;
        }

    &:after {
        content: "";
        position: absolute;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
        z-index: 1;
        border: 2px solid ${props => props.theme.colors.textHighlight};
        opacity: 0;
        border-radius: ${props => props.theme.radius};
        pointer-events: none; //needed for star-badge
        will-change: opacity;
    }

    &:hover:after {
        opacity: 1;
    }
`;

const Tag = ({ label }: { label: string }) => (
    <RatingBadge mr={"3px"} bg={"darkGray"}><Heading.h6>{label}</Heading.h6></RatingBadge>
)

const appColors = theme.appColors;

const nColors = appColors.length;

const bgGradients = appColors.map(x => ({ color1: x[0], color2: x[2] }));

const AppBg_triangle = ({ color1, color2 }: { color1: string, color2: string }) => (
    <svg height={"128px"} viewBox="0 0 72 128" >
        <path d="M0,128h72v-72z" fill={"url(#appbg_svg___" + color1 + "_" + color2} />
        <defs>
            <linearGradient
                id={"appbg_svg___" + color1 + "_" + color2}
                x1={72} x2={0} y1={128 - 72} y2={128}
                // x1={21} x2={72} y1={77} y2={128}
                gradientUnits="userSpaceOnUse"
            >
                <stop offset={0} stopColor={color1} />
                <stop offset={1} stopColor={color2} />
            </linearGradient>
        </defs>
    </svg>
);

const AppBg = ({ color1, color2 }: { color1: string, color2: string }) => (
    <svg height={"128px"} viewBox="0 0 100 128" >
        <path d="M 25,0 h 75 v 128 h -100 z" fill={"url(#appbg_svg___" + color1 + "_" + color2} />
        <defs>
            <linearGradient
                id={"appbg_svg___" + color1 + "_" + color2}
                x1={25} x2={100} y1={0} y2={128}
                gradientUnits="userSpaceOnUse"
            >
                <stop offset={0} stopColor={color1} />
                <stop offset={1} stopColor={color2} />
            </linearGradient>
        </defs>
    </svg>
);

export const AppLogoRaw = ({ rot, color1Offset, color2Offset, appC, size }: { color1Offset: number, color2Offset: number, appC: number, rot: number, size: string }) => {
    const c1 = [color1Offset % 3, (color1Offset + 1) % 3, (color1Offset + 2) % 3];
    const c2 = [color2Offset % 3, (color2Offset + 1) % 3, (color2Offset + 2) % 3];
    const centerC = nColors - 1;
    //const centerC = appC;

    const s32 = Math.sqrt(3) * .5;
    const r1 = 0.5; //inner radius of outer element (outer radius is 1)
    const r2 = 0.7; //outer radius of inner element
    const r3 = (1 + r2) * .5; // radius of white background hexagon 

    const rot120 = "rotate(120 0 0)";
    const rot240 = "rotate(240 0 0)";

    return (
        <svg
            width={size} height={size}
            viewBox={`-1 -${s32} 2 ${(2 * s32)}`}
            fillRule="evenodd"
            clipRule="evenodd"
        >
            <defs>
                <path id="hex_to___" d={"M-" + r1 + " 0H-1L-0.5 " + s32 + "H0.5L" + (0.5 * r1) + " " + (s32 * r1) + "H-" + (0.5 * r1) + "Z"} />
                <path id="hex_ti___" d={`M0 0H${r2}L${0.5 * r2} -${s32 * r2}H-${0.5 * r2}Z`} fill-opacity=".55" />
                <path id="hex_th___" d={"M-" + r3 + " 0L-" + (0.5 * r3) + " " + (s32 * r3) + "H" + (0.5 * r3) + "L" + r3 + " 0L" + (0.5 * r3) + " -" + (s32 * r3) + "H-" + (0.5 * r3) + "Z"} />
            </defs>
            <g transform={`rotate(${rot} 0 0)`} >
                <use xlinkHref="#hex_th___" fill="#fff" />
                <use xlinkHref="#hex_to___" fill={appColors[appC][c1[0]]} />
                <use xlinkHref="#hex_to___" fill={appColors[appC][c1[1]]} transform={rot120} />
                <use xlinkHref="#hex_to___" fill={appColors[appC][c1[2]]} transform={rot240} />
                <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[0]]} />
                <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[1]]} transform={rot120} />
                <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[2]]} transform={rot240} />
            </g>
        </svg>
    );
}
export const AppLogo = ({ size, hash }: { size: string, hash: number }) => {
    const i1 = (hash >>> 30) & 3;
    const i2 = (hash >>> 20) & 3;
    const rot = [0, 15, 30];
    const i3 = (hash >>> 10) % rot.length;
    const appC = appColor(hash);

    return <AppLogoRaw rot={rot[i3]} color1Offset={i1} color2Offset={i2} appC={appC} size={size} />
}


const AppRibbonContainer = styled(Absolute) <{ favorite?: boolean }>`
    ${({ favorite }) => favorite ? null : css`transform: translate(0,-30px)`};
    transition: transform ease 0.1s;
    will-change: transform;

    &: hover {
        transform: translate(0, 0);
    }
`


export function hashF(str: string): number {
    var hash = 5381,
        i = str.length;

    while (i) {
        hash = (hash * 33) ^ str.charCodeAt(--i);
    }

    /* JavaScript does bitwise operations (like XOR, above) on 32-bit signed
     * integers. Since we want the results to be always positive, convert the
     * signed int to an unsigned by doing an unsigned bitshift. */

    return hash >>> 0;

}

function appColor(hash: number): number {
    return (hash >>> 22) % (nColors - 1); //last color not used
}

const AbsoluteNoPointerEvents = styled(Absolute)`
    pointer-events: none;
`

export const ApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => {
    const hash = hashF(app.metadata.title);
    const { metadata } = app;
    const appC = appColor(hash);
    return (
        <AppCard to={linkToRun ? Pages.runApplication(metadata) : Pages.viewApplication(metadata)} hoverColor={null}>
            <AbsoluteNoPointerEvents right={0} top={0} cursor="inherit">
                <AppBg_triangle {...bgGradients[appC]} />
            </AbsoluteNoPointerEvents>
            {(!onFavorite && !isFavorite) ? null :
                <AppRibbonContainer
                    cursor="inherit"
                    right={0}
                    top={0}
                    favorite={isFavorite}
                    onClick={e => !!onFavorite ? (e.preventDefault(), onFavorite(metadata.name, metadata.version)) : undefined}
                >
                    <Icon name={"starRibbon"} color="red" size={48} />
                </AppRibbonContainer>
            }
            <Flex flexDirection={"row"} alignItems={"flex-start"} zIndex={1}>
                <AppLogo size={"48px"} hash={hash} />
                <Flex flexDirection={"column"} ml="10px">
                    <Flex>
                        <Heading.h4>{metadata.title}</Heading.h4>
                        <Text ml="0.4em" mt="3px" color="gray">v{metadata.version}</Text>
                    </Flex>
                    <EllipsedText width={200} title={`by ${metadata.authors.join(", ")} `} color="gray">
                        by {app.metadata.authors.join(", ")}
                    </EllipsedText>
                </Flex>
            </Flex>
            <Box mt="auto" />
            <Flex flexDirection={"row"} alignItems={"flex-start"} zIndex={1}>
                {app.metadata.tags.map((tag, idx) => <Tag label={tag} key={idx} />)}
            </Flex>
        </AppCard>
    );
};
