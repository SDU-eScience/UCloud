package kvdb

import (
	"bytes"
	"encoding/gob"
	"io"
	"os"
	"path/filepath"
	"sync"
	"time"
	"ucloud.dk/pkg/log"
	"ucloud.dk/pkg/util"
)

var dbFile string
var db map[string]any
var mutex = sync.Mutex{}

// NOTE(Dan): This is a simple and temporary persistent KV store. We will use this until we have a proper database
// implementation in place.

func Init(databaseFile string) bool {
	dbFile = databaseFile
	file, err := os.OpenFile(databaseFile, os.O_CREATE|os.O_RDWR, 0600)
	if err != nil {
		log.Warn("Failed to open/create database file at %v: %v", databaseFile, err)
		return false
	}
	defer util.SilentClose(file)

	fileData, err := io.ReadAll(file)
	if err != nil {
		log.Warn("Failed to read database file at %v: %v", databaseFile, err)
		return false
	}

	if len(fileData) > 0 {
		err = gob.NewDecoder(bytes.NewBuffer(fileData)).Decode(&db)
		if err != nil {
			log.Warn(
				"Failed to decode database file at %v: %v. You might need to delete this file.",
				databaseFile,
				err,
			)
			return false
		}
	} else {
		db = make(map[string]any)
	}

	go func() {
		defer util.SilentClose(file)
		for util.IsAlive {
			flush()
			time.Sleep(10 * time.Second)
		}
	}()

	return true
}

func flush() {
	file, err := os.CreateTemp(filepath.Dir(dbFile), "temp_*.db")
	if err != nil {
		log.Warn("Failed to create temp file: %v", err)
		return
	}

	buffer := bytes.Buffer{}
	err = gob.NewEncoder(&buffer).Encode(db)
	if err != nil {
		util.SilentClose(file)
		log.Warn("Failed to encode database file: %v", err)
		return
	}

	_, err = file.Write(buffer.Bytes())
	util.SilentClose(file)
	if err != nil {
		log.Warn("Failed to write temp db file: %v", err)
		return
	}

	err = os.Rename(file.Name(), dbFile)
	if err != nil {
		log.Warn("Failed to replace db file: %v", err)
		return
	}
}

func Set(key string, value any) {
	mutex.Lock()
	defer mutex.Unlock()
	db[key] = value
}

func Get(key string) (any, bool) {
	mutex.Lock()
	defer mutex.Unlock()
	value, ok := db[key]
	return value, ok
}
