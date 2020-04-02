import HttpClient from "Authentication/lib";

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
