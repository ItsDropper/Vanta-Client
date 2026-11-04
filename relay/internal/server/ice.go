package server

import (
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"fmt"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type iceServer struct {
	URLs       []string `json:"urls"`
	Username   string   `json:"username,omitempty"`
	Credential string   `json:"credential,omitempty"`
}

type iceServerResponse struct {
	ICEServers []iceServer `json:"iceServers"`
}

// hostICEServers returns configuration only for an approved, live request.
// TURN credentials are short-lived coturn REST credentials and are never
// persisted or sent to an unauthenticated invite-code holder.
func (s *Server) hostICEServers(w http.ResponseWriter, r *http.Request) {
	if !s.allowRequest(r) {
		writeError(w, http.StatusTooManyRequests, "rate_limited")
		return
	}
	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil || !validBearer(r, current.hostToken) {
		writeError(w, http.StatusUnauthorized, "invalid_host_token")
		return
	}
	current.mu.Lock()
	request := current.requests[r.PathValue("requestID")]
	current.mu.Unlock()
	if request == nil {
		writeError(w, http.StatusNotFound, "request_not_found")
		return
	}
	request.mu.Lock()
	approved := request.state == "approved" && time.Now().Before(request.expiresAt)
	request.mu.Unlock()
	if !approved {
		writeError(w, http.StatusForbidden, "guest_not_approved")
		return
	}
	s.writeICEServers(w)
}

// guestICEServers uses the guest's request-scoped token. Approval is checked
// again here; knowing a room code never grants access to ICE credentials.
func (s *Server) guestICEServers(w http.ResponseWriter, r *http.Request) {
	if !s.allowRequest(r) {
		writeError(w, http.StatusTooManyRequests, "rate_limited")
		return
	}
	_, request, ok := s.authorizeGuest(r)
	if !ok || request.id != r.PathValue("requestID") {
		writeError(w, http.StatusUnauthorized, "invalid_guest_token")
		return
	}
	request.mu.Lock()
	approved := request.state == "approved" && time.Now().Before(request.expiresAt)
	request.mu.Unlock()
	if !approved {
		writeError(w, http.StatusForbidden, "guest_not_approved")
		return
	}
	s.writeICEServers(w)
}

func (s *Server) writeICEServers(w http.ResponseWriter) {
	servers, err := s.buildICEServers()
	if err != nil {
		writeError(w, http.StatusServiceUnavailable, "ice_service_misconfigured")
		return
	}
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("Pragma", "no-cache")
	writeJSON(w, http.StatusOK, iceServerResponse{ICEServers: servers})
}

func (s *Server) buildICEServers() ([]iceServer, error) {
	result := make([]iceServer, 0, 2)
	if len(s.config.STUNURLs) > 0 {
		urls, err := validateICEURLs(s.config.STUNURLs, "stun", "stuns")
		if err != nil {
			return nil, err
		}
		result = append(result, iceServer{URLs: urls})
	}
	if len(s.config.TURNURLs) > 0 {
		if strings.TrimSpace(s.config.TURNSharedSecret) == "" {
			return nil, fmt.Errorf("TURN URLs are configured without a shared secret")
		}
		urls, err := validateICEURLs(s.config.TURNURLs, "turn", "turns")
		if err != nil {
			return nil, err
		}
		nonce, err := randomToken(12)
		if err != nil {
			return nil, err
		}
		expiresAt := time.Now().Add(s.config.TURNCredentialTTL)
		username := fmt.Sprintf("%d:%s", expiresAt.Unix(), nonce)
		mac := hmac.New(sha1.New, []byte(s.config.TURNSharedSecret))
		_, _ = mac.Write([]byte(username))
		credential := base64.StdEncoding.EncodeToString(mac.Sum(nil))
		result = append(result, iceServer{
			URLs: urls, Username: username, Credential: credential,
		})
	}
	return result, nil
}

func validateICEURLs(values []string, schemes ...string) ([]string, error) {
	allowed := make(map[string]bool, len(schemes))
	for _, scheme := range schemes {
		allowed[scheme] = true
	}
	result := make([]string, 0, len(values))
	for _, raw := range values {
		value := strings.TrimSpace(raw)
		colon := strings.IndexByte(value, ':')
		if colon <= 0 || !allowed[strings.ToLower(value[:colon])] || len(value) > 512 {
			return nil, fmt.Errorf("invalid ICE URL")
		}
		// STUN/TURN URIs use an opaque URI scheme (stun:host:port), so parse
		// the remainder as an authority instead of expecting URL.Host directly.
		authority, err := url.Parse("//" + value[colon+1:])
		if err != nil || authority.Host == "" || authority.User != nil || authority.Fragment != "" {
			return nil, fmt.Errorf("invalid ICE URL")
		}
		result = append(result, value)
	}
	if len(result) == 0 || len(result) > 8 {
		return nil, fmt.Errorf("invalid ICE URL count")
	}
	return result, nil
}
