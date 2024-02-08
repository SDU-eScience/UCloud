import * as React from "react";
import {Box, Button, Flex, Icon, Input, Label, TextArea} from "@/ui-components/index";
import {useDidMount} from "@/UtilityFunctions";
import {useCallback, useEffect, useMemo, useState} from "react";
import {Toggle} from "@/ui-components/Toggle";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import * as AppStore from "@/Applications/AppStoreApi";
import {dialogStore} from "@/Dialog/DialogStore";
import Image from "@/ui-components/Image";
import {ButtonClass} from "@/ui-components/Button";

export type ScaffoldedFormElement =
    | ScaffoldedFormObject
    | TextElement
    | TextAreaElement
    | ToggleElement
    | SelectorElement
    | ImageElement
    ;

export interface ScaffoldedFormObject {
    type: "Form";
    id: string;
    elements: ScaffoldedFormElement[];
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

export interface TextElement extends BaseElement {
    type: "Text";
}

export interface TextAreaElement extends BaseElement {
    type: "TextArea";
    rows: number;
}

export interface ToggleElement extends BaseElement {
    type: "Toggle";
}

export interface ImageElement extends BaseElement {
    type: "Image";
}

export interface SelectorElement extends BaseElement {
    type: "Selector";
    onShow: () => Promise<unknown>;
    displayValue: (data: unknown | null) => string;
}

const BaseComponent: React.FunctionComponent<{
    element: BaseElement;
    error?: string;
    children: React.ReactNode;
    isEmpty?: boolean;
    noLabel?: boolean;
}> = ({element, children, error, isEmpty, noLabel}) => {
    const content = <>
        <Flex gap={"8px"}>
            {element.label}{isEmpty && error && <Box color={"errorMain"}>(Mandatory)</Box>}
        </Flex>
        {children}
        {element.help && <Box color={"textSecondary"}>{element.help}</Box>}
        {!isEmpty && error && <Box color={"errorMain"}>{error}</Box>}
    </>;
    return <Flex flexDirection={"row"} gap={"8px"}>
        {!noLabel ? <Label>{content}</Label> : <div>{content}</div>}
    </Flex>;
};

export const ScaffoldedForm: React.FunctionComponent<{
    element: ScaffoldedFormElement;
    data: unknown | null;
    onUpdate: (newData: unknown) => void;
    ancestorId?: string;
    errors: React.MutableRefObject<Record<string, string>>;
}> = ({ancestorId, element, data, onUpdate, errors}) => {
    const childId = (ancestorId ?? "") + element.id;
    console.log(childId, data)
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

                const transformedForm: ScaffoldedFormObject = {...element};
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

                            <ScaffoldedForm
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
                        return <ScaffoldedForm
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
                if (!didMount) {
                    updateAndValidate(data ?? null);
                }
            }, [data])

            console.log(data, element.displayValue(data ?? null));

            return <BaseComponent element={element} error={myError} isEmpty={data == null || data == ""}>
                <Flex gap={"8px"}>
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
                    <Button color={"errorMain"} onClick={() => {
                        updateAndValidate(null);
                    }}>
                        <Icon name={"heroXMark"} />
                    </Button>
                </Flex>
            </BaseComponent>;
        }

        case "Image": {
            useEffect(() => {
                if (!didMount) updateAndValidate(null);
            }, [data]);

            const image = data ? data as File : null;
            const imageData = useMemo(() => {
                if (!image) return null;
                return URL.createObjectURL(image);
            }, [image]);

            return <BaseComponent element={element} error={myError} isEmpty={data == null || data == ""} noLabel>
                <Flex gap={"8px"} mb={"8px"}>
                    <label className={ButtonClass}>
                        Upload image
                        <Input
                            cursor={"pointer"}
                            placeholder={element.placeholder}
                            type={"file"}
                            hidden
                            readOnly
                            onChange={async e => {
                                const target = e.target;
                                if (target.files) {
                                    const file = target.files[0];
                                    target.value = "";
                                    updateAndValidate(file);
                                }
                            }}
                        />
                    </label>
                    {imageData &&
                        <Button
                            onClick={() => {
                                updateAndValidate(null);
                            }}
                        >
                            Remove image
                        </Button>
                    }
                </Flex>

                {imageData && <Image src={imageData} width={150} height={150} objectFit={"contain"} />}
            </BaseComponent>;
        }
    }
};
