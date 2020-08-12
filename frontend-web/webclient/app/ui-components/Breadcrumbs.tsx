import * as React from "react";
import styled from "styled-components";
import {Box, Icon, Text, Flex} from "ui-components";
import {addTrailingSlash, removeTrailingSlash} from "UtilityFunctions";
import HttpClient from "Authentication/lib";
import {pathComponents} from "Utilities/FileUtilities";
import {ProjectStatus, useProjectStatus} from "Project/cache";
import {Center} from "UtilityComponents";

// https://www.w3schools.com/howto/howto_css_breadcrumbs.asp
export const BreadCrumbsBase = styled(Flex) <{embedded: boolean}>`
    width: calc(100% - ${(props): string => props.embedded ? "50px" : "180px"});
    & > span {
        width: 1;
        font-size: 25px;
        display: inline-block;
        text-overflow: ellipsis;
        white-space: nowrap;
        overflow: hidden;
    }

    & > span + span:before {
        padding: 0 8px;
        vertical-align: top;
        color: var(--text, #f00);
        content: "/";
    }

    & > span {
        color: var(--text, #f00);
        text-decoration: none;
    }

    & > span:hover {
        cursor: pointer;
        color: var(--blue, #f00);
        text-decoration: none;
    }

    & > span:last-child:hover {
        color: var(--text, #f00);
        cursor: default;
    }
`;

export interface BreadcrumbsList {
    currentPath: string;
    navigate: (path: string) => void;
    client: HttpClient;
    embedded: boolean;
}

export interface BreadCrumbMapping {
    actualPath: string;
    local: string;
}

export const BreadCrumbs = ({
    currentPath,
    navigate,
    client,
    embedded
}: BreadcrumbsList): JSX.Element | null => {
    if (!currentPath) return null;
    const projectStatus = useProjectStatus();

    const pathsMapping = buildBreadCrumbs(currentPath, client.homeFolder, client.projectId ?? "", projectStatus);
    const activePathsMapping = pathsMapping[pathsMapping.length - 1];
    pathsMapping.pop();
    const breadcrumbs = pathsMapping.map(p => (
        <span key={p.local} test-tag={p.local} title={p.local} onClick={() => navigate(p.actualPath)}>
            {p.local}
        </span>
    ));

    const addHomeFolderLink = !(
        currentPath.startsWith(removeTrailingSlash(client.homeFolder)) || currentPath.startsWith("/projects/")
    );

    return (
        <>
            {addHomeFolderLink ? (
                <>
                    <Box>
                        <Icon test-tag="to_home" size="30px" cursor="pointer" name="home" onClick={toHome} />
                        <Center>
                            <Text cursor="pointer" fontSize="11px" onClick={toHome}>Home</Text>
                        </Center>
                    </Box>
                    <Text ml="6px" mr="6px" fontSize="24px">|</Text>
                </>
            ) : null}
            <BreadCrumbsBase embedded={embedded}>
                {breadcrumbs}
                <span title={activePathsMapping.local}>
                    {activePathsMapping.local}
                </span>
            </BreadCrumbsBase>
        </>
    );

    function toHome(): void {
        navigate(client.homeFolder);
    }
};

function buildBreadCrumbs(
    path: string,
    homeFolder: string,
    activeProject: string,
    projectStatus: ProjectStatus
): BreadCrumbMapping[] {
    const paths = pathComponents(path);
    if (paths.length === 0) return [{actualPath: "/", local: "/"}];

    const pathsMapping: BreadCrumbMapping[] = [];
    for (let i = 0; i < paths.length; i++) {
        let actualPath = "/";
        for (let j = 0; j <= i; j++) actualPath += paths[j] + "/";
        pathsMapping.push({actualPath, local: paths[i]});
    }

    // Handle starts with home
    if (addTrailingSlash(path).startsWith(homeFolder)) { // remove first two indices
        return [{actualPath: homeFolder, local: "Home"}].concat(pathsMapping.slice(2));
    } else if (path.startsWith("/home") && pathsMapping.length >= 2) {
        return [{
            actualPath: pathsMapping[1].actualPath,
            local: `Home of ${pathsMapping[1].local}`
        }].concat(pathsMapping.slice(2));
    }

    // Handle starts with project
    if (addTrailingSlash(path).startsWith("/projects/")) {
        const [, projectInPath] = pathComponents(path);
        const project = activeProject !== "" && path.includes(projectInPath) ? activeProject : projectInPath;

        let localName = project;
        const membership = projectStatus.fetch().membership.find(it => it.projectId === project);
        if (membership) {
            localName = membership.title;
        }

        return [
            {actualPath: `/projects/${project}/`, local: localName}
        ].concat(pathsMapping.slice(2));
    }
    return pathsMapping;
}
