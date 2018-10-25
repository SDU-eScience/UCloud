import * as React from "react";
import * as ReactDOM from "react-dom";
import { Dropdown, DropdownContent } from "./Dropdown";

type ClickableDropdownState = { open: boolean }
type ClickableDropdownProps = {
    children: React.ReactNode,
    trigger: React.ReactNode,
    width?: string,
    minWidth?: string
    left?: string
}

class ClickableDropdown extends React.Component<ClickableDropdownProps, ClickableDropdownState> {
    constructor(props) {
        super(props);
        this.state = { open: false };
        document.addEventListener("click", this.handleClickOutside, true);
    }

    componentWillUnmount() {
        document.removeEventListener('click', this.handleClickOutside, true);
    }

    // https://stackoverflow.com/questions/32553158/detect-click-outside-react-component#42234988
    handleClickOutside = event => {
        const domNode = ReactDOM.findDOMNode(this);
        if (!domNode || !domNode.contains(event.target)) this.setState(() => ({ open: false }));
    }

    render() {
        const { ...props } = this.props;
        return (
            <Dropdown>
                <span onClick={() => this.setState(() => ({ open: !this.state.open }))}>{this.props.trigger}</span>
                {this.state.open ?
                    <DropdownContent left={props.left} minWidth={this.props.minWidth} width={this.props.width} hover={false} onClick={() => this.setState(() => ({ open: false }))}>
                        {this.props.children}
                    </DropdownContent> : null}
            </Dropdown>
        )
    }
}

export default ClickableDropdown;