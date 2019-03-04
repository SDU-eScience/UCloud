import * as React from "react";

const SvgHourglass = props => (
  <svg
    viewBox="0 0 16 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M1.501 0a1.5 1.5 0 0 0-1.5 1.5v4.718A2 2 0 0 0 .647 7.69L5.332 12 .645 16.32a2 2 0 0 0-.644 1.47V22.5a1.5 1.5 0 0 0 1.5 1.5h13a1.5 1.5 0 0 0 1.5-1.5V17.79a2 2 0 0 0-.644-1.47L10.67 12l4.685-4.31a2 2 0 0 0 .646-1.472V1.5a1.5 1.5 0 0 0-1.5-1.5h-13zm11.831 17.532v4.125H2.67v-4.125L8 12.617l5.331 4.915zm-5.331-6.144L2.67 6.473V2.284h10.668v4.183l-5.337 4.921z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M2.67 6.472l10.669-.006L8 11.388 2.67 6.472zM2.67 18.533h10.662v3.125H2.67z"
      fill={props.color2 ? props.color2 : null}
    />
    <path
      d="M8 12.618l1 6.915H7l1-6.915z"
      fill={props.color2 ? props.color2 : null}
    />
  </svg>
);

export default SvgHourglass;
