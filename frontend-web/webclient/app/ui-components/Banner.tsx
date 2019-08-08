import * as React from "react"
import Box from "./Box"
import Flex from "./Flex"
import Text, { TextSpan } from "./Text"
import Icon, { IconName } from "./Icon"
import * as Heading from "./Heading"
import { TextAlign } from "./Types";
import { BoxProps } from "./Box";
import {ColorProps} from "styled-system";

const bannerColors = {
  green: {
    backgroundColor: "green",
    color: "white",
    icon: "success"
  },
  lightGreen: {
    backgroundColor: "lightGreen",
    color: "darkGreen",
    icon: "success"
  },
  red: {
    backgroundColor: "red",
    color: "white",
    icon: "warning"
  },
  lightRed: {
    backgroundColor: "lightRed",
    color: "darkRed",
    icon: "warning"
  },
  orange: {
    backgroundColor: "orange",
    color: "white",
    icon: "attention"
  },
  lightOrange: {
    backgroundColor: "lightOrange",
    color: "darkOrange",
    icon: "attention"
  },
  blue: {
    backgroundColor: "blue",
    color: "white",
    icon: "information"
  },
  lightBlue: {
    backgroundColor: "lightBlue",
    color: "darkBlue",
    icon: "information"
  }
};

const Banner = props => {
  const bannerColor = bannerColors[props.bg] || { color: "red" }
  const icon: IconName = props.iconName || "starRibbon";

  return (
    <Box
      {...props}
      bg={bannerColor.backgroundColor || props.bg}
      color={bannerColor.color || props.color}
    >
      <Flex justifyContent="space-between" alignItems="flex-start">
        {!!icon && props.showIcon && <Icon name={icon} mr={2} size={24} mt="-2px" />}
        <Box width={1}>
          <Text textAlign={props.textAlign}>
            <Heading.h5>{props.header}</Heading.h5>
            <TextSpan fontSize={1}>{props.text}</TextSpan>
            {props.children}
          </Text>
        </Box>
        {!!props.onClose && (
          <Icon
            onClick={props.onClose}
            ml={2}
            size={24}
            name="close"
            title="close"
            mt="-2px"
          />
        )}
      </Flex>
    </Box>
  )
};

Banner.displayName = "Banner";

Banner.defaultProps = {
  bg: "green",
  textAlign: "left",
  showIcon: true
};

export default Banner;
