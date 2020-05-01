import {callAPIWithErrorHandler, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {
    addMemberInProject,
    deleteMemberInProject,
    emptyProject,
    Project,
    ProjectRole,
    roleInProject,
    viewProject
} from "Project/index";
import * as Heading from "ui-components/Heading";
import * as React from "react";
import {useEffect, useRef, useState} from "react";
import {useParams} from "react-router";
import {Box, Button, Flex, Input, Label} from "ui-components";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {loadingAction} from "Loading";
import {
    shouldVerifyMembership,
    ShouldVerifyMembershipResponse,
    verifyMembership
} from "Project/api";
import {searchPreviousSharedUsers, ServiceOrigin} from "Shares";
import {Dropdown, DropdownContent} from "ui-components/Dropdown";
import {GroupMembers} from "./DetailedGroupView";
import {addStandardDialog} from "UtilityComponents";
import styled from "styled-components";
import GroupView from "Project/GroupView";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";

const View: React.FunctionComponent<ViewOperations> = props => {
    const id = decodeURIComponent(useParams<{ id: string }>().id);
    const [project, setProjectParams] = useCloudAPI<Project>(viewProject({id}), emptyProject(id));
    const [shouldVerify, setShouldVerifyParams] = useCloudAPI<ShouldVerifyMembershipResponse>(
        shouldVerifyMembership(id),
        {shouldVerify: false}
    );

    const role = roleInProject(project.data);
    const allowManagement = role === ProjectRole.PI || role === ProjectRole.ADMIN;
    const newMemberRef = useRef<HTMLInputElement>(null);
    const [isCreatingNewMember, createNewMember] = useAsyncCommand();
    const [isLoading, runCommand] = useAsyncCommand();

    /* Contact book */
    const SERVICE = ServiceOrigin.PROJECT_SERVICE;
    const ref = React.useRef<number>(-1);
    const [contacts, setFetchArgs,] = useCloudAPI<{ contacts: string[] }>(
        searchPreviousSharedUsers("", SERVICE),
        {contacts: []}
    );

    const onKeyUp = React.useCallback(() => {
        if (ref.current !== -1) {
            window.clearTimeout(ref.current);
        }
        ref.current = (window.setTimeout(() => {
            setFetchArgs(searchPreviousSharedUsers(newMemberRef.current!.value, SERVICE));
        }, 500));

    }, [newMemberRef.current, setFetchArgs]);

    const reload = (): void => setProjectParams(viewProject({id}));

    useEffect(() => {
        props.setRefresh(reload);
        return () => props.setRefresh();
    }, []);

    useEffect(() => {
        props.setLoading(project.loading);
    }, [project.loading]);

    useEffect(() => reload(), [id]);

    const onSubmit = async (e: React.FormEvent): Promise<void> => {
        e.preventDefault();
        const inputField = newMemberRef.current!;
        const username = inputField.value;
        try {
            await createNewMember(addMemberInProject({
                projectId: id,
                member: {
                    username,
                    role: ProjectRole.USER
                }
            }));
            inputField.value = "";
            reload();
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed adding new member"), false);
        }
    };

    const onApprove = async (): Promise<void> => {
        await callAPIWithErrorHandler(verifyMembership(id));
        setShouldVerifyParams(shouldVerifyMembership(id));
    };

    return (
        <LoadingMainContainer
            headerSize={0}
            header={null}
            sidebar={null}
            loading={project.loading && project.data.members.length === 0}
            error={project.error ? project.error.why : undefined}
            main={(
                <>
                    {!shouldVerify.data.shouldVerify ? null : (
                        <Box backgroundColor={"orange"} color={"white"} p={32} m={16}>
                            <Heading.h4>Time for a review!</Heading.h4>

                            <ul>
                                <li>PIs and admins are asked to occasionally review members of their project</li>
                                <li>We ask you to ensure that only the people who need access have access</li>
                                <li>If you find someone who should not have access then remove them by clicking
                                    'Remove'
                                    next to their name
                                </li>
                                <li>
                                    When you are done, click below:

                                    <Box mt={8}>
                                        <Button color={"green"} textColor={"white"} onClick={onApprove}>
                                            Everything looks good now
                                        </Button>
                                    </Box>
                                </li>
                            </ul>

                        </Box>
                    )}
                    <TwoColumnLayout>
                        <Box className={"members"}>
                            <BreadCrumbsBase>
                                <li><span>Members of {project.data.title}</span></li>
                            </BreadCrumbsBase>
                            {!allowManagement ? null : (
                                <form onSubmit={onSubmit}>
                                    <Dropdown fullWidth hover={false}>
                                        <Label htmlFor={"new-project-member"}>Add new member</Label>
                                        <Flex mb="6px">
                                            <Input
                                                onKeyUp={onKeyUp}
                                                id="new-project-member"
                                                placeholder="Username"
                                                ref={newMemberRef}
                                                width="350px"
                                                disabled={isCreatingNewMember}
                                                rightLabel
                                            />
                                            <Button attached>Add</Button>
                                        </Flex>
                                    </Dropdown>
                                    <DropdownContent
                                        hover={false}
                                        colorOnHover={false}
                                        width="350px"
                                        visible={contacts.data.contacts.length > 0}
                                    >
                                        {contacts.data.contacts.map(it => (
                                            <div
                                                key={it}
                                                onClick={() => {
                                                    newMemberRef.current!.value = it;
                                                    setFetchArgs(searchPreviousSharedUsers("", SERVICE));
                                                }}
                                            >
                                                {it}
                                            </div>
                                        ))}
                                    </DropdownContent>
                                </form>
                            )}

                            <GroupMembers
                                members={project.data.members}
                                promptRemoveMember={async member => addStandardDialog({
                                    title: "Remove member",
                                    message: `Remove ${member}?`,
                                    onConfirm: async () => {
                                        await runCommand(deleteMemberInProject({
                                            projectId: id,
                                            member
                                        }));

                                        reload();
                                    }
                                })}
                                reload={reload}
                                project={project.data.id}
                                allowManagement={allowManagement}
                                allowRoleManagement={allowManagement}
                            />
                        </Box>
                        <Box className={"groups"}>
                            <GroupView />
                        </Box>
                    </TwoColumnLayout>
                </>
            )}
        />
    );
};

const TwoColumnLayout = styled.div`
    display: flex;
    flex-direction: row;
    flex-wrap: wrap;
    width: 100%;
    
    & > * {
        flex-basis: 100%;
    }
    
    @media screen and (min-width: 1200px) {
        & {
            height: calc(100vh - 140px);
            overflow: hidden;
        }
        
        & > .members {
            height: 100%;
            flex: 1;
            overflow-y: scroll;
            margin-right: 32px;
        }
        
        & > .groups {
            flex: 2;
            height: 100%;
            overflow: auto;
        }
    }
`;

interface ViewOperations {
    setRefresh: (refresh?: () => void) => void;
    setLoading: (loading: boolean) => void;
}

const mapDispatchToProps = (dispatch: Dispatch): ViewOperations => ({
    setRefresh: refresh => dispatch(setRefreshFunction(refresh)),
    setLoading: loading => dispatch(loadingAction(loading))
});

export default connect(null, mapDispatchToProps)(View);
