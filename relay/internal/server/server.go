package server

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base32"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/coder/websocket"
)

const (
	maxControlBytes         = 4 * 1024
	maxDataFrameBytes       = 16 * 1024
	maxSignalMessageBytes   = 64 * 1024
	maxSignalSDPBytes       = 48 * 1024
	maxSignalCandidateBytes = 4 * 1024
	maxSignalMessages       = 512
	maxPlayerName           = 16
)

type Config struct {
	RoomTTL           time.Duration
	RequestTTL        time.Duration
	MaxRequestsPerRoom int
	MaxActiveGuests   int
	RateWindow        time.Duration
	RateLimit         int
}

func DefaultConfig() Config {
	return Config{
		RoomTTL:            45 * time.Minute,
		RequestTTL:         2 * time.Minute,
		MaxRequestsPerRoom: 16,
		MaxActiveGuests:    8,
		RateWindow:         time.Minute,
		RateLimit:          20,
	}
}

type Server struct {
	config Config

	mu    sync.RWMutex
	rooms map[string]*room
	rateMu sync.Mutex
	rates map[string]rateWindow

	done chan struct{}
	once sync.Once
}

type rateWindow struct {
	start time.Time
	count int
}

type room struct {
	code      string
	hostToken string
	expiresAt time.Time

	mu          sync.Mutex
	hostControl *websocket.Conn
	hostWriteMu sync.Mutex
	requests    map[string]*joinRequest
}

type joinRequest struct {
	id         string
	playerName string
	guestToken string
	expiresAt  time.Time
	state      string

	mu             sync.Mutex
	guestControl   *websocket.Conn
	guestWriteMu   sync.Mutex
	hostData       *websocket.Conn
	guestData       *websocket.Conn
	hostSignal      *websocket.Conn
	guestSignal     *websocket.Conn
	hostDataUsed    bool
	guestDataUsed   bool
	hostSignalUsed  bool
	guestSignalUsed bool
	offerSeen       bool
	answerSeen      bool
	signalWriteMu   sync.Mutex
	pairStarted     bool
}

type createRoomResponse struct {
	RoomCode  string    `json:"roomCode"`
	HostToken string    `json:"hostToken"`
	ExpiresAt time.Time `json:"expiresAt"`
}

type createJoinRequest struct {
	PlayerName string `json:"playerName"`
}

type createJoinResponse struct {
	RequestID  string    `json:"requestId"`
	GuestToken string    `json:"guestToken"`
	ExpiresAt  time.Time `json:"expiresAt"`
}

type controlMessage struct {
	Type      string `json:"type"`
	RequestID string `json:"requestId,omitempty"`
	PlayerName string `json:"playerName,omitempty"`
	Reason    string `json:"reason,omitempty"`
}

type joinEvent struct {
	Type       string    `json:"type"`
	RequestID  string    `json:"requestId"`
	PlayerName string    `json:"playerName,omitempty"`
	ExpiresAt  time.Time `json:"expiresAt,omitempty"`
}

type signalMessage struct {
	Type          string `json:"type"`
	SDP           string `json:"sdp,omitempty"`
	Candidate     string `json:"candidate,omitempty"`
	SDPMid        string `json:"sdpMid,omitempty"`
	SDPMLineIndex *int   `json:"sdpMLineIndex,omitempty"`
}

