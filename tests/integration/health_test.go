//go:build integration

package integration_test

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/google/uuid"
	httpdelivery "github.com/nqhhdev/ivelox-core/internal/delivery/http"
	"github.com/nqhhdev/ivelox-core/internal/domain"
	"github.com/nqhhdev/ivelox-core/internal/usecase"
)

type stubUserRepo struct{}

func (s *stubUserRepo) GetByID(_ uuid.UUID) (*domain.User, error) { return nil, nil }

func TestHealthEndpoint(t *testing.T) {
	uc := usecase.NewAuthUsecase(&stubUserRepo{})
	r := httpdelivery.NewRouter("http://localhost:5173", "test-secret", uc)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	w := httptest.NewRecorder()
	r.ServeHTTP(w, req)

	if w.Code != 200 {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
}
