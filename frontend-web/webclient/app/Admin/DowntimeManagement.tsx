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
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Page} from "Types";
import {Box, Button, Flex, Icon, Input, InputGroup, List, TextArea, Link} from "ui-components";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {Spacer} from "ui-components/Spacer";
import {addStandardDialog} from "UtilityComponents";
import {displayErrorMessageOrDefault, stopPropagationAndPreventDefault} from "UtilityFunctions";

export interface Downtime {
    id: number;
    start: number;
    end: number;
    text: string;
}

const DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

function DowntimeManagement(props: {setActivePage: () => void}) {
    const [start, setStart] = React.useState<Date | null>(null);
    const [end, setEnd] = React.useState<Date | null>(null);
    const [loading, setLoading] = React.useState(false);
    const [downtimes, setDowntimes] = React.useState<Page<Downtime>>(emptyPage);
    const textRef = React.useRef<HTMLTextAreaElement>(null);
    const promises = usePromiseKeeper();
    React.useEffect(() => {
        props.setActivePage();
        fetchDowntimes(0, 25);
    }, []);

    if (!Client.userIsAdmin) return null;

    return (
        <MainContainer
            header={<Heading.h2>Downtimes</Heading.h2>}
            main={(
                <Flex justifyContent="center">
                    <Box>
                        <form onSubmit={submit}>
                            <Flex mx="6px">
                                <InputGroup>
                                    <DatePicker
                                        placeholderText="From"
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
                                        placeholderText="To"
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
                            <TextArea
                                width={1}
                                ref={textRef}
                                rows={5}
                                required
                                style={{
                                    marginTop: "5px",
                                    marginLeft: "4px"
                                }}
                            />
                        </form>
                        <Spacer
                            left={<div />}
                            right={(
                                <Pagination.EntriesPerPageSelector
                                    entriesPerPage={downtimes.itemsPerPage}
                                    onChange={itemsPerPage => fetchDowntimes(downtimes.pageNumber, itemsPerPage)}
                                />
                            )}
                        />
                        <Pagination.List
                            loading={loading}
                            customEmptyPage={<Heading.h3>No downtimes found.</Heading.h3>}
                            onPageChanged={pageNumber => fetchDowntimes(pageNumber, downtimes.itemsPerPage)}
                            page={downtimes}
                            pageRenderer={pageRenderer}
                        />
                    </Box>
                </Flex>
            )}

            sidebar={(
                <Button
                    width={1}
                    onClick={removeAllExpired}
                    color="red"
                    disabled={downtimes.itemsInTotal > 0}
                >
                    Clear expired downtimes
                </Button>
            )}
        />
    );

    function pageRenderer(page: Page<Downtime>) {
        const now = new Date().getTime();
        const inProgress = page.items.filter(it => it.start < now && now < it.end);
        const upcoming = page.items.filter(it => it.start > now); // FIXME Won't ever be shown, I guess
        const expired = page.items.filter(it => it.end < now);

        if (inProgress.length + upcoming.length + expired.length !== page.items.length)
            throw Error("Sizes of inProgress, upcoming and expired don't match page size.");

        return (
            <Box width="420px" mt="10px">
                <DowntimeList downtimes={inProgress} name="In progress" remove={remove} />
                <DowntimeList downtimes={upcoming} name="Upcoming" remove={remove} />
                <DowntimeList downtimes={expired} name="Expired" remove={remove} />
            </Box>
        );
    }

    async function submit(e: React.FormEvent<HTMLFormElement>) {
        stopPropagationAndPreventDefault(e);
        const text = textRef.current?.value;
        if (start == null) {
            snackbarStore.addFailure("Please add a starting time and date.");
            return;
        } else if (end == null) {
            snackbarStore.addFailure("Please add an end time and date.");
            return;
        } else if (text == null || text === "") {
            snackbarStore.addFailure("Please fill out text field");
        }

        try {
            await promises.makeCancelable(
                Client.post("/downtime/add", {start: start.getTime(), end: end.getTime(), text})
            ).promise;
            snackbarStore.addSnack({
                type: SnackType.Success,
                message: "submitted",
                lifetime: 2_000
            });
            fetchDowntimes(downtimes.pageNumber, downtimes.itemsInTotal);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Could not add downtime.");
        }
    }

    async function fetchDowntimes(page: number, itemsPerPage: number) {
        try {
            setLoading(true);
            const result = await promises.makeCancelable(
                Client.get<Page<Downtime>>(`/downtime/listAll?itemsPerPage=${itemsPerPage}&page=${page}`)
            ).promise;
            setDowntimes(result.response);

        } catch (err) {
            displayErrorMessageOrDefault(err, "Could no fetch downtimes.");
        } finally {
            setLoading(false);
        }
    }

    function remove(id: number) {
        addStandardDialog({
            title: "Remove planned downtime?",
            message: "",
            onConfirm: async () => {
                await Client.post("downtime/remove", {id});
                fetchDowntimes(downtimes.pageNumber, downtimes.itemsInTotal);
            }
        });
    }

    function removeAllExpired() {
        addStandardDialog({
            title: "Remove expired downtimes?",
            message: "Do you want to remove all expired downtimes?",
            onConfirm: async () => {
                try {
                    await promises.makeCancelable(Client.post("downtime/removeExpired")).promise;
                    fetchDowntimes(0, downtimes.itemsPerPage);
                } catch (err) {
                    displayErrorMessageOrDefault(err, "Could not add downtime.");
                }
            }
        });
    }
}

export function DowntimeList(props: {downtimes: Downtime[], name: string, remove?: (id: number) => void}) {
    if (props.downtimes.length === 0) return null;
    return (
        <>
            {props.name}
            <List bordered={false}>
                {props.downtimes.map(it => (<SingleDowntime key={it.id} downtime={it} remove={props.remove} />))}
            </List>
        </>
    );
}

function SingleDowntime(props: {downtime: Downtime, remove?: (id: number) => void}) {
    return (
        <Flex key={props.downtime.id}>
            <Input my="6px" mx="6px" readOnly width="50%" value={format(props.downtime.start, DATE_FORMAT)} />
            <Input my="6px" mx="6px" readOnly width="50%" value={format(props.downtime.end, DATE_FORMAT)} />
            {props.remove ? (
                <Icon
                    mt="16px"
                    ml="5px"
                    cursor="pointer"
                    name="close"
                    color="red"
                    onClick={onRemove}
                />
            ) : null}
        </Flex>
    );

    function onRemove() {
        props.remove?.(props.downtime.id);
    }
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(DowntimeManagement);
