package server

import (
	"bytes"
	"crypto/hmac"
	"crypto/sha1"
	"encoding/base64"
	"strconv"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/coder/websocket"
)

func TestApprovedGuestDataIsRelayedBidirectionally(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()
	baseURL := httpServer.URL

	createResponse, err := http.Post(baseURL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	if createResponse.StatusCode != http.StatusCreated { t.Fatalf("create room status: %d", createResponse.StatusCode) }
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	hostURL := "ws" + strings.TrimPrefix(baseURL, "http") + "/v1/host/control/" + created.RoomCode
	host, _, err := websocket.Dial(ctx, hostURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + created.HostToken}},
	})
	if err != nil { t.Fatalf("host control connect: %v", err) }
	defer host.CloseNow()

	joinBody := bytes.NewBufferString(`{"playerName":"VantaGuest"}`)
	joinResponse, err := http.Post(baseURL+"/v1/rooms/"+created.RoomCode+"/requests", "application/json", joinBody)
	if err != nil { t.Fatal(err) }
	defer joinResponse.Body.Close()
	if joinResponse.StatusCode != http.StatusCreated { t.Fatalf("join request status: %d", joinResponse.StatusCode) }
	var joined createJoinResponse
	if err := json.NewDecoder(joinResponse.Body).Decode(&joined); err != nil { t.Fatal(err) }

	var joinEvent joinEvent
	if err := readJSON(ctx, host, &joinEvent); err != nil { t.Fatalf("host join event: %v", err) }
	if joinEvent.Type != "join_request" || joinEvent.PlayerName != "VantaGuest" {
		t.Fatalf("unexpected join event: %#v", joinEvent)
	}

	guestControlURL := "ws" + strings.TrimPrefix(baseURL, "http") +
		"/v1/guest/control/" + created.RoomCode + "/" + joined.RequestID
	guestControl, _, err := websocket.Dial(ctx, guestControlURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + joined.GuestToken}},
	})
	if err != nil { t.Fatalf("guest control connect: %v", err) }
	defer guestControl.CloseNow()

	approve := controlMessage{Type: "approve", RequestID: joined.RequestID}
	if err := writeJSONMessage(ctx, host, approve); err != nil { t.Fatalf("approve request: %v", err) }
	var approved controlMessage
	if err := readJSON(ctx, guestControl, &approved); err != nil { t.Fatalf("guest approval event: %v", err) }
	if approved.Type != "approved" { t.Fatalf("expected approved event, got %#v", approved) }
	var hostAcknowledgement controlMessage
	if err := readJSON(ctx, host, &hostAcknowledgement); err != nil {
		t.Fatalf("host decision acknowledgement: %v", err)
	}
	if hostAcknowledgement.Type != "request_updated" ||
		hostAcknowledgement.RequestID != joined.RequestID ||
		hostAcknowledgement.Reason != "approved" {
		t.Fatalf("unexpected host decision acknowledgement: %#v", hostAcknowledgement)
	}

	hostDataURL := "ws" + strings.TrimPrefix(baseURL, "http") +
		"/v1/host/data/" + created.RoomCode + "/" + joined.RequestID
	hostData, _, err := websocket.Dial(ctx, hostDataURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + created.HostToken}},
	})
	if err != nil { t.Fatalf("host data connect: %v", err) }
	defer hostData.CloseNow()

	guestDataURL := "ws" + strings.TrimPrefix(baseURL, "http") +
		"/v1/guest/data/" + created.RoomCode + "/" + joined.RequestID
	guestData, _, err := websocket.Dial(ctx, guestDataURL, &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer " + joined.GuestToken}},
	})
	if err != nil { t.Fatalf("guest data connect: %v", err) }
	defer guestData.CloseNow()

	outbound := []byte{0x00, 0x01, 0x7f, 0x80, 0xff, 0x42}
	if err := guestData.Write(ctx, websocket.MessageBinary, outbound); err != nil { t.Fatal(err) }
	messageType, reader, err := hostData.Reader(ctx)
	if err != nil { t.Fatal(err) }
	if messageType != websocket.MessageBinary { t.Fatalf("unexpected message type: %v", messageType) }
	got, err := io.ReadAll(reader)
	if err != nil { t.Fatal(err) }
	if !bytes.Equal(got, outbound) { t.Fatalf("binary payload mismatch: %v != %v", got, outbound) }

	returned := []byte{0x10, 0x20, 0x30}
	if err := hostData.Write(ctx, websocket.MessageBinary, returned); err != nil { t.Fatal(err) }
	messageType, reader, err = guestData.Reader(ctx)
	if err != nil { t.Fatal(err) }
	if messageType != websocket.MessageBinary { t.Fatalf("unexpected response type: %v", messageType) }
	got, err = io.ReadAll(reader)
	if err != nil { t.Fatal(err) }
	if !bytes.Equal(got, returned) { t.Fatalf("reverse payload mismatch: %v != %v", got, returned) }

	if err := guestData.Write(ctx, websocket.MessageText, []byte("VANTA_EOF")); err != nil { t.Fatal(err) }
	messageType, reader, err = hostData.Reader(ctx)
	if err != nil { t.Fatal(err) }
	payload, err := io.ReadAll(reader)
	if err != nil { t.Fatal(err) }
	if messageType != websocket.MessageText || string(payload) != "VANTA_EOF" {
		t.Fatalf("EOF marker not forwarded: type=%v payload=%q", messageType, payload)
	}
	if err := hostData.Write(ctx, websocket.MessageText, []byte("VANTA_EOF")); err != nil { t.Fatal(err) }
	messageType, reader, err = guestData.Reader(ctx)
	if err != nil { t.Fatal(err) }
	payload, err = io.ReadAll(reader)
	if err != nil { t.Fatal(err) }
	if messageType != websocket.MessageText || string(payload) != "VANTA_EOF" {
		t.Fatalf("reverse EOF marker not forwarded: type=%v payload=%q", messageType, payload)
	}
}

