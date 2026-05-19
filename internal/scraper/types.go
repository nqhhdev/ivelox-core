package scraper

// RawExam is unprocessed data from a scraper worker.
// Each worker fills what it can; OpenAI fills the rest.
type RawExam struct {
	SourceURL  string
	SourceName string // "archive_org" | "kmf" | "ieltsonlinetests" | "rat_sites"
	Series     string // "Cambridge 18", "IDP RAT Vol.6"
	TestNumber int
	Year       int
	Skills     map[string]*RawSkill // key: "reading"|"listening"|"writing"|"speaking"
}

type RawSkill struct {
	Skill    string
	Sections []*RawSection
}

type RawSection struct {
	Position  int
	Title     string
	Content   string   // passage text or listening transcript
	AudioURLs []string // listening audio parts
	ImageURLs []string
	Questions []*RawQuestion
}

type RawQuestion struct {
	Position       int
	Type           string   // "mcq"|"fill_blank"|"true_false"|"matching"|"short_answer"
	Prompt         string
	Options        []string
	Correct        string
	Explanation    string
	ImageURL       string
	AudioTimestamp int
	WordLimit      int
}
