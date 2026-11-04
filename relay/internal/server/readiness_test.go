package server

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestReadinessRequiresUsableICEConfiguration(t *testing.T) {
	tests := []struct {
		name   string
		config Config
		status int
	}{
		{name: "unconfigured", config: DefaultConfig(), status: http.StatusServiceUnavailable},
		{name: "valid STUN", config: Config{STUNURLs: []string{"stun:stun.example.test:3478"}}, status: http.StatusNoContent},
		{name: "invalid STUN", config: Config{STUNURLs: []string{"stun:missing-port"}}, status: http.StatusServiceUnavailable},
		{name: "TURN without secret", config: Config{TURNURLs: []string{"turn:turn.example.test:3478"}}, status: http.StatusServiceUnavailable},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			service := New(test.config)
			defer service.Close()
			request := httptest.NewRequest(http.MethodGet, "/readyz", nil)
			response := httptest.NewRecorder()
			service.Handler().ServeHTTP(response, request)
			if response.Code != test.status {
				t.Fatalf("readiness status = %d, want %d; body=%s",
					response.Code, test.status, response.Body.String())
			}
			if response.Header().Get("Cache-Control") != "no-store" {
				t.Fatal("readiness response must not be cached")
			}
		})
	}
}

func TestLivenessDoesNotRequireICEConfiguration(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	request := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	response := httptest.NewRecorder()
	service.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusNoContent {
		t.Fatalf("liveness status = %d, want %d", response.Code, http.StatusNoContent)
	}
}
