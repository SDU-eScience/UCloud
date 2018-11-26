export const projectViewPage = (filePath: string): string => {
    return `/projects/view?filePath=${encodeURIComponent(filePath)}`;
}

export const projectEditPage = (filePath: string): string => {
    return `/projects/edit?filePath=${encodeURIComponent(filePath)}`;
}