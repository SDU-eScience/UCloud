import * as React from "react";
import {ProjectInvite} from "@/Project/Api";
import {Project, ProjectGroup, ProjectMember, ProjectRole} from "@/Project";
import {Spacer} from "@/ui-components/Spacer";
import {Flex, MainContainer} from "@/ui-components";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {ProjectPageTitle} from "@/Project/Members";
import {injectStyle} from "@/Unstyled";
import {ListClass} from "@/ui-components/List";

export const TwoColumnLayout = injectStyle("two-column-layout", k => `
    ${k} {
        display: flex;
        flex-direction: row;
        flex-wrap: wrap;
        width: 100%;
    }

    ${k} > * {
        flex-basis: 100%;
    }

    @media screen and (min-width: 1200px) {
        ${k} > .left {
            border-right: 2px solid var(--borderColor, #f00);
            height: 100%;
            flex: 1;
            margin-right: 16px;
            padding-right: 16px;
        }

        ${k} > .right {
            flex: 1;
            height: 100%;
            overflow-y: auto;
            overflow-x: hidden;
        }
    }
`);

export const MembersContainer: React.FunctionComponent<{
    onInvite: (username: string) => void;
    onSearch: (newFilter: string) => void;
    onCreateLink: () => void;
    onAddToGroup: (username: string, groupId: string) => void;
    onRemoveFromGroup: (username: string, groupId: string) => void;
    onCreateGroup: (groupTitle: string) => void;
    onDeleteGroup: (groupId: string) => void;
    onChangeRole: (username: string, newRole: ProjectRole) => void;
    onRefresh: () => void;

    invitations: ProjectInvite[];
    project: Project
}> = props => {
    const members: ProjectMember[] = props.project.status.members ?? [];
    const groups: ProjectGroup[] = props.project.status.groups ?? [];

    return <MainContainer
        header={<Spacer
            left={<ProjectPageTitle>Members</ProjectPageTitle>}
            right={<Flex mr="36px" height={"26px"}><UtilityBar /></Flex>}
        />}
        headerSize={72}
        main={<div className={TwoColumnLayout}>
            <div className={"left"}>
                We have {members.length} members. The first one is {members[0].username} with role {members[0].role}.
            </div>

            <div className={"right"}>
                Right panel
            </div>
        </div>}
    />;
}
