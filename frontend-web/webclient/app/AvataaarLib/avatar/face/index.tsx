import * as React from "react";
import {Eyebrows, Eyes as EyeOptions, MouthTypes} from "@/UserSettings/AvatarOptions";
import {generateId as uniqueId} from "@/UtilityFunctions";

export default function Face(props: {eyebrow: Eyebrows, eyes: EyeOptions, mouth: MouthTypes}): JSX.Element {
    return (
        <g id="Face" transform="translate(76.000000, 82.000000)" fill="#000000" >
            <Mouth optionValue={props.mouth} />
            <NoseDefault />
            <Eyes optionValue={props.eyes} />
            <Eyebrow optionValue={props.eyebrow} />
        </g>
    );
}


class NoseDefault extends React.Component {
    public static optionValue = "Default";

    public render(): JSX.Element {
        return (
            <g
                id="Nose/Default"
                transform="translate(28.000000, 40.000000)"
                fillOpacity="0.16">
                <path
                    d="M16,8 C16,12.418278 21.372583,16 28,16 L28,16 C34.627417,16 40,12.418278 40,8"
                    id="Nose"
                />
            </g>
        );
    }
}

function Mouth(props: {optionValue: MouthTypes}): JSX.Element {
    switch (props.optionValue) {
        case MouthTypes.Concerned:
            return <Concerned />;
        case MouthTypes.Default:
            return <Default />;
        case MouthTypes.Disbelief:
            return <Disbelief />;
        case MouthTypes.Eating:
            return <Eating />;
        case MouthTypes.Grimace:
            return <Grimace />;
        case MouthTypes.Sad:
            return <Sad />;
        case MouthTypes.ScreamOpen:
            return <ScreamOpen />;
        case MouthTypes.Serious:
            return <Serious />;
        case MouthTypes.Smile:
            return <Smile />;
        case MouthTypes.Tongue:
            return <Tongue />;
        case MouthTypes.Twinkle:
            return <Twinkle />;
        case MouthTypes.Vomit:
            return <Vomit />;
    }
}

class Concerned extends React.Component {
    static optionValue = 'Concerned';

    private path1 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');

    render(): JSX.Element {
        const {path1, mask1} = this;
        return (
            <g id='Mouth/Concerned' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <path
                        d='M35.117844,15.1280772 C36.1757121,24.6198025 44.2259873,32 54,32 C63.8042055,32 71.8740075,24.574136 72.8917593,15.0400546 C72.9736685,14.272746 72.1167429,13 71.042767,13 C56.1487536,13 44.7379213,13 37.0868244,13 C36.0066168,13 35.0120058,14.1784435 35.117844,15.1280772 Z'
                        id={path1}
                    />
                </defs>
                <mask id={mask1} fill='white'>
                    <use
                        xlinkHref={'#' + path1}
                        transform='translate(54.003637, 22.500000) scale(1, -1) translate(-54.003637, -22.500000) '
                    />
                </mask>
                <use
                    id='Mouth'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    fillRule='evenodd'
                    transform='translate(54.003637, 22.500000) scale(1, -1) translate(-54.003637, -22.500000) '
                    xlinkHref={'#' + path1}
                />
                <rect
                    id='Teeth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    x='39'
                    y='2'
                    width='31'
                    height='16'
                    rx='5'
                />
                <g
                    id='Tongue'
                    strokeWidth='1'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    fill='#FF4F6D'>
                    <g transform='translate(38.000000, 24.000000)'>
                        <circle id='friend?' cx='11' cy='11' r='11' />
                        <circle id='How-you-doing' cx='21' cy='11' r='11' />
                    </g>
                </g>
            </g>
        )
    }
}

class Default extends React.Component {
    static optionValue = 'Default';

    render() {
        return (
            <g
                id='Mouth/Default'
                transform='translate(2.000000, 52.000000)'
                fillOpacity='0.699999988'>
                <path
                    d='M40,15 C40,22.7319865 46.2680135,29 54,29 L54,29 C61.7319865,29 68,22.7319865 68,15'
                    id='Mouth'
                />
            </g>
        )
    }
}

class Disbelief extends React.Component {
    static optionValue = 'Disbelief';

    render() {
        return (
            <g
                id='Mouth/Disbelief'
                transform='translate(2.000000, 52.000000)'
                fillOpacity='0.699999988'
                fill='#000000'>
                <path
                    d='M40,15 C40,22.7319865 46.2680135,29 54,29 L54,29 C61.7319865,29 68,22.7319865 68,15'
                    id='Mouth'
                    transform='translate(54.000000, 22.000000) scale(1, -1) translate(-54.000000, -22.000000) '
                />
            </g>
        )
    }
}

class Eating extends React.Component {
    static optionValue = 'Eating';

    render() {
        return (
            <g id='Mouth/Eating' transform='translate(2.000000, 52.000000)'>
                <g
                    id='Om-Nom-Nom'
                    opacity='0.599999964'
                    strokeWidth='1'
                    transform='translate(28.000000, 6.000000)'
                    fillOpacity='0.599999964'
                    fill='#000000'>
                    <path
                        d='M16.1906378,10.106319 C16.0179484,4.99553347 11.7923466,0.797193688 6.29352385,0 C9.66004124,1.95870633 11.9804619,5.49520667 11.9804619,9.67694348 C11.9804619,15.344608 6.50694731,20.2451296 0.176591694,20.2451296 C0.11761218,20.2451296 0.0587475828,20.2447983 0,20.244138 L8.8963743e-11,20.244138 C1.35764479,20.7317259 2.83995964,21 4.39225962,21 C9.71395931,21 14.2131224,17.8469699 15.6863572,13.5136402 C18.1609431,15.6698775 21.8629994,17.0394229 26,17.0394229 C30.1370006,17.0394229 33.8390569,15.6698775 36.3136428,13.5136402 C37.7868776,17.8469699 42.2860407,21 47.6077404,21 C49.1600404,21 50.6423552,20.7317259 52,20.244138 L52,20.244138 C51.9412524,20.2447983 51.8823878,20.2451296 51.8234083,20.2451296 C45.4930527,20.2451296 40.0195381,15.344608 40.0195381,9.67694348 C40.0195381,5.49520667 42.3399588,1.95870633 45.7064761,0 C40.2076534,0.797193688 35.9820516,4.99553347 35.8093622,10.106319 C33.2452605,11.8422828 29.7948543,12.9056086 26,12.9056086 C22.2051457,12.9056086 18.7547395,11.8422828 16.1906378,10.106319 Z'
                        id='Delicious'
                    />
                </g>
                <circle
                    id='Redish'
                    fillOpacity='0.2'
                    fill='#FF4646'
                    cx='17'
                    cy='15'
                    r='9'
                />
                <circle
                    id='Redish'
                    fillOpacity='0.2'
                    fill='#FF4646'
                    cx='91'
                    cy='15'
                    r='9'
                />
            </g>
        )
    }
}

class Grimace extends React.Component {
    static optionValue = 'Grimace';

    private path1 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');

    render() {
        const {path1, mask1} = this;
        return (
            <g id='Mouth/Grimace' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <rect id={path1} x='24' y='9' width='60' height='22' rx='11' />
                </defs>
                <rect
                    id='Mouth'
                    fillOpacity='0.599999964'
                    fill='#000000'
                    fillRule='evenodd'
                    x='22'
                    y='7'
                    width='64'
                    height='26'
                    rx='13'
                />
                <mask id={mask1} fill='white'>
                    <use xlinkHref={'#' + path1} />
                </mask>
                <use
                    id='Mouth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    xlinkHref={'#' + path1}
                />
                <path
                    d='M71,22 L62,22 L62,34 L58,34 L58,22 L49,22 L49,34 L45,34 L45,22 L36,22 L36,34 L32,34 L32,22 L24,22 L24,18 L32,18 L32,6 L36,6 L36,18 L45,18 L45,6 L49,6 L49,18 L58,18 L58,6 L62,6 L62,18 L71,18 L71,6 L75,6 L75,18 L83.8666667,18 L83.8666667,22 L75,22 L75,34 L71,34 L71,22 Z'
                    id='Grimace-Teeth'
                    fill='#E6E6E6'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                />
            </g>
        )
    }
}

