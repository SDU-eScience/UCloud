import * as React from 'react'
import styled, { ThemeConsumer } from 'styled-components'
import { space, color, width, SpaceProps, ColorProps, WidthProps } from "styled-system"
import Text from "./Text"
import Icon from "./Icon"
import theme from "./theme"
import { FtIconProps as UFFtIconProps, extensionType } from "UtilityFunctions";

// Label for file type icons
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
      <text text-anchor="middle" x="21.5" y="53" fill="#fff"
        style={{ fontSize: "15px", 
                 textTransform: "uppercase",
                 fontWeight: "bold",
                 letterSpacing: "1px"
              }}
      >
        {ext}
      </text>
    </>
  )
}

// Decoration for file type icons
const SvgFtType = ({type}) => {
  switch(type) {
    case "image":
      return (
        <>
          {/* Sun */}
          <circle cx={639} cy={571} r={6} transform="translate(-629 -561)"  fill="#ffd900" />
          {/* Cloud */}
          <ellipse
            cx={646.5} cy={569} rx={3.5} ry={3}
            transform="translate(-624.5 -551.143)"
            fill="#87c7ff"
          />
          <ellipse
            cx={648.5} cy={566.429} rx={3.5} ry={2.571}
            transform="translate(-623 -551.286)"
            fill="#87c7ff"
          />
          <ellipse
            cx={650} cy={570.429} rx={3} ry={2.571}
            transform="translate(-623 -551)"
            fill="#87c7ff"
          />
          <ellipse
            cx={654} cy={568.571} rx={3} ry={2.571}
            transform="translate(-624 -551.286)"
            fill="#87c7ff"
          />
          {/* Mountain green #009908 */}
          <path d="M0 39l12-18 7.999 10.857L32 16l11 14v9H0z" fill="#3d4d65" />
        </>
      );   
    case "text":
      return (
        <>
          {/* Text Lines */}
          <path
            d="M12 13H6a1 1 0 1 1 0-2h6a1 1 0 1 1 0 2zM15 18H6a1 1 0 1 1 0-2h9a1 1 0 1 1 0 2zM19 18c-.26 0-.521-.11-.71-.29-.181-.19-.29-.44-.29-.71 0-.27.109-.52.3-.71.36-.37 1.04-.37 1.41 0 .18.19.29.45.29.71 0 .26-.11.52-.29.71-.19.18-.45.29-.71.29zM31 18h-8a1 1 0 1 1 0-2h8a1 1 0 1 1 0 2zM6 33c-.26 0-.521-.11-.71-.29-.181-.19-.29-.45-.29-.71 0-.26.109-.52.29-.71.37-.37 1.05-.37 1.42.01.18.18.29.44.29.7 0 .26-.11.52-.29.71-.19.18-.45.29-.71.29zM18 33h-8a1 1 0 1 1 0-2h8a1 1 0 1 1 0 2zM37 18h-2a1 1 0 1 1 0-2h2a1 1 0 1 1 0 2zM28 23H6a1 1 0 1 1 0-2h22a1 1 0 1 1 0 2zM37 23h-6a1 1 0 1 1 0-2h6a1 1 0 1 1 0 2zM10 28H6a1 1 0 1 1 0-2h4a1 1 0 1 1 0 2zM24 28H14a1 1 0 1 1 0-2h10a1 1 0 1 1 0 2zM37 28h-9a1 1 0 1 1 0-2h9a1 1 0 1 1 0 2z"
            fill="#3d4d65"
            fillRule="nonzero"
          />
        </>
      );
    case "archive":
      return (
        <>
          {/* Zip */}
          <path
            d="M22.5 25v-2h2v-2h-2v-2h2v-2h-2v-2h2v-2h-2v-2h2V9h-2V7h-2v2h-2v2h2v2h-2v2h2v2h-2v2h2v2h-2v2h2v2h-4v5c0 2.757 2.243 5 5 5s5-2.243 5-5v-5h-4zm2 5c0 1.654-1.346 3-3 3s-3-1.346-3-3v-3h6v3z"
            fill="#3d4d65"
            fillRule="nonzero"
          />
        </>
      );
    case "audio":
      return (
        <>
          {/* Notes */}
          <path
            d="M28.668 10.011L16.772 12.32c-.242.046-.467.208-.467.462v13.852c0 .092-.006.415-.139.675-.179.34-.49.588-.929.732-.19.064-.45.121-.755.19-1.39.312-3.716.843-3.716 2.99 0 1.793 1.293 2.601 2.406 2.74.121.017.26.04.41.04.386 0 2.077-.19 2.954-.762.634-.415 1.39-1.234 1.39-2.757V18.22c0-.22.156-.41.37-.45L27.063 16a.461.461 0 0 1 .554.45v7.552c0 .236-.011.513-.144.773-.179.34-.49.588-.935.733-.19.063-.507.12-.813.19-1.39.312-3.715.837-3.715 2.983 0 1.944 1.465 2.723 2.411 2.786.375.023.646.018 1.12-.052a6.614 6.614 0 0 0 2.105-.75c1.033-.594 1.587-1.546 1.587-2.78v-17.43c-.006-.253-.22-.513-.566-.444z"
            fill="#3d4d65"
            fillRule="nonzero"
          />
        </>
      );
    case "video":
      return (
        <>
          {/* Film */}
          <path
            d="M7 11h3.333v3H7v12h3.333v3H7v3h30v-3h-3.333v-3H37V14h-3.333v-3H37V8H7v3zm23.333 15v3H27v-3h3.333zm-6.666 0v3h-3.334v-3h3.334zM17 26v3h-3.333v-3H17zm13.333-15v3H27v-3h3.333zm-6.666 0v3h-3.334v-3h3.334zM17 11v3h-3.333v-3H17z"
            fill="#3d4d65"
            fillRule="nonzero"
          />
        </>
      );
    case "code":
      return (
        <>
          <text text-anchor="middle" x="21.5" y="27" style={{ fontSize:"24px" }} fill="#3d4d65" >{'{ }'}</text>
        </>
      );
    case "pdf":
      return (
        <>
          {/* Acrobat logo */}
          <path
            d="M34.799 25.098c-.176.109-.68.176-.998.176-1.04 0-2.313-.478-4.115-1.249.695-.05 1.324-.075 1.894-.075 1.04 0 1.341 0 2.364.26 1.014.251 1.022.78.855.888zm-18.03.16c.402-.705.813-1.45 1.232-2.247a42.055 42.055 0 0 0 2.154-4.71c.964 1.751 2.163 3.235 3.562 4.425.177.15.36.293.562.444-2.858.57-5.331 1.257-7.51 2.087zm3.336-18.35c.57 0 .897 1.43.922 2.78.025 1.341-.285 2.28-.68 2.984-.326-1.04-.477-2.665-.477-3.73 0 0-.025-2.033.235-2.033zM8.923 32.659c.327-.88 1.6-2.623 3.487-4.174.117-.092.41-.368.679-.62-1.97 3.152-3.294 4.4-4.166 4.795zm26.42-9.412c-.57-.562-1.843-.855-3.771-.88-1.308-.017-2.875.1-4.535.326-.738-.427-1.5-.888-2.104-1.45-1.61-1.509-2.95-3.596-3.789-5.892.05-.218.1-.403.143-.596 0 0 .905-5.155.662-6.898a1.864 1.864 0 0 0-.118-.495l-.075-.21c-.243-.566-.73-1.17-1.492-1.137L19.82 6h-.009c-.846 0-1.542.433-1.718 1.076-.553 2.037.017 5.072 1.048 9.003l-.268.645c-.738 1.794-1.66 3.605-2.473 5.197l-.11.21a87.25 87.25 0 0 1-2.338 4.308l-.73.386c-.05.033-1.298.687-1.592.863-2.48 1.484-4.13 3.169-4.404 4.51-.087.419-.022.964.42 1.224l.704.352c.305.15.632.226.958.226 1.77 0 3.823-2.196 6.648-7.133 3.269-1.065 6.99-1.953 10.251-2.44 2.481 1.4 5.532 2.373 7.46 2.373.344 0 .637-.034.88-.1.37-.093.68-.303.872-.596.369-.562.453-1.333.344-2.13-.025-.234-.218-.527-.42-.728z"
            fill="#3d4d65"
            fillRule="nonzero"
          />
        </>
      );
    case "binary":
      return (
        <>
          <text text-anchor="middle" x="21.5" y="17" style={{ fontSize: "14px" }} fill="#3d4d65" >{'0101'}</text>
          <text text-anchor="middle" x="21.5" y="31" style={{ fontSize: "14px" }} fill="#3d4d65" >{'1110'}</text>
        </>
      );
  }

  return null;
}

