let lastActivePath = "";

export function setLastActivePath(path: string): void {
    lastActivePath = path;
    const pathNode = document.querySelector<HTMLDivElement>("[data-last-used-file-path]");
    if (pathNode) pathNode.innerText = path;
}

export function getLastActivePath(): string {
    return document.querySelector<HTMLDivElement>("[data-last-used-file-path]")?.innerText || lastActivePath;
}
