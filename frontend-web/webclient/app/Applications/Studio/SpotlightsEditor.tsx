import * as React from "react";
import {Box, Button, Flex, Icon, Input, Label, MainContainer, TextArea} from "@/ui-components";
import * as AppStore from "@/Applications/AppStoreApi";
import {ApplicationGroup, Spotlight, TopPick} from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import {doNothing, useDidMount} from "@/UtilityFunctions";
import {useCallback, useEffect, useRef, useState} from "react";
import {Toggle} from "@/ui-components/Toggle";
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
import {useLocation} from "react-router";
import {getQueryParam} from "@/Utilities/URIUtilities";

type FormElement = Form | TextElement | TextAreaElement | ToggleElement | SelectorElement;

interface Form {
    type: "Form";
    id: string;
    elements: FormElement[];
    repeated: boolean;
    title?: string;
    validator?: (data: unknown) => string | null;
    minItems?: number;
    maxItems?: number;
}

interface BaseElement {
    id: string;
    label: string;
    placeholder?: string;
    help?: string;
    validator?: (data: unknown) => string | null;
}

interface TextElement extends BaseElement {
    type: "Text";
}

interface TextAreaElement extends BaseElement {
    type: "TextArea";
    rows: number;
}

interface ToggleElement extends BaseElement {
    type: "Toggle";
}

interface SelectorElement extends BaseElement {
    type: "Selector";
    onShow: () => Promise<unknown>;
    displayValue: (data: unknown | null) => string;
}

const BaseComponent: React.FunctionComponent<{
    element: BaseElement;
    error?: string;
    children: React.ReactNode;
    isEmpty?: boolean;
}> = ({element, children, error, isEmpty}) => {
    return <Flex flexDirection={"row"} gap={"8px"}>
        <Label>
            <Flex gap={"8px"}>
                {element.label}{isEmpty && error && <Box color={"errorMain"}>(Mandatory)</Box>}
            </Flex>
            {children}
            {element.help && <Box color={"textSecondary"}>{element.help}</Box>}
            {!isEmpty && error && <Box color={"errorMain"}>{error}</Box>}
        </Label>
    </Flex>;
};

