// Command tailcraft-helper owns all Tailcat networking for the Tailcraft
// Minecraft mod. The mod starts it as a child process and reads one JSON event
// per line from stdout. Closing stdin stops the helper.
package main

import (
	"context"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"github.com/RefinedChicken/tailcraft/helper/internal/identity"
	"github.com/tailscale/tailcat"
	"tailscale.com/wgengine/filter"
)

const (
	virtualPort    = 25565
	maxConnections = 64
)

type event struct {
	Type    string `json:"type"`
	Mode    string `json:"mode,omitempty"`
	Address string `json:"address,omitempty"`
	Code    string `json:"code,omitempty"`
	Message string `json:"message,omitempty"`
}

type eventWriter struct {
	mu  sync.Mutex
	enc *json.Encoder
}

func newEventWriter(w io.Writer) *eventWriter { return &eventWriter{enc: json.NewEncoder(w)} }
func (w *eventWriter) send(e event) error {
	w.mu.Lock()
	defer w.mu.Unlock()
	return w.enc.Encode(e)
}

func main() {
	log.SetFlags(log.Ldate | log.Ltime | log.Lmicroseconds)
	out := newEventWriter(os.Stdout)
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	go func() { _, _ = io.Copy(io.Discard, os.Stdin); cancel() }()

	if len(os.Args) < 2 {
		_ = out.send(event{Type: "error", Code: "usage", Message: "expected host or join command"})
		return
	}
	var code string
	var err error
	switch os.Args[1] {
	case "host":
		code, err = runHost(ctx, out, os.Args[2:])
	case "join":
		code, err = runJoin(ctx, out, os.Args[2:])
	default:
		code, err = "usage", fmt.Errorf("unknown command %q", os.Args[1])
	}
	if err != nil && !errors.Is(err, context.Canceled) {
		_ = out.send(event{Type: "error", Code: code, Message: err.Error()})
	}
	_ = out.send(event{Type: "stopped"})
}

func runHost(ctx context.Context, out *eventWriter, args []string) (string, error) {
	fs := flag.NewFlagSet("host", flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	target := fs.String("target", "", "Minecraft server address to forward to")
	keyFile := fs.String("key-file", "", "persistent identity file; created on first use. Empty means an ephemeral address")
	if err := fs.Parse(args); err != nil {
		return "usage", err
	}
	if *target == "" {
		return "usage", errors.New("--target is required")
	}
	if err := validateTarget(*target); err != nil {
		return "usage", err
	}

	var id *identity.Identity
	var err error
	if *keyFile != "" {
		id, err = identity.LoadOrCreate(ctx, *keyFile)
	} else {
		id, err = identity.Ephemeral(ctx)
	}
	if err != nil {
		return "derp_unreachable", err
	}

	server := &tailcat.Server{
		Key:            id.Key.Private,
		PresharedKey:   id.Key.Public.PresharedKey,
		Logf:           log.Printf,
		Region:         id.Region,
		ServedTCPPorts: []filter.PortRange{{First: virtualPort, Last: virtualPort}},
	}
	defer server.Close()

	sem := make(chan struct{}, maxConnections)
	server.OnTCP = func(port uint16) func(net.Conn) {
		if port != virtualPort {
			return nil
		}
		return func(tunnel net.Conn) {
			select {
			case sem <- struct{}{}:
				defer func() { <-sem }()
			default:
				tunnel.Close()
				return
			}
			defer tunnel.Close()
			backend, err := net.DialTimeout("tcp", *target, 5*time.Second)
			if err != nil {
				log.Printf("dial Minecraft backend: %v", err)
				return
			}
			tailcat.ProxyConns(tunnel, backend)
		}
	}
	if err := server.StartContext(ctx); err != nil {
		return "derp_unreachable", fmt.Errorf("start Tailcat server: %w", err)
	}
	if err := out.send(event{Type: "ready", Mode: "host", Address: string(id.Addr())}); err != nil {
		return "helper_failed", err
	}
	<-ctx.Done()
	return "", ctx.Err()
}

func runJoin(ctx context.Context, out *eventWriter, args []string) (string, error) {
	fs := flag.NewFlagSet("join", flag.ContinueOnError)
	fs.SetOutput(io.Discard)
	address := fs.String("address", "", "Tailcat address of the server")
	if err := fs.Parse(args); err != nil {
		return "usage", err
	}
	if _, err := tailcat.ParseAddr(tailcat.Addr(*address)); err != nil {
		return "invalid_address", fmt.Errorf("invalid Tailcraft address: %w", err)
	}
	client := &tailcat.Client{Server: tailcat.Addr(*address), Logf: log.Printf}
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		client.Close()
		return "helper_failed", fmt.Errorf("listen on loopback: %w", err)
	}
	if err := out.send(event{Type: "ready", Mode: "join", Address: listener.Addr().String()}); err != nil {
		listener.Close()
		client.Close()
		return "helper_failed", err
	}

	sem := make(chan struct{}, maxConnections)
	var wg sync.WaitGroup
	go func() {
		<-ctx.Done()
		listener.Close()
		client.Close()
	}()
	defer func() {
		listener.Close()
		client.Close()
		wg.Wait()
	}()
	for {
		local, err := listener.Accept()
		if err != nil {
			if ctx.Err() != nil {
				return "", ctx.Err()
			}
			return "helper_failed", err
		}
		select {
		case sem <- struct{}{}:
		default:
			local.Close()
			continue
		}
		wg.Add(1)
		go func() {
			defer wg.Done()
			defer func() { <-sem }()
			defer local.Close()
			dialCtx, cancel := context.WithTimeout(ctx, 15*time.Second)
			tunnel, err := client.DialTCPPort(dialCtx, virtualPort)
			cancel()
			if err != nil {
				log.Printf("dial remote Minecraft port: %v", err)
				return
			}
			tailcat.ProxyConns(local, tunnel)
		}()
	}
}

// validateTarget accepts only numeric IP:port targets. The mod passes either
// loopback or the server-ip the admin configured in server.properties, never
// user-typed input.
func validateTarget(target string) error {
	host, port, err := net.SplitHostPort(target)
	if err != nil {
		return fmt.Errorf("invalid --target: %w", err)
	}
	if net.ParseIP(host) == nil {
		return errors.New("--target must be a numeric IP address")
	}
	if port == "" || port == "0" {
		return errors.New("--target must include a port")
	}
	return nil
}
