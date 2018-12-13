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

const AppBg = (props) => (
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

const AppLogo = ({ size, ...props }) => (
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

export const NewApplicationCard: React.FunctionComponent<ApplicationCardProps> = ({ app, onFavorite, isFavorite, linkToRun }: ApplicationCardProps) => {
    const appDesc = app.description;
    return (
        <NewAppCard to={linkToRun ? Pages.runApplication(app) : Pages.viewApplication(app)}>
            <Absolute right={0} top={0}>
                <AppBg />
            </Absolute>
            { !onFavorite ? null :
	            <AppRibbonContainer right={0} 
	                      top={isFavorite ? 0 : -30}
	                      onClick={e => !!onFavorite ? (e.preventDefault(), onFavorite(app.description.info.name, app.description.info.version)) : undefined}
	            >
	                <Icon name={"starRibbon"} color="red" size={48}/>
	            </AppRibbonContainer>
	        }
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
                {appDesc.tags.map((tag, idx) => <Tag label={tag} key={idx} />)}
            </Flex>
        </NewAppCard>
    );
};
