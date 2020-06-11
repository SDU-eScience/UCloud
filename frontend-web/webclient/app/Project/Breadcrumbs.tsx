import * as React from "react";
import styled from "styled-components";
import Spinner, {HexSpinWrapper} from "LoadingIcon/LoadingIcon";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import {Link} from "ui-components";
import {useProjectManagementStatus} from "Project/index";

export interface ProjectBreadcrumbsProps {
    crumbs: { title: string, link?: string }[];
}

const ProjectBreadcrumbsWrapper = styled(BreadCrumbsBase)`
    max-width: unset;
    flex-grow: 1;
    
    ${HexSpinWrapper} {
        margin: 0;
    }
`;

export const ProjectBreadcrumbs: React.FunctionComponent<ProjectBreadcrumbsProps> = props => {
    const {projectDetails, projectId} = useProjectManagementStatus();
    let projectNameComponent = <Spinner />;
    if (!projectDetails.loading) {
        const title = projectDetails.data.title;
        projectNameComponent = <>{title.slice(0, 20).trim()}{title.length > 20 ? "..." : ""}</>;
    }

    return <ProjectBreadcrumbsWrapper>
        <li><Link to="/projects">My Projects</Link></li>
        {projectId ? <li><Link to={`/project/dashboard`}>{projectNameComponent}</Link></li> : null}
        {props.crumbs.map((crumb, idx) => {
            if (crumb.link) {
                return <li key={idx}><Link to={crumb.link}>{crumb.title}</Link></li>
            } else {
                return <li key={idx}>{crumb.title}</li>;
            }
        })}
    </ProjectBreadcrumbsWrapper>;
};
