import * as React from "react";
import { Dropdown, DropdownContent } from "./Dropdown";
import Box from "./Box";
import { Icon } from "ui-components";
import * as Text from "ui-components/Text";
import { KeyCode } from "DefaultObjects";

type ClickableDropdownState = { open: boolean }
type ClickableDropdownProps = {
    children?: any
    trigger: React.ReactNode
    fullWidth?: boolean
    width?: string | number
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
        let neither = true;
        if (!!props.children) neither = false;
        if (!!props.onChange && !!props.options) neither = false;
        if (neither) throw Error("Clickable dropdown must have either children prop or options and onChange");
        document.addEventListener("mousedown", this.handleClickOutside);
        document.addEventListener("keypress", this.handleEscPress);
    }

    componentWillUnmount = () => {
        document.removeEventListener("mousedown", this.handleClickOutside);
        document.removeEventListener("keypress", this.handleEscPress);
    }

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    handleClickOutside = event => {
        if (this.ref.current && !this.ref.current.contains(event.target)) this.setState(() => ({ open: false }));
    }

    handleEscPress = event => {
        if (event.keyCode === KeyCode.ESC) this.setState(() => ({ open: false }));
    }

    render() {
        const { ...props } = this.props;
        let children: React.ReactNode[] = [];
        if (props.options !== undefined && props.onChange) {
            children = props.options.map((opt, i) =>
                <Box width="100%" key={i} ml="-17px" pl="15px" mr="-17px" onClick={() => props.onChange!(opt.value)}>{opt.text}</Box>
            )
        } else if (props.children) {
            children = props.children
        }
        const emptyChildren = React.Children.map(children, it => it).length === 0;
        let width = this.props.fullWidth ? "100%" : this.props.width;
        return (
            <Dropdown ref={this.ref} fullWidth={this.props.fullWidth}>
                <Text.TextSpan cursor="pointer" onClick={() => this.setState(() => ({ open: !this.state.open }))}>
                    {this.props.trigger}{props.chevron ? <Icon name="chevronDown" size=".7em" ml=".7em" /> : null}
                </Text.TextSpan>
                {this.state.open && !emptyChildren ?
                    <DropdownContent cursor="pointer" left={props.left} minWidth={this.props.minWidth} width={width} hover={false} onClick={() => this.setState(() => ({ open: false }))}>
                        {children}
                    </DropdownContent> : null}
            </Dropdown>
        )
    }
}

export default ClickableDropdown;