func New(config Config) *Server {
	defaults := DefaultConfig()
	if config.RoomTTL <= 0 { config.RoomTTL = defaults.RoomTTL }
	if config.RequestTTL <= 0 { config.RequestTTL = defaults.RequestTTL }
	if config.MaxRequestsPerRoom < 1 { config.MaxRequestsPerRoom = defaults.MaxRequestsPerRoom }
	if config.MaxActiveGuests < 1 { config.MaxActiveGuests = defaults.MaxActiveGuests }
	if config.RateWindow <= 0 { config.RateWindow = defaults.RateWindow }
	if config.RateLimit < 1 { config.RateLimit = defaults.RateLimit }

	s := &Server{
		config: config,
		rooms:  make(map[string]*room),
		rates:  make(map[string]rateWindow),
		done:   make(chan struct{}),
	}
	go s.reaper()
	return s
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", s.health)
	mux.HandleFunc("POST /v1/rooms", s.createRoom)
	mux.HandleFunc("POST /v1/rooms/{code}/requests", s.createJoinRequest)
	mux.HandleFunc("GET /v1/host/control/{code}", s.hostControl)
	mux.HandleFunc("GET /v1/guest/control/{code}/{requestID}", s.guestControl)
	mux.HandleFunc("GET /v1/host/signal/{code}/{requestID}", s.hostSignal)
	mux.HandleFunc("GET /v1/guest/signal/{code}/{requestID}", s.guestSignal)
	mux.HandleFunc("GET /v1/host/data/{code}/{requestID}", s.hostData)
	mux.HandleFunc("GET /v1/guest/data/{code}/{requestID}", s.guestData)
	return securityHeaders(mux)
}

func (s *Server) Close() {
	s.once.Do(func() {
		close(s.done)
		s.mu.Lock()
		rooms := make([]*room, 0, len(s.rooms))
		for _, current := range s.rooms {
			rooms = append(rooms, current)
		}
		s.rooms = make(map[string]*room)
		s.mu.Unlock()
		for _, current := range rooms {
			closeRoom(current)
		}
	})
}

func (s *Server) health(w http.ResponseWriter, _ *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) createRoom(w http.ResponseWriter, r *http.Request) {
	if !s.allowRequest(r) {
		writeError(w, http.StatusTooManyRequests, "rate_limited")
		return
	}

	codeBytes, err := randomBytes(16)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "random_source_unavailable")
		return
	}
	hostToken, err := randomToken(32)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "random_source_unavailable")
		return
	}
	code := base32.StdEncoding.WithPadding(base32.NoPadding).EncodeToString(codeBytes)
	expires := time.Now().Add(s.config.RoomTTL)
	current := &room{
		code:      code,
		hostToken: hostToken,
		expiresAt: expires,
		requests:  make(map[string]*joinRequest),
	}

	s.mu.Lock()
	s.rooms[code] = current
	s.mu.Unlock()

	writeJSON(w, http.StatusCreated, createRoomResponse{
		RoomCode: code, HostToken: hostToken, ExpiresAt: expires,
	})
}

func (s *Server) createJoinRequest(w http.ResponseWriter, r *http.Request) {
	if !s.allowRequest(r) {
		writeError(w, http.StatusTooManyRequests, "rate_limited")
		return
	}
	var input createJoinRequest
	if err := decodeJSON(r.Body, maxControlBytes, &input); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_request")
		return
	}
	if !validPlayerName(input.PlayerName) {
		writeError(w, http.StatusBadRequest, "invalid_player_name")
		return
	}

	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil {
		writeError(w, http.StatusNotFound, "room_not_found")
		return
	}
	requestID, err := randomToken(12)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "random_source_unavailable")
		return
	}
	guestToken, err := randomToken(32)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "random_source_unavailable")
		return
	}

	request := &joinRequest{
		id: requestID, playerName: input.PlayerName, guestToken: guestToken,
		expiresAt: time.Now().Add(s.config.RequestTTL), state: "pending",
	}

	current.mu.Lock()
	host := current.hostControl
	if host == nil {
		current.mu.Unlock()
		writeError(w, http.StatusConflict, "host_not_connected")
		return
	}
	pruneRequestsLocked(current)
	if len(current.requests) >= s.config.MaxRequestsPerRoom {
		current.mu.Unlock()
		writeError(w, http.StatusTooManyRequests, "room_request_limit")
		return
	}
	activeGuests := 0
	for _, existing := range current.requests {
		if existing.state == "approved" || existing.state == "pending" {
			activeGuests++
		}
	}
	if activeGuests >= s.config.MaxActiveGuests {
		current.mu.Unlock()
		writeError(w, http.StatusTooManyRequests, "room_full")
		return
	}
	current.requests[requestID] = request
	current.mu.Unlock()

	event := joinEvent{
		Type: "join_request", RequestID: requestID, PlayerName: input.PlayerName,
		ExpiresAt: request.expiresAt,
	}
	if err := writeRoomJSON(current, host, event); err != nil {
		current.mu.Lock()
		delete(current.requests, requestID)
		current.mu.Unlock()
		writeError(w, http.StatusServiceUnavailable, "host_connection_unavailable")
		return
	}
	writeJSON(w, http.StatusCreated, createJoinResponse{
		RequestID: requestID, GuestToken: guestToken, ExpiresAt: request.expiresAt,
	})
}

