import React from "react";
import {Breadcrumb} from "react-bootstrap";
export { buildBreadCrumbs }

export default function BreadCrumbs(props) {
    if (!props.path) {
        return null;
    }
    let pathsMapping = buildBreadCrumbs(props.path);
    let i = 0;
    let breadcrumbs = pathsMapping.map(path =>
        <Breadcrumb.Item key={i++} active={pathsMapping.length === i} onClick={() => props.getFiles(`${path.actualPath}`)}>{path.local}</Breadcrumb.Item>
    );
    return (
        <Breadcrumb>
            {breadcrumbs}
        </Breadcrumb>)
}

function buildBreadCrumbs(path) {
    let paths = path.split("/");
    let pathsMapping = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({actualPath: actualPath, local: paths[i],})
    }
    return pathsMapping;
}