import * as React from "react";
import { Breadcrumb } from "semantic-ui-react";
import { BreadCrumbMapping, Breadcrumbs as BreadCrumbsList } from ".";

export const BreadCrumbs = ({ currentPath, navigate, homeFolder }: BreadCrumbsList) => {
    if (!currentPath) return null;
    const pathsMapping = buildBreadCrumbs(currentPath, homeFolder);
    const activePathsMapping = pathsMapping[pathsMapping.length - 1];
    pathsMapping.pop();
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
                {activePathsMapping.local}
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
        </Breadcrumb>
    );
}

function buildBreadCrumbs(path: string, homeFolder: string) {
    const paths = path.split("/").filter((path: string) => path);
    let pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({ actualPath: actualPath, local: paths[i] });
    }
    if (path.startsWith(homeFolder)) { // remove first two indices 
        pathsMapping =
            [{ actualPath: homeFolder, local: "Home" }].concat(pathsMapping.slice(2, pathsMapping.length));
    }
    return pathsMapping;
}