func (s *Server) hostControl(w http.ResponseWriter, r *http.Request) {
	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil || !validBearer(r, current.hostToken) {
		writeError(w, http.StatusUnauthorized, "invalid_host_token")
		return
	}
	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}

	current.mu.Lock()
	if current.hostControl != nil {
		current.mu.Unlock()
		_ = conn.Close(websocket.StatusPolicyViolation, "host already connected")
		return
	}
	current.hostControl = conn
	current.mu.Unlock()

	defer func() {
		current.mu.Lock()
		wasCurrent := current.hostControl == conn
		if wasCurrent {
			current.hostControl = nil
		}
		current.mu.Unlock()
		_ = conn.Close(websocket.StatusNormalClosure, "host disconnected")
		if wasCurrent {
			closeRoomSignalling(current)
		}
	}()

	for {
		var command controlMessage
		if err := readJSON(r.Context(), conn, &command); err != nil {
			return
		}
		switch command.Type {
		case "approve":
			s.setRequestState(current, conn, command.RequestID, "approved", "")
		case "reject":
			reason := sanitizeReason(command.Reason)
			s.setRequestState(current, conn, command.RequestID, "rejected", reason)
		case "ping":
			_ = writeRoomJSON(current, conn, controlMessage{Type: "pong"})
		default:
			_ = conn.Close(websocket.StatusPolicyViolation, "unknown host command")
			return
		}
	}
}

func (s *Server) setRequestState(current *room, host *websocket.Conn,
	requestID, state, reason string) {
	current.mu.Lock()
	if current.hostControl != host {
		current.mu.Unlock()
		return
	}
	request := current.requests[requestID]
	if request == nil {
		current.mu.Unlock()
		_ = writeRoomJSON(current, host, controlMessage{
			Type: "request_error", RequestID: requestID, Reason: "request_not_found",
		})
		return
	}

	request.mu.Lock()
	if request.state != "pending" || time.Now().After(request.expiresAt) {
		request.mu.Unlock()
		current.mu.Unlock()
		_ = writeRoomJSON(current, host, controlMessage{
			Type: "request_error", RequestID: requestID, Reason: "request_not_pending",
		})
		return
	}
	request.state = state
	guestControl := request.guestControl
	request.mu.Unlock()
	current.mu.Unlock()

	// Confirm the authoritative state change to the host before it opens the
	// data socket on a separate connection. Without this acknowledgement the
	// host and relay can race, and attachData may see the old pending state.
	_ = writeRoomJSON(current, host, controlMessage{
		Type: "request_updated", RequestID: requestID, Reason: state,
	})
	if guestControl != nil {
		event := controlMessage{Type: state, RequestID: requestID, Reason: reason}
		_ = writeGuestJSON(request, guestControl, event)
	}
}

