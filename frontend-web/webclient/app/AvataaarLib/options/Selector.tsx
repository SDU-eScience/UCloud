import * as PropTypes from "prop-types";
import * as React from "react";
import Option from "./Option";
import OptionContext, {OptionCtx} from "./OptionContext";

function getComponentOptionValue(component: React.ComponentClass) {
  const optionValue = (component as any).optionValue;
  if (!optionValue) {
    throw new Error(`optionValue should be provided for ${component}`);
  }
  return optionValue;
}

export interface Props {
  option: Option;
  defaultOption: React.ComponentClass | string;
}

export default function Selector(props: Props & {children: React.ReactNode}) {
  const context = React.useContext(OptionCtx);

  React.useEffect(() => {
    const {option, defaultOption} = props;
    const defaultValue = (
      typeof defaultOption === "string" ?
        defaultOption : getComponentOptionValue(defaultOption)
    );
    context.optionEnter(option.key);
    const optionState = context.getOptionState(option.key);
    updateOptionValues();
    if (optionState) {
      context.setDefaultValue(option.key, defaultValue);
    }
    return () => {
      context.optionExit(props.option.key);
    };
  }, []);

  React.useEffect(() => {
    updateOptionValues(props);
  }, [props]);

  let result: React.ReactNode | null = null;
  const {option, children} = props;
  const value = context.getValue(option.key)!;
  React.Children.forEach(children, child => {
    if (getComponentOptionValue((child as any).type) === value) {
      result = child;
    }
  });
  return result;

  function updateOptionValues(
    nextProps?: Props & {children?: React.ReactNode}
  ) {
    if (nextProps && props.children === nextProps.children) {
      return;
    }
    const {option, children} = props;
    const values = React.Children.map(
      children,
      // TODO: also validate and throw error if we don"t see optionValue
      child => getComponentOptionValue((child as any).type)
    );
    if (new Set(values).size !== values.length) {
      throw new Error("Duplicate values");
    }
    context.setOptions(option.key, values);
  }

}
