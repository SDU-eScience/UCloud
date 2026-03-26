package kubevirt

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"

	corek8s "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/resource"
	metak8s "k8s.io/apimachinery/pkg/apis/meta/v1"
	cfg "ucloud.dk/pkg/config"
	"ucloud.dk/pkg/integrations/k8s/filesystem"
	"ucloud.dk/pkg/integrations/k8s/shared"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

var diskGlobals struct {
	Buckets []*diskCacheBucket
	Path    string
}

type diskCacheBucket struct {
	Mu     sync.RWMutex
	Cached map[string]util.Empty // reading from the map is required to know that access to image is safe to use
}

func initDisks() {
	for i := 0; i < runtime.NumCPU(); i++ {
		diskGlobals.Buckets = append(diskGlobals.Buckets, &diskCacheBucket{
			Cached: map[string]util.Empty{},
		})
	}

	mnt := shared.ServiceConfig.FileSystem.MountPoint
	cachePath := filepath.Join(mnt, diskCacheInternalPath)
	diskGlobals.Path = cachePath
	err := os.MkdirAll(cachePath, 0700)
	if err != nil {
		log.Fatal("Failed to create cache directory for VM golden images at %s (%s)", cachePath, err)
	}
}

func diskPrepareForJob(job *orc.Job, pvcName string, owner metak8s.OwnerReference, diskSize int) *util.HttpError {
	app := &job.Status.ResolvedApplication.Value
	imageUrl := app.Invocation.Tool.Tool.Value.Description.Image
	canonicalImageName := fmt.Sprintf("%s-%s", app.Metadata.Name, app.Metadata.Version)
	canonicalImageName = strings.ReplaceAll(canonicalImageName, "/", "-")
	canonicalImageName = strings.ReplaceAll(canonicalImageName, ".", "-")

	sourcePath := filepath.Join(diskGlobals.Path, canonicalImageName)

	{
		// NOTE(Dan): This block will download or wait for image to be ready. By the end of this block, we will either
		// return an error or the image will be available and ready for use at path.
		b := diskGlobals.Buckets[util.NonCryptographicHash(canonicalImageName)%len(diskGlobals.Buckets)]

		needWrite := true
		_, err := os.Stat(sourcePath)
		if err == nil {
			b.Mu.RLock()
			_, ok := b.Cached[canonicalImageName]
			if ok {
				needWrite = false
			}
			b.Mu.RUnlock()
		}

		if needWrite {
			var herr *util.HttpError = nil

			b.Mu.Lock()
			_, ok := b.Cached[canonicalImageName]
			if !ok {
				resp, err := http.Get(imageUrl)
				if err != nil || resp.StatusCode >= 400 {
					log.Warn("Failed to download VM image from: %s", imageUrl)
					herr = util.HttpErr(http.StatusBadGateway, "unable to prepare image (1)")
				} else {
					out, err := os.OpenFile(sourcePath, os.O_RDWR|os.O_CREATE|os.O_TRUNC, 0600)
					if err != nil {
						log.Warn("Failed to create golden VM image at: %s (%s)", sourcePath, err)
						herr = util.HttpErr(http.StatusBadGateway, "unable to prepare image (2)")
					} else {
						_, err = io.Copy(out, resp.Body)
						if err != nil {
							log.Warn("Failed to copy data to golden VM image at: %s (%s)", sourcePath, err)
							herr = util.HttpErr(http.StatusBadGateway, "unable to prepare image (3)")
						}
					}
				}

				if herr == nil {
					b.Cached[canonicalImageName] = util.Empty{}
				} else {
					_ = os.Remove(sourcePath)
				}
			}
			b.Mu.Unlock()

			if herr != nil {
				return herr
			}
		}
	}

	targetDir, herr := JobFolder(job)
	if herr != nil {
		return herr
	}

	mountPoint := filepath.Clean(shared.ServiceConfig.FileSystem.MountPoint)
	targetDirClean := filepath.Clean(targetDir)
	targetDirSuffix, ok := strings.CutPrefix(targetDirClean, mountPoint+"/")
	if !ok {
		log.Warn("Failed to compute VM storage suffix. targetDir=%s mountPoint=%s", targetDir, mountPoint)
		return util.HttpErr(http.StatusInternalServerError, "failed to prepare VM image")
	}

	diskPath := filepath.Join(targetDir, "disk.img") // required by KubeVirt
	err := os.MkdirAll(targetDir, 0700)
	if err != nil {
		log.Warn("Failed to create virtual machine image: %s", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to create virtual machine images")
	}

	stdout, _, ok := util.RunCommand([]string{
		"qemu-img",
		"convert",
		"-p",
		"-O",
		"raw",
		sourcePath,
		diskPath,
	})

	if !ok {
		_ = os.Remove(diskPath)
		log.Warn("Failed to convert VM image to disk.img: %s", stdout)
		return util.HttpErr(http.StatusInternalServerError, "failed to convert VM image")
	}

	stdout, _, ok = util.RunCommand([]string{
		"qemu-img",
		"resize",
		"-f",
		"raw",
		diskPath,
		fmt.Sprintf("%dG", diskSize),
	})

	if !ok {
		_ = os.Remove(diskPath)
		log.Warn("Failed to convert VM image to disk.img: %s", stdout)
		return util.HttpErr(http.StatusInternalServerError, "failed to convert VM image")
	}

	err = os.Chown(diskPath, diskCacheUid, diskCacheUid)
	if err != nil {
		_ = os.Remove(diskPath)
		log.Warn("Failed to change owner of VM image: %s", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to convert VM image")
	}

	storageClassName := ""
	pvSource := corek8s.PersistentVolumeSource{}

	switch shared.ServiceConfig.Compute.VirtualMachines.Storage.Type {
	case cfg.KubernetesVmVolHostPath:
		targetDirHostPath := filepath.Join(
			filepath.Clean(shared.ServiceConfig.Compute.VirtualMachines.Storage.HostPath),
			targetDirSuffix,
		)

		pvSource.HostPath = &corek8s.HostPathVolumeSource{
			Path: targetDirHostPath,
		}

	case cfg.KubernetesVmVolCsi:
		csiCfg := &shared.ServiceConfig.Compute.VirtualMachines.Storage.Csi
		storageClassName = csiCfg.StorageClassName

		volumeAttributes := map[string]string{}
		for key, value := range csiCfg.VolumeAttributes {
			volumeAttributes[key] = value
		}

		const subpathPrefix = "volumeAttributes."
		subpathKey := strings.TrimPrefix(csiCfg.SubpathField, subpathPrefix)
		volumeAttributes[subpathKey] = filepath.Join(volumeAttributes[subpathKey], targetDirSuffix)

		csiSource := &corek8s.CSIPersistentVolumeSource{
			Driver:           csiCfg.Driver,
			VolumeHandle:     fmt.Sprintf("ucloud-%s", pvcName),
			VolumeAttributes: volumeAttributes,
		}

		if csiCfg.NodeStageSecret.Present {
			csiSource.NodeStageSecretRef = &corek8s.SecretReference{
				Name:      csiCfg.NodeStageSecret.Value.Name,
				Namespace: csiCfg.NodeStageSecret.Value.Namespace,
			}
		}

		pvSource.CSI = csiSource

	default:
		log.Warn("Unsupported VM storage mode: %s", shared.ServiceConfig.Compute.VirtualMachines.Storage.Type)
		return util.HttpErr(http.StatusInternalServerError, "failed to prepare VM image")
	}

	pv := &corek8s.PersistentVolume{
		ObjectMeta: metak8s.ObjectMeta{
			Name: pvcName,
		},
		Spec: corek8s.PersistentVolumeSpec{
			PersistentVolumeReclaimPolicy: corek8s.PersistentVolumeReclaimRetain,
			Capacity: map[corek8s.ResourceName]resource.Quantity{
				corek8s.ResourceStorage: resource.MustParse(fmt.Sprintf("%dGi", diskSize)),
			},
			VolumeMode:             util.Pointer(corek8s.PersistentVolumeFilesystem),
			AccessModes:            []corek8s.PersistentVolumeAccessMode{corek8s.ReadWriteMany},
			StorageClassName:       storageClassName,
			PersistentVolumeSource: pvSource,
		},
	}

	namespace := shared.ServiceConfig.Compute.Namespace
	pvc := &corek8s.PersistentVolumeClaim{
		ObjectMeta: metak8s.ObjectMeta{
			Name:            pvcName,
			Namespace:       namespace,
			OwnerReferences: []metak8s.OwnerReference{owner},
		},
		Spec: corek8s.PersistentVolumeClaimSpec{
			AccessModes: []corek8s.PersistentVolumeAccessMode{corek8s.ReadWriteMany},
			Resources: corek8s.VolumeResourceRequirements{
				Requests: map[corek8s.ResourceName]resource.Quantity{
					corek8s.ResourceStorage: resource.MustParse(fmt.Sprintf("%dGi", diskSize)),
				},
			},
			StorageClassName: util.Pointer(storageClassName),
			VolumeName:       pvcName,
			VolumeMode:       util.Pointer(corek8s.PersistentVolumeFilesystem),
		},
	}

	ctx := context.Background()

	_, err = shared.K8sClient.CoreV1().PersistentVolumes().Create(ctx, pv, metak8s.CreateOptions{})
	if err != nil {
		log.Warn("Failed to create PV: %s", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to prepare VM image")
	}

	_, err = shared.K8sClient.CoreV1().PersistentVolumeClaims(namespace).Create(ctx, pvc, metak8s.CreateOptions{})
	if err != nil {
		log.Warn("Failed to create PVC: %s", err)
		return util.HttpErr(http.StatusInternalServerError, "failed to prepare VM image")
	}

	return nil
}

func diskCleanup(job *orc.Job) {
	app := &job.Status.ResolvedApplication.Value
	canonicalImageName := fmt.Sprintf("%s-%s", app.Metadata.Name, app.Metadata.Version)
	canonicalImageName = strings.ReplaceAll(canonicalImageName, "/", "-")
	canonicalImageName = strings.ReplaceAll(canonicalImageName, ".", "-")

	internalMemberFiles, _, herr := filesystem.InitializeMemberFiles(job.Owner.CreatedBy, job.Owner.Project)
	if herr == nil {
		targetDir := filepath.Join(internalMemberFiles, "Jobs", "VirtualMachines", job.Id)
		diskPath := filepath.Join(targetDir, "disk.img") // required by KubeVirt

		_ = os.Remove(diskPath)
	}
}

const diskCacheInternalPath = "vm-golden-images"
const diskCacheUid = 107 // maps to qemu
