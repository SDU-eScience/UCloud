import * as React from "react";
import { Link, Image, Markdown } from "ui-components";
import { Relative, Box, Absolute, Text, Icon, Flex, RatingBadge, Card } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import * as Pages from "./Pages";
import { Application } from ".";
import styled from "styled-components";
import * as Heading from "ui-components/Heading"
import theme from "ui-components/theme"

interface ApplicationCardProps {
    onFavorite?: (name: string, version: string) => void,
    app: Application,
    isFavorite?: boolean,
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
    return (
        <AppCardBase to={props.linkToRun ? Pages.runApplication(props.app) : Pages.viewApplication(props.app)}>
            <Box mr={16} >
                <AppLogo size={"32px"} hash={hashF(props.app.description.title)} />
            </Box>
            {/* <img src={props.app.imageUrl} /> */}
            <strong>{props.app.description.title} v{props.app.description.info.version}</strong>
            <EllipsedText>
                <Markdown
                    source={props.app.description.description}
                    allowedTypes={["text", "root", "paragraph"]} />
            </EllipsedText>
            <AppCardActionsBase><Icon name="chevronDown" rotation={-90} /></AppCardActionsBase>
        </AppCardBase>
    );
};

export const ApplicationCard = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => (
    <Link to={linkToRun ? Pages.runApplication(app) : Pages.viewApplication(app)}>
        <Card width="250px">
            <Relative height="135px">
                <Box>
                    <Image src={app.imageUrl} />
                    <Absolute top="6px" left="10px">
                        <Text
                            fontSize={2}
                            align="left"
                            color="white"
                        >
                            {app.description.title}
                        </Text>
                    </Absolute>
                    <Absolute top={"26px"} left={"14px"}>
                        <Text fontSize={"xxs-small"} align="left" color="white">
                            v{app.description.info.version}
                        </Text>
                    </Absolute>
                    <Absolute bottom="10px" left="10px">
                        <EllipsedText width={220} title={`by ${app.description.authors.join(", ")}`} color="white">
                            by {app.description.authors.join(", ")}
                        </EllipsedText>
                    </Absolute>
                </Box>
            </Relative>
            <Box m="10px">
                <Text>
                    {app.description.description.slice(0, 100)}
                </Text>
            </Box>
        </Card >
    </Link >
);

export const NewAppCard = styled(Link)`
    padding: 10px;
    width: 100%;
    min-width: 350px;
    height: 128px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    border-radius: ${props => props.theme.radius};
    position: relative;
    /* flex: 1 0 auto; */
    overflow: hidden;
    box-shadow: ${({ theme }) => theme.shadows["sm"]};

    transition: ${({ theme }) => theme.timingFunctions.easeInOut} ${({ theme }) => theme.transitionDelays.small};
    &:hover {
        transform: scale(1.03);
    }

    // Background
    &:before {
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
        z-index: -1;
        border: solid #c9d3df 1px;
        border-radius: ${props => props.theme.radius};
        pointer-events: none; //needed for star-badge
    }

    &:hover:after {
        border-color: ${props => props.theme.colors.textHighlight};
        z-index: 1;
    }
`;

const Tag = ({ label }: { label: string }) => (
    <RatingBadge mr={"3px"} bg={"darkGray"}><Heading.h6>{label}</Heading.h6></RatingBadge>
)

// function hslToHex(h, s, l) {
//     h /= 360;
//     s /= 100;
//     l /= 100;
//     let r, g, b;
//     if (s === 0) {
//         r = g = b = l; // achromatic
//     } else {
//         const hue2rgb = (p, q, t) => {
//             if (t < 0) t += 1;
//             if (t > 1) t -= 1;
//             if (t < 1 / 6) return p + (q - p) * 6 * t;
//             if (t < 1 / 2) return q;
//             if (t < 2 / 3) return p + (q - p) * (2 / 3 - t) * 6;
//             return p;
//         };
//         const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
//         const p = 2 * l - q;
//         r = hue2rgb(p, q, h + 1 / 3);
//         g = hue2rgb(p, q, h);
//         b = hue2rgb(p, q, h - 1 / 3);
//     }
//     const toHex = x => {
//         const hex = Math.round(x * 255).toString(16);
//         return hex.length === 1 ? '0' + hex : hex;
//     };
//     return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
// }

// const hues = [0, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300, 330]; 
// //const hues = [...Array(12).keys()].map( x=> (360/12)*x);

// const appColors2 = hues.map( x=> ( 
//     [ hslToHex(x,60,70), hslToHex(x,60,55), hslToHex(x,60,40) ]
// ));


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