class Sad extends React.Component {
    static optionValue = 'Sad';

    render() {
        return (
            <g
                id='Mouth/Sad'
                transform='translate(2.000000, 52.000000)'
                fillOpacity='0.699999988'
                fill='#000000'>
                <path
                    d='M40.0582943,16.6539438 C40.7076459,23.6831146 46.7016363,28.3768187 54,28.3768187 C61.3416045,28.3768187 67.3633339,23.627332 67.9526838,16.5287605 C67.9840218,16.1513016 67.0772329,15.8529531 66.6289111,16.077395 C61.0902255,18.8502083 56.8805885,20.2366149 54,20.2366149 C51.1558456,20.2366149 47.0072148,18.8804569 41.5541074,16.168141 C41.0473376,15.9160792 40.0197139,16.2363147 40.0582943,16.6539438 Z'
                    id='Mouth'
                    transform='translate(54.005357, 22.188409) scale(1, -1) translate(-54.005357, -22.188409) '
                />
            </g>
        )
    }
}

class ScreamOpen extends React.Component {
    static optionValue = 'ScreamOpen';

    private path1 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');

    render() {
        const {path1, mask1} = this;
        return (
            <g id='Mouth/Scream-Open' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <path
                        d='M34.0082051,15.1361102 C35.1280248,29.123916 38.2345159,40.9925405 53.9961505,40.9999965 C69.757785,41.0074525 72.9169073,29.0566179 73.9942614,15.0063928 C74.0809675,13.8756222 73.1738581,12.9999965 72.0369872,12.9999965 C65.3505138,12.9999965 62.6703194,14.9936002 53.9894323,14.9999965 C45.3085452,15.0063928 40.7567994,12.9999965 36.0924943,12.9999965 C34.9490269,12.9999965 33.8961688,13.7366502 34.0082051,15.1361102 Z'
                        id={path1}
                    />
                </defs>
                <mask id={mask1} fill='white'>
                    <use
                        xlinkHref={'#' + path1}
                        transform='translate(54.000000, 26.999998) scale(1, -1) translate(-54.000000, -26.999998) '
                    />
                </mask>
                <use
                    id='Mouth'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    fillRule='evenodd'
                    transform='translate(54.000000, 26.999998) scale(1, -1) translate(-54.000000, -26.999998) '
                    xlinkHref={'#' + path1}
                />
                <rect
                    id='Teeth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    x='39'
                    y='2'
                    width='31'
                    height='16'
                    rx='5'
                />
                <g
                    id='Tongue'
                    strokeWidth='1'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    fill='#FF4F6D'>
                    <g transform='translate(38.000000, 32.000000)' id='Say-ahhhh'>
                        <circle cx='11' cy='11' r='11' />
                        <circle cx='21' cy='11' r='11' />
                    </g>
                </g>
            </g>
        )
    }
}

class Serious extends React.Component {
    static optionValue = 'Serious';

    render() {
        return (
            <g
                id='Mouth/Serious'
                transform='translate(2.000000, 52.000000)'
                fill='#000000'
                fillOpacity='0.699999988'>
                <rect id='Why-so-serious?' x='42' y='18' width='24' height='6' rx='3' />
            </g>
        )
    }
}

class Smile extends React.Component {
    static optionValue = 'Smile';

    private path1 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');

    render() {
        const {path1, mask1} = this;
        return (
            <g id='Mouth/Smile' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <path
                        d='M35.117844,15.1280772 C36.1757121,24.6198025 44.2259873,32 54,32 C63.8042055,32 71.8740075,24.574136 72.8917593,15.0400546 C72.9736685,14.272746 72.1167429,13 71.042767,13 C56.1487536,13 44.7379213,13 37.0868244,13 C36.0066168,13 35.0120058,14.1784435 35.117844,15.1280772 Z'
                        id={path1}
                    />
                </defs>
                <mask id={mask1} fill='white'>
                    <use xlinkHref={'#' + path1} />
                </mask>
                <use
                    id='Mouth'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    fillRule='evenodd'
                    xlinkHref={'#' + path1}
                />
                <rect
                    id='Teeth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    x='39'
                    y='2'
                    width='31'
                    height='16'
                    rx='5'
                />
                <g
                    id='Tongue'
                    strokeWidth='1'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    fill='#FF4F6D'>
                    <g transform='translate(38.000000, 24.000000)'>
                        <circle cx='11' cy='11' r='11' />
                        <circle cx='21' cy='11' r='11' />
                    </g>
                </g>
            </g>
        )
    }
}

class Tongue extends React.Component {
    static optionValue = 'Tongue';

    private path1 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');

    render() {
        const {path1, mask1} = this;
        return (
            <g id='Mouth/Tongue' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <path
                        d='M29,15.6086957 C30.410031,25.2313711 41.062182,33 54,33 C66.9681454,33 77.6461342,25.183301 79,14.7391304 C79.1012093,14.3397326 78.775269,13 76.826087,13 C56.838426,13 41.7395748,13 31.173913,13 C29.3833142,13 28.870211,14.2404669 29,15.6086957 Z'
                        id={path1}
                    />
                </defs>
                <mask id={mask1} fill='white'>
                    <use xlinkHref={'#' + path1} />
                </mask>
                <use
                    id='Mouth'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    fillRule='evenodd'
                    xlinkHref={'#' + path1}
                />
                <rect
                    id='Teeth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    x='39'
                    y='2'
                    width='31'
                    height='16'
                    rx='5'
                />
                <path
                    d='M65.9841079,23.7466656 C65.9945954,23.8296335 66,23.9141856 66,24 L66,33 C66,39.0751322 61.0751322,44 55,44 L54,44 C47.9248678,44 43,39.0751322 43,33 L43,24 L43,24 C43,23.9141856 43.0054046,23.8296335 43.0158921,23.7466656 C43.0053561,23.6651805 43,23.5829271 43,23.5 C43,21.5670034 45.9101491,20 49.5,20 C51.510438,20 53.3076958,20.4914717 54.5,21.2634601 C55.6923042,20.4914717 57.489562,20 59.5,20 C63.0898509,20 66,21.5670034 66,23.5 C66,23.5829271 65.9946439,23.6651805 65.9841079,23.7466656 Z'
                    id='Tongue'
                    fill='#FF4F6D'
                    fillRule='evenodd'
                />
            </g>
        )
    }
}

class Twinkle extends React.Component {
    static optionValue = 'Twinkle';

    render() {
        return (
            <g
                id='Mouth/Twinkle'
                transform='translate(2.000000, 52.000000)'
                fillOpacity='0.599999964'
                fillRule='nonzero'
                fill='#000000'>
                <path
                    d='M40,16 C40,21.371763 46.1581544,25 54,25 C61.8418456,25 68,21.371763 68,16 C68,14.8954305 67.050301,14 66,14 C64.7072748,14 64.1302316,14.9051755 64,16 C62.7575758,18.9378973 59.6832595,20.7163149 54,21 C48.3167405,20.7163149 45.2424242,18.9378973 44,16 C43.8697684,14.9051755 43.2927252,14 42,14 C40.949699,14 40,14.8954305 40,16 Z'
                    id='Mouth'
                />
            </g>
        )
    }
}

class Vomit extends React.Component {
    static optionValue = 'Vomit';