// File type icon component
const SvgFt = ({color, color2, hasExt, ext, type, ...props}) => (
  <svg
    viewBox="0 0 43 56"
    fillRule="evenodd"
    clipRule="evenodd"
    {...props}
  >
    <path
      d="M29 0H1.463C.655 0 0 .655 0 1.926v52.611C.009 55.246.655 56 1.463 56h40.074c.808 0 1.453-.709 1.463-1.463V10L29 0z"
      fill={color}
      // fillRule="nonzero"
    />
    <SvgFtType type={type} />
    <path
      d="M29 0l14 10-12 2-2-12z"
      fill={color2}
    />
    <SvgFtLabel hasExt={hasExt} ext={ext} />

  </svg>
);

//Folder type icon component
const SvgFtFolder = ({color, color2, ...props}) => (
  <svg
    viewBox="0 0 24 22"
    fillRule="evenodd"
    clipRule="evenodd"
    {...props}
  >
    <path
      d="M0 21.313c0 .378.27.687.6.687h22.8c.33 0 .6-.309.6-.687v-16.5c0-.378-.27-.688-.6-.688H10.8L7.2 0H.6C.27 0 0 .31 0 .688v20.625z"
      fill= { color ? color : "currentcolor" }
      fillRule="nonzero"
    />
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
  const ext4 = hasExt ? fileIcon.ext.substring(0, 4) : undefined;
  const type = hasExt ? extensionType(fileIcon.ext.toLocaleLowerCase()) : undefined;

  switch (fileIcon.type) {
    case "FAVFOLDER":
      return (<Icon name={"ftFavFolder"} size={size} color={"FtIconColor2"} color2={"lightGray"} />);
    case "TRASHFOLDER":
      return (<Icon name={"trash"} size={size} color={"red"} color2={"lightRed"} />);
    case "RESULTFOLDER":
      return (<Icon name={"ftResultsFolder"} size={size} color={"FtIconColor2"} color2={"lightGray"} />);
    case "DIRECTORY":
      // return (<Icon name={"ftFolder"} size={size} color={"FtIconColor2"} />);
      return (<SvgFtFolder width={size} height={size} color={theme.colors["FtIconColor2"]} color2={theme.colors["lightGray"]}/>);
  }
  /* fileIcon.type should be "FILE" at this point */
  return (
    <SvgFt width={size} height={size}
      color={theme.colors["FtIconColor"]} color2={theme.colors["FtIconColor2"]}
      hasExt={hasExt} ext={ext4} type={type} {...props} />
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
`;

FtIcon.displayName = "FtIcon"

FtIcon.defaultProps = {
  theme,
  cursor: "inherit",
  size: 24
}

export default FtIcon