func TestPruneKeepsPairedGuestsCountedUntilDisconnect(t *testing.T) {
	expired := time.Now().Add(-time.Second)
	current := &room{
		requests: map[string]*joinRequest{
			"active": {
				id: "active", expiresAt: expired, state: "approved", pairStarted: true,
			},
			"half-open": {
				id: "half-open", expiresAt: expired, state: "approved",
			},
			"rejected": {
				id: "rejected", expiresAt: time.Now().Add(time.Hour), state: "rejected",
			},
		},
	}

	current.mu.Lock()
	pruneRequestsLocked(current)
	_, activeRemains := current.requests["active"]
	_, halfOpenRemains := current.requests["half-open"]
	_, rejectedRemains := current.requests["rejected"]
	current.mu.Unlock()

	if !activeRemains {
		t.Fatal("an established data pair must remain counted after its join ticket expires")
	}
	if halfOpenRemains {
		t.Fatal("an expired half-open request must be removed")
	}
	if rejectedRemains {
		t.Fatal("a rejected request must be removed")
	}
}

func TestJoinRequiresLiveHostAndValidPlayerName(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()

	createResponse, err := http.Post(httpServer.URL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	body := bytes.NewBufferString(`{"playerName":"not a valid name"}`)
	response, err := http.Post(httpServer.URL+"/v1/rooms/"+created.RoomCode+"/requests", "application/json", body)
	if err != nil { t.Fatal(err) }
	defer response.Body.Close()
	if response.StatusCode != http.StatusBadRequest { t.Fatalf("expected 400, got %d", response.StatusCode) }

	body = bytes.NewBufferString(`{"playerName":"Guest"}`)
	response, err = http.Post(httpServer.URL+"/v1/rooms/"+created.RoomCode+"/requests", "application/json", body)
	if err != nil { t.Fatal(err) }
	defer response.Body.Close()
	if response.StatusCode != http.StatusConflict { t.Fatalf("expected 409 when host is offline, got %d", response.StatusCode) }
}

func TestHostControlRejectsInvalidToken(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()

	createResponse, err := http.Post(httpServer.URL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	url := "ws" + strings.TrimPrefix(httpServer.URL, "http") + "/v1/host/control/" + created.RoomCode
	_, response, err := websocket.Dial(ctx, url, &websocket.DialOptions{
		HTTPHeader: http.Header{"Authorization": []string{"Bearer invalid-token"}},
	})
	if err == nil { t.Fatal("host control accepted an invalid token") }
	if response == nil || response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected HTTP 401, got %#v", response)
	}
}


func TestApprovedGuestSignallingRelaysOfferAnswerAndICE(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()

	createResponse, err := http.Post(httpServer.URL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	if createResponse.StatusCode != http.StatusCreated { t.Fatalf("create room status: %d", createResponse.StatusCode) }
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	websocketURL := "ws" + strings.TrimPrefix(httpServer.URL, "http")
	hostControl, _, err := websocket.Dial(ctx, websocketURL+"/v1/host/control/"+created.RoomCode,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+created.HostToken}}})
	if err != nil { t.Fatalf("host control connect: %v", err) }
	defer hostControl.CloseNow()

	joinResponse, err := http.Post(httpServer.URL+"/v1/rooms/"+created.RoomCode+"/requests",
		"application/json", strings.NewReader(`{"playerName":"SignalGuest","playerUUID":"f47ac10b-58cc-4372-a567-0e02b2c3d479"}`))
	if err != nil { t.Fatal(err) }
	defer joinResponse.Body.Close()
	if joinResponse.StatusCode != http.StatusCreated { t.Fatalf("join request status: %d", joinResponse.StatusCode) }
	var joined createJoinResponse
	if err := json.NewDecoder(joinResponse.Body).Decode(&joined); err != nil { t.Fatal(err) }

	var requested joinEvent
	if err := readJSON(ctx, hostControl, &requested); err != nil { t.Fatalf("host join event: %v", err) }
	if requested.Type != "join_request" || requested.PlayerName != "SignalGuest" ||
		requested.PlayerUUID != "f47ac10b-58cc-4372-a567-0e02b2c3d479" {
		t.Fatalf("unexpected join event or profile claim: %#v", requested)
	}

	guestControl, _, err := websocket.Dial(ctx,
		websocketURL+"/v1/guest/control/"+created.RoomCode+"/"+joined.RequestID,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+joined.GuestToken}}})
	if err != nil { t.Fatalf("guest control connect: %v", err) }
	defer guestControl.CloseNow()
	if err := writeJSONMessage(ctx, hostControl, controlMessage{Type: "approve", RequestID: joined.RequestID}); err != nil {
		t.Fatalf("approve guest: %v", err)
	}
	var approved controlMessage
	if err := readJSON(ctx, guestControl, &approved); err != nil { t.Fatalf("guest approval: %v", err) }
	if approved.Type != "approved" { t.Fatalf("expected approval, got %#v", approved) }
	var acknowledgement controlMessage
	if err := readJSON(ctx, hostControl, &acknowledgement); err != nil { t.Fatalf("host acknowledgement: %v", err) }
	if acknowledgement.Type != "request_updated" || acknowledgement.Reason != "approved" {
		t.Fatalf("unexpected approval acknowledgement: %#v", acknowledgement)
	}

	hostSignal, _, err := websocket.Dial(ctx,
		websocketURL+"/v1/host/signal/"+created.RoomCode+"/"+joined.RequestID,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+created.HostToken}}})
	if err != nil { t.Fatalf("host signalling connect: %v", err) }
	defer hostSignal.CloseNow()
	guestSignal, _, err := websocket.Dial(ctx,
		websocketURL+"/v1/guest/signal/"+created.RoomCode+"/"+joined.RequestID,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+joined.GuestToken}}})
	if err != nil { t.Fatalf("guest signalling connect: %v", err) }
	defer guestSignal.CloseNow()

	var hostReady, guestReady signalMessage
	if err := readJSON(ctx, hostSignal, &hostReady); err != nil { t.Fatalf("host ready event: %v", err) }
	if err := readJSON(ctx, guestSignal, &guestReady); err != nil { t.Fatalf("guest ready event: %v", err) }
	if hostReady.Type != "ready" || guestReady.Type != "ready" { t.Fatalf("peers not ready: host=%#v guest=%#v", hostReady, guestReady) }

	offer := signalMessage{Type: "offer", SDP: "v=0\\r\\no=- 1 1 IN IP4 0.0.0.0\\r\\ns=-\\r\\nt=0 0\\r\\n"}
	if err := writeJSONMessage(ctx, hostSignal, offer); err != nil { t.Fatalf("send offer: %v", err) }
	var gotOffer signalMessage
	if err := readJSON(ctx, guestSignal, &gotOffer); err != nil { t.Fatalf("receive offer: %v", err) }
	if gotOffer.Type != offer.Type || gotOffer.SDP != offer.SDP { t.Fatalf("offer changed: %#v", gotOffer) }

	answer := signalMessage{Type: "answer", SDP: "v=0\\r\\no=- 2 2 IN IP4 0.0.0.0\\r\\ns=-\\r\\nt=0 0\\r\\n"}
	if err := writeJSONMessage(ctx, guestSignal, answer); err != nil { t.Fatalf("send answer: %v", err) }
	var gotAnswer signalMessage
	if err := readJSON(ctx, hostSignal, &gotAnswer); err != nil { t.Fatalf("receive answer: %v", err) }
	if gotAnswer.Type != answer.Type || gotAnswer.SDP != answer.SDP { t.Fatalf("answer changed: %#v", gotAnswer) }

	lineIndex := 0
	hostCandidate := signalMessage{Type:"ice", Candidate:"candidate:1 1 UDP 2122260223 192.0.2.1 5000 typ host", SDPMid:"0", SDPMLineIndex:&lineIndex}
	if err := writeJSONMessage(ctx, hostSignal, hostCandidate); err != nil { t.Fatalf("send host ICE: %v", err) }
	var gotHostCandidate signalMessage
	if err := readJSON(ctx, guestSignal, &gotHostCandidate); err != nil { t.Fatalf("receive host ICE: %v", err) }
	if gotHostCandidate.Type != "ice" || gotHostCandidate.Candidate != hostCandidate.Candidate ||
		gotHostCandidate.SDPMid != "0" || gotHostCandidate.SDPMLineIndex == nil || *gotHostCandidate.SDPMLineIndex != 0 {
		t.Fatalf("host ICE changed: %#v", gotHostCandidate)
	}

	guestCandidate := signalMessage{Type:"ice", Candidate:"candidate:2 1 UDP 2122260222 192.0.2.2 5001 typ host", SDPMid:"0", SDPMLineIndex:&lineIndex}
	if err := writeJSONMessage(ctx, guestSignal, guestCandidate); err != nil { t.Fatalf("send guest ICE: %v", err) }
	var gotGuestCandidate signalMessage
	if err := readJSON(ctx, hostSignal, &gotGuestCandidate); err != nil { t.Fatalf("receive guest ICE: %v", err) }
	if gotGuestCandidate.Type != "ice" || gotGuestCandidate.Candidate != guestCandidate.Candidate {
		t.Fatalf("guest ICE changed: %#v", gotGuestCandidate)
	}

	peerConnected := httptest.NewRequest(http.MethodPost,
		"/v1/rooms/"+created.RoomCode+"/requests/"+joined.RequestID+"/peer-connected",
		strings.NewReader("{}"))
	peerConnected.Header.Set("Authorization", "Bearer "+created.HostToken)
	peerResponse := httptest.NewRecorder()
	service.Handler().ServeHTTP(peerResponse, peerConnected)
	if peerResponse.Code != http.StatusOK {
		t.Fatalf("host could not confirm the negotiated P2P peer: status=%d body=%s",
			peerResponse.Code, peerResponse.Body.String())
	}
	current := service.getRoom(created.RoomCode)
	current.mu.Lock()
	activeRequest := current.requests[joined.RequestID]
	current.mu.Unlock()
	if activeRequest == nil {
		t.Fatal("approved peer request disappeared before the P2P transport closed")
	}
	activeRequest.mu.Lock()
	p2pActive := activeRequest.p2pStarted
	activeRequest.mu.Unlock()
	if !p2pActive {
		t.Fatal("relay did not retain the established P2P guest in room accounting")
	}
}

