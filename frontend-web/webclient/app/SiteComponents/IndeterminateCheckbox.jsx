import React from "react";
import ReactDOM from 'react-dom';

export default class IndeterminateCheckbox extends React.Component {
    constructor(props) {
        super(props);
        if (props.isIndeterminate) {
            this.state = {
                lastValue: 2,
                value: (props.defaultValue === null || props.defaultValue === undefined) ? null : props.defaultValue,
                indeterminateAllowed: true,
            };
        } else {
            this.state = {
                indeterminateAllowed: false,
            };
        }
        this.updateValue = this.updateValue.bind(this);
    }

    updateValue() {
        let checkbox = ReactDOM.findDOMNode(this);
        if (this.state.indeterminateAllowed) {
            const nextValue = (this.state.lastValue + 1) % 3;
            let value = false;
            switch (nextValue) {
                case 0: {
                    value = checkbox.checked = checkbox.indeterminate = false;
                    break;
                }
                case 1: {
                    value = checkbox.checked = true;
                    checkbox.indeterminate = false;
                    break;
                }
                case 2: {
                    checkbox.checked = false;
                    checkbox.indeterminate = true;
                    value = null;
                    break;
                }
            }
            this.setState(() => ({
                lastValue: nextValue
            }));
            this.props.onChange(this.props.parameter.name, {target: {value: value}});
        } else {
            this.props.onChange(this.props.parameter.name, {target: {value: checkbox.checked}});
        }
    }

    componentDidMount() {
        ReactDOM.findDOMNode(this).indeterminate = this.props.isIndeterminate && (this.props.defaultValue === null || this.props.defaultValue === undefined);
    }

    render() {
        return (<input onChange={() => this.updateValue()} type="checkbox"/>)
    }
}