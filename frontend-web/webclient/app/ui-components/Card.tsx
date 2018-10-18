import * as React from "react";
import styled from "styled-components";
import Box, { BoxProps } from "./Box";
import Text from "./Text";
import BackgroundImage from "./BackgroundImage";
import theme from "./theme";
import { borderRadius, BoxShadowProps, BorderProps, BorderRadiusProps, BorderColorProps, HeightProps, height } from "styled-system";
import Relative from "./Relative";
import Absolute from "./Absolute";
import { Description, ApplicationDescription } from "Applications";
import Icon, { IconProps } from "./Icon";
import { Link } from "react-router-dom";

const boxShadow = props => {
  const boxShadows = {
    sm: {
      'box-shadow': props.theme.boxShadows[0]
    },
    md: {
      'box-shadow': props.theme.boxShadows[1]
    },
    lg: {
      'box-shadow': props.theme.boxShadows[2]
    },
    xl: {
      'box-shadow': props.theme.boxShadows[3]
    }
  }
  return boxShadows[props.boxShadowSize]
}

const boxBorder = props => ({
  border: `${props.borderWidth}px solid ${
    props.theme.colors[props.borderColor]
    }`
});

export interface CardProps extends HeightProps, BoxProps, BorderColorProps, BoxShadowProps, BorderProps, BorderRadiusProps {
  borderWidth?: number | string
}

const Card = styled(Box) <CardProps>`
  ${height} ${boxShadow} ${boxBorder} ${borderRadius};
`;

Card.defaultProps = {
  borderColor: 'borderGray',
  borderRadius: 1,
  borderWidth: 1,
  theme: theme,
  height: 336.8
};

export const CardGroup = styled.div`
  display: flex;
  flex-wrap: wrap;
  & > div {
    margin: 5px 5px 5px 5px;
    height: 212px;
    width: 252px;
    flex-shrink: 0;
  }
`;

export const PlayIconBase = styled(Icon)`
  transition: ease 0.3s;

  &:hover {
    filter: saturate(5);
    transform: scale(1.5);
    transition: ease 0.3s;
  }
`;

const PlayIcon = () => (<PlayIconBase cursor={"pointer"} name="play" size={38} />);


export const ApplicationCard = ({ appDescription }: { appDescription: ApplicationDescription }) => (
  <Card height={212} width={252}>
    <Relative>
      <BackgroundImage
        height="138px"
        image="https://placekitten.com/212/138">
        <Box p={4}>
          <Absolute top="16px" left="10px">
            <Text fontSize={2} align="left" color="grey">
              {appDescription.info.name}
            </Text>
          </Absolute>
          <Absolute top={"34px"} left={"14px"}>
            <Text fontSize={"xxs-small"} align="left" color="grey">
              v {appDescription.info.version}
            </Text>
          </Absolute>
          <Absolute top={"86px"} left={"200px"}>
            <Link to={`/applications/${appDescription.info.name}/${appDescription.info.version}/`}>
              <PlayIcon />
            </Link>
          </Absolute>
        </Box>
      </BackgroundImage>
    </Relative>
    <Relative>
      <Text>
        {appDescription.description.slice(0, 100)}
      </Text>
    </Relative>
  </Card>
);

Card.displayName = "Card";

export default Card;
