import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import Icon, {EveryIcon, IconName} from "@/ui-components/Icon";
import {Grid, Box, Button, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ThemeColor} from "@/ui-components/theme";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {api as ProjectApi, Project, useProjectId} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import BaseLink from "@/ui-components/BaseLink";
import {sendNotification} from "@/Notifications";
import {capitalized, timestampUnixMs} from "@/UtilityFunctions";
import {ProductSelectorPlayground} from "@/Products/Selector";
import * as icons from "@/ui-components/icons";
import {BinaryAllocator, loadMessage, messageTest} from "@/UCloud/Messages";
import {
    AppParameterFile,
    Wrapper
} from "@/UCloud/Scratch";
import {ContextSwitcher} from "@/Project/ContextSwitcher";
import {useSelector} from "react-redux";
import {getCssPropertyValue} from "@/Utilities/StylingUtilities";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {SnackType} from "@/Snackbar/Snackbars";
import {ResourceBrowseFeatures} from "@/ui-components/ResourceBrowser";
import {dialogStore} from "@/Dialog/DialogStore";
import {Operation, ShortcutKey} from "@/ui-components/Operation";
import {refreshFunctionCache} from "@/ui-components/Sidebar";
import {useRefresh} from "@/Utilities/ReduxUtilities";
import {injectStyle} from "@/Unstyled";

const iconsNames = Object.keys(icons) as IconName[];

export const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            <Button onClick={() => {
                messageTest();
            }}>UCloud message test</Button>
            <Button onClick={() => {
                function useAllocator<R>(block: (allocator: BinaryAllocator) => R): R {
                    const allocator = BinaryAllocator.create(1024 * 512, 128)
                    return block(allocator);
                }

                const encoded = useAllocator(alloc => {
                    const root = Wrapper.create(
                        alloc,
                        AppParameterFile.create(
                            alloc,
                            "/home/dan/.vimrc"
                        )
                    );
                    alloc.updateRoot(root);
                    return alloc.slicedBuffer();
                });

                console.log(encoded);

                {
                    const value = loadMessage(Wrapper, encoded).wrapThis;

                    if (value instanceof AppParameterFile) {
                        console.log(value.path, value.encodeToJson());
                    } else {
                        console.log("Not a file. It must be something else...", value);
                    }
                }
            }}>UCloud message test2</Button>
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

            <Button onClick={() => snackbarStore.addSuccess("Hello. This is a success.", false, 5000)}>Add success notification</Button>
            <Button onClick={() => snackbarStore.addInformation("Hello. This is THE information.", false, 5000)}>Add info notification</Button>
            <Button onClick={() => snackbarStore.addFailure("Hello. This is a failure.", false, 5000)}>Add failure notification</Button>
            <Button onClick={() => snackbarStore.addSnack({
                message: "Hello. This is a custom one with a text that's pretty long.",
                addAsNotification: false,
                icon: iconsNames.at(Math.floor(Math.random() * iconsNames.length))!,
                type: SnackType.Custom
            })}>Add custom notification</Button>

            <Grid gridTemplateColumns={"repeat(5, 1fr)"} mb={"32px"}>
                <EveryIcon />
            </Grid>

            <Grid
                gridTemplateColumns="repeat(10, 1fr)"
                style={{overflowY: "auto"}}
                mb={"32px"}
            >
                {colors.map((c: ThemeColor) => (
                    <div
                        title={`${c}, var(${c})`}
                        key={c}
                        style={{color: "black", backgroundColor: `var(--${c})`, height: "100%", width: "100%"}}
                    >
                        {c} {getCssPropertyValue(c)}
                    </div>
                ))}
            </Grid>
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"red"} />
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

const ARROW_UP = "↑";
const ARROW_DOWN = "↓";
const ALT_KEY = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌥" : "alt";
const CTRL_KEY = navigator["userAgentData"]?.["platform"] === "macOS" ? "⌘" : "ctrl";
const ShortcutClass = injectStyle("shortcut", k => `
    ${k} {
        border-radius: 5px;
        border: 1px solid var(--black);
        font-size: 12px;
        min-width: 20px;
        height: 20px;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 0 5px;
    }
`);

interface ShortcutProps {
    name: string;
    ctrl?: boolean;
    alt?: boolean;
    shift?: boolean;
    keys: string | string[];
}

