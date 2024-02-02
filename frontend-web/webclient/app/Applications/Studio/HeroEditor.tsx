import * as React from "react";
import {ScaffoldedForm, ScaffoldedFormObject} from "@/ui-components/ScaffoldedForm";
import {dialogStore} from "@/Dialog/DialogStore";
import {GroupSelector} from "@/Applications/Studio/GroupSelector";
import {doNothing} from "@/UtilityFunctions";
import {ApplicationGroup, CarrouselItem, updateCarrousel} from "@/Applications/AppStoreApi";
import {Box, Button, Icon, MainContainer} from "@/ui-components";
import {useCallback, useEffect, useRef, useState} from "react";
import {Hero} from "@/Applications/Landing";
import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {fetchAll} from "@/Utilities/PageUtilities";
import {TooltipV2} from "@/ui-components/Tooltip";

const form: ScaffoldedFormObject = {
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
            ]
        }
    ]
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
    const [rawData, setData] = useState<Record<string, unknown>>({});
    const data = rawData as Partial<HeroData>;
    const errors = useRef<Record<string, string>>({});
    const [slides, imageLinks] = translateHeroData(data);

    const allErrors = Object.values(errors.current);
    const firstError = allErrors.length > 0 ? allErrors[0] : null;

    const refresh = useCallback(async () => {
        let didCancel = false;
        const groupPromise = fetchAll(next => callAPI(AppStore.browseGroups({itemsPerPage: 250, next})));
        const landingPromise = callAPI(AppStore.retrieveLandingPage({}))

        const groups = await groupPromise;
        const carrousel = (await landingPromise).carrousel;
        if (didCancel) return;

        const newData: HeroData = {
            slides: carrousel.map((slide, idx) => ({
                title: slide.title,
                body: slide.body,
                imageCredit: slide.imageCredit,
                image: new File([""], loadedFilename + "/" + idx),
                linkedGroup: slide.linkedGroup ? groups.find(it => it.metadata.id === slide.linkedGroup) : undefined,
                linkedWebPage: slide.linkedWebPage ?? undefined
            }))
        };
        setData(newData);

        const imagePromises = newData.slides.map((it, idx) => {
            return fetch(AppStore.retrieveCarrouselImage({
                index: idx,
                slideTitle: it.title
            })).then(it => it.blob())
        });

        const images = await Promise.all(imagePromises);
        if (didCancel) return;

        const newDataWithImages: HeroData = {
            slides: newData.slides.map((s, index) => ({
                ...s,
                image: new File([images[index]], loadedFilename + "/" + index)
            }))
        };
        setData(newDataWithImages);

        return () => {
            didCancel = true;
        };
    }, []);

    useEffect(() => {
        refresh();
    }, [refresh]);

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
        await refresh();
    }, [data]);

    return <MainContainer
        main={<>
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
                <ScaffoldedForm data={data} errors={errors} onUpdate={setData} element={form}/>
            </Box>
        </>}
    />;
};

// NOTE(Dan): Used to detect if a file has been changed or not. This reduces the number of uploads we need to do.
const loadedFilename = "LOADED_FROM_API";

// NOTE(Dan): A very small transparent 1x1 GIF. Used as a placeholder when no image is supplied.
const dummyImage = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

export default HeroEditor;