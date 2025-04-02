package containers

import (
	"encoding/json"
	"errors"
	"fmt"
	lru "github.com/hashicorp/golang-lru/v2/expirable"
	"net/http"
	"regexp"
	"strings"
	"time"
	"ucloud.dk/pkg/util"
)

var cache = lru.NewLRU[string, uint64](4096, nil, 30*time.Minute)

// EstimateCompressedImageSize fetches the compressed size of an image from its registry.
func EstimateCompressedImageSize(name string) (uint64, error) {
	result, ok := cache.Get(name)
	if ok {
		return result, nil
	}

	result, err := EstimateCompressedImageSizeWithoutCache(name)
	if err != nil {
		return 0, err
	} else {
		cache.Add(name, result)
		return result, nil
	}
}

func EstimateCompressedImageSizeWithoutCache(name string) (uint64, error) {
	registry, repo, tag := parseImageName(name)
	if tag == "" {
		tag = "latest"
	}

	authURL, service, scope, err := getAuthDetails(registry, repo, tag)
	if err != nil {
		return 0, err
	}

	token, err := getAuthToken(authURL, service, scope)
	if err != nil {
		return 0, err
	}

	size, err := fetchImageSize(registry, repo, tag, token)
	if err != nil {
		return 0, err
	}

	return size, nil
}

func parseImageName(name string) (registry, repo, tag string) {
	parts := strings.Split(name, "/")
	if len(parts) == 1 {
		// Official Docker images (e.g., "ubuntu")
		registry = "registry-1.docker.io"
		repo = "library/" + parts[0]
	} else if len(parts) == 2 {
		// Docker Hub user/org images (e.g., "nginx/nginx")
		registry = "registry-1.docker.io"
		repo = parts[0] + "/" + parts[1]
	} else {
		// Custom registry (e.g., "myregistry.com/nginx/nginx")
		registry = parts[0]
		repo = strings.Join(parts[1:], "/")
	}

	// Extract tag if provided
	if strings.Contains(repo, ":") {
		segments := strings.Split(repo, ":")
		repo, tag = segments[0], segments[1]
	}

	return registry, repo, tag
}

var authRegex = regexp.MustCompile(`Bearer realm="([^"]+)",service="([^"]+)",scope="([^"]+)"`)

func getAuthDetails(registry, repo, tag string) (authURL, service, scope string, err error) {
	url := fmt.Sprintf("https://%s/v2/%s/manifests/%s", registry, repo, tag)
	req, _ := http.NewRequest("HEAD", url, nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", "", "", err
	}
	defer util.SilentClose(resp.Body)

	if resp.StatusCode == http.StatusOK {
		return "", "", "", nil
	}

	if resp.StatusCode != http.StatusUnauthorized {
		return "", "", "", fmt.Errorf("unexpected response: %d", resp.StatusCode)
	}

	authHeader := resp.Header.Get("WWW-Authenticate")
	if authHeader == "" {
		return "", "", "", errors.New("no WWW-Authenticate header found")
	}

	matches := authRegex.FindStringSubmatch(authHeader)
	if len(matches) != 4 {
		return "", "", "", errors.New("failed to parse WWW-Authenticate header")
	}

	return matches[1], matches[2], matches[3], nil
}

func getAuthToken(authURL, service, scope string) (string, error) {
	if authURL == "" {
		return "", nil // No authentication required
	}

	url := fmt.Sprintf("%s?service=%s&scope=%s", authURL, service, scope)
	resp, err := http.Get(url)
	if err != nil {
		return "", err
	}
	defer util.SilentClose(resp.Body)

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("failed to get token: %d", resp.StatusCode)
	}

	var result struct {
		Token string `json:"token"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", err
	}

	return result.Token, nil
}

func fetchImageSize(registry, repo, tag, token string) (uint64, error) {
	url := fmt.Sprintf("https://%s/v2/%s/manifests/%s", registry, repo, tag)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("Accept", "application/vnd.docker.distribution.manifest.v2+json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer util.SilentClose(resp.Body)

	if resp.StatusCode != http.StatusOK {
		return 0, fmt.Errorf("failed to fetch manifest: %d", resp.StatusCode)
	}

	var manifest struct {
		Layers []struct {
			Size uint64 `json:"size"`
		} `json:"layers"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&manifest); err != nil {
		return 0, err
	}

	var totalSize uint64
	for _, layer := range manifest.Layers {
		totalSize += layer.Size
	}

	return totalSize, nil
}
