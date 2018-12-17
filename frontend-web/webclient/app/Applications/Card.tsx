import * as React from "react";
import { Link, Image } from "ui-components";
import { Relative, Box, Absolute, Text, Icon, Flex, RatingBadge, Card } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import * as Pages from "./Pages";
import { Application } from ".";
import styled from "styled-components";
import * as ReactMarkdown from "react-markdown";
import * as Heading from "ui-components/Heading"
import { bgColor } from "styled-system";
import { SSL_OP_NO_QUERY_MTU } from "constants";

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
                <ReactMarkdown
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
    width: 30%;
    min-width: 350px;
    height: 128px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    border-radius: ${props => props.theme.radius};
    background-color: #ebeff3;
    position: relative;
    /* flex: 1 0 auto; */
    overflow: hidden;
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


// Colors in the array come in 3 shades: light, medium , dark
// last color is for logo centers only
const appColors = [
    //["#0096ff", "#043eff"], // blue
    ["#F7D06A", "#E98C33", "#C46927"], // gold
    ["#EC6F8E", "#C75480", "#AA2457"], // salmon
    ["#B8D1E3", "#7C8DB3", "#5B698C"], // silver
    ["#83D8F9", "#3F80F6", "#2951BE"], // blue
    ["#AE83CF", "#9065D1", "#68449E"], // violet
    ["#E392CC", "#E2689D", "#B33B6D"], // pink
    ["#ECB08C", "#EA7B4B", "#BC4F33"], // bronze
    ["#90DCA1", "#69C97D", "#4D9161"], // green
    ["#F3B576", "#B77D50", "#7C4C3C"], // brown
    ["#D57AC5", "#E439C9", "#A1328F"], // purple
    ["#98E0F9", "#53A5F5", "#3E79C0"], // lightblue
    ["#DC6AA6", "#C62A5A", "#AA2457"], // red
    ["#c9d3df", "#8393A7", "#53657D"], // gray colors from the theme
];
const nColors = appColors.length;

const bgGradients = appColors.map(x => ({ color1: x[0], color2: x[2] }));

const AppBg = ({ color1, color2 }: { color1: string, color2: string }) => (
    <svg height={"128px"} viewBox="0 0 100 128" >
        <path d="M 25,0 h 75 v 128 h -100 z" fill={"url(#appbg_svg___"+color1+"_"+color2} />
        <defs>
            <linearGradient
                id={"appbg_svg___"+color1+"_"+color2}
                x1={25} x2={100} y1={0} y2={128}
                gradientUnits="userSpaceOnUse"
            >
                <stop offset={0} stopColor={color1} />
                <stop offset={1} stopColor={color2} />
            </linearGradient>
        </defs>
    </svg>
);

export const AppLogo = ({ size, hash }: { size: string, hash: number }) => {
    const i1=(hash>>>30)&3;
    const i2=(hash>>>20)&3;
    const c1 = [i1%3, (i1+1)%3, (i1+2)%3];
    const c2 = [i2%3, (i2+1)%3, (i2+2)%3];
    const appC = appColor(hash);
    //const centerC = nColors-1;
    const centerC = appC;

    const i3=(hash>>>10)&3;
    const rot = [0, 15, 30];

    const s32=Math.sqrt(3)*.5;
    const r1 = 0.5; //inner radius of outer element (outer radius is 1)
    const r2 = 0.7; //outer radius of inner element
    const r3 = (1+r2)*.5; // radius of white background hexagon 

    return (
    <svg
        width={size} height={size}
        viewBox={"-1 -"+s32+" 2 "+(2*s32)}
        fillRule="evenodd"
            clipRule="evenodd"
        >
        <defs>
            <path id="hex_to___" d={"M-"+r1+" 0H-1L-0.5 "+s32+"H0.5L"+(0.5*r1)+" "+(s32*r1)+"H-"+(0.5*r1)+"Z"} />
            <path id="hex_ti___" d={"M0 0H"+r2+"L"+(0.5*r2)+" -"+(s32*r2)+"H-"+(0.5*r2)+"Z"} fill-opacity=".55"/>
            <path id="hex_th___" d={"M-"+r3+" 0L-"+(0.5*r3)+" "+(s32*r3)+"H"+(0.5*r3)+"L"+r3+" 0L"+(0.5*r3)+" -"+(s32*r3)+"H-"+(0.5*r3)+"Z"} />
        </defs>
        <g  transform={"rotate("+rot[i3]+" 0 0)"} >
            <use xlinkHref="#hex_th___" fill="#fff"/>
            <use xlinkHref="#hex_to___" fill={appColors[appC][c1[0]]}/>
            <use xlinkHref="#hex_to___" fill={appColors[appC][c1[1]]} transform="rotate(120 0 0)"/>
            <use xlinkHref="#hex_to___" fill={appColors[appC][c1[2]]} transform="rotate(240 0 0)"/>         
            <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[0]]}/>
            <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[1]]} transform="rotate(120 0 0)"/>
            <use xlinkHref="#hex_ti___" fill={appColors[centerC][c2[2]]} transform="rotate(240 0 0)"/>
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


export function hashF(str: string):number {
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

function appColor(hash: number):number {
    return (hash>>>22)%(nColors-1); //last color not used
}

export const NewApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => {
    const appDesc = app.description;
    const hash=hashF(appDesc.title);
    const appC=appColor(hash);
    return (
        <NewAppCard to={linkToRun ? Pages.runApplication(app) : Pages.viewApplication(app)}>
            <Absolute right={0} top={0} cursor="inherit">
                <AppBg {...bgGradients[appC]}/>
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
            <Flex flexDirection={"row"} alignItems={"flex-start"}>
                <AppLogo size={"48px"} hash={hash}/>
                <Flex flexDirection={"column"} ml="10px">
                    <Heading.h4>{appDesc.title}</Heading.h4>
                    <EllipsedText width={200} title={`by ${appDesc.authors.join(", ")}`} color="gray">
                        by {appDesc.authors.join(", ")}
                    </EllipsedText>
                </Flex>
            </Flex>
            <Box mt="auto" />
            <Flex flexDirection={"row"} alignItems={"flex-start"}>
                {appDesc.tags.map((tag, idx) => <Tag label={tag} key={idx} />)}
            </Flex>
        </NewAppCard>
    );
};
