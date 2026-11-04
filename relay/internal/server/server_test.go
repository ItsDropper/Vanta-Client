package server

import (
	"bytes"
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