func (s *Server) guestControl(w http.ResponseWriter, r *http.Request) {
	_, request, ok := s.authorizeGuest(r)
	if !ok {
		writeError(w, http.StatusUnauthorized, "invalid_guest_token")
		return
	}
	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}

	request.mu.Lock()
	if request.guestControl != nil {
		request.mu.Unlock()
		_ = conn.Close(websocket.StatusPolicyViolation, "guest control already connected")
		return
	}
	request.guestControl = conn
	state := request.state
	request.mu.Unlock()

	if state != "pending" {
		_ = writeGuestJSON(request, conn, controlMessage{
			Type: state, RequestID: request.id,
		})
		_ = conn.Close(websocket.StatusNormalClosure, "request already resolved")
	} else {
		// Guest control is receive-only: approval/rejection is sent by the host.
		// Drain one bounded message and close if a guest attempts to send commands.
		_, reader, readErr := conn.Reader(r.Context())
		if readErr == nil {
			payload, drainErr := io.ReadAll(io.LimitReader(reader, maxControlBytes+1))
			if drainErr == nil && len(payload) <= maxControlBytes {
				_ = conn.Close(websocket.StatusPolicyViolation, "guest control is receive-only")
			}
		}
	}

	request.mu.Lock()
	if request.guestControl == conn {
		request.guestControl = nil
	}
	request.mu.Unlock()
	_ = conn.Close(websocket.StatusNormalClosure, "guest control disconnected")
}

func (s *Server) hostSignal(w http.ResponseWriter, r *http.Request) {
	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil || !validBearer(r, current.hostToken) {
		writeError(w, http.StatusUnauthorized, "invalid_host_token")
		return
	}
	s.attachSignal(w, r, current, r.PathValue("requestID"), true)
}

func (s *Server) guestSignal(w http.ResponseWriter, r *http.Request) {
	current, request, ok := s.authorizeGuest(r)
	if !ok || request.id != r.PathValue("requestID") {
		writeError(w, http.StatusUnauthorized, "invalid_guest_token")
		return
	}
	s.attachSignal(w, r, current, request.id, false)
}

// attachSignal provides bounded, authenticated SDP/ICE signalling for one
// approved peer pair. The host is the sole offerer; the guest answers. Data
// channel payloads never pass through this endpoint.
func (s *Server) attachSignal(w http.ResponseWriter, r *http.Request, current *room,
	requestID string, isHost bool) {
	current.mu.Lock()
	request := current.requests[requestID]
	hostOnline := current.hostControl != nil
	current.mu.Unlock()
	if request == nil {
		writeError(w, http.StatusNotFound, "request_not_found")
		return
	}
	if !hostOnline {
		writeError(w, http.StatusConflict, "host_not_connected")
		return
	}

	request.mu.Lock()
	available := request.state == "approved" && time.Now().Before(request.expiresAt) &&
		!request.pairStarted &&
		((isHost && !request.hostSignalUsed) || (!isHost && !request.guestSignalUsed))
	request.mu.Unlock()
	if !available {
		writeError(w, http.StatusConflict, "signalling_not_available")
		return
	}

	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}

	request.mu.Lock()
	if request.state != "approved" || time.Now().After(request.expiresAt) || request.pairStarted {
		request.mu.Unlock()
		_ = conn.Close(websocket.StatusPolicyViolation, "request is not available for P2P signalling")
		return
	}
	var peer *websocket.Conn
	if isHost {
		if request.hostSignalUsed {
			request.mu.Unlock()
			_ = conn.Close(websocket.StatusPolicyViolation, "host signalling socket already used")
			return
		}
		request.hostSignalUsed = true
		request.hostSignal = conn
		peer = request.guestSignal
	} else {
		if request.guestSignalUsed {
			request.mu.Unlock()
			_ = conn.Close(websocket.StatusPolicyViolation, "guest signalling socket already used")
			return
		}
		request.guestSignalUsed = true
		request.guestSignal = conn
		peer = request.hostSignal
	}
	request.mu.Unlock()

	if peer != nil {
		ready := signalMessage{Type: "ready"}
		if err := writeSignal(request, conn, ready); err != nil {
			_ = conn.CloseNow()
			_ = peer.CloseNow()
			return
		}
		if err := writeSignal(request, peer, ready); err != nil {
			_ = conn.CloseNow()
			_ = peer.CloseNow()
			return
		}
	}

	defer func() {
		request.mu.Lock()
		var other *websocket.Conn
		if isHost && request.hostSignal == conn {
			request.hostSignal = nil
			other = request.guestSignal
			request.guestSignal = nil
		} else if !isHost && request.guestSignal == conn {
			request.guestSignal = nil
			other = request.hostSignal
			request.hostSignal = nil
		}
		request.mu.Unlock()
		_ = conn.Close(websocket.StatusNormalClosure, "signalling closed")
		if other != nil {
			_ = other.CloseNow()
		}
	}()

	for count := 0; count < maxSignalMessages; count++ {
		message, err := readSignal(r.Context(), conn)
		if err != nil {
			return
		}
		if !validSignalMessage(message, isHost) {
			_ = conn.Close(websocket.StatusPolicyViolation, "invalid signalling message")
			return
		}

		request.mu.Lock()
		var destination *websocket.Conn
		if isHost {
			destination = request.guestSignal
			if message.Type == "offer" {
				if request.offerSeen {
					request.mu.Unlock()
					_ = conn.Close(websocket.StatusPolicyViolation, "duplicate offer")
					return
				}
				request.offerSeen = true
			}
			if message.Type == "ice" && !request.offerSeen {
				request.mu.Unlock()
				_ = conn.Close(websocket.StatusPolicyViolation, "ICE arrived before the host offer")
				return
			}
		} else {
			destination = request.hostSignal
			if message.Type == "answer" {
				if !request.offerSeen || request.answerSeen {
					request.mu.Unlock()
					_ = conn.Close(websocket.StatusPolicyViolation, "answer is missing an offer or is duplicated")
					return
				}
				request.answerSeen = true
			}
			if message.Type == "ice" && !request.answerSeen {
				request.mu.Unlock()
				_ = conn.Close(websocket.StatusPolicyViolation, "ICE arrived before the guest answer")
				return
			}
		}
		request.mu.Unlock()

		if destination == nil {
			_ = conn.Close(websocket.StatusPolicyViolation, "peer signalling socket is not connected")
			return
		}
		if err := writeSignal(request, destination, message); err != nil {
			return
		}
	}
	_ = conn.Close(websocket.StatusPolicyViolation, "too many signalling messages")
}

