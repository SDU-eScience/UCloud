import * as React from "react";
import styled from "styled-components";
import { addTrailingSlash } from "UtilityFunctions";

// https://www.w3schools.com/howto/howto_css_breadcrumbs.asp
const BreadCrumbsBase = styled.ul<{ divider?: string }>`
    padding: 0;
    padding-right: 10px;
    margin: 0;
    list-style: none;

    & li {
        display: inline;
        font-size: 25px;
    }

    & li + li:before {
        padding: 8px;
        color: black;
        content: "${props => props.divider}";
    }

    & li span {
        color: #0275d8;
        text-decoration: none;
    }

    & li span:hover {
        cursor: pointer;
        color: #01447e;
        text-decoration: none;
    }
`;

BreadCrumbsBase.defaultProps = {
    divider: "/"
}

export interface BreadcrumbsList { currentPath: string, navigate: (path: string) => void, homeFolder: string }

export interface BreadCrumbMapping {
    actualPath: string
    local: string
}

export const BreadCrumbs = ({ currentPath, navigate, homeFolder }: BreadcrumbsList) => {
    if (!currentPath) return null;
    const pathsMapping = buildBreadCrumbs(currentPath, homeFolder);
    const activePathsMapping = pathsMapping[pathsMapping.length - 1];
    pathsMapping.pop();
    const breadcrumbs = pathsMapping.map((path, index) => (
        <li key={index}>
            <span onClick={() => navigate(`${path.actualPath}`)}>
                {path.local}
            </span>
        </li>
    ));

    return (
        <BreadCrumbsBase divider="/">
            {breadcrumbs}
            <li>
                {activePathsMapping.local}
            </li>
        </BreadCrumbsBase>
    );
}

function buildBreadCrumbs(path: string, homeFolder: string) {
    const paths = path.split("/").filter(path => path);
    let pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) {
            actualPath += paths[j] + "/";
        }
        pathsMapping.push({ actualPath: actualPath, local: paths[i] });
    }
    if (addTrailingSlash(path).startsWith(homeFolder)) { // remove first two indices 
        pathsMapping =
            [{ actualPath: homeFolder, local: "Home" }].concat(pathsMapping.slice(2, pathsMapping.length));
    }
    return pathsMapping;
}