import * as React from "react";
import {Box, Button, Flex, Icon, MainContainer} from "@/ui-components";
import {ScaffoldedForm, ScaffoldedFormObject} from "@/ui-components/ScaffoldedForm";
import {dialogStore} from "@/Dialog/DialogStore";
import {GroupSelector} from "@/Applications/Studio/GroupSelector";
import {doNothing} from "@/UtilityFunctions";
import {ApplicationGroup, Spotlight, TopPick} from "@/Applications/AppStoreApi";
import * as Heading from "@/ui-components/Heading";
import {TooltipV2} from "@/ui-components/Tooltip";
import {SpotlightCard, TopPicksCard} from "@/Applications/Landing";
import {useCallback, useEffect, useRef, useState} from "react";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {deepCopy} from "@/Utilities/CollectionUtilities";

const form: ScaffoldedFormObject = {
    id: "",
    type: "Form",
    repeated: false,
    elements: [
        {
            id: "applications",
            title: "Application",
            type: "Form",
            repeated: true,
            minItems: 3,
            maxItems: 9,
            elements: [
                {
                    id: "group",
                    type: "Selector",
                    label: "Application",
                    placeholder: "Click to select an application",
                    validator: (d) => {
                        if (!d) return "An application must be selected";
                        return null;
                    },
                    onShow: () => {
                        return new Promise((resolve) => {
                            dialogStore.addDialog(
                                <GroupSelector onSelect={g => {
                                    resolve(g);
                                    dialogStore.success();
                                }}/>,
                                doNothing,
                                true
                            );
                        })
                    },
                    displayValue: (value: ApplicationGroup | null) => {
                        if (value) return value.specification.title;
                        return "";
                    }
                },
                {
                    id: "description",
                    label: "Description",
                    type: "TextArea",
                    help: "The description shown next to the application row, this can be different from the normal description.",
                    rows: 2,
                    validator: data => {
                        if (!data) return "Description cannot be empty";
                        return null;
                    }
                },
            ]
        }
    ]
}

interface TopPicksData extends Record<string, unknown> {
    applications: {
        description: string;
        group: ApplicationGroup;
    }[];
}

const TopPicksEditor: React.FunctionComponent = () => {
    const [rawData, setData] = useState<Record<string, unknown>>({});
    const [topPicksPreview, setTopPicksPreview] = useState<TopPick[]>([]);
    const data = rawData as Partial<TopPicksData>;
    const errors = useRef<Record<string, string>>({});
    const allErrors = Object.values(errors.current);
    const firstError = allErrors.length > 0 ? allErrors[0] : null;

    const refresh = useCallback(() => {
        let didCancel = false;
        (async () => {
            const groupPromise = fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})));
            const landingPromise = callAPI(AppStore.retrieveLandingPage({}));

            const groups = await groupPromise;
            const landing = await landingPromise;
            if (didCancel) return;

            const newData: TopPicksData = {
                applications: landing.topPicks.map(app => ({
                    title: app.title,
                    description: app.description,
                    group: groups.find(g => g.metadata.id === app.groupId)!
                }))
            };

            console.log("Setting this", newData);

            setData(newData);
        })();

        return () => {
            didCancel = true;
        };
    }, []);

    useEffect(() => {
        refresh();
    }, [refresh]);

    useEffect(() => {
        setTopPicksPreview((data.applications ?? []).map(app => ({
            title: app?.group?.specification?.title ?? "",
            description: app.description ?? "",
            groupId: app?.group?.metadata?.id ?? 0,
        })))
    }, [data]);

    useEffect(() => {
        let didUpdate = false;
        const dcopy = deepCopy(data);
        if (dcopy.applications) {
            for (const d of dcopy.applications) {
                if (d.group && !d.description) {
                    d.description = d.group.specification.description;
                    didUpdate = true;
                }
            }
        }

        if (didUpdate) setData(dcopy);
    }, [data]);

    const onSave = useCallback(() => {
        callAPI(AppStore.updateTopPicks({ newTopPicks: topPicksPreview })).then(refresh);
    }, [topPicksPreview, refresh]);

    const onShowPreview = useCallback(() => {
        dialogStore.addDialog(
            <div style={{width: "1100px"}}>
                <TopPicksCard topPicks={topPicksPreview} />
            </div>,
            doNothing,
            true,
            {
                ...largeModalStyle,
                content: {
                    ...largeModalStyle.content,
                    width: "1140px",
                    left: `calc(50vw - 570px)`,
                }
            }
        );
    }, [topPicksPreview]);

    return <MainContainer
        main={<>
            <Flex gap={"32px"}>
                <Flex flexDirection={"column"} gap={"8px"} flexGrow={1} maxHeight={"calc(100vh - 32px)"} overflowY={"auto"}>
                    <Flex>
                        <Heading.h3>Editing top picks</Heading.h3>
                        <Box flexGrow={1}/>
                        <TooltipV2 tooltip={!firstError ? undefined : <>Unable to save because of an error in the form: {firstError}</>}>
                            <Button disabled={firstError !== null} color={"successMain"} onClick={onSave}>
                                <Icon name={"heroCheck"}/>
                                <div>Save</div>
                            </Button>
                        </TooltipV2>
                    </Flex>
                    <ScaffoldedForm element={form} data={rawData} onUpdate={setData} errors={errors}/>
                </Flex>
                <div style={{width: "550px"}}>
                    <Flex gap={"8px"} alignItems={"center"} mb={"16px"}>
                        <Heading.h3>Preview</Heading.h3>
                        <Box flexGrow={1}/>
                        <Button onClick={onShowPreview}>
                            <Icon name={"heroMagnifyingGlass"}/>
                            <div>View real size</div>
                        </Button>
                    </Flex>
                    <div style={{transform: "translate(-25%, -25%) scale(0.5)"}}>
                        <div style={{width: "1100px"}}>
                            <TopPicksCard topPicks={topPicksPreview} />
                        </div>
                    </div>
                </div>
            </Flex>
        </>}
    />;
}

export default TopPicksEditor;