func TestSignallingRejectsUnauthenticatedAndUnapprovedPeers(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()
	createResponse, err := http.Post(httpServer.URL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
	defer cancel()
	websocketURL := "ws" + strings.TrimPrefix(httpServer.URL, "http")
	_, response, err := websocket.Dial(ctx, websocketURL+"/v1/host/signal/"+created.RoomCode+"/abcdefghijklmnop",
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer invalid-token"}}})
	if err == nil { t.Fatal("host signalling accepted an invalid token") }
	if response == nil || response.StatusCode != http.StatusUnauthorized { t.Fatalf("expected HTTP 401, got %#v", response) }

	host, _, err := websocket.Dial(ctx, websocketURL+"/v1/host/control/"+created.RoomCode,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+created.HostToken}}})
	if err != nil { t.Fatalf("host control connect: %v", err) }
	defer host.CloseNow()
	joinResponse, err := http.Post(httpServer.URL+"/v1/rooms/"+created.RoomCode+"/requests",
		"application/json", strings.NewReader(`{"playerName":"PendingGuest"}`))
	if err != nil { t.Fatal(err) }
	defer joinResponse.Body.Close()
	var joined createJoinResponse
	if err := json.NewDecoder(joinResponse.Body).Decode(&joined); err != nil { t.Fatal(err) }

	_, response, err = websocket.Dial(ctx,
		websocketURL+"/v1/host/signal/"+created.RoomCode+"/"+joined.RequestID,
		&websocket.DialOptions{HTTPHeader: http.Header{"Authorization": []string{"Bearer "+created.HostToken}}})
	if err == nil { t.Fatal("host signalling accepted an unapproved request") }
	if response == nil || response.StatusCode != http.StatusConflict { t.Fatalf("expected HTTP 409, got %#v", response) }
}

