import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage} from "Navigation/Redux/StatusActions";
import * as Pagination from "Pagination";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Page} from "Types";
import {Box, Button, Flex, Icon, Input, InputGroup, List, TextArea, Link, Text} from "ui-components";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {displayErrorMessageOrDefault, stopPropagationAndPreventDefault} from "UtilityFunctions";
import {NewsPost} from "Dashboard/Dashboard";
import {buildQueryString} from "Utilities/URIUtilities";

const DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

function NewsManagement(props: {setActivePage: () => void}): JSX.Element | null {
    const [start, setStart] = React.useState<Date | null>(null);
    const [end, setEnd] = React.useState<Date | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [news, setNews] = React.useState<Page<NewsPost>>(emptyPage);
    const titleRef = React.useRef<HTMLInputElement>(null);
    const subtitleRef = React.useRef<HTMLInputElement>(null);
    const bodyRef = React.useRef<HTMLTextAreaElement>(null);
    const categoryRef = React.useRef<HTMLInputElement>(null);
    const promises = usePromiseKeeper();
    React.useEffect(() => {
        props.setActivePage();
        fetchNewsPost(0, 25);
    }, []);

    if (!Client.userIsAdmin) return null;

    return (
        <MainContainer
            header={<Heading.h2>News</Heading.h2>}
            main={(
                <Flex justifyContent="center">
                    <Box>
                        <form onSubmit={submit}>
                            <Flex mx="6px">
                                <InputGroup>
                                    <DatePicker
                                        placeholderText="Show from"
                                        fontSize="18px"
                                        value={start ? format(start, DATE_FORMAT) : undefined}
                                        onChange={setStart}
                                        minDate={new Date()}
                                        selectsStart
                                        required
                                        showTimeSelect
                                        endDate={end}
                                    />
                                    <DatePicker
                                        placeholderText="Show until (Optional)"
                                        fontSize="18px"
                                        required
                                        value={end ? format(end, DATE_FORMAT) : undefined}
                                        onChange={setEnd}
                                        minDate={start == null ? new Date() : start}
                                        startDate={start}
                                        showTimeSelect
                                        selectsEnd
                                    />
                                </InputGroup>
                                <Button ml="5px" mr="-5px">Add</Button>
                            </Flex>
                            <Input width={1} my="3px" placeholder="Post title..." ref={titleRef} />
                            <Input width={1} my="3px" placeholder="Short summation..." ref={subtitleRef} />
                            <TextAreaWithMargin
                                width={1}
                                placeholder="Post body... (supports markdown)"
                                ref={bodyRef}
                                rows={5}
                                required
                            />
                            <Input width={1} my="3px" ref={categoryRef} />
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
            <Box width="420px" mt="10px">
                <NewsList news={page.items} title="Posts" toggleHidden={toggleHidden} />
            </Box>
        );
    }

    async function toggleHidden(id: number): Promise<void> {
        try {
            await promises.makeCancelable(
                Client.post("/news/toggleHidden", {id})
            ).promise;
            const hidden = !news.items.find(it => it.id === id)?.hidden ? "Now hidden" : "Now visible";
            snackbarStore.addSuccess(hidden, false);
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
        }

        try {
            await promises.makeCancelable(
                Client.put("/news/post", {showFrom: start.getTime(), hideFrom: end?.getTime(), title, subtitle, body, category})
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

export function NewsList(props: {news: NewsPost[], title: string, toggleHidden?: (id: number) => void}): JSX.Element | null {
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
            <Spacer
                left={
                    <>
                        <Input my="6px" mx="6px" readOnly width="50%" value={format(props.post.showFrom, DATE_FORMAT)} />
                        {props.post.hideFrom == null ? "No expiration" : <Input my="6px" mx="6px" readOnly width="50%" value={format(props.post.hideFrom, DATE_FORMAT)} />}
                    </>
                }
                right={props.toggleHidden ? (
                    <Icon
                        mt="16px"
                        ml="5px"
                        cursor="pointer"
                        name={props.post.hidden ? "radioEmpty" : "radioChecked"}
                        color="blue"
                        onClick={onToggleHidden}
                    />
                ) : null}
            />
        </Link>
    );

    function onToggleHidden(e: React.SyntheticEvent): void {
        stopPropagationAndPreventDefault(e);
        props.toggleHidden?.(props.post.id);
    }
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(NewsManagement);
