package registry

import (
	"context"
	"io"
	"net/http"
	"time"

	ocid "github.com/distribution/distribution/v3"
	"github.com/distribution/reference"
	"github.com/opencontainers/go-digest"
	v1 "github.com/opencontainers/image-spec/specs-go/v1"
)

const accountingMiddlewareName = "ucloud-accounting"

type accountingRepository struct {
	repository ocid.Repository
}

func newAccountingRepository(_ context.Context, repository ocid.Repository, _ map[string]any) (ocid.Repository, error) {
	return &accountingRepository{repository: repository}, nil
}

func (r *accountingRepository) Named() reference.Named {
	return r.repository.Named()
}

func (r *accountingRepository) Manifests(ctx context.Context, options ...ocid.ManifestServiceOption) (ocid.ManifestService, error) {
	manifests, err := r.repository.Manifests(ctx, options...)
	if err != nil {
		return nil, err
	}
	return &accountingManifestService{
		ManifestService: manifests,
		repository:      r.repository.Named().Name(),
		request:         requestStateFromContext(ctx),
	}, nil
}

func (r *accountingRepository) Blobs(ctx context.Context) ocid.BlobStore {
	return &accountingBlobStore{
		BlobStore:  r.repository.Blobs(ctx),
		repository: r.repository.Named().Name(),
		request:    requestStateFromContext(ctx),
	}
}

func (r *accountingRepository) Tags(ctx context.Context) ocid.TagService {
	return &accountingTagService{
		TagService: r.repository.Tags(ctx),
		repository: r.repository.Named().Name(),
		request:    requestStateFromContext(ctx),
	}
}

type accountingBlobStore struct {
	ocid.BlobStore
	repository string
	request    *requestState
}

func (s *accountingBlobStore) Stat(ctx context.Context, dgst digest.Digest) (v1.Descriptor, error) {
	return s.BlobStore.Stat(ctx, dgst)
}

func (s *accountingBlobStore) Get(ctx context.Context, dgst digest.Digest) ([]byte, error) {
	return s.BlobStore.Get(ctx, dgst)
}

func (s *accountingBlobStore) Open(ctx context.Context, dgst digest.Digest) (io.ReadSeekCloser, error) {
	return s.BlobStore.Open(ctx, dgst)
}

func (s *accountingBlobStore) Put(ctx context.Context, mediaType string, payload []byte) (v1.Descriptor, error) {
	// TODO Extend here, should have byte count here also
	return s.BlobStore.Put(ctx, mediaType, payload)
}

func (s *accountingBlobStore) Create(ctx context.Context, options ...ocid.BlobCreateOption) (ocid.BlobWriter, error) {
	writer, err := s.BlobStore.Create(ctx, options...)
	if err != nil {
		return nil, err
	}
	return &accountingBlobWriter{BlobWriter: writer, repository: s.repository, request: s.request}, nil
}

func (s *accountingBlobStore) Resume(ctx context.Context, id string) (ocid.BlobWriter, error) {
	writer, err := s.BlobStore.Resume(ctx, id)
	if err != nil {
		return nil, err
	}
	return &accountingBlobWriter{BlobWriter: writer, repository: s.repository, request: s.request}, nil
}

func (s *accountingBlobStore) ServeBlob(ctx context.Context, w http.ResponseWriter, r *http.Request, dgst digest.Digest) error {
	return s.BlobStore.ServeBlob(ctx, w, r, dgst)
}

func (s *accountingBlobStore) Delete(ctx context.Context, dgst digest.Digest) error {
	return s.BlobStore.Delete(ctx, dgst)
}

type accountingBlobWriter struct {
	ocid.BlobWriter
	repository string
	request    *requestState
	written    int64
}

func (w *accountingBlobWriter) Write(payload []byte) (int, error) {
	n, err := w.BlobWriter.Write(payload)
	w.written += int64(n)
	return n, err
}

func (w *accountingBlobWriter) ReadFrom(reader io.Reader) (int64, error) {
	n, err := w.BlobWriter.ReadFrom(reader)
	w.written += n
	return n, err
}

func (w *accountingBlobWriter) Close() error {
	return w.BlobWriter.Close()
}

func (w *accountingBlobWriter) Size() int64 {
	return w.BlobWriter.Size()
}

func (w *accountingBlobWriter) ID() string {
	return w.BlobWriter.ID()
}

func (w *accountingBlobWriter) StartedAt() time.Time {
	return w.BlobWriter.StartedAt()
}

func (w *accountingBlobWriter) Commit(ctx context.Context, provisional v1.Descriptor) (v1.Descriptor, error) {
	// TODO Extend here, should have byte count here also
	return w.BlobWriter.Commit(ctx, provisional)
}

func (w *accountingBlobWriter) Cancel(ctx context.Context) error {
	return w.BlobWriter.Cancel(ctx)
}

type accountingManifestService struct {
	ocid.ManifestService
	repository string
	request    *requestState
}

func (s *accountingManifestService) Exists(ctx context.Context, dgst digest.Digest) (bool, error) {
	return s.ManifestService.Exists(ctx, dgst)
}

func (s *accountingManifestService) Get(ctx context.Context, dgst digest.Digest, options ...ocid.ManifestServiceOption) (ocid.Manifest, error) {
	return s.ManifestService.Get(ctx, dgst, options...)
}

func (s *accountingManifestService) Put(ctx context.Context, manifest ocid.Manifest, options ...ocid.ManifestServiceOption) (digest.Digest, error) {
	// TODO Extend here
	return s.ManifestService.Put(ctx, manifest, options...)
}

func (s *accountingManifestService) Delete(ctx context.Context, dgst digest.Digest) error {
	return s.ManifestService.Delete(ctx, dgst)
}

type accountingTagService struct {
	ocid.TagService
	repository string
	request    *requestState
}

func (s *accountingTagService) Get(ctx context.Context, tag string) (v1.Descriptor, error) {
	return s.TagService.Get(ctx, tag)
}

func (s *accountingTagService) Tag(ctx context.Context, tag string, descriptor v1.Descriptor) error {
	// TODO Extend here
	return s.TagService.Tag(ctx, tag, descriptor)
}

func (s *accountingTagService) Untag(ctx context.Context, tag string) error {
	return s.TagService.Untag(ctx, tag)
}

func (s *accountingTagService) All(ctx context.Context) ([]string, error) {
	return s.TagService.All(ctx)
}

func (s *accountingTagService) Lookup(ctx context.Context, descriptor v1.Descriptor) ([]string, error) {
	return s.TagService.Lookup(ctx, descriptor)
}

func (s *accountingTagService) List(ctx context.Context, limit int, last string) ([]string, error) {
	return s.TagService.List(ctx, limit, last)
}
