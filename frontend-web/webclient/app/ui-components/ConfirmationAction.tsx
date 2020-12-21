import styled from "styled-components";
import * as React from "react";
import {Button} from "ui-components/index";
import {useCallback, useLayoutEffect, useRef} from "react";

const Wrapper = styled.div`
  --progress-border: #2B3044;
  --progress-active: #F6F8FF;
  --progress-success: #16BF78;
  --color: #F6F8FF;
  --background: #1A1E32;
  --tick-stroke: var(--progress-active);
  --shadow: #{rgba(#00093D, .2)};
  --shadow-active: #{rgba(#00093D, .32)};
  font-size: 16px;
  font-weight: 500;
  line-height: 19px;
  padding: 12px 32px;
  border: 0;
  border-radius: 24px;
  outline: none;
  user-select: none;
  cursor: pointer;
  backface-visibility: hidden;
  -webkit-appearance: none;
  -webkit-tap-highlight-color: transparent;
  transition: transform .3s, box-shadow .3s;
  box-shadow: 0 var(--shadow-y, 4px) var(--shadow-blur, 12px) var(--shadow);
  transform: scale(var(--scale, 1));

  & > div {
    border-radius: 50%;
    top: 12px;
    left: 12px;
    position: absolute;
    background: var(--progress-border);
    transition: transform .3s, opacity .2s;
    opacity: var(--icon-o, 0);
    transform: translateX(var(--icon-x, -4px));
  }

  & > div:before {
    content: '';
    width: 16px;
    height: 16px;
    left: 2px;
    top: 2px;
    z-index: 1;
    position: absolute;
    background: var(--background);
    border-radius: inherit;
    transform: scale(var(--background-scale, 1));
    transition: transform .32s ease;
  }

  svg {
    display: block;
    fill: none;
    width: 20px;
    height: 20px;
  }

  svg.progress {
    transform: rotate(-90deg) scale(var(--progress-scale, 1));
    transition: transform .5s ease;
  }

  svg.progress circle {
    stroke-dashoffset: 1;
    stroke-dasharray: var(--progress-array, 0) 52;
    stroke-width: 16;
    stroke: var(--progress-active);
    transition: stroke-dasharray var(--duration) linear;
  }

  svg.tick {
    left: 0;
    top: 0;
    position: absolute;
    stroke-width: 3;
    stroke-linecap: round;
    stroke-linejoin: round;
    stroke: var(--tick-stroke);
    transition: stroke .3s ease .7s;
  }

  svg.tick polyline {
    stroke-dasharray: 18 18 18;
    stroke-dashoffset: var(--tick-offset, 18);
    transition: stroke-dashoffset .4s ease .7s;
  }

  ul {
    margin: 0;
    padding: 0;
    text-align: center;
    pointer-events: none;
    list-style: none;
    min-width: 52px;
    position: relative;
    backface-visibility: hidden;
    transition: transform .3s;
    transform: translate3d(var(--ul-x, 0), 0, 0);
  }

  ul li {
    top: var(--t, 0);
    backface-visibility: hidden;
    transform: translateY(var(--ul-y)) translateZ(0);
    transition: transform .3s ease .16s, opacity .2s ease .16s;
  }

  ul li:not(:first-child) {
    --o: 0;
    position: absolute;
    left: 0;
    right: 0;
  }

  ul li:nth-child(1) {
    opacity: var(--ul-o-1, 1);
  }

  ul li:nth-child(2) {
    --t: 100%;
    opacity: var(--ul-o-2, 0);
  }

  ul li:nth-child(3) {
    --t: 200%;
    opacity: var(--ul-o-3, 0);
  }

  &:focus:not(.process), &:hover:not(.process) {
    --shadow: var(--shadow-active);
    --shadow-y: 8px;
    --shadow-blur: 16px;
  }

  &:active:not(.process) {
    --scale: .96;
    --shadow-y: 4px;
    --shadow-blur: 8px;
  }

  &.process {
    --icon-x: 0;
    --ul-y: -100%;
    --ul-o-1: 0;
    --ul-o-2: 1;
    --ul-o-3: 0;
  }

  &.process,
  &.success {
    --ul-x: 8px;
    --icon-o: 1;
    --progress-array: 52;
  }

  &.success {
    --icon-x: 6px;
    --progress-border: none;
    --progress-scale: .11;
    --tick-stroke: var(--progress-success);
    --background-scale: 0;
    --tick-offset: 36;
    --ul-y: -200%;
    --ul-o-1: 0;
    --ul-o-2: 0;
    --ul-o-3: 1;
  }

  &.success > div svg.progress {
    animation: tick .3s linear forwards .4s;
  }

  @keyframes tick {
    100% {
      transform: rotate(-90deg) translate(0, -5px) scale(var(--progress-scale));
    }
  }
`;

export const ConfirmationButton: React.FunctionComponent = () => {
    const buttonRef = useRef<HTMLDivElement>(null);
    const button = buttonRef.current;
    const timeout = useRef(-1);

    const success = useCallback(() => {
        if (!button) return;
        button.classList.add("success");
    }, [button]);

    const start = useCallback(() => {
        if (!button) return;
        if (button.classList.contains("process")) return;

        button.classList.add("process")
        timeout.current = setTimeout(success, 1600);
    }, [button]);

    const end = useCallback(() => {
        if (!button) return;
        button.classList.remove("process");
        if (timeout.current !== -1) {
            clearTimeout(timeout.current);
            timeout.current = -1;
        }
    }, [button, timeout]);

    useLayoutEffect(() => {
        if (!button) return;

        button.style.setProperty("--duration", "1600ms");
    }, [button]);

    return <Button>
        <Wrapper onMouseDown={start} onTouchStart={start} onMouseUp={end} onTouchEnd={end} ref={buttonRef}>
            <div>
                <svg className="progress" viewBox="0 0 32 32">
                    <circle r="8" cx="16" cy="16"/>
                </svg>
                <svg className="tick" viewBox="0 0 24 24">
                    <polyline points="18,7 11,16 6,12"/>
                </svg>
            </div>
            <ul>
                <li>Publish</li>
                <li>Sure ?</li>
                <li>Public</li>
            </ul>
        </Wrapper>
    </Button>
};