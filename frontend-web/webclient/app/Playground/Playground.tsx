import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useEffect, useRef} from "react";
import {EveryIcon, IconName} from "@/ui-components/Icon";
import {Box, Flex} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {api as ProjectApi, useProjectId} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as icons from "@/ui-components/icons";
import {Project} from "@/Project";
import * as d3 from "d3";
import * as plot from "@observablehq/plot";
import * as Plot from "@observablehq/plot";
import * as JobViz from "@/Applications/Jobs/JobViz"
import {WidgetColorIntensity, WidgetWindow} from "@/Applications/Jobs/JobViz"
import {CpuChartDemo} from "@/Playground/D3Test";

const iconsNames = Object.keys(icons) as IconName[];

function sendToProcessor(processor: JobViz.StreamProcessor, message: any) {
    processor.accept(JSON.stringify(message) + "\n");
}

const Playground: React.FunctionComponent = () => {
    const data = React.useMemo((): PlottingData[] => {
        const tick = d3.scaleTime();
        tick.ticks(100);
        return tick.ticks(100).map((t, index, arr) => ({date: t, value: [0, 1].includes(index % 4) ? 0 : 25}));
    }, []);

    const stream = useRef(new JobViz.StreamProcessor());

    useEffect(() => {
        {
            const header: JobViz.WidgetPacketHeader = {
                action: JobViz.WidgetAction.WidgetActionCreate
            };
            sendToProcessor(stream.current, header);

            const widget: JobViz.Widget = {
                id: "test2",
                type: JobViz.WidgetType.WidgetTypeLabel,
                location: {
                    icon: JobViz.WidgetIcon.Chat,
                    window: WidgetWindow.WidgetWindowMain,
                    tab: "Tab",
                },
            };

            sendToProcessor(stream.current, widget);

            const label: JobViz.WidgetLabel = {
                text: "Hello world 2"
            };
            sendToProcessor(stream.current, label);
        }
        {
            const header: JobViz.WidgetPacketHeader = {
                action: JobViz.WidgetAction.WidgetActionCreate
            };
            sendToProcessor(stream.current, header);

            const widget: JobViz.Widget = {
                id: "root",
                type: JobViz.WidgetType.WidgetTypeContainer,
                location: {
                    icon: JobViz.WidgetIcon.Chat,
                    window: WidgetWindow.WidgetWindowMain,
                    tab: "Tab",
                },
            };

            sendToProcessor(stream.current, widget);

            const container: JobViz.WidgetContainer = {
                gap: 0,
                foreground: {shade: JobViz.WidgetColorShade.WidgetColorPrimary, intensity: WidgetColorIntensity.WidgetColorDark},
                background: {shade: JobViz.WidgetColorShade.WidgetColorNone, intensity: WidgetColorIntensity.WidgetColorMain},
                width: {minimum: 0, maximum: 0},
                height: {minimum: 0, maximum: 0},
                grow: 0,
                direction: JobViz.WidgetDirection.WidgetDirectionColumn,
                children: [{id: {id: "test"}}]
            };
            sendToProcessor(stream.current, container);
        }


        {
            const header: JobViz.WidgetPacketHeader = {
                action: JobViz.WidgetAction.WidgetActionCreate
            };
            sendToProcessor(stream.current, header);

            const widget: JobViz.Widget = {
                id: "test",
                type: JobViz.WidgetType.WidgetTypeLabel,
                location: {
                    icon: JobViz.WidgetIcon.Chat,
                    window: WidgetWindow.WidgetWindowMain,
                    tab: "Tab",
                },
            };

            sendToProcessor(stream.current, widget);

            const label: JobViz.WidgetLabel = {
                text: "Hello world"
            };
            sendToProcessor(stream.current, label);
        }

        {
            const header: JobViz.WidgetPacketHeader = {
                action: JobViz.WidgetAction.WidgetActionCreate
            };
            sendToProcessor(stream.current, header);

            const widget: JobViz.Widget = {
                id: "pbar",
                type: JobViz.WidgetType.WidgetTypeProgressBar,
                location: {
                    icon: JobViz.WidgetIcon.Chat,
                    window: WidgetWindow.WidgetWindowMain,
                    tab: "Progress",
                },
            };

            sendToProcessor(stream.current, widget);

            const label: JobViz.WidgetProgressBar = {
                progress: 0,
            };
            sendToProcessor(stream.current, label);
        }

        const update = (progress: number) => {
            {
                if (progress > 1) return;

                const header: JobViz.WidgetPacketHeader = {
                    action: JobViz.WidgetAction.WidgetActionUpdate
                };
                sendToProcessor(stream.current, header);

                const widget: JobViz.Widget = {
                    id: "pbar",
                    type: JobViz.WidgetType.WidgetTypeProgressBar,
                    location: {
                        icon: JobViz.WidgetIcon.Chat,
                        window: WidgetWindow.WidgetWindowMain,
                        tab: "Progress",
                    },
                };

                sendToProcessor(stream.current, widget);

                const label: JobViz.WidgetProgressBar = {
                    progress: progress,
                };
                sendToProcessor(stream.current, label);
            }

            setTimeout(() => {
                update(progress + 0.001);
            }, 100);
        }

        update(0);
    }, []);

    const main = (
        <>
            <CpuChartDemo />
            {/*<JobViz.Renderer processor={stream.current} windows={[JobViz.WidgetWindow.WidgetWindowMain]} />*/}
            {/*<AreaPlot data={data} keyX="date" keyY="value" />*/}
            {/**/}
            {/*<Box mb="60px" />*/}
            {/**/}
            {/*<PaletteColors />*/}
            {/*<Colors />*/}
            {/*<EveryIcon />*/}
            {/*
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
            <ConfirmationButton icon={"trash"} actionText={"Delete"} color={"errorMain"} />

            <TabbedCard>
                <TabbedCardTab icon={"heroChatBubbleBottomCenter"} name={"Messages"}>
                    These are the messages!
                </TabbedCardTab>

                <TabbedCardTab icon={"heroGlobeEuropeAfrica"} name={"Public links"}>
                    Public links go here!
                </TabbedCardTab>

                <TabbedCardTab icon={"heroServerStack"} name={"Connected jobs"}>
                    Connections!
                </TabbedCardTab>
            </TabbedCard>
            */}
        </>
    );
    return <MainContainer main={main} />;
};

