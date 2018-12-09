import * as React from 'react'
import styled, { ThemeConsumer } from 'styled-components'
import { space, color, width, SpaceProps, ColorProps, WidthProps } from "styled-system"
import Text from "./Text"
import Icon from "./Icon"
import theme from "./theme"
import { FtIconProps as UFFtIconProps } from "UtilityFunctions";


const SvgFtLabel = ({hasExt, ext}) => {
  if (!hasExt) {
    return null;
  }

  const color3="red";

  return (
    <>
      <path
        d="M41.537 56H1.463A1.463 1.463 0 0 1 0 54.537V39h43v15.537c0 .808-.655 1.463-1.463 1.463z"
        fill={color3}
      // fillRule="nonzero"
      />
      <text text-anchor="middle"
        x="21.5" y="53">{ext}</text>
    </>
  )
}


const SvgFt = ({color, color2, hasExt, ext, ...props}) => (
  <svg
    viewBox="0 0 43 56"
    fillRule="evenodd"
    clipRule="evenodd"
    {...props}
  >
    <path
      d="M29 0H1.463C.655 0 0 .655 0 1.926V55c0 .345.655 1 1.463 1h40.074c.808 0 1.463-.655 1.463-1V10L29 0z"
      fill={color}
      // fillRule="nonzero"
    />
    <path
      d="M29 0l14 10-12 2-2-12z"
      fill={color2}
    />
    <SvgFtLabel hasExt={hasExt} ext={ext} />
  </svg>
);


type FtLabelProps = WidthProps;
const FtLabel = styled(Text)<FtLabelProps>`
    position: absolute;
    bottom: 1px;
    text-align:center;
    vertical-align: middle;
    ${width}
`;

const FtIconBase = ({ fileIcon, size, theme, ...props }): JSX.Element => {
  const hasExt = fileIcon.ext ? true : false;
  const ext3 = hasExt ? fileIcon.ext.substring(0, 3) : undefined;
  switch (fileIcon.type) {
    case "FAVFOLDER":
      return (<Icon name={"ftFavFolder"} size={size} color={"FtIconColor2"} color2={"lightGray"} />);
      break;
    case "TRASHFOLDER":
      return (<Icon name={"trash"} size={size} color={"red"} color2={"lightRed"} />);
      break;
    case "RESULTFOLDER":
      return (<Icon name={"ftResultsFolder"} size={size} color={"FtIconColor2"} color2={"lightGray"} />);
      break;
    case "DIRECTORY":
      return (<Icon name={"ftFolder"} size={size} color={"FtIconColor2"} />);
  }
  /* fileIcon.type should be "FILE" at this point */
  return (
    <SvgFt width={size} height={size}
      color={theme.colors["FtIconColor"]} color2={theme.colors["FtIconColor2"]}
      hasExt={hasExt} ext={ext3} {...props} />
  );
}

export interface FtIconProps extends SpaceProps, ColorProps {
  fileIcon: UFFtIconProps,
  cursor?: string
}

const FtIcon = styled(FtIconBase) <FtIconProps>`
  flex: none;
  vertical-align: middle;
  cursor: ${props => props.cursor};
  ${space} ${color};

  & text {
    color: white;
    font-size: 16px;
    text-transform: uppercase;
    font-weight: bold;
    letter-spacing: 1px;
  }

`;

FtIcon.displayName = "FtIcon"

FtIcon.defaultProps = {
  theme,
  cursor: "inherit",
  size: 24
}

export default FtIcon
