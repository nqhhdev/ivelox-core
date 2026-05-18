package http_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

func TestRouter_HealthCheck(t *testing.T) {
	repo := &fakeUserRepo{users: nil}
	uc := usecase.NewAuthUsecase(repo, &fakeAuthProvider{})
	r := httpdelivery.NewRouter("http://localhost:5173", "test-secret-key-that-is-long-enough", uc)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
}
