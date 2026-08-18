package registry

import (
	"context"
	"errors"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"

	ocid "github.com/distribution/distribution/v3"
	"github.com/distribution/distribution/v3/manifest/schema2"
	"github.com/distribution/distribution/v3/registry/storage/driver"
	"github.com/distribution/reference"
	"github.com/opencontainers/go-digest"
	v1 "github.com/opencontainers/image-spec/specs-go/v1"
	"ucloud.dk/pkg/controller"
	fnd "ucloud.dk/shared/pkg/foundation"
	"ucloud.dk/shared/pkg/log"
	orc "ucloud.dk/shared/pkg/orchestrators"
	"ucloud.dk/shared/pkg/util"
)

func ImagesValidateVariant(owner orc.ResourceOwner, image string, requireProjectAccess bool) (orc.ApplicationVariantValidateImageResponse, *util.HttpError) {
	server, err := url.Parse(Server())
	if err != nil || server.Host == "" {
		return orc.ApplicationVariantValidateImageResponse{}, util.ServerHttpError("invalid registry configuration")
	}
	prefix := server.Host + "/"
	if !strings.HasPrefix(image, prefix) {
		return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusBadRequest, "image is not hosted by this provider")
	}
	referenceText := strings.TrimPrefix(image, prefix)
	repositoryName := referenceText
	lastSlash := strings.LastIndex(repositoryName, "/")
	if digestIndex := strings.LastIndex(repositoryName, "@"); digestIndex > lastSlash {
		repositoryName = repositoryName[:digestIndex]
	} else if tagIndex := strings.LastIndex(repositoryName, ":"); tagIndex > lastSlash {
		repositoryName = repositoryName[:tagIndex]
	}
	repositoryResource, ok := controller.ContainerRepositoryRetrieveByRepository(repositoryName)
	if !ok {
		return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusNotFound, "container image repository not found")
	}
	if !controller.ResourceCanUse(owner, repositoryResource.Owner, repositoryResource.Permissions, true) {
		return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusForbidden, "container image is not readable")
	}
	if requireProjectAccess {
		if !owner.Project.Present {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusForbidden, "container image is not available to the project")
		}
		defaultRepository, defaultErr := RepositoryFindProjectDefault(owner.Project.Value)
		if defaultErr != nil || repositoryResource.Specification.Name != defaultRepository {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusForbidden, "shared variants must use the project's default repository")
		}
	}

	named, nameErr := reference.WithName(repositoryName)
	if nameErr != nil {
		return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusBadRequest, "invalid container image")
	}
	repository, repositoryErr := registryAccounting.namespace.Repository(context.Background(), named)
	if repositoryErr != nil {
		return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusNotFound, "container image not found")
	}
	var descriptor v1.Descriptor
	if digestIndex := strings.LastIndex(referenceText, "@"); digestIndex >= 0 {
		parsedDigest, digestErr := digest.Parse(referenceText[digestIndex+1:])
		if digestErr != nil {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusBadRequest, "invalid container image digest")
		}
		manifests, manifestsErr := repository.Manifests(context.Background())
		if manifestsErr != nil {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusNotFound, "container image not found")
		}
		exists, existsErr := manifests.Exists(context.Background(), parsedDigest)
		if existsErr != nil || !exists {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusNotFound, "container image not found")
		}
		descriptor.Digest = parsedDigest
	} else {
		tagIndex := strings.LastIndex(referenceText, ":")
		if tagIndex <= strings.LastIndex(referenceText, "/") {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusBadRequest, "container image must include a tag or digest")
		}
		tag := referenceText[tagIndex+1:]
		descriptor, err = repository.Tags(context.Background()).Get(context.Background(), tag)
		if err != nil {
			return orc.ApplicationVariantValidateImageResponse{}, util.HttpErr(http.StatusNotFound, "container image not found")
		}
	}
	digestImage := prefix + repositoryName + "@" + descriptor.Digest.String()
	return orc.ApplicationVariantValidateImageResponse{Image: image, ImageDigest: digestImage}, nil
}

type imageLayer struct {
	descriptor v1.Descriptor
	platforms  map[string]bool
}

type taggedImage struct {
	repository     ocid.Repository
	repositoryName string
	tag            string
	descriptor     v1.Descriptor
}

