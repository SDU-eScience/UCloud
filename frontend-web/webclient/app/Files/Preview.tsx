import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import * as React from "react";
import {useLocation} from "react-router";
import {getQueryParam} from "Utilities/URIUtilities";
import * as UCloud from "UCloud";
import {extensionFromPath, extensionTypeFromPath, isExtPreviewSupported} from "UtilityFunctions";
import {PredicatedLoadingSpinner} from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";
import {Markdown} from "ui-components";
import {fileName} from "./Files";
import * as Heading from "ui-components/Heading";

export const MAX_PREVIEW_SIZE_IN_BYTES = 5_000_000;

function Preview(): JSX.Element {
    const params = useLocation();
    const pathFromQuery = getQueryParam(params.search, "path") ?? "";

    const extension = extensionFromPath(pathFromQuery);
    const isValidExtension = isExtPreviewSupported(extension);
    const type = extensionTypeFromPath(pathFromQuery);
    const [loading, invokeCommand] = useCloudCommand();

    const [statResult] = useCloudAPI<undefined | UCloud.file.orchestrator.UFile>(
        !isValidExtension ? {noop: true} : UCloud.file.orchestrator.files.retrieve({
            path: pathFromQuery,
            includeSizes: true
        }), undefined)

    const [data, setData] = React.useState("");

    const fetchData = React.useCallback(async () => {
        if (!loading && isValidExtension) {
            // setData(await invokeCommand(Cloud.file.orchestrator.files.download()));

/*             // @TEMP
            switch (type) {
                case "audio":
                    setData("SoundEffect");
                    break;
                case "code":
                    console.log("TODO" + type);
                    break;
                case "image":
                    setData("TerrorBilly==");
                    break;
                case "markdown":
                    setData("DoomExample");
                    break;
                case "pdf":
                    console.log("TODO" + type);
                    break;
                case "text":
                    setData(LoremText);
                    break;
                case "video":
                    console.log("TODO" + type);
                    break;
                default:
                    console.log("UNHANDLED PREVIEW")
            }
            // @TEMP-END */
        }
    }, [pathFromQuery]);

    React.useEffect(() => {
        fetchData();
    }, [statResult]);

    if (loading) return <PredicatedLoadingSpinner loading />
    if (!data) return <div />;

    let node: JSX.Element | null = null

    switch (type) {
        case "image":
            node = <img src={`data:;base64,${data}`} />
            break;
        case "text":
            node = <pre style={{maxWidth: "100%"}}>{data}</pre>
            break;
        case "audio":
            node = <audio controls src={`data:;base64,${data}`} />;
            break;
        case "markdown":
            node = <Markdown>{data}</Markdown>
            break;
        default:
            node = <div />
            break;
    }

    return <MainContainer
        header={<Heading.h3>{fileName(pathFromQuery)}</Heading.h3>}
        main={<div style={{maxWidth: "100%"}}>{node}</div>}
    />
}

export default Preview;

