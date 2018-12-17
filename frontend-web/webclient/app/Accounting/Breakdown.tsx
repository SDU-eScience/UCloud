import * as React from "react";
import * as API from "./api";
import Table, { TableRow, TableCell } from "ui-components/Table";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import { Text, Flex } from "ui-components";
import * as moment from "moment";
import { data as MockEvents } from "./mock/events.json";

const BreakdownItem: React.FunctionComponent<{ item: API.AccountingEvent }> = props => {
    return <TableRow style={{ verticalAlign: "top" }}>
        <TableCell>
            <Dropdown>
                <Text fontSize={1} color="text">{moment(new Date(props.item.timestamp)).fromNow()}</Text>
                <DropdownContent>
                    {moment(new Date(props.item.timestamp)).format("llll")}
                </DropdownContent>
            </Dropdown>
        </TableCell>
        <TableCell>
            <Flex>
                <Text fontSize={2}>{props.item.title}</Text>
            </Flex>
        </TableCell>
        <TableCell>
            {props.item.description}
        </TableCell>
    </TableRow>;

}

interface BreakdownProps {
    events?: API.AccountingEvent[]
}

class Breakdown extends React.Component<BreakdownProps> {
    render() {
        const events: API.AccountingEvent[] = this.props.events || MockEvents.items;
        return <Table>
            {events.map((e, idx) => <BreakdownItem item={e} key={idx} />)}
        </Table>;
    }
}

export default Breakdown;