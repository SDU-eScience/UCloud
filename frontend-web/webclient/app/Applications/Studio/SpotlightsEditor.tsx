import * as React from "react";
import {Box, Button, Flex, Icon, MainContainer} from "@/ui-components";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationGroup, Spotlight, TopPick} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {doNothing} from "@/UtilityFunctions";
import {useCallback, useEffect, useRef, useState} from "react";
import {fetchAll} from "@/Utilities/PageUtilities";
import {callAPI} from "@/Authentication/DataHook";
import {useDidUnmount} from "@/Utilities/ReactUtilities";
import {ListRow} from "@/ui-components/List";
import {AppToolLogo} from "@/Applications/AppToolLogo";
import {deepCopy} from "@/Utilities/CollectionUtilities";
import {SpotlightCard} from "@/Applications/Landing";
import * as Heading from "@/ui-components/Heading";
import {largeModalStyle} from "@/Utilities/ModalUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {useLocation, useNavigate} from "react-router";
import {getQueryParam} from "@/Utilities/URIUtilities";
import AppRoutes from "@/Routes";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {ScaffoldedForm, ScaffoldedFormObject} from "@/ui-components/ScaffoldedForm";
import {GroupSelector} from "@/Applications/Studio/GroupSelector";

const SpotlightForm: ScaffoldedFormObject = {
    type: "Form",
    id: "",
    repeated: false,
    elements: [
        {
            id: "title",
            type: "Text",
            label: "Title",
            placeholder: "Artificial intelligence",
            help: "This is the title which will be displayed in the card's title.",
            validator: (t: string) => {
                if (!t) return "Title cannot be empty";
                return null;
            }
        },
        {
            id: "body",
            type: "TextArea",
            label: "Description",
            help: "This is the text which will be shown next to the applications. Use it to motivate why these applications are interesting.",
            rows: 12,
            validator: (t: string) => {
                if (!t) return "Description cannot be empty";
                return null;
            }
        },
        {
            id: "active",
            type: "Toggle",
            label: "Active",
            help: `A flag indicating if this spotlight is the active spotlight. Only one spotlight can be active at any point.`
        },
        {
            id: "applications",
            title: "Application",
            type: "Form",
            repeated: true,
            minItems: 2,
            maxItems: 5,
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
                    id: "title",
                    type: "Text",
                    label: "Title",
                    help: "The title shown in the application row, this can be different from the application's real name.",
                    validator: data => {
                        if (!data) return "Title cannot be empty";
                        return null;
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

interface SpotlightData extends Record<string, unknown> {
    title: string;
    body: string;
    active: boolean;
    applications?: {
        title: string;
        description: string;
        group: ApplicationGroup;
    }[]
}

function translateSpotlight(data: Partial<SpotlightData>, previous: Spotlight): Spotlight {
    const newSpotlight = deepCopy(previous);
    if (data.active !== undefined) newSpotlight.active = data.active;
    if (data.title) newSpotlight.title = data.title;
    if (data.body) newSpotlight.body = data.body;
    if (data.applications) {
        newSpotlight.applications = [];
        for (let i = 0; i < data.applications.length; i++) {
            const dRow = data.applications[i];

            let row: TopPick;
            if (previous.applications.length > i) {
                const newTopPick = {...previous.applications[i]};
                newSpotlight.applications.push(newTopPick);
                row = newTopPick;
            } else {
                const newTopPick: TopPick = {
                    title: "",
                    groupId: 0,
                    description: "",
                };
                newSpotlight.applications.push(newTopPick);
                row = newTopPick;
            }

            if (dRow.title) row.title = dRow.title;
            if (dRow.group) row.groupId = dRow.group.metadata.id;
            if (dRow.description) row.description = dRow.description;
        }
    }
    return newSpotlight;
}

const SpotlightsEditor: React.FunctionComponent = () => {
    const location = useLocation();
    const navigate = useNavigate();
    const id = getQueryParam(location.search, "id");

    const [rawData, setData] = useState<Record<string, unknown>>({});
    const [spotlightPreview, setSpotlightPreview] = useState<Spotlight>({
        title: "",
        body: "",
        applications: [],
        active: true
    });
    const data = rawData as Partial<SpotlightData>;
    const errors = useRef<Record<string, string>>({});
    const allErrors = Object.values(errors.current);
    const firstError = allErrors.length > 0 ? allErrors[0] : null;

    useEffect(() => {
        if (!id) return;
        const parsed = parseInt(id);
        if (isNaN(parsed)) return;

        let didCancel = false;
        (async () => {
            const groupPromise = fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})));
            const spotlightPromise = callAPI(AppStore.retrieveSpotlight({id: parsed}));

            const groups = await groupPromise;
            const spotlight = await spotlightPromise;
            if (didCancel) return;

            const newData: SpotlightData = {
                title: spotlight.title,
                body: spotlight.body,
                active: spotlight.active,
                applications: spotlight.applications.map(app => ({
                    title: app.title,
                    description: app.description,
                    group: groups.find(g => g.metadata.id === app.groupId)!
                }))
            };

            setData(newData);
        })();

        return () => {
            didCancel = true;
        };
    }, [id]);

    useEffect(() => {
        let didUpdate = false;
        const dcopy = deepCopy(data);
        if (dcopy.applications) {
            for (const d of dcopy.applications) {
                if (d.group && !d.title) {
                    d.title = d.group.specification.title;
                    didUpdate = true;
                }

                if (d.group && !d.description) {
                    d.description = d.group.specification.description;
                    didUpdate = true;
                }
            }
        }

        if (didUpdate) setData(dcopy);
    }, [data]);

    useEffect(() => {
        setSpotlightPreview(prev => translateSpotlight(data, prev));
    }, [data]);

    const onShowPreview = useCallback(() => {
        dialogStore.addDialog(
            <div style={{width: "1100px"}}>
                <SpotlightCard spotlight={spotlightPreview} target={"_blank"} />
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
    }, [spotlightPreview]);

    const onSave = useCallback(async () => {
        const spotlight = translateSpotlight(data, spotlightPreview);
        let actualId: number;
        let didCreate = false;

        if (id) {
            actualId = parseInt(id);
            await callAPI(AppStore.updateSpotlight({ ...spotlight, id: actualId }));
        } else {
            didCreate = true;
            actualId = (await callAPI(AppStore.createSpotlight(spotlight))).id;
        }

        snackbarStore.addSuccess("Spotlight has been saved", false);

        if (didCreate) navigate(AppRoutes.apps.studioSpotlightsEditor(actualId));
    }, [data, id]);

    return <MainContainer
        main={<>
            <Flex gap={"32px"}>
                <Flex flexDirection={"column"} gap={"8px"} flexGrow={1} maxHeight={"calc(100vh - 32px)"} overflowY={"auto"}>
                    <Flex>
                        <Heading.h3>Editing spotlight</Heading.h3>
                        <Box flexGrow={1}/>
                        <TooltipV2 tooltip={!firstError ? undefined : <>Unable to save because of an error in the form: {firstError}</>}>
                            <Button disabled={firstError !== null} color={"successMain"} onClick={onSave}>
                                <Icon name={"heroDocumentCheck"}/>
                                <div>Save</div>
                            </Button>
                        </TooltipV2>
                    </Flex>
                    <ScaffoldedForm element={SpotlightForm} data={rawData} onUpdate={setData} errors={errors}/>
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
                            <SpotlightCard spotlight={spotlightPreview} target={"_blank"}/>
                        </div>
                    </div>
                </div>
            </Flex>
        </>}
    />;
};

export default SpotlightsEditor;