func readSignal(ctx context.Context, conn *websocket.Conn) (signalMessage, error) {
	messageType, reader, err := conn.Reader(ctx)
	if err != nil {
		return signalMessage{}, err
	}
	if messageType != websocket.MessageText {
		return signalMessage{}, errors.New("signalling messages must be text")
	}
	payload, err := io.ReadAll(io.LimitReader(reader, maxSignalMessageBytes+1))
	if err != nil {
		return signalMessage{}, err
	}
	if len(payload) > maxSignalMessageBytes {
		return signalMessage{}, errors.New("signalling message too large")
	}
	decoder := json.NewDecoder(bytes.NewReader(payload))
	decoder.DisallowUnknownFields()
	var message signalMessage
	if err := decoder.Decode(&message); err != nil {
		return signalMessage{}, err
	}
	if decoder.Decode(new(any)) != io.EOF {
		return signalMessage{}, errors.New("trailing signalling JSON")
	}
	if !validSignalMessage(message, false) && !validSignalMessage(message, true) {
		return signalMessage{}, errors.New("invalid signalling message")
	}
	return message, nil
}

func validSignalMessage(message signalMessage, isHost bool) bool {
	switch message.Type {
	case "offer":
		return isHost && len(message.SDP) > 0 && len(message.SDP) <= maxSignalSDPBytes &&
			strings.Contains(message.SDP, "v=0") && message.Candidate == "" &&
			message.SDPMid == "" && message.SDPMLineIndex == nil
	case "answer":
		return !isHost && len(message.SDP) > 0 && len(message.SDP) <= maxSignalSDPBytes &&
			strings.Contains(message.SDP, "v=0") && message.Candidate == "" &&
			message.SDPMid == "" && message.SDPMLineIndex == nil
	case "ice":
		return len(message.Candidate) > 0 && len(message.Candidate) <= maxSignalCandidateBytes &&
			strings.HasPrefix(message.Candidate, "candidate:") &&
			len(message.SDPMid) > 0 && len(message.SDPMid) <= 256 &&
			message.SDPMLineIndex != nil && *message.SDPMLineIndex >= 0 &&
			*message.SDPMLineIndex <= 255 && message.SDP == ""
	default:
		return false
	}
}

