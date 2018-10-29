import * as React from "react";
import IconButton, { IconButtonProps } from "./IconButton";
import { Omit } from "react-redux";
import { IconName } from "./Icon";

const CloseButton = (props: IconButtonProps) => <IconButton {...props} name="close" />

CloseButton.defaultProps = {
  size: 24,
  title: "close"
}

CloseButton.displayName = "CloseButton";

export default CloseButton;
