import * as React from "react";
import HttpClient from "Authentication/lib";
import {addStandardInputDialog, addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import {errorMessageOrDefault} from "UtilityFunctions";
import {AccessRight} from "Types";
import {dialogStore} from "Dialog/DialogStore";
import {Box, Button, List} from "ui-components";
import {useCloudAPI} from "Authentication/DataHook";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import {File, Acl, ProjectEntity} from "Files";
import {ListRow} from "ui-components/List";
import ClickableDropdown from "ui-components/ClickableDropdown";
import {Spacer} from "ui-components/Spacer";

export function repositoryName(path: string): string {
    if (!path.startsWith("/projects/")) return "";
    return path.split("/").filter(it => it)[2];
}

export function repositoryTrashFolder(path: string, client: HttpClient): string {
    const repo = repositoryName(path);
    if (!repo) return "";
    return `${client.currentProjectFolder}${repo}/Trash`;
}

export function repositoryJobsFolder(path: string, client: HttpClient): string {
    const repo = repositoryName(path);
    if (!repo) return "";
    return `${client.currentProjectFolder}${repo}/Jobs`;
}

export async function promptCreateRepository(client: HttpClient, reload: () => void): Promise<void> {
    const result = await addStandardInputDialog({
        title: "Create repository",
        addToFront: false,
        confirmText: "Create",
        defaultValue: "",
        validationFailureMessage: "Name can't be empty.",
        validator: val => !!val,
        placeholder: "Repository name...",
        cancelText: "Cancel"
    });

    if ("cancelled" in result) return;

    try {
        await client.post("/projects/repositories", {name: result.result});
        snackbarStore.addSnack({
            type: SnackType.Success,
            message: "Repository created"
        });
        reload();
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error ocurred creating."));
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
                reload();
            } catch (err) {
                snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to delete."));
            }
        }
    });
}

export async function promptRenameRepository(oldName: string, client: HttpClient, reload: () => void): Promise<void> {
    const res = await addStandardInputDialog({
        title: `Rename ${oldName}`,
        addToFront: false,
        cancelText: "Cancel",
        confirmText: "Rename",
        defaultValue: "",
        placeholder: "Enter new name...",
        validationFailureMessage: "Name can't be empty or the same as the old one.",
        validator: val => (oldName !== val && !!val)
    });

    if ("cancelled" in res) return;

    try {
        await client.post("/projects/repositories/update", {oldName, newName: res.result});
        snackbarStore.addSnack({
            type: SnackType.Success,
            message: "Repository renamed"
        });
        reload();
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "An error ocurred renaming repository."));
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

export function updatePermissionsPrompt(client: HttpClient, file: File): void {
    const reponame = repositoryName(file.path);
    const rights = file.acl ?? [];
    dialogStore.addDialog(<UpdatePermissionsDialog client={client} repository={reponame} rights={rights} />, () => undefined);
}

export function UpdatePermissionsDialog(props: {client: HttpClient; repository: string; rights: Acl[]}): JSX.Element {
    const [groups, params, setParams] = useCloudAPI<string[]>({path: "/projects/groups", method: "GET"}, []);
    const [newRights, setNewRights] = React.useState<Map<string, AccessRight[]>>(new Map());
    // HACK -- The setNewRights doesn't trigger at new update.
    const [, forceUpdate] = React.useState("");
    // HACK END

    return (
        <Box width="auto" minWidth="300px">
            {groups.loading ? <LoadingSpinner size={24} /> : null}
            <List>
                {groups.data.map(g => {
                    const acl = newRights.get(g) ?? props.rights.find(a => (a.entity as ProjectEntity).group === g)?.rights ?? [];
                    let rights = "None";
                    if (acl.includes("READ")) rights = "Read";
                    if (acl.includes("WRITE")) rights = "Edit";
                    return (
                        <ListRow
                            key={g}
                            left={g}
                            select={() => undefined}
                            isSelected={false}
                            right={
                                <ClickableDropdown
                                    chevron
                                    onChange={value => {
                                        if (value === "") newRights.set(g, []);
                                        else if (value === "READ") newRights.set(g, [AccessRight.READ]);
                                        else if (value === "WRITE") newRights.set(g, [AccessRight.READ, AccessRight.WRITE]);
                                        // HACK
                                        forceUpdate(g + value);
                                        // HACK end
                                        setNewRights(newRights);
                                    }}
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
                <Spacer
                    mt="50px"
                    left={<Button color="red" onClick={() => dialogStore.failure()}>Cancel</Button>}
                    right={<Button onClick={update}>Update</Button>}
                />
            </List>
        </Box>
    );

    function update(): void {
        updatePermissions(props.client, props.repository, [...newRights.entries()].map(([group, rights]) => ({
            group, rights
        })));
    }
}

export async function updatePermissions(
    client: HttpClient,
    repository: string,
    newAcl: ProjectAclEntry[]
): Promise<void> {
    try {
        client.post<UpdatePermissionsResponse>("/projects/repositories/update-permissions", {
            repository,
            newAcl
        } as UpdatePermissionsRequest);
    } catch (err) {
        snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to update permissions"));
    }
}