    private path1 = uniqueId('react-path-');
    private path2 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');
    private filter1 = uniqueId('react-filter-');

    render() {
        const {path1, path2, filter1, mask1} = this;
        return (
            <g id='Mouth/Vomit' transform='translate(2.000000, 52.000000)'>
                <defs>
                    <path
                        d='M34.0082051,12.6020819 C35.1280248,23.0929366 38.2345159,31.9944054 53.9961505,31.9999974 C69.757785,32.0055894 72.9169073,23.0424631 73.9942614,12.5047938 C74.0809675,11.6567158 73.1738581,10.9999965 72.0369872,10.9999965 C65.3505138,10.9999965 62.6703194,12.4951994 53.9894323,12.4999966 C45.3085452,12.5047938 40.7567994,10.9999965 36.0924943,10.9999965 C34.9490269,10.9999965 33.8961688,11.5524868 34.0082051,12.6020819 Z'
                        id={path1}
                    />
                    <path
                        d='M59.9170416,36 L60,36 C60,39.3137085 62.6862915,42 66,42 C69.3137085,42 72,39.3137085 72,36 L72,35 L72,31 C72,27.6862915 69.3137085,25 66,25 L66,25 L42,25 L42,25 C38.6862915,25 36,27.6862915 36,31 L36,31 L36,35 L36,38 C36,41.3137085 38.6862915,44 42,44 C45.3137085,44 48,41.3137085 48,38 L48,36 L48.0829584,36 C48.5590365,33.1622867 51.0270037,31 54,31 C56.9729963,31 59.4409635,33.1622867 59.9170416,36 Z'
                        id={path2}
                    />
                    <filter
                        x='-1.4%'
                        y='-2.6%'
                        width='102.8%'
                        height='105.3%'
                        filterUnits='objectBoundingBox'
                        id={filter1}>
                        <feOffset
                            dx='0'
                            dy='-1'
                            in='SourceAlpha'
                            result='shadowOffsetInner1'
                        />
                        <feComposite
                            in='shadowOffsetInner1'
                            in2='SourceAlpha'
                            operator='arithmetic'
                            k2='-1'
                            k3='1'
                            result='shadowInnerInner1'
                        />
                        <feColorMatrix
                            values='0 0 0 0 0   0 0 0 0 0   0 0 0 0 0  0 0 0 0.1 0'
                            type='matrix'
                            in='shadowInnerInner1'
                        />
                    </filter>
                </defs>
                <mask id={mask1} fill='white'>
                    <use
                        xlinkHref={'#' + path1}
                        transform='translate(54.000000, 21.499998) scale(1, -1) translate(-54.000000, -21.499998) '
                    />
                </mask>
                <use
                    id='Mouth'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    fillRule='evenodd'
                    transform='translate(54.000000, 21.499998) scale(1, -1) translate(-54.000000, -21.499998) '
                    xlinkHref={'#' + path1}
                />
                <rect
                    id='Teeth'
                    fill='#FFFFFF'
                    fillRule='evenodd'
                    mask={`url(#${mask1})`}
                    x='39'
                    y='0'
                    width='31'
                    height='16'
                    rx='5'
                />
                <g id='Vomit-Stuff'>
                    <use fill='#88C553' fillRule='evenodd' xlinkHref={'#' + path2} />
                    <use
                        fill='black'
                        fillOpacity='1'
                        filter={`url(#${filter1})`}
                        xlinkHref={'#' + path2}
                    />
                </g>
            </g>
        )
    }
}

function Eyes(props: {optionValue: EyeOptions}): JSX.Element {
    switch (props.optionValue) {
        case EyeOptions.Close:
            return <Close />;
        case EyeOptions.Cry:
            return <Cry />;
        case EyeOptions.Default:
            return <EyesDefault />;
        case EyeOptions.Dizzy:
            return <Dizzy />;
        case EyeOptions.EyeRoll:
            return <EyeRoll />;
        case EyeOptions.Happy:
            return <Happy />;
        case EyeOptions.Hearts:
            return <Hearts />;
        case EyeOptions.Side:
            return <Side />;
        case EyeOptions.Squint:
            return <Squint />;
        case EyeOptions.Surprised:
            return <Surprised />;
        case EyeOptions.Wink:
            return <Wink />;
        case EyeOptions.WinkWacky:
            return <WinkWacky />;
    }
}

class Close extends React.Component {
    static optionValue = 'Close';

    render() {
        return (
            <g
                id='Eyes/Closed-😌'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'>
                <path
                    d='M16.1601674,32.4473116 C18.006676,28.648508 22.1644225,26 26.9975803,26 C31.8136766,26 35.9591217,28.629842 37.8153518,32.4071242 C38.3667605,33.5291977 37.5821037,34.4474817 36.790607,33.7670228 C34.3395063,31.6597833 30.8587163,30.3437884 26.9975803,30.3437884 C23.2572061,30.3437884 19.8737584,31.5787519 17.4375392,33.5716412 C16.5467928,34.3002944 15.6201012,33.5583844 16.1601674,32.4473116 Z'
                    id='Closed-Eye'
                    transform='translate(27.000000, 30.000000) scale(1, -1) translate(-27.000000, -30.000000) '
                />
                <path
                    d='M74.1601674,32.4473116 C76.006676,28.648508 80.1644225,26 84.9975803,26 C89.8136766,26 93.9591217,28.629842 95.8153518,32.4071242 C96.3667605,33.5291977 95.5821037,34.4474817 94.790607,33.7670228 C92.3395063,31.6597833 88.8587163,30.3437884 84.9975803,30.3437884 C81.2572061,30.3437884 77.8737584,31.5787519 75.4375392,33.5716412 C74.5467928,34.3002944 73.6201012,33.5583844 74.1601674,32.4473116 Z'
                    id='Closed-Eye'
                    transform='translate(85.000000, 30.000000) scale(1, -1) translate(-85.000000, -30.000000) '
                />
            </g>
        )
    }
}

class Cry extends React.Component {
    static optionValue = 'Cry';

    render() {
        return (
            <g id='Eyes/Cry-😢' transform='translate(0.000000, 8.000000)'>
                <circle
                    id='Eye'
                    fillOpacity='0.599999964'
                    fill='#000000'
                    fillRule='evenodd'
                    cx='30'
                    cy='22'
                    r='6'
                />
                <path
                    d='M25,27 C25,27 19,34.2706667 19,38.2706667 C19,41.5846667 21.686,44.2706667 25,44.2706667 C28.314,44.2706667 31,41.5846667 31,38.2706667 C31,34.2706667 25,27 25,27 Z'
                    id='Drop'
                    fill='#92D9FF'
                    fillRule='nonzero'
                />
                <circle
                    id='Eye'
                    fillOpacity='0.599999964'
                    fill='#000000'
                    fillRule='evenodd'
                    cx='82'
                    cy='22'
                    r='6'
                />
            </g>
        )
    }
}

class EyesDefault extends React.Component {
    static optionValue = 'Default';

    render() {
        return (
            <g
                id='Eyes/Default-😀'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'>
                <circle id='Eye' cx='30' cy='22' r='6' />
                <circle id='Eye' cx='82' cy='22' r='6' />
            </g>
        )
    }
}

class Dizzy extends React.Component {
    static optionValue = 'Dizzy';

