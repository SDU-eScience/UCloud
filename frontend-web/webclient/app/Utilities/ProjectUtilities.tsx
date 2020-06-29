import * as React from "react";
import HttpClient from "Authentication/lib";
import {addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {errorMessageOrDefault} from "UtilityFunctions";
import {AccessRight, Page} from "Types";
import {dialogStore} from "Dialog/DialogStore";
import {Box, Button, Flex, List, Truncate} from "ui-components";
import {useCloudAPI} from "Authentication/DataHook";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {Acl, File, ProjectEntity} from "Files";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Spacer} from "ui-components/Spacer";
import {groupSummaryRequest, ProjectRole} from "Project";
import {pathComponents} from "Utilities/FileUtilities";
import styled from "styled-components";
import {useHistory} from "react-router";
import {useCallback} from "react";
import {GroupWithSummary} from "Project/GroupList";
import {emptyPage} from "DefaultObjects";
import * as Pagination from "Pagination";

export function repositoryName(path: string): string {
    const components = pathComponents(path);
    if (components.length < 3) return "";
    if (components[0] !== "projects") return "";
    return components[2];
}

export function isRepository(path: string): boolean {
    const components = pathComponents(path);
    return (components.length === 3 && components[0] === "projects");
}

export async function createRepository(client: HttpClient, name: string, reload: () => void): Promise<void> {
    try {
        await client.post("/projects/repositories", {name});
        snackbarStore.addSuccess(`Folder '${name}' created`, true);
        reload();
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred creating."), false);
    }
}

export function promptDeleteRepository(name: string, client: HttpClient, reload: () => void): void {
    addStandardDialog({
        title: "Delete?",
        message: `Delete ${name} and all associated files? This cannot be undone.`,
        confirmText: "Delete",
        onConfirm: async () => {
            try {
                await client.delete("/projects/repositories", {name});
                snackbarStore.addSuccess(`Folder '${name}' deleted`, true);
                reload();
            } catch (err) {
                snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to delete repository."), false);
            }
        }
    });
}

export async function renameRepository(
    oldName: string,
    newName: string,
    client: HttpClient,
    reload: () => void
): Promise<void> {
    try {
        await client.post("/projects/repositories/update", {oldName, newName});
        snackbarStore.addSuccess("Repository renamed", false);
        reload();
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error occurred renaming repository."), false);
    }
}

interface UpdatePermissionsRequest {
    repository: string;
    newAcl: ProjectAclEntry[];
}

interface ProjectAclEntry {
    group: string;
    rights: AccessRight[];
}

type UpdatePermissionsResponse = void;

export function updatePermissionsPrompt(client: HttpClient, file: File, reload: () => void): void {
    const reponame = repositoryName(file.path);
    const rights = file.acl ?? [];
    dialogStore.addDialog(
        <UpdatePermissionsDialog
            reload={reload}
            client={client}
            repository={reponame}
            rights={rights}
        />, () => undefined
    );
}

const InnerProjectPermissionBox = styled.div`
    height: 300px;
    overflow-y: auto;
`;

export function UpdatePermissionsDialog(props: { client: HttpClient; repository: string; rights: Acl[]; reload: () => void }): JSX.Element {
    const [groups, fetchGroups, groupParams] = useCloudAPI<Page<GroupWithSummary>>(
        groupSummaryRequest({itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [newRights, setNewRights] = React.useState<Map<string, AccessRight[]>>(new Map());
    const history = useHistory();

    const onCreateGroup = useCallback(() => {
        history.push("/projects/view/");
        dialogStore.failure();
    }, [history]);
    return (
        <Box width="auto" minWidth="300px">
            {groups.loading ? <LoadingSpinner size={24}/> : null}
            <InnerProjectPermissionBox>
                <List height={"100%"}>
                    <Pagination.List
                        loading={groups.loading}
                        page={groups.data}
                        onPageChanged={(page) => fetchGroups(groupSummaryRequest({...groupParams.parameters, page}))}
                        customEmptyPage={(
                            <Flex width={"100%"} height={"100%"} alignItems={"center"} justifyContent={"center"}
                                  flexDirection={"column"}>
                                <Box>
                                    No groups exist for this project.
                                </Box>

                                <Button onClick={onCreateGroup}>Create group</Button>
                            </Flex>
                        )}
                        pageRenderer={() => (
                            <>
                                {groups.data.items.map(summary => {
                                    const g = summary.group;
                                    const acl = newRights.get(g) ?? props.rights.find(a => (a.entity as ProjectEntity).group === g)?.rights ?? [];
                                    let rights = "None";
                                    if (acl.includes(AccessRight.READ)) rights = "Read";
                                    if (acl.includes(AccessRight.WRITE)) rights = "Edit";
                                    return (
                                        <ListRow
                                            key={g}
                                            left={<Truncate width={"300px"} mr={16} title={g}>{g}</Truncate>}
                                            select={() => undefined}
                                            isSelected={false}
                                            right={
                                                <ClickableDropdown
                                                    chevron
                                                    onChange={value => {
                                                        if (value === "") newRights.set(g, []);
                                                        else if (value === "READ") newRights.set(g, [AccessRight.READ]);
                                                        else if (value === "WRITE") newRights.set(g, [AccessRight.READ, AccessRight.WRITE]);
                                                        setNewRights(new Map(newRights));
                                                    }}
                                                    minWidth="75px"
                                                    width="75px"
                                                    options={[
                                                        {text: "Read", value: "READ"},
                                                        {text: "Edit", value: "WRITE"},
                                                        {text: "None", value: ""}
                                                    ]} trigger={rights}
                                                />
                                            }
                                            navigate={() => undefined}
                                        />
                                    );
                                })}
                            </>
                        )}
                    />
                </List>
            </InnerProjectPermissionBox>

            <Spacer
                mt="25px"
                left={<Button color="red" onClick={() => dialogStore.failure()}>Cancel</Button>}
                right={<Button disabled={newRights.size === 0} onClick={update}>Update</Button>}
            />
        </Box>
    );

    async function update(): Promise<void> {
        await updatePermissions(props.client, props.repository, [...newRights.entries()].map(([group, rights]) => ({
            group, rights
        })));
        props.reload();
    }
}

export async function updatePermissions(
    client: HttpClient,
    repository: string,
    newAcl: ProjectAclEntry[]
): Promise<void> {
    try {
        await client.post<UpdatePermissionsResponse>("/projects/repositories/update-permissions", {
            repository,
            newAcl
        } as UpdatePermissionsRequest);
        snackbarStore.addSuccess("Updated permissions.", false);
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to update permissions"), false);
    }
}

export function isAdminOrPI(role: ProjectRole): boolean {
    return [ProjectRole.ADMIN, ProjectRole.PI].includes(role);
}


