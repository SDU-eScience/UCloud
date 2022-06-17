import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box, Button} from "@/ui-components";
import theme, {ThemeColor} from "@/ui-components/theme";
import {getCssVar} from "@/Utilities/StyledComponentsUtilities";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {api as ProjectApi, Project} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import {useProjectId} from "@/Project";
import BaseLink from "@/ui-components/BaseLink";
import {triggerNotification, NotificationContainer} from "@/Notifications/NotificationContainer";

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <NotificationContainer />
            <Button onClick={() => {
                for (let i = 0; i < 50; i++) {
                    triggerNotification({
                        icon: "bug",
                        title: `Notification ${i}`,
                        body: "This is a test notification",
                        isPinned: false
                    });
                }
            }}>Trigger 50 notifications</Button>
            <Button onClick={() => {
                triggerNotification({
                    icon: "logoSdu",
                    title: `This is a really long notification title which probably shouldn't be this long`,
                    body: "This is some text which maybe is slightly longer than it should be but who really cares.",
                    isPinned: false
                });
            }}>Trigger notification</Button>

            <Button onClick={() => {
                triggerNotification({
                    icon: "key",
                    title: `Connection required`,
                    body: <>
                        You must <BaseLink href="javascript:void(0)">re-connect</BaseLink> with 'Hippo' to continue 
                        using it.
                    </>,
                    isPinned: true
                });
            }}>Trigger pinned notification</Button>



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
    return <MainContainer main={main} />;
};

export const ProjectPlayground: React.FunctionComponent = () => {
    const projectId = useProjectId();
    const [project, fetchProject] = useCloudAPI<Project | null>({noop: true}, null);
    useEffect(() => {
        fetchProject(ProjectApi.retrieve({id: projectId ?? "", includeMembers: true, includeGroups: true, includeFavorite: true}));
    }, [projectId]);

    if (project.data) {
        return <>Title: {project.data.specification.title}</>;
    } else {
        return <>Project is still loading...</>;
    }
}

const colors: ThemeColor[] = [
    "black",
    "white",
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
