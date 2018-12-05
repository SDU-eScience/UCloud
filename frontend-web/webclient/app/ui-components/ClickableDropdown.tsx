import * as React from "react";
import { Dropdown, DropdownContent } from "./Dropdown";
import Box from "./Box";
import { Icon } from "ui-components";
import * as Text from "ui-components/Text";

type ClickableDropdownState = { open: boolean }
type ClickableDropdownProps = {
    children?: any,
    trigger: React.ReactNode,
    width?: string | number,
    minWidth?: string
    left?: string
    options?: { text: string, value: string }[]
    chevron?: boolean
    onChange?: (key: string) => void
}

class ClickableDropdown extends React.Component<ClickableDropdownProps, ClickableDropdownState> {
    private ref = React.createRef<HTMLDivElement>();;

    constructor(props) {
        super(props);
        this.state = { open: false };
        document.addEventListener("mousedown", this.handleClickOutside);
        let neither = true;
        if (!!props.children) neither = false;
        if (!!props.onChange && !!props.options) neither = false;
        if (neither) throw Error("Clickable dropdown must have either children prop or options and onChange");
        
    }

    componentWillUnmount = () => document.removeEventListener("mousedown", this.handleClickOutside);

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    handleClickOutside = event => {
        if (this.ref.current && !this.ref.current.contains(event.target)) this.setState(() => ({ open: false }));
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
            <Dropdown ref={this.ref}>
                <Text.TextSpan cursor="pointer" onClick={() => this.setState(() => ({ open: !this.state.open }))}>
                    {this.props.trigger}{props.chevron ? <Icon name="chevronDown" /> : null}
                </Text.TextSpan>
                {this.state.open ?
                    <DropdownContent cursor="pointer" left={props.left} minWidth={this.props.minWidth} width={this.props.width || "auto"} hover={false} onClick={() => this.setState(() => ({ open: false }))}>
                        {children}
                    </DropdownContent> : null}
            </Dropdown>
        )
    }
}

export default ClickableDropdown;