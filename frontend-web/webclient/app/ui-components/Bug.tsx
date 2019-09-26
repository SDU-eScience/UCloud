import * as React from "react";
import * as icons from "./icons";

const randomInt = (min:number, max:number) => {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function Bug({size, theme, color2, spin, ...props}) {
  const bugs: string[] = ['bug1','bug2','bug3','bug4','bug5','bug6'];
  const [idx, setIdx] = React.useState(randomInt(0, bugs.length-1));

  React.useEffect(() => {
    const time = randomInt(30,42)*10000; //5-7min in ms
    const timer = setInterval(() => {
      setIdx(randomInt(0, bugs.length-1));
    }, time);
    return () => clearInterval(timer);
  });

  const Component = icons[bugs[idx]];

  return (
    <Component width={size} height={size} color2={color2 ? theme.colors[color2] : undefined} {...props} />
  );
}

export default Bug;