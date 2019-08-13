import * as React from "react";
import styled, {css} from "styled-components";
import Text, {EllipsedText} from "./Text";
import Icon, {IconName} from "./Icon";
import Flex from "./Flex";
import Box from "./Box";
import Link from "./Link";
import Divider from "./Divider";
import {Cloud} from "Authentication/SDUCloudObject";
import {fileTablePage} from "Utilities/FileUtilities";
import ExternalLink from "./ExternalLink";
import RatingBadge from "./RatingBadge"
import Tooltip from "./Tooltip";
import RBox from "./RBox";
import {ReduxObject} from "DefaultObjects"
import {connect} from 'react-redux'
import {FlexCProps} from "./Flex";
import {inDevEnvironment, copyToClipboard} from "UtilityFunctions";
import {ContextSwitcher} from "Project/ContextSwitcher";

const SidebarElementContainer = styled(Flex) <{hover?: boolean, active?: boolean}>`
    justify-content: left;
    flex-flow: row;
    align-items: center;

    & > ${Text} {
        white-space: nowrap;
    }
`;

//This is applied to SidebarContainer on small screens
const HideText = css`
${({theme}) => theme.mediaQueryLT["xl"]} {
    
    will-change: transform;
    transition: transform ${({theme}) => theme.timingFunctions.easeOut} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
    transform: translate(-122px,0); //122 = 190-68 (original - final width)

    & ${Icon},${RatingBadge} {
        will-change: transform;
        transition: transform ${({theme}) => theme.timingFunctions.easeOut} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        transform: translate(122px,0); //inverse transformation; same transition function!
    }

    & ${SidebarElementContainer} > ${Text} {
        // transition: opacity ${({theme}) => theme.timingFunctions.easeOutQuit} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        transition: opacity ${({theme}) => theme.timingFunctions.stepStart} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
        opacity: 0;
        will-change: opacity;
    }


    &:hover { 
            transition: transform ${({theme}) => theme.timingFunctions.easeIn} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
            transform: translate(0,0);

            & ${Icon},${RatingBadge} {
                transition: transform ${({theme}) => theme.timingFunctions.easeIn} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                transform: translate(0,0); //inverter transformation
            }

            ${SidebarElementContainer} > ${Text} {
                // transition: opacity ${({theme}) => theme.timingFunctions.easeInQuint} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                transition: opacity ${({theme}) => theme.timingFunctions.stepEnd} ${({theme}) => theme.duration.fastest} ${({theme}) => theme.transitionDelays.xsmall};
                opacity: 1;
            }
        
    }
    
}
`;

const SidebarContainer = styled(Flex) <FlexCProps>`
    position: fixed;
    z-index: 80;
    top: 0;
    left: 0;
    padding-top: 48px;
    height: 100%;
    background-color: ${props => props.theme.colors.lightGray}; 
    // border-right: 1px solid ${props => props.theme.colors.borderGray};
    //background: linear-gradient(135deg, rgba(246,248,249,1) 0%,rgba(229,235,238,1) 69%,rgba(215,222,227,1) 71%,rgba(245,247,249,1) 100%);
    ${HideText}
`;

interface TextLabelProps {
    icon: IconName
    children: React.ReactText | JSX.Element
    ml?: string
    height?: string
    color?: string
    color2?: string
    iconSize?: string
    textSize?: number
    space?: string
    hover?: boolean
    title?: string
}

export const SidebarTextLabel = (
    {
        icon, children, title, height = "30px", color = "iconColor", color2 = "iconColor2",
        iconSize = "24", space = "22px", textSize = 3, hover = true
    }: TextLabelProps
) => (
        <SidebarElementContainer title={title} height={height} ml="22px" hover={hover}>
            <Icon name={icon} color={color} color2={color2} size={iconSize} mr={space} />
            <Text fontSize={textSize}> {children} </Text>
        </SidebarElementContainer>
    );

const SidebarLink = styled(Link) <{active?: boolean}>`
    ${props => props.active ?
        `&:not(:hover) > * > ${Text} { 
            color: ${props.theme.colors.blue};
        }
        &:not(:hover) > * > ${Icon} { 
            filter: saturate(500%);
        }        
    ` : null}

    
    text-decoration: none;

    &:hover > ${Text}, &:hover > * > ${Icon} {
        filter: saturate(500%);
    }
`;

interface SidebarElement {
    icon: IconName,
    label: string,
    to: string,
    external?: boolean,
    activePage: SidebarPages
}

const SidebarElement = ({icon, label, to, activePage}: SidebarElement) => (
    <SidebarLink to={to} active={enumToLabel(activePage) === label ? true : undefined}>
        <SidebarTextLabel icon={icon}>{label}</SidebarTextLabel>
    </SidebarLink>
);

