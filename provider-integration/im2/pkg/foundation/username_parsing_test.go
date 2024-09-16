package foundation

import "testing"

func TestAsciiNormalization(t *testing.T) {
	parsed := ParseUCloudUsername("Dan…æT")
	assertEquals(t, "Dan...ae", parsed.FirstName)
	assertEquals(t, "T", parsed.LastName)
	assertEquals(t, "dt", parsed.SuggestedUsername)
}

func TestAsciiNormalization2(t *testing.T) {
	parsed := ParseUCloudUsername("JensHågensen#5128")
	assertEquals(t, "Jens", parsed.FirstName)
	assertEquals(t, "Haagensen", parsed.LastName)
	assertEquals(t, "jhagensen", parsed.SuggestedUsername)
}

func TestUppercaseEdge(t *testing.T) {
	parsed := ParseUCloudUsername("DANTHRANE")
	assertEquals(t, "Danthrane", parsed.FirstName)
	assertEquals(t, "Unknown", parsed.LastName)
	assertEquals(t, "danthrane", parsed.SuggestedUsername)
}

func TestSimple(t *testing.T) {
	parsed := ParseUCloudUsername("DanThrane")
	assertEquals(t, "Dan", parsed.FirstName)
	assertEquals(t, "Thrane", parsed.LastName)
	assertEquals(t, "dthrane", parsed.SuggestedUsername)
}

func TestLongUsername(t *testing.T) {
	parsed := ParseUCloudUsername("ThisisaverylongusernameLongerthanwewouldexpectmostpeopletohave#1234")
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
