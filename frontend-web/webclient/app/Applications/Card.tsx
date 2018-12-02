import * as React from "react";
import { MaterialColors } from "Assets/materialcolors.json";
import { Link, Image, Button } from "ui-components";
import { Relative, Box, Absolute, Text, Icon, Flex, RatingBadge, Card } from "ui-components";
import { EllipsedText } from "ui-components/Text";
import { PlayIcon } from "ui-components/Card";
import { Application } from ".";
import styled from "styled-components";
import * as ReactMarkdown from "react-markdown";
import * as Heading from "ui-components/Heading"

const COLORS_KEYS = Object.keys(MaterialColors);
const circuitBoard = require("Assets/Images/circuitboard-bg.png");

interface ApplicationCardProps {
    favoriteApp?: (name: string, version: string) => void,
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
    const appInfo = props.app.description.info;
    return (
        <AppCardBase to={props.linkToRun ? `/applications/${appInfo.name}/${appInfo.version}` : `/applications/details/${appInfo.name}/${appInfo.version}`}>
            <img src={circuitBoard} />
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

export const ApplicationCard = ({ app, favoriteApp, isFavorite, linkToRun }: ApplicationCardProps) => (
    <Card width="250px">
        <Relative height="135px">
            <Box>
                <Box style={{ background: hexFromAppName(app.description.title) }}>
                    <Image
                        src={circuitBoard}
                        style={{ opacity: 0.4 }}
                    />
                </Box>
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
                        v {app.description.info.version}
                    </Text>
                </Absolute>
                <Absolute top="10px" right="10px">
                    <Icon
                        onClick={() => !!favoriteApp ? favoriteApp(app.description.info.name, app.description.info.version) : undefined}
                        cursor="pointer"
                        color="red"
                        name={isFavorite ? "starFilled" : "starEmpty"}
                    />
                </Absolute>
                <Absolute bottom="10px" left="10px">
                    <EllipsedText width={180} title={`by ${app.description.authors.join(", ")}`} color="white">
                        by {app.description.authors.join(", ")}
                    </EllipsedText>
                </Absolute>
                <Absolute bottom="10px" right="10px">
                    <Link to={linkToRun ? `/applications/${app.description.info.name}/${app.description.info.version}` : `/applications/details/${app.description.info.name}/${app.description.info.version}`}>
                        <PlayIcon />
                    </Link>
                </Absolute>
            </Box>
        </Relative>
        <Box m="10px">
            <Text>
                {app.description.description.slice(0, 100)}
            </Text>
        </Box>
    </Card >
);

function hexFromAppName(name: string): string {
    const hashCode = toHashCode(name);
    const color = COLORS_KEYS[(hashCode % COLORS_KEYS.length)];
    const mClength = MaterialColors[color].length;
    return MaterialColors[color][(hashCode % mClength)];
}

function toHashCode(name: string): number {
    let hash = 0;
    if (name.length == 0) { // FIXME can this ever happen?
        return hash;
    }
    for (let i = 0; i < name.length; i++) {
        let char = name.charCodeAt(i);
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32bit integer
    }
    return Math.abs(hash);
}


const NewAppCard = styled(Link)`
    padding: 10px;
    width: 30%;
    min-width: 350px;
    height: 128px;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    // border: 2px solid ${(props) => props.theme.colors.midGray};
    border-radius: ${props => props.theme.radius};
    // background-color: ${(props) => props.theme.colors.lightGray};
    background-color: #ebeff3;
    position: relative;
    flex: 1 0 auto;
    overflow: hidden;
`;

const Tag = ( {label}: {label: string} ) => (
    <RatingBadge mr={"3px"} bg={"darkGray"}><Heading.h6>{label}</Heading.h6></RatingBadge>
)

const AppBg= (props) => (
    <svg height={"128px"} viewBox="0 0 100 128" >
        <path d="M 25,0 h 75 v 128 h -100 z" fill="url(#appbg_svg___Linear1)" />
        <defs>
            <linearGradient
                id="appbg_svg___Linear1"
                x1={25} x2={100} y1={0} y2={128}
                gradientUnits="userSpaceOnUse"
            >
                <stop offset={0} stopColor="#0096ff" />
                <stop offset={1} stopColor="#043eff" />
            </linearGradient>
        </defs>
    </svg>
);

const AppLogo = ({size, ...props}) => (
    <svg width={size} height={size} viewBox="-1000 -1000 2000 2000" >
        <clipPath id="myClip">
            <rect x="-1000" y="-1000" width="2000" height="2000" rx="500" ry="500" />
        </clipPath>
        <g clip-path="url(#myClip)" >
            <g transform="rotate(15 0 0)">
                <ellipse cx="0" cy="0" rx="1600" ry="400" fill="#0096ff" fill-opacity=".85" transform="translate(0 800)" />
                <ellipse cx="0" cy="0" rx="400" ry="1600" fill="#ff2600" fill-opacity=".85" transform="translate(-800 0)" />
                <ellipse cx="0" cy="0" rx="400" ry="1600" fill="#008f00" fill-opacity=".85" transform="translate(800 0)" />
                <ellipse cx="0" cy="0" rx="1600" ry="400" fill="#ff9300" fill-opacity=".85" transform="translate(0 -800)" />
            </g></g>
    </svg>
);

export const NewApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, favoriteApp, isFavorite, linkToRun }: ApplicationCardProps) => {
    const appDesc = app.description;
    return (
        <NewAppCard to={linkToRun ? `/applications/${appDesc.info.name}/${appDesc.info.version}` : `/applications/details/${appDesc.info.name}/${appDesc.info.version}`}>
            <Absolute right={0} top={0}>
                <AppBg />
            </Absolute>
            <Absolute right={0} 
                      top={isFavorite ? 0 : -30}
                      onClick={(e) => !!favoriteApp ? (e.preventDefault(), favoriteApp(app.description.info.name, app.description.info.version)) : undefined}
                      >
                <Icon name={"appFav"} color="red" size={48}/>
            </Absolute>
            <Flex flexDirection={"row"} alignItems={"flex-start"}>
                <AppLogo size={"48px"} />
                <Flex flexDirection={"column"} ml="10px">
                    <Heading.h4>{app.description.title}</Heading.h4>
                    <EllipsedText width={200} title={`by ${appDesc.authors.join(", ")}`} color="gray">
                        by {appDesc.authors.join(", ")}
                    </EllipsedText>
                </Flex>
            </Flex>
            <Box mt="auto" />
            <Flex flexDirection={"row"} alignItems={"flex-start"}>
                <Tag label="Singularity" />
                <Tag label="Biocontainers" />
                <Tag label="Toys" />
                <Tag label="Health Science " />
            </Flex>
        </NewAppCard>
    );
};