function enumToLabel(value: SidebarPages): string {
    switch (value) {
        case SidebarPages.Files:
            return "Files";
        case SidebarPages.Shares:
            return "Shares";
        case SidebarPages.Favorites:
            return "Favorites";
        case SidebarPages.AppStore:
            return "Apps";
        case SidebarPages.Runs:
            return "Runs";
        case SidebarPages.Publish:
            return "Publish";
        case SidebarPages.Activity:
            return "Activity";
        case SidebarPages.Admin:
            return "Admin";
        default:
            return "";
    }
}

const SidebarSpacer = () => (<Box mt="12px" />);

const SidebarPushToBottom = styled.div`
    flex-grow: 1;
`;

type MenuElement = {icon: IconName, label: string, to: string | (() => string)};
type SidebarMenuElements = {
    items: MenuElement[]
    predicate: () => boolean
}

export const sideBarMenuElements: {guest: SidebarMenuElements, general: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements} = {
    guest: {
        items: [
            {icon: "files", label: "Files", to: "/login"},
            {icon: "projects", label: "Projects", to: "/login"},
            {icon: "apps", label: "Apps", to: "/login"}
        ], predicate: () => !Cloud.isLoggedIn
    },
    general: {
        items: [
            {icon: "files", label: "Files", to: () => fileTablePage(Cloud.homeFolder)},
            {icon: "shareMenu", label: "Shares", to: "/shares/"},
            {icon: "starFilled", label: "Favorites", to: "/favorites"},
            {icon: "appStore", label: "Apps", to: "/applications/"},
            {icon: "results", label: "Runs", to: "/applications/results/"}
        ], predicate: () => Cloud.isLoggedIn
    },
    auditing: {items: [{icon: "activity", label: "Activity", to: "/activity/"}], predicate: () => Cloud.isLoggedIn},
    admin: {items: [{icon: "admin", label: "Admin", to: "/admin/userCreation/"}], predicate: () => Cloud.userIsAdmin}
};

interface SidebarStateProps {
    page: SidebarPages
    loggedIn: boolean
    activeProject?: string
}

interface SidebarProps extends SidebarStateProps {
    sideBarEntries?: any
}

const Sidebar = ({sideBarEntries = sideBarMenuElements, page, loggedIn}: SidebarProps) => {
    if (!loggedIn) return null;
    const sidebar = Object.keys(sideBarEntries)
        .map(key => sideBarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="sidebar" flexDirection="column" width={190}>
            {sidebar.map((category, categoryIdx) =>
                <React.Fragment key={categoryIdx}>
                    {category.items.map(({icon, label, to}: MenuElement) => (
                        <React.Fragment key={label}>
                            {categoryIdx === 0 ? <SidebarSpacer /> : null}
                            <SidebarElement icon={icon} activePage={page} label={label}
                                to={typeof to === "function" ? to() : to} />
                        </React.Fragment>))}
                    {categoryIdx !== sidebar.length - 1 ? (<Divider mt="6px" mb="6px" />) : null}
                </React.Fragment>
            )}
            <SidebarPushToBottom />
            {/* Screen size indicator */}
            {inDevEnvironment() ? <Flex mb={"5px"} width={190} ml={19} justifyContent="left"><RBox /> </Flex> : null}
            {Cloud.userRole === "ADMIN" ? <ContextSwitcher maxSize={140} /> : null}
            {Cloud.isLoggedIn ?
                <SidebarTextLabel height="25px" hover={false} icon="id" iconSize="1em" textSize={1} space=".5em"
                    title={Cloud.username || ""}>
                    <Tooltip
                        left="-50%"
                        top={"1"}
                        mb="35px"
                        trigger={
                            <EllipsedText
                                cursor="pointer"
                                onClick={() => copyToClipboard({
                                    value: Cloud.username,
                                    message: "Username copied to clipboard"
                                })}
                                width={"140px"}>{Cloud.username}</EllipsedText>
                        }>
                        {`Click to copy "${Cloud.username}" to clipboard`}
                    </Tooltip>
                </SidebarTextLabel> : null}
            <ExternalLink href="https://www.sdu.dk/en/om_sdu/om_dette_websted/databeskyttelse">
                <SidebarTextLabel height="25px" icon="verified" iconSize="1em" textSize={1}
                    space=".5em">
                    SDU Data Protection
                </SidebarTextLabel>
            </ExternalLink>
            <Box mb="10px" />
        </SidebarContainer>
    );
};

const mapStateToProps = ({status, project}: ReduxObject): SidebarStateProps => ({
    page: status.page,

    /* Used to ensure re-rendering of Sidebar after user logs in. */
    loggedIn: Cloud.isLoggedIn,

    /* Used to ensure re-rendering of Sidebar after project change. */
    activeProject: project.project
});

export const enum SidebarPages {
    Files,
    Shares,
    Favorites,
    AppStore,
    Runs,
    Publish,
    Activity,
    Admin,
    None
}

export default connect<SidebarStateProps>(mapStateToProps)(Sidebar);
