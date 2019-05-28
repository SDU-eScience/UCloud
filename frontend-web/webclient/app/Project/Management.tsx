import * as React from "react";
import { MainContainer } from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import { Spacer } from "ui-components/Spacer";
import { Box, Button, Select, Flex } from "ui-components";
import { prettierString } from "UtilityFunctions";
import { connect } from "react-redux";
import { UserAvatar } from "Navigation/Header";
import { defaultAvatar } from "UserSettings/Avataaar";
import styled from "styled-components";
import { Dispatch } from "redux";
import { fetchProjectMembers, setError } from "./Redux/ManagementActions";
import { TextSpan } from "ui-components/Text";
import { getQueryParamOrElse, RouterLocationProps } from "Utilities/URIUtilities";
import { SnackType } from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";

export enum ProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    DATA_STEWARD = "DATA_STEWARD",
    USER = "USER"
}

interface ManagementState {
    projectName: string
    memberCount: number
    admins: string[]
    datastewards: string[]
    users: string[]
    project: any
}

class Management extends React.Component<ManagementOperations, ManagementState> {
    constructor(props) {
        super(props);
        this.state = {
            project: undefined,
            projectName: "projectName",
            memberCount: 5,
            admins: ["Foo", "Bar"],
            datastewards: ["Foo", "Bar"],
            users: ["Foo", "Bar"]
        };
        this.props.fetchProjectMembers(this.projectId(props));
    }

    private projectId = (props: RouterLocationProps): string => getQueryParamOrElse(props, "projectId", "");

    public render() {
        const { ...state } = this.state;
        const header = (<>
            <Heading.h3>Project Management: <b>{state.projectName}</b></Heading.h3>
            <Spacer
                left={<Box>{state.memberCount} Members</Box>}
                right={<Button onClick={() => snackbarStore.addSnack({
                    message: "Wouldn't it be great if this button worked?",
                    type: SnackType.Custom,
                    icon: "ellipsis"
                })}>Invite member</Button>}
            />
        </>)
        const main = (<>
            <MemberList
                members={state.admins}
                defaultValue={ProjectRole.ADMIN}
                title="Admins"
                update={() => undefined}
                remove={() => undefined}
            />

            <MemberList
                members={state.datastewards}
                defaultValue={ProjectRole.DATA_STEWARD}
                title="Data Stewards"
                update={() => undefined}
                remove={() => undefined}
            />

            <MemberList
                members={state.users}
                defaultValue={ProjectRole.USER}
                title="Users"
                update={() => undefined}
                remove={() => undefined}
            />

        </>);

        return (
            <MainContainer
                main={main}
                header={header}
            />
        )
    }
}

interface Admins {
    members: string[]
    defaultValue: ProjectRole
    title: string
    update: (role: ProjectRole, user: string) => void
    remove: (user: string) => void
}

const MemberList = (props: Admins) => (
    <Box mb="1.5em" >
        <Heading.h3>{props.title}</Heading.h3>
        {props.members.map(member => (
            <React.Fragment key={member}>
                <Box mb="5px" />
                <Spacer
                    left={<>
                        <UserAvatar avatar={defaultAvatar} />
                        <TextSpan>{member}</TextSpan>
                    </>}
                    right={<RoleSelect
                        member={member}
                        defaultValue={props.defaultValue}
                        remove={props.remove}
                        updateRole={role => props.update(role, member)}
                    />}
                />
            </React.Fragment>))}
    </Box>
);

interface RoleSelectProps {
    defaultValue: ProjectRole
    updateRole: (role: ProjectRole) => void
    remove: (username: string) => void
    member: string
}

type RoleSelectState = { role: ProjectRole }

class RoleSelect extends React.Component<RoleSelectProps, RoleSelectState> {
    constructor(props: RoleSelectProps) {
        super(props);
        this.state = {
            role: props.defaultValue
        }
    }

    private readonly setRole = (role: string) => this.setState(() => ({ role: role.toUpperCase() as ProjectRole }));

    render() {
        const { role } = this.state;
        const { ...props } = this.props;
        return (
            <Flex>
                {role === props.defaultValue ? null : <Button onClick={() => props.updateRole(role)} color="green" mr="10px">Update</Button>}
                <MemberSelect onChange={({ target: { value } }) => this.setRole(value)}>
                    {Object.keys(ProjectRole).filter(role => role !== "PI").map((r: ProjectRole) =>
                        <option selected={r === props.defaultValue} key={`${props.defaultValue}${r}`}>{prettierString(r)}</option>
                    )}
                </MemberSelect>
                <Button ml="30px" onClick={() => props.remove(props.member)} color="red">Remove</Button>
            </Flex>
        )
    }
}

const MemberSelect = styled(Select)`
    min-width: 200px;
`;


interface ManagementOperations {
    fetchProjectMembers: (id: string) => void
    clearError: () => void
}

const mapDispatchToProps = (dispatch: Dispatch): ManagementOperations => ({
    fetchProjectMembers: async id => dispatch(await fetchProjectMembers(id)),
    clearError: () => dispatch(setError())
});

export default connect<void, ManagementOperations>(null, mapDispatchToProps)(Management);