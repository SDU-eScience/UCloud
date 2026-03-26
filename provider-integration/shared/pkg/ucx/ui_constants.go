package ucx

import "ucloud.dk/shared/pkg/util"

// Color and IconName constants are derived from frontend-web/webclient
// to keep UCX backend and frontend values aligned.
type Color string
type IconName string

const (
	ColorPrimaryMain          Color = "primaryMain"
	ColorPrimaryLight         Color = "primaryLight"
	ColorPrimaryDark          Color = "primaryDark"
	ColorPrimaryContrast      Color = "primaryContrast"
	ColorPrimaryContrastAlt   Color = "primaryContrastAlt"
	ColorSecondaryMain        Color = "secondaryMain"
	ColorSecondaryLight       Color = "secondaryLight"
	ColorSecondaryDark        Color = "secondaryDark"
	ColorSecondaryContrast    Color = "secondaryContrast"
	ColorSecondaryContrastAlt Color = "secondaryContrastAlt"
	ColorErrorMain            Color = "errorMain"
	ColorErrorLight           Color = "errorLight"
	ColorErrorDark            Color = "errorDark"
	ColorErrorContrast        Color = "errorContrast"
	ColorErrorContrastAlt     Color = "errorContrastAlt"
	ColorWarningMain          Color = "warningMain"
	ColorWarningLight         Color = "warningLight"
	ColorWarningDark          Color = "warningDark"
	ColorWarningContrast      Color = "warningContrast"
	ColorWarningContrastAlt   Color = "warningContrastAlt"
	ColorInfoMain             Color = "infoMain"
	ColorInfoLight            Color = "infoLight"
	ColorInfoDark             Color = "infoDark"
	ColorInfoContrast         Color = "infoContrast"
	ColorInfoContrastAlt      Color = "infoContrastAlt"
	ColorSuccessMain          Color = "successMain"
	ColorSuccessLight         Color = "successLight"
	ColorSuccessDark          Color = "successDark"
	ColorSuccessContrast      Color = "successContrast"
	ColorSuccessContrastAlt   Color = "successContrastAlt"
	ColorBackgroundDefault    Color = "backgroundDefault"
	ColorBackgroundCard       Color = "backgroundCard"
	ColorTextPrimary          Color = "textPrimary"
	ColorTextSecondary        Color = "textSecondary"
	ColorTextDisabled         Color = "textDisabled"
	ColorIconColor            Color = "iconColor"
	ColorIconColor2           Color = "iconColor2"
	ColorFixedWhite           Color = "fixedWhite"
	ColorFixedBlack           Color = "fixedBlack"
	ColorFavoriteColor        Color = "favoriteColor"
	ColorFavoriteColorEmpty   Color = "favoriteColorEmpty"
	ColorWayfGreen            Color = "wayfGreen"
	ColorBorderColor          Color = "borderColor"
	ColorBorderColorHover     Color = "borderColorHover"
	ColorRowHover             Color = "rowHover"
	ColorRowActive            Color = "rowActive"
	ColorFtFolderColor        Color = "FtFolderColor"
	ColorFtFolderColor2       Color = "FtFolderColor2"
	ColorSidebarColor         Color = "sidebarColor"
	ColorLinkColor            Color = "linkColor"
	ColorLinkColorHover       Color = "linkColorHover"
)

