import * as React from "react";
import {Box, Button, Flex, Icon, MainContainer, Select} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {usePage} from "@/Navigation/Redux";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import * as AppStore from "@/Applications/AppStoreApi";
import {emptyPageV2, fetchAll} from "@/Utilities/PageUtilities";
import {useCallback, useEffect, useState} from "react";
import {ApplicationCategory} from "@/Applications/AppStoreApi";
import {ListRow} from "@/ui-components/List";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";
import {doNothing, inDevEnvironment} from "@/UtilityFunctions";
import {addStandardInputDialog} from "@/UtilityComponents";
import {findDomAttributeFromAncestors} from "@/Utilities/HTMLUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {useProjectId} from "@/Project/Api";
import {ContextSwitcher} from "@/Project/ContextSwitcher";

const Categories: React.FunctionComponent = () => {
    const projectId = useProjectId();

    const [categories, setCategories] = useState<ApplicationCategory[]>([]);
    usePage("Application Studio | Categories", SidebarTabId.APPLICATION_STUDIO);

    const fetchCategories = () => {
        if (!projectId) {
            setCategories([]);
            return;
        }

        fetchAll(next => {
            return callAPI(AppStore.browseCategories({itemsPerPage: 250, next}));
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

        await callAPI(AppStore.createCategory({
            title: categoryName,
        }));

        fetchCategories();
    }, []);

    const move = useCallback(async (e: React.SyntheticEvent, up: boolean) => {
        const id = parseInt(findDomAttributeFromAncestors(e.target, "data-id") ?? "");
        const index = parseInt(findDomAttributeFromAncestors(e.target, "data-idx") ?? "");
        const newIndex = Math.max(0, up ? index - 1 : index + 1);

        await callAPI(AppStore.assignPriorityToCategory({ id, priority: newIndex }));
        fetchCategories();
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
        fetchCategories();
    }, []);

    return <MainContainer
        main={<>
            <Flex justifyContent="space-between" mb="20px">
                <Heading.h2>Category Management</Heading.h2>
                <ContextSwitcher />
            </Flex>

            {categories.length < 1 ? <>No categories here</>: <>
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
        </>}
    />;
};

export default Categories;