    render() {
        return (
            <g
                id='Eyes/X-Dizzy-😵'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'
                fillRule='nonzero'>
                <path
                    d='M29,25.2 L34.5,30.7 C35,31.1 35.7,31.1 36.1,30.7 L37.7,29.1 C38.1,28.6 38.1,27.9 37.7,27.5 L32.2,22 L37.7,16.5 C38.1,16 38.1,15.3 37.7,14.9 L36.1,13.3 C35.6,12.9 34.9,12.9 34.5,13.3 L29,18.8 L23.5,13.3 C23,12.9 22.3,12.9 21.9,13.3 L20.3,14.9 C19.9,15.3 19.9,16 20.3,16.5 L25.8,22 L20.3,27.5 C19.9,28 19.9,28.7 20.3,29.1 L21.9,30.7 C22.4,31.1 23.1,31.1 23.5,30.7 L29,25.2 Z'
                    id='Eye'
                />
                <path
                    d='M83,25.2 L88.5,30.7 C89,31.1 89.7,31.1 90.1,30.7 L91.7,29.1 C92.1,28.6 92.1,27.9 91.7,27.5 L86.2,22 L91.7,16.5 C92.1,16 92.1,15.3 91.7,14.9 L90.1,13.3 C89.6,12.9 88.9,12.9 88.5,13.3 L83,18.8 L77.5,13.3 C77,12.9 76.3,12.9 75.9,13.3 L74.3,14.9 C73.9,15.3 73.9,16 74.3,16.5 L79.8,22 L74.3,27.5 C73.9,28 73.9,28.7 74.3,29.1 L75.9,30.7 C76.4,31.1 77.1,31.1 77.5,30.7 L83,25.2 Z'
                    id='Eye'
                />
            </g>
        )
    }
}

class EyeRoll extends React.Component {
    static optionValue = 'EyeRoll';

    render() {
        return (
            <g id='Eyes/Eye-Roll-🙄' transform='translate(0.000000, 8.000000)'>
                <circle id='Eyeball' fill='#FFFFFF' cx='30' cy='22' r='14' />
                <circle id='The-white-stuff' fill='#FFFFFF' cx='82' cy='22' r='14' />
                <circle
                    id='Eye'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    cx='30'
                    cy='14'
                    r='6'
                />
                <circle
                    id='Eye'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    cx='82'
                    cy='14'
                    r='6'
                />
            </g>
        )
    }
}

class Happy extends React.Component {
    static optionValue = 'Happy';

    render() {
        return (
            <g
                id='Eyes/Happy-😁'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'>
                <path
                    d='M16.1601674,22.4473116 C18.006676,18.648508 22.1644225,16 26.9975803,16 C31.8136766,16 35.9591217,18.629842 37.8153518,22.4071242 C38.3667605,23.5291977 37.5821037,24.4474817 36.790607,23.7670228 C34.3395063,21.6597833 30.8587163,20.3437884 26.9975803,20.3437884 C23.2572061,20.3437884 19.8737584,21.5787519 17.4375392,23.5716412 C16.5467928,24.3002944 15.6201012,23.5583844 16.1601674,22.4473116 Z'
                    id='Squint'
                />
                <path
                    d='M74.1601674,22.4473116 C76.006676,18.648508 80.1644225,16 84.9975803,16 C89.8136766,16 93.9591217,18.629842 95.8153518,22.4071242 C96.3667605,23.5291977 95.5821037,24.4474817 94.790607,23.7670228 C92.3395063,21.6597833 88.8587163,20.3437884 84.9975803,20.3437884 C81.2572061,20.3437884 77.8737584,21.5787519 75.4375392,23.5716412 C74.5467928,24.3002944 73.6201012,23.5583844 74.1601674,22.4473116 Z'
                    id='Squint'
                />
            </g>
        )
    }
}

class Hearts extends React.Component {
    static optionValue = 'Hearts';

    render() {
        return (
            <g
                id='Eyes/Hearts-😍'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.8'
                fillRule='nonzero'
                fill='#FF5353'>
                <path
                    d='M35.9583333,10 C33.4074091,10 30.8837273,11.9797894 29.5,13.8206358 C28.1106364,11.9797894 25.5925909,10 23.0416667,10 C17.5523182,10 14,13.3341032 14,17.6412715 C14,23.3708668 18.4118636,26.771228 23.0416667,30.376724 C24.695,31.6133636 27.8223436,34.7777086 28.2083333,35.470905 C28.5943231,36.1641015 30.3143077,36.1885229 30.7916667,35.470905 C31.2690257,34.7532872 34.3021818,31.6133636 35.9583333,30.376724 C40.5853182,26.771228 45,23.3708668 45,17.6412715 C45,13.3341032 41.4476818,10 35.9583333,10 Z'
                    id='Heart'
                />
                <path
                    d='M88.9583333,10 C86.4074091,10 83.8837273,11.9797894 82.5,13.8206358 C81.1106364,11.9797894 78.5925909,10 76.0416667,10 C70.5523182,10 67,13.3341032 67,17.6412715 C67,23.3708668 71.4118636,26.771228 76.0416667,30.376724 C77.695,31.6133636 80.8223436,34.7777086 81.2083333,35.470905 C81.5943231,36.1641015 83.3143077,36.1885229 83.7916667,35.470905 C84.2690257,34.7532872 87.3021818,31.6133636 88.9583333,30.376724 C93.5853182,26.771228 98,23.3708668 98,17.6412715 C98,13.3341032 94.4476818,10 88.9583333,10 Z'
                    id='Heart'
                />
            </g>
        )
    }
}



class Side extends React.Component {
    static optionValue = 'Side';

    render() {
        return (
            <g
                id='Eyes/Side-😒'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'>
                <path
                    d='M27.2409577,20.3455337 C26.462715,21.3574913 26,22.6247092 26,24 C26,27.3137085 28.6862915,30 32,30 C35.3137085,30 38,27.3137085 38,24 C38,23.7097898 37.9793961,23.4243919 37.9395713,23.1451894 C37.9474218,22.9227843 37.9097825,22.6709538 37.8153518,22.4071242 C37.7703692,22.2814477 37.7221152,22.1572512 37.6706873,22.0345685 C37.3370199,21.0717264 36.7650456,20.2202109 36.0253277,19.550585 C33.898886,17.3173253 30.5064735,16 26.9975803,16 C22.1644225,16 18.006676,18.648508 16.1601674,22.4473116 C15.6201012,23.5583844 16.5467928,24.3002944 17.4375392,23.5716412 C19.8737584,21.5787519 23.2572061,20.3437884 26.9975803,20.3437884 C27.0788767,20.3437884 27.1600045,20.3443718 27.2409577,20.3455337 Z'
                    id='Eye'
                />
                <path
                    d='M85.2409577,20.3455337 C84.462715,21.3574913 84,22.6247092 84,24 C84,27.3137085 86.6862915,30 90,30 C93.3137085,30 96,27.3137085 96,24 C96,23.7097898 95.9793961,23.4243919 95.9395713,23.1451894 C95.9474218,22.9227843 95.9097825,22.6709538 95.8153518,22.4071242 C95.7703692,22.2814477 95.7221152,22.1572512 95.6706873,22.0345685 C95.3370199,21.0717264 94.7650456,20.2202109 94.0253277,19.550585 C91.898886,17.3173253 88.5064735,16 84.9975803,16 C80.1644225,16 76.006676,18.648508 74.1601674,22.4473116 C73.6201012,23.5583844 74.5467928,24.3002944 75.4375392,23.5716412 C77.8737584,21.5787519 81.2572061,20.3437884 84.9975803,20.3437884 C85.0788767,20.3437884 85.1600045,20.3443718 85.2409577,20.3455337 Z'
                    id='Eye'
                />
            </g>
        )
    }
}

class Squint extends React.Component {
    static optionValue = 'Squint';

    private path1 = uniqueId('react-path-');
    private path2 = uniqueId('react-path-');
    private mask1 = uniqueId('react-mask-');
    private mask2 = uniqueId('react-mask-');

