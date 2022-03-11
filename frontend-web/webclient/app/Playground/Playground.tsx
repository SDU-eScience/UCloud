import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {api as ProjectApi, Project} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useProjectId} from "@/Project";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>
                <EveryIcon />
            </Grid>
            <Grid
                gridTemplateColumns="repeat(10, 1fr)"
                style={{overflowY: "scroll"}}
                mb={"32px"}
            >
                {colors.map((c: ThemeColor) => (
                    <Box
                        title={`${c}, ${getCssVar(c)}`}
                        key={c}
                        backgroundColor={c}
                        height={"100px"}
                        width={"100%"}
                    />
                ))}
            </Grid>

            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
            <ProjectPlayground />
        </>
    );
    return <MainContainer main={main}/>;
};

export const ProjectPlayground: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [project, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    useEffect(() => {
        fetchProject(ProjectApi.retrieve({id: projectId, includeMembers: true, includeGroups: true, includeFavorite: true}));
    }, [projectId]);

    if (project.data) {
        return <>Title: {project.data.specification.title}</>;
    } else {
        return "Project is still loading...";
    }
}

const colors = [
    "black",
    "white",
    "textBlack",
    "lightGray",
    "midGray",
    "gray",
    "darkGray",
    "lightBlue",
    "lightBlue2",
    "blue",
    "darkBlue",
    "lightGreen",
    "green",
    "darkGreen",
    "lightRed",
    "red",
    "darkRed",
    "orange",
    "darkOrange",
    "lightPurple",
    "purple",
    "yellow",
    "text",
    "textHighlight",
    "headerText",
    "headerBg",
    "headerIconColor",
    "headerIconColor2",
    "borderGray",
    "paginationHoverColor",
    "paginationDisabled",
    "iconColor",
    "iconColor2",
    "FtIconColor",
    "FtIconColor2",
    "FtFolderColor",
    "FtFolderColor2",
    "spinnerColor",
    "tableRowHighlight",
    "appCard",
    "wayfGreen",
];

export default Playground;
