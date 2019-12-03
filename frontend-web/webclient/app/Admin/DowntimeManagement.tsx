import {Client} from "Authentication/HttpClientInstance";
import {format} from "date-fns/esm";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {connect} from "react-redux";
import {Box, Flex, Icon, Input, List, InputGroup, Button} from "ui-components";
import {DatePicker} from "ui-components/DatePicker";
import * as Heading from "ui-components/Heading";

interface Downtime {
    from: number;
    to: number;
}

export function DowntimeManagement() {
    const [start, setStart] = React.useState<Date | null>(null);
    const [end, setEnd] = React.useState<Date | null>(null);
    const [downtimes, setDowntimes] = React.useState<Downtime[]>([]);
    React.useEffect(() => {
        setDowntimes([
            {from: new Date().getTime(), to: new Date().getTime() + 3_600_000},
            {from: new Date().getTime(), to: new Date().getTime() + 3_600_000},
            {from: new Date().getTime() - 3_600_000 * 5, to: new Date().getTime() - 3_600_000 * 3},
            {from: new Date().getTime() + 3_600_000 * 12, to: new Date().getTime() + 3_600_000 * 15}
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
                <Box>
                    <Flex mx="6px">
                        <InputGroup>
                            <DatePicker
                                placeholderText="From"
                                fontSize="18px"
                                value={start ? format(start, "dd/MM/yyyy HH:mm:ss") : undefined}
                                onChange={setStart}
                                selectsStart
                                showTimeSelect
                                endDate={end}
                            />
                            <DatePicker
                                placeholderText="To"
                                fontSize="18px"
                                value={end ? format(end, "dd/MM/yyyy HH:mm:ss") : undefined}
                                onChange={setEnd}
                                startDate={start}
                                showTimeSelect
                                selectsEnd
                            />
                        </InputGroup>
                        <Button ml="5px" mr="-5px" onClick={() => alert("TODO ")}>Add</Button>
                    </Flex>
                    <Box width="420px">
                        <DowntimeList downtimes={inProgress} name="In progress" remove={() => alert("TODO")} />
                        <DowntimeList downtimes={upcoming} name="Upcoming" remove={() => alert("TODO")} />
                        <DowntimeList downtimes={expired} name="Expired" remove={() => alert("TODO")} />
                    </Box>
                </Box>
            )}
        />
    );
}

function DowntimeList(props: {downtimes: Downtime[], name: string, remove: () => void}) {
    return (
        <>
            {props.name}
            <List>
                {name}
                {props.downtimes.map(it => (
                    <Flex key={it.to}>
                        <Input my="6px" mx="6px" readOnly width="50%" value={format(it.from, "HH:mm:ss dd/MM/yyyy")} />
                        <Input my="6px" mx="6px" readOnly width="50%" value={format(it.to, "HH:mm:ss dd/MM/yyyy")} />
                        <Icon mt="16px" ml="5px" cursor="pointer" name="close" color="red" onClick={props.remove} />
                    </Flex>
                ))}
            </List>
        </>
    );
}

export default connect(null)(DowntimeManagement);