const StupidForm: React.FunctionComponent<{
    element: FormElement;
    data: unknown | null;
    onUpdate: (newData: unknown) => void;
    ancestorId?: string;
    errors: React.MutableRefObject<Record<string, string>>;
}> = ({ancestorId, element, data, onUpdate, errors}) => {
    const childId = (ancestorId ?? "") + element.id;
    const myError = errors?.current?.[childId];

    const didMount = useDidMount();

    function validate(newValue: unknown): { didChange: boolean } {
        let errorMessage: string | null = null;
        if ("validator" in element && element.validator) {
            errorMessage = element.validator(newValue)
        }

        if (errorMessage) {
            const oldError = errors.current[childId];
            errors.current[childId] = errorMessage;
            return {didChange: oldError != errorMessage};
        } else {
            const hadError = errors.current[childId] != null;
            delete errors.current[childId];
            return {didChange: hadError};
        }
    }

    useEffect(() => {
        validate(data);
    }, [data]);

    function updateAndValidate(newValue: unknown) {
        if (!errors.current) return;
        validate(newValue);
        onUpdate(newValue);
    }

    switch (element.type) {
        case "Form": {
            const elements = element.elements;
            const errorElement = <>
                {myError && <Box color={"errorMain"}>{myError}</Box>}
            </>;

            if (element.repeated) {
                let eData = data as unknown[] | undefined | null;
                if (eData === null || eData === undefined) {
                    eData = [{}];
                }

                const eArray = eData as unknown[];
                if (element.minItems && eArray.length < element.minItems) {
                    for (let i = 0; i < element.minItems - eArray.length; i++) {
                        eArray.push({});
                    }
                }

                useEffect(() => {
                    if (eData !== data) {
                        updateAndValidate(eData);
                    }
                }, [eData]);


                const deleteDisabled = element.minItems !== undefined && eArray.length <= element.minItems;
                const addDisabled = element.maxItems !== undefined && eArray.length >= element.maxItems;

                const transformedForm: Form = {...element};
                transformedForm.repeated = false;
                transformedForm.title = "";

                return <>
                    {errorElement}

                    {eArray.length == 0 && <>
                        <Button onClick={() => {
                            const newArray = [...eArray];
                            newArray.push({});
                            updateAndValidate(newArray);
                        }}>
                            <Icon name={"heroPlus"}/>
                            <div>Create {element.title}</div>
                        </Button>
                    </>}

                    {eArray.map((d, idx) => {
                        return <Box key={idx}>
                            <Flex gap={"8px"} alignItems={"center"}>
                                <div>{element.title} #{idx + 1}</div>
                                <Button disabled={addDisabled} onClick={() => {
                                    const newArray = [...eArray];
                                    newArray.splice(idx + 1, 0, {});
                                    updateAndValidate(newArray);
                                }}>
                                    <Icon name={"heroPlus"}/>
                                </Button>
                                <Button disabled={deleteDisabled} color={"errorMain"} onClick={() => {
                                    const newArray = [...eArray];
                                    newArray.splice(idx, 1);
                                    updateAndValidate(newArray);
                                }}>
                                    <Icon name={"heroTrash"}/>
                                </Button>
                                <Box flexGrow={1}/>
                                <Button disabled={idx == 0} onClick={() => {
                                    if (idx == 0) return;
                                    const newArray = [...eArray];
                                    const tmp = newArray[idx - 1]
                                    newArray[idx - 1] = newArray[idx];
                                    newArray[idx] = tmp;
                                    updateAndValidate(newArray);
                                }}>
                                    <Icon name={"heroArrowUp"}/>
                                </Button>

                                <Button disabled={idx == eArray.length - 1} onClick={() => {
                                    if (idx == eArray.length - 1) return;
                                    const newArray = [...eArray];
                                    const tmp = newArray[idx + 1]
                                    newArray[idx + 1] = newArray[idx];
                                    newArray[idx] = tmp;
                                    updateAndValidate(newArray);
                                }}>
                                    <Icon name={"heroArrowDown"}/>
                                </Button>
                            </Flex>

                            <StupidForm
                                errors={errors}
                                element={transformedForm}
                                data={d}
                                ancestorId={childId + idx}
                                onUpdate={newData => {
                                    const newArray = [...eArray];
                                    newArray[idx] = newData;
                                    updateAndValidate(newArray);
                                }}
                            />
                        </Box>
                    })}
                </>
            }

            return <Box>
                {element.id && element.title && <Box mb={"16px"}>{element.title}</Box>}
                {errorElement}
                <Flex flexDirection={"column"} ml={element.id ? "30px" : undefined} gap={"16px"}>
                    {elements.map(e => {
                        const eData = data && typeof data === "object" ? data[e.id] ?? null : null;
                        const eUpdate = (newValue: unknown) => {
                            if (data && typeof data === "object") {
                                const newData = {...data};
                                newData[e.id] = newValue;
                                updateAndValidate(newData);
                            } else {
                                const newData = {};
                                newData[e.id] = newValue;
                                updateAndValidate(newData);
                            }
                        };
                        return <StupidForm
                            key={e.id}
                            element={e}
                            data={eData}
                            ancestorId={childId}
                            onUpdate={eUpdate}
                            errors={errors}
                        />;
                    })}
                </Flex>
            </Box>;
        }
        case "Text": {
            const updateData = useCallback((e: React.SyntheticEvent) => {
                const newValue = (e.target as HTMLInputElement).value;
                updateAndValidate(newValue);
            }, [onUpdate]);

            useEffect(() => {
                if (data == null) updateAndValidate("");
            }, [data])

            return <BaseComponent element={element} error={myError} isEmpty={data == null || data == ""}>
                <Input value={data as string ?? ""} onChange={updateData} placeholder={element.placeholder}/>
            </BaseComponent>;
        }
        case "TextArea": {
            const updateData = useCallback((e: React.SyntheticEvent) => {
                const newValue = (e.target as HTMLInputElement).value;
                updateAndValidate(newValue);
            }, [onUpdate]);

            useEffect(() => {
                if (data == null) updateAndValidate("");
            }, [data]);

            return <BaseComponent element={element} error={myError} isEmpty={data == null || data == ""}>
                <TextArea rows={element.rows} value={data as string ?? ""} onChange={updateData}
                          placeholder={element.placeholder}/>
            </BaseComponent>;
        }
        case "Toggle": {
            const updateData = useCallback((prev: boolean) => {
                const newValue = !prev;
                updateAndValidate(newValue);
            }, [onUpdate]);

            useEffect(() => {
                if (data == null) updateAndValidate(false);
            }, [])

            return <BaseComponent element={element} error={myError}>
                <Toggle checked={data as boolean ?? false} onChange={updateData}/>
            </BaseComponent>;
        }

        case "Selector": {
            useEffect(() => {
                if (!didMount) updateAndValidate(null);
            }, [data])

            return <BaseComponent element={element} error={myError} isEmpty={data == null || data == ""}>
                <Input
                    cursor={"pointer"}
                    placeholder={element.placeholder}
                    value={element.displayValue(data ?? null)}
                    readOnly
                    onClick={e => {
                        e.preventDefault();
                        element.onShow().then(data => {
                            updateAndValidate(data);
                        })
                    }}
                />
            </BaseComponent>;
        }
    }
};

const SpotlightForm: Form = {
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
            rows: 3,
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

const GroupSelector: React.FunctionComponent<{ onSelect: (group: ApplicationGroup) => void }> = props => {
    const didCancel = useDidUnmount();
    const [groups, setGroups] = useState<ApplicationGroup[]>([]);
    useEffect(() => {
        fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})))
            .then(g => didCancel.current === false && setGroups(g));
    }, [])
    return <Flex flexDirection={"column"} maxHeight={"100%"} overflowY={"auto"}>
        {groups.map(g =>
            <ListRow
                key={g.metadata.id}
                left={<><AppToolLogo name={g.metadata.id.toString()} type={"GROUP"}
                                     size={"32px"}/> {g.specification.title}</>}
                right={<Button onClick={() => props.onSelect(g)}>Use</Button>}
            />
        )}
    </Flex>;
};

function translateSpotlight(data: Partial<SpotlightData>, previous: Spotlight): Spotlight {
    const newSpotlight = deepCopy(previous);
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

const Spotlights: React.FunctionComponent = () => {
    const location = useLocation();
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

    return <MainContainer
        main={<>

            <Flex gap={"32px"}>
                <Flex flexDirection={"column"} gap={"8px"} flexGrow={1} maxHeight={"calc(100vh - 32px)"} overflowY={"auto"}>
                    <Flex>
                        <Heading.h3>Editing spotlight</Heading.h3>
                        <Box flexGrow={1}/>
                        <TooltipV2 tooltip={!firstError ? undefined : <>Unable to save because of an error in the form: {firstError}</>}>
                            <Button disabled={firstError !== null} color={"successMain"}>
                                <Icon name={"heroDocumentCheck"}/>
                                <div>Save</div>
                            </Button>
                        </TooltipV2>
                    </Flex>
                    <StupidForm element={SpotlightForm} data={rawData} onUpdate={setData} errors={errors}/>
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

export default Spotlights;
