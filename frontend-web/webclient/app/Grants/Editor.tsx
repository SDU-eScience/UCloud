import * as React from "react";
import {injectStyle} from "@/Unstyled";
import MainContainer from "@/MainContainer/MainContainer";
import {Button, Checkbox, Icon, Input, Select, TextArea} from "@/ui-components";
import {IconName} from "@/ui-components/Icon";
import {Logo} from "@/Project/Grant/ProjectBrowser";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {useCallback, useEffect, useReducer, useState} from "react";
import {Product, ProductType} from "@/Accounting";
import {browseAffiliations, browseProducts, GrantApplication} from "@/Project/Grant/GrantApplicationTypes";
import {grant, PageV2} from "@/UCloud";
import ProjectWithTitle = grant.ProjectWithTitle;
import {callAPI} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {retrieveDescription, RetrieveDescriptionResponse} from "@/Project/Grant";
import {useDidUnmount} from "@/Utilities/ReactUtilities";

interface ApplicationSection {
    title: string;
    description: string;
    rows: number;
    limit?: number;
}

const defaultTemplate = `Project Title
--------------
Add a descriptive title for this project. It might be a longer version of the title set above (max 128 ch).

Short abstract
-----------------
Provide a short abstract of your project (max 250 ch).

Description of the specific research activities
-----------------------------------------------------
Describe the specific calculations you plan to do, the computational methods that you are planning to use, improve or develop, the codes, packages or libraries that you need to undertake the project, and how these will enable the research to be achieved. Justify the computational resources requested. (max 4000 ch).

Collaborators
----------------
Specify if the project involves external collaborators who do not have WAYF access to UCloud.

Additional software
-----------------------
Specify if you need support to install new software as a service on UCloud.`;

function parseIntoSections(text: string): ApplicationSection[] {
    function normalizeTitle(title: string): string {
        const words = title.split(" ");
        let builder = "";
        if (words.length > 0) builder = words[0];
        for (let i = 1; i < words.length; i++) {
            builder += " ";
            const word = words[i];
            if (word.toUpperCase() === word || word.toLowerCase() === word) {
                builder += word;
            } else {
                builder += word.toLowerCase();
            }
        }
        return builder;
    }

    const result: ApplicationSection[] = [];
    const lines = text.split("\n");
    const sectionSeparators: number[] = [];
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i];
        if (line.startsWith("---") && /-+$/.test(line)) sectionSeparators.push(i);
    }

    let titles: string[] = [];
    for (const lineIdx of sectionSeparators) {
        if (lineIdx > 0) titles.push(lines[lineIdx - 1]);
    }

    let foundDescriptionBeforeFirstTitle = false;
    const descriptions: string[] = [];
    let currentStartLine = 0;
    for (let i = 0; i <= sectionSeparators.length; i++) {
        const end = i < sectionSeparators.length ? sectionSeparators[i] - 1 : lines.length;
        let builder = "";
        for (let row = currentStartLine; row < end; row++) {
            builder += lines[row];
            builder += "\n";

        }
        builder = builder.trim();
        if (builder.length > 0) {
            if (i === 0) foundDescriptionBeforeFirstTitle = true;
            descriptions.push(builder);
        }
        currentStartLine = end + 2;
    }

    if (foundDescriptionBeforeFirstTitle) {
        if (titles.length > 0) titles = ["Introduction", ...titles];
        else titles = ["Application"];
    }

    for (let i = 0; i < titles.length; i++) {
        const section: ApplicationSection = {
            title: normalizeTitle(titles[i]),
            description: descriptions[i],
            rows: 3
        };

        const limitMatches = section.description.matchAll(/max (\d+) ch/g);
        while (true) {
            const match = limitMatches.next();
            if (match.done) break;
            section.limit = parseInt(match.value[1]);
        }

        section.rows = Math.min(15, Math.max(2, Math.floor((section.limit ?? 250) / 50)));

        if (section.title.toLowerCase().indexOf("project title") !== -1) {
            section.rows = 2;
        }

        result.push(section);
    }

    // Move large sections to the end
    for (let i = 0; i < result.length; i++) {
        const section = result[i];
        if ((section.limit ?? 0) > 1000) {
            result.splice(i, 1);
            result.push(section);
        }
    }

    return result;
}

