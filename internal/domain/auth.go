package domain

// AuthProvider abstracts the Supabase Auth API (register, login).
type AuthProvider interface {
	SignUp(email, password string) (*AuthResult, error)
	SignIn(email, password string) (*AuthResult, error)
}

type AuthResult struct {
	AccessToken       string
	RefreshToken      string
	UserID            string
	Email             string
	NeedsVerification bool
}
