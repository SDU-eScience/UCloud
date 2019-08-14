import * as React from "react";

const SvgProjects = (props: any) => (
  <svg
    viewBox="0 0 25 24"
    fillRule="evenodd"
    clipRule="evenodd"
    fill="currentcolor"
    {...props}
  >
    <path
      d="M11.985 17.95a7.032 7.032 0 01-3.485.919 7.033 7.033 0 01-3.485-.918C2.287 18.783.615 21.54 0 23.97h17c-.615-2.43-2.287-5.188-5.015-6.02z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M12.945 17.287a7.616 7.616 0 002.643-3.418 7.03 7.03 0 003.398-.918c2.728.832 4.399 3.59 5.015 6.019h-8.25c-.801-.741-1.737-1.332-2.806-1.683z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
    <path
      d="M8.5 5.025a6.1 6.1 0 100 12.197 6.1 6.1 0 100-12.197z"
      fill={undefined}
      fillRule="nonzero"
    />
    <path
      d="M9.895 3.653a6.126 6.126 0 0111.73 2.47 6.123 6.123 0 01-5.604 6.101 7.6 7.6 0 00-6.126-8.571z"
      fill={props.color2 ? props.color2 : null}
      fillRule="nonzero"
    />
  </svg>
);

export default SvgProjects;