    render() {
        const {path1, path2, mask1, mask2} = this;
        return (
            <g id='Eyes/Squint-😊' transform='translate(0.000000, 8.000000)'>
                <defs>
                    <path
                        d='M14,14.0481187 C23.6099827,14.0481187 28,18.4994466 28,11.5617716 C28,4.62409673 21.7319865,0 14,0 C6.2680135,0 0,4.62409673 0,11.5617716 C0,18.4994466 4.39001726,14.0481187 14,14.0481187 Z'
                        id={path1}
                    />
                    <path
                        d='M14,14.0481187 C23.6099827,14.0481187 28,18.4994466 28,11.5617716 C28,4.62409673 21.7319865,0 14,0 C6.2680135,0 0,4.62409673 0,11.5617716 C0,18.4994466 4.39001726,14.0481187 14,14.0481187 Z'
                        id={path2}
                    />
                </defs>
                <g id='Eye' transform='translate(16.000000, 13.000000)'>
                    <mask id={mask1} fill='white'>
                        <use xlinkHref={'#' + path1} />
                    </mask>
                    <use id='The-white-stuff' fill='#FFFFFF' xlinkHref={'#' + path1} />
                    <circle
                        fillOpacity='0.699999988'
                        fill='#000000'
                        mask={`url(#${mask1})`}
                        cx='14'
                        cy='10'
                        r='6'
                    />
                </g>
                <g id='Eye' transform='translate(68.000000, 13.000000)'>
                    <mask id={mask2} fill='white'>
                        <use xlinkHref={'#' + path2} />
                    </mask>
                    <use id='Eyeball-Mask' fill='#FFFFFF' xlinkHref={'#' + path2} />
                    <circle
                        fillOpacity='0.699999988'
                        fill='#000000'
                        mask={`url(#${mask2})`}
                        cx='14'
                        cy='10'
                        r='6'
                    />
                </g>
            </g>
        )
    }
}

class Surprised extends React.Component {
    static optionValue = 'Surprised';

    render() {
        return (
            <g id='Eyes/Surprised-😳' transform='translate(0.000000, 8.000000)'>
                <circle id='The-White-Stuff' fill='#FFFFFF' cx='30' cy='22' r='14' />
                <circle id='Eye-Ball' fill='#FFFFFF' cx='82' cy='22' r='14' />
                <circle
                    id='Eye'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    cx='30'
                    cy='22'
                    r='6'
                />
                <circle
                    id='Eye'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    cx='82'
                    cy='22'
                    r='6'
                />
            </g>
        )
    }
}

class Wink extends React.Component {
    static optionValue = 'Wink';

    render() {
        return (
            <g
                id='Eyes/Wink-😉'
                transform='translate(0.000000, 8.000000)'
                fillOpacity='0.599999964'>
                <circle id='Eye' cx='30' cy='22' r='6' />
                <path
                    d='M70.4123979,24.204889 C72.2589064,20.4060854 76.4166529,17.7575774 81.2498107,17.7575774 C86.065907,17.7575774 90.2113521,20.3874194 92.0675822,24.1647016 C92.618991,25.2867751 91.8343342,26.2050591 91.0428374,25.5246002 C88.5917368,23.4173607 85.1109468,22.1013658 81.2498107,22.1013658 C77.5094365,22.1013658 74.1259889,23.3363293 71.6897696,25.3292186 C70.7990233,26.0578718 69.8723316,25.3159619 70.4123979,24.204889 Z'
                    id='Winky-Wink'
                    transform='translate(81.252230, 21.757577) rotate(-4.000000) translate(-81.252230, -21.757577) '
                />
            </g>
        )
    }
}

class WinkWacky extends React.Component {
    static optionValue = 'WinkWacky';

    render() {
        return (
            <g id='Eyes/Wink-Wacky-😜' transform='translate(0.000000, 8.000000)'>
                <circle
                    id='Cornea?-I-don&#39;t-know'
                    fill='#FFFFFF'
                    cx='82'
                    cy='22'
                    r='12'
                />
                <circle
                    id='Eye'
                    fillOpacity='0.699999988'
                    fill='#000000'
                    cx='82'
                    cy='22'
                    r='6'
                />
                <path
                    d='M16.1601674,25.4473116 C18.006676,21.648508 22.1644225,19 26.9975803,19 C31.8136766,19 35.9591217,21.629842 37.8153518,25.4071242 C38.3667605,26.5291977 37.5821037,27.4474817 36.790607,26.7670228 C34.3395063,24.6597833 30.8587163,23.3437884 26.9975803,23.3437884 C23.2572061,23.3437884 19.8737584,24.5787519 17.4375392,26.5716412 C16.5467928,27.3002944 15.6201012,26.5583844 16.1601674,25.4473116 Z'
                    id='Winky-Doodle'
                    fillOpacity='0.599999964'
                    fill='#000000'
                />
            </g>
        )
    }
}

function Eyebrow(props: {optionValue: Eyebrows}): JSX.Element {
    switch (props.optionValue) {
        case Eyebrows.Angry:
            return <Angry />;
        case Eyebrows.AngryNatural:
            return <AngryNatural />;
        case Eyebrows.Default:
            return <EyebrowDefault />;
        case Eyebrows.DefaultNatural:
            return <DefaultNatural />;
        case Eyebrows.FlatNatural:
            return <FlatNatural />;
        case Eyebrows.FrownNatural:
            return <FrownNatural />
        case Eyebrows.RaisedExcited:
            return <RaisedExcited />;
        case Eyebrows.RaisedExcitedNatural:
            return <RaisedExcitedNatural />;
        case Eyebrows.SadConcerned:
            return <SadConcerned />;
        case Eyebrows.SadConcernedNatural:
            return <SadConcernedNatural />;
        case Eyebrows.UnibrowNatural:
            return <UnibrowNatural />;
        case Eyebrows.UpDown:
            return <UpDown />;
        case Eyebrows.UpDownNatural:
            return <UpDownNatural />;
    }
}

class Angry extends React.Component {
    static optionValue = 'Angry';

    render() {
        return (
            <g
                id='Eyebrow/Outline/Angry'
                fillOpacity='0.599999964'
                fillRule='nonzero'>
                <path
                    d='M15.6114904,15.1845247 C19.8515017,9.41618792 22.4892046,9.70087612 28.9238518,14.5564693 C29.1057771,14.6937504 29.2212592,14.7812575 29.5936891,15.063789 C34.4216439,18.7263562 36.7081807,20 40,20 C41.1045695,20 42,19.1045695 42,18 C42,16.8954305 41.1045695,16 40,16 C37.9337712,16 36.0986396,14.9777974 32.011227,11.8770179 C31.6358269,11.5922331 31.5189458,11.5036659 31.3332441,11.3635351 C27.5737397,8.52660822 25.3739873,7.28738405 22.6379899,6.99208688 C18.9538127,6.59445233 15.5799484,8.47367246 12.3885096,12.8154753 C11.7343147,13.7054768 11.9254737,14.9572954 12.8154753,15.6114904 C13.7054768,16.2656853 14.9572954,16.0745263 15.6114904,15.1845247 Z'
                    id='Eyebrow'
                />
                <path
                    d='M73.6114904,15.1845247 C77.8515017,9.41618792 80.4892046,9.70087612 86.9238518,14.5564693 C87.1057771,14.6937504 87.2212592,14.7812575 87.5936891,15.063789 C92.4216439,18.7263562 94.7081807,20 98,20 C99.1045695,20 100,19.1045695 100,18 C100,16.8954305 99.1045695,16 98,16 C95.9337712,16 94.0986396,14.9777974 90.011227,11.8770179 C89.6358269,11.5922331 89.5189458,11.5036659 89.3332441,11.3635351 C85.5737397,8.52660822 83.3739873,7.28738405 80.6379899,6.99208688 C76.9538127,6.59445233 73.5799484,8.47367246 70.3885096,12.8154753 C69.7343147,13.7054768 69.9254737,14.9572954 70.8154753,15.6114904 C71.7054768,16.2656853 72.9572954,16.0745263 73.6114904,15.1845247 Z'
                    id='Eyebrow'
                    transform='translate(84.999934, 13.470064) scale(-1, 1) translate(-84.999934, -13.470064) '
                />
            </g>
        )
    }
}