func imagesBrowse(request orc.ContainerRepositoriesProviderBrowseImagesRequest) (fnd.PageV2[orc.ContainerRepositoryImage], *util.HttpError) {
	owner := walletOwner(request.ResolvedRepository)
	lock := repositoryOwnerLock(owner)
	lock.Lock()
	defer lock.Unlock()

	ctx := context.Background()
	catalog, err := accountingCatalog(ctx)
	if err != nil {
		if _, ok := errors.AsType[driver.PathNotFoundError](err); ok {
			return fnd.PageV2[orc.ContainerRepositoryImage]{Items: make([]orc.ContainerRepositoryImage, 0)}, nil
		} else {
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusInternalServerError, "unable to browse container images")
		}
	}

	root := request.ResolvedRepository.Specification.Name
	if request.Repository.Present && !repositoryBelongsToRoot(root, request.Repository.Value) {
		return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusBadRequest, "invalid container image")
	}
	if request.Tag.Present && !request.Repository.Present {
		return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusBadRequest, "invalid container image")
	}

	groups := make([]orc.ContainerRepositoryImage, 0)
	images := make([]taggedImage, 0)
	for _, repositoryName := range catalog {
		if !repositoryBelongsToRoot(root, repositoryName) {
			continue
		}
		if request.Repository.Present && repositoryName != request.Repository.Value {
			continue
		}

		named, nameErr := reference.WithName(repositoryName)
		if nameErr != nil {
			continue
		}
		repository, repositoryErr := registryAccounting.namespace.Repository(ctx, named)
		if repositoryErr != nil {
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusInternalServerError, "unable to browse container images")
		}
		tags := repository.Tags(ctx)
		tagNames, tagsErr := tags.All(ctx)
		if tagsErr != nil {
			if _, empty := tagsErr.(ocid.ErrRepositoryUnknown); empty {
				continue
			}
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusInternalServerError, "unable to browse container images")
		}
		sort.Strings(tagNames)
		if !request.Repository.Present {
			if len(tagNames) == 0 {
				continue
			}
			groups = append(groups, orc.ContainerRepositoryImage{
				Kind:       "IMAGE",
				Name:       strings.TrimPrefix(strings.TrimPrefix(repositoryName, root), "/"),
				Repository: repositoryName,
				TagCount:   len(tagNames),
				Layers:     []orc.ContainerRepositoryImageLayer{},
			})
			continue
		}
		for _, tag := range tagNames {
			if request.Tag.Present && tag != request.Tag.Value {
				continue
			}
			descriptor, descriptorErr := tags.Get(ctx, tag)
			if descriptorErr != nil {
				return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusInternalServerError, "unable to browse container images")
			}
			images = append(images, taggedImage{
				repository:     repository,
				repositoryName: repositoryName,
				tag:            tag,
				descriptor:     descriptor,
			})
		}
	}

	itemsPerPage := request.ItemsPerPage
	if itemsPerPage <= 0 || itemsPerPage > 250 {
		itemsPerPage = 100
	}
	offset := 0
	entryCount := len(images)
	if !request.Repository.Present {
		entryCount = len(groups)
	}
	if request.Next.Present {
		parsed, parseErr := strconv.Atoi(request.Next.Value)
		if parseErr != nil || parsed < 0 || parsed > entryCount {
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusBadRequest, "invalid pagination token")
		}
		offset = parsed
	}
	end := min(offset+itemsPerPage, entryCount)
	next := util.Option[string]{}
	if end < entryCount {
		next = util.OptValue(strconv.Itoa(end))
	}
	if !request.Repository.Present {
		return fnd.PageV2[orc.ContainerRepositoryImage]{
			Items:        util.NonNilSlice(groups[offset:end]),
			ItemsPerPage: itemsPerPage,
			Next:         next,
		}, nil
	}
	pageItems := make([]orc.ContainerRepositoryImage, 0, end-offset)
	for _, image := range images[offset:end] {
		described, imageErr := imagesDescribe(ctx, image.repository, image.repositoryName, image.tag, image.descriptor)
		if imageErr != nil {
			return fnd.PageV2[orc.ContainerRepositoryImage]{}, util.HttpErr(http.StatusInternalServerError, "unable to inspect container image")
		}
		pageItems = append(pageItems, described)
	}
	return fnd.PageV2[orc.ContainerRepositoryImage]{
		Items:        util.NonNilSlice(pageItems),
		ItemsPerPage: itemsPerPage,
		Next:         next,
	}, nil
}

func imagesDescribe(
	ctx context.Context,
	repository ocid.Repository,
	repositoryName, tag string,
	descriptor v1.Descriptor,
) (orc.ContainerRepositoryImage, error) {
	seen := map[digest.Digest]bool{}
	layers := map[digest.Digest]*imageLayer{}
	total := int64(0)
	mediaType, err := imagesDescribeManifest(ctx, repository, descriptor.Digest, "", seen, layers, &total)
	if err != nil {
		return orc.ContainerRepositoryImage{}, err
	}

	resultLayers := make([]orc.ContainerRepositoryImageLayer, 0, len(layers))
	for _, layer := range layers {
		platforms := make([]string, 0, len(layer.platforms))
		for platform := range layer.platforms {
			if platform != "" {
				platforms = append(platforms, platform)
			}
		}
		sort.Strings(platforms)
		resultLayers = append(resultLayers, orc.ContainerRepositoryImageLayer{
			Digest:      layer.descriptor.Digest.String(),
			MediaType:   layer.descriptor.MediaType,
			SizeInBytes: layer.descriptor.Size,
			Platforms:   util.NonNilSlice(platforms),
		})
	}
	sort.Slice(resultLayers, func(i, j int) bool { return resultLayers[i].Digest < resultLayers[j].Digest })

	return orc.ContainerRepositoryImage{
		Kind:        "TAG",
		Repository:  repositoryName,
		Tag:         tag,
		Digest:      descriptor.Digest.String(),
		MediaType:   mediaType,
		SizeInBytes: total,
		Layers:      resultLayers,
	}, nil
}