const LoremText = `
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam fringilla ipsum sem, id egestas risus mollis nec.
    Quisque sed efficitur lectus. Vestibulum magna erat, auctor at malesuada ut, scelerisque nec quam. Nam mattis at
    turpis nec vestibulum. Donec vel sapien tempus, porta odio sed, tincidunt metus. Integer ex turpis, pharetra at
    pellentesque ut, suscipit ut tortor. In hac habitasse platea dictumst. Morbi blandit fermentum gravida. Proin in
    ultricies mi, sed bibendum dui. Ut eros risus, ultrices vel nisi ac, sodales dictum arcu. Donec mattis urna nec
    arcu posuere efficitur. Integer luctus ac tellus non tempus. Proin sodales volutpat auctor. Nam laoreet, tellus
    in sodales egestas, odio ante bibendum odio, vel elementum ipsum quam at neque. Aliquam nec nisl sodales, placerat
    odio sit amet, aliquam metus.

    Aenean at nunc venenatis, ultricies ex id, varius enim. Fusce lacinia vulputate est vel bibendum. Ut at consequat
    nulla. Sed placerat erat dolor, in molestie neque egestas nec. Nulla rhoncus, mauris vitae hendrerit volutpat,
    dui mauris scelerisque quam, id rutrum tortor elit nec nunc. Etiam id elementum metus, a tristique mi. Aenean
    imperdiet, quam ac tempus feugiat, magna tellus tristique mauris, at vehicula quam mi vel nunc. Maecenas volutpat
    aliquam elit, eget elementum lorem interdum at.

    Vestibulum id lacus vitae nisi tristique tincidunt. Maecenas facilisis turpis vel metus auctor, in imperdiet dolor
    ultrices. Integer venenatis hendrerit vehicula. Integer id mauris erat. Vivamus posuere sollicitudin purus, vitae
    interdum massa posuere ut. Etiam et eleifend diam, in luctus diam. Aenean volutpat sem id lacus imperdiet malesuada.
    Morbi vulputate est eget leo luctus gravida. Aenean commodo a libero a feugiat. Ut ullamcorper elementum ex, quis
    ultrices nulla congue scelerisque. Phasellus bibendum eu metus ut efficitur.

    Praesent tempor ipsum ac euismod consequat. Quisque semper tortor ac magna aliquam, consectetur pretium sapien
    suscipit. Phasellus eu augue eget massa gravida feugiat sed sit amet ipsum. Aenean condimentum aliquam sapien vel
    suscipit. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Orci varius
    natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Integer scelerisque sem leo, nec bibendum
    elit vestibulum ut. Phasellus lacinia venenatis sollicitudin.

    Pellentesque molestie varius fermentum. In eu purus non lacus tincidunt lacinia. In hac habitasse platea dictumst.
    Quisque blandit sed nulla at accumsan. Donec finibus est eros, euismod iaculis diam porttitor ac. Duis nec arcu
    eleifend, ullamcorper quam et, ultricies metus. Vivamus non justo id quam lobortis volutpat.
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam fringilla ipsum sem, id egestas risus mollis nec.
    Quisque sed efficitur lectus. Vestibulum magna erat, auctor at malesuada ut, scelerisque nec quam. Nam mattis at
    turpis nec vestibulum. Donec vel sapien tempus, porta odio sed, tincidunt metus. Integer ex turpis, pharetra at
    pellentesque ut, suscipit ut tortor. In hac habitasse platea dictumst. Morbi blandit fermentum gravida. Proin in
    ultricies mi, sed bibendum dui. Ut eros risus, ultrices vel nisi ac, sodales dictum arcu. Donec mattis urna nec
    arcu posuere efficitur. Integer luctus ac tellus non tempus. Proin sodales volutpat auctor. Nam laoreet, tellus
    in sodales egestas, odio ante bibendum odio, vel elementum ipsum quam at neque. Aliquam nec nisl sodales, placerat
    odio sit amet, aliquam metus.

    Aenean at nunc venenatis, ultricies ex id, varius enim. Fusce lacinia vulputate est vel bibendum. Ut at consequat
    nulla. Sed placerat erat dolor, in molestie neque egestas nec. Nulla rhoncus, mauris vitae hendrerit volutpat,
    dui mauris scelerisque quam, id rutrum tortor elit nec nunc. Etiam id elementum metus, a tristique mi. Aenean
    imperdiet, quam ac tempus feugiat, magna tellus tristique mauris, at vehicula quam mi vel nunc. Maecenas volutpat
    aliquam elit, eget elementum lorem interdum at.

    Vestibulum id lacus vitae nisi tristique tincidunt. Maecenas facilisis turpis vel metus auctor, in imperdiet dolor
    ultrices. Integer venenatis hendrerit vehicula. Integer id mauris erat. Vivamus posuere sollicitudin purus, vitae
    interdum massa posuere ut. Etiam et eleifend diam, in luctus diam. Aenean volutpat sem id lacus imperdiet malesuada.
    Morbi vulputate est eget leo luctus gravida. Aenean commodo a libero a feugiat. Ut ullamcorper elementum ex, quis
    ultrices nulla congue scelerisque. Phasellus bibendum eu metus ut efficitur.

    Praesent tempor ipsum ac euismod consequat. Quisque semper tortor ac magna aliquam, consectetur pretium sapien
    suscipit. Phasellus eu augue eget massa gravida feugiat sed sit amet ipsum. Aenean condimentum aliquam sapien vel
    suscipit. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Orci varius
    natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Integer scelerisque sem leo, nec bibendum
    elit vestibulum ut. Phasellus lacinia venenatis sollicitudin.

    Pellentesque molestie varius fermentum. In eu purus non lacus tincidunt lacinia. In hac habitasse platea dictumst.
    Quisque blandit sed nulla at accumsan. Donec finibus est eros, euismod iaculis diam porttitor ac. Duis nec arcu
    eleifend, ullamcorper quam et, ultricies metus. Vivamus non justo id quam lobortis volutpat.
    Lorem ipsum dolor sit amet, consectetur adipiscing elit. Nullam fringilla ipsum sem, id egestas risus mollis nec.
    Quisque sed efficitur lectus. Vestibulum magna erat, auctor at malesuada ut, scelerisque nec quam. Nam mattis at
    turpis nec vestibulum. Donec vel sapien tempus, porta odio sed, tincidunt metus. Integer ex turpis, pharetra at
    pellentesque ut, suscipit ut tortor. In hac habitasse platea dictumst. Morbi blandit fermentum gravida. Proin in
    ultricies mi, sed bibendum dui. Ut eros risus, ultrices vel nisi ac, sodales dictum arcu. Donec mattis urna nec
    arcu posuere efficitur. Integer luctus ac tellus non tempus. Proin sodales volutpat auctor. Nam laoreet, tellus
    in sodales egestas, odio ante bibendum odio, vel elementum ipsum quam at neque. Aliquam nec nisl sodales, placerat
    odio sit amet, aliquam metus.

    Aenean at nunc venenatis, ultricies ex id, varius enim. Fusce lacinia vulputate est vel bibendum. Ut at consequat
    nulla. Sed placerat erat dolor, in molestie neque egestas nec. Nulla rhoncus, mauris vitae hendrerit volutpat,
    dui mauris scelerisque quam, id rutrum tortor elit nec nunc. Etiam id elementum metus, a tristique mi. Aenean
    imperdiet, quam ac tempus feugiat, magna tellus tristique mauris, at vehicula quam mi vel nunc. Maecenas volutpat
    aliquam elit, eget elementum lorem interdum at.

    Vestibulum id lacus vitae nisi tristique tincidunt. Maecenas facilisis turpis vel metus auctor, in imperdiet dolor
    ultrices. Integer venenatis hendrerit vehicula. Integer id mauris erat. Vivamus posuere sollicitudin purus, vitae
    interdum massa posuere ut. Etiam et eleifend diam, in luctus diam. Aenean volutpat sem id lacus imperdiet malesuada.
    Morbi vulputate est eget leo luctus gravida. Aenean commodo a libero a feugiat. Ut ullamcorper elementum ex, quis
    ultrices nulla congue scelerisque. Phasellus bibendum eu metus ut efficitur.

    Praesent tempor ipsum ac euismod consequat. Quisque semper tortor ac magna aliquam, consectetur pretium sapien
    suscipit. Phasellus eu augue eget massa gravida feugiat sed sit amet ipsum. Aenean condimentum aliquam sapien vel
    suscipit. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Orci varius
    natoque penatibus et magnis dis parturient montes, nascetur ridiculus mus. Integer scelerisque sem leo, nec bibendum
    elit vestibulum ut. Phasellus lacinia venenatis sollicitudin.

    Pellentesque molestie varius fermentum. In eu purus non lacus tincidunt lacinia. In hac habitasse platea dictumst.
    Quisque blandit sed nulla at accumsan. Donec finibus est eros, euismod iaculis diam porttitor ac. Duis nec arcu
    eleifend, ullamcorper quam et, ultricies metus. Vivamus non justo id quam lobortis volutpat.
`;