func writeSignal(request *joinRequest, conn *websocket.Conn, value signalMessage) error {
	request.signalWriteMu.Lock()
	defer request.signalWriteMu.Unlock()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	return writeJSONMessage(ctx, conn, value)
}

func (s *Server) hostData(w http.ResponseWriter, r *http.Request) {
	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil || !validBearer(r, current.hostToken) {
		writeError(w, http.StatusUnauthorized, "invalid_host_token")
		return
	}
	s.attachData(w, r, current, r.PathValue("requestID"), true)
}

func (s *Server) guestData(w http.ResponseWriter, r *http.Request) {
	current, request, ok := s.authorizeGuest(r)
	if !ok || request.id != r.PathValue("requestID") {
		writeError(w, http.StatusUnauthorized, "invalid_guest_token")
		return
	}
	s.attachData(w, r, current, request.id, false)
}

func (s *Server) attachData(w http.ResponseWriter, r *http.Request, current *room,
	requestID string, isHost bool) {
	current.mu.Lock()
	request := current.requests[requestID]
	current.mu.Unlock()
	if request == nil {
		writeError(w, http.StatusNotFound, "request_not_found")
		return
	}

	conn, err := websocket.Accept(w, r, nil)
	if err != nil {
		return
	}

	request.mu.Lock()
	if request.state != "approved" || time.Now().After(request.expiresAt) {
		request.mu.Unlock()
		_ = conn.Close(websocket.StatusPolicyViolation, "request not approved or expired")
		return
	}
	if isHost {
		if request.hostDataUsed {
			request.mu.Unlock()
			_ = conn.Close(websocket.StatusPolicyViolation, "host data stream already used")
			return
		}
		request.hostDataUsed = true
		request.hostData = conn
	} else {
		if request.guestDataUsed {
			request.mu.Unlock()
			_ = conn.Close(websocket.StatusPolicyViolation, "guest data stream already used")
			return
		}
		request.guestDataUsed = true
		request.guestData = conn
	}
	startPair := request.hostData != nil && request.guestData != nil && !request.pairStarted
	if startPair {
		request.pairStarted = true
	}
	hostData, guestData := request.hostData, request.guestData
	request.mu.Unlock()

	if startPair {
		go s.relayPair(current, request, hostData, guestData)
	}

	<-r.Context().Done()
	_ = conn.Close(websocket.StatusNormalClosure, "data connection closed")
}

func (s *Server) relayPair(current *room, request *joinRequest,
	host, guest *websocket.Conn) {
	results := make(chan error, 2)
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go func() { results <- relayDirection(ctx, host, guest) }()
	go func() { results <- relayDirection(ctx, guest, host) }()

	first := <-results
	if first != nil {
		_ = host.CloseNow()
		_ = guest.CloseNow()
		cancel()
		<-results
	} else {
		second := <-results
		if second != nil {
			_ = host.CloseNow()
			_ = guest.CloseNow()
		} else {
			_ = host.Close(websocket.StatusNormalClosure, "stream complete")
			_ = guest.Close(websocket.StatusNormalClosure, "stream complete")
		}
	}
	current.mu.Lock()
	delete(current.requests, request.id)
	current.mu.Unlock()
}

func relayDirection(ctx context.Context, source, destination *websocket.Conn) error {
	for {
		messageType, reader, err := source.Reader(ctx)
		if err != nil {
			return err
		}
		payload, err := io.ReadAll(io.LimitReader(reader, maxDataFrameBytes+1))
		if err != nil {
			return err
		}
		if len(payload) > maxDataFrameBytes {
			return errors.New("data frame exceeds limit")
		}
		if messageType == websocket.MessageText && string(payload) == "VANTA_EOF" {
			return destination.Write(ctx, websocket.MessageText, payload)
		}
		if messageType != websocket.MessageBinary {
			return errors.New("only binary data and VANTA_EOF are allowed")
		}
		if err := destination.Write(ctx, websocket.MessageBinary, payload); err != nil {
			return err
		}
	}
}

