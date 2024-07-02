import {MainContainer} from "@/ui-components/MainContainer";
import * as React from "react";
import {useEffect} from "react";
import {EveryIcon, IconName} from "@/ui-components/Icon";
import {Flex} from "@/ui-components";
import {ThemeColor} from "@/ui-components/theme";
import {api as ProjectApi, useProjectId} from "@/Project/Api";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as icons from "@/ui-components/icons";
import {Project} from "@/Project";
import {NewAndImprovedProgress} from "@/ui-components/Progress";
import {showWarning} from "@/Accounting/Allocations";

const iconsNames = Object.keys(icons) as IconName[];

interface UsageAndQuota {
    usage: number;
    quota: number;
    unit: string;
    maxUsable: number;
    retiredAmount: number;
    retiredAmountStillCounts: boolean;
}

const usagesAndTypes: {
    uq: UsageAndQuota;
    triangle: boolean;
}[] = [{
    triangle: false,
    uq: {maxUsable: 90 - 4, quota: 100, retiredAmount: 0, retiredAmountStillCounts: false, unit: "N/A", usage: 4}
},{
    triangle: true,
    uq: {maxUsable: 90 - (0.95 * 90), quota: 100, retiredAmount: 0, retiredAmountStillCounts: false, unit: "N/A", usage: 0.95 * 90}
}, {
    triangle: false,
    uq: {maxUsable: 90 - 84, quota: 100, retiredAmount: 0, retiredAmountStillCounts: false, unit: "N/A", usage: 84}
}, /* {
    triangle: true,
    uq: {maxUsable: 0, quota: 100, retiredAmount: 0, retiredAmountStillCounts: false, unit: "N/A", usage: 44}
} */];

const Playground: React.FunctionComponent = () => {
    const main = (
        <>
            {usagesAndTypes.map(({uq, triangle}) => {
                if (uq.quota == 0) return null;
                let usage: number
                if (uq.retiredAmountStillCounts) {
                    usage = uq.usage
                } else {
                    usage = uq.usage - uq.retiredAmount
                }

                return <NewAndImprovedProgress
                    limitPercentage={uq.quota === 0 ? 100 : ((uq.maxUsable + usage) / uq.quota) * 100}
                    label={triangle ? "Expect warning" : "Expect no warning"}
                    percentage={uq.quota === 0 ? 0 : (usage / uq.quota) * 100}
                    withWarning={showWarning(uq.quota, uq.maxUsable, uq.usage)}
                />
            })}


            {/* <NewAndImprovedProgress limitPercentage={20} label="Twenty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={40} label="Forty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={60} label="Sixty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={80} label="Eighty!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={100} label="Hundred!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={120} label="Above!!" percentage={30} />
            <NewAndImprovedProgress limitPercentage={120} label="OY!" percentage={110} />
            <NewAndImprovedProgress limitPercentage={100} label="OY!" percentage={130} withWarning /> */}
            <PaletteColors />
            <Colors />
            <EveryIcon />
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


const paletteColors = ["purple", "red", "orange", "yellow", "green", "gray", "blue"];
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
            return <div style={style}>--{color}</div>
        })}
    </Flex>
}

function PaletteColors(): React.ReactNode {
    return <Flex>
        {paletteColors.map(color => <div>{numbers.map(number => <CSSPaletteColorVar color={color} num={number} />)}</div>)}
    </Flex>
}

export default Playground;
