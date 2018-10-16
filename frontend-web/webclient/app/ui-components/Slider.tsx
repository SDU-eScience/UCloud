import styled from 'styled-components'
import { Range } from 'rc-slider'
import * as StyledSystem from "styled-system";

//{ space, color, theme as getTheme, SpaceProps, ColorProps } from 'styled-system'
import theme from './theme';

console.log(StyledSystem);

const Slider = styled<any, SliderProps>(Range)`
  position: relative;
  height: 32px;
  padding-top: 12px;
  border-radius: 9999px;
  touch-action: none;

  ${StyledSystem.space} ${StyledSystem.color} & .rc-slider-rail, & .rc-slider-track {
    height: 8px;
  }
  & .rc-slider-handle {
    width: 32px;
    height: 32px;
    margin-left: -16px;
    margin-top: -12px;
  }

  & .rc-slider-rail {
    position: absolute;
    width: 100%;
    background-color: ${StyledSystem.theme('colors.lightGray')};
    border-radius: 9999px;
  }

  & .rc-slider-track {
    position: absolute;
    left: 0;
    border-radius: 9999px;
    background-color: currentcolor;
  }

  & .rc-slider-handle {
    position: absolute;
    cursor: pointer;
    cursor: grab;
    border-radius: 9999px;
    border: solid 4px currentcolor;
    background-color: #fff;
    touch-action: pan-x;

    &:hover {
    }
    &:active {
    }
    &:focus {
      box-shadow: 0 0 0 2px ${StyledSystem.theme('colors.alphablue')};
    }
  }

  &.rc-slider-disabled {
    color: ${StyledSystem.theme('colors.borderGray')};
    .rc-slider-track {
    }
    .rc-slider-handle {
      box-shadow: none;
      cursor: default;
    }
  }
`

Slider.defaultProps = {
  allowCross: false,
  color: 'blue',
  theme
}

interface SliderProps extends StyledSystem.SpaceProps, StyledSystem.ColorProps {
  allowCross: boolean
  color: string
}

export default Slider