func (s *Server) authorizeGuest(r *http.Request) (*room, *joinRequest, bool) {
	current := s.getRoom(normalizeCode(r.PathValue("code")))
	if current == nil {
		return nil, nil, false
	}
	current.mu.Lock()
	request := current.requests[r.PathValue("requestID")]
	current.mu.Unlock()
	if request == nil {
		return nil, nil, false
	}
	request.mu.Lock()
	token := request.guestToken
	valid := validBearer(r, token) && time.Now().Before(request.expiresAt)
	request.mu.Unlock()
	if !valid {
		return nil, nil, false
	}
	return current, request, true
}

func (s *Server) getRoom(code string) *room {
	s.mu.RLock()
	current := s.rooms[code]
	s.mu.RUnlock()
	if current == nil || time.Now().After(current.expiresAt) {
		return nil
	}
	return current
}

func (s *Server) allowRequest(r *http.Request) bool {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		host = r.RemoteAddr
	}
	now := time.Now()
	s.rateMu.Lock()
	defer s.rateMu.Unlock()
	window := s.rates[host]
	if window.start.IsZero() || now.Sub(window.start) >= s.config.RateWindow {
		window = rateWindow{start: now}
	}
	window.count++
	s.rates[host] = window
	return window.count <= s.config.RateLimit
}

func (s *Server) reaper() {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-s.done:
			return
		case now := <-ticker.C:
			s.mu.Lock()
			for code, current := range s.rooms {
				if now.After(current.expiresAt) {
					delete(s.rooms, code)
					go closeRoom(current)
					continue
				}
				current.mu.Lock()
				pruneRequestsLocked(current)
				current.mu.Unlock()
			}
			s.rateMu.Lock()
			for host, window := range s.rates {
				if now.Sub(window.start) >= s.config.RateWindow*2 {
					delete(s.rates, host)
				}
			}
			s.rateMu.Unlock()
			s.mu.Unlock()
		}
	}
}

func pruneRequestsLocked(current *room) {
	now := time.Now()
	for id, request := range current.requests {
		request.mu.Lock()
		// A paired data stream is a live guest session, not an expired invite.
		// Keep it in the room map so MaxActiveGuests continues to count it until
		// relayPair closes and removes the completed/disconnected stream.
		expired := (now.After(request.expiresAt) || request.state == "rejected") &&
			!request.pairStarted
		request.mu.Unlock()
		if expired {
			delete(current.requests, id)
			go closeRequest(request)
		}
	}
}

func closeRequest(request *joinRequest) {
	request.mu.Lock()
	guestControl, hostData, guestData := request.guestControl, request.hostData, request.guestData
	hostSignal, guestSignal := request.hostSignal, request.guestSignal
	request.guestControl, request.hostData, request.guestData = nil, nil, nil
	request.hostSignal, request.guestSignal = nil, nil
	request.mu.Unlock()
	if guestControl != nil { _ = guestControl.CloseNow() }
	if hostData != nil { _ = hostData.CloseNow() }
	if guestData != nil { _ = guestData.CloseNow() }
	if hostSignal != nil { _ = hostSignal.CloseNow() }
	if guestSignal != nil { _ = guestSignal.CloseNow() }
}

func closeRoomSignalling(current *room) {
	current.mu.Lock()
	requests := make([]*joinRequest, 0, len(current.requests))
	for _, request := range current.requests {
		requests = append(requests, request)
	}
	current.mu.Unlock()
	for _, request := range requests {
		request.mu.Lock()
		hostSignal, guestSignal := request.hostSignal, request.guestSignal
		request.hostSignal, request.guestSignal = nil, nil
		request.mu.Unlock()
		if hostSignal != nil { _ = hostSignal.CloseNow() }
		if guestSignal != nil { _ = guestSignal.CloseNow() }
	}
}