const style = injectStyle("grant-editor", k => `
    ${k} {
        width: 1000px;
    }
    
    ${k} h4 {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    
    ${k} h3.no-margin {
        margin-top: 0;
    }
    
    ${k} h3 {
        margin-top: 50px;
    }
    
    ${k} .project-info, ${k} .select-resources, ${k} .application {
        display: grid;
        grid-template-columns: 450px 550px;
        row-gap: 30px;
    }
    
    ${k} label code {
        font-weight: normal;
    }
    
    ${k} label {
        font-weight: bold;
        user-select: none;
    }
    
    ${k} label.section {
        font-size: 120%;
    }
    
    ${k} .description {
        color: var(--gray);
        margin-right: 20px;
    }
    
    ${k} .description p:first-child {
        margin-top: 0;
    }
    
    ${k} .form-body {
        display: flex;
        flex-direction: column;
        gap: 15px;
    }
    
    ${k} .grant-giver {
        display: flex;
        flex-direction: row;
        align-items: start;
        margin-bottom: 20px;
    }
    
    ${k} .grow {
        flex-grow: 1;
    }
    
    ${k} .grant-giver label {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    
    ${k} .grant-giver .checkbox > div {
        margin: 0;
    }
    
    ${k} textarea {
        resize: vertical;
    }
    
    ${k} .application textarea {
        margin-top: 23px;
    }
    
    ${k} .submit-area {
        display: flex;
        justify-content: end;
        margin-top: 50px;
    }
`);

const FormField: React.FunctionComponent<{
    icon?: IconName;
    title: React.ReactNode;
    id: string;
    description?: React.ReactNode;
    children?: React.ReactNode;
}> = props => {
    return <>
        <div>
            <label htmlFor={props.id} className={"section"}>
                {props.icon && <Icon name={props.icon} mr={"8px"} size={30}/>}
                {props.title}
            </label>

            {props.description &&
                <div className={"description"} style={props.icon && {marginLeft: "38px"}}>
                    {props.description}
                </div>
            }
        </div>

        <div className="form-body">{props.children}</div>
    </>;
};

const GrantGiver: React.FunctionComponent<{
    projectId: string;
    title: string;
    description: string;
    checked: boolean;
    onChange: (projectId: string, checked: boolean) => void;
}> = props => {
    const checkboxId = `check-${props.projectId}`;
    const size = 30;
    const invisiblePaddingFromCheckbox = 5;
    const onChange = useCallback(() => {
        props.onChange(props.projectId, !props.checked);
    }, [props.projectId, props.onChange, props.checked]);

    return <div className={"grant-giver"}>
        <div className={"grow"}>
            <label htmlFor={checkboxId}>
                <Logo projectId={props.projectId} size={`${size}px`}/>
                {props.title}
            </label>
            <div className={"description"} style={{marginLeft: (size + 8) + "px"}}>{props.description}</div>
        </div>
        <div
            className={"checkbox"}
            style={{
                marginTop: (-invisiblePaddingFromCheckbox) + "px",
                position: "relative",
                left: invisiblePaddingFromCheckbox + "px",
            }}
        >
            <Checkbox
                id={checkboxId}
                size={size + invisiblePaddingFromCheckbox * 2}
                checked={props.checked}
                onChange={onChange}
                handleWrapperClick={onChange}
            />
        </div>
    </div>
}

const FormIds = {
    pi: "pi",
    title: "title",
    startDate: "start-date",
    endDate: "end-date"
};

const months = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October",
    "November", "December"];

interface EditorState {
    locked: boolean;

    startDate: MonthAndYear;
    durationInMonths: number;