class AngryNatural extends React.Component {
    static optionValue = 'AngryNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Angry-Natural' fillOpacity='0.599999964'>
                <path
                    d='M44.8565785,12.2282877 C44.8578785,12.2192877 44.8578785,12.2192877 44.8565785,12.2282877 M17.5862288,7.89238094 C15.2441598,8.3302947 13.0866155,9.78806858 12.1523766,12.0987479 C11.8009169,12.967391 11.3917103,14.9243181 11.7083227,15.8073302 C11.8284629,16.14295 12.0332321,16.1008692 12.9555234,16.0430509 C14.643791,15.9369937 16.9330912,13.6622369 18.7484684,13.2557982 C21.2753939,12.6899315 23.9825295,13.1148447 26.4961798,13.6882381 C30.8109365,14.6725177 36.4854008,17.7875215 40.9461842,16.1699775 C41.2783949,16.0495512 45.6210294,12.9225732 44.3685187,12.2769925 C43.9238011,11.9068186 41.1370145,12.0854053 40.6216067,11.9988489 C38.2277647,11.5971998 35.7297127,10.9345131 33.373373,10.3265657 C28.2329017,9.00016592 22.9666484,6.88073171 17.5862288,7.89238094'
                    id='Eyebrows-The-Web'
                    transform='translate(28.094701, 12.127505) rotate(17.000000) translate(-28.094701, -12.127505) '
                />
                <path
                    d='M100.918293,12.2094196 C100.919593,12.2004196 100.919593,12.2004196 100.918293,12.2094196 M73.5862288,7.89238094 C71.2441598,8.3302947 69.0866155,9.78806858 68.1523766,12.0987479 C67.8009169,12.967391 67.3917103,14.9243181 67.7083227,15.8073302 C67.8284629,16.14295 68.0332321,16.1008692 68.9555234,16.0430509 C70.643791,15.9369937 72.9330912,13.6622369 74.7484684,13.2557982 C77.2753939,12.6899315 79.9825295,13.1148447 82.4961798,13.6882381 C86.8109365,14.6725177 92.4854008,17.7875215 96.9461842,16.1699775 C97.2783949,16.0495512 101.621029,12.9225732 100.368519,12.2769925 C99.9238011,11.9068186 97.1370145,12.0854053 96.6216067,11.9988489 C94.2277647,11.5971998 91.7297127,10.9345131 89.373373,10.3265657 C84.2329017,9.00016592 78.9666484,6.88073171 73.5862288,7.89238094'
                    id='Eyebrows-The-Web'
                    transform='translate(84.094701, 12.127505) scale(-1, 1) rotate(17.000000) translate(-84.094701, -12.127505) '
                />
            </g>
        )
    }
}

class EyebrowDefault extends React.Component {
    static optionValue = 'Default';

    render() {
        return (
            <g id='Eyebrow/Outline/Default' fillOpacity='0.599999964'>
                <g id='I-Browse' transform='translate(12.000000, 6.000000)'>
                    <path
                        d='M3.63024536,11.1585767 C7.54515501,5.64986673 18.2779197,2.56083721 27.5230268,4.83118046 C28.5957248,5.0946055 29.6788665,4.43856013 29.9422916,3.36586212 C30.2057166,2.2931641 29.5496712,1.21002236 28.4769732,0.94659732 C17.7403633,-1.69001789 5.31209962,1.88699832 0.369754639,8.84142326 C-0.270109626,9.74178291 -0.0589363917,10.9903811 0.84142326,11.6302454 C1.74178291,12.2701096 2.9903811,12.0589364 3.63024536,11.1585767 Z'
                        id='Eyebrow'
                        fillRule='nonzero'
                    />
                    <path
                        d='M61.6302454,11.1585767 C65.545155,5.64986673 76.2779197,2.56083721 85.5230268,4.83118046 C86.5957248,5.0946055 87.6788665,4.43856013 87.9422916,3.36586212 C88.2057166,2.2931641 87.5496712,1.21002236 86.4769732,0.94659732 C75.7403633,-1.69001789 63.3120996,1.88699832 58.3697546,8.84142326 C57.7298904,9.74178291 57.9410636,10.9903811 58.8414233,11.6302454 C59.7417829,12.2701096 60.9903811,12.0589364 61.6302454,11.1585767 Z'
                        id='Eyebrow'
                        fillRule='nonzero'
                        transform='translate(73.000154, 6.039198) scale(-1, 1) translate(-73.000154, -6.039198) '
                    />
                </g>
            </g>
        )
    }
}

class DefaultNatural extends React.Component {
    static optionValue = 'DefaultNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Default-Natural' fillOpacity='0.599999964'>
                <path
                    d='M26.0390934,6.21012364 C20.2775554,6.98346216 11.2929313,12.0052479 12.04426,17.8178111 C12.0689481,18.0080543 12.3567302,18.0673468 12.4809077,17.9084937 C14.9674041,14.7203351 34.1927973,10.0365481 41.1942673,11.0147151 C41.8350523,11.1044465 42.2580662,10.4430343 41.8210501,10.0302067 C38.0765663,6.49485426 31.2003792,5.51224825 26.0390934,6.21012364'
                    id='Eyebrow'
                    transform='translate(27.000000, 12.000000) rotate(5.000000) translate(-27.000000, -12.000000) '
                />
                <path
                    d='M85.0390934,6.21012364 C79.2775554,6.98346216 70.2929313,12.0052479 71.04426,17.8178111 C71.0689481,18.0080543 71.3567302,18.0673468 71.4809077,17.9084937 C73.9674041,14.7203351 93.1927973,10.0365481 100.194267,11.0147151 C100.835052,11.1044465 101.258066,10.4430343 100.82105,10.0302067 C97.0765663,6.49485426 90.2003792,5.51224825 85.0390934,6.21012364'
                    id='Eyebrow'
                    transform='translate(86.000000, 12.000000) scale(-1, 1) rotate(5.000000) translate(-86.000000, -12.000000) '
                />
            </g>
        )
    }
}

class FlatNatural extends React.Component {
    static optionValue = 'FlatNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Flat-Natural' fillOpacity='0.599999964'>
                <path
                    d='M38.5686071,10.7022978 C33.5865557,11.2384494 28.6553385,11.1338998 23.6562444,11.1010606 C19.8231061,11.0762636 15.91974,10.6892291 12.3246118,12.5091287 C11.6361455,12.8572921 7.8767609,14.9449324 8.00311195,16.0108688 C8.10389896,16.8633498 12.0128479,18.0636592 12.7165939,18.2838164 C16.4280826,19.4452548 19.9241869,18.9282036 23.6870976,18.5703225 C28.3024371,18.1316834 32.9139567,18.1745756 37.5322346,17.8739956 C40.6422336,17.6719334 45.4224171,16.9769469 46.8293214,13.1484895 C47.2530382,11.9954284 46.8152171,9.73353891 46.3074622,8.50642195 C46.1050066,8.01751871 45.5634602,7.84963624 45.1688335,8.14921095 C43.7560524,9.22218432 40.9851444,10.4425994 38.5686071,10.7022978'
                    id='Fill-10'
                    transform='translate(27.500000, 13.500000) rotate(2.000000) translate(-27.500000, -13.500000) '
                />
                <path
                    d='M95.5686071,10.7022978 C90.5865557,11.2384494 85.6553385,11.1338998 80.6562444,11.1010606 C76.8231061,11.0762636 72.91974,10.6892291 69.3246118,12.5091287 C68.6361455,12.8572921 64.8767609,14.9449324 65.003112,16.0108688 C65.103899,16.8633498 69.0128479,18.0636592 69.7165939,18.2838164 C73.4280826,19.4452548 76.9241869,18.9282036 80.6870976,18.5703225 C85.3024371,18.1316834 89.9139567,18.1745756 94.5322346,17.8739956 C97.6422336,17.6719334 102.422417,16.9769469 103.829321,13.1484895 C104.253038,11.9954284 103.815217,9.73353891 103.307462,8.50642195 C103.105007,8.01751871 102.56346,7.84963624 102.168833,8.14921095 C100.756052,9.22218432 97.9851444,10.4425994 95.5686071,10.7022978'
                    id='Fill-10'
                    transform='translate(84.500000, 13.500000) scale(-1, 1) rotate(2.000000) translate(-84.500000, -13.500000) '
                />
            </g>
        )
    }
}

