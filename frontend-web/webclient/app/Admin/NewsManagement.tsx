import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {
    Box, Button, Flex, Input, InputGroup, List, TextArea, Link, Text, Card, Markdown, SelectableText, Checkbox, Label
} from "ui-components";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {displayErrorMessageOrDefault, stopPropagationAndPreventDefault, capitalized} from "UtilityFunctions";
import {NewsPost} from "Dashboard/Dashboard";
import {buildQueryString} from "Utilities/URIUtilities";
import {useCloudAPI} from "Authentication/DataHook";
import Fuse from "fuse.js";
import {addStandardDialog} from "UtilityComponents";

const DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

function NewsManagement(): JSX.Element | null {
    const [start, setStart] = React.useState<Date | null>(null);
    const [end, setEnd] = React.useState<Date | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [news, setNews] = React.useState<Page<NewsPost>>(emptyPage);
    const titleRef = React.useRef<HTMLInputElement>(null);
    const subtitleRef = React.useRef<HTMLInputElement>(null);
    const bodyRef = React.useRef<HTMLTextAreaElement>(null);
    const categoryRef = React.useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();

    useSidebarPage(SidebarPages.Admin);
    useTitle("News Management");

    React.useEffect(() => {
        fetchNewsPost(0, 25);
    }, []);

    const [showPreview, setPreview] = React.useState(false);

    const [categories] = useCloudAPI<string[]>({
        path: "/news/listCategories"
    }, []);

    const [fuse, setFuse] = React.useState(new Fuse([] as string[], {
        shouldSort: true,
        threshold: 0.2,
        location: 0,
        distance: 100,
        minMatchCharLength: 1,
    }));

    React.useEffect(() => {
        setFuse(new Fuse(categories.data, {
            shouldSort: true,
            threshold: 0.2,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
        }));
    }, [categories.data, categories.data.length]);

    const [results, setResults] = React.useState<string[]>([]);
    const onKeyUp = React.useCallback((e) => {
        const category = e.target?.value ?? "";
        setResults(fuse.search(category).map(it => capitalized(it.item)));
    }, [categoryRef.current, categories.data, categories.data.length, fuse]);

    if (!Client.userIsAdmin) return null;

    return (
        <MainContainer
            header={<Heading.h2>News</Heading.h2>}
            main={(
                <Flex justifyContent="center">
                    <Box maxWidth="800px" width={1}>
                        <form onSubmit={submit}>
                            <Flex justifyContent="center" mx="6px">
                                <InputGroup>
                                    <DatePicker
                                        placeholderText="Show from"
                                        fontSize="18px"
                                        selected={start}
                                        onChange={d => setStart(d as Date)}
                                        dateFormat={DATE_FORMAT}
                                        minDate={new Date()}
                                        selectsStart
                                        required
                                        showTimeSelect
                                        endDate={end}
                                    />
                                    <DatePicker
                                        placeholderText="Show until (Optional)"
                                        fontSize="18px"
                                        selected={end}
                                        onChange={d => setEnd(d as Date)}
                                        dateFormat={DATE_FORMAT}
                                        startDate={start}
                                        showTimeSelect
                                        selectsEnd
                                        isClearable
                                    />
                                </InputGroup>
                            </Flex>
                            <Input width={1} my="3px" required placeholder="Post title..." ref={titleRef} />
                            <Input width={1} my="3px" required placeholder="Short summation..." ref={subtitleRef} />
                            <Flex mb="3px">
                                <SelectableText
                                    cursor="pointer"
                                    mr="5px"
                                    selected={!showPreview}
                                    onClick={() => setPreview(false)}
                                >Edit</SelectableText>
                                <SelectableText
                                    cursor="pointer"
                                    selected={showPreview}
                                    onClick={() => setPreview(true)}
                                >Preview</SelectableText>
                            </Flex>
                            <TextAreaWithMargin
                                width={1}
                                placeholder="Post body... (supports markdown)"
                                ref={bodyRef}
                                rows={5}
                                required
                                hidden={showPreview}
                            />
                            {showPreview ?
                                <Card minHeight="5px" borderRadius="6px" mt="2px" pl="5px" overflow="scroll">
                                    <Markdown unwrapDisallowed source={bodyRef.current?.value ?? ""} />
                                </Card>
                                : null}
                            <Input
                                width={1}
                                autoComplete="off"
                                onKeyUp={onKeyUp}
                                my="3px"
                                placeholder="Category"
                                required
                                ref={categoryRef}
                            />
                            <Categories categories={results} onSelect={category => {
                                categoryRef.current!.value = category;
                                setResults([]);
                            }} />
                            <Button width={1}>Post</Button>
                        </form>
                        <Spacer
                            left={<div />}
                            right={(
                                <Pagination.EntriesPerPageSelector
                                    entriesPerPage={news.itemsPerPage}
                                    onChange={itemsPerPage => fetchNewsPost(news.pageNumber, itemsPerPage)}
                                />
                            )}
                        />
                        <Pagination.List
                            loading={loading}
                            customEmptyPage={<Heading.h3>No news posted.</Heading.h3>}
                            onPageChanged={pageNumber => fetchNewsPost(pageNumber, news.itemsPerPage)}
                            page={news}
                            pageRenderer={pageRenderer}
                        />
                    </Box>
                </Flex>
            )}
        />
    );

    function pageRenderer(page: Page<NewsPost>): JSX.Element {
        return (
            <Box width={1} mt="10px">
                <NewsList news={page.items} title="Posts" toggleHidden={toggleHidden} />
            </Box>
        );
    }

    async function toggleHidden(id: number): Promise<void> {
        try {
            await promises.makeCancelable(
                Client.post("/news/toggleHidden", {id})
            ).promise;
            fetchNewsPost(news.pageNumber, news.itemsPerPage);
        } catch (e) {
            displayErrorMessageOrDefault(e, "Could not toggle post visibility.");
        }
    }

    async function submit(e: React.FormEvent<HTMLFormElement>): Promise<void> {
        stopPropagationAndPreventDefault(e);
        const title = titleRef.current?.value;
        const subtitle = subtitleRef.current?.value;
        const body = bodyRef.current?.value;
        const category = categoryRef.current?.value;
        if (start == null) {
            snackbarStore.addFailure("Please add a starting time and date.", false);
            return;
        } else if (body == null || body === "") {
            snackbarStore.addFailure("Please fill out body field", false);
            return;
        } else if (end != null && start.getTime() > end.getTime()) {
            snackbarStore.addFailure("End time cannot be before start.", false);
            return;
        } else if (category == null) {
            snackbarStore.addFailure("Please add a category.", false);
            return;
        }

        if (!categories.data.includes(category.toLocaleLowerCase())) {
            const proceed = await new Promise(resolve => addStandardDialog({
                title: "Create category?",
                message: `${category} doesn't exist, create it?`,
                onConfirm: () => resolve(true),
                onCancel: () => resolve(false)
            }));
            if (!proceed) return;
        }

        try {
            await promises.makeCancelable(
                Client.put("/news/post", {
                    title, subtitle, body, category, showFrom: start.getTime(), hideFrom: end?.getTime()
                })
            ).promise;
            snackbarStore.addSuccess("Submitted", false, 2_000);
            fetchNewsPost(0, news.itemsInTotal);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Could not add post.");
        }
    }

    async function fetchNewsPost(page: number, itemsPerPage: number): Promise<void> {
        try {
            setLoading(true);
            const {response} = await promises.makeCancelable(
                Client.get<Page<NewsPost>>(buildQueryString("/news/list", {page, itemsPerPage, withHidden: true}))
            ).promise;
            setNews(response);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Could no fetch posts.");
        } finally {
            setLoading(false);
        }
    }
}

