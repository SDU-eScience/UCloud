package backup

import (
	"fmt"
	"strings"

	"ucloud.dk/shared/pkg/ucx"
)

func UISizeLabel(sizeBytes int64) string {
	if sizeBytes <= 0 {
		return ""
	}

	const (
		kilo = int64(1000)
		mega = 1000 * kilo
		giga = 1000 * mega
	)

	switch {
	case sizeBytes >= giga:
		return fmt.Sprintf("%.1f GB", float64(sizeBytes)/float64(giga))
	case sizeBytes >= mega:
		return fmt.Sprintf("%.0f MB", float64(sizeBytes)/float64(mega))
	case sizeBytes >= kilo:
		return fmt.Sprintf("%.0f KB", float64(sizeBytes)/float64(kilo))
	default:
		return fmt.Sprintf("%d B", sizeBytes)
	}
}

func UIBackupRow(id string, release string, sizeBytes int64, createdByNode string, trailing ...ucx.UiNode) ucx.UiNode {
	children := []ucx.UiNode{
		ucx.Text(id).Sx(ucx.SxMinWidth(0), ucx.SxWordBreak("break-word")),
	}

	details := []string{}
	if release != "" {
		details = append(details, release)
	}
	if size := UISizeLabel(sizeBytes); size != "" {
		details = append(details, size)
	}
	if createdByNode != "" {
		details = append(details, "backed up by "+createdByNode)
	}
	if len(details) > 0 {
		children = append(children, ucx.Text(strings.Join(details, " — ")).Sx(
			ucx.SxColor(ucx.ColorTextSecondary),
			ucx.SxMinWidth(0),
			ucx.SxWordBreak("break-word"),
		))
	}

	children = append(children, ucx.Box().Sx(ucx.SxFlexGrow(1)))
	children = append(children, trailing...)

	return ucx.Flex(ucx.FlexProps{Direction: "row", Gap: 12}).
		Sx(ucx.SxAlignItemsCenter, ucx.SxFlexWrapWrap, ucx.SxMinHeight(56), ucx.SxPy(8), ucx.SxBoxSizing("border-box")).
		Children(children...)
}
