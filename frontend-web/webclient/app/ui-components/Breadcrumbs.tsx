import * as React from "react";
import styled from "styled-components";
import {Box, Icon, Text} from "ui-components";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";

// https://www.w3schools.com/howto/howto_css_breadcrumbs.asp
const BreadCrumbsBase = styled.ul<{divider?: string}>`
    padding: 0;
    padding-right: 10px;
    margin: 0;
    list-style: none;
    max-width: 85%;
    height: 85px;
    overflow-y: auto;

    & li {
        display: inline;
        font-size: 25px;
    }

    & li + li:before {
        padding: 8px;
        color: ${p => p.theme.colors.black};
        content: "${p => p.divider}";
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
};

export interface BreadcrumbsList {
    currentPath: string;
    navigate: (path: string) => void;
    homeFolder: string;
}

export interface BreadCrumbMapping {
    actualPath: string;
    local: string;
}

export const BreadCrumbs = ({currentPath, navigate, homeFolder}: BreadcrumbsList) => {
    if (!currentPath) return null;
    const pathsMapping = buildBreadCrumbs(currentPath, homeFolder);
    const activePathsMapping = pathsMapping[pathsMapping.length - 1];
    pathsMapping.pop();
    const breadcrumbs = pathsMapping.map((path, index) => (
        <li key={index}>
            <span title={path.local} onClick={() => navigate(path.actualPath)}>
                {`${path.local.slice(0, 20).trim()}${path.local.length > 20 ? "..." : ""}`}
            </span>
        </li>
    ));

    const addHomeFolderLink = !currentPath.startsWith(removeTrailingSlash(homeFolder));

    return (
        <>
            {addHomeFolderLink ?
                <>
                    <Box ml="15px">
                        <Icon size="30px" cursor="pointer" name="home" onClick={() => navigate(homeFolder)} />
                        <Text
                            cursor="pointer"
                            ml="-15px"
                            fontSize="11px"
                            onClick={() => navigate(homeFolder)}
                        >Go to home</Text>
                    </Box>
                    <Text ml="8px" mr="8px" fontSize="25px">|</Text>
                </> : null}
            <BreadCrumbsBase divider="/">
                {breadcrumbs}
                <li title={activePathsMapping.local}>
                    {activePathsMapping.local.slice(0, 20).trim()}{activePathsMapping.local.length > 20 ? "..." : ""}
                </li>
            </BreadCrumbsBase>
        </>
    );
};

function buildBreadCrumbs(path: string, homeFolder: string): BreadCrumbMapping[] {
    const paths = path.split("/").filter(p => p !== "");
    if (paths.length === 0) return [{actualPath: "/", local: "/"}];

    let pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) actualPath += paths[j] + "/";
        pathsMapping.push({actualPath, local: paths[i]});
    }
    if (addTrailingSlash(path).startsWith(homeFolder)) // remove first two indices
        pathsMapping = [{actualPath: homeFolder, local: "Home", }].concat(pathsMapping.slice(2));
    else if (path.startsWith("/home") && pathsMapping.length >= 2)
        pathsMapping = [{
            actualPath: pathsMapping[1].actualPath,
            local: `Home of ${pathsMapping[1].local}`
        }].concat(pathsMapping.slice(2));
    return pathsMapping;
}
