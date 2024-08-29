import * as React from "react";
import {ScaffoldedForm, ScaffoldedFormObject} from "@/ui-components/ScaffoldedForm";
import {dialogStore} from "@/Dialog/DialogStore";
import {GroupSelector} from "@/Applications/Studio/GroupSelector";
import {doNothing} from "@/UtilityFunctions";
import {ApplicationGroup, CarrouselItem, updateCarrousel} from "@/Applications/AppStoreApi";
import {Box, Button, Flex, Icon, MainContainer, Select} from "@/ui-components";
import {useCallback, useEffect, useRef, useState} from "react";
import {Hero} from "@/Applications/Landing";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import * as Heading from "@/ui-components/Heading";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const form: (storeFront: number) => ScaffoldedFormObject = (storeFront: number) => {
    return {
        id: "",
        type: "Form",
        repeated: false,
        elements: [
            {
                id: "slides",
                type: "Form",
                title: "Slide",
                minItems: 1,
                maxItems: 6,
                repeated: true,
                elements: [
                    {
                        type: "Text",
                        id: "title",
                        label: "Title",
                        help: "The title of the slide.",
                        placeholder: "New application on UCloud!"
                    },
                    {
                        type: "TextArea",
                        id: "body",
                        label: "Body",
                        help: "The text on the slide.",
                        placeholder: "Lorem ipsum dolar sit amet...",
                        rows: 12,
                    },
                    {
                        type: "Text",
                        id: "imageCredit",
                        label: "Image credit",
                        help: "Attribution for the image",
                        placeholder: "Lorem ipsum dolar sit amet..."
                    },
                    {
                        type: "Image",
                        id: "image",
                        label: "Image",
                        help: "Image",
                    },
                    {
                        type: "Text",
                        id: "linkedWebPage",
                        label: "Link (webpage)",
                        placeholder: "https://example.com",
                        help: "A link to a web page (cannot be used with other link options)"
                    },
                    {
                        id: "linkedGroup",
                        type: "Selector",
                        label: "Link (application)",
                        placeholder: "Click to select an application",
                        help: "A link to an application (cannot be used with other link options)",
                        onShow: () => {
                            return new Promise((resolve) => {
                                dialogStore.addDialog(
                                    <GroupSelector
                                        storeFront={storeFront}
                                        onSelect={g => {
                                            resolve(g);
                                            dialogStore.success();
                                        }}
                                    />,
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
                ]
            }
        ]
    };
};

interface HeroData extends Record<string, unknown> {
    slides: {
        title: string;
        body: string;
        imageCredit: string;
        image: File;
        linkedGroup?: ApplicationGroup;
        linkedWebPage?: string;
    }[]
}

function translateHeroData(data: Partial<HeroData>): [CarrouselItem[], string[]] {
    const slides: Partial<HeroData["slides"][0]>[] = data.slides ?? [];
    const items = slides.map<CarrouselItem>(s => ({
        title: s.title ?? "",
        body: s.body ?? "",
        imageCredit: s.imageCredit ?? "",
        linkedGroup: s.linkedGroup?.metadata?.id ?? undefined,
        linkedWebPage: s.linkedWebPage ?? undefined,
    }));

    const links = slides.map(s => s.image ? URL.createObjectURL(s.image) : dummyImage);
    return [items, links]
}

const HeroEditor: React.FunctionComponent = () => {
    usePage("Carrousel editor", SidebarTabId.APPLICATION_STUDIO);

    const [rawData, setData] = useState<Record<string, unknown>>({});
    const data = rawData as Partial<HeroData>;
    const errors = useRef<Record<string, string>>({});
    const [slides, imageLinks] = translateHeroData(data);
    const selectRef = React.useRef<HTMLSelectElement>(null);

    const [storeFront, setStoreFront] = React.useState<number>(0);
    const [storeFronts, setStoreFronts] = useCloudAPI(
        AppStore.browseStoreFronts({itemsPerPage: 250}),
        emptyPageV2
    );

    useEffect(() => {
        if (storeFront < 1) return;
        callAPI(AppStore.retrieveLandingPage({storeFront: storeFront})).then(landingPage => {
            let didCancel = false;

            fetchAll(next => callAPI(AppStore.browseGroups({storeFront: storeFront, itemsPerPage: 250, next}))).then(groups => {
                const newData: HeroData = {
                    slides: landingPage.carrousel.map((slide, idx) => ({
                    title: slide.title,
                    body: slide.body,
                    imageCredit: slide.imageCredit,
                    image: new File([""], loadedFilename + "/" + idx),
                    linkedGroup: slide.linkedGroup ? groups.find(it => it.metadata.id === slide.linkedGroup) : undefined,
                    linkedWebPage: slide.linkedWebPage ?? undefined
                }))};

                setData(newData);

                const imagePromises = newData.slides.map((it, idx) => {
                    return fetch(AppStore.retrieveCarrouselImage({
                        index: idx,
                        slideTitle: it.title
                    })).then(it => it.blob())
                });

                Promise.all(imagePromises).then(images => {
                    if (didCancel) return;

                    const newDataWithImages: HeroData = {
                        slides: newData.slides.map((s, index) => ({
                            ...s,
                            image: new File([images[index]], loadedFilename + "/" + index)
                        }))
                    };
                    setData(newDataWithImages);
                });
            });
        });
    }, [storeFront]);


    const allErrors = Object.values(errors.current);
    const firstError = allErrors.length > 0 ? allErrors[0] : null;

    const onSave = useCallback(async () => {
        const [normalized] = translateHeroData(data);
        const promises: Promise<unknown>[] = [];
        await callAPI(updateCarrousel({ newSlides: normalized }));

        const slides = data.slides ?? [];
        for (let i = 0; i < slides.length; i++) {
            const slide = slides[i];
            if (!slide.image) continue;
            if (slide.image.name === loadedFilename + "/" + i) continue;

            promises.push(AppStore.updateCarrouselImage(i, slide.image));
        }

        await Promise.all(promises);
    }, [data]);

    return <MainContainer
        header={
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Carrousel</Heading.h2>
                <Select selectRef={selectRef} width={500} onChange={() => {
                    if (!selectRef.current) return;
                    if (selectRef.current.value === "") return;
                    setStoreFront(parseInt(selectRef.current.value, 10));
                }}>
                    <option disabled selected>Select store front...</option>
                    {storeFronts.data.items.map(front => 
                        <option value={front.metadata.id}>{front.specification.title}</option>
                    )}
                </Select>
            </Flex>
        }
        main={<>
            {storeFront < 1 ? null : <>
                <Box width={"1100px"} margin={"30px auto"}>
                    <Hero slides={slides} imageLinks={imageLinks} isPreview={true}/>
                </Box>
                <Box height={"calc(100vh - 16px - 30px - 420px)"} overflowY={"auto"}>
                    <TooltipV2 tooltip={!firstError ? undefined : <>Unable to save because of an error in the form: {firstError}</>}>
                        <Button fullWidth disabled={firstError !== null} color={"successMain"} onClick={onSave} mb={"16px"}>
                            <Icon name={"heroCheck"}/>
                            <div>Save</div>
                        </Button>
                    </TooltipV2>
                    <ScaffoldedForm data={data} errors={errors} onUpdate={setData} element={form(storeFront)}/>
                </Box>
            </>}
        </>}
    />;
};

// NOTE(Dan): Used to detect if a file has been changed or not. This reduces the number of uploads we need to do.
const loadedFilename = "LOADED_FROM_API";

// NOTE(Dan): A very small transparent 1x1 GIF. Used as a placeholder when no image is supplied.
const dummyImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

export default HeroEditor;