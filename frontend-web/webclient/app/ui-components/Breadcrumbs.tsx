import * as React from "react";
import styled from "styled-components";
import {Box, Icon, Text} from "ui-components";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";

// https://www.w3schools.com/howto/howto_css_breadcrumbs.asp
const BreadCrumbsBase = styled.ul`
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
        color: var(--text, #f00);
        content: "/";
    }

    & li span {
        color: var(--text, #f00);
        text-decoration: none;
    }

    & li span:hover {
        cursor: pointer;
        color: var(--blue, #f00);
        text-decoration: none;
    }
`;

export interface BreadcrumbsList {
    currentPath: string;
    navigate: (path: string) => void;
    homeFolder: string;
    projectFolder: string;
}

export interface BreadCrumbMapping {
    actualPath: string;
    local: string;
}

export const BreadCrumbs = ({
    currentPath,
    navigate,
    homeFolder,
    projectFolder
}: BreadcrumbsList): JSX.Element | null => {
    if (!currentPath) return null;
    const pathsMapping = buildBreadCrumbs(currentPath, homeFolder, projectFolder);
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
            {addHomeFolderLink ? (
                <>
                    <Box ml="15px">
                        <Icon size="30px" cursor="pointer" name="home" onClick={toHome} />
                        <Text cursor="pointer" ml="-15px" fontSize="11px" onClick={toHome}>
                            Go to home
                        </Text>
                    </Box>
                    <Text ml="6px" mr="6px" fontSize="24px">|</Text>
                </>
            ) : null}
            <BreadCrumbsBase>
                {breadcrumbs}
                <li title={activePathsMapping.local}>
                    {activePathsMapping.local.slice(0, 20).trim()}{activePathsMapping.local.length > 20 ? "..." : ""}
                </li>
            </BreadCrumbsBase>
        </>
    );

    function toHome(): void {
        navigate(homeFolder);
    }
};

function buildBreadCrumbs(path: string, homeFolder: string, projectFolder: string): BreadCrumbMapping[] {
    const paths = path.split("/").filter(p => p !== "");
    if (paths.length === 0) return [{actualPath: "/", local: "/"}];

    const pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) actualPath += paths[j] + "/";
        pathsMapping.push({actualPath, local: paths[i]});
    }

    // Handle starts with home
    if (addTrailingSlash(path).startsWith(homeFolder)) // remove first two indices
        return [{actualPath: homeFolder, local: "Home"}].concat(pathsMapping.slice(2));
    else if (path.startsWith("/home") && pathsMapping.length >= 2)
        return [{
            actualPath: pathsMapping[1].actualPath,
            local: `Home of ${pathsMapping[1].local}`
        }].concat(pathsMapping.slice(2));

    // Handle starts with project
    if (addTrailingSlash(path).startsWith("/projects/")) {
        return [{actualPath: projectFolder, local: "Projects"}].concat(pathsMapping.slice(2));
    }

    return pathsMapping;
}
