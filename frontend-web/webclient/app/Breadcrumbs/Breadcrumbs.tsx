import * as React from "react";
import { Breadcrumb } from "semantic-ui-react";
import { BreadCrumbMapping, Breadcrumbs as BreadCrumbsList } from ".";
import { Cloud } from "Authentication/SDUCloudObject";

export const BreadCrumbs = ({ currentPath, navigate }: BreadCrumbsList) => {
    if (!currentPath) return null;
    const pathsMapping = buildBreadCrumbs(currentPath);
    const activePathsMapping = pathsMapping.pop()
    const breadcrumbs = pathsMapping.map((path, index) => (
        <React.Fragment key={index}>
            <Breadcrumb.Section onClick={() => navigate(`${path.actualPath}`)} link>
                {path.local}
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
        </React.Fragment>
    ));

    return (
        <Breadcrumb size="large" className="breadcrumb-margin">
            {breadcrumbs}
            <Breadcrumb.Section active>
                {activePathsMapping ? activePathsMapping.local : null}
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
        </Breadcrumb>
    );

}

export function buildBreadCrumbs(path: string) {
    const paths = path.split("/").filter((path: string) => path);
    let pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({ actualPath: actualPath, local: paths[i] });
    }
    if (path.includes(Cloud.homeFolder)) { // remove first two indices 
        pathsMapping =
            [{ actualPath: Cloud.homeFolder, local: "Home" }].concat(pathsMapping.slice(2, pathsMapping.length));
    }
    return pathsMapping;
}