func closeRoom(current *room) {
	current.mu.Lock()
	host := current.hostControl
	current.hostControl = nil
	requests := make([]*joinRequest, 0, len(current.requests))
	for _, request := range current.requests {
		requests = append(requests, request)
	}
	current.mu.Unlock()
	if host != nil { _ = host.CloseNow() }
	for _, request := range requests {
		request.mu.Lock()
		guestControl, hostData, guestData := request.guestControl, request.hostData, request.guestData
		hostSignal, guestSignal := request.hostSignal, request.guestSignal
		request.guestControl, request.hostData, request.guestData = nil, nil, nil
		request.hostSignal, request.guestSignal = nil, nil
		request.mu.Unlock()
		if guestControl != nil { _ = guestControl.CloseNow() }
		if hostData != nil { _ = hostData.CloseNow() }
		if guestData != nil { _ = guestData.CloseNow() }
		if hostSignal != nil { _ = hostSignal.CloseNow() }
		if guestSignal != nil { _ = guestSignal.CloseNow() }
	}
}

func writeRoomJSON(current *room, conn *websocket.Conn, value any) error {
	current.hostWriteMu.Lock()
	defer current.hostWriteMu.Unlock()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	return writeJSONMessage(ctx, conn, value)
}

func writeGuestJSON(request *joinRequest, conn *websocket.Conn, value any) error {
	request.guestWriteMu.Lock()
	defer request.guestWriteMu.Unlock()
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	return writeJSONMessage(ctx, conn, value)
}

func writeJSONMessage(ctx context.Context, conn *websocket.Conn, value any) error {
	payload, err := json.Marshal(value)
	if err != nil { return err }
	return conn.Write(ctx, websocket.MessageText, payload)
}

func readJSON(ctx context.Context, conn *websocket.Conn, target any) error {
	messageType, reader, err := conn.Reader(ctx)
	if err != nil { return err }
	if messageType != websocket.MessageText { return errors.New("control messages must be text") }
	payload, err := io.ReadAll(io.LimitReader(reader, maxControlBytes+1))
	if err != nil { return err }
	if len(payload) > maxControlBytes { return errors.New("control message too large") }
	return json.Unmarshal(payload, target)
}

func decodeJSON(body io.Reader, limit int64, target any) error {
	payload, err := io.ReadAll(io.LimitReader(body, limit+1))
	if err != nil { return err }
	if int64(len(payload)) > limit { return errors.New("request body too large") }
	decoder := json.NewDecoder(strings.NewReader(string(payload)))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil { return err }
	if decoder.Decode(new(any)) != io.EOF { return errors.New("trailing JSON data") }
	return nil
}

func validBearer(r *http.Request, expected string) bool {
	header := r.Header.Get("Authorization")
	if !strings.HasPrefix(header, "Bearer ") || expected == "" { return false }
	provided := strings.TrimPrefix(header, "Bearer ")
	if len(provided) != len(expected) { return false }
	return subtle.ConstantTimeCompare([]byte(provided), []byte(expected)) == 1
}

func validPlayerName(value string) bool {
	if len(value) < 1 || len(value) > maxPlayerName { return false }
	for _, r := range value {
		if !((r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '_') {
			return false
		}
	}
	return true
}

func sanitizeReason(value string) string {
	value = strings.TrimSpace(value)
	if len(value) > 80 { value = value[:80] }
	return value
}

func normalizeCode(value string) string {
	return strings.ToUpper(strings.TrimSpace(value))
}

func randomBytes(count int) ([]byte, error) {
	result := make([]byte, count)
	_, err := rand.Read(result)
	return result, err
}

func randomToken(count int) (string, error) {
	value, err := randomBytes(count)
	if err != nil { return "", err }
	return base64.RawURLEncoding.EncodeToString(value), nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}

func writeError(w http.ResponseWriter, status int, code string) {
	writeJSON(w, status, map[string]string{"error": code})
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(w, r)
	})
}

func (s *Server) setHostForTest(code string, conn *websocket.Conn) {
	current := s.getRoom(code)
	if current == nil { panic(fmt.Sprintf("missing room %s", code)) }
	current.mu.Lock()
	current.hostControl = conn
	current.mu.Unlock()
}
