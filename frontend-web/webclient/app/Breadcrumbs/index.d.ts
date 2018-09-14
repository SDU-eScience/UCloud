export interface Breadcrumbs { currentPath: string, navigate: (path: string) => void, homeFolder: string }

export interface BreadCrumbMapping {
    actualPath: string
    local: string
}