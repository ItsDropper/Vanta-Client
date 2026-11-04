package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
	"strings"

	"github.com/itsdropper/vanta-client/relay/internal/server"
)

func splitURLs(value string) []string {
	parts := strings.Split(value, ",")
	result := make([]string, 0, len(parts))
	for _, part := range parts {
		if cleaned := strings.TrimSpace(part); cleaned != "" {
			result = append(result, cleaned)
		}
	}
	return result
}

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	config := server.DefaultConfig()
	config.STUNURLs = splitURLs(os.Getenv("VANTA_STUN_URLS"))
	config.TURNURLs = splitURLs(os.Getenv("VANTA_TURN_URLS"))
	config.TURNSharedSecret = os.Getenv("VANTA_TURN_SHARED_SECRET")
	service := server.New(config)
	httpServer := &http.Server{
		Addr:              ":" + port,
		Handler:           service.Handler(),
		ReadHeaderTimeout: 5 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	stopped := make(chan os.Signal, 1)
	signal.Notify(stopped, os.Interrupt, syscall.SIGTERM)
	go func() {
		<-stopped
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		_ = httpServer.Shutdown(ctx)
		service.Close()
	}()

	log.Printf("Vanta relay listening on :%s (place behind an HTTPS/WSS reverse proxy)", port)
	if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		log.Fatal(err)
	}
	service.Close()
}
