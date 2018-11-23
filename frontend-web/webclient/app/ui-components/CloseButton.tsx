import * as React from "react";
import IconButton, { IconButtonProps } from "./IconButton";

type CloseButtonProps = Pick<IconButtonProps, Exclude<keyof IconButtonProps, "name">>

const CloseButton = (props: CloseButtonProps) => <IconButton {...props} name="close" />

CloseButton.defaultProps = {
  size: 24,
  title: "close"
}

CloseButton.displayName = "CloseButton";

export default CloseButton;
