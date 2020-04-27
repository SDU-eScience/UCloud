import * as React from "react";
import styled from "styled-components";
import {Box, Icon, Text} from "ui-components";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";
import HttpClient from "Authentication/lib";

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
    client: HttpClient;
}

export interface BreadCrumbMapping {
    actualPath: string;
    local: string;
}

export const BreadCrumbs = ({
    currentPath,
    navigate,
    client
}: BreadcrumbsList): JSX.Element | null => {
    if (!currentPath) return null;
    const isRepoFolder = client.projectFolder === currentPath;

    const path = isRepoFolder ? `${client.homeFolder}${client.projectId}` : currentPath;
    const pathsMapping = buildBreadCrumbs(path, client.homeFolder, client.projectId ?? "");
    const activePathsMapping = pathsMapping[pathsMapping.length - 1];
    pathsMapping.pop();
    const breadcrumbs = pathsMapping.map((p, index) => (
        <li key={index}>
            <span title={p.local} onClick={() => navigate(p.actualPath)}>
                {`${p.local.slice(0, 20).trim()}${p.local.length > 20 ? "..." : ""}`}
            </span>
        </li>
    ));

    const addHomeFolderLink = !(path.startsWith(removeTrailingSlash(client.homeFolder)) || path.startsWith(client.currentProjectFolder));

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
        navigate(client.homeFolder);
    }
};

function buildBreadCrumbs(path: string, homeFolder: string, activeProject: string): BreadCrumbMapping[] {
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
        return [
            {actualPath: homeFolder, local: "Home"},
            {actualPath: `/projects/${activeProject}/`, local: activeProject}
        ].concat(pathsMapping.slice(2));
    }

    return pathsMapping;
}
