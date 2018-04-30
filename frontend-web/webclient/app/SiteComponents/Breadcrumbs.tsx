import * as React from "react";
import "react-bootstrap";
import { Breadcrumb } from "semantic-ui-react"

const BreadcrumbExampleStandard = () => (
    <Breadcrumb>
        <Breadcrumb.Section link>Home</Breadcrumb.Section>
        <Breadcrumb.Divider />
        <Breadcrumb.Section link>Store</Breadcrumb.Section>
        <Breadcrumb.Divider />
        <Breadcrumb.Section active>T-Shirt</Breadcrumb.Section>
    </Breadcrumb>
)



interface Breadcrumbs { currentPath: string, navigate: (path: string) => void }
export const BreadCrumbs = ({ currentPath, navigate }: Breadcrumbs) => {
    if (!currentPath) {
        return null;
    }
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
        <Breadcrumb>
            {breadcrumbs}
            <Breadcrumb.Section active>
                {activePathsMapping.local}
            </Breadcrumb.Section>
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