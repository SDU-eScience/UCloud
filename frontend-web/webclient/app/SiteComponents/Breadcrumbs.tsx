import * as React from "react";
import "react-bootstrap";
import { BreadcrumbItem, Breadcrumb } from "react-bootstrap/lib";


interface Breadcrumbs { currentPath: string, navigate: (path: string) => void }
export const BreadCrumbs = ({ currentPath, navigate }: Breadcrumbs) => {
    if (!currentPath) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(currentPath);
    let breadcrumbs = pathsMapping.map((path, index) => (
        <BreadcrumbItem key={index} active={pathsMapping.length === index + 1}>
            <span onClick={() => navigate(`${path.actualPath}`)}>{path.local}</span>
        </BreadcrumbItem>
    ));
    return (
        <Breadcrumb>
            {breadcrumbs}
        </Breadcrumb>
    );

}

export function buildBreadCrumbs(path: string) {
    const paths: string[] = path.split("/").filter((path: string) => path);
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({ actualPath: actualPath, local: paths[i] })
    }
    return pathsMapping
}