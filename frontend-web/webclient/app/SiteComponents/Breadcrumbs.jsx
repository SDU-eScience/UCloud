import React from "react";
import {Breadcrumb} from "react-bootstrap";

export const BreadCrumbs = ({currentPath, navigate}) => {
    if (!currentPath) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(currentPath);
    let breadcrumbs = pathsMapping.map((path, index) =>
        <Breadcrumb.Item key={index} active={pathsMapping.length === index + 1} onClick={() => navigate(`${path.actualPath}`)}>{path.local}</Breadcrumb.Item>
    );
    return (
        <Breadcrumb>
            {breadcrumbs}
        </Breadcrumb>
    );

}

export function buildBreadCrumbs(path) {
    let paths = path.split("/").filter(path => path);
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({actualPath: actualPath, local: paths[i]})
    }
    return pathsMapping
}