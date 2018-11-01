import * as React from "react";
import * as ReactDOM from "react-dom";
import { Dropdown, DropdownContent } from "./Dropdown";
import Box from "./Box";
import { Icon } from "ui-components";

type ClickableDropdownState = { open: boolean }
type ClickableDropdownProps = {
    children?: any,
    trigger: React.ReactNode,
    width?: string,
    minWidth?: string
    left?: string
    options?: { text: string, value: string }[]
    chevron?: boolean
    onChange?: (key: string) => void
}

class ClickableDropdown extends React.Component<ClickableDropdownProps, ClickableDropdownState> {
    constructor(props) {
        super(props);
        this.state = { open: false };
        document.addEventListener("click", this.handleClickOutside, true);
        let neither = true;
        if (!!props.children) neither = false;
        if (!!props.onChange && !!props.options) neither = false;
        if (neither) console.error("Clickable dropdown must have either children prop or options and onChange");
    }

    componentWillUnmount = () => document.removeEventListener("click", this.handleClickOutside, true);

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    handleClickOutside = event => {
        const domNode = ReactDOM.findDOMNode(this);
        if (!domNode || !domNode.contains(event.target)) this.setState(() => ({ open: false }));
    }

    render() {
        const { ...props } = this.props;
        let children: React.ReactNode[] = [];
        if (props.options !== undefined && props.onChange) {
            children = props.options.map((opt, i) =>
                // FIXME: onChange should have been properly checked at this point
                <Box key={i} ml="-17px" pl="15px" mr="-17px" onClick={() => { if (props.onChange) { props.onChange(opt.value) } }}>{opt.text}</Box>
            )
        } else if (props.children) {
            children = props.children
        }
        return (
            <Dropdown>
                <span onClick={() => this.setState(() => ({ open: !this.state.open }))}>
                    {this.props.trigger}{props.chevron ? <Icon name="chevronDown" /> : null}
                </span>
                {this.state.open ?
                    <DropdownContent cursor="pointer" left={props.left} minWidth={this.props.minWidth} width={this.props.width} hover={false} onClick={() => this.setState(() => ({ open: false }))}>
                        {children}
                    </DropdownContent> : null}
            </Dropdown>
        )
    }
}

export default ClickableDropdown;