class FrownNatural extends React.Component {
    static optionValue = 'FrownNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Frown-Natural' fillOpacity='0.599999964'>
                <path
                    d='M36.3692975,6.87618545 C34.3991755,9.78053246 30.8236346,11.5165625 27.6315757,12.5601676 C23.6890255,13.8490851 9.08080143,15.9390364 12.5196198,23.9079177 C12.572332,24.029546 12.7390347,24.0312591 12.7920764,23.9096308 C13.9448284,21.2646433 30.256648,18.7865093 31.7648785,18.2064622 C36.2101722,16.4974987 40.1579937,12.7153722 40.9269343,7.66282939 C41.2794477,5.34640965 40.2901039,1.6143049 39.3791695,0.113308759 C39.2697915,-0.0669067099 39.0052417,-0.02339461 38.9498938,0.181831751 C38.5898029,1.51323348 37.5385221,5.15317482 36.3692975,6.87618545'
                    id='Fill-5'
                />
                <path
                    d='M95.3692975,6.87618545 C93.3991755,9.78053246 89.8236346,11.5165625 86.6315757,12.5601676 C82.6890255,13.8490851 68.0808014,15.9390364 71.5196198,23.9079177 C71.572332,24.029546 71.7390347,24.0312591 71.7920764,23.9096308 C72.9448284,21.2646433 89.256648,18.7865093 90.7648785,18.2064622 C95.2101722,16.4974987 99.1579937,12.7153722 99.9269343,7.66282939 C100.279448,5.34640965 99.2901039,1.6143049 98.3791695,0.113308759 C98.2697915,-0.0669067099 98.0052417,-0.02339461 97.9498938,0.181831751 C97.5898029,1.51323348 96.5385221,5.15317482 95.3692975,6.87618545'
                    id='Fill-5'
                    transform='translate(85.500000, 12.000000) scale(-1, 1) translate(-85.500000, -12.000000) '
                />
            </g>
        )
    }
}

class RaisedExcited extends React.Component {
    static optionValue = 'RaisedExcited';

    render() {
        return (
            <g id='Eyebrow/Outline/Raised-Excited' fillOpacity='0.599999964'>
                <g id='I-Browse' transform='translate(12.000000, 0.000000)'>
                    <path
                        d='M3.97579559,17.1279169 C5.47099148,7.60476158 18.0585488,1.10867597 27.1635167,5.30104271 C28.1668367,5.76301969 29.3546946,5.32417444 29.8166716,4.32085442 C30.2786486,3.3175344 29.8398033,2.12967649 28.8364833,1.66769952 C17.3488212,-3.62177466 1.93575948,4.3324746 0.0242044059,16.507492 C-0.147121205,17.5986938 0.598585765,18.6221744 1.68978754,18.7935 C2.78098932,18.9648257 3.80446998,18.2191187 3.97579559,17.1279169 Z'
                        id='Eyebrow'
                        fillRule='nonzero'
                    />
                    <path
                        d='M61.9757956,17.1279169 C63.4709915,7.60476158 76.0585488,1.10867597 85.1635167,5.30104271 C86.1668367,5.76301969 87.3546946,5.32417444 87.8166716,4.32085442 C88.2786486,3.3175344 87.8398033,2.12967649 86.8364833,1.66769952 C75.3488212,-3.62177466 59.9357595,4.3324746 58.0242044,16.507492 C57.8528788,17.5986938 58.5985858,18.6221744 59.6897875,18.7935 C60.7809893,18.9648257 61.80447,18.2191187 61.9757956,17.1279169 Z'
                        id='Eyebrow'
                        fillRule='nonzero'
                        transform='translate(73.000097, 9.410436) scale(-1, 1) translate(-73.000097, -9.410436) '
                    />
                </g>
            </g>
        )
    }
}

class RaisedExcitedNatural extends React.Component {
    static optionValue = 'RaisedExcitedNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Raised-Excited-Natural' fillOpacity='0.599999964'>
                <path
                    d='M22.7663531,1.57844898 L23.6772984,1.17582144 C28.9190996,-0.905265751 36.8645466,-0.0328729562 41.7227321,2.29911638 C42.2897848,2.57148957 41.9021563,3.4519421 41.3211012,3.40711006 C26.4021788,2.25602197 16.3582869,11.5525942 12.9460869,17.8470939 C12.8449215,18.0337142 12.5391523,18.05489 12.4635344,17.8808353 C10.156283,12.5620676 16.9134476,3.89614725 22.7663531,1.57844898 Z'
                    id='Eye-Browse-Reddit'
                />
                <path
                    d='M80.7663531,1.57844898 L81.6772984,1.17582144 C86.9190996,-0.905265751 94.8645466,-0.0328729562 99.7227321,2.29911638 C100.289785,2.57148957 99.9021563,3.4519421 99.3211012,3.40711006 C84.4021788,2.25602197 74.3582869,11.5525942 70.9460869,17.8470939 C70.8449215,18.0337142 70.5391523,18.05489 70.4635344,17.8808353 C68.156283,12.5620676 74.9134476,3.89614725 80.7663531,1.57844898 Z'
                    id='Eye-Browse-Reddit'
                    transform='translate(85.000000, 9.000000) scale(-1, 1) translate(-85.000000, -9.000000) '
                />
            </g>
        )
    }
}

class SadConcerned extends React.Component {
    static optionValue = 'SadConcerned';

    render() {
        return (
            <g
                id='Eyebrow/Outline/Sad-Concerned'
                fillOpacity='0.599999964'
                fillRule='nonzero'>
                <path
                    d='M15.9726042,19.4088529 C17.452356,11.0203704 30.0622688,5.22829657 39.2106453,8.9774793 C40.2254706,9.39337449 41.4016967,8.94600219 41.8378196,7.97824531 C42.2739426,7.01048842 41.8048116,5.88881678 40.7899862,5.47292159 C29.3457328,0.782843812 13.9550264,7.85221132 12.0280273,18.7760684 C11.84479,19.8148122 12.5792704,20.798534 13.6685352,20.9732726 C14.7578,21.1480113 15.7893668,20.4475967 15.9726042,19.4088529 Z'
                    id='Eyebrow'
                    transform='translate(27.000414, 12.500000) scale(-1, -1) translate(-27.000414, -12.500000) '
                />
                <path
                    d='M73.9726042,19.4088529 C75.452356,11.0203704 88.0622688,5.22829657 97.2106453,8.9774793 C98.2254706,9.39337449 99.4016967,8.94600219 99.8378196,7.97824531 C100.273943,7.01048842 99.8048116,5.88881678 98.7899862,5.47292159 C87.3457328,0.782843812 71.9550264,7.85221132 70.0280273,18.7760684 C69.84479,19.8148122 70.5792704,20.798534 71.6685352,20.9732726 C72.7578,21.1480113 73.7893668,20.4475967 73.9726042,19.4088529 Z'
                    id='Eyebrow'
                    transform='translate(85.000414, 12.500000) scale(1, -1) translate(-85.000414, -12.500000) '
                />
            </g>
        )
    }
}

