package idfreeipa

import "testing"

func TestAsciiNormalization(t *testing.T) {
	parsed := parseUCloudUsername("Dan…æT")
	assertEquals(t, "Dan...ae", parsed.FirstName)
	assertEquals(t, "T", parsed.LastName)
	assertEquals(t, "dt", parsed.SuggestedUsername)
}

func TestUppercaseEdge(t *testing.T) {
	parsed := parseUCloudUsername("DANTHRANE")
	assertEquals(t, "Danthrane", parsed.FirstName)
	assertEquals(t, "Unknown", parsed.LastName)
	assertEquals(t, "danthrane", parsed.SuggestedUsername)
}

func TestSimple(t *testing.T) {
	parsed := parseUCloudUsername("DanThrane")
	assertEquals(t, "Dan", parsed.FirstName)
	assertEquals(t, "Thrane", parsed.LastName)
	assertEquals(t, "dthrane", parsed.SuggestedUsername)
}

func TestLongUsername(t *testing.T) {
	parsed := parseUCloudUsername("ThisisaverylongusernameLongerthanwewouldexpectmostpeopletohave#1234")
	assertEquals(t, "Thisisaverylongusername", parsed.FirstName)
	assertEquals(t, "Longerthanwewouldexpectmostpeopletohave", parsed.LastName)
	assertEquals(t, "tlongerthanwewouldexpectmost", parsed.SuggestedUsername)
}

func assertEquals(t *testing.T, expected, actual any) bool {
	if expected != actual {
		t.Errorf("Expected '%v', got '%v'", expected, actual)
		return false
	}
	return true
}
