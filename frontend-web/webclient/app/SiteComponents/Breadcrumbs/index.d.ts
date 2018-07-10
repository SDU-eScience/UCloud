export interface Breadcrumbs { currentPath: string, navigate: (path: string) => void }

export interface BreadCrumbMapping {
    actualPath: string
    local: string
}