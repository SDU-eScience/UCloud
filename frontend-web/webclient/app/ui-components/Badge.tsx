import {color, space, SpaceProps} from "styled-system";
import styled, {keyframes} from "styled-components";
import theme, {ThemeColor, Theme} from "./theme";

export const colorScheme = (props: {theme: Theme, bg?: ThemeColor, color?: ThemeColor}) => {
  const badgeColors = {
    blue: {
      backgroundColor: props.theme.colors.blue,
      color: props.theme.colors.white
    },
    lightBlue: {
      backgroundColor: props.theme.colors.lightBlue,
      color: props.theme.colors.darkBlue
    },
    green: {
      backgroundColor: props.theme.colors.green,
      color: props.theme.colors.white
    },
    lightGreen: {
      backgroundColor: props.theme.colors.lightGreen,
      color: props.theme.colors.darkGreen
    },
    red: {
      backgroundColor: props.theme.colors.red,
      color: props.theme.colors.white
    },
    lightRed: {
      backgroundColor: props.theme.colors.lightRed,
      color: props.theme.colors.darkRed
    },
    orange: {
      backgroundColor: props.theme.colors.orange,
      color: props.theme.colors.text
    },
    gray: {
      backgroundColor: props.theme.colors.gray,
      color: props.theme.colors.white
    },
    lightGray: {
      backgroundColor: props.theme.colors.lightGray,
      color: props.theme.colors.text
    }
  };
  return props.bg && props.color && (badgeColors[props.bg] || badgeColors.lightGray);
};

const fadeIn = keyframes`
  from {
    opacity: 0;
  }

  to {
    opacity: 1;
  }
`;

const Badge = styled.div<SpaceProps & {color?: ThemeColor, bg?: ThemeColor}>`
  border-radius: 99999px;
  display: inline-block;
  font-size: ${props => props.theme.fontSizes[0]}px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: ${p => p.theme.letterSpacings.caps};
  ${space} ${colorScheme} ${color};
`;

Badge.displayName = "Badge";

Badge.defaultProps = {
  px: 2,
  py: 1,
  theme
};

const DevelopmentBadgeBase = styled(Badge)`
  background-color: ${p => p.theme.colors.red};
  margin: 15px 25px 14px 5px;
  color: white;
  animation: ${fadeIn} 1.5s ease 1.5s infinite alternate;
  animation-direction: alternate;
`;

export default Badge;

export {DevelopmentBadgeBase};