type PlottingData = {value: number; date: Date}

export function DealersChoicePlot({data, keyX, keyY}: {data: PlottingData[]; keyX: string; keyY: string}) {
    const containerRef = React.useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (data === undefined) return;
        const areaPlot = Plot.auto(data, {x: keyX, y: keyY, color: "count"}).plot()
        containerRef.current?.append(areaPlot);
        return () => areaPlot.remove();
    }, [data]);

    return <div ref={containerRef} />;
}

export function AreaPlot({data, keyX, keyY, fill = "pink", grid = false}: {data: PlottingData[]; keyX: string; keyY: string, fill?: plot.ChannelValueSpec | undefined; grid?: boolean}) {
    const containerRef = React.useRef<HTMLDivElement>(null);

    useEffect(() => {
        const p = Plot.plot({
            y: {
                grid,
            },
            marks: [
                Plot.areaY(data, {x: keyX, y: keyY, fill, fillOpacity: 0.3}),
                Plot.lineY(data, {x: keyX, y: keyY, fill}),
                Plot.ruleY([0])
            ]
        })
        containerRef.current?.append(p);
        return () => p.remove();
    }, [data]);

    return <div ref={containerRef} />;
}

const ProjectPlayground: React.FunctionComponent = () => {
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
    "primaryMain",
    "primaryLight",
    "primaryDark",
    "primaryContrast",

    "secondaryMain",
    "secondaryLight",
    "secondaryDark",
    "secondaryContrast",

    "errorMain",
    "errorLight",
    "errorDark",
    "errorContrast",

    "warningMain",
    "warningLight",
    "warningDark",
    "warningContrast",

    "infoMain",
    "infoLight",
    "infoDark",
    "infoContrast",

    "successMain",
    "successLight",
    "successDark",
    "successContrast",

    "backgroundDefault",
    "backgroundCard",

    "textPrimary",
    "textSecondary",
    "textDisabled",

    "iconColor",
    "iconColor2",

    "fixedWhite",
    "fixedBlack",

    "wayfGreen",
];


const paletteColors = ["purple", "red", "orange", "yellow", "green", "gray", "blue", "pink"];
const numbers = [5, 10, 20, 30, 40, 50, 60, 70, 80, 90];

function CSSPaletteColorVar({color, num}: {color: string, num: number}) {
    const style: React.CSSProperties = {
        backgroundColor: `var(--${color}-${num})`,
        color: num >= 60 ? "white" : "black",
        width: "150px",
        height: "100px",
        paddingTop: "38px",
        paddingLeft: "32px",
    }
    return <div style={style}>--{color}-{num}</div>
}

function Colors(): React.ReactNode {
    return <Flex>
        {colors.map(color => {
            const style: React.CSSProperties = {
                backgroundColor: `var(--${color})`,
                color: "teal",
                width: "150px",
                height: "100px",
                paddingTop: "38px",
                paddingLeft: "32px",
            }
            return <div key={color} style={style}>--{color}</div>
        })}
    </Flex>
}

function PaletteColors(): React.ReactNode {
    return <Flex>
        {paletteColors.map(color => <div key={color}>{numbers.map(number => <CSSPaletteColorVar key={number} color={color} num={number} />)}</div>)}
    </Flex>
}

export default Playground;