const (
	IconActivity                       IconName = "activity"
	IconAdmin                          IconName = "admin"
	IconAnglesDownSolid                IconName = "anglesDownSolid"
	IconAnglesUpSolid                  IconName = "anglesUpSolid"
	IconAppFav                         IconName = "appFav"
	IconAppStore                       IconName = "appStore"
	IconApps                           IconName = "apps"
	IconArrowDown                      IconName = "arrowDown"
	IconBackward                       IconName = "backward"
	IconBoxChecked                     IconName = "boxChecked"
	IconBoxEmpty                       IconName = "boxEmpty"
	IconBroom                          IconName = "broom"
	IconBug                            IconName = "bug"
	IconCalendar                       IconName = "calendar"
	IconCheck                          IconName = "check"
	IconCheckDouble                    IconName = "checkDouble"
	IconChrono                         IconName = "chrono"
	IconCircle                         IconName = "circle"
	IconClose                          IconName = "close"
	IconCloudTryingItsBest             IconName = "cloudTryingItsBest"
	IconCopy                           IconName = "copy"
	IconCpu                            IconName = "cpu"
	IconCubeSolid                      IconName = "cubeSolid"
	IconDashboard                      IconName = "dashboard"
	IconDeiCLogo                       IconName = "deiCLogo"
	IconDocs                           IconName = "docs"
	IconDocumentation                  IconName = "documentation"
	IconDownload                       IconName = "download"
	IconEdit                           IconName = "edit"
	IconEllipsis                       IconName = "ellipsis"
	IconExtract                        IconName = "extract"
	IconEye                            IconName = "eye"
	IconFavIcon                        IconName = "favIcon"
	IconFileSignatureSolid             IconName = "fileSignatureSolid"
	IconFiles                          IconName = "files"
	IconFilterSolid                    IconName = "filterSolid"
	IconFloppyDisk                     IconName = "floppyDisk"
	IconFork                           IconName = "fork"
	IconForward                        IconName = "forward"
	IconFtFavFolder                    IconName = "ftFavFolder"
	IconFtFileSystem                   IconName = "ftFileSystem"
	IconFtFolder                       IconName = "ftFolder"
	IconFtFolderAlt                    IconName = "ftFolderAlt"
	IconFtFsFolder                     IconName = "ftFsFolder"
	IconFtResultsFolder                IconName = "ftResultsFolder"
	IconFtSharesFolder                 IconName = "ftSharesFolder"
	IconGlobeEuropeSolid               IconName = "globeEuropeSolid"
	IconGrant                          IconName = "grant"
	IconGsd                            IconName = "gsd"
	IconHashtag                        IconName = "hashtag"
	IconHdd                            IconName = "hdd"
	IconHeroAcademicCap                IconName = "heroAcademicCap"
	IconHeroAdjustmentsHorizontal      IconName = "heroAdjustmentsHorizontal"
	IconHeroAdjustmentsVertical        IconName = "heroAdjustmentsVertical"
	IconHeroArchiveBox                 IconName = "heroArchiveBox"
	IconHeroArchiveBoxArrowDown        IconName = "heroArchiveBoxArrowDown"
	IconHeroArchiveBoxXMark            IconName = "heroArchiveBoxXMark"
	IconHeroArrowDown                  IconName = "heroArrowDown"
	IconHeroArrowDownCircle            IconName = "heroArrowDownCircle"
	IconHeroArrowDownLeft              IconName = "heroArrowDownLeft"
	IconHeroArrowDownOnSquare          IconName = "heroArrowDownOnSquare"
	IconHeroArrowDownOnSquareStack     IconName = "heroArrowDownOnSquareStack"
	IconHeroArrowDownRight             IconName = "heroArrowDownRight"
	IconHeroArrowDownTray              IconName = "heroArrowDownTray"
	IconHeroArrowLeft                  IconName = "heroArrowLeft"
	IconHeroArrowLeftCircle            IconName = "heroArrowLeftCircle"
	IconHeroArrowLeftOnRectangle       IconName = "heroArrowLeftOnRectangle"
	IconHeroArrowLongDown              IconName = "heroArrowLongDown"
	IconHeroArrowLongLeft              IconName = "heroArrowLongLeft"
	IconHeroArrowLongRight             IconName = "heroArrowLongRight"
	IconHeroArrowLongUp                IconName = "heroArrowLongUp"
	IconHeroArrowPath                  IconName = "heroArrowPath"
	IconHeroArrowPathRoundedSquare     IconName = "heroArrowPathRoundedSquare"
	IconHeroArrowRight                 IconName = "heroArrowRight"
	IconHeroArrowRightCircle           IconName = "heroArrowRightCircle"
	IconHeroArrowRightOnRectangle      IconName = "heroArrowRightOnRectangle"
	IconHeroArrowSmallDown             IconName = "heroArrowSmallDown"
	IconHeroArrowSmallLeft             IconName = "heroArrowSmallLeft"
	IconHeroArrowSmallRight            IconName = "heroArrowSmallRight"
	IconHeroArrowSmallUp               IconName = "heroArrowSmallUp"
	IconHeroArrowTopRightOnSquare      IconName = "heroArrowTopRightOnSquare"
	IconHeroArrowTrendingDown          IconName = "heroArrowTrendingDown"
	IconHeroArrowTrendingUp            IconName = "heroArrowTrendingUp"
	IconHeroArrowUp                    IconName = "heroArrowUp"
	IconHeroArrowUpCircle              IconName = "heroArrowUpCircle"
	IconHeroArrowUpLeft                IconName = "heroArrowUpLeft"
	IconHeroArrowUpOnSquare            IconName = "heroArrowUpOnSquare"
	IconHeroArrowUpOnSquareStack       IconName = "heroArrowUpOnSquareStack"
	IconHeroArrowUpRight               IconName = "heroArrowUpRight"
	IconHeroArrowUpTray                IconName = "heroArrowUpTray"
	IconHeroArrowUturnDown             IconName = "heroArrowUturnDown"
	IconHeroArrowUturnLeft             IconName = "heroArrowUturnLeft"
	IconHeroArrowUturnRight            IconName = "heroArrowUturnRight"
	IconHeroArrowUturnUp               IconName = "heroArrowUturnUp"
	IconHeroArrowsPointingIn           IconName = "heroArrowsPointingIn"
	IconHeroArrowsPointingOut          IconName = "heroArrowsPointingOut"
	IconHeroArrowsRightLeft            IconName = "heroArrowsRightLeft"
	IconHeroArrowsUpDown               IconName = "heroArrowsUpDown"
	IconHeroAtSymbol                   IconName = "heroAtSymbol"
	IconHeroBackspace                  IconName = "heroBackspace"
	IconHeroBackward                   IconName = "heroBackward"
	IconHeroBanknotes                  IconName = "heroBanknotes"
	IconHeroBars2                      IconName = "heroBars2"
	IconHeroBars3                      IconName = "heroBars3"
	IconHeroBars3BottomLeft            IconName = "heroBars3BottomLeft"
	IconHeroBars3BottomRight           IconName = "heroBars3BottomRight"
	IconHeroBars3CenterLeft            IconName = "heroBars3CenterLeft"
	IconHeroBars4                      IconName = "heroBars4"
	IconHeroBarsArrowDown              IconName = "heroBarsArrowDown"
	IconHeroBarsArrowUp                IconName = "heroBarsArrowUp"
	IconHeroBattery0                   IconName = "heroBattery0"
	IconHeroBattery100                 IconName = "heroBattery100"
	IconHeroBattery50                  IconName = "heroBattery50"
	IconHeroBeaker                     IconName = "heroBeaker"
	IconHeroBell                       IconName = "heroBell"
	IconHeroBellAlert                  IconName = "heroBellAlert"
	IconHeroBellSlash                  IconName = "heroBellSlash"
	IconHeroBellSnooze                 IconName = "heroBellSnooze"
	IconHeroBolt                       IconName = "heroBolt"
	IconHeroBoltSlash                  IconName = "heroBoltSlash"
	IconHeroBookOpen                   IconName = "heroBookOpen"
	IconHeroBookmark                   IconName = "heroBookmark"
	IconHeroBookmarkSlash              IconName = "heroBookmarkSlash"
	IconHeroBookmarkSquare             IconName = "heroBookmarkSquare"
	IconHeroBriefcase                  IconName = "heroBriefcase"
	IconHeroBugAnt                     IconName = "heroBugAnt"
	IconHeroBuildingLibrary            IconName = "heroBuildingLibrary"
	IconHeroBuildingOffice             IconName = "heroBuildingOffice"
	IconHeroBuildingOffice2            IconName = "heroBuildingOffice2"
	IconHeroBuildingStorefront         IconName = "heroBuildingStorefront"
	IconHeroCake                       IconName = "heroCake"
	IconHeroCalculator                 IconName = "heroCalculator"
	IconHeroCalendar                   IconName = "heroCalendar"
	IconHeroCalendarDays               IconName = "heroCalendarDays"
	IconHeroCamera                     IconName = "heroCamera"
	IconHeroChartBar                   IconName = "heroChartBar"
	IconHeroChartBarSquare             IconName = "heroChartBarSquare"
	IconHeroChartPie                   IconName = "heroChartPie"
	IconHeroChatBubbleBottomCenter     IconName = "heroChatBubbleBottomCenter"
	IconHeroChatBubbleBottomCenterText IconName = "heroChatBubbleBottomCenterText"
	IconHeroChatBubbleLeft             IconName = "heroChatBubbleLeft"
	IconHeroChatBubbleLeftEllipsis     IconName = "heroChatBubbleLeftEllipsis"
	IconHeroChatBubbleLeftRight        IconName = "heroChatBubbleLeftRight"
	IconHeroChatBubbleOvalLeft         IconName = "heroChatBubbleOvalLeft"
	IconHeroChatBubbleOvalLeftEllipsis IconName = "heroChatBubbleOvalLeftEllipsis"
	IconHeroCheck                      IconName = "heroCheck"
	IconHeroCheckBadge                 IconName = "heroCheckBadge"
	IconHeroCheckCircle                IconName = "heroCheckCircle"
	IconHeroChevronDoubleDown          IconName = "heroChevronDoubleDown"
	IconHeroChevronDoubleLeft          IconName = "heroChevronDoubleLeft"
	IconHeroChevronDoubleRight         IconName = "heroChevronDoubleRight"
	IconHeroChevronDoubleUp            IconName = "heroChevronDoubleUp"
	IconHeroChevronDown                IconName = "heroChevronDown"
	IconHeroChevronLeft                IconName = "heroChevronLeft"
	IconHeroChevronRight               IconName = "heroChevronRight"
	IconHeroChevronUp                  IconName = "heroChevronUp"
	IconHeroChevronUpDown              IconName = "heroChevronUpDown"
	IconHeroCircleStack                IconName = "heroCircleStack"
	IconHeroClipboard                  IconName = "heroClipboard"
	IconHeroClipboardDocument          IconName = "heroClipboardDocument"
	IconHeroClipboardDocumentCheck     IconName = "heroClipboardDocumentCheck"
	IconHeroClipboardDocumentList      IconName = "heroClipboardDocumentList"
	IconHeroClock                      IconName = "heroClock"
	IconHeroCloud                      IconName = "heroCloud"
	IconHeroCloudArrowDown             IconName = "heroCloudArrowDown"
	IconHeroCloudArrowUp               IconName = "heroCloudArrowUp"
	IconHeroCodeBracket                IconName = "heroCodeBracket"
	IconHeroCodeBracketSquare          IconName = "heroCodeBracketSquare"
	IconHeroCog                        IconName = "heroCog"
	IconHeroCog6Tooth                  IconName = "heroCog6Tooth"
	IconHeroCog8Tooth                  IconName = "heroCog8Tooth"
	IconHeroCommandLine                IconName = "heroCommandLine"
	IconHeroComputerDesktop            IconName = "heroComputerDesktop"
	IconHeroCpuChip                    IconName = "heroCpuChip"
	IconHeroCreditCard                 IconName = "heroCreditCard"
	IconHeroCube                       IconName = "heroCube"
	IconHeroCubeTransparent            IconName = "heroCubeTransparent"
	IconHeroCurrencyBangladeshi        IconName = "heroCurrencyBangladeshi"
	IconHeroCurrencyDollar             IconName = "heroCurrencyDollar"
	IconHeroCurrencyEuro               IconName = "heroCurrencyEuro"
	IconHeroCurrencyPound              IconName = "heroCurrencyPound"
	IconHeroCurrencyRupee              IconName = "heroCurrencyRupee"
	IconHeroCurrencyYen                IconName = "heroCurrencyYen"
	IconHeroCursorArrowRays            IconName = "heroCursorArrowRays"
	IconHeroCursorArrowRipple          IconName = "heroCursorArrowRipple"
	IconHeroDevicePhoneMobile          IconName = "heroDevicePhoneMobile"
	IconHeroDeviceTablet               IconName = "heroDeviceTablet"
	IconHeroDocument                   IconName = "heroDocument"
	IconHeroDocumentArrowDown          IconName = "heroDocumentArrowDown"
	IconHeroDocumentArrowUp            IconName = "heroDocumentArrowUp"
	IconHeroDocumentChartBar           IconName = "heroDocumentChartBar"
	IconHeroDocumentCheck              IconName = "heroDocumentCheck"
	IconHeroDocumentDuplicate          IconName = "heroDocumentDuplicate"
	IconHeroDocumentMagnifyingGlass    IconName = "heroDocumentMagnifyingGlass"
	IconHeroDocumentMinus              IconName = "heroDocumentMinus"
	IconHeroDocumentPlus               IconName = "heroDocumentPlus"
	IconHeroDocumentText               IconName = "heroDocumentText"
	IconHeroEnvelope                   IconName = "heroEnvelope"
	IconHeroEnvelopeOpen               IconName = "heroEnvelopeOpen"
	IconHeroExclamationCircle          IconName = "heroExclamationCircle"
	IconHeroExclamationTriangle        IconName = "heroExclamationTriangle"
	IconHeroEye                        IconName = "heroEye"
	IconHeroEyeDropper                 IconName = "heroEyeDropper"
	IconHeroEyeSlash                   IconName = "heroEyeSlash"
	IconHeroFaceFrown                  IconName = "heroFaceFrown"
	IconHeroFaceSmile                  IconName = "heroFaceSmile"
	IconHeroFilm                       IconName = "heroFilm"
	IconHeroFingerPrint                IconName = "heroFingerPrint"
	IconHeroFire                       IconName = "heroFire"
	IconHeroFlag                       IconName = "heroFlag"
	IconHeroFolder                     IconName = "heroFolder"
	IconHeroFolderArrowDown            IconName = "heroFolderArrowDown"
	IconHeroFolderMinus                IconName = "heroFolderMinus"
	IconHeroFolderOpen                 IconName = "heroFolderOpen"
	IconHeroFolderPlus                 IconName = "heroFolderPlus"
	IconHeroForward                    IconName = "heroForward"
	IconHeroFunnel                     IconName = "heroFunnel"
	IconHeroGif                        IconName = "heroGif"
	IconHeroGift                       IconName = "heroGift"
	IconHeroGiftTop                    IconName = "heroGiftTop"
	IconHeroGlobeAlt                   IconName = "heroGlobeAlt"
	IconHeroGlobeAmericas              IconName = "heroGlobeAmericas"
	IconHeroGlobeAsiaAustralia         IconName = "heroGlobeAsiaAustralia"
	IconHeroGlobeEuropeAfrica          IconName = "heroGlobeEuropeAfrica"
	IconHeroHandRaised                 IconName = "heroHandRaised"
	IconHeroHandThumbDown              IconName = "heroHandThumbDown"
	IconHeroHandThumbUp                IconName = "heroHandThumbUp"
	IconHeroHashtag                    IconName = "heroHashtag"
	IconHeroHeart                      IconName = "heroHeart"
	IconHeroHome                       IconName = "heroHome"
	IconHeroHomeModern                 IconName = "heroHomeModern"
	IconHeroIdentification             IconName = "heroIdentification"
	IconHeroInbox                      IconName = "heroInbox"
	IconHeroInboxArrowDown             IconName = "heroInboxArrowDown"
	IconHeroInboxStack                 IconName = "heroInboxStack"
	IconHeroInformationCircle          IconName = "heroInformationCircle"
	IconHeroKey                        IconName = "heroKey"
	IconHeroLanguage                   IconName = "heroLanguage"
	IconHeroLifebuoy                   IconName = "heroLifebuoy"
	IconHeroLightBulb                  IconName = "heroLightBulb"
	IconHeroLink                       IconName = "heroLink"
	IconHeroListBullet                 IconName = "heroListBullet"
	IconHeroLockClosed                 IconName = "heroLockClosed"
	IconHeroLockOpen                   IconName = "heroLockOpen"
	IconHeroMagnifyingGlass            IconName = "heroMagnifyingGlass"
	IconHeroMagnifyingGlassCircle      IconName = "heroMagnifyingGlassCircle"
	IconHeroMagnifyingGlassMinus       IconName = "heroMagnifyingGlassMinus"
	IconHeroMagnifyingGlassPlus        IconName = "heroMagnifyingGlassPlus"
	IconHeroMap                        IconName = "heroMap"
	IconHeroMapPin                     IconName = "heroMapPin"
	IconHeroMegaphone                  IconName = "heroMegaphone"
	IconHeroMicrophone                 IconName = "heroMicrophone"
	IconHeroMinus                      IconName = "heroMinus"
	IconHeroMinusCircle                IconName = "heroMinusCircle"
	IconHeroMinusSmall                 IconName = "heroMinusSmall"
	IconHeroMoon                       IconName = "heroMoon"
	IconHeroMusicalNote                IconName = "heroMusicalNote"
	IconHeroNewspaper                  IconName = "heroNewspaper"
	IconHeroNoSymbol                   IconName = "heroNoSymbol"
	IconHeroPaintBrush                 IconName = "heroPaintBrush"
	IconHeroPaperAirplane              IconName = "heroPaperAirplane"
	IconHeroPaperClip                  IconName = "heroPaperClip"
	IconHeroPauseCircle                IconName = "heroPauseCircle"
	IconHeroPencil                     IconName = "heroPencil"
	IconHeroPencilSquare               IconName = "heroPencilSquare"
	IconHeroPhone                      IconName = "heroPhone"
	IconHeroPhoneArrowDownLeft         IconName = "heroPhoneArrowDownLeft"
	IconHeroPhoneArrowUpRight          IconName = "heroPhoneArrowUpRight"
	IconHeroPhoneXMark                 IconName = "heroPhoneXMark"
	IconHeroPhoto                      IconName = "heroPhoto"
	IconHeroPlay                       IconName = "heroPlay"
	IconHeroPlayCircle                 IconName = "heroPlayCircle"
	IconHeroPlayPause                  IconName = "heroPlayPause"
	IconHeroPlus                       IconName = "heroPlus"
	IconHeroPlusCircle                 IconName = "heroPlusCircle"
	IconHeroPlusSmall                  IconName = "heroPlusSmall"
	IconHeroPower                      IconName = "heroPower"
	IconHeroPresentationChartBar       IconName = "heroPresentationChartBar"
	IconHeroPresentationChartLine      IconName = "heroPresentationChartLine"
	IconHeroPrinter                    IconName = "heroPrinter"
	IconHeroPuzzlePiece                IconName = "heroPuzzlePiece"
	IconHeroQrCode                     IconName = "heroQrCode"
	IconHeroQuestionMarkCircle         IconName = "heroQuestionMarkCircle"
	IconHeroQueueList                  IconName = "heroQueueList"
	IconHeroRadio                      IconName = "heroRadio"
	IconHeroReceiptPercent             IconName = "heroReceiptPercent"
	IconHeroReceiptRefund              IconName = "heroReceiptRefund"
	IconHeroRectangleGroup             IconName = "heroRectangleGroup"
	IconHeroRectangleStack             IconName = "heroRectangleStack"
	IconHeroRocketLaunch               IconName = "heroRocketLaunch"
	IconHeroRss                        IconName = "heroRss"
	IconHeroScale                      IconName = "heroScale"
	IconHeroScissors                   IconName = "heroScissors"
	IconHeroServer                     IconName = "heroServer"
	IconHeroServerStack                IconName = "heroServerStack"
	IconHeroShare                      IconName = "heroShare"
	IconHeroShieldCheck                IconName = "heroShieldCheck"
	IconHeroShieldExclamation          IconName = "heroShieldExclamation"
	IconHeroShoppingBag                IconName = "heroShoppingBag"
	IconHeroShoppingCart               IconName = "heroShoppingCart"
	IconHeroSignal                     IconName = "heroSignal"
	IconHeroSignalSlash                IconName = "heroSignalSlash"
	IconHeroSparkles                   IconName = "heroSparkles"
	IconHeroSpeakerWave                IconName = "heroSpeakerWave"
	IconHeroSpeakerXMark               IconName = "heroSpeakerXMark"
	IconHeroSquare2Stack               IconName = "heroSquare2Stack"
	IconHeroSquare3Stack3D             IconName = "heroSquare3Stack3D"
	IconHeroSquares2X2                 IconName = "heroSquares2X2"
	IconHeroSquaresPlus                IconName = "heroSquaresPlus"
	IconHeroStar                       IconName = "heroStar"
	IconHeroStop                       IconName = "heroStop"
	IconHeroStopCircle                 IconName = "heroStopCircle"
	IconHeroSun                        IconName = "heroSun"
	IconHeroSwatch                     IconName = "heroSwatch"
	IconHeroTableCells                 IconName = "heroTableCells"
	IconHeroTag                        IconName = "heroTag"
	IconHeroTicket                     IconName = "heroTicket"
	IconHeroTrash                      IconName = "heroTrash"
	IconHeroTrophy                     IconName = "heroTrophy"
	IconHeroTruck                      IconName = "heroTruck"
	IconHeroTv                         IconName = "heroTv"
	IconHeroUser                       IconName = "heroUser"
	IconHeroUserCircle                 IconName = "heroUserCircle"
	IconHeroUserGroup                  IconName = "heroUserGroup"
	IconHeroUserMinus                  IconName = "heroUserMinus"
	IconHeroUserPlus                   IconName = "heroUserPlus"
	IconHeroUsers                      IconName = "heroUsers"
	IconHeroVariable                   IconName = "heroVariable"
	IconHeroVideoCamera                IconName = "heroVideoCamera"
	IconHeroVideoCameraSlash           IconName = "heroVideoCameraSlash"
	IconHeroViewColumns                IconName = "heroViewColumns"
	IconHeroViewfinderCircle           IconName = "heroViewfinderCircle"
	IconHeroWallet                     IconName = "heroWallet"
	IconHeroWifi                       IconName = "heroWifi"
	IconHeroWindow                     IconName = "heroWindow"
	IconHeroWrench                     IconName = "heroWrench"
	IconHeroWrenchScrewdriver          IconName = "heroWrenchScrewdriver"
	IconHeroXCircle                    IconName = "heroXCircle"
	IconHome                           IconName = "home"
	IconHourglass                      IconName = "hourglass"
	IconId                             IconName = "id"
	IconImportIcon                     IconName = "importIcon"
	IconInfo                           IconName = "info"
	IconKey                            IconName = "key"
	IconKeyboardSolid                  IconName = "keyboardSolid"
	IconLicense                        IconName = "license"
	IconLink                           IconName = "link"
	IconLogoCloud                      IconName = "logoCloud"
	IconLogoEsc                        IconName = "logoEsc"
	IconLogoSdu                        IconName = "logoSdu"
	IconLogout                         IconName = "logout"
	IconMapMarkedAltSolid              IconName = "mapMarkedAltSolid"
	IconMemorySolid                    IconName = "memorySolid"
	IconMoon                           IconName = "moon"
	IconMove                           IconName = "move"
	IconNetworkWiredSolid              IconName = "networkWiredSolid"
	IconNewFolder                      IconName = "newFolder"
	IconNotchedCircle                  IconName = "notchedCircle"
	IconNotification                   IconName = "notification"
	IconOpen                           IconName = "open"
	IconOuterEllipsis                  IconName = "outerEllipsis"
	IconPauseSolid                     IconName = "pauseSolid"
	IconPlay                           IconName = "play"
	IconPreview                        IconName = "preview"
	IconProjects                       IconName = "projects"
	IconProperties                     IconName = "properties"
	IconPublish                        IconName = "publish"
	IconQuestionSolid                  IconName = "questionSolid"
	IconRadioChecked                   IconName = "radioChecked"
	IconRadioEmpty                     IconName = "radioEmpty"
	IconRefresh                        IconName = "refresh"
	IconRename                         IconName = "rename"
	IconResults                        IconName = "results"
	IconRulerSolid                     IconName = "rulerSolid"
	IconSearch                         IconName = "search"
	IconSensitivity                    IconName = "sensitivity"
	IconShare                          IconName = "share"
	IconShareMenu                      IconName = "shareMenu"
	IconSortAscending                  IconName = "sortAscending"
	IconSortDescending                 IconName = "sortDescending"
	IconStarEmpty                      IconName = "starEmpty"
	IconStarFilled                     IconName = "starFilled"
	IconStarRibbon                     IconName = "starRibbon"
	IconSuggestion                     IconName = "suggestion"
	IconSun                            IconName = "sun"
	IconTags                           IconName = "tags"
	IconTerminalSolid                  IconName = "terminalSolid"
	IconTrash                          IconName = "trash"
	IconUpload                         IconName = "upload"
	IconUploadFolder                   IconName = "uploadFolder"
	IconUser                           IconName = "user"
	IconUserAdmin                      IconName = "userAdmin"
	IconUserPi                         IconName = "userPi"
	IconVerified                       IconName = "verified"
	IconWarning                        IconName = "warning"
)