func imagesDescribeManifest(
	ctx context.Context,
	repository ocid.Repository,
	manifestDigest digest.Digest,
	platform string,
	seen map[digest.Digest]bool,
	layers map[digest.Digest]*imageLayer,
	total *int64,
) (string, error) {
	if !seen[manifestDigest] {
		descriptor, err := registryAccounting.namespace.BlobStatter().Stat(ctx, manifestDigest)
		if err != nil {
			return "", err
		}

		*total += descriptor.Size
		seen[manifestDigest] = true
	}

	manifests, err := repository.Manifests(ctx)
	if err != nil {
		return "", err
	}
	manifest, err := manifests.Get(ctx, manifestDigest)
	if err != nil {
		return "", err
	}
	mediaType, _, err := manifest.Payload()
	if err != nil {
		return "", err
	}

	for _, referenced := range manifest.References() {
		exists, existsErr := manifests.Exists(ctx, referenced.Digest)
		if existsErr != nil {
			return "", existsErr
		}
		if exists {
			childPlatform := imagesPlatformName(referenced.Platform)
			if childPlatform == "" {
				childPlatform = platform
			}
			if !seen[referenced.Digest] {
				if _, walkErr := imagesDescribeManifest(ctx, repository, referenced.Digest, childPlatform, seen, layers, total); walkErr != nil {
					return "", walkErr
				}
			}
			continue
		}

		blobDescriptor, statErr := registryAccounting.namespace.BlobStatter().Stat(ctx, referenced.Digest)
		if statErr != nil {
			return "", statErr
		}
		if !seen[referenced.Digest] {
			*total += blobDescriptor.Size
			seen[referenced.Digest] = true
		}
		if imagesIsLayerMediaType(referenced.MediaType) {
			layer := layers[referenced.Digest]
			if layer == nil {
				referenced.Size = blobDescriptor.Size
				layer = &imageLayer{descriptor: referenced, platforms: map[string]bool{}}
				layers[referenced.Digest] = layer
			}
			layer.platforms[platform] = true
		}
	}
	return mediaType, nil
}

func imagesDelete(request orc.ContainerRepositoriesProviderDeleteImageRequest) *util.HttpError {
	if request.Image != "" {
		return imagesDeleteReference(request.Image)
	}
	return imagesDeleteTag(request.ResolvedRepository, request.Repository, request.Tag, false)
}

func imagesDeleteTag(resolvedRepository orc.ContainerRepository, repositoryName, tag string, missingOkay bool) *util.HttpError {
	root := resolvedRepository.Specification.Name
	if !repositoryBelongsToRoot(root, repositoryName) || strings.TrimSpace(tag) == "" {
		return util.HttpErr(http.StatusBadRequest, "invalid container image")
	}

	named, err := reference.WithName(repositoryName)
	if err != nil {
		return util.HttpErr(http.StatusBadRequest, "invalid container image")
	}
	repository, err := registryAccounting.namespace.Repository(context.Background(), named)
	if err != nil {
		if missingOkay {
			return nil
		}
		return util.HttpErr(http.StatusNotFound, "container image not found")
	}
	ctx := context.Background()
	tags := repository.Tags(ctx)
	if _, err = tags.Get(ctx, tag); err != nil {
		if missingOkay {
			return nil
		}
		return util.HttpErr(http.StatusNotFound, "container image not found")
	}
	if err = tags.Untag(ctx, tag); err != nil {
		log.Warn("Unable to delete container image %s:%s: %v", repositoryName, tag, err)
		return util.HttpErr(http.StatusInternalServerError, "unable to delete container image")
	}
	return nil
}

func imagesDeleteReference(image string) *util.HttpError {
	server, err := url.Parse(Server())
	if err != nil || server.Host == "" {
		return util.ServerHttpError("invalid registry configuration")
	}
	prefix := server.Host + "/"
	if !strings.HasPrefix(image, prefix) {
		return util.HttpErr(http.StatusBadRequest, "image is not hosted by this provider")
	}
	referenceText := strings.TrimPrefix(image, prefix)
	lastSlash := strings.LastIndex(referenceText, "/")
	tagIndex := strings.LastIndex(referenceText, ":")
	if tagIndex <= lastSlash {
		return util.HttpErr(http.StatusBadRequest, "container image must include a tag")
	}
	repositoryName := referenceText[:tagIndex]
	repository, ok := controller.ContainerRepositoryRetrieveByRepository(repositoryName)
	if !ok {
		return nil
	}
	return imagesDeleteTag(repository, repositoryName, referenceText[tagIndex+1:], true)
}

func imagesIsLayerMediaType(mediaType string) bool {
	mediaType = strings.ToLower(mediaType)
	return strings.Contains(mediaType, "layer") || mediaType == schema2.MediaTypeLayer
}

func imagesPlatformName(platform *v1.Platform) string {
	if platform == nil {
		return ""
	}
	result := platform.OS + "/" + platform.Architecture
	if platform.Variant != "" {
		result += "/" + platform.Variant
	}
	return strings.Trim(result, "/")
}