    stateDuringCreate?: {
        creatingWorkspace: boolean;
        loadedProjects: { id: string; title: string; }[];
    };

    stateDuringEdit?: {
        id: string;
        allocatorId?: string;
        comments: {
            id: string;
            username: string;
            comment: string;
            timestamp: number;
        }[];
        sourceAllocations: {}[];
    };

    allocators: {
        id: string;
        title: string;
        description: string;
        checked: boolean;
    }[];

    application: ApplicationSection[];

    resources: Record<string, ResourceCategory[]>;
}

interface ResourceCategory {
    type: ProductType;
    name: string;
    description: string;
    unitName: string;
    floatingPoint: boolean;
    allocators: string[];
}

const defaultState: EditorState = {
    locked: false,
    allocators: [],
    resources: {},
    startDate: {month: new Date().getMonth(), year: new Date().getFullYear()},
    application: [],
    durationInMonths: 12,
};

interface MonthAndYear {
    month: number;
    year: number;
}

type EditorAction =
    { type: "GrantLoaded", grant: GrantApplication }
    | { type: "AllocatorsLoaded", allocators: EditorState["allocators"] }
    | { type: "DurationUpdated", start: MonthAndYear, durationInMonths: number }
    | { type: "AllocatorChecked", isChecked: boolean, allocatorId: string }
    | { type: "ProductsLoaded", productsByAllocator: Record<string, Product[]> }
    ;

function stateReducer(state: EditorState, action: EditorAction): EditorState {
    switch (action.type) {
        case "AllocatorsLoaded": {
            return {
                ...state,
                allocators: action.allocators,
            };
        }

        case "AllocatorChecked": {
            return {
                ...state,
                allocators: state.allocators.map(it => {
                    if (it.id === action.allocatorId) {
                        return { ...it, checked: action.isChecked };
                    } else {
                        return it;
                    }
                })
            }
        }

        case "ProductsLoaded": {
            const newResources: EditorState["resources"] = {};

            for (const [allocator, products] of Object.entries(action.productsByAllocator)) {
                for (const product of products) {
                    if (product.hiddenInGrantApplications) continue;
                    if (product.freeToUse) continue;

                    const productProvider = product.category.provider;
                    let section = newResources[productProvider];
                    if (!section) {
                        newResources[productProvider] = [];
                        section = newResources[productProvider]!;
                    }

                    let resourceCategory = section.find(it => it.name === product.category.name);
                    if (!resourceCategory) {
                        const newCategory: ResourceCategory = {
                            name: product.category.name,
                            allocators: [],
                            description: "TODO TODO TODO TODO TODO TODO TODO TODO TODO",
                            type: product.productType,
                            floatingPoint: false,
                            unitName: "? TODO TODO ?"
                        };

                        section.push(newCategory);

                        resourceCategory = newCategory;
                    }

                    if (!resourceCategory.allocators.some(it => it === allocator)) {
                        resourceCategory.allocators.push(allocator);
                    }
                }
            }

            return {
                ...state,
                resources: newResources,
            };
        }
    }
    return state;
}

type EditorEvent =
    EditorAction
    | { type: "Init", allocationId?: string }
    ;

async function fetchAll<T>(paginator: (next?: string) => Promise<PageV2<T>>): Promise<T[]> {
    const result: T[] = [];
    let next: string | undefined = undefined;
    while (true) {
        const nextPage = await paginator(next);
        result.push(...nextPage.items)
        if (nextPage.next == null) break;
        next = nextPage.next;
    }
    return result;
}