const Shortcut: React.FunctionComponent<ShortcutProps> = props => {
    const normalizedKeys = typeof props.keys === "string" ? [props.keys] : props.keys;
    return <tr>
        <td>{props.name}</td>
        <td>
            <Flex gap={"4px"} justifyContent={"end"}>
                {normalizedKeys.map(((k, i) =>
                    <React.Fragment key={i}>
                        {i > 0 && <> or </>}
                        {props.ctrl && <div className={ShortcutClass}>{CTRL_KEY}</div>}
                        {props.alt && <div className={ShortcutClass}>{ALT_KEY}</div>}
                        {props.shift && <div className={ShortcutClass}>Shift</div>}
                        <div className={ShortcutClass}>{k}</div>
                    </React.Fragment>
                ))}
            </Flex>
        </td>
    </tr>;
}

const ControlsClass = injectStyle("controls", k => `
    ${k} table {
        margin: 16px 0;
        width: 100%;
    }
    
    ${k} td:first-child, ${k} th:first-child {
        border-left: 1px solid var(--borderGray);
    }
    
    ${k} td:last-child, ${k} th:last-child {
        border-right: 1px solid var(--borderGray);
    }
    
    ${k} td, ${k} th {
        padding: 8px;
        border-top: 1px solid var(--borderGray);
        border-bottom: 1px solid var(--borderGray);
    }
    
    ${k} td:last-child {
        text-align: right;
    }
`);

interface ControlDescription {
    name: string;
    shortcut?: Partial<ShortcutProps>;
    description?: string;
}

function ControlsDialog({features, custom}: {features: ResourceBrowseFeatures, custom?: ControlDescription[]}) {
    return <div className={ControlsClass}>
        <Heading.h4>Controls and keyboard shortcuts</Heading.h4>
        <table>
            <tbody>
            <Shortcut name={"Move selection up/down (Movement keys)"} keys={[ARROW_UP, "Home", "PgUp", ARROW_DOWN, "End", "PgDown"]}/>
            <Shortcut name={"Select multiple (in a row)"} shift keys={["Movement key", "Left click"]}/>
            <Shortcut name={"Select multiple (individual)"} ctrl keys={["Left click"]}/>
            <tr>
                <td>Go to row</td>
                <td>Type part of name</td>
            </tr>
            <Shortcut name={"Open selection"} keys={"Enter"}/>
            <Shortcut name={"Delete"} keys={"Delete"}/>
            {(features.supportsMove || features.supportsCopy) && <>
                <Shortcut name={"Undo"} ctrl keys={"Z"}/>
                <Shortcut name={"Copy"} ctrl keys={"C"}/>
                <Shortcut name={"Cut"} ctrl keys={"X"}/>
                <Shortcut name={"Paste"} ctrl keys={"V"}/>
            </>}

            {(custom ?? []).map((c, i) => <React.Fragment key={i}>
                {c.shortcut && <Shortcut name={c.name} keys={[]} {...c.shortcut} />}
                {c.description && <tr>
                    <td>{c.name}</td>
                    <td>{c.description}</td>
                </tr>}
            </React.Fragment>)}
            </tbody>
        </table>
    </div>
}

export function controlsOperation(features: ResourceBrowseFeatures, custom?: ControlDescription[]): Operation<unknown, unknown> & any {
    return {
        text: "",
        icon: "heroBolt",
        onClick: () => dialogStore.addDialog(<ControlsDialog features={features} custom={custom} />, () => {}),
        enabled: () => true,
        shortcut: ShortcutKey.Z,
        hackNotInTheContextMenu: true
    };
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
    return (<Flex zIndex={"1"} alignItems={"center"}>
        <Box width="32px"><SearchThing enabled={props.searchEnabled} /></Box>
        <Box width="32px" mr={10}><RefreshThing /></Box>
        <ContextSwitcher />
    </Flex>);
}

function SearchThing({enabled}): JSX.Element | null {
    if (!enabled) return null;
    return <Icon size={20} color="var(--primary)" name="heroMagnifyingGlass" />
}

function RefreshThing(): JSX.Element | null {
    const refresh = useRefresh();
    const spin = useSelector((it: ReduxObject) => it.loading);
    const loading = useSelector((it: ReduxObject) => it.status.loading);
    if (!refresh) return null;
    return <Icon cursor="pointer" size={24} onClick={refresh} spin={spin || loading} hoverColor="blue"
        color="var(--primary)" name="heroArrowPath" />
}

export default Playground;
