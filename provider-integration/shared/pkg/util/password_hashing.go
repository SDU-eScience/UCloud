package util

import (
	"crypto/rand"
	"crypto/sha512"
	"crypto/subtle"

	"golang.org/x/crypto/pbkdf2"
)

const (
	saltLength = 16
	iterations = 10000
	keyLength  = 256 // bits
)

type HashedPasswordAndSalt struct {
	HashedPassword []byte
	Salt           []byte
}

func GenSalt() []byte {
	salt := make([]byte, saltLength)
	_, err := rand.Read(salt)
	if err != nil {
		panic(err)
	}
	return salt
}

// HashPassword hashes a password with the given salt, or generates a new salt if not provided
func HashPassword(password string, salt []byte) HashedPasswordAndSalt {
	if salt == nil {
		salt = GenSalt()
	}

	passwordBytes := []byte(password)
	hashed := pbkdf2.Key(passwordBytes, salt, iterations, keyLength/8, sha512.New)

	return HashedPasswordAndSalt{
		HashedPassword: hashed,
		Salt:           salt,
	}
}

// CheckPassword compares the stored correctPassword with the hash of the plainPassword
func CheckPassword(correctPassword, salt []byte, plainPassword string) bool {
	incoming := HashPassword(plainPassword, salt)
	// Use constant time comparison
	if subtle.ConstantTimeCompare(incoming.HashedPassword, correctPassword) == 1 {
		return true
	}
	return false
}
