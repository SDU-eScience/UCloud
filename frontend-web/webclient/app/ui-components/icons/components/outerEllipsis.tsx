import * as React from "react";

const SvgOuterEllipsis = props => (
  <svg
    id="outer_ellipsis_svg__Layer_1"
    x={0}
    y={0}
    viewBox="0 0 24 24"
    xmlSpace="preserve"
    fill="currentcolor"
    {...props}
  >
    <style />
    <ellipse cx={5} cy={12} rx={3} ry={3} />
    <ellipse cx={19} cy={12} rx={3} ry={3} />
  </svg>
);

export default SvgOuterEllipsis;
