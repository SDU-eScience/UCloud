package foundation

import "testing"

func TestAsciiNormalization(t *testing.T) {
	parsed := ParseUCloudUsername("Dan…æT")
	assertEquals(t, "Danae", parsed.FirstName)
	assertEquals(t, "T", parsed.LastName)
	assertEquals(t, "danae", parsed.SuggestedUsername)
}

func TestAsciiNormalization2(t *testing.T) {
	parsed := ParseUCloudUsername("JensHågensen#5128")
	assertEquals(t, "Jens", parsed.FirstName)
	assertEquals(t, "Hagensen", parsed.LastName)
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

	// While obviously not a real name (initials are ABCDEF), it is actually representative of names we see in practice.
	parsed = ParseUCloudUsername("Albert-BernardCarlsenDanielsenEriksenFriedrich#5234")
	assertEquals(t, "Albert-Bernard", parsed.FirstName)
	assertEquals(t, "Friedrich", parsed.LastName)
	assertEquals(t, "afriedrich", parsed.SuggestedUsername)
}

func TestUserNamesWithDots(t *testing.T) {
	parsed := ParseUCloudUsername("DanT.#4132")
	assertEquals(t, "dan", parsed.SuggestedUsername)

	parsed = ParseUCloudUsername("D.Thrane#4132")
	assertEquals(t, "dthrane", parsed.SuggestedUsername)

	parsed = ParseUCloudUsername("CarlA.B.#5348")
	assertEquals(t, "carl", parsed.SuggestedUsername)

	parsed = ParseUCloudUsername("CarlA.B.Jensen#5348")
	assertEquals(t, "cjensen", parsed.SuggestedUsername)
}

func TestWeirdUserNames(t *testing.T) {
	parsed := ParseUCloudUsername("-Lars#5361")
	assertEquals(t, "lars", parsed.SuggestedUsername)
}

func TestEmptyName(t *testing.T) {
	parsed := ParseUCloudUsername("")
	if len(parsed.SuggestedUsername) == 0 {
		t.Errorf("Expected a non-empty suggested username")
	}
}

func assertEquals(t *testing.T, expected, actual any) bool {
	if expected != actual {
		t.Errorf("Expected '%v', got '%v'", expected, actual)
		return false
	}
	return true
}