const AppBg2 = ({ color1, color2 }: { color1: string, color2: string }) => {

    const s32 = Math.sqrt(3) * .5;
    const s15 = 1.5;
    const rot60 = "rotate(60 0 0)";

    const rots = [0, 60, 120, 180, 240, 300].map(x => (` rotate(${x} 0 0)`));
    const r = [1, 2, 4, 2,
        5, 4, 2, 3,
        4, 1, 0, 3,
        4, 5, 2, 1
    ];

    return (
        <svg height={"128px"} viewBox="0 0 100 128" >
            <use xlinkHref="#bg_card___" fill={"url(#appbg_svg___" + color1 + "_" + color2} />
            <g clipPath="url(#bg_clip___)">
                <g transform="scale(20)">
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (4 * s32) + ")" + rots[r[0]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (6 * s32) + ")" + rots[r[1]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (8 * s32) + ")" + rots[r[2]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (1 * s32) + ")" + rots[r[3]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (3 * s32) + ")" + rots[r[4]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (5 * s32) + ")" + rots[r[5]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (7 * s32) + ")" + rots[r[6]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (0 * s32) + ")" + rots[r[7]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (2 * s32) + ")" + rots[r[8]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (4 * s32) + ")" + rots[r[9]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (6 * s32) + ")" + rots[r[10]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (8 * s32) + ")" + rots[r[11]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (1 * s32) + ")" + rots[r[12]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (3 * s32) + ")" + rots[r[13]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (5 * s32) + ")" + rots[r[14]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (7 * s32) + ")" + rots[r[15]]} />
                </g> </g>
            <defs>
                <linearGradient
                    id={"appbg_svg___" + color1 + "_" + color2}
                    x1={25} x2={100} y1={0} y2={128}
                    gradientUnits="userSpaceOnUse"
                >
                    <stop offset={0} stopColor={color1} />
                    <stop offset={1} stopColor={color2} />
                </linearGradient>
                <path id="bg_card___" d="M 25,0 h 75 v 128 h -100 z" />
                <clipPath id="bg_clip___">
                    <use xlinkHref="#bg_card___" />
                </clipPath>
                <path id="hex_l1___" d={"M0 " + s32 + "Q 0 0 0.75 -" + (s32 * 0.5)} fill="none" />
                <path id="hex_l2___" d={"M0 -" + s32 + "Q 0 0 -0.75 -" + (s32 * 0.5)} fill="none" />
                <g id="hex_tile1___">
                    {/* <use xlinkHref="#hex_th___" fill="#fff"/> */}
                    <use xlinkHref="#hex_l1___" stroke="black" strokeWidth="0.15" />
                    <use xlinkHref="#hex_l1___" stroke="white" strokeWidth="0.1" />
                    <use xlinkHref="#hex_l1___" stroke="black" strokeWidth="0.15" transform={rot60} />
                    <use xlinkHref="#hex_l1___" stroke="white" strokeWidth="0.1" transform={rot60} />
                    <use xlinkHref="#hex_l2___" stroke="black" strokeWidth="0.15" />
                    <use xlinkHref="#hex_l2___" stroke="white" strokeWidth="0.1" />
                </g>
            </defs>
        </svg>
    );
}
const AppBg2_1 = ({ color1, color2 }: { color1: string, color2: string }) => {

    const s32 = Math.sqrt(3) * .5;
    const s15 = 1.5;
    const rot60 = "rotate(60 0 0)";

    const rots = [0, 60, 120, 180, 240, 300].map(x => (` rotate(${x} 0 0)`));
    const r = [1, 2, 4, 2,
        5, 4, 2, 3,
        4, 1, 0, 3,
        4, 5, 2, 1
    ];

    return (
        <svg height={"128px"} viewBox="-200 0 300 128" >
            <g >
                <g transform="scale(20)">
                    <use xlinkHref="#hex_tile1___" transform={"translate(-10.5," + (1 * s32) + ")" + rots[r[12]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-10.5," + (3 * s32) + ")" + rots[r[13]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-10.5," + (5 * s32) + ")" + rots[r[14]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-10.5," + (7 * s32) + ")" + rots[r[15]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-9.0," + (0 * s32) + ")" + rots[r[7]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-9.0," + (2 * s32) + ")" + rots[r[8]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-9.0," + (4 * s32) + ")" + rots[r[9]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-9.0," + (6 * s32) + ")" + rots[r[10]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-9.0," + (8 * s32) + ")" + rots[r[11]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-7.5," + (1 * s32) + ")" + rots[r[12]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-7.5," + (3 * s32) + ")" + rots[r[13]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-7.5," + (5 * s32) + ")" + rots[r[14]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-7.5," + (7 * s32) + ")" + rots[r[15]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-6.0," + (0 * s32) + ")" + rots[r[7]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-6.0," + (2 * s32) + ")" + rots[r[8]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-6.0," + (4 * s32) + ")" + rots[r[9]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-6.0," + (6 * s32) + ")" + rots[r[10]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-6.0," + (8 * s32) + ")" + rots[r[11]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-4.5," + (1 * s32) + ")" + rots[r[12]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-4.5," + (3 * s32) + ")" + rots[r[13]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-4.5," + (5 * s32) + ")" + rots[r[14]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-4.5," + (7 * s32) + ")" + rots[r[15]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-3.0," + (0 * s32) + ")" + rots[r[7]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-3.0," + (2 * s32) + ")" + rots[r[8]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-3.0," + (4 * s32) + ")" + rots[r[9]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-3.0," + (6 * s32) + ")" + rots[r[10]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-3.0," + (8 * s32) + ")" + rots[r[11]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-1.5," + (1 * s32) + ")" + rots[r[3]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-1.5," + (3 * s32) + ")" + rots[r[4]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-1.5," + (5 * s32) + ")" + rots[r[5]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(-1.5," + (7 * s32) + ")" + rots[r[6]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (0 * s32) + ")" + rots[r[0]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (2 * s32) + ")" + rots[r[0]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (4 * s32) + ")" + rots[r[0]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (6 * s32) + ")" + rots[r[1]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(0.0," + (8 * s32) + ")" + rots[r[2]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (1 * s32) + ")" + rots[r[3]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (3 * s32) + ")" + rots[r[4]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (5 * s32) + ")" + rots[r[5]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(1.5," + (7 * s32) + ")" + rots[r[6]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (0 * s32) + ")" + rots[r[7]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (2 * s32) + ")" + rots[r[8]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (4 * s32) + ")" + rots[r[9]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (6 * s32) + ")" + rots[r[10]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(3.0," + (8 * s32) + ")" + rots[r[11]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (1 * s32) + ")" + rots[r[12]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (3 * s32) + ")" + rots[r[13]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (5 * s32) + ")" + rots[r[14]]} />
                    <use xlinkHref="#hex_tile1___" transform={"translate(4.5," + (7 * s32) + ")" + rots[r[15]]} />
                </g> </g>
            <defs>
                <linearGradient
                    id={"appbg_svg___" + color1 + "_" + color2}
                    x1={25} x2={100} y1={0} y2={128}
                    gradientUnits="userSpaceOnUse"
                >
                    <stop offset={0} stopColor={color1} />
                    <stop offset={1} stopColor={color2} />
                </linearGradient>
                <path id="bg_card___" d="M 25,0 h 75 v 128 h -100 z" />
                <clipPath id="bg_clip___">
                    <use xlinkHref="#bg_card___" />
                </clipPath>
                <path id="hex_l1___" d={"M0 " + s32 + "Q 0 0 0.75 -" + (s32 * 0.5)} fill="none" />
                <path id="hex_l2___" d={"M0 -" + s32 + "Q 0 0 -0.75 -" + (s32 * 0.5)} fill="none" />
                <g id="hex_tile1___">
                    {/* <use xlinkHref="#hex_th___" fill="#fff"/> */}
                    <use xlinkHref="#hex_l1___" stroke="black" strokeWidth="0.15" />
                    <use xlinkHref="#hex_l1___" stroke="white" strokeWidth="0.1" />
                    <use xlinkHref="#hex_l1___" stroke="black" strokeWidth="0.15" transform={rot60} />
                    <use xlinkHref="#hex_l1___" stroke="white" strokeWidth="0.1" transform={rot60} />
                    <use xlinkHref="#hex_l2___" stroke="black" strokeWidth="0.15" />
                    <use xlinkHref="#hex_l2___" stroke="white" strokeWidth="0.1" />
                </g>
            </defs>
        </svg>
    );
}

const AppBg3 = ({ color1, color2 }: { color1: string, color2: string }) => {

    const s32 = Math.sqrt(3) * .5;
    const fill = "url(#appbg_svg___" + color1 + "_" + color2 + ") #fff";
    const hexRxy = (r: number, dx: number, dy: number) => ("M" + (-r + dx) + " " + (dy) + "L" + (-0.5 * r + dx) + " " + (s32 * r + dy) + "H" + (0.5 * r + dx) + "L" + (r + dx) + " " + (dy) + "L" + (0.5 * r + dx) + " " + (-s32 * r + dy) + "H" + (-0.5 * r + dx) + "Z")

    const hR = 22;
    const hScale = (s: number) => (1 - 0.05 * s);
    const dX = hR * 1.5;
    const dY = hR * s32;

    const hPath = (x: number, y: number) => (hexRxy(hR * hScale(3 - x), x * dX, y * dY));

    return (
        <svg height={"128px"} viewBox="0 0 100 128" >
            <g fill={fill} clipPath="url(#bg_clip___)">
                {/* <use xlinkHref="#bg_card___" /> */}
                <path d={hPath(0, 1)} />
                <path d={hPath(0, 3)} />
                <path d={hPath(0, 5)} />
                <path d={hPath(0, 7)} />
                <path d={hPath(0, 9)} />
                <path d={hPath(1, 0)} />
                <path d={hPath(1, 2)} />
                <path d={hPath(1, 4)} />
                <path d={hPath(1, 6)} />
                <path d={hPath(1, 8)} />
                <path d={hPath(2, 1)} />
                <path d={hPath(2, 3)} />
                <path d={hPath(2, 5)} />
                <path d={hPath(2, 7)} />
                <path d={hPath(2, 9)} />
                <path d={hPath(3, 0)} />
                <path d={hPath(3, 2)} />
                <path d={hPath(3, 4)} />
                <path d={hPath(3, 6)} />
                <path d={hPath(3, 8)} />
            </g>

            <defs>
                <linearGradient
                    id={`appbg_svg___${color1}_${color2}`}
                    x1={25} x2={100} y1={0} y2={128}
                    gradientUnits="userSpaceOnUse"
                >
                    <stop offset={0} stopColor={color1} />
                    <stop offset={1} stopColor={color2} />
                </linearGradient>
                <path id="bg_card___" d="M 25,0 h 75 v 128 h -100 z" />
                <path id="hex_th1___" d={"M-1 0L-0.5" + (s32) + "H0.5L1 0L0.5 -" + (s32) + "H-0.5Z"} />
                <clipPath id="bg_clip___" >
                    <use xlinkHref="#bg_card___" />
                </clipPath>
            </defs>
        </svg>
    );
}

export const AppLogo = ({ size, hash }: { size: string, hash: number }) => {
    const i1 = (hash >>> 30) & 3;
    const i2 = (hash >>> 20) & 3;
    const c1 = [i1 % 3, (i1 + 1) % 3, (i1 + 2) % 3];
    const c2 = [i2 % 3, (i2 + 1) % 3, (i2 + 2) % 3];
    const appC = appColor(hash);
    const centerC = nColors - 1;
    //const centerC = appC;

    const rot = [0, 15, 30];
    const i3 = (hash >>> 10) % rot.length;
    

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
            <g transform={`rotate(${rot[i3]} 0 0)`} >
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


const AppRibbonContainer = styled(Absolute)`
    transition: ease 0.2s;
    &:hover {
        top: 0
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

export const NewApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => {
    const appDesc = app.description;
    const hash = hashF(appDesc.title);
    const appC = appColor(hash);
    return (
        <NewAppCard to={linkToRun ? Pages.runApplication(app) : Pages.viewApplication(app)}>
            <Absolute right={0} top={0} cursor="inherit" >
                <AppBg_triangle {...bgGradients[appC]} />
            </Absolute>
            {!onFavorite ? null :
                <AppRibbonContainer
                    cursor="inherit"
                    right={0}
                    top={isFavorite ? 0 : -30}
                    onClick={e => !!onFavorite ? (e.preventDefault(), onFavorite(appDesc.info.name, appDesc.info.version)) : undefined}
                >
                    <Icon name={"starRibbon"} color="red" size={48} />
                </AppRibbonContainer>
            }
            <Flex flexDirection={"row"} alignItems={"flex-start"} zIndex={1}>
                <AppLogo size={"48px"} hash={hash} />
                <Flex flexDirection={"column"} ml="10px">
                    <Heading.h4>{appDesc.title}</Heading.h4>
                    <EllipsedText width={200} title={`by ${appDesc.authors.join(", ")}`} color="gray">
                        by {appDesc.authors.join(", ")}
                    </EllipsedText>
                </Flex>
            </Flex>
            <Box mt="auto" />
            <Flex flexDirection={"row"} alignItems={"flex-start"} zIndex={1}>
                {appDesc.tags.map((tag, idx) => <Tag label={tag} key={idx} />)}
            </Flex>
        </NewAppCard>
    );
};