func TestSignallingRejectsRoleViolationsAndOversizedPayloads(t *testing.T) {
	if validSignalMessage(signalMessage{Type:"offer", SDP:"v=0\\r\\n"}, false) { t.Fatal("guest must not originate offers") }
	if validSignalMessage(signalMessage{Type:"answer", SDP:"v=0\\r\\n"}, true) { t.Fatal("host must not send answers") }
	if validSignalMessage(signalMessage{Type:"offer", SDP:strings.Repeat("x", maxSignalSDPBytes+1)}, true) {
		t.Fatal("oversized SDP must be rejected")
	}
	index := 0
	if validSignalMessage(signalMessage{Type:"ice", Candidate:"not-a-candidate", SDPMid:"0", SDPMLineIndex:&index}, true) {
		t.Fatal("malformed ICE candidate must be rejected")
	}
}


func TestBuildICEServersIssuesShortLivedTURNCredentials(t *testing.T) {
	service := New(Config{
		STUNURLs: []string{"stun:stun.example.test:3478"},
		TURNURLs: []string{"turn:turn.example.test:3478?transport=udp"},
		TURNSharedSecret: "test-shared-secret",
		TURNCredentialTTL: 5 * time.Minute,
	})
	defer service.Close()

	servers, err := service.buildICEServers()
	if err != nil {
		t.Fatal(err)
	}
	if len(servers) != 2 {
		t.Fatalf("expected STUN and TURN entries, got %d", len(servers))
	}
	turn := servers[1]
	if len(turn.URLs) != 1 || turn.Username == "" || turn.Credential == "" {
		t.Fatalf("TURN entry is incomplete: %#v", turn)
	}
	parts := strings.SplitN(turn.Username, ":", 2)
	if len(parts) != 2 {
		t.Fatalf("TURN username has no expiry: %q", turn.Username)
	}
	unixExpiry, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		t.Fatalf("invalid TURN expiry: %v", err)
	}
	remaining := time.Until(time.Unix(unixExpiry, 0))
	if remaining <= 0 || remaining > 5*time.Minute {
		t.Fatalf("TURN credential expiry outside configured window: %v", remaining)
	}
	mac := hmac.New(sha1.New, []byte("test-shared-secret"))
	_, _ = mac.Write([]byte(turn.Username))
	expected := base64.StdEncoding.EncodeToString(mac.Sum(nil))
	if turn.Credential != expected {
		t.Fatal("TURN credential does not match coturn REST HMAC-SHA1 format")
	}
}

