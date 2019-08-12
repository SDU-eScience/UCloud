import * as React from "react"
import styled, { keyframes, css } from "styled-components"
import Box from "./Box"
import Flex from "./Flex"
import Select from "./Select"
import Icon, { IconName } from "./Icon"
import Label from "./Label"
import Input from "./Input"
import theme from "./theme"

const Root = styled(Box)`
  & ${Box} {
    pointer-events: none;
  }
`;

const fadeIn = keyframes`
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
`;

const labelStyles = css`
  animation: ${fadeIn} 0.3s;
`;

const getFieldStyles = (showLabel: boolean) =>
  showLabel ? {
    paddingTop: "9.5px",
    paddingBottom: "8px",
    transition: "padding-top 0.1s, padding-bottom 0.1s"
  } : {
    paddingTop: "9.5px",
    paddingBottom: "9.5px",
    transition: "padding-top 0.1s, padding-bottom 0.1s"
  };

const noop = () => { };

const formElements = [Input, Select];

const isFormElement = (element) => formElements.includes(element);

class FormField extends React.Component<{
  onChange: (e: React.SyntheticEvent) => void
  label?: string
  icon?: IconName
  id?: string
  placeholder?: string
  size?: number
  alwaysShowLabel?: boolean
}> {

  private fieldRef: any;

  static defaultProps = {
    // for backwards-compatibility
    id: "d",
    onChange: noop,
    theme: theme
  };

  // for backwards-compatibility
  handleChange = onChange => e => {
    this.props.onChange(e);
    if (typeof onChange !== "function") return;
    onChange(e)
  };

  hasValue = () => {
    const { children } = this.props;
    return React.Children.toArray(children).reduce(
      (a, child: any) =>
        a || (child && isFormElement(child.type) && child.props.value),
      false
    )
  };

  render() {
    const { label, icon, children, onChange, ...props } = this.props;

    let FieldChild;
    let position = -1;
    let LabelChild;
    let BeforeIcon;
    let AfterIcon;
    let fieldId;
    let fieldPlaceholder;
    let iconAdjustment;

    React.Children.forEach(children, (child, index) => {
      if (!child) return;

      switch ((child as any).type) {
        case Label:
          LabelChild = child;
          break;
        case Input:
        case Select:
          position = index;
          FieldChild = child;
          fieldId = props.id;
          // For aria-label when Label child is not rendered
          fieldPlaceholder = props.placeholder;
          break;
        case Icon:
          if (position < 0) {
            BeforeIcon = child;
            iconAdjustment = (props.size ? props.size : 0) - 24
          } else {
            AfterIcon = child
          }
          break
      }
    });

    // Handle old version on component's api
    if (icon) {
      AfterIcon = <Icon name={icon} />
    }
    if (label) {
      LabelChild = <Label>{label}</Label>
    }
    if (!FieldChild) {
      FieldChild = <Input id={this.props.id} />
    }

    const showLabel =
      LabelChild && LabelChild.props.hidden
        ? false
        : this.props.alwaysShowLabel || (LabelChild && this.hasValue());

    return (
      <Root>
        {showLabel &&
          React.cloneElement(LabelChild, {
            pl: BeforeIcon ? 40 : 2,
            mt: '6px',
            style: labelStyles,
            htmlFor: fieldId
          })}
        <Flex alignItems="center" width={1} mt={0}>
          {BeforeIcon && (
            <Box
              mr={-4}
              ml={`${8 - iconAdjustment}px`}
              mt={showLabel ? "-12px" : "2px"}
            >
              {BeforeIcon}
            </Box>
          )}
          {React.cloneElement(FieldChild, {
            'aria-label':
              !showLabel && fieldPlaceholder ? fieldPlaceholder : null,
            mt: showLabel && -20,
            pl: BeforeIcon ? 40 : 2,
            pr: AfterIcon && 40,
            style: getFieldStyles(showLabel),
            width: 1,
            innerRef: (elem: Element) => {
              this.fieldRef = elem
            },
            // for backwards compatibility
            onChange: this.handleChange(FieldChild.props.onChange),
            ...props
          })}
          {AfterIcon && (
            <Box ml={-4} mt={showLabel ? -12 : 2}>
              {AfterIcon}
            </Box>
          )}
        </Flex>
      </Root>
    )
  }
}

export default FormField;