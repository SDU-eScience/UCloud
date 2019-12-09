import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {MainContainer} from "MainContainer/MainContainer";
import {setActivePage} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {connect} from "react-redux";
import {Dispatch} from "redux";
import {SnackType} from "Snackbar/Snackbars";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Box, Button, Flex, Icon, Input, InputGroup, List} from "ui-components";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";
import {SidebarPages} from "ui-components/Sidebar";
import {addStandardDialog} from "UtilityComponents";
import {stopPropagationAndPreventDefault} from "UtilityFunctions";

interface Downtime {
    id: number;
    from: number;
    to: number;
}

const DATE_FORMAT = "dd/MM/yyyy HH:mm:ss";

function DowntimeManagement(props: {setActivePage: () => void}) {
    const [start, setStart] = React.useState<Date | null>(null);
    const [end, setEnd] = React.useState<Date | null>(null);
    const [downtimes, setDowntimes] = React.useState<Downtime[]>([]);
    React.useEffect(() => {
        props.setActivePage();
        setDowntimes([
            {id: 1, from: new Date().getTime(), to: new Date().getTime() + 3_600_000},
            {id: 2, from: new Date().getTime(), to: new Date().getTime() + 3_600_000},
            {id: 3, from: new Date().getTime() - 3_600_000 * 5, to: new Date().getTime() - 3_600_000 * 3},
            {id: 4, from: new Date().getTime() + 3_600_000 * 12, to: new Date().getTime() + 3_600_000 * 15}
        ]);
    }, []);

    if (!Client.userIsAdmin) return null;

    const now = new Date().getTime();
    const inProgress = downtimes.filter(it => it.from < now && now < it.to);
    const upcoming = downtimes.filter(it => it.from > now);
    const expired = downtimes.filter(it => it.to < now);

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
                                        selectsStart
                                        showTimeSelect
                                        endDate={end}
                                    />
                                    <DatePicker
                                        placeholderText="To"
                                        fontSize="18px"
                                        value={end ? format(end, DATE_FORMAT) : undefined}
                                        onChange={setEnd}
                                        startDate={start}
                                        showTimeSelect
                                        selectsEnd
                                    />
                                </InputGroup>
                                <Button ml="5px" mr="-5px">Add</Button>
                            </Flex>
                        </form>
                        <Box width="420px" mt="40px">
                            <DowntimeList downtimes={inProgress} name="In progress" remove={remove} />
                            <DowntimeList downtimes={upcoming} name="Upcoming" remove={remove} />
                            <DowntimeList downtimes={expired} name="Expired" remove={remove} />
                        </Box>
                    </Box>
                </Flex>
            )}
        />
    );

    function submit(e: React.FormEvent<HTMLFormElement>) {
        stopPropagationAndPreventDefault(e);
        if (start == null) {
            snackbarStore.addFailure("Please add a starting time and date.");
            return;
        } else if (end == null) {
            snackbarStore.addFailure("Please add an end time and date.");
            return;
        }

        snackbarStore.addSnack({
            type: SnackType.Success,
            message: "\"submitted\"",
            lifetime: 2_000
        });
    }

    function remove(id: number) {
        addStandardDialog({
            title: "Remove planned downtime?",
            message: "",
            onConfirm: () => {
                setDowntimes(downtimes.filter(it => it.id !== id));
            }
        });
    }
}

function DowntimeList(props: {downtimes: Downtime[], name: string, remove: (id: number) => void}) {
    return (
        <>
            {props.name}
            <List bordered={false}>
                {props.downtimes.map(it => (<Downtime key={it.id} downtime={it} remove={props.remove} />))}
            </List>
        </>
    );
}

function Downtime(props: {downtime: Downtime, remove: (id: number) => void}) {
    return (
        <Flex key={props.downtime.id}>
            <Input my="6px" mx="6px" readOnly width="50%" value={format(props.downtime.from, DATE_FORMAT)} />
            <Input my="6px" mx="6px" readOnly width="50%" value={format(props.downtime.to, DATE_FORMAT)} />
            <Icon
                mt="16px"
                ml="5px"
                cursor="pointer"
                name="close"
                color="red"
                onClick={onRemove}
            />
        </Flex>
    );

    function onRemove() {
        props.remove(props.downtime.id);
    }
}

const mapDispatchToProps = (dispatch: Dispatch) => ({
    setActivePage: () => dispatch(setActivePage(SidebarPages.Admin)),
});

export default connect(null, mapDispatchToProps)(DowntimeManagement);