var AllColors = []Color{
	Color("primaryMain"),
	Color("primaryLight"),
	Color("primaryDark"),
	Color("primaryContrast"),
	Color("primaryContrastAlt"),
	Color("secondaryMain"),
	Color("secondaryLight"),
	Color("secondaryDark"),
	Color("secondaryContrast"),
	Color("secondaryContrastAlt"),
	Color("errorMain"),
	Color("errorLight"),
	Color("errorDark"),
	Color("errorContrast"),
	Color("errorContrastAlt"),
	Color("warningMain"),
	Color("warningLight"),
	Color("warningDark"),
	Color("warningContrast"),
	Color("warningContrastAlt"),
	Color("infoMain"),
	Color("infoLight"),
	Color("infoDark"),
	Color("infoContrast"),
	Color("infoContrastAlt"),
	Color("successMain"),
	Color("successLight"),
	Color("successDark"),
	Color("successContrast"),
	Color("successContrastAlt"),
	Color("backgroundDefault"),
	Color("backgroundCard"),
	Color("textPrimary"),
	Color("textSecondary"),
	Color("textDisabled"),
	Color("iconColor"),
	Color("iconColor2"),
	Color("fixedWhite"),
	Color("fixedBlack"),
	Color("favoriteColor"),
	Color("favoriteColorEmpty"),
	Color("wayfGreen"),
	Color("borderColor"),
	Color("borderColorHover"),
	Color("rowHover"),
	Color("rowActive"),
	Color("FtFolderColor"),
	Color("FtFolderColor2"),
	Color("sidebarColor"),
	Color("linkColor"),
	Color("linkColorHover"),
}

