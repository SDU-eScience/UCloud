import * as React from "react";
import { Box, Input } from "ui-components";
import ClickableDropdown from "./ClickableDropdown";
import { allLicenses } from "Project/licenses";
import { identifierTypes } from "DefaultObjects";

export const contentValuePairLicenses = allLicenses.map(it => ({ content: it.name, value: it.identifier }))
export const contentValuePairIdentifierTypes = identifierTypes.map(it => ({ content: it.text, value: it.value }))

type ContentValuePair = { content: string, value: string };

interface DataListProps {
    options: ContentValuePair[];
    onSelect: (value: string) => void
    placeholder: string
    width?: number | string
    clearOnSelect?: boolean
}
export class DataList extends React.PureComponent<DataListProps, { text: string }> {
    constructor(props) {
        super(props);
        this.state = {
            text: ""
        }
    }

    private readonly totalShown = 8;

    private onSelect(content: string, value: string) {
        this.props.onSelect(value);
        if (this.state.text && this.props.clearOnSelect) this.setState(() => ({ text: "" }));
        else this.setState(() => ({ text: content }))
    }

    render() {
        const lowerCasedInput = this.state.text.toLowerCase();
        const filtered = this.props.options.filter(it => it.content.toLowerCase().includes(lowerCasedInput));
        const subsetFiltered = filtered.slice(0, this.totalShown);
        const fuzzySearch = undefined; // :(
        return (
            <ClickableDropdown fullWidth trigger={
                <Input
                    placeholder={this.props.placeholder}
                    autoComplete="off"
                    type="text"
                    value={this.state.text}
                    onChange={({ target }) => this.setState(() => ({ text: target.value }))}
                />}>
                {subsetFiltered.map(({ content, value }) => (
                    <Box key={content} onClick={() => this.onSelect(content, value)} mb="0.5em">
                        {content}
                    </Box>
                ))}
                {filtered.length > this.totalShown ? <Box mb="0.5em">...</Box> : null}
            </ClickableDropdown>);
    }
}

export default DataList;