export const Editor: React.FunctionComponent = () => {
    const didCancel = useDidUnmount();
    const [state, doDispatch] = useReducer(stateReducer, defaultState);
    const dispatchEvent = useCallback(async (event: EditorEvent) => {
        function dispatch(ev: EditorAction) {
            if (didCancel.current === true) return;
            doDispatch(ev);
        }

        switch (event.type) {
            case "Init": {
                const recipientId = Client.username!;
                const recipientType = "personalWorkspace";
                fetchAll<ProjectWithTitle>(next => {
                    return callAPI(browseAffiliations({
                        recipientId,
                        recipientType,
                        next,
                        itemsPerPage: 250,
                    }))
                }).then(async (affiliations) => {
                    const allDescriptions = await Promise.all(affiliations.map(it => {
                        return callAPI<RetrieveDescriptionResponse>(retrieveDescription({ projectId: it.projectId }))
                    }));

                    const allocators: EditorState["allocators"] = [];
                    for (let i = 0; i < affiliations.length && i < allDescriptions.length; i++) {
                        allocators.push({
                            title: affiliations[i].title,
                            id: affiliations[i].projectId,
                            description: allDescriptions[i].description,
                            checked: false
                        });
                    }

                    dispatch({ type: "AllocatorsLoaded", allocators });

                    const allProducts = await Promise.all(allocators.map(it => {
                        return callAPI<{ availableProducts: Product[] }>(browseProducts({
                            projectId: it.id,
                            recipientId,
                            recipientType,
                            showHidden: false
                        }));
                    }));

                    const productsByProvider: Record<string, Product[]> = {};
                    for (let i = 0; i < allocators.length && i < allProducts.length; i++) {
                        productsByProvider[allocators[i].id] = allProducts[i].availableProducts;
                    }

                    dispatch({ type: "ProductsLoaded", productsByAllocator: productsByProvider });
                });

                if (event.allocationId) {

                }
                break;
            }

            default: {
                dispatch(event);
                break;
            }
        }
    }, [doDispatch]);

    useEffect(() => {
        dispatchEvent({ type: "Init" });
    }, []);

    console.log(state.resources);

    const monthOptions: { value: string, text: string }[] = [];
    {
        let date = new Date();
        date.setDate(1);

        for (let i = 0; i <= 12; i++) {
            let thisMonth = date.getMonth();
            monthOptions.push({
                text: i === 0 ? `Immediately (${months[thisMonth]} ${date.getFullYear()})` : `${months[thisMonth]} ${date.getFullYear()}`,
                value: `${thisMonth}/${date.getFullYear()}`
            });

            date.setMonth((thisMonth + 1) % 12);
            if (thisMonth === 11) date.setFullYear(date.getFullYear() + 1);
        }
    }

    const sections = parseIntoSections(defaultTemplate);

    const [isCreatingNewProject, setIsCreatingNewProject] = useState(true);
    const switchToNewWorkspace = useCallback(() => setIsCreatingNewProject(true), [setIsCreatingNewProject]);
    const switchToExistingWorkspace = useCallback(() => setIsCreatingNewProject(false), [setIsCreatingNewProject]);

    const onAllocatorChecked = useCallback((projectId: string, checked: boolean) => {
        dispatchEvent({ type: "AllocatorChecked", allocatorId: projectId, isChecked: checked });
    }, [dispatchEvent]);

    console.log("Allocators:", state.allocators);

    return <MainContainer
        headerSize={0}
        main={
            <div className={style}>
                <form>
                    <h3 className={"no-margin"}>1. Information about your project</h3>
                    <div className={"project-info"}>
                        <FormField
                            id={FormIds.title}
                            title={"Project information"}
                            description={<>
                                <p>
                                    The principal investigator is the person primarily responsible for the project.
                                    This role can be changed to another member once the application has been approved.
                                </p>
                                <p>
                                    Please keep the project title specified here <i>short</i> and memorable. You will
                                    have a chance to justify your project in the "Application" section.
                                </p>
                            </>}
                        >
                            <label>
                                Principal investigator (PI)
                                <Input id={FormIds.pi} disabled
                                       value={"Dan Sebastian Thrane (DanSebastianThrane#1234)"}/>
                            </label>

                            <label>
                                {isCreatingNewProject && <>
                                    New project <a href="#" onClick={switchToExistingWorkspace}>
                                    (click here to select an existing workspace instead)
                                </a>
                                    <Input id={FormIds.title} placeholder={"Please enter the title of your project"}/>
                                </>}
                                {!isCreatingNewProject && <>
                                    Existing workspace <a href="#" onClick={switchToNewWorkspace}>
                                    (click here to create a new project instead)
                                </a>
                                    <Select>
                                        <option>My workspace</option>
                                        <option>Project 1</option>
                                        <option>Project 2</option>
                                        <option>Project 3</option>
                                        <option>Project 4</option>
                                        <option>Project 5</option>
                                        <option>Project 6</option>
                                        <option>Project 7</option>
                                        <option>Project 8</option>
                                        <option>Project 9</option>
                                        <option>Project 10</option>
                                        <option>Project 11</option>
                                    </Select>
                                </>}
                            </label>
                        </FormField>

                        <FormField
                            id={FormIds.startDate}
                            title={"Allocation duration"}
                            description={"lorem ipsum dolar sit amet"}
                        >
                            <label>
                                When should the allocation start?
                                <Select>
                                    {monthOptions.map(it => <option value={it.value} key={it.value}>{it.text}</option>)}
                                </Select>
                            </label>
                            <label>
                                For how many months should this allocation last?
                                <Select defaultValue={"12"}>
                                    <option value="3">3 months</option>
                                    <option value="6">6 months</option>
                                    <option value="12">12 months</option>
                                    <option value="24">24 months</option>
                                </Select>
                            </label>
                        </FormField>
                    </div>

                    <h3>2. Select grant giver(s)</h3>
                    <div className={"select-grant-givers"}>
                        {state.allocators.map(it =>
                            <GrantGiver
                                projectId={it.id}
                                title={it.title}
                                description={it.description}
                                key={it.id}
                                checked={it.checked}
                                onChange={onAllocatorChecked}
                            />
                        )}
                    </div>

                    <h3>3. Select resources</h3>
                    <h4><ProviderLogo providerId={"hippo"} size={30}/> <ProviderTitle providerId={"hippo"}/></h4>
                    <div className={"select-resources"}>
                        <FormField
                            title={<code>hippo-hm1</code>}
                            id={"hippo-compute"}
                            description={"Hippo HM1 machines are high memory machines. Each machine has 2x AMD EPYC 7742 64-Core @ 2.25Ghz and 4TB of memory."}
                            icon={"heroCpuChip"}
                        >
                            <label>
                                Core hours requested
                                <Input id={"hippo-compute"} type={"number"} placeholder={"0"}/>
                            </label>
                        </FormField>

                        <FormField
                            title={<code>hippo-hm2</code>}
                            id={"hippo-compute2"}
                            description={"Hippo HM2 machines are high memory machines. Each machine has 2x AMD EPYC 7713 64-Core @ 2.0Ghz and 1TB of memory."}
                            icon={"heroCpuChip"}
                        >
                            <label>
                                Core hours requested
                                <Input id={"hippo-compute2"} type={"number"} placeholder={"0"}/>
                            </label>
                        </FormField>

                        <FormField
                            title={<code>hippo-ess</code>}
                            id={"hippo-storage"}
                            description={"Hippo storage uses an IBM Elastic Storage System (ESS). If you applying for Hippo based compute then you must also apply for storage."}
                            icon={"heroCircleStack"}
                        >
                            <label>
                                GB requested
                                <Input id={"hippo-storage"} type={"number"} placeholder={"0"}/>
                            </label>
                        </FormField>
                    </div>

                    <h3>4. Application</h3>
                    <div className="application">
                        {sections.map((val, idx) => {
                            return <FormField title={val.title} key={idx} id={`app-${idx}`}
                                              description={val.description}>
                                <TextArea id={`app-${idx}`} rows={val.rows} maxLength={val.limit}/>
                            </FormField>
                        })}
                    </div>

                    <div className={"submit-area"}>
                        <Button type={"submit"}>Submit application</Button>
                    </div>
                </form>
            </div>
        }
    />;
};

export default Editor;
