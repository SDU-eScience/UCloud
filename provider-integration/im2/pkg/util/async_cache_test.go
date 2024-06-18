package util

import (
	"fmt"
	"sync"
	"testing"
	"time"
)

func TestSimpleUsage(t *testing.T) {
	cache := NewCache[string, int](30 * time.Minute)

	counter := 0
	getter := func() (int, error) {
		counter += 1
		return counter, nil
	}

	for i := 0; i < 10; i++ {
		res, ok := cache.Get("test", getter)
		if !ok {
			t.Error("should have returned true")
		}
		if res != 1 {
			t.Errorf("should have returned 1 but returned %d", res)
		}
	}
}

func TestExpiration(t *testing.T) {
	cache := NewCache[string, int](200 * time.Millisecond)

	counter := 0
	getter := func() (int, error) {
		counter += 1
		return counter, nil
	}

	for i := 0; i < 10; i++ {
		res, ok := cache.Get("test", getter)
		if !ok {
			t.Error("should have returned true")
		}
		if res != 1 {
			t.Errorf("should have returned 1 but returned %d", res)
		}
	}

	time.Sleep(500 * time.Millisecond)
	for i := 0; i < 10; i++ {
		res, ok := cache.Get("test", getter)
		if !ok {
			t.Error("should have returned true")
		}
		if res != 2 {
			t.Errorf("should have returned 2 but returned %d", res)
		}
	}
}

func TestConcurrency(t *testing.T) {
	cache := NewCache[string, int](10000 * time.Hour)

	counter := 0
	getter := func() (int, error) {
		counter += 1
		return counter, nil
	}

	for iterations := 0; iterations < 10; iterations++ {
		wg := sync.WaitGroup{}
		wg.Add(100)

		for coroutine := 0; coroutine < 100; coroutine++ {
			go func() {
				for i := 0; i < 1000; i++ {
					res, ok := cache.Get("test", getter)
					if !ok {
						t.Error("should have returned true")
					}
					if res != 1 {
						t.Errorf("should have returned 1 but returned %d", res)
					}
				}

				wg.Done()
			}()
		}

		wg.Wait()
	}
}

func TestFailure(t *testing.T) {
	cache := NewCache[string, int](1000 * time.Millisecond)

	go func() {
		res, ok := cache.Get("test", func() (int, error) {
			time.Sleep(250 * time.Millisecond)
			return 0, fmt.Errorf("synthetic failure")
		})

		if ok {
			t.Errorf("Should have failed but got back %v", res)
		}
	}()

	// Wait a bit to ensure that we are hitting the synthetic failure
	time.Sleep(100 * time.Millisecond)

	counter := 0
	getter := func() (int, error) {
		counter += 1
		return counter, nil
	}

	res, ok := cache.Get("test", getter)
	if ok {
		t.Errorf("Should have failed but got back %v", res)
	}

	time.Sleep(1 * time.Second)
	res, ok = cache.Get("test", getter)
	if !ok {
		t.Errorf("Should have succeeded but got back %v", res)
	}
}
