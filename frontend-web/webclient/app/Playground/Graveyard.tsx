// Killed by eScience

// Components/concepts that were development but is no longer necessary.

// Progress circle: a circuluar progress bar that can show pending, in progress and failures all at once.
/*
function ProgressCircle({
    successes,
    failures,
    total,
    pendingColor,
    finishedColor,
    textColor,
    size
}: {successes: number; failures: number; total: number; size: number; textColor: ThemeColor; pendingColor: ThemeColor; finishedColor: ThemeColor;}): React.ReactNode {
    // inspired/reworked from https://codepen.io/mjurczyk/pen/wvBKOvP
    const successAngle = successes / total;
    const failureAngle = (failures + successes) / total;
    const radius = 61.5;
    const circumference = 2 * Math.PI * radius;
    const successDashArray = successAngle * circumference;
    const failureDashArray = failureAngle * circumference;
    const successStrokeDasharray = `${successDashArray} ${circumference - successDashArray}`;
    const failureStrokeDasharray = `${failureDashArray} ${circumference - failureDashArray}`;

    return (<svg width={size.toString()} height={size.toString()} viewBox="-17.875 -17.875 178.75 178.75" version="1.1" xmlns="http://www.w3.org/2000/svg" style={{transform: "rotate(-90deg)"}}>
        <circle r={radius} cx="71.5" cy="71.5" fill="transparent" stroke={`var(--${pendingColor})`} strokeWidth="15" strokeDasharray="386.22px" strokeDashoffset=""></circle>
        <circle r={radius} cx="71.5" cy="71.5" stroke={`var(--errorMain)`} strokeWidth="15" strokeLinecap="butt" strokeDashoffset={0} fill="transparent" strokeDasharray={failureStrokeDasharray}></circle>
        <circle r={radius} cx="71.5" cy="71.5" stroke={`var(--${finishedColor})`} strokeWidth="15" strokeLinecap="butt" strokeDashoffset={0} fill="transparent" strokeDasharray={successStrokeDasharray}></circle>
        <text x="35px" y="83px" fill={`var(--${textColor})`} fontSize="39px" fontWeight="bold" style={{transform: "rotate(90deg) translate(0%, -139px)"}}>
            {successes + failures + "/" + total}
        </text>
    </svg>)
}
*/