class SadConcernedNatural extends React.Component {
    static optionValue = 'SadConcernedNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Sad-Concerned-Natural' fillOpacity='0.599999964'>
                <path
                    d='M22.7663531,5.57844898 L23.6772984,5.17582144 C28.9190996,3.09473425 36.8645466,3.96712704 41.7227321,6.29911638 C42.2897848,6.57148957 41.9021563,7.4519421 41.3211012,7.40711006 C26.4021788,6.25602197 16.3582869,15.5525942 12.9460869,21.8470939 C12.8449215,22.0337142 12.5391523,22.05489 12.4635344,21.8808353 C10.156283,16.5620676 16.9134476,7.89614725 22.7663531,5.57844898 Z'
                    id='Eyebrow'
                    transform='translate(27.000000, 13.000000) scale(-1, -1) translate(-27.000000, -13.000000) '
                />
                <path
                    d='M80.7663531,5.57844898 L81.6772984,5.17582144 C86.9190996,3.09473425 94.8645466,3.96712704 99.7227321,6.29911638 C100.289785,6.57148957 99.9021563,7.4519421 99.3211012,7.40711006 C84.4021788,6.25602197 74.3582869,15.5525942 70.9460869,21.8470939 C70.8449215,22.0337142 70.5391523,22.05489 70.4635344,21.8808353 C68.156283,16.5620676 74.9134476,7.89614725 80.7663531,5.57844898 Z'
                    id='Eyebrow'
                    transform='translate(85.000000, 13.000000) scale(1, -1) translate(-85.000000, -13.000000) '
                />
            </g>
        )
    }
}

class UnibrowNatural extends React.Component {
    static optionValue = 'UnibrowNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Unibrow-Natural' fillOpacity='0.599999964'>
                <path
                    d='M57.000525,12 C56.999825,11.9961 56.999825,11.9961 57.000525,12 M59.4596631,14.892451 C61.3120123,16.058698 64.1131185,16.7894891 65.7030886,17.0505179 C71.9486685,18.0766191 78.0153663,15.945512 84.1715051,15.0153209 C89.636055,14.1895424 95.8563653,13.4967455 100.86041,16.507708 C100.987756,16.584232 101.997542,17.2147893 102.524546,17.7511372 C102.91024,18.1443003 103.563259,18.0619945 103.822605,17.5722412 C105.241692,14.8939029 97.7243204,8.76008291 96.2812935,8.14993193 C89.7471082,5.39200867 81.0899445,8.32440654 74.4284137,9.38927986 C70.6888462,9.98718701 66.9279989,10.3803501 63.2409655,11.2908151 C61.9188284,11.6171635 60.6278928,12.2066818 59.3382119,12.3724317 C59.1848981,12.1429782 58.9889964,12 58.7633758,12 C57.5922879,12 55.8451696,15.4574504 58.0750241,15.6547468 C58.7728345,15.7164887 59.215997,15.3816732 59.4596631,14.892451 Z'
                    id='Kahlo'
                    transform='translate(80.500000, 12.500000) rotate(-2.000000) translate(-80.500000, -12.500000) '
                />
                <path
                    d='M54.999475,12 C55.000175,11.9961 55.000175,11.9961 54.999475,12 M15.7187065,8.14993193 C22.2528918,5.39200867 30.9100555,8.32440654 37.5715863,9.38927986 C41.3111538,9.98718701 45.0720011,10.3803501 48.7590345,11.2908151 C50.2416282,11.6567696 51.6849876,12.3536477 53.1313394,12.4128263 C53.8325707,12.4413952 54.2674737,13.2763566 53.8149484,13.8242681 C52.3320222,15.6179895 48.3271239,16.7172136 46.2969114,17.0505179 C40.0513315,18.0766191 33.9846337,15.945512 27.8284949,15.0153209 C22.363945,14.1895424 16.1436347,13.4967455 11.1395899,16.507708 C11.0122444,16.584232 10.0024581,17.2147893 9.47545402,17.7511372 C9.0897602,18.1443003 8.43674067,18.0619945 8.17739482,17.5722412 C6.75830756,14.8939029 14.2756796,8.76008291 15.7187065,8.14993193 Z M54.9339874,11 C56.1050753,11 57.8521936,15.4015737 55.6223391,15.6527457 C53.3924847,15.9039176 53.7628995,11 54.9339874,11 Z'
                    id='Frida'
                    transform='translate(32.348682, 12.500000) rotate(2.000000) translate(-32.348682, -12.500000) '
                />
            </g>
        )
    }
}

class UpDown extends React.Component {
    static optionValue = 'UpDown';

    render() {
        return (
            <g
                id='Eyebrow/Outline/Up-Down'
                fillOpacity='0.599999964'
                fillRule='nonzero'>
                <path
                    d='M15.5914402,14.1619718 C20.0874642,7.83556966 29.6031809,4.65350252 39.3473715,7.79575991 C40.3986323,8.13476518 41.5256656,7.55736801 41.8646708,6.50610724 C42.2036761,5.45484647 41.6262789,4.32781316 40.5750182,3.98880789 C29.1665516,0.309863172 17.8358054,4.09887835 12.3309495,11.8448183 C11.6910852,12.7451779 11.9022584,13.9937761 12.8026181,14.6336404 C13.7029777,15.2735046 14.9515759,15.0623314 15.5914402,14.1619718 Z'
                    id='Eyebrow'
                />
                <path
                    d='M73.6376405,21.1577995 C77.5525501,15.6490895 88.2853148,12.56006 97.5304219,14.8304032 C98.6031199,15.0938282 99.6862617,14.4377829 99.9496867,13.3650849 C100.213112,12.2923868 99.5570664,11.2092451 98.4843684,10.9458201 C87.7477584,8.30920485 75.3194947,11.8862211 70.3771498,18.840646 C69.7372855,19.7410057 69.9484587,20.9896038 70.8488184,21.6294681 C71.749178,22.2693324 72.9977762,22.0581591 73.6376405,21.1577995 Z'
                    id='Eyebrow'
                    transform='translate(85.007549, 16.038421) scale(-1, 1) translate(-85.007549, -16.038421) '
                />
            </g>
        )
    }
}

class UpDownNatural extends React.Component {
    static optionValue = 'UpDownNatural';

    render() {
        return (
            <g id='Eyebrow/Natural/Up-Down-Natural' fillOpacity='0.599999964'>
                <path
                    d='M22.7663531,1.57844898 L23.6772984,1.17582144 C28.9190996,-0.905265751 36.8645466,-0.0328729562 41.7227321,2.29911638 C42.2897848,2.57148957 41.9021563,3.4519421 41.3211012,3.40711006 C26.4021788,2.25602197 16.3582869,11.5525942 12.9460869,17.8470939 C12.8449215,18.0337142 12.5391523,18.05489 12.4635344,17.8808353 C10.156283,12.5620676 16.9134476,3.89614725 22.7663531,1.57844898 Z'
                    id='Eye-Browse-Reddit'
                />
                <path
                    d='M86.535177,12.0246305 C92.3421916,12.2928751 101.730304,16.5124899 101.488432,22.3684172 C101.480419,22.5600881 101.1989,22.6442368 101.06135,22.496811 C98.306449,19.5374968 78.7459953,16.5471364 71.8564209,18.1317995 C71.2258949,18.2770375 70.7468448,17.6550104 71.1462176,17.2056651 C74.5683263,13.3574126 81.3327077,11.7792465 86.535177,12.0246305 Z'
                    id='Eyebrow'
                    transform='translate(86.246508, 17.285912) rotate(5.000000) translate(-86.246508, -17.285912) '
                />
            </g>
        )
    }
}
