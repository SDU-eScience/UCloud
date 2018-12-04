import * as React from "react";
import { Box, Input, Divider } from "ui-components";
import { Dropdown, DropdownContent } from "./Dropdown";
import List from "./List";
import ClickableDropdown from "./ClickableDropdown";
import { allLicenses } from "Project/licenses";

export const contentValuePairLicenses = allLicenses.map(it => ({ content: it.name, value: it.identifier }))

type ContentValuePair = { content: string, value: string };

interface DataListProps {
    options: ContentValuePair[];
    onSelect: (value: string) => void
}
export class DataList extends React.Component<DataListProps> {

    private ref = React.createRef<HTMLInputElement>();
    private totalShown: 8 = 8;

    private onSelect(content: string, value: string) {
        this.props.onSelect(value);
        if (this.ref.current) this.ref.current.value = content;
    }

    render() {
        const trimmedInput = this.ref.current && this.ref.current.value || "";
        const hidden = trimmedInput.length < 2 || !!this.props.options.find(it => it.value === trimmedInput)
        const filtered = this.props.options.filter(it => it.value.includes(trimmedInput));
        const subsetFiltered = filtered.slice(0, this.totalShown);
        return (
            <ClickableDropdown width="auto" trigger={<Input autoComplete="off" type="text" ref={this.ref} />}>
                {subsetFiltered.map(({ content, value }) => (
                    <Box onClick={() => this.onSelect(content, value)} mb="0.5em">
                        {content}
                    </Box>
                ))}
                {filtered.length > this.totalShown ? <Box mb="0.5em">...</Box> : null}
            </ClickableDropdown>);
    }
}

export default DataList;