var AllIcons = []IconName{
	IconName("activity"),
	IconName("admin"),
	IconName("anglesDownSolid"),
	IconName("anglesUpSolid"),
	IconName("appFav"),
	IconName("appStore"),
	IconName("apps"),
	IconName("arrowDown"),
	IconName("backward"),
	IconName("boxChecked"),
	IconName("boxEmpty"),
	IconName("broom"),
	IconName("bug"),
	IconName("calendar"),
	IconName("check"),
	IconName("checkDouble"),
	IconName("chrono"),
	IconName("circle"),
	IconName("close"),
	IconName("cloudTryingItsBest"),
	IconName("copy"),
	IconName("cpu"),
	IconName("cubeSolid"),
	IconName("dashboard"),
	IconName("deiCLogo"),
	IconName("docs"),
	IconName("documentation"),
	IconName("download"),
	IconName("edit"),
	IconName("ellipsis"),
	IconName("extract"),
	IconName("eye"),
	IconName("favIcon"),
	IconName("fileSignatureSolid"),
	IconName("files"),
	IconName("filterSolid"),
	IconName("floppyDisk"),
	IconName("fork"),
	IconName("forward"),
	IconName("ftFavFolder"),
	IconName("ftFileSystem"),
	IconName("ftFolder"),
	IconName("ftFolderAlt"),
	IconName("ftFsFolder"),
	IconName("ftResultsFolder"),
	IconName("ftSharesFolder"),
	IconName("globeEuropeSolid"),
	IconName("grant"),
	IconName("gsd"),
	IconName("hashtag"),
	IconName("hdd"),
	IconName("heroAcademicCap"),
	IconName("heroAdjustmentsHorizontal"),
	IconName("heroAdjustmentsVertical"),
	IconName("heroArchiveBox"),
	IconName("heroArchiveBoxArrowDown"),
	IconName("heroArchiveBoxXMark"),
	IconName("heroArrowDown"),
	IconName("heroArrowDownCircle"),
	IconName("heroArrowDownLeft"),
	IconName("heroArrowDownOnSquare"),
	IconName("heroArrowDownOnSquareStack"),
	IconName("heroArrowDownRight"),
	IconName("heroArrowDownTray"),
	IconName("heroArrowLeft"),
	IconName("heroArrowLeftCircle"),
	IconName("heroArrowLeftOnRectangle"),
	IconName("heroArrowLongDown"),
	IconName("heroArrowLongLeft"),
	IconName("heroArrowLongRight"),
	IconName("heroArrowLongUp"),
	IconName("heroArrowPath"),
	IconName("heroArrowPathRoundedSquare"),
	IconName("heroArrowRight"),
	IconName("heroArrowRightCircle"),
	IconName("heroArrowRightOnRectangle"),
	IconName("heroArrowSmallDown"),
	IconName("heroArrowSmallLeft"),
	IconName("heroArrowSmallRight"),
	IconName("heroArrowSmallUp"),
	IconName("heroArrowTopRightOnSquare"),
	IconName("heroArrowTrendingDown"),
	IconName("heroArrowTrendingUp"),
	IconName("heroArrowUp"),
	IconName("heroArrowUpCircle"),
	IconName("heroArrowUpLeft"),
	IconName("heroArrowUpOnSquare"),
	IconName("heroArrowUpOnSquareStack"),
	IconName("heroArrowUpRight"),
	IconName("heroArrowUpTray"),
	IconName("heroArrowUturnDown"),
	IconName("heroArrowUturnLeft"),
	IconName("heroArrowUturnRight"),
	IconName("heroArrowUturnUp"),
	IconName("heroArrowsPointingIn"),
	IconName("heroArrowsPointingOut"),
	IconName("heroArrowsRightLeft"),
	IconName("heroArrowsUpDown"),
	IconName("heroAtSymbol"),
	IconName("heroBackspace"),
	IconName("heroBackward"),
	IconName("heroBanknotes"),
	IconName("heroBars2"),
	IconName("heroBars3"),
	IconName("heroBars3BottomLeft"),
	IconName("heroBars3BottomRight"),
	IconName("heroBars3CenterLeft"),
	IconName("heroBars4"),
	IconName("heroBarsArrowDown"),
	IconName("heroBarsArrowUp"),
	IconName("heroBattery0"),
	IconName("heroBattery100"),
	IconName("heroBattery50"),
	IconName("heroBeaker"),
	IconName("heroBell"),
	IconName("heroBellAlert"),
	IconName("heroBellSlash"),
	IconName("heroBellSnooze"),
	IconName("heroBolt"),
	IconName("heroBoltSlash"),
	IconName("heroBookOpen"),
	IconName("heroBookmark"),
	IconName("heroBookmarkSlash"),
	IconName("heroBookmarkSquare"),
	IconName("heroBriefcase"),
	IconName("heroBugAnt"),
	IconName("heroBuildingLibrary"),
	IconName("heroBuildingOffice"),
	IconName("heroBuildingOffice2"),
	IconName("heroBuildingStorefront"),
	IconName("heroCake"),
	IconName("heroCalculator"),
	IconName("heroCalendar"),
	IconName("heroCalendarDays"),
	IconName("heroCamera"),
	IconName("heroChartBar"),
	IconName("heroChartBarSquare"),
	IconName("heroChartPie"),
	IconName("heroChatBubbleBottomCenter"),
	IconName("heroChatBubbleBottomCenterText"),
	IconName("heroChatBubbleLeft"),
	IconName("heroChatBubbleLeftEllipsis"),
	IconName("heroChatBubbleLeftRight"),
	IconName("heroChatBubbleOvalLeft"),
	IconName("heroChatBubbleOvalLeftEllipsis"),
	IconName("heroCheck"),
	IconName("heroCheckBadge"),
	IconName("heroCheckCircle"),
	IconName("heroChevronDoubleDown"),
	IconName("heroChevronDoubleLeft"),
	IconName("heroChevronDoubleRight"),
	IconName("heroChevronDoubleUp"),
	IconName("heroChevronDown"),
	IconName("heroChevronLeft"),
	IconName("heroChevronRight"),
	IconName("heroChevronUp"),
	IconName("heroChevronUpDown"),
	IconName("heroCircleStack"),
	IconName("heroClipboard"),
	IconName("heroClipboardDocument"),
	IconName("heroClipboardDocumentCheck"),
	IconName("heroClipboardDocumentList"),
	IconName("heroClock"),
	IconName("heroCloud"),
	IconName("heroCloudArrowDown"),
	IconName("heroCloudArrowUp"),
	IconName("heroCodeBracket"),
	IconName("heroCodeBracketSquare"),
	IconName("heroCog"),
	IconName("heroCog6Tooth"),
	IconName("heroCog8Tooth"),
	IconName("heroCommandLine"),
	IconName("heroComputerDesktop"),
	IconName("heroCpuChip"),
	IconName("heroCreditCard"),
	IconName("heroCube"),
	IconName("heroCubeTransparent"),
	IconName("heroCurrencyBangladeshi"),
	IconName("heroCurrencyDollar"),
	IconName("heroCurrencyEuro"),
	IconName("heroCurrencyPound"),
	IconName("heroCurrencyRupee"),
	IconName("heroCurrencyYen"),
	IconName("heroCursorArrowRays"),
	IconName("heroCursorArrowRipple"),
	IconName("heroDevicePhoneMobile"),
	IconName("heroDeviceTablet"),
	IconName("heroDocument"),
	IconName("heroDocumentArrowDown"),
	IconName("heroDocumentArrowUp"),
	IconName("heroDocumentChartBar"),
	IconName("heroDocumentCheck"),
	IconName("heroDocumentDuplicate"),
	IconName("heroDocumentMagnifyingGlass"),
	IconName("heroDocumentMinus"),
	IconName("heroDocumentPlus"),
	IconName("heroDocumentText"),
	IconName("heroEnvelope"),
	IconName("heroEnvelopeOpen"),
	IconName("heroExclamationCircle"),
	IconName("heroExclamationTriangle"),
	IconName("heroEye"),
	IconName("heroEyeDropper"),
	IconName("heroEyeSlash"),
	IconName("heroFaceFrown"),
	IconName("heroFaceSmile"),
	IconName("heroFilm"),
	IconName("heroFingerPrint"),
	IconName("heroFire"),
	IconName("heroFlag"),
	IconName("heroFolder"),
	IconName("heroFolderArrowDown"),
	IconName("heroFolderMinus"),
	IconName("heroFolderOpen"),
	IconName("heroFolderPlus"),
	IconName("heroForward"),
	IconName("heroFunnel"),
	IconName("heroGif"),
	IconName("heroGift"),
	IconName("heroGiftTop"),
	IconName("heroGlobeAlt"),
	IconName("heroGlobeAmericas"),
	IconName("heroGlobeAsiaAustralia"),
	IconName("heroGlobeEuropeAfrica"),
	IconName("heroHandRaised"),
	IconName("heroHandThumbDown"),
	IconName("heroHandThumbUp"),
	IconName("heroHashtag"),
	IconName("heroHeart"),
	IconName("heroHome"),
	IconName("heroHomeModern"),
	IconName("heroIdentification"),
	IconName("heroInbox"),
	IconName("heroInboxArrowDown"),
	IconName("heroInboxStack"),
	IconName("heroInformationCircle"),
	IconName("heroKey"),
	IconName("heroLanguage"),
	IconName("heroLifebuoy"),
	IconName("heroLightBulb"),
	IconName("heroLink"),
	IconName("heroListBullet"),
	IconName("heroLockClosed"),
	IconName("heroLockOpen"),
	IconName("heroMagnifyingGlass"),
	IconName("heroMagnifyingGlassCircle"),
	IconName("heroMagnifyingGlassMinus"),
	IconName("heroMagnifyingGlassPlus"),
	IconName("heroMap"),
	IconName("heroMapPin"),
	IconName("heroMegaphone"),
	IconName("heroMicrophone"),
	IconName("heroMinus"),
	IconName("heroMinusCircle"),
	IconName("heroMinusSmall"),
	IconName("heroMoon"),
	IconName("heroMusicalNote"),
	IconName("heroNewspaper"),
	IconName("heroNoSymbol"),
	IconName("heroPaintBrush"),
	IconName("heroPaperAirplane"),
	IconName("heroPaperClip"),
	IconName("heroPauseCircle"),
	IconName("heroPencil"),
	IconName("heroPencilSquare"),
	IconName("heroPhone"),
	IconName("heroPhoneArrowDownLeft"),
	IconName("heroPhoneArrowUpRight"),
	IconName("heroPhoneXMark"),
	IconName("heroPhoto"),
	IconName("heroPlay"),
	IconName("heroPlayCircle"),
	IconName("heroPlayPause"),
	IconName("heroPlus"),
	IconName("heroPlusCircle"),
	IconName("heroPlusSmall"),
	IconName("heroPower"),
	IconName("heroPresentationChartBar"),
	IconName("heroPresentationChartLine"),
	IconName("heroPrinter"),
	IconName("heroPuzzlePiece"),
	IconName("heroQrCode"),
	IconName("heroQuestionMarkCircle"),
	IconName("heroQueueList"),
	IconName("heroRadio"),
	IconName("heroReceiptPercent"),
	IconName("heroReceiptRefund"),
	IconName("heroRectangleGroup"),
	IconName("heroRectangleStack"),
	IconName("heroRocketLaunch"),
	IconName("heroRss"),
	IconName("heroScale"),
	IconName("heroScissors"),
	IconName("heroServer"),
	IconName("heroServerStack"),
	IconName("heroShare"),
	IconName("heroShieldCheck"),
	IconName("heroShieldExclamation"),
	IconName("heroShoppingBag"),
	IconName("heroShoppingCart"),
	IconName("heroSignal"),
	IconName("heroSignalSlash"),
	IconName("heroSparkles"),
	IconName("heroSpeakerWave"),
	IconName("heroSpeakerXMark"),
	IconName("heroSquare2Stack"),
	IconName("heroSquare3Stack3D"),
	IconName("heroSquares2X2"),
	IconName("heroSquaresPlus"),
	IconName("heroStar"),
	IconName("heroStop"),
	IconName("heroStopCircle"),
	IconName("heroSun"),
	IconName("heroSwatch"),
	IconName("heroTableCells"),
	IconName("heroTag"),
	IconName("heroTicket"),
	IconName("heroTrash"),
	IconName("heroTrophy"),
	IconName("heroTruck"),
	IconName("heroTv"),
	IconName("heroUser"),
	IconName("heroUserCircle"),
	IconName("heroUserGroup"),
	IconName("heroUserMinus"),
	IconName("heroUserPlus"),
	IconName("heroUsers"),
	IconName("heroVariable"),
	IconName("heroVideoCamera"),
	IconName("heroVideoCameraSlash"),
	IconName("heroViewColumns"),
	IconName("heroViewfinderCircle"),
	IconName("heroWallet"),
	IconName("heroWifi"),
	IconName("heroWindow"),
	IconName("heroWrench"),
	IconName("heroWrenchScrewdriver"),
	IconName("heroXCircle"),
	IconName("home"),
	IconName("hourglass"),
	IconName("id"),
	IconName("importIcon"),
	IconName("info"),
	IconName("key"),
	IconName("keyboardSolid"),
	IconName("license"),
	IconName("link"),
	IconName("logoCloud"),
	IconName("logoEsc"),
	IconName("logoSdu"),
	IconName("logout"),
	IconName("mapMarkedAltSolid"),
	IconName("memorySolid"),
	IconName("moon"),
	IconName("move"),
	IconName("networkWiredSolid"),
	IconName("newFolder"),
	IconName("notchedCircle"),
	IconName("notification"),
	IconName("open"),
	IconName("outerEllipsis"),
	IconName("pauseSolid"),
	IconName("play"),
	IconName("preview"),
	IconName("projects"),
	IconName("properties"),
	IconName("publish"),
	IconName("questionSolid"),
	IconName("radioChecked"),
	IconName("radioEmpty"),
	IconName("refresh"),
	IconName("rename"),
	IconName("results"),
	IconName("rulerSolid"),
	IconName("search"),
	IconName("sensitivity"),
	IconName("share"),
	IconName("shareMenu"),
	IconName("sortAscending"),
	IconName("sortDescending"),
	IconName("starEmpty"),
	IconName("starFilled"),
	IconName("starRibbon"),
	IconName("suggestion"),
	IconName("sun"),
	IconName("tags"),
	IconName("terminalSolid"),
	IconName("trash"),
	IconName("upload"),
	IconName("uploadFolder"),
	IconName("user"),
	IconName("userAdmin"),
	IconName("userPi"),
	IconName("verified"),
	IconName("warning"),
}

var ColorSet = (func() map[Color]util.Empty {
	result := map[Color]util.Empty{}
	for _, color := range AllColors {
		result[color] = util.Empty{}
	}
	return result
})()

var IconSet = (func() map[IconName]util.Empty {
	result := map[IconName]util.Empty{}
	for _, icon := range AllIcons {
		result[icon] = util.Empty{}
	}
	return result
})()

func ColorIsValid(name string) bool {
	_, ok := ColorSet[Color(name)]
	return ok
}

func IconIsValid(name string) bool {
	_, ok := IconSet[IconName(name)]
	return ok
}
