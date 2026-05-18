package domain

// AuthProvider abstracts the Supabase Auth API.
type AuthProvider interface {
	SignUp(email, password string) (*AuthResult, error)
	SignIn(email, password string) (*AuthResult, error)
	RefreshToken(refreshToken string) (*AuthResult, error)
	SignOut(accessToken string) error
}

type AuthResult struct {
	AccessToken       string
	RefreshToken      string
	UserID            string
	Email             string
	NeedsVerification bool
}
