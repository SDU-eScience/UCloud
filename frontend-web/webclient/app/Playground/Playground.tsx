import {MainContainer} from "@/MainContainer/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import Icon, {EveryIcon} from "@/ui-components/Icon";
import {Grid, Box, Button, Flex} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {ConfirmationButton, ConfirmationButtonPlainHTML} from "@/ui-components/ConfirmationAction";
import {api as ProjectApi, Project, useProjectId} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import BaseLink from "@/ui-components/BaseLink";
import {sendNotification} from "@/Notifications";
import {timestampUnixMs} from "@/UtilityFunctions";
import {ProductSelectorPlayground} from "@/Products/Selector";
import {useSelector} from "react-redux";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {Toggle} from "@/ui-components/Toggle";
import {getCssColorVar} from "@/Utilities/StyledComponentsUtilities";
import {div} from "@/Utilities/HTMLUtilities";

export const Playground: React.FunctionComponent = () => {
    const [checked, setChecked] = React.useState(false);
    const confirmationButtonPlainHTMLRef = React.useRef<HTMLDivElement>(null);

    React.useLayoutEffect(() => {
        const image = div("")
        image.style.backgroundColor = "black";
        image.style.color = "black";
        image.style.backgroundImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";
        confirmationButtonPlainHTMLRef.current?.append(ConfirmationButtonPlainHTML(image, "Some text", () => console.log("foobar"), {}));
    }, []);


    const main = (
        <>
            <UtilityBar searchEnabled />
            <Toggle checked={checked} onChange={() => setChecked(e => !e)} />

            <ProductSelectorPlayground />
            <Button onClick={() => {
                const now = timestampUnixMs();
                for (let i = 0; i < 50; i++) {
                    sendNotification({
                        icon: "bug",
                        title: `Notification ${i}`,
                        body: "This is a test notification",
                        isPinned: false,
                        uniqueId: `${now}-${i}`,
                    });
                }
            }}>Trigger 50 notifications</Button>
            <Button onClick={() => {
                sendNotification({
                    icon: "logoSdu",
                    title: `This is a really long notification title which probably shouldn't be this long`,
                    body: "This is some text which maybe is slightly longer than it should be but who really cares.",
                    isPinned: false,
                    uniqueId: `${timestampUnixMs()}`,
                });
            }}>Trigger notification</Button>

            <Button onClick={() => {
                sendNotification({
                    icon: "key",
                    title: `Connection required`,
                    body: <>
                        You must <BaseLink href="#">re-connect</BaseLink> with 'Hippo' to continue
                        using it.
                    </>,
                    isPinned: true,
                    // NOTE(Dan): This is static such that we can test the snooze functionality. You will need to
                    // clear local storage for this to start appearing again after dismissing it enough times.
                    uniqueId: `playground-notification`,
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
                    <div
                        title={`${c}, var(${c})`}
                        key={c}
                        style={{color: "black", backgroundColor: `var(--${c})`, height: "100%", width: "100%"}}
                    >
                        {c} {getCssColorVar(c)}
                    </div>
                ))}
            </Grid>
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
            <div ref={confirmationButtonPlainHTMLRef} />
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

export function UtilityBar(props: {searchEnabled: boolean;}): JSX.Element {
    return (<Flex zIndex={"1"}>
        <Box width="32px"><SearchThing enabled={props.searchEnabled} /></Box>
        <Box width="32px"><RefreshThing /></Box>
        <Box width="200px"><ContextSwitcher /></Box>
    </Flex>);
}

function SearchThing({enabled}): JSX.Element | null {
    if (!enabled) return null;
    return <Icon size={20} color="var(--blue)" name="heroMagnifyingGlass" />
}

function RefreshThing(): JSX.Element | null {
    const refresh = useSelector((it: ReduxObject) => it.header.refresh);
    const spin = useSelector((it: ReduxObject) => it.loading);
    const loading = useSelector((it: ReduxObject) => it.status.loading);
    if (!refresh) return null;
    return <Icon cursor="pointer" size={24} onClick={refresh} spin={spin || loading} hoverColor="blue" color="var(--blue)" name="heroArrowPath" />
}

export default Playground;
