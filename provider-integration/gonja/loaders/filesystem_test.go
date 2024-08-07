package loaders_test

import (
	"io"
	"os"
	"path/filepath"

	"ucloud.dk/gonja/v2/loaders"

	. "github.com/onsi/ginkgo/v2"
	. "github.com/onsi/gomega"
)

var _ = Context("filesystem", func() {
	var (
		loader loaders.Loader
		root   = new(string)

		returnedErr = new(error)
	)

	BeforeEach(func() {
		*root = ""
	})

	JustBeforeEach(func() {
		loader = loaders.MustNewFileSystemLoader(*root)
	})

	Context("Read", func() {
		var (
			path = new(string)
			file = new(os.File)

			reader = new(io.Reader)
		)
		BeforeEach(func() {
			file = MustReturn(os.CreateTemp("", "*.filesystem"))
			MustReturn(file.WriteString("content"))
			*path = file.Name()
		})
		AfterEach(func() {
			os.Remove(file.Name())
		})
		JustBeforeEach(func() {
			*reader, *returnedErr = loader.Read(*path)
		})
		Context("when path is absolute", func() {
			It("should retrieve the expected file", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("returning a reader with the correct content")
				content, err := io.ReadAll(*reader)
				Expect(err).To(BeNil())
				Expect(string(content)).To(Equal("content"))
			})
		})
		Context("when path is relative", func() {
			BeforeEach(func() {
				os.Remove(file.Name())

				file = MustReturn(os.CreateTemp("", "*.filesystem"))
				MustReturn(file.WriteString("content"))
				*path = file.Name()
			})
			It("should retrieve the expected file", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("returning a reader with the correct content")
				content, err := io.ReadAll(*reader)
				Expect(err).To(BeNil())
				Expect(string(content)).To(Equal("content"))
			})
		})
		Context("when root is defined", func() {
			BeforeEach(func() {
				*root = MustReturn(os.MkdirTemp("", "*.filesystem"))
			})
			AfterEach(func() {
				os.RemoveAll(*root)
			})
			Context("when path is absolute", func() {
				It("should retrieve the expected file", func() {
					By("not returning an error")
					Expect(*returnedErr).To(BeNil())
					By("returning a reader with the correct content")
					content, err := io.ReadAll(*reader)
					Expect(err).To(BeNil())
					Expect(string(content)).To(Equal("content"))
				})
			})
			Context("when path is relative", func() {
				BeforeEach(func() {
					relativeFile := MustReturn(os.CreateTemp(*root, "*.filesystem"))
					MustReturn(relativeFile.WriteString("content"))

					*path = filepath.Base(relativeFile.Name())
				})
				It("should retrieve the expected file", func() {
					By("not returning an error")
					Expect(*returnedErr).To(BeNil())
					By("returning a reader with the correct content")
					content, err := io.ReadAll(*reader)
					Expect(err).To(BeNil())
					Expect(string(content)).To(Equal("content"))
				})
			})
		})
	})
	Context("Resolve", func() {
		var (
			path = new(string)
			file = new(os.File)

			returnedPath = new(string)
		)
		BeforeEach(func() {
			file = MustReturn(os.CreateTemp("", "*.filesystem"))
			MustReturn(file.WriteString("content"))
			*path = file.Name()
		})
		AfterEach(func() {
			os.Remove(file.Name())
		})
		JustBeforeEach(func() {
			*returnedPath, *returnedErr = loader.Resolve(*path)
		})
		Context("when path is absolute", func() {
			It("should retrieve the expected file", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("returning the correct path")
				Expect(string(*returnedPath)).To(Equal(file.Name()))
			})
		})
		Context("when path is relative", func() {
			BeforeEach(func() {
				os.Remove(file.Name())

				file = MustReturn(os.CreateTemp("", "*.filesystem"))
				MustReturn(file.WriteString("content"))
				*path = file.Name()
			})
			It("should retrieve the expected file", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("returning the correct path")
				Expect(string(*returnedPath)).To(Equal(file.Name()))
			})
		})
		Context("when root is defined", func() {
			BeforeEach(func() {
				*root = MustReturn(os.MkdirTemp("", "*.filesystem"))
			})
			AfterEach(func() {
				os.RemoveAll(*root)
			})
			Context("when path is absolute", func() {
				It("should retrieve the expected file", func() {
					By("not returning an error")
					Expect(*returnedErr).To(BeNil())
					By("returning the correct path")
					Expect(*returnedPath).To(Equal(file.Name()))
				})
			})
			Context("when path is relative", func() {
				var (
					relativeFile = new(os.File)
				)
				BeforeEach(func() {
					relativeFile = MustReturn(os.CreateTemp(*root, "*.filesystem"))
					MustReturn(relativeFile.WriteString("content"))

					*path = filepath.Base(relativeFile.Name())
				})
				It("should retrieve the expected file", func() {
					By("not returning an error")
					Expect(*returnedErr).To(BeNil())
					By("returning the correct path")
					Expect(*returnedPath).To(Equal(relativeFile.Name()))
				})
			})
		})
	})
	Context("Inherit", func() {
		var (
			file = new(os.File)

			newIdentifier = new(string)

			returnedLoader = new(loaders.Loader)
		)
		BeforeEach(func() {
			*newIdentifier = ""

			*root = MustReturn(os.MkdirTemp("", "*.filesystem"))

			file = MustReturn(os.CreateTemp(*root, "*.filesystem"))
			MustReturn(file.WriteString("content"))
		})
		AfterEach(func() {
			os.RemoveAll(*root)
		})
		JustBeforeEach(func() {
			*returnedLoader, *returnedErr = loader.Inherit(*newIdentifier)
		})
		Context("when no relative identifier is provided", func() {
			It("should create a new Loader without errors", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("having the loader operate relatively to the inherited root")
				_, err := (*returnedLoader).Read(filepath.Base(file.Name()))
				Expect(err).To(BeNil())
			})
		})
		Context("when a relative identifier is defined", func() {
			var (
				newRoot = new(string)
			)
			BeforeEach(func() {
				*newRoot = MustReturn(os.MkdirTemp("", "*.filesystem"))

				file = MustReturn(os.CreateTemp(*newRoot, "*.filesystem"))
				MustReturn(file.WriteString("content"))
				*newIdentifier = file.Name()
			})
			AfterEach(func() {
				os.RemoveAll(*newRoot)
			})
			It("should create a new Loader without errors", func() {
				By("not returning an error")
				Expect(*returnedErr).To(BeNil())
				By("having the loader operate relatively to the new root")
				_, err := (*returnedLoader).Read(filepath.Base(file.Name()))
				Expect(err).To(BeNil())
			})
		})
	})
})
