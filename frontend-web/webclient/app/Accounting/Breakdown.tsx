import {formatDistanceToNow} from "date-fns/esm";
import * as React from "react";
import styled from "styled-components";
import {Flex, Text} from "ui-components";
import Table, {TableCell, TableHeader, TableHeaderCell, TableRow} from "ui-components/Table";
import * as API from "./api";
import {data as MockEvents} from "./mock/events.json";

const BreakdownItem: React.FunctionComponent<{item: API.AccountingEvent}> = props => (
    <VARow>
        <TableCell>
            <Text fontSize={1} color="text">{formatDistanceToNow(props.item.timestamp, {addSuffix: true})}</Text>
        </TableCell>
        <TableCell>
            <Flex>
                <Text fontSize={2}>{props.item.title}</Text>
            </Flex>
        </TableCell>
        <TableCell>
            {props.item.description}
        </TableCell>
    </VARow>
);

const VARow = styled(TableRow)`
    vertical-align: top;
`;

interface BreakdownProps {
    events?: API.AccountingEvent[];
}

function Breakdown(props: BreakdownProps) {
    const events: API.AccountingEvent[] = props.events || MockEvents.items;
    return (
        <Table>
            <LeftAlignedTableHeader>
                <TableRow>
                    <TableHeaderCell>Time</TableHeaderCell>
                    <TableHeaderCell>Type</TableHeaderCell>
                    <TableHeaderCell>Description</TableHeaderCell>
                </TableRow>
            </LeftAlignedTableHeader>
            <tbody>
                {events.map((e, idx) => <BreakdownItem item={e} key={idx} />)}
            </tbody>
        </Table>
    );
}

const LeftAlignedTableHeader = styled(TableHeader)`
    text-align: left;
`;

export default Breakdown;
