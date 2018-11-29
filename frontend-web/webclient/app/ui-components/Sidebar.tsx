import * as React from "react";
import styled from "styled-components";
import Text from "./Text";
import Icon, { IconName } from "./Icon";
import Flex from "./Flex";
import Box from "./Box";
import Link from "./Link";
import Divider from "./Divider";
import { Cloud } from "Authentication/SDUCloudObject";
import { PP } from "UtilityComponents";
import { fileTablePage } from "Utilities/FileUtilities";

const SidebarContainer = styled(Flex)`
    position: fixed;
    top:48px;
    //width: 190px;
    //margin-top: 48px;
    height: calc(100% - 48px);
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors.borderGray}
`;

const SidebarElementContainer = styled(Flex)`
    justify-content: left;
    flex-flow: row;
    align-items: center;
    :hover {
        svg {
            filter: saturate(500%);
        }
    }
`
interface SidebarElementProps { icon: IconName, label: string, showLabel: boolean, to: string }
const SidebarElement = ({ icon, label, showLabel, to }: SidebarElementProps) => (
    <Link to={to}>
        <SidebarElementContainer >
            <Flex mx="22px" alignItems='center'>
                <Icon cursor="pointer" name={icon} color="iconColor" color2="iconColor2" size="24" />
            </Flex>
            {showLabel &&
                <Text cursor="pointer" fontSize={3} bold>
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

const SidebarInfoBox = styled.div`
    flex-shrink: 0;
    margin: 22px;
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

type MenuElement = { icon: IconName, label: string, to: string };
type SidebarMenuElements = {
    items: MenuElement[]
    predicate: () => boolean
}

export const sideBarMenuElements: { general: SidebarMenuElements, auditing: SidebarMenuElements, admin: SidebarMenuElements } = {
    general: {
        items: [
            { icon: "dashboard", label: "Dashboard", to: "/dashboard/" },
            { icon: "files", label: "Files", to: fileTablePage(Cloud.homeFolder) },
            { icon: "share", label: "Shares", to: "/shares/" },
            { icon: "apps", label: "Apps", to: "/applications/" },
            { icon: "information", label: "Job Results", to: "/applications/results/" },
            { icon: "publish", label: "Publish", to: "/zenodo/publish/" },
        ], predicate: () => true
    },
    auditing: { items: [{ icon: "activity", label: "Activity", to: "/activity/" }], predicate: () => true },
    admin: { items: [{ icon: "admin", label: "Admin", to: "/admin/userCreation/" }], predicate: () => Cloud.userIsAdmin }
};

const Sidebar = ({ sideBarEntries = sideBarMenuElements, showLabel = true }: { sideBarEntries?: any, showLabel?: boolean }) => {
    let sidebar = Object.keys(sideBarEntries)
        .map(key => sideBarEntries[key])
        .filter(it => it.predicate());
    return (
        <SidebarContainer color="text" bg="lightGray" width={190}>
            {sidebar.map((category, categoryIdx) =>
                <React.Fragment key={categoryIdx}>
                    {category.items.map(({ icon, label, to }: MenuElement) => (
                        <React.Fragment key={label}>
                            {categoryIdx === 0 ? <SidebarSpacer /> : null}
                            <SidebarElement icon={icon} label={label} showLabel={showLabel} to={to} />
                        </React.Fragment>))}
                    {categoryIdx !== sidebar.length - 1 ? (<Divider mt="10px" mb="10px" />) : null}
                </React.Fragment>
            )}
            <SidebarPushToBottom />

            <SidebarInfoBox>
                <div>ID: {Cloud.username}</div>
                <div>
                    <a href="https://www.sdu.dk/en/om_sdu/om_dette_websted/databeskyttelse" target="_blank" rel="noopener">Data Protection at SDU</a>
                </div>
            </SidebarInfoBox>
            <PP visible={false} />

        </SidebarContainer>
    );
};

export default Sidebar;