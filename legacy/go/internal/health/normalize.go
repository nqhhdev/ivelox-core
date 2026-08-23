package health

import (
	"strings"
	"unicode"

	"golang.org/x/text/runes"
	"golang.org/x/text/transform"
	"golang.org/x/text/unicode/norm"
)

// NormalizeFoodName trims, lowercases, strips Vietnamese diacritics, and collapses spaces.
func NormalizeFoodName(s string) string {
	s = strings.TrimSpace(s)
	s = strings.ToLower(s)
	// đ does not decompose under NFD
	s = strings.ReplaceAll(s, "đ", "d")

	t := transform.Chain(norm.NFD, runes.Remove(runes.In(unicode.Mn)), norm.NFC)
	result, _, err := transform.String(t, s)
	if err != nil {
		result = s
	}

	return strings.Join(strings.Fields(result), " ")
}