func TestBuildICEServersRejectsTURNWithoutSharedSecret(t *testing.T) {
	service := New(Config{TURNURLs: []string{"turn:turn.example.test:3478"}})
	defer service.Close()
	if _, err := service.buildICEServers(); err == nil {
		t.Fatal("TURN configuration without a shared secret must fail closed")
	}
}


func TestValidateICEURLsAcceptsSTUNAndTURNURIForms(t *testing.T) {
	for _, value := range []string{
		"stun:stun.example.test:3478",
		"stuns:stun.example.test:5349",
		"turn:turn.example.test:3478?transport=udp",
		"turns:turn.example.test:5349?transport=tcp",
	} {
		if _, err := validateICEURLs([]string{value}, "stun", "stuns", "turn", "turns"); err != nil {
			t.Fatalf("valid ICE URI %q rejected: %v", value, err)
		}
	}
	for _, value := range []string{
		"ftp://example.test:21",
		"turn://user:pass@example.test:3478",
		"turn:missing-port",
		"turn:",
		"turn:example.test:3478#fragment",
	} {
		if _, err := validateICEURLs([]string{value}, "stun", "stuns", "turn", "turns"); err == nil {
			t.Fatalf("invalid ICE URI %q was accepted", value)
		}
	}
}

func TestP2PRequestRemainsCountedUntilSignallingDisconnects(t *testing.T) {
	expired := time.Now().Add(-time.Second)
	current := &room{
		requests: map[string]*joinRequest{
			"active-p2p": {
				id: "active-p2p", expiresAt: expired, state: "approved", p2pStarted: true,
			},
			"expired-p2p": {
				id: "expired-p2p", expiresAt: expired, state: "approved",
			},
		},
	}
	current.mu.Lock()
	pruneRequestsLocked(current)
	_, activeRemains := current.requests["active-p2p"]
	_, expiredRemains := current.requests["expired-p2p"]
	current.mu.Unlock()
	if !activeRemains {
		t.Fatal("an established P2P session must remain counted until its signalling sockets close")
	}
	if expiredRemains {
		t.Fatal("an expired request that never established a P2P session must be pruned")
	}
}

