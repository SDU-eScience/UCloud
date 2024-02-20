import * as React from "react";
import {Button, Flex, Icon, MainContainer} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {callAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {fetchAll} from "@/Utilities/PageUtilities";
import {useCallback, useEffect, useState} from "react";
import {ApplicationCategory} from "@/Applications/AppStoreApi";
import {ListRow} from "@/ui-components/List";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {doNothing} from "@/UtilityFunctions";
import {addStandardInputDialog} from "@/UtilityComponents";
import {findDomAttributeFromAncestors} from "@/Utilities/HTMLUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

const Categories: React.FunctionComponent = () => {
    const [categories, setCategories] = useState<ApplicationCategory[]>([]);
    usePage("Application Studio | Categories", SidebarTabId.APPLICATIONS);

    const refresh = useCallback(() => {
        let didCancel = false;

        (async () => {
            const categories = await fetchAll(next => {
                return callAPI(AppStore.browseCategories({itemsPerPage: 250, next}));
            });

            if (!didCancel) setCategories(categories);
        })();

        return () => {
            didCancel = true;
        };
    }, []);

    useEffect(() => {
        refresh();
    }, []);

    const createCategory = useCallback(async () => {
        const categoryName = (await addStandardInputDialog({
            title: "Create category",
            placeholder: "Name",
        })).result;

        await callAPI(AppStore.createCategory({
            title: categoryName,
        }));

        refresh();
    }, [refresh]);

    const move = useCallback(async (e: React.SyntheticEvent, up: boolean) => {
        const id = parseInt(findDomAttributeFromAncestors(e.target, "data-id") ?? "");
        const index = parseInt(findDomAttributeFromAncestors(e.target, "data-idx") ?? "");
        const newIndex = Math.max(0, up ? index - 1 : index + 1);

        await callAPI(AppStore.assignPriorityToCategory({ id, priority: newIndex }));
        refresh();
    }, []);

    const moveUp = useCallback((e: React.SyntheticEvent) => {
        move(e, true).then(doNothing);
    }, [move]);

    const moveDown = useCallback((e: React.SyntheticEvent) => {
        move(e, false).then(doNothing);
    }, [move]);

    const deleteCategory = useCallback(async (key: string) => {
        const id = parseInt(key);
        await callAPI(AppStore.deleteCategory({id}));
        refresh();
    }, []);

    return <MainContainer
        main={<>
            <Heading.h2>Category Management</Heading.h2>

            <Button onClick={createCategory}>Create category</Button>

            {categories.map((c, idx) => {
                return <ListRow
                    key={c.metadata.id}
                    left={c.specification.title}
                    right={<>
                        <Flex gap={"8px"}>
                            <Button onClick={moveUp} data-id={c.metadata.id} data-idx={idx}><Icon name={"heroArrowUp"} /></Button>
                            <Button onClick={moveDown} data-id={c.metadata.id} data-idx={idx}><Icon name={"heroArrowDown"} /></Button>
                            <ConfirmationButton actionKey={c.metadata.id.toString()} color={"errorMain"} icon={"heroTrash"} onAction={deleteCategory} />
                        </Flex>
                    </>}
                />
            })}
        </>}
    />;
};

export default Categories;