import * as React from "react";
import styled from "styled-components";
import Text from "./Text";
import Icon, { IconName } from "./Icon";
import Flex from "./Flex";
import Box from "./Box";
import Link from "./Link";
import Divider from "./Divider";
import { Cloud } from "Authentication/SDUCloudObject";
import { fileTablePage } from "Utilities/FileUtilities";
import { ExternalLink } from "ui-components";
import RBox from "Responsive/ScreenSize";
import { ReduxObject, ResponsiveReduxObject } from "DefaultObjects"
import { connect } from 'react-redux'


const SidebarContainer = styled(Flex)`
    position: fixed;
    z-index: 80;
    top: 0;
    left: 0;
    padding-top: 48px;
    height: 100%;
    border-right: 1px solid ${props => props.theme.colors.borderGray};

    transition: ${({ theme }) => theme.timingFunctions.easeInOut} ${({ theme }) => theme.transitionDelays.small};

    :hover {
        width: 190px;
    }
`;

const SidebarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    // &:hover {
    //     svg {
    //         filter: saturate(500%);
    //     }
    // }
`;

const SidebarInfoBox = styled.div`
flex-shrink: 0;
margin: 18px;
color: ${props => props.theme.colors.iconColor};

& div {
    width: 100%;
}

& a {
    color: ${props => props.theme.colors.iconColor};
}

& a:hover {
    color: ${props => props.theme.colors.blue};
}
`;


interface SidebarElementProps { icon: IconName, label: string, showLabel: boolean, to: string }
const SidebarElement = ({ icon, label, showLabel, to }: SidebarElementProps) => (
    <Link to={to}>
        <SidebarElementContainer height="30px" >
            <Flex mx="22px" alignItems='center'>
                <Icon cursor="pointer" name={icon} color="iconColor" color2="iconColor2" size="24" 
                css={`${SidebarElementContainer} :hover { filter: saturate(500%); }`} />
            </Flex>
            {showLabel &&
                <Text cursor="pointer" fontSize={3} >
                    {label}
                </Text>
            }
        </SidebarElementContainer>
    </Link>
);

const SidebarSpacer = () => (
    <Box mt="20px" />
);

const SidebarPushToBottom = styled.div`
    flex-grow: 1;
`;

type MenuElement = { icon: IconName, label: string, to: string };
type SidebarMenuElements = {
    items: MenuElement[]
    predicate: () => boolean
}

export const sideBarMenuElements: { general: SidebarMenuElements, dev: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements } = {
    general: {
        items: [
            { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
            { icon: "files", label: "Files", to: fileTablePage(Cloud.homeFolder) },
            { icon: "share", label: "Shares", to: "/shares/" },
            { icon: "apps", label: "My Apps", to: "/applications/installed/" },
            { icon: "appStore", label: "App Store", to: "/applications/" },
            { icon: "results", label: "My Results", to: "/applications/results/" }
        ], predicate: () => true
    },
    dev: { items: [{ icon: "publish", label: "Publish", to: "/zenodo/publish/" }], predicate: () => process.env.NODE_ENV === "development" },
    auditing: { items: [{ icon: "activity", label: "Activity", to: "/activity/" }], predicate: () => true },
    admin: { items: [{ icon: "admin", label: "Admin", to: "/admin/userCreation/" }], predicate: () => Cloud.userIsAdmin }
};
interface SidebarProps {
    sideBarEntries?: any 
    responsiveState: ResponsiveReduxObject | undefined
}

const Sidebar = ({ sideBarEntries = sideBarMenuElements, responsiveState }: SidebarProps )  => {
    let sidebar = Object.keys(sideBarEntries)
        .map(key => sideBarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="text" bg="lightGray" 
            width={responsiveState!.greaterThan.xl ? 190 : 68}
            flexDirection="column"
            >
            {sidebar.map((category, categoryIdx) =>
                <React.Fragment key={categoryIdx}>
                    {category.items.map(({ icon, label, to }: MenuElement) => (
                        <React.Fragment key={label}>
                            {categoryIdx === 0 ? <SidebarSpacer /> : null}
                            <SidebarElement icon={icon} label={label} showLabel={responsiveState!.greaterThan.xl} to={to} />
                        </React.Fragment>))}
                    {categoryIdx !== sidebar.length - 1 ? (<Divider mt="10px" mb="10px" />) : null}
                </React.Fragment>
            )}
            <SidebarPushToBottom />
            {/* Screen size indicator */}
            { process.env.NODE_ENV === "development" ? <RBox /> : null } 
            <SidebarInfoBox> 
                <Text fontSize={1}><Icon name={"id"} size="1em" /> {responsiveState!.greaterThan.xl ? Cloud.username : null }</Text>
                <div>
                    <ExternalLink href="https://www.sdu.dk/en/om_sdu/om_dette_websted/databeskyttelse">
                        <Text fontSize={1}><Icon name="verified" size="1em" color2="lightGray" /> {responsiveState!.greaterThan.xl ? "SDU Data Protection": null}</Text>
                    </ExternalLink>
                </div>
            </SidebarInfoBox>
        </SidebarContainer>
    );
};

const mapStateToProps = ({ responsive }: ReduxObject): SidebarProps   => ({
    responsiveState: responsive
});

export default connect(mapStateToProps, null )(Sidebar);
