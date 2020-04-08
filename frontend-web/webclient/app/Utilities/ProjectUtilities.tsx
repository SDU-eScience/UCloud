import * as React from "react";
import HttpClient from "Authentication/lib";
import {addStandardInputDialog, addStandardDialog} from "UtilityComponents";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {SnackType} from "Snackbar/Snackbars";
import {errorMessageOrDefault} from "UtilityFunctions";
import {AccessRight} from "Types";
import {Client} from "Authentication/HttpClientInstance";

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

export function updatePermissions(props: {client: HttpClient; repository: string}): void {
    // const [data, setFetchParams, params] = useCloudAPI();
    // const result = dialogStore.addDialog(updatePermissionsDialog(), () => cancelled = true);
    // if (cancelled) return;
    Client.post<UpdatePermissionsResponse>("/projects/repositories/update-permissions", {
        repository: "Single",
        newAcl: [{group: "g", rights: ["READ"]}]
    } as UpdatePermissionsRequest);
}
