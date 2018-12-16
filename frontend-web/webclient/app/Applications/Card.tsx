import * as React from "react";
import { Link, Image } from "ui-components";
import { Relative, Box, Absolute, Text, Icon, Flex, RatingBadge, Card } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import * as Pages from "./Pages";
import { Application } from ".";
import styled from "styled-components";
import * as ReactMarkdown from "react-markdown";
import * as Heading from "ui-components/Heading"

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
            <img src={props.app.imageUrl} />
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

// const bgGradients2 = hues.map( x=> ( 
//     {
//         color1: hslToHex(x,60,60),
//         color2: hslToHex(x,60,40)
//     }
// ));

const bgGradients = [
    //["#0096ff", "#043eff"], // blue
    ["#F7D06A", "#C46927"], // golden
    ["#EC6F8E", "#AA2457"], // salmon
    ["#B8D1E3", "#5B698C"], // bluegray
    ["#83D8F9", "#2951BE"], // blue
    ["#AE83CF", "#68449E"], // violet
    ["#E392CC", "#B33B6D"], // pink
    ["#ECB08C", "#BC4F33"], // bronze
    ["#90DCA1", "#4D9161"], // green
    ["#F3B576", "#7C4C3C"], // brown
    ["#D57AC5", "#A1328F"], // purple
    ["#98E0F9", "#3E79C0"], // lightblue
    ["#DC6AA6", "#AA2457"] // red
//    ["#", "#"], //
].map(x => ({ color1: x[0], color2: x[1] }));

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

const AppLogo = ({ size }: { size: string }) => (
    <svg width={size} height={size} viewBox="-1000 -1000 2000 2000" >
        <clipPath id="myClip">
            <rect x="-1000" y="-1000" width="2000" height="2000" rx="500" ry="500" />
        </clipPath>
        <g clipPath="url(#myClip)" >
            <g transform="rotate(15 0 0)">
                <ellipse cx="0" cy="0" rx="1600" ry="400" fill="#0096ff" fillOpacity=".85" transform="translate(0 800)" />
                <ellipse cx="0" cy="0" rx="400" ry="1600" fill="#ff2600" fillOpacity=".85" transform="translate(-800 0)" />
                <ellipse cx="0" cy="0" rx="400" ry="1600" fill="#008f00" fillOpacity=".85" transform="translate(800 0)" />
                <ellipse cx="0" cy="0" rx="1600" ry="400" fill="#ff9300" fillOpacity=".85" transform="translate(0 -800)" />
            </g>
        </g>
    </svg>
);

const AppRibbonContainer = styled(Absolute)`
    transition: ease 0.2s;
    &:hover {
        top: 0
    }
`


function hashF(str: string):number {
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

function bgHash(hash: number):number {
    return (hash>>>22)%bgGradients.length;
}


let i=0;
export const NewApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => {
    const appDesc = app.description;
    const hash=hashF(appDesc.title);
    console.log(bgGradients);
    return (
        <NewAppCard to={linkToRun ? Pages.runApplication(app) : Pages.viewApplication(app)}>
            <Absolute right={0} top={0}>
                <AppBg {...bgGradients[bgHash(hash)]}/>
            </Absolute>
            {!onFavorite ? null :
                <AppRibbonContainer right={0}
                    top={isFavorite ? 0 : -30}
                    onClick={e => !!onFavorite ? (e.preventDefault(), onFavorite(appDesc.info.name, appDesc.info.version)) : undefined}
                >
                    <Icon name={"starRibbon"} color="red" size={48} />
                </AppRibbonContainer>
            }
            <Flex flexDirection={"row"} alignItems={"flex-start"}>
                <AppLogo size={"48px"} />
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
