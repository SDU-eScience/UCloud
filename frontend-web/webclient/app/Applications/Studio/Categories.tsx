import * as React from "react";
import {Button, Flex, Icon, MainContainer} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {callAPI, callAPIWithErrorHandler} from "@/Authentication/DataHook";
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
import {useProjectId} from "@/Project/Api";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";

const Categories: React.FunctionComponent = () => {
    const projectId = useProjectId();

    const [categories, setCategories] = useState<ApplicationCategory[]>([]);
    usePage("Application Studio | Categories", SidebarTabId.APPLICATION_STUDIO);

    const fetchCategories = () => {
        fetchAll(next => {
            return callAPI(AppStore.browseStudioCategories({itemsPerPage: 250, next}));
        }).then(categories => setCategories(categories));
    };

    useEffect(() => {
        fetchCategories();
    }, [projectId]);

    const createCategory = useCallback(async () => {
        const categoryName = (await addStandardInputDialog({
            title: "Create category",
            placeholder: "Name",
        })).result;

        await callAPIWithErrorHandler(AppStore.createCategory({
            title: categoryName,
        }));

        fetchCategories();
    }, []);

    const move = useCallback(async (e: React.SyntheticEvent, up: boolean) => {
        const index = parseInt(findDomAttributeFromAncestors(e.target, "data-idx") ?? "");

        const categoriesCopy = [...categories];
        const newIndex = Math.min(Math.max(0, up ? index - 1 : index + 1), categoriesCopy.length - 1);
        const temp = categoriesCopy[newIndex];
        categoriesCopy[newIndex] = categoriesCopy[index];
        categoriesCopy[index] = temp;

        const promises: Promise<unknown>[] = [];
        for (let i = 0; i < categoriesCopy.length; i++) {
            if (categoriesCopy[i].metadata.priority !== i) {
                promises.push(callAPIWithErrorHandler(AppStore.assignPriorityToCategory({
                    id: categoriesCopy[i].metadata.id,
                    priority: i
                })));
            }
        }
        await Promise.all(promises);
        fetchCategories();
    }, [categories]);

    const moveUp = useCallback((e: React.SyntheticEvent) => {
        move(e, true).then(doNothing);
    }, [move]);

    const moveDown = useCallback((e: React.SyntheticEvent) => {
        move(e, false).then(doNothing);
    }, [move]);

    const deleteCategory = useCallback(async (key: string) => {
        const id = parseInt(key);
        await callAPIWithErrorHandler(AppStore.deleteCategory({id}));
        fetchCategories();
    }, []);

    return <MainContainer
        main={<>
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Category Management</Heading.h2>
                <ProjectSwitcher />
            </Flex>

            <Button onClick={createCategory}>Create category</Button>

            {categories.length < 1 ? <>No categories here</>: <>
                {categories.map((c, idx) => {
                    return <ListRow
                        key={c.metadata.id}
                        left={c.specification.title}
                        right={<>
                            <Flex gap={"8px"}>
                                <Button onClick={moveUp} data-id={c.metadata.id} data-idx={c.metadata.priority}><Icon name={"heroArrowUp"} /></Button>
                                <Button onClick={moveDown} data-id={c.metadata.id} data-idx={c.metadata.priority}><Icon name={"heroArrowDown"} /></Button>
                                <ConfirmationButton actionKey={c.metadata.id.toString()} color={"errorMain"} icon={"heroTrash"} onAction={deleteCategory} />
                            </Flex>
                        </>}
                    />
                })}
            </>}
        </>}
    />;
};

export default Categories;