func TestPeerConnectedRequiresHostCredentialsAndCompletedNegotiation(t *testing.T) {
	service := New(DefaultConfig())
	current := &room{
		code: "ABCDEFGHIJKLMNOPQRSTUVWX",
		hostToken: "host-token-with-enough-length-to-pass-bearer-check",
		expiresAt: time.Now().Add(time.Hour),
		requests: map[string]*joinRequest{
			"request-123456": {
				id: "request-123456",
				guestToken: "guest-token-with-enough-length-to-pass-bearer-check",
				expiresAt: time.Now().Add(time.Minute),
				state: "approved",
				offerSeen: true,
				answerSeen: true,
			},
		},
	}
	service.mu.Lock()
	service.rooms[current.code] = current
	service.mu.Unlock()
	item := current.requests["request-123456"]
	defer func() {
		// These are non-nil sentinels used only to exercise the approval gate.
		// Clear them before Server.Close so the websocket package never closes
		// an uninitialized zero-value connection.
		item.mu.Lock()
		item.hostSignal = nil
		item.guestSignal = nil
		item.mu.Unlock()
		service.Close()
	}()

	request := httptest.NewRequest(http.MethodPost,
		"/v1/rooms/"+current.code+"/requests/request-123456/peer-connected", strings.NewReader("{}"))
	request.Header.Set("Authorization", "Bearer "+current.hostToken)
	response := httptest.NewRecorder()
	service.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusConflict {
		t.Fatalf("peer confirmation without both live signalling sockets must fail, got %d", response.Code)
	}

	request = httptest.NewRequest(http.MethodPost,
		"/v1/rooms/"+current.code+"/requests/request-123456/peer-connected", strings.NewReader("{}"))
	request.Header.Set("Authorization", "Bearer incorrect-host-token")
	response = httptest.NewRecorder()
	service.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusUnauthorized {
		t.Fatalf("peer confirmation accepted an invalid host token, got %d", response.Code)
	}

	item.mu.Lock()
	item.hostSignal = &websocket.Conn{}
	item.guestSignal = &websocket.Conn{}
	item.mu.Unlock()

	request = httptest.NewRequest(http.MethodPost,
		"/v1/rooms/"+current.code+"/requests/request-123456/peer-connected", strings.NewReader("{}"))
	request.Header.Set("Authorization", "Bearer "+current.hostToken)
	response = httptest.NewRecorder()
	service.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusOK {
		t.Fatalf("host could not activate a fully negotiated peer session: status=%d body=%s",
			response.Code, response.Body.String())
	}
	item.mu.Lock()
	active := item.p2pStarted
	item.mu.Unlock()
	if !active {
		t.Fatal("peer-connected endpoint did not mark the approved session active")
	}

	request = httptest.NewRequest(http.MethodPost,
		"/v1/rooms/"+current.code+"/requests/request-123456/peer-connected", strings.NewReader("{}"))
	request.Header.Set("Authorization", "Bearer "+current.hostToken)
	response = httptest.NewRecorder()
	service.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusConflict {
		t.Fatalf("duplicate peer activation must be rejected, got %d", response.Code)
	}
}


func TestJoinRejectsMalformedPlayerUUIDClaims(t *testing.T) {
	service := New(DefaultConfig())
	defer service.Close()
	httpServer := httptest.NewServer(service.Handler())
	defer httpServer.Close()

	createResponse, err := http.Post(httpServer.URL+"/v1/rooms", "application/json", strings.NewReader("{}"))
	if err != nil { t.Fatal(err) }
	defer createResponse.Body.Close()
	var created createRoomResponse
	if err := json.NewDecoder(createResponse.Body).Decode(&created); err != nil { t.Fatal(err) }

	body := strings.NewReader(`{"playerName":"VantaGuest","playerUUID":"not-a-uuid"}`)
	response, err := http.Post(httpServer.URL+"/v1/rooms/"+created.RoomCode+"/requests", "application/json", body)
	if err != nil { t.Fatal(err) }
	defer response.Body.Close()
	if response.StatusCode != http.StatusBadRequest {
		t.Fatalf("malformed profile UUID should be rejected, got %d", response.StatusCode)
	}
}
