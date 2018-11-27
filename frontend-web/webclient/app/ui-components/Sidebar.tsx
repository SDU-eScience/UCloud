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

const SideBarContainer = styled(Flex)`
    position: fixed;
    top:48px;
    //width: 190px;
    //margin-top: 48px;
    height: 100%;
    flex-flow: column;
    border-right: 1px solid ${props => props.theme.colors.borderGray}
`;

const SideBarElementContainer = styled(Flex)`
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
const SideBarElement = ({ icon, label, showLabel, to }: SidebarElementProps) => (
    <Link to={to}>
        <SideBarElementContainer >
            <Flex mx="22px" alignItems='center'>
                <Icon cursor="pointer" name={icon} color="iconColor" color2="iconColor2" size="24" />
            </Flex>
            {showLabel &&
                <Text cursor="pointer" fontSize={3} >
                    {label}
                </Text>
            }
        </SideBarElementContainer>
    </Link>
);

const SideBarSpacer = () => (
    <Box mt="20px" />
);

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
        <SideBarContainer color="text" bg="lightGray" width={190}>
            {sidebar.map((it, iteration) =>
                <React.Fragment key={iteration}>
                    {it.items.map(({ icon, label, to }: { icon: IconName, label: string, to: string }) => (
                        <React.Fragment key={label}>
                            {iteration === 0 ? <SideBarSpacer /> : null}
                            <SideBarElement icon={icon} label={label} showLabel={showLabel} to={to} />
                        </React.Fragment>))}
                    {iteration !== sidebar.length - 1 ? (<Divider mt="10px" mb="10px" />) : null}
                </React.Fragment>
            )}
            <PP visible={false} />
        </SideBarContainer>
    );
};

export default Sidebar;