const TextAreaWithMargin = styled(TextArea)`
    marginTop: 5px;
    marginLeft: 4px;
`;

interface NewsListProps {
    news: NewsPost[];
    title: string;
    toggleHidden?: (id: number) => void;
}

export function NewsList(props: NewsListProps): JSX.Element | null {
    if (props.news.length === 0) return null;
    return (
        <>
            {props.title}
            <List bordered>
                {props.news.map(it => (<SingleNewsPost key={it.id} post={it} toggleHidden={props.toggleHidden} />))}
            </List>
        </>
    );
}

function SingleNewsPost(props: {post: NewsPost, toggleHidden?: (id: number) => void}): JSX.Element {
    return (
        <Link to={`/news/detailed/${props.post.id}`}>
            <Heading.h4>{props.post.title}</Heading.h4>
            <Text>{props.post.subtitle}</Text>
            <Flex>
                <Input my="6px" mx="6px" width="210px" readOnly value={format(props.post.showFrom, DATE_FORMAT)} />
                {props.post.hideFrom == null ?
                    <Box style={{content: ""}} width="222px" /> :
                    <Input
                        my="6px" mx="6px" readOnly width="210px" value={format(props.post.hideFrom, DATE_FORMAT)} />}
                {props.toggleHidden ? (
                    <Flex mt="18px" ml="10px">
                        <Label onClick={onToggleHidden}>
                            Hidden <Checkbox checked={props.post.hidden} onChange={e => e} />
                        </Label>
                    </Flex>
                ) : null}
            </Flex>
        </Link>
    );

    function onToggleHidden(e: React.SyntheticEvent): void {
        stopPropagationAndPreventDefault(e);
        props.toggleHidden?.(props.post.id);
    }
}

const Categories = (props: {categories: string[], onSelect: (cat: string) => void}): JSX.Element | null => {
    if (props.categories.length === 0) return null;
    return (
        <Card p="10px" borderRadius="6px" my="3px">
            <Text fontSize={1}>Existing categories:</Text>
            {props.categories.map(it => (
                <Text
                    pl="4px"
                    cursor="pointer"
                    key={it}
                    onClick={() => props.onSelect(it)}
                >
                    {it}
                </Text>
            ))}
        </Card>
    );
};

export default NewsManagement;
